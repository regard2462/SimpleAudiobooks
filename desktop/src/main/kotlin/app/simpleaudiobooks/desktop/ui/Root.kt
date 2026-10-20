package app.simpleaudiobooks.desktop.ui

import androidx.compose.foundation.DefaultContextMenuRepresentation
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.simpleaudiobooks.data.Filter
import app.simpleaudiobooks.desktop.DesktopApp
import app.simpleaudiobooks.desktop.IconArt
import app.simpleaudiobooks.desktop.tr
import app.simpleaudiobooks.desktop.trPlural
import kotlinx.coroutines.launch

enum class Page { LIBRARY, STATS, SETTINGS }

/** Zustand der Oberfläche, den auch Tastenkürzel brauchen. */
class UiState {
    var page by mutableStateOf(Page.LIBRARY)
    var selected by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var filter by mutableStateOf(Filter.ALL)
    var dialog by mutableStateOf<BookDialog?>(null)
    val searchFocus = FocusRequester()

    /** Zählt hoch, wenn das Suchfeld den Fokus bekommen soll (Strg+F). */
    var focusSearch by mutableStateOf(0)

    /** Lesezeichen an der aktuellen Stelle; setzt die Oberfläche. */
    var bookmark: () -> Unit = {}

    private var lastClickId: String? = null
    private var lastClickAt = 0L

    fun openBook(id: String) {
        page = Page.LIBRARY
        selected = id
    }

    /** Einfacher Klick wählt ein Buch, ein zweiter kurz danach spielt es. */
    fun click(id: String, onDouble: () -> Unit) {
        val now = System.currentTimeMillis()
        if (lastClickId == id && now - lastClickAt < 450) {
            lastClickId = null
            onDouble()
        } else {
            lastClickId = id
            lastClickAt = now
            selected = id
        }
    }
}

val LocalUi = staticCompositionLocalOf<UiState> { error("UiState fehlt") }
val LocalWindow = staticCompositionLocalOf<ComposeWindow?> { null }
val LocalActions = staticCompositionLocalOf<BookActions> { error("BookActions fehlt") }

@Composable
fun AppRoot(app: DesktopApp, ui: UiState, window: ComposeWindow?) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = remember { { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } } }
    val actions = remember { BookActions(app, ui, scope, { window }, notify) }
    SideEffect { ui.bookmark = actions::addBookmark }
    val colors = MaterialTheme.colorScheme
    val menuLook = remember(colors) {
        DefaultContextMenuRepresentation(
            backgroundColor = colors.surfaceContainerHigh,
            textColor = colors.onSurface,
            itemHoverColor = colors.onSurface.copy(alpha = 0.08f),
        )
    }
    CompositionLocalProvider(
        LocalApp provides app,
        LocalUi provides ui,
        LocalNotify provides notify,
        LocalWindow provides window,
        LocalActions provides actions,
        LocalContextMenuRepresentation provides menuLook,
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Sidebar(ui)
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            when (ui.page) {
                                Page.LIBRARY -> LibraryPane()
                                Page.STATS -> StatsPane()
                                Page.SETTINGS -> SettingsPane()
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    PlayerBar()
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp)) { data ->
                    Snackbar(data, shape = RoundedCornerShape(12.dp))
                }
            }
            BookDialogHost(actions)
        }
    }
}

@Composable
private fun Sidebar(ui: UiState) {
    val app = LocalApp.current
    val entries by app.library.entries.collectAsState()
    val scan by app.library.scan.collectAsState()
    val covers by app.onlineCovers.progress.collectAsState()
    val icon = remember { IconArt.render(96).toComposeImageBitmap() }
    Column(
        Modifier.width(248.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
            Image(icon, null, Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Text(tr("app_name"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(24.dp))
        NavItem(Icons.AutoMirrored.Rounded.LibraryBooks, tr("library"), ui.page == Page.LIBRARY, entries.size.takeIf { it > 0 }?.toString()) { ui.page = Page.LIBRARY }
        NavItem(Icons.Rounded.BarChart, tr("statistics"), ui.page == Page.STATS) { ui.page = Page.STATS }
        NavItem(Icons.Rounded.Settings, tr("settings"), ui.page == Page.SETTINGS) { ui.page = Page.SETTINGS }
        Spacer(Modifier.weight(1f))
        val s = scan
        val c = covers
        when {
            s != null -> SideNote(if (s.total > 0) trPlural("scan_progress", s.total, s.done, s.total) else tr("scan_listing"))
            c != null -> SideNote(tr("web_covers_progress", c.done, c.total))
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, badge: String? = null, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (badge != null) Text(badge, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SideNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
