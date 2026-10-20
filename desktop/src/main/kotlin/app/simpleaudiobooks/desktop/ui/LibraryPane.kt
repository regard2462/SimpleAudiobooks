package app.simpleaudiobooks.desktop.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.simpleaudiobooks.data.Entry
import app.simpleaudiobooks.data.Filter
import app.simpleaudiobooks.data.SortOrder
import app.simpleaudiobooks.data.Status
import app.simpleaudiobooks.data.matches
import app.simpleaudiobooks.data.matchesQuery
import app.simpleaudiobooks.data.sortEntries
import app.simpleaudiobooks.desktop.DFormat
import app.simpleaudiobooks.desktop.IconArt
import app.simpleaudiobooks.desktop.tr
import app.simpleaudiobooks.desktop.trPlural

@Composable
fun LibraryPane() {
    val app = LocalApp.current
    val ui = LocalUi.current
    val settings by app.settings.flow.collectAsState()
    val entries by app.library.entries.collectAsState()
    val scan by app.library.scan.collectAsState()
    val unreachable by app.library.unreachableRoots.collectAsState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelWidth = if (maxWidth > 1100.dp) 400.dp else 340.dp
        // Bei schmalem Fenster liegt der Detailbereich über der Bibliothek statt daneben
        val overlay = maxWidth < 900.dp
        val selected = ui.selected?.let { id -> entries.firstOrNull { it.id == id } }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (settings.roots.isNotEmpty()) LibraryHeader(entries.size, settings.sort, settings.grid, scan != null)
                for (root in unreachable) UnreachableNote(root)
                when {
                    settings.roots.isEmpty() -> Welcome()
                    entries.isEmpty() && scan != null -> FirstScan()
                    entries.isEmpty() -> CenterNote(tr("no_books_found"))
                    else -> LibraryContent(entries, settings.sort, settings.grid, settings.hideFinished)
                }
            }
            if (selected != null && !overlay) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                BookPanel(selected, Modifier.width(panelWidth).fillMaxHeight())
            }
        }
        if (selected != null && overlay) {
            Surface(shadowElevation = 16.dp, modifier = Modifier.align(Alignment.CenterEnd).width(panelWidth).fillMaxHeight()) {
                BookPanel(selected, Modifier.fillMaxSize())
            }
        }
    }
}

// ---------------------------------------------------------------- Kopfzeile

@Composable
private fun LibraryHeader(count: Int, sort: SortOrder, grid: Boolean, scanning: Boolean) {
    val app = LocalApp.current
    val ui = LocalUi.current
    LaunchedEffect(ui.focusSearch) {
        if (ui.focusSearch > 0) {
            runCatching { ui.searchFocus.requestFocus() }
            ui.focusSearch = 0
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(tr("library"), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text(trPlural("books", count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.weight(1f).widthIn(min = 16.dp))
        SearchBox(ui.query, { ui.query = it }, tr("search_hint"), ui.searchFocus, Modifier.weight(4f, fill = false).widthIn(max = 320.dp).width(300.dp))
        Spacer(Modifier.width(8.dp))
        SortButton(sort)
        Tip(tr(if (grid) "view_list" else "view_grid")) {
            IconButton(onClick = { app.settings.update { it.copy(grid = !grid) } }) {
                Icon(if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView, tr("view"))
            }
        }
        Tip(tr("rescan_desc")) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (scanning) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                } else {
                    IconButton(onClick = { app.library.rescan() }) { Icon(Icons.Rounded.Refresh, tr("rescan")) }
                }
            }
        }
    }
}

