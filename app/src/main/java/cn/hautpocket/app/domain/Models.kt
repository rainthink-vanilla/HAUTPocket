package cn.hautpocket.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class Campus(val label: String) {
    LIANHUA("莲花街"), SONGSHAN("嵩山路");
    companion object {
        fun from(value: String): Campus = if (value.isBlank()) LIANHUA else
            entries.firstOrNull { it.name == value } ?: throw IllegalArgumentException("无法识别校区：$value")
    }
}

data class Course(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val day: Int,
    val weeks: List<Int>,
    val sections: List<Int>,
    val campus: Campus = Campus.LIANHUA,
    val note: String = "",
) {
    init {
        require(name.isNotBlank()) { "课程名不能为空" }
        require(day in 1..7) { "星期必须在 1–7 之间" }
        require(weeks.isNotEmpty() && weeks.all { it in 1..60 }) { "周次必须在 1–60 之间" }
        require(sections.isNotEmpty() && sections.all { it in 1..20 }) { "节次必须在 1–20 之间" }
    }
}

data class SectionTime(val section: Int, val start: String, val end: String) {
    init {
        require(section in 1..20)
        require(LocalTime.parse(end).isAfter(LocalTime.parse(start))) { "结束时间应晚于开始时间" }
    }
}

enum class MeterKind(val label: String, val unit: String) {
    ELECTRICITY("宿舍电量", "度"), AIR_CONDITIONER("空调电量", "度"), WATER("水量", "m³")
}

data class Meter(
    val id: String = UUID.randomUUID().toString(),
    val campus: Campus,
    val building: String,
    val room: String,
    val kind: MeterKind,
    val providerRoomId: String = "",
) {
    init { require(building.isNotBlank() && room.isNotBlank()) { "请填写楼栋和房间" } }
    val label: String get() = "$building · $room"
}

data class Reading(
    val id: String = UUID.randomUUID().toString(),
    val meterId: String,
    val timestamp: Long,
    val remaining: Double,
    // null means that intervening top-ups are unknown. Never call the balance drop consumption.
    val addedSincePrevious: Double? = null,
    val note: String = "手工抄录自官方服务",
) {
    init {
        require(remaining.isFinite() && remaining >= 0) { "剩余量应为非负数" }
        require(addedSincePrevious == null || (addedSincePrevious.isFinite() && addedSincePrevious >= 0)) { "充值量应为非负数" }
    }
}

data class FocusSession(val finishedAt: Long, val minutes: Int)

data class OnlineDevice(val name: String, val category: String, val balance: String, val todayUse: String, val totalBought: String, val status: String)
data class OnlineRoom(val id: String, val name: String, val devices: List<OnlineDevice>, val fetchedAt: Long, val failed: Boolean = false)

data class PocketState(
    val campus: Campus = Campus.LIANHUA,
    val semesterStart: String = "",
    val totalWeeks: Int = 20,
    val courses: List<Course> = emptyList(),
    val sectionTimes: List<SectionTime> = emptyList(),
    val scheduleImportedAt: Long = 0,
    val meters: List<Meter> = emptyList(),
    val readings: List<Reading> = emptyList(),
    val focusSessions: List<FocusSession> = emptyList(),
    val focusEndsAt: Long = 0,
    val focusMinutes: Int = 25,
    val onlineRooms: List<OnlineRoom> = emptyList(),
    val onlineRoomCampuses: Map<String, Campus> = emptyMap(),
)

fun semesterWeek(start: String, today: LocalDate, totalWeeks: Int): Int? {
    val date = runCatching { LocalDate.parse(start) }.getOrNull() ?: return null
    val days = ChronoUnit.DAYS.between(date, today)
    if (days < 0) return null
    return (days / 7 + 1).toInt().takeIf { it in 1..totalWeeks }
}

fun consumption(previous: Reading, current: Reading): Double? {
    if (previous.meterId != current.meterId || current.timestamp <= previous.timestamp) return null
    val added = current.addedSincePrevious ?: return null
    return (previous.remaining + added - current.remaining).takeIf { it >= -0.000001 }?.coerceAtLeast(0.0)
}

/** The following record's top-up amount refers to the removed interval and must be re-confirmed. */
fun removeReading(readings: List<Reading>, id: String): List<Reading> {
    val removed = readings.find { it.id == id } ?: return readings
    val next = readings.filter { it.meterId == removed.meterId && it.timestamp > removed.timestamp }.minByOrNull { it.timestamp }
    return readings.filterNot { it.id == id }.map { if (it.id == next?.id) it.copy(addedSincePrevious = null) else it }
}

fun coursesOn(courses: List<Course>, day: Int, week: Int) = courses
    .filter { it.day == day && week in it.weeks }
    .sortedBy { it.sections.minOrNull() }

fun sectionSummary(sections: List<Int>): String {
    val values = sections.distinct().sorted()
    val groups = mutableListOf<String>()
    var index = 0
    while (index < values.size) {
        val first = values[index]
        var last = first
        while (index + 1 < values.size && values[index + 1] == last + 1) { index++; last = values[index] }
        groups += if (first == last) "$first" else "$first–$last"
        index++
    }
    return groups.joinToString("、") + " 节"
}
