package app.simpleaudiobooks.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.simpleaudiobooks.data.Backup
import app.simpleaudiobooks.data.BackupFile
import app.simpleaudiobooks.data.SettingsBackup
import app.simpleaudiobooks.desktop.DFormat
import app.simpleaudiobooks.desktop.Tr
import app.simpleaudiobooks.desktop.tr
import app.simpleaudiobooks.desktop.trPlural
import app.simpleaudiobooks.online.CoverCandidate
import app.simpleaudiobooks.online.OnlineCovers
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Zeigt den offenen Buch-Dialog von [actions]. */
@Composable
fun BookDialogHost(actions: BookActions) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val d = actions.dialog ?: return
    val close = { actions.dialog = null }
    val entry = app.library.entry(d.id)
    if (entry == null) {
        LaunchedEffect(d) { close() }
        return
    }
    when (d) {
        is BookDialog.Edit -> {
            var title by remember(d) { mutableStateOf(entry.title) }
            var author by remember(d) { mutableStateOf(entry.author.orEmpty()) }
            var narrator by remember(d) { mutableStateOf(entry.narrator.orEmpty()) }
            AlertDialog(
                onDismissRequest = close,
                title = { Text(tr("edit_book")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(title, { title = it }, label = { Text(tr("field_title")) }, singleLine = true, modifier = Modifier.tracksTextFocus())
                        OutlinedTextField(author, { author = it }, label = { Text(tr("field_author")) }, singleLine = true, modifier = Modifier.tracksTextFocus())
                        OutlinedTextField(narrator, { narrator = it }, label = { Text(tr("field_narrator")) }, singleLine = true, modifier = Modifier.tracksTextFocus())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        app.library.editMeta(entry.id, title, author, narrator)
                        close()
                    }) { Text(tr("save")) }
                },
                dismissButton = { TextButton(onClick = close) { Text(tr("cancel")) } },
            )
        }
        is BookDialog.Reset -> AlertDialog(
            onDismissRequest = close,
            title = { Text(tr("reset_title")) },
            text = { Text(tr("reset_text", entry.title)) },
            confirmButton = {
                TextButton(onClick = {
                    app.library.resetProgress(entry.id)
                    if (app.player.currentBookId == entry.id) app.player.seekTo(0)
                    close()
                }) { Text(tr("reset")) }
            },
            dismissButton = { TextButton(onClick = close) { Text(tr("cancel")) } },
        )
        is BookDialog.Remove -> {
            var deleteFiles by remember(d) { mutableStateOf(false) }
            var busy by remember(d) { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { if (!busy) close() },
                title = { Text(tr("remove_book_title")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (deleteFiles) tr("remove_book_text_delete", entry.title) else tr("remove_book_text", entry.title))
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).clickable { deleteFiles = !deleteFiles }.padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(deleteFiles, { deleteFiles = it })
                            Text(tr("remove_book_delete_files"))
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            app.player.unload(entry.id)
                            if (ui.selected == entry.id) ui.selected = null
                            if (deleteFiles) {
                                busy = true
                                scope.launch {
                                    val ok = app.library.deleteBookFiles(entry.id)
                                    notify(if (ok) Tr.get("remove_book_deleted", entry.title) else Tr.get("remove_book_delete_failed"))
                                    close()
                                }
                            } else {
                                app.library.hideBook(entry.id)
                                notify(Tr.get("remove_book_done", entry.title))
                                close()
                            }
                        },
                    ) {
                        Text(
                            tr(if (deleteFiles) "delete" else "remove"),
                            color = if (deleteFiles) MaterialTheme.colorScheme.error else Color.Unspecified,
                        )
                    }
                },
                dismissButton = { TextButton(onClick = close, enabled = !busy) { Text(tr("cancel")) } },
            )
        }
        is BookDialog.CoverSearch -> CoverSearchDialog(entry.id, entry.title, entry.author, close)
        is BookDialog.BookmarkNote -> {
            var note by remember(d) { mutableStateOf(d.note) }
            AlertDialog(
                onDismissRequest = close,
                title = { Text(tr("bookmark_note")) },
                text = {
                    OutlinedTextField(note, { note = it }, placeholder = { Text(tr("bookmark_note_hint")) }, modifier = Modifier.fillMaxWidth().tracksTextFocus())
                },
                confirmButton = {
                    TextButton(onClick = {
                        app.library.editBookmark(entry.id, d.bookmarkId, note)
                        close()
                    }) { Text(tr("save")) }
                },
                dismissButton = { TextButton(onClick = close) { Text(tr("cancel")) } },
            )
        }
    }
}

// ---------------------------------------------------------------- Coversuche