@Composable
private fun SortButton(sort: SortOrder) {
    val app = LocalApp.current
    var open by remember { mutableStateOf(false) }
    Box {
        Tip(tr("sort_by")) {
            IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, tr("sort")) }
        }
        DropdownMenu(open, { open = false }) {
            Text(tr("sort_by"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            for (o in SortOrder.entries) {
                DropdownMenuItem(
                    text = { Text(tr(o.labelKey())) },
                    onClick = {
                        app.settings.update { it.copy(sort = o) }
                        open = false
                    },
                    trailingIcon = { if (o == sort) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                )
            }
        }
    }
}

fun SortOrder.labelKey(): String = when (this) {
    SortOrder.RECENT -> "sort_recent"
    SortOrder.TITLE -> "sort_title"
    SortOrder.AUTHOR -> "sort_author"
    SortOrder.ADDED -> "sort_added"
    SortOrder.LENGTH -> "sort_length"
    SortOrder.PROGRESS -> "sort_progress"
}

private fun Filter.labelKey(): String = when (this) {
    Filter.ALL -> "filter_all"
    Filter.STARTED -> "filter_started"
    Filter.NEW -> "filter_new"
    Filter.FINISHED -> "filter_finished"
}

@Composable
private fun UnreachableNote(root: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(tr("unreachable", root), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---------------------------------------------------------------- Inhalt

@Composable
private fun LibraryContent(entries: List<Entry>, sort: SortOrder, grid: Boolean, hideFinished: Boolean) {
    val ui = LocalUi.current
    val filter = ui.filter
    val query = ui.query
    val shown = remember(entries, sort, filter, query, hideFinished) {
        sortEntries(entries.filter { it.matches(filter, hideFinished && query.isBlank()) && it.matchesQuery(query) }, sort)
    }
    val counts = remember(entries, hideFinished) { Filter.entries.associateWith { f -> entries.count { it.matches(f, hideFinished) } } }
    val continueList = remember(entries) {
        entries.filter { it.status == Status.STARTED }.sortedByDescending { it.state.lastPlayedAt }.take(8)
    }
    val showContinue = filter == Filter.ALL && query.isBlank() && continueList.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (f in Filter.entries) {
                FilterChip(
                    selected = filter == f,
                    onClick = { ui.filter = f },
                    label = { Text(tr(f.labelKey()) + "  " + counts[f]) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                )
            }
        }
        if (shown.isEmpty() && !showContinue) {
            CenterNote(tr(if (query.isNotBlank()) "no_search_results" else "no_books_filter"))
            return@Column
        }
        Box(Modifier.fillMaxSize()) {
            if (grid) {
                val state = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(168.dp),
                    state = state,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (showContinue) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "continue") { ContinueRow(continueList) }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "all-title") { SectionTitle(tr("filter_all")) }
                    }
                    items(shown, key = { it.id }) { e -> GridCard(e) }
                }
                VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
            } else {
                val state = rememberLazyListState()
                LazyColumn(
                    state = state,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (showContinue) {
                        item(key = "continue") { ContinueRow(continueList) }
                        item(key = "all-title") { SectionTitle(tr("filter_all")) }
                    }
                    items(shown, key = { it.id }) { e -> ListRow(e) }
                }
                VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp))
}

/** Menü per Rechtsklick mit denselben Aktionen wie im Detailbereich. */
@Composable
private fun BookContextMenu(e: Entry, content: @Composable () -> Unit) {
    val actions = LocalActions.current
    ContextMenuArea(items = { actions.menu(e).map { ContextMenuItem(it.label, it.run) } }, content = content)
}

/** Ob das Buch gerade geladen ist und ob es läuft; zeichnet nur bei einer Änderung davon neu. */
@Composable
fun isCurrent(e: Entry): Pair<Boolean, Boolean> {
    val app = LocalApp.current
    val snap = app.player.snapshot.collectAsState()
    val v by remember(e.id) {
        derivedStateOf {
            val s = snap.value
            (s.bookId == e.id) to (s.bookId == e.id && s.playing)
        }
    }
    return v
}

// ---------------------------------------------------------------- Weiterhören

@Composable
private fun ContinueRow(list: List<Entry>) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(tr("continue_listening"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            items(list, key = { it.id }) { e -> ContinueCard(e) }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ContinueCard(e: Entry) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val (_, playing) = isCurrent(e)
    BookContextMenu(e) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (ui.selected == e.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.width(320.dp).height(104.dp).clip(RoundedCornerShape(16.dp)).clickable { ui.click(e.id) { app.player.play(e.id) } },
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCover(e, Modifier.size(80.dp), RoundedCornerShape(10.dp), imagePx = 200)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    e.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.height(8.dp))
                    ThinProgress(e.progress)
                    Spacer(Modifier.height(4.dp))
                    Text(statusLine(e), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = { if (playing) app.player.pause() else app.player.play(e.id) },
                    modifier = Modifier.size(40.dp),
                ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr(if (playing) "pause" else "resume")) }
            }
        }
    }
}

