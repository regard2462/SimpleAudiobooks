package app.simpleaudiobooks.desktop

import app.simpleaudiobooks.data.Book
import app.simpleaudiobooks.data.LibraryPlatform
import java.awt.Desktop
import java.io.File

/** Bibliotheksordner im Dateisystem; Löschen geht, wo das System es kann, in den Papierkorb. */
class DesktopPlatform(dataDir: File) : LibraryPlatform {

    override val covers = SkiaCovers(File(dataDir, "covers"))
    override val scanner = FileScanner(covers)

    override fun deleteBookFiles(book: Book): Boolean {
        val ownFolder = book.path.isNotEmpty() && !(book.tracks.size == 1 && book.tracks[0].path == book.path.substringAfterLast('/'))
        val targets = if (ownFolder) listOf(File(book.root, book.path)) else book.tracks.map { File(it.uri) }
        return targets.all { remove(it) }
    }

    private fun remove(f: File): Boolean {
        if (!f.exists()) return true
        val trash = runCatching {
            Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH) &&
                Desktop.getDesktop().moveToTrash(f)
        }.getOrDefault(false)
        return trash || f.deleteRecursively()
    }
}