@Composable
private fun CoverSearchDialog(id: String, title: String, author: String?, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(listOfNotNull(title, author).joinToString(" ")) }
    var round by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<OnlineCovers.SearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf<CoverCandidate?>(null) }

    LaunchedEffect(round) {
        if (query.isBlank()) return@LaunchedEffect
        loading = true
        result = app.onlineCovers.search(query, title, author)
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(760.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(tr("cover_search_title")) },
        text = {
            Column(Modifier.height(520.dp)) {
                OutlinedTextField(
                    query,
                    { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { round++ }),
                    modifier = Modifier.fillMaxWidth().tracksTextFocus(),
                )
                Text(tr("cover_search_note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                Box(Modifier.fillMaxSize()) {
                    val r = result
                    when {
                        loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        r is OnlineCovers.SearchResult.Offline -> Text(tr("cover_search_offline"), Modifier.align(Alignment.Center))
                        r is OnlineCovers.SearchResult.Found && r.candidates.isEmpty() -> Text(tr("cover_search_none"), Modifier.align(Alignment.Center))
                        r is OnlineCovers.SearchResult.Found -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(130.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(r.candidates, key = { it.imageUrl }) { c ->
                                Column(
                                    Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = saving == null) {
                                        saving = c
                                        scope.launch {
                                            val bytes = app.onlineCovers.download(c)
                                            val ok = bytes != null && app.library.setCustomCoverBytes(id, bytes)
                                            saving = null
                                            notify(Tr.get(if (ok) "cover_saved" else "cover_save_failed"))
                                            if (ok) onDismiss()
                                        }
                                    },
                                ) {
                                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                        rememberUrlImage(c.previewUrl, 300)?.let { Image(it, c.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                        if (saving == c) CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp))
                                    }
                                    Text(c.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                    Text(
                                        listOfNotNull(c.authors.firstOrNull(), c.source).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { round++ }) { Text(tr("search")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("close")) } },
    )
}

// ---------------------------------------------------------------- Ordner hinzufügen

/** Liefert eine Aktion, die einen Ordner wählen lässt und danach nach seiner Art fragt. */
@Composable
fun rememberAddFolder(): () -> Unit {
    val app = LocalApp.current
    val window = LocalWindow.current
    var pending by remember { mutableStateOf<String?>(null) }
    pending?.let { path ->
        var single by remember(path) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(tr("folder_type_title")) },
            text = {
                Column {
                    Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
                    TypeOption(!single, tr("folder_type_collection"), tr("folder_type_collection_desc")) { single = false }
                    TypeOption(single, tr("folder_type_single"), tr("folder_type_single_desc")) { single = true }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    app.library.addRoot(path, single)
                    pending = null
                }) { Text(tr("add_folder")) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(tr("cancel")) } },
        )
    }
    return {
        FileDialogs.chooseFolder(window, Tr.get("d_choose_folder_title"))?.let { pending = it.absolutePath }
    }
}

@Composable
private fun TypeOption(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected, null, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- Export und Import

class ImportExport(val export: () -> Unit, val import: () -> Unit)

@Composable
fun rememberImportExport(onLanguage: (String) -> Unit): ImportExport {
    val app = LocalApp.current
    val window = LocalWindow.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    var opened by remember { mutableStateOf<BackupFile?>(null) }

    opened?.let { file ->
        val hasBooks = file.books.isNotEmpty()
        val hasStats = !file.stats?.days.isNullOrEmpty()
        val hasSettings = file.settings != null
        var books by remember(file) { mutableStateOf(hasBooks) }
        var stats by remember(file) { mutableStateOf(hasStats) }
        var settings by remember(file) { mutableStateOf(hasSettings) }
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(tr("import_title")) },
            text = {
                Column {
                    Text(tr("import_from", DFormat.date(file.exportedAt)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (hasBooks) Choice(books, tr("import_books"), trPlural("books", file.books.size)) { books = it }
                    if (hasStats) Choice(stats, tr("statistics"), null) { stats = it }
                    if (hasSettings) Choice(settings, tr("settings"), tr("import_settings_desc")) { settings = it }
                }
            },
            confirmButton = {
                TextButton(enabled = books || stats || settings, onClick = {
                    opened = null
                    scope.launch {
                        val messages = ArrayList<String>()
                        if (books) {
                            val (done, waiting) = withContext(Dispatchers.IO) { app.library.importBooks(file.books) }
                            if (done > 0 || waiting == 0) messages += Tr.plural("backup_restored", done)
                            if (waiting > 0) messages += Tr.plural("import_waiting", waiting)
                        }
                        if (stats) file.stats?.let { app.stats.replace(Backup.mergeStats(app.stats.data.value, it)) }
                        val imported = file.settings
                        if (settings && imported != null) {
                            app.settings.update { imported.applyTo(it) }
                            imported.language?.takeIf { it in listOf("", "de", "en") }?.let(onLanguage)
                            messages += Tr.get("import_settings_done")
                        }
                        notify(messages.joinToString(" "))
                    }
                }) { Text(tr("import_do")) }
            },
            dismissButton = { TextButton(onClick = { opened = null }) { Text(tr("cancel")) } },
        )
    }

    return remember(window) {
        ImportExport(
            export = {
                FileDialogs.saveFile(window, Tr.get("d_export_title"), "simple-audiobooks-${LocalDate.now()}.json")?.let { target ->
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching {
                                val file = BackupFile(
                                    exportedAt = System.currentTimeMillis(),
                                    books = app.library.exportBooks(),
                                    stats = app.stats.data.value,
                                    settings = SettingsBackup.from(app.settings.value, app.settings.prefs.value.language),
                                )
                                target.writeText(Backup.encode(file))
                                true
                            }.getOrDefault(false)
                        }
                        notify(Tr.get(if (ok) "backup_saved" else "backup_failed"))
                    }
                }
            },
            import = {
                FileDialogs.openFile(window, Tr.get("d_import_title"), listOf("json"))?.let { source ->
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { runCatching { Backup.decode(source.readText()) }.getOrNull() }
                        if (file == null) notify(Tr.get("backup_invalid")) else opened = file
                    }
                }
            },
        )
    }
}

@Composable
private fun Choice(checked: Boolean, title: String, subtitle: String?, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onChange)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
