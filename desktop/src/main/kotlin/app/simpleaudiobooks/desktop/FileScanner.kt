package app.simpleaudiobooks.desktop

import app.simpleaudiobooks.data.CoverStore
import app.simpleaudiobooks.data.LibraryRoot
import app.simpleaudiobooks.scan.BookScanner
import app.simpleaudiobooks.scan.FsDir
import app.simpleaudiobooks.scan.FsFile
import app.simpleaudiobooks.scan.Tags
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/** Liest Bibliotheksordner im Dateisystem; Bücher baut [BookScanner], Tags und Dauer liefert FFmpeg. */
class FileScanner(covers: CoverStore) : BookScanner(covers) {

    override fun listTree(root: LibraryRoot): FsDir? {
        val dir = File(root.uri)
        if (!dir.isDirectory) return null
        return walk(dir, 0)
    }

    private fun walk(dir: File, depth: Int): FsDir {
        val files = ArrayList<FsFile>()
        val dirs = ArrayList<FsDir>()
        for (f in dir.listFiles().orEmpty()) {
            if (f.name.startsWith(".") || f.isHidden) continue
            if (f.isDirectory) {
                if (depth < 10) dirs += walk(f, depth + 1)
            } else if (f.isFile) {
                files += FsFile(f.absolutePath, f.name, f.length(), f.lastModified())
            }
        }
        return FsDir(dir.absolutePath, dir.name, files, dirs)
    }

    override fun fileUri(root: LibraryRoot, file: FsFile): String = file.id

    override fun metadata(uri: String): Pair<Long, Tags> {
        val info = Ffmpeg.probe(uri) ?: return 0L to Tags()
        val t = info.tags
        return info.durationMs to Tags(
            title = t["title"],
            album = t["album"],
            artist = t["artist"],
            albumArtist = t["album_artist"] ?: t["albumartist"] ?: t["album artist"],
            composer = t["composer"] ?: t["narrator"] ?: t["writer"],
            year = (t["date"] ?: t["year"])?.take(4),
        )
    }

    override fun <R> withChannel(uri: String, block: (FileChannel) -> R): R? =
        RandomAccessFile(uri, "r").use { block(it.channel) }

    override fun embeddedPicture(uri: String): ByteArray? = Ffmpeg.probe(uri, withPicture = true)?.picture

    override fun readBytes(root: LibraryRoot, file: FsFile, maxBytes: Int): ByteArray? {
        val f = File(file.id)
        if (!f.isFile || f.length() > maxBytes) return null
        return runCatching { f.readBytes() }.getOrNull()
    }
}
