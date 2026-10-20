package app.simpleaudiobooks.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.simpleaudiobooks.desktop.DesktopApp
import app.simpleaudiobooks.desktop.Tr

/** Hinweistext beim Darüberfahren mit der Maus. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 3.dp,
            ) {
                Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        },
        delayMillis = 550,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 18.dp)),
        content = content,
    )
}

/** Schmales Suchfeld für Kopfzeilen. */
@Composable
fun SearchBox(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier
            .focusRequester(focus)
            .tracksTextFocus()
            .onPreviewKeyEvent { if (it.key == Key.Escape && value.isNotEmpty()) { onChange(""); true } else false },
        decorationBox = { inner ->
            Row(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceContainerHigh)
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).clickable { onChange("") }.pointerHoverIcon(PointerIcon.Default),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Close, Tr.get("clear"), Modifier.size(18.dp), tint = colors.onSurfaceVariant) }
                }
            }
        },
    )
}

/**
 * Schieberegler mit dünner Spur. Während des Ziehens zeigt er den Wert unter der Maus; [onCommit] kommt beim
 * Loslassen, [onChange] (wenn gesetzt) laufend.
 */
@Composable
fun SeekBar(
    fraction: Float,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: ((Float) -> Unit)? = null,
    onDrag: (Float?) -> Unit = {},
    height: Dp = 4.dp,
) {
    val colors = MaterialTheme.colorScheme
    var dragging by remember { mutableStateOf<Float?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val commit by rememberUpdatedState(onCommit)
    val change by rememberUpdatedState(onChange)
    val dragReport by rememberUpdatedState(onDrag)
    BoxWithConstraints(
        modifier
            .height(20.dp)
            .hoverable(interaction, enabled)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o -> commit((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { o ->
                        val f = (o.x / size.width).coerceIn(0f, 1f)
                        dragging = f
                        dragReport(f)
                        change?.invoke(f)
                    },
                    onDragEnd = {
                        dragging?.let { commit(it) }
                        dragging = null
                        dragReport(null)
                    },
                    onDragCancel = {
                        dragging = null
                        dragReport(null)
                    },
                ) { change2, _ ->
                    val f = (change2.position.x / size.width).coerceIn(0f, 1f)
                    dragging = f
                    dragReport(f)
                    change?.invoke(f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val shown = (dragging ?: fraction).coerceIn(0f, 1f)
        val active = enabled && (hovered || dragging != null)
        val track = if (active) height + 2.dp else height
        Box(Modifier.fillMaxWidth().height(track).clip(RoundedCornerShape(track / 2)).background(colors.surfaceVariant))
        Box(
            Modifier
                .fillMaxWidth(shown)
                .height(track)
                .clip(RoundedCornerShape(track / 2))
                .background(if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.3f)),
        )
        if (active) {
            val knob = 14.dp
            Box(
                Modifier
                    .offset(x = (maxWidth - knob) * shown)
                    .size(knob)
                    .clip(CircleShape)
                    .background(colors.primary),
            )
        }
    }
}

/** Kreispfeil mit Sekundenzahl, rückwärts oder vorwärts. */
@Composable
fun SkipIcon(seconds: Int, forward: Boolean, size: Dp = 30.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.Replay,
            contentDescription = null,
            modifier = Modifier.size(size).graphicsLayer { if (forward) scaleX = -1f },
            tint = LocalContentColor.current,
        )
        val fontSize = with(LocalDensity.current) { (size * 0.3f).toSp() }
        Text(
            seconds.toString(),
            style = TextStyle(
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = FontWeight.Bold,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            color = LocalContentColor.current,
            modifier = Modifier.graphicsLayer { translationY = size.toPx() * 0.05f },
        )
    }
}

/** Bereich der Einstellungen mit Überschrift. */
@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}

/** Zeile der Einstellungen: Titel, Beschreibung und rechts ein Bedienelement. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, enabled, onClick = { onChange(!checked) }) {
        Switch(checked, onChange, enabled = enabled)
    }
}

/** Kleiner farbiger Punkt. */
@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Stellt die Sprache um und merkt sie sich. */
fun setLanguage(app: DesktopApp, choice: String) {
    app.settings.updatePrefs { it.copy(language = choice) }
    Tr.load(choice)
}
