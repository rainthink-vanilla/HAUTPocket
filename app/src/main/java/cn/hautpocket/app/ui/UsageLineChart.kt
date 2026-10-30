package cn.hautpocket.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

internal data class UsagePlotPoint(
    val id: String,
    val dateLabel: String,
    val value: Double?,
    val valueLabel: String,
    val detail: String,
    val connectPrevious: Boolean = true,
)

/** Shared presentation for the existing online and manually recorded series. */
@Composable
internal fun UsageLineChart(points: List<UsagePlotPoint>, unit: String = "度") {
    if (points.isEmpty()) return
    var selectedId by remember(points.map { it.id }) { mutableStateOf(points.lastOrNull { it.value != null }?.id) }
    val selectedPoint = points.find { it.id == selectedId }
    val maximum = (points.mapNotNull { it.value }.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    Text("用量（$unit） · 点击圆点查看详情 · 可左右滑动", style = MaterialTheme.typography.labelSmall)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chartWidth = maxOf(maxWidth, 240.dp, (64 + points.size * 80).dp)
        val left = 48.dp
        val right = chartWidth - 40.dp
        val top = 36.dp
        val bottom = 180.dp
        fun x(index: Int) = if (points.size == 1) (left + right) / 2 else left + (right - left) * index / (points.size - 1)
        fun y(value: Double) = bottom - (bottom - top) * (value / maximum).toFloat()
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Box(Modifier.width(chartWidth).height(244.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    listOf(0.0, maximum / 2, maximum).forEach { value ->
                        drawLine(grid, Offset(left.toPx(), y(value).toPx()), Offset(right.toPx(), y(value).toPx()), 1.dp.toPx())
                    }
                    points.zipWithNext().forEachIndexed { i, (a, b) ->
                        if (a.value != null && b.value != null && b.connectPrevious) {
                            val area = Path().apply {
                                moveTo(x(i).toPx(), bottom.toPx()); lineTo(x(i).toPx(), y(a.value).toPx())
                                lineTo(x(i + 1).toPx(), y(b.value).toPx()); lineTo(x(i + 1).toPx(), bottom.toPx()); close()
                            }
                            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = .18f), Color.Transparent), top.toPx(), bottom.toPx()))
                            drawLine(color, Offset(x(i).toPx(), y(a.value).toPx()), Offset(x(i + 1).toPx(), y(b.value).toPx()), 2.dp.toPx())
                        }
                    }
                    points.forEachIndexed { i, point -> point.value?.let { value ->
                        val position = Offset(x(i).toPx(), y(value).toPx())
                        if (point.id == selectedId) {
                            drawLine(color.copy(alpha = .3f), Offset(position.x, top.toPx()), Offset(position.x, bottom.toPx()), 1.dp.toPx())
                            drawCircle(color.copy(alpha = .16f), 13.dp.toPx(), position)
                        }
                        drawCircle(color, if (point.id == selectedId) 7.dp.toPx() else 5.dp.toPx(), position)
                        if (point.id != selectedId) drawCircle(surface, 2.dp.toPx(), position)
                    } }
                }
                listOf(0.0, maximum / 2, maximum).forEach { value ->
                    Text(String.format(Locale.ROOT, "%.1f", value), Modifier.offset(y = y(value) - 8.dp).width(42.dp),
                        style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                points.forEachIndexed { i, point ->
                    point.value?.let { value ->
                        Text(point.valueLabel, Modifier.offset(x = x(i) - 36.dp, y = y(value) - 28.dp).width(72.dp),
                            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = color,
                            fontWeight = if (point.id == selectedId) FontWeight.Bold else FontWeight.Normal)
                        Box(Modifier.offset(x = x(i) - 24.dp, y = y(value) - 24.dp).size(48.dp)
                            .semantics { contentDescription = point.detail; selected = point.id == selectedId }
                            .clickable(role = Role.Button, onClickLabel = "查看用电详情") { selectedId = point.id })
                    }
                    Text(point.dateLabel, Modifier.offset(x = x(i) - 36.dp, y = 198.dp).width(72.dp)
                        .clickable { selectedId = point.id }, style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    selectedPoint?.let { point ->
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${point.dateLabel} · 所选数据", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("${point.valueLabel} $unit", style = MaterialTheme.typography.titleMedium)
                }
                Text(point.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
