package cn.hautpocket.app.domain

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object TimetableExport {
    fun json(state: PocketState, now: Long): String {
        val rows = JSONArray()
        for (course in state.courses) {
            val sections = course.sections.distinct().sorted()
            var index = 0
            while (index < sections.size) {
                val first = sections[index]
                var last = first
                while (index + 1 < sections.size && sections[index + 1] == last + 1) { index++; last = sections[index] }
                rows.put(JSONObject().put("name", course.name).put("teacher", course.teacher).put("location", course.location)
                    .put("dayOfWeek", course.day).put("startPeriod", first).put("endPeriod", last)
                    .put("weeks", JSONArray(course.weeks)).put("note", course.note).put("campus", course.campus.name))
                index++
            }
        }
        val meta = JSONObject().put("school", "河南工业大学").put("totalWeeks", state.totalWeeks).put("scrapedAt", Instant.ofEpochMilli(now).toString())
        if (state.semesterStart.isNotBlank()) meta.put("semesterStart", state.semesterStart)
        return JSONObject().put("contract", "univ-timetable/v1").put("meta", meta)
            .put("periods", JSONArray(state.sectionTimes.map { JSONObject().put("index", it.section).put("start", it.start).put("end", it.end) }))
            .put("courses", rows).toString(2)
    }
}
