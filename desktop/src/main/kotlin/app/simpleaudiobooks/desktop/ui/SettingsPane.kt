package app.simpleaudiobooks.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.simpleaudiobooks.data.ColorPalette
import app.simpleaudiobooks.data.ThemeMode
import app.simpleaudiobooks.desktop.DFormat
import app.simpleaudiobooks.desktop.DesktopApp
import app.simpleaudiobooks.desktop.DesktopPaths
import app.simpleaudiobooks.desktop.Tr
import app.simpleaudiobooks.desktop.tr
import app.simpleaudiobooks.desktop.trPlural
import app.simpleaudiobooks.ui.theme.Palettes

@Composable
fun SettingsPane() {
    val app = LocalApp.current
    val notify = LocalNotify.current
    val settings by app.settings.flow.collectAsState()
    val prefs by app.settings.prefs.collectAsState()
    val unreachable by app.library.unreachableRoots.collectAsState()
    val hidden by app.library.hiddenEntries.collectAsState()
    val entries by app.library.entries.collectAsState()
    val scan by app.library.scan.collectAsState()
    val addFolder = rememberAddFolder()
    val io = rememberImportExport { setLanguage(app, it) }
    var removeRoot by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 28.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 820.dp).fillMaxWidth()) {
                Text(tr("settings"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 18.dp))

                SettingsSection(tr("section_library")) {
                    if (settings.roots.isEmpty()) {
                        Text(tr("d_folders_none"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                    }
                    for (root in settings.roots) {
                        val off = root.uri in unreachable
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (off) Icons.Rounded.FolderOff else Icons.Rounded.Folder, null, tint = if (off) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(root.uri, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val count = entries.count { it.book.root == root.uri }
                                Text(
                                    listOfNotNull(
                                        tr(if (root.single) "folder_type_single" else "folder_type_collection"),
                                        trPlural("books", count),
                                        if (off) tr("folder_unreachable") else null,
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (off) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { removeRoot = root.uri }) { Text(tr("remove")) }
                        }
                    }
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = addFolder) {
                            Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("add_folder"))
                        }
                        OutlinedButton(onClick = { app.library.rescan() }, enabled = scan == null && settings.roots.isNotEmpty()) { Text(tr("rescan")) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SwitchRow(tr("hide_finished"), null, settings.hideFinished) { on -> app.settings.update { it.copy(hideFinished = on) } }
                }

                if (hidden.isNotEmpty()) {
                    SettingsSection(tr("section_removed")) {
                        for (e in hidden) {
                            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                BookCover(e, Modifier.size(40.dp), RoundedCornerShape(6.dp), imagePx = 120)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    e.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                                }
                                TextButton(onClick = { app.library.restoreBook(e.id) }) { Text(tr("restore")) }
                            }
                        }
                    }
                }

                SettingsSection(tr("section_covers")) {
                    SwitchRow(tr("web_covers"), tr("web_covers_desc"), settings.webCovers) { on -> app.settings.update { it.copy(webCovers = on) } }
                    val count = remember(entries, hidden) { app.library.webCoverCount() }
                    SettingRow(trPlural("web_covers_count", count), null) {
                        TextButton(
                            onClick = {
                                app.library.clearWebCovers()
                                notify(Tr.get("web_covers_cleared"))
                            },
                            enabled = count > 0,
                        ) { Text(tr("web_covers_clear")) }
                    }
                }

                SettingsSection(tr("section_playback")) {
                    val secs = { n: Int -> Tr.get("seconds_n", n) }
                    ChoiceRow(tr("skip_back"), null, listOf(5, 10, 15, 20, 30, 45, 60), settings.skipBackSec, secs) { v -> app.settings.update { it.copy(skipBackSec = v) } }
                    ChoiceRow(tr("skip_forward"), null, listOf(5, 10, 15, 20, 30, 45, 60, 90), settings.skipForwardSec, secs) { v -> app.settings.update { it.copy(skipForwardSec = v) } }
                    ChoiceRow(
                        tr("resume_rewind"),
                        tr("resume_rewind_desc"),
                        listOf(0, 2, 3, 5, 10, 15, 20, 30),
                        settings.resumeRewindSec,
                        { n -> if (n == 0) Tr.get("off") else secs(n) },
                    ) { v -> app.settings.update { it.copy(resumeRewindSec = v) } }
                    SwitchRow(tr("smart_rewind"), tr("smart_rewind_desc"), settings.smartRewind, enabled = settings.resumeRewindSec > 0) { on ->
                        app.settings.update { it.copy(smartRewind = on) }
                    }
                    ChoiceRow(
                        tr("default_speed"),
                        tr("default_speed_desc"),
                        listOf(0.75f, 0.8f, 0.9f, 1.0f, 1.1f, 1.2f, 1.25f, 1.3f, 1.4f, 1.5f, 1.75f, 2.0f),
                        settings.defaultSpeed,
                        { DFormat.speed(it) },
                    ) { v -> app.settings.update { it.copy(defaultSpeed = v) } }
                    SwitchRow(tr("sleep_fade"), tr("sleep_fade_desc"), settings.sleepFade) { on -> app.settings.update { it.copy(sleepFade = on) } }
                }

                SettingsSection(tr("section_appearance")) {
                    SettingRow(tr("theme")) {
                        val modes = ThemeMode.entries
                        SingleChoiceSegmentedButtonRow {
                            modes.forEachIndexed { i, m ->
                                SegmentedButton(
                                    selected = settings.theme == m,
                                    onClick = { app.settings.update { it.copy(theme = m) } },
                                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                ) {
                                    Text(
                                        tr(
                                            when (m) {
                                                ThemeMode.SYSTEM -> "theme_system"
                                                ThemeMode.LIGHT -> "theme_light"
                                                ThemeMode.DARK -> "theme_dark"
                                            },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    PaletteRow(if (settings.palette == ColorPalette.WALLPAPER) ColorPalette.NIGHT else settings.palette) { p -> app.settings.update { it.copy(palette = p) } }
                    SwitchRow(tr("pure_black"), tr("pure_black_desc"), settings.pureBlack, enabled = settings.theme != ThemeMode.LIGHT) { on ->
                        app.settings.update { it.copy(pureBlack = on) }
                    }
                    ChoiceRow(
                        tr("language"),
                        null,
                        listOf("", "de", "en"),
                        prefs.language,
                        { when (it) { "de" -> Tr.get("language_de"); "en" -> Tr.get("language_en"); else -> Tr.get("language_system") } },
                    ) { setLanguage(app, it) }
                }

                SettingsSection(tr("section_data")) {
                    SettingRow(tr("backup_export"), tr("backup_export_desc")) { OutlinedButton(onClick = io.export) { Text(tr("backup_export")) } }
                    SettingRow(tr("backup_import"), tr("backup_import_desc")) { OutlinedButton(onClick = io.import) { Text(tr("backup_import")) } }
                }

                SettingsSection(tr("d_shortcuts")) {
                    val mod = if (DesktopPaths.isMac) "⌘" else "Ctrl"
                    Shortcut(tr("d_key_space"), tr("d_sc_play"))
                    Shortcut(tr("d_key_arrows"), tr("d_sc_skip"))
                    Shortcut(tr("d_key_shift_arrows"), tr("d_sc_chapter"))
                    Shortcut("$mod + F", tr("d_sc_search"))
                    Shortcut("$mod + B", tr("d_sc_bookmark"))
                    Shortcut("Esc", tr("d_sc_close"))
                }

                SettingsSection(tr("section_about")) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text("${tr("app_name")} ${DesktopApp.VERSION}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(tr("about_text"), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(tr("d_about_licenses"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }

    removeRoot?.let { uri ->
        AlertDialog(
            onDismissRequest = { removeRoot = null },
            title = { Text(tr("remove_folder")) },
            text = { Text(tr("remove_folder_text", uri)) },
            confirmButton = {
                TextButton(onClick = {
                    app.library.removeRoot(uri)
                    removeRoot = null
                }) { Text(tr("remove")) }
            },
            dismissButton = { TextButton(onClick = { removeRoot = null }) { Text(tr("cancel")) } },
        )
    }
}

/** Auswahl aus festen Werten als Knopf mit aufklappender Liste. */
@Composable
private fun <T> ChoiceRow(title: String, subtitle: String?, options: List<T>, value: T, label: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, subtitle, onClick = { open = true }) {
        Box {
            OutlinedButton(onClick = { open = true }, contentPadding = PaddingValues(start = 14.dp, end = 8.dp)) {
                Text(label(value))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.UnfoldMore, null, Modifier.size(18.dp))
            }
            DropdownMenu(open, { open = false }) {
                for (o in options) {
                    DropdownMenuItem(
                        text = { Text(label(o)) },
                        onClick = {
                            open = false
                            onSelect(o)
                        },
                        trailingIcon = { if (o == value) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Shortcut(keys: String, action: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(150.dp),
        ) {
            Text(
                keys,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(action, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Farbschemata als runde Felder: außen die helle, innen die dunkle Hauptfarbe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteRow(selected: ColorPalette, onSelect: (ColorPalette) -> Unit) {
    val options = ColorPalette.entries.filter { it != ColorPalette.WALLPAPER }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(tr("color_scheme"), style = MaterialTheme.typography.bodyLarge)
        Text(tr(selected.labelKey()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (p in options) {
                val (outer, inner) = remember(p) { Palettes.swatch(p) }
                val isSelected = p == selected
                val name = tr(p.labelKey())
                Tip(name) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(outer)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(p) }
                            .semantics { contentDescription = name },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(inner), contentAlignment = Alignment.Center) {
                            if (isSelected) Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = outer)
                        }
                    }
                }
            }
        }
    }
}

private fun ColorPalette.labelKey(): String = "palette_" + name.lowercase()
