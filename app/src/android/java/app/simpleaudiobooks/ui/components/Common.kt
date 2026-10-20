package app.simpleaudiobooks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.simpleaudiobooks.SimpleAudiobooksApp
import app.simpleaudiobooks.R
import app.simpleaudiobooks.data.Chapter
import app.simpleaudiobooks.data.Entry
import app.simpleaudiobooks.data.Status
import app.simpleaudiobooks.util.Format

val LocalApp = staticCompositionLocalOf<SimpleAudiobooksApp> { error("SimpleAudiobooksApp fehlt") }

/** Platz, den der Mini-Player unten belegt; Listen geben ihn als Innenabstand dazu. */
val LocalBottomInset = compositionLocalOf { 0.dp }

@Composable
fun ThinProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = color.copy(alpha = 0.22f),
    height: Dp = 4.dp,
) {
    Box(modifier.height(height).clip(CircleShape).background(track)) {
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(p).clip(CircleShape).background(color))
        }
    }
}

@Composable
fun durationText(ms: Long): String = Format.duration(LocalContext.current, ms)

@Composable
fun percentText(fraction: Float): String = stringResource(R.string.percent, (fraction * 100).toInt().coerceIn(0, 100))

@Composable
fun chapterTitle(chapter: Chapter?, index: Int): String =
    chapter?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chapter_n, index + 1)

/** Kurze Zustandszeile für Listen: "Neu · 12 h", "noch 3 h 5 min", "Gehört". */
@Composable
fun statusLine(entry: Entry): String = when (entry.status) {
    Status.NEW -> stringResource(R.string.status_new_len, durationText(entry.durationMs))
    Status.STARTED -> stringResource(R.string.remaining, durationText(entry.remainingMs))
    Status.FINISHED -> stringResource(R.string.status_finished)
}

@Composable
fun chaptersCount(n: Int): String = pluralStringResource(R.plurals.chapters, n, n)

/** "Lesezeichen" ohne Zahl, solange es keine gibt. */
@Composable
fun bookmarksLabel(n: Int): String =
    if (n == 0) stringResource(R.string.bookmarks_none) else pluralStringResource(R.plurals.bookmarks, n, n)