// ---------------------------------------------------------------- Raster

@Composable
private fun GridCard(e: Entry) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val (current, playing) = isCurrent(e)
    val selected = ui.selected == e.id
    BookContextMenu(e) {
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .hoverable(interaction)
                .clickable { ui.click(e.id) { app.player.play(e.id) } }
                .padding(8.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                BookCover(e, Modifier.fillMaxSize(), RoundedCornerShape(10.dp), elevation = if (hovered) 6.dp else 2.dp)
                if (e.status == Status.FINISHED) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.CheckCircle, tr("status_finished"), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
                }
                if (hovered || current) {
                    FilledIconButton(
                        onClick = { if (playing) app.player.pause() else app.player.play(e.id) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(44.dp),
                    ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr(if (playing) "pause" else "play"), Modifier.size(26.dp)) }
                }
                if (e.status == Status.STARTED) {
                    LinearProgressIndicator(
                        progress = { e.progress },
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 0.dp).height(4.dp).clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)),
                        trackColor = Color.Black.copy(alpha = 0.35f),
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                e.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(e.author.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(statusLine(e), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ---------------------------------------------------------------- Liste

@Composable
private fun ListRow(e: Entry) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val (current, playing) = isCurrent(e)
    val selected = ui.selected == e.id
    BookContextMenu(e) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .clickable { ui.click(e.id) { app.player.play(e.id) } }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(e, Modifier.size(52.dp), RoundedCornerShape(8.dp), imagePx = 160)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    e.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    listOfNotNull(e.author, e.narrator?.let { tr("read_by", it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                DFormat.duration(e.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(110.dp),
            )
            Column(Modifier.width(170.dp)) {
                if (e.status == Status.STARTED) ThinProgress(e.progress)
                Text(statusLine(e), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { if (playing) app.player.pause() else app.player.play(e.id) }) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr(if (playing) "pause" else "play"), tint = MaterialTheme.colorScheme.primary)
            }
            BookMoreButton(e)
        }
    }
}

/** Drei Punkte mit dem Menü eines Buches. */
@Composable
fun BookMoreButton(e: Entry) {
    val actions = LocalActions.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
            Icon(Icons.Rounded.MoreVert, tr("more"))
        }
        DropdownMenu(open, { open = false }) {
            for (a in actions.menu(e)) {
                DropdownMenuItem(text = { Text(a.label) }, onClick = {
                    open = false
                    a.run()
                })
            }
        }
    }
}

// ---------------------------------------------------------------- Leere Zustände

@Composable
private fun CenterNote(text: String) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
    }
}

@Composable
private fun FirstScan() {
    val app = LocalApp.current
    val scan by app.library.scan.collectAsState()
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 480.dp)) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
            Text(tr("scanning"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(tr("scanning_first"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            val s = scan
            if (s != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (s.total > 0) trPlural("scan_progress", s.total, s.done, s.total) else tr("scan_listing"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Welcome() {
    val app = LocalApp.current
    val addFolder = rememberAddFolder()
    val io = rememberImportExport { setLanguage(app, it) }
    val icon = remember { IconArt.render(256).toComposeImageBitmap() }
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 560.dp)) {
            Image(icon, null, Modifier.size(112.dp))
            Spacer(Modifier.height(24.dp))
            Text(tr("welcome_title"), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(tr("welcome_text"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = addFolder, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
                    Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(tr("choose_folder"))
                }
                OutlinedButton(onClick = io.import, contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)) {
                    Icon(Icons.Rounded.UploadFile, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(tr("welcome_import"))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(tr("welcome_privacy"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Kleiner Textknopf für Kopfzeilen. */
@Composable
fun SmallTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text(text) }
}
