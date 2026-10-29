package cn.hautpocket.app.domain

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** Adapter for the local Univ Timetable v1 contract. UI frameworks remain independent. */
object TimetableFile {
    data class Calendar(val start: String?, val weeks: Int?, val times: List<SectionTime>)

    fun calendar(root: JSONObject, warnings: MutableList<String>): Calendar {
        val meta = root.optJSONObject("meta") ?: root
        val dateText = meta.optString("semesterStart").trim()
        val date = if (dateText.isBlank()) null else runCatching {
            val parts = dateText.split('-').map { it.toInt() }
            require(parts.size == 3)
            LocalDate.of(parts[0], parts[1], parts[2]).also { require(it.dayOfWeek.value == 1) }
        }.getOrElse { warnings += "文件学期起始日不是有效周一，未导入该日期。"; null }
        val total = meta.optInt("totalWeeks", 0).takeIf { it in 1..60 }
        if (meta.has("totalWeeks") && total == null) warnings += "文件总周数超出 1–60，未应用。"
        val array = root.optJSONArray("periods") ?: root.optJSONArray("periodTimes") ?: meta.optJSONArray("periods")
        val times = mutableListOf<SectionTime>()
        if (array != null) {
            require(array.length() <= 20) { "节次时间条目超过 20" }
            for (i in 0 until array.length()) {
                runCatching {
                    val item = array.getJSONObject(i)
                    SectionTime(item.optInt("index", item.optInt("period", i + 1)), clock(item.getString("start")), clock(item.getString("end")))
                }.onSuccess { times += it }.onFailure { warnings += "第 ${i + 1} 条节次时间有误，未应用整份作息表。" }
            }
        }
        val ordered = times.sortedBy { it.section }
        val valid = ordered.distinctBy { it.section }.size == ordered.size && ordered.zipWithNext().all { (a, b) -> LocalTime.parse(a.end) <= LocalTime.parse(b.start) }
        if (!valid) warnings += "文件节次时间重复、重叠或倒序，未应用整份作息表。"
        return Calendar(date?.toString(), total, if (valid && (array == null || times.size == array.length())) ordered else emptyList())
    }

    private fun clock(text: String): String {
        val parts = text.trim().replace('：', ':').split(':')
        require(parts.size == 2)
        return LocalTime.of(parts[0].toInt(), parts[1].toInt()).toString().let { if (it.length == 5) it else it.take(5) }
    }

    private fun cells(line: String) = line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim().replace("**", "").replace("`", "") }
    private fun separator(values: List<String>) = values.all { Regex(":?-{2,}:?").matches(it) }

    fun parseMarkdown(text: String, campus: Campus): ScheduleParser.Result {
        val blocks = Regex("```(?:json|JSON)\\s*\\r?\\n([\\s\\S]*?)```").findAll(text).map { it.groupValues[1].trim() }.toList()
        val candidate = blocks.firstOrNull { block -> runCatching { val o = JSONObject(block); o.has("courses") || o.has("kbList") }.getOrDefault(false) }
        if (candidate != null) return ScheduleParser.parse(candidate, campus).let { it.copy(source = "Markdown 内 JSON · ${it.source}") }

        val rows = JSONArray()
        val periods = JSONArray()
        val meta = JSONObject()
        var header: List<String>? = null
        var periodHeader = false
        var rejected = 0
        for (line in text.lines()) {
            if (!line.trim().startsWith("|")) { header = null; periodHeader = false; continue }
            val values = cells(line)
            if (separator(values)) continue
            if (values.size >= 2) when (values[0]) {
                "学期开始日期", "学期起始日", "semesterStart" -> meta.put("semesterStart", values[1])
                "学期总周数", "总周数", "totalWeeks" -> values[1].toIntOrNull()?.let { meta.put("totalWeeks", it) }
            }
            if (values.any { it.contains("课程名称") || it == "课程" } && values.any { it == "星期" || it == "星期几" }) { header = values; periodHeader = false; continue }
            if (values.size >= 2 && values[0] == "节次" && (values[1] == "时间" || values[1].contains("开始"))) { header = null; periodHeader = true; continue }
            if (periodHeader && values.size >= 2) {
                val index = Regex("\\d+").find(values[0])?.value?.toIntOrNull()
                val span = Regex("(\\d{1,2}[:：]\\d{2})\\s*[-–—至到]\\s*(\\d{1,2}[:：]\\d{2})").find(values[1])
                if (index != null && span != null) periods.put(JSONObject().put("index", index).put("start", span.groupValues[1]).put("end", span.groupValues[2]))
                else if (index != null && values.size >= 3) periods.put(JSONObject().put("index", index).put("start", values[1]).put("end", values[2]))
                continue
            }
            val columns = header ?: continue
            fun field(vararg keys: String): String {
                val index = columns.indexOfFirst { it in keys }
                return values.getOrNull(index)?.takeUnless { it == "-" }.orEmpty()
            }
            val name = field("课程名称", "课程")
            if (name.isBlank()) continue
            runCatching {
                val weekdays = days(field("星期", "星期几"))
                require(weekdays.isNotEmpty())
                val sections = ScheduleParser.sections(field("节次", "时段"))
                val weekText = field("周次", "周数")
                require(weekText.isNotBlank()) { "缺少周次" }
                val weeks = ScheduleParser.weeks(weekText)
                for (day in weekdays) rows.put(JSONObject().put("name", name).put("teacher", field("教师", "老师"))
                    .put("location", field("地点", "教室")).put("day", day).put("weeks", JSONArray(weeks))
                    .put("sections", JSONArray(sections)).put("note", field("备注")))
            }.onFailure { rejected++ }
            require(rows.length() <= 2000) { "课程条目过多" }
        }
        require(rows.length() > 0) { "未找到有效课表。支持大学课表 .md/.json、课程明细表和正方 JSON。" }
        val result = ScheduleParser.parse(JSONObject().put("courses", rows).put("meta", meta).put("periods", periods).toString(), campus)
        return result.copy(skipped = result.skipped + rejected, source = "Markdown 课程明细表")
    }

    fun days(text: String): List<Int> {
        val normalized = text.trim().replace("星期", "").replace("周", "").replace("礼拜", "")
        if (normalized == "末" || normalized == "六日") return listOf(6, 7)
        val map = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7)
        fun day(c: Char): Int? {
            val value = map[c] ?: c.digitToIntOrNull() ?: return null
            return (if (value == 0) 7 else value).takeIf { it in 1..7 }
        }
        val range = Regex("^([1-7一二三四五六日天])[-至到~]([1-7一二三四五六日天])$").matchEntire(normalized)
        if (range != null) {
            val start = day(range.groupValues[1][0])!!; val end = day(range.groupValues[2][0])!!
            require(end >= start)
            return (start..end).toList()
        }
        require(normalized.all { it in map.keys || it in '0'..'7' || it in "、,，/ ／;；和 " }) { "星期格式无法识别" }
        return normalized.mapNotNull(::day).distinct().sorted()
    }
}
