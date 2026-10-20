package app.simpleaudiobooks.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.simpleaudiobooks.data.Status
import app.simpleaudiobooks.desktop.DFormat
import app.simpleaudiobooks.desktop.tr
import app.simpleaudiobooks.desktop.trPlural
import java.time.LocalDate

private const val DAYS = 30

@Composable
fun StatsPane() {
    val app = LocalApp.current
    val ui = LocalUi.current
    val stats by app.stats.data.collectAsState()
    val entries by app.library.entries.collectAsState()
    val today = LocalDate.now()
    val days = remember(today) { (DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) } }
    val values = remember(stats, days) { days.map { stats.days[it.toString()] ?: 0L } }
    val week = values.takeLast(7).sum()
    val total = stats.days.values.sum()
    val top = remember(entries) { entries.filter { it.state.listenedMs > 60_000 }.sortedByDescending { it.state.listenedMs }.take(8) }
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 28.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 1180.dp).fillMaxWidth()) {
                Text(tr("statistics"), style = MaterialTheme.typography.headlineSmall)
                Text(tr("statistics_desc"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(tr("stats_today"), DFormat.duration(values.last()), Modifier.weight(1f))
                    StatTile(tr("stats_week"), DFormat.duration(week), Modifier.weight(1f))
                    StatTile(tr("stats_total"), DFormat.duration(total), Modifier.weight(1f))
                    StatTile(tr("filter_finished"), entries.count { it.status == Status.FINISHED }.toString(), Modifier.weight(1f))
                    StatTile(tr("filter_started"), entries.count { it.status == Status.STARTED }.toString(), Modifier.weight(1f))
                    StatTile(tr("filter_new"), entries.count { it.status == Status.NEW }.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val wide = maxWidth > 900.dp
                    val chart = @Composable { m: Modifier -> DayChart(days, values, m) }
                    val list = @Composable { m: Modifier ->
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = m) {
                            Column(Modifier.padding(20.dp)) {
                                Text(tr("stats_most_listened"), style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(12.dp))
                                if (top.isEmpty()) {
                                    Text(
                                        tr("stats_empty"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    )
                                } else {
                                    val max = top.first().state.listenedMs.toFloat()
                                    for (e in top) {
                                        Row(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { ui.openBook(e.id) }.padding(vertical = 6.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            BookCover(e, Modifier.size(44.dp), RoundedCornerShape(8.dp), imagePx = 140)
                                            Spacer(Modifier.width(14.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Spacer(Modifier.height(6.dp))
                                                ThinProgress(e.state.listenedMs / max)
                                            }
                                            Spacer(Modifier.width(14.dp))
                                            Text(DFormat.duration(e.state.listenedMs), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            chart(Modifier.weight(1.5f))
                            list(Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            chart(Modifier.fillMaxWidth())
                            list(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Säulen je Tag; die Maus zeigt den Wert eines Tages, sonst steht heute da. */
@Composable
private fun DayChart(days: List<LocalDate>, values: List<Long>, modifier: Modifier) {
    var hovered by remember(days) { mutableStateOf<Int?>(null) }
    val selected = hovered ?: days.lastIndex
    val bar = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(60_000L).toFloat()

    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(20.dp)) {
            Text(trPlural("stats_last_days", DAYS, DAYS), style = MaterialTheme.typography.titleSmall)
            val d = days[selected]
            Text(
                "${DFormat.weekday(d)}, ${DFormat.date(d)}: ${DFormat.duration(values[selected])}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .pointerInput(days) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val pos = ev.changes.firstOrNull()?.position ?: continue
                                hovered = when (ev.type) {
                                    PointerEventType.Exit -> null
                                    else -> (pos.x / (size.width / days.size.toFloat())).toInt().coerceIn(0, days.lastIndex)
                                }
                            }
                        }
                    },
            ) {
                val slot = size.width / days.size
                val barW = (slot * 0.62f).coerceAtLeast(3f)
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                values.forEachIndexed { i, v ->
                    val h = if (v <= 0) 0f else (v / max * size.height).coerceAtLeast(3.dp.toPx())
                    if (h <= 0f) return@forEachIndexed
                    val left = i * slot + (slot - barW) / 2
                    drawRoundRect(
                        color = if (i == selected) bar else dim,
                        topLeft = Offset(left, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                days.forEachIndexed { i, day ->
                    Text(
                        if (i % 3 == (days.lastIndex % 3) || i == selected) day.dayOfMonth.toString() else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
