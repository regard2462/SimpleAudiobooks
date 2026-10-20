package app.simpleaudiobooks.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.simpleaudiobooks.data.Entry
import app.simpleaudiobooks.desktop.DFormat
import app.simpleaudiobooks.desktop.tr
import app.simpleaudiobooks.desktop.trPlural
import app.simpleaudiobooks.playback.SleepTimer
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPEED_PRESETS = listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.75f, 2.0f)
private val BOOST_STEPS = listOf(0, 3, 6, 9)
private val SLEEP_MINUTES = listOf(5, 10, 15, 20, 30, 45, 60, 90)

private fun roundSpeed(v: Float): Float = ((v * 20f).roundToInt() / 20f).coerceIn(0.5f, 3f)

/** Ziffern gleicher Breite, damit laufende Zeiten nicht springen. */
private const val TABULAR = "tnum"

/** Leiste unten im Fenster: laufendes Buch, Steuerung, Kapitelfortschritt, Tempo, Schlaftimer und Lautstärke. */
@Composable
fun PlayerBar() {
    val app = LocalApp.current
    val ui = LocalUi.current
    val actions = LocalActions.current
    val snap by app.player.snapshot.collectAsState()
    val entries by app.library.entries.collectAsState()
    val settings by app.settings.flow.collectAsState()
    val loaded = snap.bookId != null
    val entry: Entry? = snap.bookId?.let { id -> entries.firstOrNull { it.id == id } } ?: remember(entries) { app.library.lastPlayed() }
    val global = when {
        entry == null -> 0L
        loaded -> entry.book.globalOf(snap.trackIndex, snap.trackPositionMs)
        else -> entry.positionMs
    }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    Row(
        Modifier.fillMaxWidth().height(96.dp).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Links: Buch und Kapitel
        Row(Modifier.width(300.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry != null) {
                val chIdx = entry.book.chapterAt(global)
                val ch = entry.book.chapters.getOrNull(chIdx)
                Tip(tr("d_now_playing_book")) {
                    Row(
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable { ui.openBook(entry.id) }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BookCover(entry, Modifier.size(62.dp), RoundedCornerShape(8.dp), imagePx = 200)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.width(210.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (snap.error) {
                                Text(tr("playback_error"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                            } else {
                                Text(
                                    ch?.title ?: if (entry.book.chapters.size > 1) tr("chapter_of", chIdx + 1, entry.book.chapters.size) else entry.author.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (entry.book.chapters.size > 1) {
                                    Text(
                                        tr("chapter_of", chIdx + 1, entry.book.chapters.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(tr("d_nothing_playing"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
            }
        }

        // Mitte: Steuerung und Kapitelfortschritt
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tip(tr("previous_chapter")) {
                    IconButton(onClick = { app.player.previousChapter() }, enabled = loaded) { Icon(Icons.Rounded.SkipPrevious, tr("previous_chapter")) }
                }
                Tip(trPlural("skip_back_n", settings.skipBackSec)) {
                    IconButton(onClick = { app.player.skip(-settings.skipBackSec * 1000L) }, enabled = loaded) { SkipIcon(settings.skipBackSec, forward = false, size = 28.dp) }
                }
                Tip(tr("d_sc_play")) {
                    FilledIconButton(onClick = { app.player.togglePlay() }, enabled = entry != null, modifier = Modifier.size(48.dp)) {
                        Icon(if (snap.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr(if (snap.playing) "pause" else "play"), Modifier.size(30.dp))
                    }
                }
                Tip(trPlural("skip_forward_n", settings.skipForwardSec)) {
                    IconButton(onClick = { app.player.skip(settings.skipForwardSec * 1000L) }, enabled = loaded) { SkipIcon(settings.skipForwardSec, forward = true, size = 28.dp) }
                }
                Tip(tr("next_chapter")) {
                    IconButton(onClick = { app.player.nextChapter() }, enabled = loaded) { Icon(Icons.Rounded.SkipNext, tr("next_chapter")) }
                }
            }
            if (entry != null) {
                val book = entry.book
                val ch = book.chapters.getOrNull(book.chapterAt(global))
                val start = ch?.startMs ?: 0L
                val length = (ch?.durationMs ?: book.durationMs).coerceAtLeast(1)
                val inChapter = (global - start).coerceIn(0, length)
                val shownPos = dragFraction?.let { (it * length).toLong() } ?: inChapter
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TimeText(DFormat.clock(shownPos), TextAlign.End)
                    SeekBar(
                        fraction = inChapter.toFloat() / length,
                        onCommit = { f -> app.player.seekTo(start + (f * length).toLong()) },
                        onDrag = { dragFraction = it },
                        enabled = loaded,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    TimeText("-" + DFormat.clock(length - shownPos), TextAlign.Start)
                }
            }
        }

        // Rechts: Tempo, Schlaftimer, Lesezeichen, Lautstärke
        Row(Modifier.width(300.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            if (entry != null) SpeedButton(entry, snap.speed, loaded)
            SleepButton(entry, global, loaded)
            Tip(tr("d_sc_bookmark")) {
                IconButton(onClick = { actions.addBookmark() }, enabled = loaded) { Icon(Icons.Rounded.BookmarkAdd, tr("bookmark")) }
            }
            VolumeControl()
        }
    }
}

@Composable
private fun TimeText(text: String, align: TextAlign) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier.width(56.dp),
    )
}

// ---------------------------------------------------------------- Tempo

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedButton(entry: Entry, speed: Float, loaded: Boolean) {
    val app = LocalApp.current
    val settings by app.settings.flow.collectAsState()
    var open by remember { mutableStateOf(false) }
    val state = entry.state
    fun set(v: Float) = if (loaded) app.player.setSpeed(v) else app.library.setSpeed(entry.id, v)
    Box {
        Tip(tr("speed")) {
            TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(DFormat.speed(speed), style = MaterialTheme.typography.labelLarge)
            }
        }
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, (-8).dp), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.width(320.dp).padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(tr("speed"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    FilledTonalIconButton(onClick = { set(roundSpeed(speed - 0.05f)) }) { Icon(Icons.Rounded.Remove, tr("slower")) }
                    Text(DFormat.speed(speed), style = MaterialTheme.typography.headlineMedium)
                    FilledTonalIconButton(onClick = { set(roundSpeed(speed + 0.05f)) }) { Icon(Icons.Rounded.Add, tr("faster")) }
                }
                Spacer(Modifier.height(8.dp))
                SeekBar((speed - 0.5f) / 2.5f, onCommit = { set(roundSpeed(0.5f + it * 2.5f)) }, onChange = { set(roundSpeed(0.5f + it * 2.5f)) })
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in SPEED_PRESETS) Choice(DFormat.speed(p), abs(p - speed) < 0.001f) { set(p) }
                }
                if (abs(settings.defaultSpeed - speed) > 0.001f) {
                    TextButton(onClick = { app.settings.update { it.copy(defaultSpeed = speed) } }) { Text(tr("speed_as_default")) }
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("skip_silence"), style = MaterialTheme.typography.bodyLarge)
                        Text(tr("skip_silence_desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(state.skipSilence, { on -> if (loaded) app.player.setSkipSilence(on) else app.library.setSkipSilence(entry.id, on) })
                }
                Spacer(Modifier.height(12.dp))
                Text(tr("volume_boost"), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (db in BOOST_STEPS) {
                        Choice(if (db == 0) tr("off") else "+$db dB", state.boostDb == db) {
                            if (loaded) app.player.setBoost(db) else app.library.setBoost(entry.id, db)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primary else colors.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) colors.onPrimary else colors.onSurface)
    }
}

// ---------------------------------------------------------------- Schlaftimer

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepButton(entry: Entry?, position: Long, loaded: Boolean) {
    val app = LocalApp.current
    val timer by SleepTimer.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    val t = timer
    val label = when (t) {
        is SleepTimer.Countdown -> DFormat.clock(t.remaining())
        is SleepTimer.ChapterEnd -> tr("sleep_chapter_end_short")
        null -> null
    }
    Box {
        Tip(if (label != null) tr("d_sleep_left", label) else tr("sleep_timer")) {
            if (label != null) {
                TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Rounded.Bedtime, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR))
                }
            } else {
                IconButton(onClick = { open = true }) { Icon(Icons.Rounded.Bedtime, tr("sleep_timer")) }
            }
        }
        DropdownMenu(open, { open = false }, offset = DpOffset(0.dp, (-8).dp), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.width(300.dp).padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(tr("sleep_timer"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                if (t != null) {
                    Text(label.orEmpty(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(tr("sleep_running"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (t is SleepTimer.Countdown) {
                            OutlinedButton(onClick = { SleepTimer.extend(5 * 60_000L) }, modifier = Modifier.weight(1f)) { Text(tr("plus_5_min")) }
                        }
                        Button(onClick = { SleepTimer.cancel(); open = false }, modifier = Modifier.weight(1f)) { Text(tr("sleep_stop")) }
                    }
                } else {
                    Text(tr("sleep_pause_after"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (m in SLEEP_MINUTES) {
                            Choice(tr("minutes_short", m), false) {
                                SleepTimer.startMinutes(m)
                                app.settings.update { it.copy(sleepMinutes = m) }
                                open = false
                            }
                        }
                    }
                    val chIdx = entry?.book?.chapterAt(position) ?: -1
                    val chapter = entry?.book?.chapters?.getOrNull(chIdx)
                    if (entry != null && chapter != null && entry.book.chapters.size > 1 && loaded) {
                        Spacer(Modifier.height(10.dp))
                        FilledTonalButton(
                            onClick = {
                                SleepTimer.startChapterEnd(entry.id, chIdx, chapter.endMs)
                                open = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(tr("sleep_chapter_end")) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Lautstärke

@Composable
private fun VolumeControl() {
    val app = LocalApp.current
    var volume by remember { mutableFloatStateOf(app.player.volume) }
    var before by remember { mutableFloatStateOf(1f) }
    fun set(v: Float) {
        volume = v
        app.player.volume = v
        app.settings.updatePrefs { it.copy(volume = v) }
    }
    Tip(tr("d_volume") + " " + DFormat.percent(volume)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    if (volume > 0f) {
                        before = volume
                        set(0f)
                    } else {
                        set(before.coerceAtLeast(0.1f))
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(contentColor = LocalContentColor.current),
            ) {
                Icon(
                    when {
                        volume <= 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                        volume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    tr("d_volume"),
                )
            }
            SeekBar(volume, onCommit = { set(it) }, onChange = { set(it) }, modifier = Modifier.width(84.dp))
        }
    }
}
