package app.simpleaudiobooks.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.simpleaudiobooks.data.Entry
import app.simpleaudiobooks.data.Status
import app.simpleaudiobooks.desktop.DFormat
import app.simpleaudiobooks.desktop.tr
import app.simpleaudiobooks.desktop.trPlural
import java.awt.Desktop
import java.io.File

/** Detailbereich rechts neben der Bibliothek. */
@Composable
fun BookPanel(e: Entry, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val ui = LocalUi.current
    val actions = LocalActions.current
    val snap by app.player.snapshot.collectAsState()
    val current = snap.bookId == e.id
    val playing = current && snap.playing
    val position = if (current) e.book.globalOf(snap.trackIndex, snap.trackPositionMs) else e.positionMs
    val chapterIndex = e.book.chapterAt(position)
    var tab by remember(e.id) { mutableIntStateOf(0) }
    val state = rememberLazyListState()

    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        LazyColumn(state = state, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "head") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                        BookMoreButton(e)
                        Tip(tr("d_sc_close")) {
                            IconButton(onClick = { ui.selected = null }) { Icon(Icons.Rounded.Close, tr("close")) }
                        }
                    }
                    BookCover(e, Modifier.size(220.dp), RoundedCornerShape(14.dp), elevation = 8.dp, imagePx = 600)
                    Spacer(Modifier.height(20.dp))
                    Text(e.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    e.author?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                    e.narrator?.let {
                        Text(tr("read_by", it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        listOfNotNull(
                            DFormat.duration(e.durationMs),
                            e.book.chapters.size.takeIf { it > 1 }?.let { trPlural("chapters", it) },
                            e.book.year,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { if (playing) app.player.pause() else app.player.play(e.id) },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            when {
                                playing -> Icons.Rounded.Pause
                                e.status == Status.FINISHED -> Icons.Rounded.Replay
                                else -> Icons.Rounded.PlayArrow
                            },
                            null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            tr(
                                when {
                                    playing -> "pause"
                                    e.status == Status.NEW -> "play"
                                    e.status == Status.STARTED -> "resume"
                                    else -> "listen_again"
                                },
                            ),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    when (e.status) {
                        Status.STARTED -> {
                            val remaining = (e.durationMs - position).coerceAtLeast(0)
                            val fraction = if (e.durationMs > 0) position.toFloat() / e.durationMs else 0f
                            ThinProgress(fraction)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                tr("book_progress", DFormat.percent(fraction), DFormat.duration(remaining)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Status.FINISHED -> if (e.state.finishedAt > 0) {
                            Text(tr("finished_on", DFormat.date(e.state.finishedAt)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Status.NEW -> {}
                    }
                    Spacer(Modifier.height(18.dp))
                    Tabs(
                        listOf(
                            tr("chapters") + "  " + e.book.chapters.size,
                            tr("bookmarks_none") + "  " + e.state.bookmarks.size,
                            tr("files") + "  " + e.book.tracks.size,
                        ),
                        tab,
                    ) { tab = it }
                    Spacer(Modifier.height(8.dp))
                }
            }
            when (tab) {
                0 -> itemsIndexed(e.book.chapters, key = { i, _ -> "c$i" }) { i, ch ->
                    val isNow = i == chapterIndex && e.status != Status.NEW
                    Tip(tr("d_play_chapter")) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isNow) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { app.player.play(e.id, ch.startMs) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(30.dp)) {
                                if (isNow && playing) {
                                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    Text((i + 1).toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                ch.title ?: tr("chapter_n", i + 1),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(DFormat.clock(ch.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                1 -> {
                    if (current) {
                        item(key = "add-bm") {
                            OutlinedButton(onClick = { actions.addBookmark() }, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(tr("bookmark_here", DFormat.clock(position)))
                            }
                        }
                    }
                    if (e.state.bookmarks.isEmpty()) {
                        item(key = "no-bm") {
                            Text(tr("no_bookmarks"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                        }
                    }
                    itemsIndexed(e.state.bookmarks, key = { _, b -> "b${b.id}" }) { _, b ->
                        val chIdx = e.book.chapterAt(b.positionMs)
                        val chTitle = e.book.chapters.getOrNull(chIdx)?.let { it.title ?: tr("chapter_n", chIdx + 1) }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { app.player.play(e.id, b.positionMs) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    DFormat.clock(b.positionMs) + (chTitle?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    b.note.ifBlank { DFormat.dateTime(b.createdAt) },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (b.note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Tip(tr("edit_note")) {
                                IconButton(onClick = { actions.dialog = BookDialog.BookmarkNote(e.id, b.id, b.note) }) {
                                    Icon(Icons.Rounded.Edit, tr("edit_note"), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Tip(tr("d_delete_bookmark")) {
                                IconButton(onClick = { app.library.deleteBookmark(e.id, b.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, tr("d_delete_bookmark"), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                else -> {
                    itemsIndexed(e.book.tracks, key = { i, _ -> "t$i" }) { i, t ->
                        val now = current && snap.trackIndex == i
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                t.path,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (now) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(DFormat.clock(t.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item(key = "folder") {
                        OutlinedButton(
                            onClick = {
                                val target = File(e.book.root, e.book.path).let { if (it.isFile) it.parentFile else it }
                                runCatching { Desktop.getDesktop().open(target) }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("d_show_in_folder"))
                        }
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}

/** Reiter als Reihe von Schaltflächen mit Unterstrich. */
@Composable
private fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
