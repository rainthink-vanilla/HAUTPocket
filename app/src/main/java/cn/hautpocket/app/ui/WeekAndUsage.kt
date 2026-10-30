package cn.hautpocket.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.hautpocket.app.domain.*
import cn.hautpocket.app.data.DailySample
import cn.hautpocket.app.data.DailySampling
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun OnlineElectricityChart(rooms: List<OnlineRoom>, samples: List<DailySample>) {
    val series = rooms.flatMap { room -> room.devices.filter { it.category == "电" && it.name != "—" }.map { room to it.name } }.distinctBy { it.first.id to it.second }
    var key by remember(series.map { it.first.id to it.second }) { mutableStateOf(series.firstOrNull()?.let { it.first.id to it.second }) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("在线用电趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("近14次有效采样 · kWh", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (series.isEmpty()) Text("请先查询智能水电并确认房间所属校区。", style = MaterialTheme.typography.bodySmall)
            else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    series.forEach { (room, device) -> FilterChip(selected = key == (room.id to device), onClick = { key = room.id to device }, label = { Text("${room.name} · $device") }) }
                }
                val history = samples.filter { it.roomId == key?.first && it.device == key?.second }.sortedBy { it.date }.takeLast(14)
                if (history.isEmpty()) Text("暂无有效在线采样，请先手动查询。缺失日期无法由今日用量补算。", style = MaterialTheme.typography.bodySmall)
                else {
                    val values = history.map { DailySampling.value(it.raw) }
                    UsageLineChart(history.mapIndexed { index, sample ->
                        val time = Instant.ofEpochMilli(sample.sampledAt).atZone(DailySampling.zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        val unit = values[index]?.second.orEmpty()
                        val consecutive = index > 0 && LocalDate.parse(history[index - 1].date).plusDays(1).toString() == sample.date
                        UsagePlotPoint(
                            id = "${sample.roomId}/${sample.device}/${sample.date}",
                            dateLabel = sample.date.takeLast(5),
                            value = values[index]?.first,
                            valueLabel = values[index]?.first?.let { "%.2f".format(java.util.Locale.ROOT, it) } ?: "未知",
                            detail = "${sample.roomName} · ${sample.device}\n日期：${sample.date}\n采样用量：${values[index]?.let { "${it.first} ${it.second}" } ?: sample.raw}\n原始读数：${sample.raw}\n采样时间：$time\n为截至采样时的今日用量，可能未覆盖全天。",
                            connectPrevious = consecutive && values[index - 1]?.second == unit,
                        )
                    }, unit = "kWh")
                    Text("每个点是该日最后取得的读数，单位 kWh；可能未覆盖全天。接口重置时间仍待核实。", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun WeekTable(courses: List<Course>, week: Int, times: List<SectionTime>) {
    var selected by remember { mutableStateOf<Course?>(null) }
    val active = courseBlocks(courses, week)
    val maxSection = maxOf(8, active.flatMap { it.sections }.maxOrNull() ?: 0, times.maxOfOrNull { it.section } ?: 0).coerceAtMost(20)
    val lanes = (1..7).map { day ->
        val columns = mutableListOf<MutableList<Course>>()
        active.filter { it.day == day }.forEach { course ->
            val lane = columns.firstOrNull { items -> items.none { other -> other.sections.any { it in course.sections } } }
            if (lane == null) columns.add(mutableListOf(course)) else lane.add(course)
        }
        columns.ifEmpty { mutableListOf(mutableListOf()) }
    }
    val rowHeight = 80.dp
    val primary = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primaryContainer
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("节次", Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall)
            lanes.forEachIndexed { index, columns -> Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium, modifier = Modifier.width(112.dp * columns.size).padding(3.dp)) {
                Text("周${listOf("一", "二", "三", "四", "五", "六", "日")[index]}", Modifier.padding(horizontal = 12.dp, vertical = 14.dp), fontWeight = FontWeight.Bold)
            } }
        }
        Row {
            Column(Modifier.width(52.dp)) {
                for (section in 1..maxSection) Column(Modifier.height(rowHeight).padding(4.dp)) {
                    Text("$section", fontWeight = FontWeight.Bold)
                    times.find { it.section == section }?.let { Text(it.start, style = MaterialTheme.typography.labelSmall) }
                }
            }
            lanes.forEach { columns ->
                columns.forEach { entries ->
                    Box(Modifier.width(112.dp).height(rowHeight * maxSection)) {
                        Column { repeat(maxSection) { Box(Modifier.height(rowHeight).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) { HorizontalDivider() } } }
                        entries.forEach { course ->
                            Column(Modifier.offset(y = rowHeight * (course.sections.first() - 1)).width(112.dp)
                                .height(rowHeight * course.sections.size).padding(2.dp)
                                .background(soft, RoundedCornerShape(14.dp)).clickable { selected = course }.padding(9.dp)) {
                                Text(course.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(sectionSummary(course.sections), style = MaterialTheme.typography.labelSmall, color = primary)
                                if (course.location.isNotBlank()) Text(course.location, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { course -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(course.name) }, text = {
        Text("周${course.day} · ${sectionSummary(course.sections)}\n${course.location.ifBlank { "未提供教室" }} · ${course.campus.label}\n${course.teacher}\n周次：${course.weeks.joinToString("、")}\n${course.note}")
    }, confirmButton = { TextButton(onClick = { selected = null }) { Text("关闭") } }) }
}

@Composable
fun ElectricityChart(meters: List<Meter>, readings: List<Reading>) {
    val electric = meters.filter { it.kind != MeterKind.WATER }
    var selectedId by remember(electric.map { it.id }) { mutableStateOf(electric.firstOrNull()?.id) }
    val meter = electric.find { it.id == selectedId }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("宿舍用电趋势", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (electric.isEmpty()) Text("添加电表并记录读数后可查看用电趋势。", style = MaterialTheme.typography.bodySmall)
            else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    electric.forEach { item -> FilterChip(selected = item.id == selectedId, onClick = { selectedId = item.id }, label = { Text("${item.label} · ${item.kind.label}") }) }
                }
                val ordered = readings.filter { it.meterId == meter?.id }.sortedBy { it.timestamp }.takeLast(12)
                val points = ordered.zipWithNext { a, b -> b to consumption(a, b) }
                Text("手工记录的相邻区间用电量（度）；充值量未知时断开。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (points.none { it.second != null }) Text("至少需要两条读数，并填写期间充值量才能计算。", style = MaterialTheme.typography.bodySmall)
                else {
                    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    UsageLineChart(points.mapIndexed { index, (reading, usage) ->
                        val start = Instant.ofEpochMilli(ordered[index].timestamp).atZone(DailySampling.zone).format(format)
                        val end = Instant.ofEpochMilli(reading.timestamp).atZone(DailySampling.zone).format(format)
                        val quantity = usage?.let { "%.2f 度".format(java.util.Locale.ROOT, it) } ?: "未知（期间充值量未确认或读数异常）"
                        UsagePlotPoint(
                            id = reading.id,
                            dateLabel = end.takeLast(11).replace(" ", "\n"),
                            value = usage,
                            valueLabel = usage?.let { "%.2f".format(java.util.Locale.ROOT, it) } ?: "未知",
                            detail = "${meter?.label.orEmpty()} · ${meter?.kind?.label.orEmpty()}\n区间耗电量：$quantity\n开始：$start\n结束：$end",
                        )
                    })
                }
            }
        }
    }
}
