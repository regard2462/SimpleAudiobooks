package app.simpleaudiobooks.ui.player

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.simpleaudiobooks.R
import app.simpleaudiobooks.data.Entry
import app.simpleaudiobooks.playback.PlayerSnapshot
import app.simpleaudiobooks.ui.components.Cover
import app.simpleaudiobooks.ui.components.LocalApp
import app.simpleaudiobooks.ui.components.SkipIcon
import app.simpleaudiobooks.ui.components.ThinProgress
import app.simpleaudiobooks.ui.components.bookColor
import app.simpleaudiobooks.ui.components.chapterTitle

val MiniPlayerHeight = 68.dp

@Composable
fun MiniPlayer(entry: Entry, snap: PlayerSnapshot, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val loaded = snap.bookId == entry.id
    val playing = loaded && snap.playWhenReady
    val position = if (loaded) entry.book.globalOf(snap.trackIndex, snap.trackPositionMs) else entry.positionMs
    val chIdx = entry.book.chapterAt(position)
    val chapter = entry.book.chapters.getOrNull(chIdx)
    val progress = if (entry.durationMs > 0) position.toFloat() / entry.durationMs else 0f
    val tint = bookColor(entry).copy(alpha = 0.16f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
    val skipBack = app.settings.value.skipBackSec

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        color = tint,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -40f) onOpen() },
                ) { _, dy -> total += dy }
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(entry, Modifier.size(48.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (entry.book.chapters.size > 1) chapterTitle(chapter, chIdx) else entry.author.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = {
                    if (loaded) app.playback.skipBack() else app.playback.seekTo(entry.id, (position - skipBack * 1000L).coerceAtLeast(0))
                }) {
                    SkipIcon(skipBack, forward = false, size = 26.dp, contentDescription = pluralStringResource(R.plurals.skip_back_n, skipBack, skipBack))
                }
                IconButton(onClick = { if (playing) app.playback.pause() else app.playback.play(entry.id) }) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (playing) R.string.pause else R.string.play),
                        Modifier.size(32.dp),
                    )
                }
            }
            ThinProgress(
                progress,
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 3.dp),
                height = 3.dp,
            )
        }
    }
}
