package cn.hautpocket.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.hautpocket.app.domain.*

@Composable
fun ScheduleImportDialog(result: ScheduleParser.Result, hasExisting: Boolean, close: () -> Unit, confirm: (ImportMode, Boolean) -> Unit) {
    var mode by remember(result) { mutableStateOf(if (hasExisting) ImportMode.MERGE else ImportMode.REPLACE) }
    var calendar by remember(result) { mutableStateOf(true) }
    val hasCalendar = result.semesterStart != null || result.totalWeeks != null || result.sectionTimes.isNotEmpty()
    AlertDialog(onDismissRequest = close, title = { Text("预览 ${result.courses.size} 条课程") }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("来源：${result.source}", style = MaterialTheme.typography.labelMedium)
            Text(result.courses.take(10).joinToString("\n") { "${it.name} · 周${it.day} · ${sectionSummary(it.sections)}" })
            if (result.courses.size > 10) Text("另有 ${result.courses.size - 10} 条课程。")
            if (result.skipped > 0) Text("${result.skipped} 条无法解析。可取消后核查源文件。", color = MaterialTheme.colorScheme.error)
            result.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ImportMode.entries.forEach { option -> FilterChip(selected = mode == option, onClick = { mode = option }, label = { Text(option.label) }) }
            }
            Text(when (mode) {
                ImportMode.REPLACE -> "用本次内容替换全部课程，适合换学期。"
                ImportMode.MERGE -> "同名、同星期、同节次且同校区的课程更新，其余新增。已删除或调到其他时间的旧课不会自动移除。"
                ImportMode.APPEND -> "保留原课表并加入本次全部课程，可能出现重复。"
            }, style = MaterialTheme.typography.bodySmall)
            if (hasCalendar) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = calendar, onCheckedChange = { calendar = it })
                    Text("应用文件中的学期与节次时间", style = MaterialTheme.typography.bodySmall)
                }
                Text(listOfNotNull(result.semesterStart?.let { "学期起始：$it" }, result.totalWeeks?.let { "$it 周" }, result.sectionTimes.takeIf { it.isNotEmpty() }?.let { "${it.size} 节作息" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            } else Text("本次没有校历与作息信息，保留已有设置。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { confirm(mode, calendar && hasCalendar) }) { Text("确认导入") } },
        dismissButton = { TextButton(onClick = close) { Text("取消") } })
}
