package app.simpleaudiobooks.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.simpleaudiobooks.data.AppSettings
import app.simpleaudiobooks.data.Book
import app.simpleaudiobooks.data.BookState
import app.simpleaudiobooks.data.Bookmark
import app.simpleaudiobooks.data.ColorPalette
import app.simpleaudiobooks.data.JsonFile
import app.simpleaudiobooks.data.LibraryRoot
import app.simpleaudiobooks.data.StatsData
import app.simpleaudiobooks.data.ThemeMode
import app.simpleaudiobooks.desktop.player.NullSink
import app.simpleaudiobooks.desktop.ui.AppRoot
import app.simpleaudiobooks.desktop.ui.BookDialog
import app.simpleaudiobooks.desktop.ui.DesktopTheme
import app.simpleaudiobooks.desktop.ui.Page
import app.simpleaudiobooks.desktop.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Point
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.random.Random

/**
 * Bildschirmfotos für die README, ohne Fenster gerechnet. Eine Beispielbibliothek mit gemeinfreien Klassikern
 * entsteht in einem temporären Ordner; Titelbilder sind schlichte, hier gezeichnete Grafiken.
 * Aufruf über `gradlew :desktop:screenshots`.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "screenshots").apply { mkdirs() }
    // Die Bibliothek liegt in einem kurzen, neutralen Ordner, weil die Einstellungen ihren Pfad zeigen
    val library = File(args.getOrNull(1)?.takeIf { it.isNotBlank() } ?: error("Ordner für die Beispielbibliothek fehlt"))
    check(!library.exists()) { "$library gibt es schon" }
    val work = Files.createTempDirectory("sa-bilder").toFile()
    try {
        Demo(library, work).run(out)
    } finally {
        library.deleteRecursively()
        work.deleteRecursively()
    }
    kotlin.system.exitProcess(0)
}

private class DemoBook(
    val author: String,
    val title: String,
    val chapterNames: List<String>,
    val progress: Float,
    val finished: Boolean = false,
    val cover: Int? = null,
    val narrator: String? = null,
    val year: String? = null,
)

private class Demo(private val root: File, work: File) {
    private val data = File(work, "daten")
    private val rnd = Random(7)

    private fun numbered(prefix: String, n: Int) = (1..n).map { "$prefix $it" }

    private val books = listOf(
        DemoBook("Franz Kafka", "Die Verwandlung", listOf("Erster Teil", "Zweiter Teil", "Dritter Teil"), 0f, cover = 215, year = "1915"),
        DemoBook("Theodor Fontane", "Effi Briest", numbered("Kapitel", 12), 0.62f, cover = 340, year = "1895"),
        DemoBook("Theodor Storm", "Der Schimmelreiter", numbered("Kapitel", 6), 1f, finished = true, cover = 195, year = "1888"),
        DemoBook("Jane Austen", "Pride and Prejudice", numbered("Chapter", 14), 0.18f, cover = 25, year = "1813"),
        DemoBook("Herman Melville", "Moby Dick", numbered("Chapter", 20), 0f, cover = 200),
        DemoBook("Arthur Conan Doyle", "The Hound of the Baskervilles", numbered("Chapter", 10), 0.4f, cover = 275),
        DemoBook("Johanna Spyri", "Heidi", numbered("Kapitel", 8), 0f, cover = 95),
        DemoBook("Jules Verne", "Reise um die Erde in 80 Tagen", numbered("Kapitel", 9), 1f, finished = true, cover = 45),
        DemoBook("E. T. A. Hoffmann", "Der Sandmann", numbered("Teil", 3), 0f),
        DemoBook("Annette von Droste-Hülshoff", "Die Judenbuche", numbered("Kapitel", 4), 0.85f),
        DemoBook("Mary Shelley", "Frankenstein", numbered("Chapter", 11), 0f, cover = 160),
        DemoBook("Robert Louis Stevenson", "Treasure Island", numbered("Chapter", 12), 0f),
        DemoBook("Lewis Carroll", "Alice's Adventures in Wonderland", numbered("Chapter", 7), 1f, finished = true, cover = 300),
        DemoBook("Selma Lagerlöf", "Nils Holgersson", numbered("Kapitel", 15), 0f),
    )

    fun run(out: File) {
        createFiles()
        // Erster Durchlauf liest die Dateien ein; danach bekommen die Bücher glaubhafte Längen und Hörstände
        settings(ThemeMode.DARK, grid = true)
        DesktopApp(data, Dispatchers.Default) { NullSink(44_100, 1.0) }.apply {
            waitForScan(this)
            shutdown()
        }
        val playingId = prepareData()

        val app = DesktopApp(data, Dispatchers.Default) { NullSink(44_100, 1.0) }
        try {
            waitForScan(app)
            app.player.play(playingId)
            Thread.sleep(600)
            for (lang in listOf("de", "en")) {
                Tr.load(lang)
                shot(app, out, "$lang-bibliothek", ThemeMode.DARK, grid = true) { selected = playingId }
                shot(app, out, "$lang-liste", ThemeMode.LIGHT, grid = false) { selected = null }
                shot(app, out, "$lang-statistik", ThemeMode.DARK, grid = true) { page = Page.STATS }
                shot(app, out, "$lang-einstellungen", ThemeMode.LIGHT, grid = true) { page = Page.SETTINGS }
            }
            // Zur Prüfung: Menüs und Dialoge, geöffnet per simuliertem Klick oder direkt
            val check = File(out, "pruefung").apply { mkdirs() }
            Tr.load("de")
            shot(app, check, "tempo", ThemeMode.DARK, grid = true, clicks = listOf(Click(1455f, 1067f))) {}
            shot(app, check, "schlaftimer", ThemeMode.DARK, grid = true, clicks = listOf(Click(1520f, 1067f))) {}
            shot(app, check, "kontextmenue", ThemeMode.DARK, grid = true, clicks = listOf(Click(700f, 560f, secondary = true))) {}
            shot(app, check, "sortieren", ThemeMode.LIGHT, grid = true, clicks = listOf(Click(1625f, 58f))) {}
            shot(app, check, "bearbeiten", ThemeMode.DARK, grid = true) { dialog = BookDialog.Edit(playingId) }
            shot(app, check, "entfernen", ThemeMode.LIGHT, grid = true) { dialog = BookDialog.Remove(playingId) }
            shot(app, check, "notiz", ThemeMode.DARK, grid = true) { selected = playingId; dialog = BookDialog.BookmarkNote(playingId, 1, "Gregor erwacht") }
            shot(app, check, "lesezeichen", ThemeMode.DARK, grid = true, clicks = listOf(Click(1550f, 656f))) { selected = playingId }
            shot(app, check, "suche", ThemeMode.LIGHT, grid = false) { query = "the" }
            Tr.load("en")
            shot(app, check, "en-schmal", ThemeMode.DARK, grid = true, width = 1200, height = 800) { selected = playingId }
            shot(app, check, "en-kleinst", ThemeMode.LIGHT, grid = true, width = 960, height = 640) { selected = playingId }
            shot(app, check, "en-kleinst-liste", ThemeMode.DARK, grid = false, width = 960, height = 640) {}
        } finally {
            app.shutdown()
        }
    }

    private fun settings(theme: ThemeMode, grid: Boolean) {
        data.mkdirs()
        JsonFile(File(data, "settings.json"), AppSettings.serializer()).write(
            AppSettings(roots = listOf(LibraryRoot(root.absolutePath, false)), webCovers = false, theme = theme, grid = grid, palette = ColorPalette.NIGHT),
        )
    }

    private fun waitForScan(app: DesktopApp) {
        val until = System.currentTimeMillis() + 60_000
        Thread.sleep(300)
        while (System.currentTimeMillis() < until && (app.library.scan.value != null || app.library.entries.value.size < books.size)) Thread.sleep(100)
        check(app.library.entries.value.size == books.size) { "eingelesen: ${app.library.entries.value.size}" }
    }

    private fun createFiles() {
        books.forEachIndexed { i, b ->
            val dir = File(root, "${b.author}/${b.title}").apply { mkdirs() }
            b.chapterNames.forEachIndexed { j, name ->
                // Das laufende Buch bekommt echte, lange (stille) Dateien, die übrigen kurze
                val seconds = if (i == 0) listOf(14 * 60, 19 * 60, 16 * 60)[j] else 1
                wav(File(dir, "%02d %s.wav".format(j + 1, name)), seconds)
            }
            b.cover?.let { hue -> File(dir, "cover.jpg").writeBytes(coverArt(b, hue.toFloat())) }
        }
    }

    /** Setzt Längen, Hörstände, Lesezeichen und Statistik; liefert die ID des laufenden Buches. */
    private fun prepareData(): String {
        val booksFile = JsonFile(File(data, "books.json"), ListSerializer(Book.serializer()))
        val list = booksFile.read()!!
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val states = HashMap<String, BookState>()
        var playingId = ""
        val changed = list.map { book ->
            val i = books.indexOfFirst { book.path.endsWith(it.title) }
            val demo = books[i]
            val nb = if (i == 0) {
                playingId = book.id
                book
            } else {
                book.copy(tracks = book.tracks.map { it.copy(durationMs = (22 + rnd.nextInt(24)) * 60_000L + rnd.nextInt(60) * 1000L) })
            }
            val total = nb.tracks.sumOf { it.durationMs }
            val state = when {
                i == 0 -> BookState(
                    positionMs = nb.tracks[0].durationMs + 4 * 60_000L + 10_000L,
                    lastPlayedAt = now - 60_000,
                    listenedMs = 41 * 60_000L,
                    bookmarks = listOf(
                        Bookmark(1, 6 * 60_000L + 32_000L, "Gregor erwacht als Ungeziefer", now - 2 * day),
                        Bookmark(2, nb.tracks[0].durationMs + 2 * 60_000L, "", now - day),
                    ),
                )
                demo.finished -> BookState(positionMs = total, finished = true, finishedAt = now - (3 + i) * day, lastPlayedAt = now - (3 + i) * day, listenedMs = (total * 1.05).toLong())
                demo.progress > 0 -> BookState(positionMs = (total * demo.progress).toLong(), lastPlayedAt = now - i * 3_600_000L, listenedMs = (total * demo.progress).toLong())
                else -> BookState()
            }
            states[nb.id] = state
            nb.copy(addedAt = now - i * day, year = demo.year ?: nb.year)
        }
        booksFile.write(changed)
        JsonFile(File(data, "states.json"), MapSerializer(String.serializer(), BookState.serializer())).write(states)
        val today = LocalDate.now()
        val days = (0 until 30).associate { d ->
            val minutes = if (rnd.nextInt(5) == 0) 0 else 15 + rnd.nextInt(80)
            today.minusDays(d.toLong()).toString() to minutes * 60_000L
        }
        JsonFile(File(data, "stats.json"), StatsData.serializer()).write(StatsData(days))
        return playingId
    }

    class Click(val x: Float, val y: Float, val secondary: Boolean = false)

    @OptIn(ExperimentalComposeUiApi::class)
    private fun shot(
        app: DesktopApp,
        out: File,
        name: String,
        theme: ThemeMode,
        grid: Boolean,
        clicks: List<Click> = emptyList(),
        width: Int = 1440,
        height: Int = 900,
        setup: UiState.() -> Unit,
    ) {
        app.settings.update { it.copy(theme = theme, grid = grid) }
        val ui = UiState().apply(setup)
        val scene = ImageComposeScene((width * 1.25f).toInt(), (height * 1.25f).toInt(), Density(1.25f), coroutineContext = Dispatchers.Unconfined) {
            DesktopTheme(app.settings.value) { AppRoot(app, ui, null) }
        }
        try {
            var time = 0L
            fun frame() {
                Thread.sleep(90)
                Snapshot.sendApplyNotifications()
                time += 100_000_000L
                scene.render(time)
            }
            var image = scene.render(0)
            repeat(8) { frame() }
            for (c in clicks) {
                val at = Offset(c.x, c.y)
                val button = if (c.secondary) PointerButton.Secondary else PointerButton.Primary
                val pressed = if (c.secondary) PointerButtons(isSecondaryPressed = true) else PointerButtons(isPrimaryPressed = true)
                scene.sendPointerEvent(PointerEventType.Move, at)
                frame()
                scene.sendPointerEvent(PointerEventType.Press, at, buttons = pressed, button = button)
                scene.sendPointerEvent(PointerEventType.Release, at, buttons = PointerButtons(), button = button)
                repeat(3) { frame() }
            }
            repeat(8) { n ->
                Thread.sleep(90)
                Snapshot.sendApplyNotifications()
                time += 100_000_000L
                image = scene.render(time)
            }
            File(out, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            println("$name.png")
        } finally {
            scene.close()
        }
    }

    /** Stille WAV-Datei, 8 kHz, 8 Bit, mono. */
    private fun wav(f: File, seconds: Int) {
        val rate = 8000
        val n = rate * seconds
        DataOutputStream(f.outputStream().buffered()).use { o ->
            fun le32(v: Int) = o.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = o.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            o.writeBytes("RIFF"); le32(36 + n); o.writeBytes("WAVE")
            o.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(rate); le32(rate); le16(1); le16(8)
            o.writeBytes("data"); le32(n)
            val chunk = ByteArray(8000) { 0x80.toByte() }
            var left = n
            while (left > 0) {
                val c = minOf(left, chunk.size)
                o.write(chunk, 0, c)
                left -= c
            }
        }
    }

    /** Schlichtes Titelbild: Verlauf, ein paar Formen, Titel und Autor. */
    private fun coverArt(b: DemoBook, hue: Float): ByteArray {
        val size = 600
        val s = Surface.makeRasterN32Premul(size, size)
        val c = s.canvas
        fun hsl(h: Float, sat: Float, l: Float): Int {
            val col = app.simpleaudiobooks.ui.theme.Palettes.hsl(h, sat, l)
            return org.jetbrains.skia.Color.makeARGB(255, (col.red * 255).toInt(), (col.green * 255).toInt(), (col.blue * 255).toInt())
        }
        c.drawPaint(Paint().apply {
            shader = Shader.makeLinearGradient(Point(0f, 0f), Point(size.toFloat(), size.toFloat()), intArrayOf(hsl(hue, 0.45f, 0.42f), hsl(hue + 25, 0.5f, 0.2f)))
        })
        val light = Paint().apply { color = hsl(hue + 180, 0.5f, 0.75f); alpha = 70 }
        c.drawCircle(size * 0.72f, size * 0.62f, size * 0.32f, light)
        c.drawCircle(size * 0.28f, size * 0.8f, size * 0.14f, Paint().apply { color = hsl(hue + 40, 0.6f, 0.65f); alpha = 90 })
        val line = Paint().apply { color = 0x55FFFFFF; mode = PaintMode.STROKE; strokeWidth = 3f }
        c.drawLine(60f, 300f, size - 60f, 300f, line)
        val typeface: Typeface = FontMgr.default.matchFamilyStyle("Georgia", FontStyle.BOLD) ?: FontMgr.default.matchFamilyStyle(null, FontStyle.BOLD)!!
        val plain: Typeface = FontMgr.default.matchFamilyStyle("Georgia", FontStyle.NORMAL) ?: typeface
        val white = Paint().apply { color = 0xFFFFFFFF.toInt() }
        val titleFont = Font(typeface, 54f)
        var y = 120f
        for (row in wrap(b.title, titleFont, size - 120f)) {
            val w = titleFont.measureTextWidth(row)
            c.drawString(row, (size - w) / 2, y, titleFont, white)
            y += 64f
        }
        val authorFont = Font(plain, 30f)
        val w = authorFont.measureTextWidth(b.author)
        c.drawString(b.author, (size - w) / 2, size - 60f, authorFont, Paint().apply { color = 0xDDFFFFFF.toInt() })
        return s.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 88)!!.bytes
    }

    private fun wrap(text: String, font: Font, max: Float): List<String> {
        val rows = ArrayList<String>()
        var row = ""
        for (word in text.split(' ')) {
            val next = if (row.isEmpty()) word else "$row $word"
            if (font.measureTextWidth(next) > max && row.isNotEmpty()) {
                rows += row
                row = word
            } else {
                row = next
            }
        }
        if (row.isNotEmpty()) rows += row
        return rows
    }
}
