package app.simpleaudiobooks.desktop.player

import app.simpleaudiobooks.desktop.Ffmpeg
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.javacv.FFmpegFrameFilter
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import java.nio.ShortBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Spielt eine Audiodatei: FFmpeg dekodiert und rechnet auf 44,1 kHz Stereo um, ein Filter ändert auf Wunsch das
 * Tempo ohne die Tonhöhe, kürzt Stille und hebt die Lautstärke an. Alles läuft auf einem eigenen Thread; die
 * Methoden stellen nur Aufträge in eine Warteschlange.
 */
class AudioEngine(
    private val listener: Listener,
    private val sinkFactory: () -> AudioSink = AudioSink::create,
) {
    interface Listener {
        /** Die Datei ist zu Ende gespielt. */
        fun onEnded()

        /** Die Datei ließ sich nicht öffnen oder dekodieren. */
        fun onError(e: Throwable)

        /** Tatsächliche Länge der geöffneten Datei. */
        fun onDuration(ms: Long)
    }

    /** Ein geschriebenes Stück PCM: ab welchem Ausgabe-Frame, ab welcher Stelle der Datei, in welchem Tempo. */
    private class Segment(val frameStart: Long, val mediaUs: Long, val frames: Int, val speed: Double)

    private val commands = LinkedBlockingQueue<() -> Unit>()

    @Volatile
    private var running = true

    @Volatile
    private var sink: AudioSink? = null
    private var grabber: FFmpegFrameGrabber? = null
    private var filter: FFmpegFrameFilter? = null
    private var filterKey = ""
    private var ended = false

    @Volatile
    var isPlaying = false
        private set
    private var writtenFrames = 0L
    private var cursorUs = -1L
    private val segments = ArrayDeque<Segment>()
    private val segLock = Any()

    @Volatile
    private var stillUs = 0L
    private var pcm = ByteArray(0)

    @Volatile
    private var speed = 1.0

    @Volatile
    private var skipSilence = false

    @Volatile
    private var boostDb = 0

    /** Lautstärke 0 bis 1. */
    @Volatile
    var volume = 1f

    /** Zusätzlicher Faktor zum Ausblenden (Schlaftimer). */
    @Volatile
    var fade = 1f

    private val worker = thread(name = "Wiedergabe", isDaemon = true) { loop() }

    // ---------------------------------------------------------------- Aufträge

    fun open(path: String, startMs: Long, play: Boolean) = post {
        doOpen(path, startMs)
        if (play) doPlay() else doPause()
    }

    fun play() = post { doPlay() }
    fun pause() = post { doPause() }
    fun seek(ms: Long) = post { doSeek(ms) }

    fun setEffects(speed: Double, skipSilence: Boolean, boostDb: Int) = post {
        this.speed = speed.coerceIn(0.5, 3.0)
        this.skipSilence = skipSilence
        this.boostDb = boostDb.coerceIn(0, 12)
        rebuildFilter()
    }

    /** Schließt die Datei; die Ausgabe bleibt offen. */
    fun close() = post { closeSource() }

    /** Beendet den Thread und gibt alles frei. */
    fun shutdown() {
        post {
            closeSource()
            sink?.close()
            sink = null
            running = false
        }
        worker.join(3000)
    }

    private fun post(cmd: () -> Unit) {
        commands.put(cmd)
    }

    /** Wartet, bis alle bisher gestellten Aufträge erledigt sind (für Tests und das Beenden). */
    fun sync(timeoutMs: Long = 5000): Boolean {
        val done = java.util.concurrent.CountDownLatch(1)
        post { done.countDown() }
        return done.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    // ---------------------------------------------------------------- Position

    /** Stelle in der geöffneten Datei, die gerade zu hören ist. */
    fun positionMs(): Long {
        val s = sink ?: return stillUs / 1000
        val played = s.playedFrames
        synchronized(segLock) {
            while (segments.size > 1 && segments[1].frameStart <= played) segments.removeFirst()
            val seg = segments.firstOrNull() ?: return stillUs / 1000
            if (played < seg.frameStart) return seg.mediaUs / 1000
            val into = (played - seg.frameStart).coerceAtMost(seg.frames.toLong())
            return (seg.mediaUs + (into * 1_000_000.0 / s.sampleRate * seg.speed).toLong()) / 1000
        }
    }

    // ---------------------------------------------------------------- Thread

    private fun loop() {
        while (running) {
            val active = isPlaying && grabber != null && !ended
            val cmd = if (active) commands.poll() else commands.poll(200, TimeUnit.MILLISECONDS)
            try {
                cmd?.invoke()
                if (isPlaying && grabber != null && !ended) decodeChunk()
            } catch (e: Throwable) {
                fail(e)
            }
        }
    }

    private fun fail(e: Throwable) {
        isPlaying = false
        runCatching { sink?.stop() }
        closeSource()
        listener.onError(e)
    }

    private fun ensureSink(): AudioSink = sink ?: sinkFactory().also { sink = it }

    private fun doOpen(path: String, startMs: Long) {
        closeSource()
        Ffmpeg.init()
        val g = FFmpegFrameGrabber(path)
        g.sampleMode = FrameGrabber.SampleMode.SHORT
        g.sampleRate = AudioSink.RATE
        g.audioChannels = AudioSink.CHANNELS
        g.start()
        grabber = g
        ended = false
        listener.onDuration(g.lengthInTime / 1000)
        if (startMs > 0) g.setAudioTimestamp(startMs * 1000)
        resetOutput(startMs * 1000)
        filterKey = ""
        rebuildFilter()
    }

    private fun closeSource() {
        runCatching { filter?.close() }
        filter = null
        filterKey = ""
        runCatching { grabber?.close() }
        grabber = null
    }

    private fun resetOutput(mediaUs: Long) {
        val s = ensureSink()
        s.flush()
        writtenFrames = s.playedFrames
        cursorUs = -1
        stillUs = mediaUs
        synchronized(segLock) { segments.clear() }
    }

    private fun doPlay() {
        if (grabber == null) return
        ensureSink().start()
        isPlaying = true
    }

    private fun doPause() {
        stillUs = positionMs() * 1000
        sink?.stop()
        isPlaying = false
    }

    private fun doSeek(ms: Long) {
        val g = grabber ?: return
        g.setAudioTimestamp(ms.coerceAtLeast(0) * 1000)
        ended = false
        resetOutput(ms * 1000)
        filterKey = ""
        rebuildFilter()
    }

    private fun rebuildFilter() {
        val parts = ArrayList<String>()
        if (skipSilence) parts += "silenceremove=stop_periods=-1:stop_duration=0.6:stop_threshold=-42dB:stop_silence=0.25"
        if (abs(speed - 1.0) > 0.001) parts += tempoChain(speed)
        if (boostDb > 0) parts += listOf("volume=${boostDb}dB", "alimiter=limit=0.95:level=false")
        val key = parts.joinToString(",")
        if (key == filterKey && (filter != null || key.isEmpty())) return
        runCatching { filter?.close() }
        filter = null
        filterKey = key
        if (key.isEmpty() || grabber == null) return
        // Lautstärke und Begrenzer rechnen in Fließkomma; die Ausgabe braucht wieder 16 Bit Stereo
        filter = FFmpegFrameFilter("$key,aformat=sample_fmts=s16:channel_layouts=stereo", AudioSink.CHANNELS).apply {
            sampleFormat = AV_SAMPLE_FMT_S16
            sampleRate = AudioSink.RATE
            start()
        }
    }

    /** atempo reicht von 0,5 bis 2; schnellere Tempi als Kette. */
    private fun tempoChain(s: Double): String =
        if (s <= 2.0) "atempo=%.4f".format(java.util.Locale.ROOT, s)
        else "atempo=2.0,atempo=%.4f".format(java.util.Locale.ROOT, s / 2.0)

    private fun decodeChunk() {
        val g = grabber ?: return
        val frame = g.grabSamples()
        if (frame == null) {
            finishSource()
            return
        }
        val ts = g.timestamp
        val f = filter
        if (f == null) {
            writeFrame(frame, ts, 1.0)
        } else {
            f.push(frame)
            while (true) {
                val out = f.pullSamples() ?: break
                writeFrame(out, ts, if (abs(speed - 1.0) > 0.001) speed else 1.0)
            }
        }
    }

    private fun finishSource() {
        ended = true
        // Rest aus dem Filter holen und alles ausspielen, dann melden
        filter?.let { f ->
            runCatching {
                f.push(null)
                while (true) {
                    val out = f.pullSamples() ?: break
                    writeFrame(out, cursorUs.coerceAtLeast(0), if (abs(speed - 1.0) > 0.001) speed else 1.0)
                }
            }
        }
        sink?.drain()
        stillUs = positionMs() * 1000
        listener.onEnded()
    }

    private fun writeFrame(frame: Frame, inputTsUs: Long, segSpeed: Double) {
        val samples = frame.samples?.firstOrNull() as? ShortBuffer ?: return
        val count = samples.remaining()
        if (count <= 0) return
        val frames = count / AudioSink.CHANNELS
        val durUs = (frames * 1_000_000.0 / AudioSink.RATE * segSpeed).toLong()
        var start = if (cursorUs < 0) inputTsUs else cursorUs
        // Nach Stille-Kürzung oder Filterverzug wieder an die Zeit der Eingabe anlehnen
        if (abs(start - inputTsUs) > 600_000) start = inputTsUs
        cursorUs = start + durUs

        val bytes = count * 2
        if (pcm.size < bytes) pcm = ByteArray(bytes)
        val gain = (volume * fade).coerceIn(0f, 1f)
        val pos = samples.position()
        for (i in 0 until count) {
            var v = samples.get(pos + i).toInt()
            if (gain < 0.999f) v = (v * gain).toInt()
            pcm[2 * i] = (v and 0xFF).toByte()
            pcm[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        synchronized(segLock) { segments.addLast(Segment(writtenFrames, start, frames, segSpeed)) }
        writtenFrames += frames
        ensureSink().write(pcm, 0, bytes)
    }
}
