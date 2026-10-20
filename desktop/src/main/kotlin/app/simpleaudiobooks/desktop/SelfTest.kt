package app.simpleaudiobooks.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.simpleaudiobooks.data.AppSettings
import app.simpleaudiobooks.data.JsonFile
import app.simpleaudiobooks.data.LibraryRoot
import app.simpleaudiobooks.desktop.player.AudioEngine
import app.simpleaudiobooks.desktop.player.NullSink
import app.simpleaudiobooks.desktop.ui.AppRoot
import app.simpleaudiobooks.desktop.ui.DesktopTheme
import app.simpleaudiobooks.desktop.ui.UiState
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Prüft ein fertiges Paket ohne Fenster und ohne Ton: Texte, FFmpeg, Wiedergabe in eine stumme Ausgabe und die
 * Oberfläche, außerhalb des Bildschirms gerechnet. Schreibt einen Bericht und daneben ein Bild der Oberfläche.
 * Aufruf: `Simple Audiobooks --selbsttest bericht.txt`, Rückgabewert 0 heißt bestanden.
 */
object SelfTest {

    fun run(report: File): Int {
        val lines = ArrayList<String>()
        var ok = true
        fun check(name: String, block: () -> String) {
            val result = runCatching(block)
            lines += if (result.isSuccess) "$name=ok ${result.getOrThrow()}".trim() else "$name=FEHLER ${result.exceptionOrNull()}"
            if (result.isFailure) ok = false
        }
        val work = Files.createTempDirectory("sa-selbsttest").toFile()
        try {
            lines += "fassung=${DesktopApp.VERSION}"
            lines += "system=${System.getProperty("os.name")} ${System.getProperty("os.arch")} java=${System.getProperty("java.version")}"

            check("texte") {
                Tr.load("de")
                val de = Tr.get("library")
                Tr.load("en")
                val en = Tr.get("library")
                require(de == "Bibliothek" && en == "Library") { "$de/$en" }
                "$de/$en"
            }

            val books = File(work, "Bibliothek/Testbuch").apply { mkdirs() }
            for (name in listOf("nero.m4b", "chapters23.mp3")) {
                val res = SelfTest::class.java.getResourceAsStream("/selbsttest/$name") ?: error("Ressource $name fehlt")
                res.use { input -> File(books, name).outputStream().use { input.copyTo(it) } }
            }
            val m4b = File(books, "nero.m4b")

            check("ffmpeg") {
                val info = Ffmpeg.probe(m4b.absolutePath, withPicture = true) ?: error("nicht lesbar")
                require(info.durationMs > 1000) { "Dauer ${info.durationMs}" }
                "dauer=${info.durationMs}ms tags=${info.tags.size}"
            }

            check("wiedergabe") {
                val ended = CountDownLatch(1)
                var error: Throwable? = null
                val engine = AudioEngine(object : AudioEngine.Listener {
                    override fun onEnded() = ended.countDown()
                    override fun onError(e: Throwable) { error = e }
                    override fun onDuration(ms: Long) {}
                }, { NullSink(44_100, 8.0) })
                try {
                    engine.open(m4b.absolutePath, 0, play = true)
                    // Beim allerersten Start packt JavaCPP die Bibliotheken erst aus
                    require(engine.sync(60_000)) { "keine Antwort" }
                    require(ended.await(20, TimeUnit.SECONDS)) { "kein Ende, Position ${engine.positionMs()}" }
                    error?.let { throw it }
                    "ende erreicht"
                } finally {
                    engine.shutdown()
                }
            }

            val data = File(work, "daten").apply { mkdirs() }
            JsonFile(File(data, "settings.json"), AppSettings.serializer())
                .write(AppSettings(roots = listOf(LibraryRoot(books.parentFile.absolutePath, false)), webCovers = false))
            val app = DesktopApp(data, Dispatchers.Default) { NullSink(44_100, 1.0) }
            try {
                check("bibliothek") {
                    val until = System.currentTimeMillis() + 20_000
                    while (System.currentTimeMillis() < until && (app.library.scan.value != null || app.library.entries.value.isEmpty())) Thread.sleep(100)
                    val list = app.library.entries.value
                    require(list.isNotEmpty() && list.sumOf { it.book.tracks.size } == 2) { "Bücher ${list.size}, Dateien ${list.sumOf { it.book.tracks.size }}" }
                    "buecher=${list.size} kapitel=${list.sumOf { it.book.chapters.size }}"
                }
                check("oberflaeche") {
                    Tr.load("de")
                    val ui = UiState()
                    val scene = ImageComposeScene(1280, 800, Density(1f), coroutineContext = Dispatchers.Unconfined) {
                        DesktopTheme(app.settings.value) { AppRoot(app, ui, null) }
                    }
                    try {
                        var image = scene.render(0)
                        repeat(10) { n ->
                            Thread.sleep(80)
                            Snapshot.sendApplyNotifications()
                            image = scene.render((n + 1) * 100_000_000L)
                        }
                        val bitmap = Bitmap()
                        bitmap.allocN32Pixels(image.width, image.height)
                        image.readPixels(bitmap)
                        val colors = HashSet<Int>()
                        for (y in 0 until image.height step 7) for (x in 0 until image.width step 7) colors += bitmap.getColor(x, y)
                        require(colors.size > 40) { "Bild fast einfarbig: ${colors.size} Farben" }
                        image.encodeToData(EncodedImageFormat.PNG)?.bytes?.let { File(report.absoluteFile.parentFile, report.nameWithoutExtension + ".png").writeBytes(it) }
                        "${image.width}x${image.height} farben=${colors.size}"
                    } finally {
                        scene.close()
                    }
                }
            } finally {
                app.shutdown()
            }
        } catch (t: Throwable) {
            lines += "abbruch=$t"
            ok = false
        } finally {
            work.deleteRecursively()
        }
        lines += "ergebnis=" + if (ok) "OK" else "FEHLER"
        runCatching {
            report.absoluteFile.parentFile?.mkdirs()
            report.writeText(lines.joinToString("\n", postfix = "\n"))
        }
        println(lines.joinToString("\n"))
        return if (ok) 0 else 1
    }
}
