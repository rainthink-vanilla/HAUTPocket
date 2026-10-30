package cn.hautpocket.app.domain

import org.json.JSONArray
import org.json.JSONObject

/** Protocol interpretation, independently implemented. No third-party parser source is copied. */
object ScheduleParser {
    data class Result(
        val courses: List<Course>, val skipped: Int,
        val semesterStart: String? = null, val totalWeeks: Int? = null,
        val sectionTimes: List<SectionTime> = emptyList(),
        val warnings: List<String> = emptyList(), val source: String = "JSON",
    )

    fun weeks(text: String): List<Int> {
        val normalized = text.replace('，', ',').replace('、', ',').replace("到", "-")
            .replace('－', '-').replace('–', '-').replace('—', '-')
        require(normalized.isNotBlank()) { "缺少周次" }
        val result = sortedSetOf<Int>()
        val parts = normalized.split(Regex("[,;；]"))
        for (part in parts) {
            if (part.isBlank()) continue
            val match = Regex("^\\s*(?:第\\s*)?(\\d+)(?:\\s*-\\s*(\\d+))?\\s*(?:周)?\\s*(?:[（(]?([单双])[）)]?)?\\s*$").matchEntire(part)
                ?: throw IllegalArgumentException("无法识别周次：$part")
            val first = match.groupValues[1].toInt()
            val last = match.groupValues[2].toIntOrNull() ?: first
            require(first in 1..60 && last in first..60) { "周次超出范围" }
            val parity = match.groupValues[3]
            result += (first..last).filter { parity.isBlank() || (parity == "单" && it % 2 == 1) || (parity == "双" && it % 2 == 0) }
        }
        require(result.isNotEmpty()) { "没有有效周次" }
        return result.toList()
    }

    fun sections(text: String): List<Int> {
        val result = sortedSetOf<Int>()
        for (part in text.replace("第", "").replace("节", "").replace('，', ',').replace('、', ',').replace('–', '-').split(',')) {
            val match = Regex("^\\s*(\\d+)(?:\\s*-\\s*(\\d+))?\\s*$").matchEntire(part)
                ?: throw IllegalArgumentException("无法识别节次：$part")
            val first = match.groupValues[1].toInt()
            val last = match.groupValues[2].toIntOrNull() ?: first
            require(first in 1..20 && last in first..20) { "节次超出范围" }
            result += first..last
        }
        require(result.isNotEmpty()) { "没有有效节次" }
        return result.toList()
    }

    fun parse(text: String, defaultCampus: Campus): Result {
        require(text.length <= 2_000_000) { "课表文件过大" }
        val trimmed = text.removePrefix("\uFEFF").trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return TimetableFile.parseMarkdown(trimmed, defaultCampus)
        val obj = if (trimmed.startsWith("{")) JSONObject(trimmed) else null
        if (obj?.has("contract") == true) require(obj.getString("contract") == "univ-timetable/v1") { "暂不支持该课表文件契约版本" }
        val list = if (obj == null) JSONArray(trimmed) else {
            obj.optJSONArray("kbList") ?: obj.optJSONObject("data")?.optJSONArray("kbList")
                ?: obj.optJSONArray("courses") ?: throw IllegalArgumentException("找不到 kbList 或 courses。请导入课表 JSON。")
        }
        require(list.length() <= 2000) { "课程条目过多" }
        val courses = mutableListOf<Course>()
        var skipped = 0
        val warnings = mutableListOf<String>()
        val calendar = TimetableFile.calendar(obj ?: JSONObject(), warnings)
        for (i in 0 until list.length()) {
            runCatching {
                val row = list.getJSONObject(i)
                val name = row.optString("kcmc", row.optString("name")).trim()
                val location = row.optString("cdmc", row.optString("location")).trim()
                val campusName = row.optString("xqmc") + " " + location
                val campus = when {
                    campusName.contains("嵩山") -> Campus.SONGSHAN
                    campusName.contains("莲花") -> Campus.LIANHUA
                    else -> if (row.has("campus")) Campus.from(row.getString("campus")) else defaultCampus
                }
                val weekValues = row.optJSONArray("weeks")?.let { a -> (0 until a.length()).map { a.getInt(it) } }
                    ?: weeks(row.optString("zcd", row.optString("weeks")))
                val sectionValues = row.optJSONArray("sections")?.let { a -> (0 until a.length()).map { a.getInt(it) } }
                    ?: if (row.has("startPeriod")) {
                        val first = row.getInt("startPeriod")
                        val last = row.optInt("endPeriod", first)
                        require(first in 1..20 && last in first..20) { "节次范围有误" }
                        (first..last).toList()
                    } else sections(row.optString("jcs", row.optString("jc")))
                val day = when { row.has("xqj") -> row.getInt("xqj"); row.has("dayOfWeek") -> row.getInt("dayOfWeek"); else -> row.getInt("day") }
                Course(name = name, teacher = row.optString("xm", row.optString("teacher")), location = location,
                    day = day, weeks = weekValues.distinct().sorted(), sections = sectionValues.distinct().sorted(), campus = campus, note = row.optString("note"))
            }.onSuccess { courses += it }.onFailure { skipped++ }
        }
        require(courses.isNotEmpty()) { "未读到有效课程（$skipped 条无法解析）。原课表已保留。" }
        if (obj?.has("theme") == true) warnings += "文件主题未应用：口袋工大保留自身外观。"
        return Result(courses.distinctBy { listOf(it.name, it.teacher, it.location, it.day, it.weeks, it.sections, it.campus, it.note) }, skipped,
            calendar.start, calendar.weeks, calendar.times, warnings, if (obj?.optString("contract") == "univ-timetable/v1") "大学课表文件" else "JSON")
    }
}
