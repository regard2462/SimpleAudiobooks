package app.simpleaudiobooks.desktop

import app.simpleaudiobooks.data.LibraryRoot
import app.simpleaudiobooks.desktop.player.AudioEngine
import app.simpleaudiobooks.desktop.player.NullSink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EngineTest {

    private fun fixture(name: String): String = File(javaClass.getResource("/fixtures/$name")!!.toURI()).absolutePath

    @Test
    fun probesAllFixtureFormats() {
        for (n in listOf("nero.m4b", "quicktime.m4b", "chapters23.mp3", "chapters24.mp3", "chapters.opus")) {
            val info = Ffmpeg.probe(fixture(n))
            assertNotNull(n, info)
            assertTrue("$n Dauer ${info!!.durationMs}", info.durationMs > 1000)
        }
    }

    @Test
    fun scansAFolderWithUmlautsIntoBooks() = runBlocking {
        val root = Files.createTempDirectory("hörbücher").toFile()
        try {
            val book = File(root, "Anna Beispiel - Die Prüfung").apply { mkdirs() }
            File(fixture("chapters23.mp3")).copyTo(File(book, "01 Anfang.mp3"))
            File(fixture("chapters24.mp3")).copyTo(File(book, "02 Ende.mp3"))
            File(fixture("nero.m4b")).copyTo(File(root, "Kurzgeschichte.m4b"))
            val covers = SkiaCovers(File(root, ".covers"))
            val books = FileScanner(covers).scanRoot(LibraryRoot(root.absolutePath, false), emptyMap()) { }
            assertNotNull(books)
            assertEquals(2, books!!.size)
            val pruefung = books.first { it.path == "Anna Beispiel - Die Prüfung" }
            assertEquals(2, pruefung.tracks.size)
            assertTrue(pruefung.durationMs > 2000)
            // Kapitel aus den ID3-CHAP-Rahmen der Testdateien
            assertTrue("Kapitel ${pruefung.chapters.size}", pruefung.chapters.size >= 2)
            val m4b = books.first { it.path == "Kurzgeschichte.m4b" }
            assertTrue(m4b.chapters.size >= 2)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun playsSeeksChangesTempoAndEnds() {
        val ended = CountDownLatch(1)
        var error: Throwable? = null
        var duration = 0L
        val engine = AudioEngine(object : AudioEngine.Listener {
            override fun onEnded() = ended.countDown()
            override fun onError(e: Throwable) { error = e }
            override fun onDuration(ms: Long) { duration = ms }
        }, { NullSink(44_100, 8.0) })
        try {
            engine.open(fixture("nero.m4b"), 0, play = true)
            assertTrue(engine.sync())
            Thread.sleep(400)
            val p1 = engine.positionMs()
            assertTrue("Position nach 0,4 s bei 8-fach: $p1", p1 in 1500..5000)
            assertTrue(duration > 3000)

            engine.pause()
            engine.sync()
            val paused = engine.positionMs()
            Thread.sleep(300)
            assertEquals(paused, engine.positionMs())

            engine.seek(1000)
            engine.sync()
            assertTrue(engine.positionMs() in 900..1100)

            engine.setEffects(2.0, skipSilence = false, boostDb = 6)
            engine.play()
            engine.sync()
            Thread.sleep(300)
            val p2 = engine.positionMs()
            // 0,3 s mal 8 mal Tempo 2 ergibt knapp 5 s Datei; Puffer und Filter lassen Spielraum
            assertTrue("Position mit Tempo 2: $p2, Fehler: $error", p2 in 2500..9000)

            assertTrue("Ende erreicht", ended.await(20, TimeUnit.SECONDS))
            assertEquals(null, error)
        } finally {
            engine.shutdown()
        }
    }
}
