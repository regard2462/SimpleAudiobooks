package app.simpleaudiobooks.desktop.ui

import app.simpleaudiobooks.data.Entry
import app.simpleaudiobooks.data.Status
import app.simpleaudiobooks.desktop.DFormat
import app.simpleaudiobooks.desktop.DesktopApp
import app.simpleaudiobooks.desktop.Tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Frame
import java.io.File

/** Ein offener Dialog zu einem Buch. */
sealed interface BookDialog {
    val id: String

    data class Edit(override val id: String) : BookDialog
    data class Reset(override val id: String) : BookDialog
    data class Remove(override val id: String) : BookDialog
    data class CoverSearch(override val id: String) : BookDialog
    data class BookmarkNote(override val id: String, val bookmarkId: Long, val note: String) : BookDialog
}

/** Ein Eintrag im Menü eines Buches. */
data class MenuAction(val label: String, val run: () -> Unit)

/** Aktionen zu Büchern, gemeinsam für Raster, Liste und Detailbereich. */
class BookActions(
    private val app: DesktopApp,
    private val ui: UiState,
    private val scope: CoroutineScope,
    private val window: () -> Frame?,
    private val notify: (String) -> Unit,
) {
    /** Offener Dialog; liegt im [UiState], damit auch Tastenkürzel und Tests ihn sehen. */
    var dialog: BookDialog?
        get() = ui.dialog
        set(value) {
            ui.dialog = value
        }

    fun menu(e: Entry): List<MenuAction> {
        val lib = app.library
        val playing = app.player.isPlaying && app.player.currentBookId == e.id
        val list = ArrayList<MenuAction>()
        list += MenuAction(
            Tr.get(
                when {
                    playing -> "pause"
                    e.status == Status.NEW -> "play"
                    e.status == Status.STARTED -> "resume"
                    else -> "listen_again"
                },
            ),
        ) { if (playing) app.player.pause() else app.player.play(e.id) }
        list += MenuAction(Tr.get("details")) { ui.openBook(e.id) }
        list += if (e.status == Status.FINISHED) {
            MenuAction(Tr.get("mark_unfinished")) { lib.markFinished(e.id, false) }
        } else {
            MenuAction(Tr.get("mark_finished")) { lib.markFinished(e.id, true) }
        }
        if (e.status != Status.NEW) list += MenuAction(Tr.get("reset")) { dialog = BookDialog.Reset(e.id) }
        list += MenuAction(Tr.get("edit_book")) { dialog = BookDialog.Edit(e.id) }
        list += MenuAction(Tr.get("d_cover_from_file")) { chooseCover(e.id) }
        list += MenuAction(Tr.get("search_cover")) { dialog = BookDialog.CoverSearch(e.id) }
        if (e.state.customCover != null) list += MenuAction(Tr.get("reset_cover")) { lib.clearCustomCover(e.id) }
        if (e.webCover != null) list += MenuAction(Tr.get("remove_web_cover")) { lib.dropWebCover(e.id) }
        list += MenuAction(Tr.get("d_show_in_folder")) { showInFolder(e) }
        list += MenuAction(Tr.get("remove_book")) { dialog = BookDialog.Remove(e.id) }
        return list
    }

    private fun chooseCover(id: String) {
        val file = FileDialogs.openFile(window(), Tr.get("d_choose_image"), listOf("jpg", "jpeg", "png", "webp")) ?: return
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() }.getOrNull() }
            val ok = bytes != null && app.library.setCustomCoverBytes(id, bytes)
            notify(Tr.get(if (ok) "cover_saved" else "cover_save_failed"))
        }
    }

    private fun showInFolder(e: Entry) {
        val target = File(e.book.root, e.book.path).let { if (it.isFile) it.parentFile else it }
        runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(target) }
    }

    fun addBookmark() {
        val id = app.player.currentBookId ?: return
        val pos = app.player.globalPosition()
        app.library.addBookmark(id, pos)
        notify(Tr.get("bookmark_added", DFormat.clock(pos)))
    }
}
