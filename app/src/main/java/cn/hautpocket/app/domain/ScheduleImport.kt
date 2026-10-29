package cn.hautpocket.app.domain

enum class ImportMode(val label: String) { REPLACE("整体替换"), MERGE("更新合并"), APPEND("追加") }

object ScheduleImport {
    fun apply(state: PocketState, result: ScheduleParser.Result, mode: ImportMode, applyCalendar: Boolean, now: Long): PocketState {
        require(result.courses.isNotEmpty()) { "没有可导入课程" }
        val courses = when (mode) {
            ImportMode.REPLACE -> result.courses
            ImportMode.APPEND -> state.courses + result.courses.map { it.copy(id = java.util.UUID.randomUUID().toString()) }
            ImportMode.MERGE -> state.courses.toMutableList().apply {
                for (incoming in result.courses) {
                    val index = indexOfFirst { it.name == incoming.name && it.day == incoming.day && it.sections == incoming.sections && it.campus == incoming.campus }
                    if (index < 0) add(incoming) else set(index, incoming.copy(id = this[index].id))
                }
            }
        }
        require(courses.size <= 4000) { "课程总数超过 4000，请清理重复导入" }
        return state.copy(courses = courses, scheduleImportedAt = now,
            semesterStart = if (applyCalendar) result.semesterStart ?: state.semesterStart else state.semesterStart,
            totalWeeks = if (applyCalendar) result.totalWeeks ?: state.totalWeeks else state.totalWeeks,
            sectionTimes = if (applyCalendar && result.sectionTimes.isNotEmpty()) result.sectionTimes else state.sectionTimes)
    }
}
