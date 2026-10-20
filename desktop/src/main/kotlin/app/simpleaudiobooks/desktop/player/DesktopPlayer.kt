package app.simpleaudiobooks.desktop.player

import app.simpleaudiobooks.data.Book
import app.simpleaudiobooks.data.Entry
import app.simpleaudiobooks.data.Library
import app.simpleaudiobooks.data.SettingsStore
import app.simpleaudiobooks.data.Stats
import app.simpleaudiobooks.playback.Rewind
import app.simpleaudiobooks.playback.SleepTimer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Hörbuchwiedergabe am Desktop: Buch laden, über Dateigrenzen springen, Hörstand und Hörzeit sichern, Schlaftimer,
 * Rücksprung nach Pausen. Alle Zustände werden nur auf dem Haupt-Thread verändert.
 */
class DesktopPlayer(
    private val library: Library,
    private val settings: SettingsStore,
    private val stats: Stats,
    private val scope: CoroutineScope,
    sinkFactory: () -> AudioSink = AudioSink::create,
    /** Thread, auf dem der Zustand verändert wird; in der App der der Oberfläche. */
    private val main: CoroutineDispatcher = Dispatchers.Main,
) : AudioEngine.Listener {

    data class Snapshot(
        val bookId: String? = null,
        val trackIndex: Int = 0,
        val trackPositionMs: Long = 0,
        val playing: Boolean = false,
        val speed: Float = 1f,
        val error: Boolean = false,
    )

    private val engine = AudioEngine(this, sinkFactory)
    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private var bookId: String? = null
    private var trackIndex = 0
    private var playing = false
    private var error = false
    private var pausedAt = 0L
    private var lastTick = 0L
    private var unsavedListenMs = 0L
    private var lastSaveAt = 0L

    private val now: Long get() = System.nanoTime() / 1_000_000

    init {
        scope.launch(main) {
            while (true) {
                tick()
                delay(200)
            }
        }
    }

    val currentBookId: String? get() = bookId
    val isPlaying: Boolean get() = playing

    fun currentEntry(): Entry? = library.entry(bookId)

    /** Gesamtposition im aktuellen Buch. */
    fun globalPosition(): Long {
        val book = library.book(bookId) ?: return 0
        return book.globalOf(trackIndex, engine.positionMs())
    }

    var volume: Float
        get() = engine.volume
        set(v) {
            engine.volume = v.coerceIn(0f, 1f)
        }

    // ---------------------------------------------------------------- Steuerung

    /** Spielt ein Buch; ohne [fromMs] am gemerkten Stand, ein gehörtes Buch von vorn. */
    fun play(id: String, fromMs: Long? = null) {
        val e = library.entry(id) ?: return
        if (id == bookId && fromMs == null) {
            resume()
            return
        }
        saveNow(flush = true)
        val start = fromMs ?: if (e.state.finished) 0L else e.positionMs
        if (e.state.finished) library.markFinished(id, false)
        load(e.book, start, play = true)
    }

    private fun load(book: Book, globalMs: Long, play: Boolean) {
        if (book.tracks.isEmpty()) return
        val (ti, pos) = book.locate(globalMs.coerceIn(0, book.durationMs))
        bookId = book.id
        trackIndex = ti
        error = false
        applyEffects()
        engine.open(book.tracks[ti].uri, pos, play)
        playing = play
        pausedAt = if (play) 0 else now
        lastTick = now
        library.savePosition(book.id, globalMs)
        publish(pos)
    }

    fun togglePlay() = if (playing) pause() else resume()

    fun pause() {
        if (!playing) return
        engine.pause()
        playing = false
        pausedAt = now
        saveNow(flush = true)
        publish()
    }

    fun resume() {
        val id = bookId
        if (id == null || library.book(id) == null) {
            library.lastPlayed()?.let { play(it.id) }
            return
        }
        if (playing) return
        if (error) {
            // Nach einem Fehler die Datei neu öffnen
            val book = library.book(id) ?: return
            load(book, library.state(id).positionMs, play = true)
            return
        }
        val back = if (pausedAt > 0) Rewind.resumeRewindMs(now - pausedAt, settings.value.resumeRewindSec, settings.value.smartRewind) else 0
        if (back > 0) seekTo(globalPosition() - back)
        engine.play()
        playing = true
        pausedAt = 0
        lastTick = now
        publish()
    }

    fun seekTo(globalMs: Long) {
        val book = library.book(bookId) ?: return
        val g = globalMs.coerceIn(0, (book.durationMs - 300).coerceAtLeast(0))
        val (ti, pos) = book.locate(g)
        if (ti != trackIndex) {
            trackIndex = ti
            engine.open(book.tracks[ti].uri, pos, playing)
        } else {
            engine.seek(pos)
        }
        library.savePosition(book.id, g)
        library.saveSoon()
        publish(pos)
    }

    fun skip(deltaMs: Long) = seekTo(globalPosition() + deltaMs)

    fun nextChapter() {
        val book = library.book(bookId) ?: return
        val idx = book.chapterAt(globalPosition())
        val next = book.chapters.getOrNull(idx + 1) ?: return
        seekTo(next.startMs)
    }

    /** Im Kapitel zurück an den Anfang; liegt der keine drei Sekunden zurück, ins vorige Kapitel. */
    fun previousChapter() {
        val book = library.book(bookId) ?: return
        val pos = globalPosition()
        val idx = book.chapterAt(pos)
        val ch = book.chapters.getOrNull(idx) ?: return
        val target = if (pos - ch.startMs > 3000 || idx == 0) ch.startMs else book.chapters[idx - 1].startMs
        seekTo(target)
    }

    fun setSpeed(speed: Float) {
        val id = bookId ?: return
        library.setSpeed(id, speed)
        applyEffects()
        publish()
    }

    fun setSkipSilence(on: Boolean) {
        val id = bookId ?: return
        library.setSkipSilence(id, on)
        applyEffects()
    }

    fun setBoost(db: Int) {
        val id = bookId ?: return
        library.setBoost(id, db)
        applyEffects()
    }

    /** Hält an und vergisst das Buch, etwa wenn es aus der Bibliothek entfernt wird. */
    fun unload(id: String) {
        if (bookId != id) return
        engine.pause()
        engine.close()
        playing = false
        bookId = null
        _snapshot.value = Snapshot()
    }

    fun speedOf(id: String?): Float {
        val s = library.state(id).speed
        return if (s > 0f) s else settings.value.defaultSpeed
    }

    private fun applyEffects() {
        val id = bookId ?: return
        val st = library.state(id)
        engine.setEffects(speedOf(id).toDouble(), st.skipSilence, st.boostDb)
    }

    /** Beim Beenden: Stand sichern und den Wiedergabe-Thread freigeben. */
    fun shutdown() {
        if (playing) {
            engine.pause()
            playing = false
        }
        saveNow(flush = true)
        engine.shutdown()
    }

    // ---------------------------------------------------------------- Zeitgeber

    private fun tick() {
        val pos = engine.positionMs()
        if (playing) {
            val t = now
            if (lastTick > 0) unsavedListenMs += (t - lastTick).coerceIn(0, 2000)
            lastTick = t
            if (t - lastSaveAt > 5000) saveNow(flush = false)
            checkSleepTimer()
        }
        publish(pos)
    }

    private fun checkSleepTimer() {
        when (val timer = SleepTimer.state.value) {
            is SleepTimer.Countdown -> {
                val left = timer.remaining()
                if (left <= 0) {
                    SleepTimer.cancel()
                    engine.fade = 1f
                    pause()
                } else {
                    engine.fade = if (settings.value.sleepFade && left < FADE_MS) (left.toFloat() / FADE_MS).coerceIn(0.05f, 1f) else 1f
                }
            }
            is SleepTimer.ChapterEnd -> {
                if (timer.bookId != bookId) {
                    SleepTimer.cancel()
                } else if (globalPosition() >= timer.chapterEndMs - 150) {
                    SleepTimer.cancel()
                    pause()
                }
            }
            null -> if (engine.fade != 1f) engine.fade = 1f
        }
    }

    private fun saveNow(flush: Boolean) {
        val id = bookId ?: return
        if (library.book(id) == null) return
        val listened = unsavedListenMs
        unsavedListenMs = 0
        library.savePosition(id, globalPosition(), listened)
        if (listened > 0) stats.add(listened)
        lastSaveAt = now
        if (flush) {
            library.flush()
            stats.flush()
        } else {
            library.saveSoon()
        }
    }

    private fun publish(pos: Long = engine.positionMs()) {
        _snapshot.value = Snapshot(bookId, trackIndex, pos, playing, speedOf(bookId), error)
    }

    // ---------------------------------------------------------------- Rückmeldungen der Engine

    override fun onEnded() {
        scope.launch(main) {
            val book = library.book(bookId) ?: return@launch
            if (trackIndex < book.tracks.lastIndex) {
                trackIndex++
                engine.open(book.tracks[trackIndex].uri, 0, playing)
                publish(0)
            } else {
                // Buch zu Ende
                engine.pause()
                playing = false
                if (SleepTimer.state.value is SleepTimer.ChapterEnd) SleepTimer.cancel()
                saveNow(flush = false)
                library.markFinished(book.id, true)
                library.flush()
                publish()
            }
        }
    }

    override fun onError(e: Throwable) {
        scope.launch(main) {
            playing = false
            error = true
            saveNow(flush = true)
            publish()
        }
    }

    override fun onDuration(ms: Long) {
        scope.launch(main) {
            val id = bookId ?: return@launch
            if (ms > 0) library.correctDuration(id, trackIndex, ms)
        }
    }

    companion object {
        private const val FADE_MS = 15_000L
    }
}
