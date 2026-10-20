package app.simpleaudiobooks.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.simpleaudiobooks.data.Entry
import app.simpleaudiobooks.data.Status
import app.simpleaudiobooks.desktop.DFormat
import app.simpleaudiobooks.desktop.DesktopApp
import app.simpleaudiobooks.desktop.Tr
import app.simpleaudiobooks.ui.theme.Palettes
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

val LocalApp = staticCompositionLocalOf<DesktopApp> { error("DesktopApp fehlt") }

/** Kurze Meldung unten im Fenster. */
val LocalNotify = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Merkt, ob gerade ein Eingabefeld den Fokus hat; dann wirken Tastenkürzel wie die Leertaste nicht. */
object TextFocus {
    @Volatile
    var active = false
}

fun Modifier.tracksTextFocus(): Modifier = onFocusChanged { TextFocus.active = it.isFocused }

// ---------------------------------------------------------------- Farben

/** Leitfarbe eines Buches: aus dem Titelbild oder aus dem Titel abgeleitet. */
fun bookColor(entry: Entry): Color = entry.color?.let { Color(it) } ?: hashColor(entry.title)

fun hashColor(seed: String): Color {
    val h = ((seed.hashCode().toLong() and 0xFFFFFFFFL) % 360).toFloat()
    return Palettes.hsl(h, 0.42f, 0.42f)
}

/** Farbe auf eine bestimmte Helligkeit bringen; Farbton bleibt, Sättigung höchstens [maxSaturation]. */
fun Color.withLightness(l: Float, maxSaturation: Float = 0.6f): Color {
    val r = red
    val g = green
    val b = blue
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val light = (mx + mn) / 2
    val d = mx - mn
    var h = 0f
    var s = 0f
    if (d > 0.0001f) {
        s = if (light > 0.5f) d / (2 - mx - mn) else d / (mx + mn)
        h = when (mx) {
            r -> ((g - b) / d + (if (g < b) 6 else 0)) * 60
            g -> ((b - r) / d + 2) * 60
            else -> ((r - g) / d + 4) * 60
        }
    }
    return Palettes.hsl(h, s.coerceAtMost(maxSaturation), l)
}

// ---------------------------------------------------------------- Titelbild

@Composable
fun BookCover(
    entry: Entry,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    elevation: Dp = 0.dp,
    imagePx: Int = 400,
) {
    val base = remember(entry.id, entry.color, entry.title) { bookColor(entry) }
    Box(
        modifier
            .then(if (elevation > 0.dp) Modifier.shadow(elevation, shape) else Modifier)
            .clip(shape)
            .background(Brush.linearGradient(listOf(base.withLightness(0.46f), base.withLightness(0.26f)))),
    ) {
        val img = rememberFileImage(entry.cover, imagePx)
        if (img != null) {
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Placeholder(entry.title, entry.author)
        }
    }
}

@Composable
private fun Placeholder(title: String, author: String?) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val size = maxWidth
        if (size < 72.dp) {
            // Klein reicht der Platz nicht für den Titel: Anfangsbuchstaben
            val initials = title.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
            Text(initials, color = Color.White, fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            return@BoxWithConstraints
        }
        val titleSize = (size.value * 0.105f).coerceIn(9f, 30f).sp
        Column(
            Modifier.padding(size * 0.09f),
            verticalArrangement = Arrangement.spacedBy(size * 0.03f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = titleSize,
                lineHeight = titleSize * 1.15f,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (!author.isNullOrBlank() && size > 70.dp) {
                Text(
                    author,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = titleSize * 0.62f,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Texte zu einem Buch

/** "Neu · 3 h", "noch 2 h 10 min", "Gehört". */
fun statusLine(e: Entry): String = when (e.status) {
    Status.NEW -> Tr.get("status_new_len", DFormat.duration(e.durationMs))
    Status.STARTED -> Tr.get("remaining", DFormat.duration(e.remainingMs))
    Status.FINISHED -> Tr.get("status_finished")
}

/** Dünner Fortschrittsbalken. */
@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
    }
}

/** Ob sich zwei Werte praktisch gleichen (für Tempo-Vergleiche). */
fun near(a: Float, b: Float) = abs(a - b) < 0.001f
