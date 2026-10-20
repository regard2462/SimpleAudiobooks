package app.simpleaudiobooks.desktop.player

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/** Ausgabe für 16-Bit-PCM, Stereo, little endian. */
interface AudioSink {
    val sampleRate: Int

    /** Zahl der Frames, die tatsächlich hörbar ausgegeben wurden. */
    val playedFrames: Long

    fun start()
    fun stop()

    /** Verwirft gepufferte, noch nicht gespielte Daten. */
    fun flush()

    /** Schreibt PCM-Daten; blockiert, solange der Puffer voll ist. */
    fun write(buf: ByteArray, off: Int, len: Int)

    /** Wartet, bis alles Geschriebene gespielt ist. */
    fun drain()
    fun close()

    companion object {
        const val RATE = 44_100
        const val CHANNELS = 2
        const val FRAME_BYTES = 4

        /** Java Sound, in Tests (-Dsimpleaudiobooks.audio=null) eine stumme Ausgabe. */
        fun create(): AudioSink =
            if (System.getProperty("simpleaudiobooks.audio") == "null") NullSink(RATE, System.getProperty("simpleaudiobooks.nullrate")?.toDoubleOrNull() ?: 1.0)
            else JavaSoundSink(RATE)
    }
}

/** Ausgabe über Java Sound mit etwa 0,3 Sekunden Puffer, damit Pause und Springen schnell wirken. */
class JavaSoundSink(override val sampleRate: Int) : AudioSink {
    private val format = AudioFormat(sampleRate.toFloat(), 16, AudioSink.CHANNELS, true, false)
    private val line: SourceDataLine = AudioSystem.getSourceDataLine(format).apply {
        open(format, sampleRate * AudioSink.FRAME_BYTES * 3 / 10)
    }

    override val playedFrames: Long get() = line.longFramePosition
    override fun start() = line.start()
    override fun stop() = line.stop()
    override fun flush() = line.flush()
    override fun write(buf: ByteArray, off: Int, len: Int) {
        var o = off
        var l = len
        while (l > 0) {
            val n = line.write(buf, o, l)
            if (n <= 0) break
            o += n
            l -= n
        }
    }
    override fun drain() = line.drain()
    override fun close() = line.close()
}

/**
 * Stumme Ausgabe für Tests: spielt in Echtzeit mal [rate] und verwirft die Daten. Verhält sich beim Puffern wie
 * Java Sound, damit Position, Pause und Springen dieselben Wege gehen.
 */
class NullSink(override val sampleRate: Int, private val rate: Double) : AudioSink {
    private val lock = Object()
    private val bufferFrames = sampleRate * 3L / 10
    private var written = 0L
    private var basePlayed = 0L
    private var startedAt = 0L
    private var running = false

    private fun playedNow(): Long {
        if (!running) return basePlayed
        val elapsed = (System.nanoTime() - startedAt) / 1e9 * sampleRate * rate
        return minOf(written, basePlayed + elapsed.toLong())
    }

    override val playedFrames: Long get() = synchronized(lock) { playedNow() }

    override fun start() = synchronized(lock) {
        if (!running) {
            running = true
            startedAt = System.nanoTime()
        }
    }

    override fun stop() = synchronized(lock) {
        basePlayed = playedNow()
        running = false
    }

    override fun flush() = synchronized(lock) {
        basePlayed = playedNow()
        written = basePlayed
        startedAt = System.nanoTime()
    }

    override fun write(buf: ByteArray, off: Int, len: Int) {
        val frames = len / AudioSink.FRAME_BYTES
        while (true) {
            synchronized(lock) {
                val played = playedNow()
                if (written + frames - played <= bufferFrames) {
                    // Gespielte Zeit festhalten: läuft der Puffer leer, bleibt die Position stehen wie bei echter Ausgabe
                    if (running) {
                        basePlayed = played
                        startedAt = System.nanoTime()
                    }
                    written += frames
                    return
                }
            }
            // Voller Puffer blockiert wie Java Sound, auch bei angehaltener Ausgabe
            Thread.sleep(2)
        }
    }

    override fun drain() {
        while (playedFrames < synchronized(lock) { written }) Thread.sleep(2)
    }

    override fun close() {}
}
