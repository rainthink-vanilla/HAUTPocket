package cn.hautpocket.app.domain

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File

class TimetableCompatibilityTest {
    private fun fixture(): ScheduleParser.Result {
        val text = javaClass.getResource("/univ-timetable-export.md")!!.readText()
        return ScheduleParser.parse(text, Campus.LIANHUA)
    }

    @Test fun readsTheActualLocalAppsExportIncludingCalendarAndNotes() {
        val result = fixture()
        assertEquals(2, result.courses.size)
        assertEquals(0, result.skipped)
        assertEquals("2026-09-07", result.semesterStart)
        assertEquals(20, result.totalWeeks)
        assertEquals(4, result.sectionTimes.size)
        assertEquals("08:30", result.sectionTimes.first().start)
        assertEquals("合成备注", result.courses.first().note)
        assertEquals(Campus.SONGSHAN, result.courses.last().campus)
        assertTrue(result.source.contains("Markdown"))
    }

    @Test fun jsonUsesDayOfWeekAndPeriodBoundsAndSupportsTwentyPeriods() {
        val result = ScheduleParser.parse("""{"contract":"univ-timetable/v1","courses":[{"name":"课","dayOfWeek":7,"startPeriod":19,"endPeriod":20,"weeks":[31,60]}]}""", Campus.SONGSHAN)
        assertEquals(listOf(19,20), result.courses.single().sections)
        assertEquals(listOf(31,60), result.courses.single().weeks)
    }

    @Test fun markdownTableSupportsWeekendMultipleDaysAndSeparatePeriodTable() {
        val result = ScheduleParser.parse("""
            # 课表
            | 项目 | 内容 |
            | --- | --- |
            | 学期开始日期 | 2026-09-07 |
            | 学期总周数 | 20 |

            | 节次 | 时间 |
            | --- | --- |
            | 第1节 | 08:30 - 09:15 |
            | 第2节 | 09:20 - 10:05 |

            | 课程名称 | 教师 | 地点 | 星期 | 节次 | 周次 | 备注 |
            | --- | --- | --- | --- | --- | --- | --- |
            | 合成课程 | 甲 | 嵩山路测试教室 | 周六、周日 | 第1-2节 | 1-6周(单) | 备注 |
        """.trimIndent(), Campus.LIANHUA)
        assertEquals(listOf(6,7), result.courses.map { it.day })
        assertEquals(listOf(1,3,5), result.courses.first().weeks)
        assertEquals(2, result.sectionTimes.size)
        assertEquals("2026-09-07", result.semesterStart)
    }

    @Test fun embeddedJsonTakesPrecedenceOverReadableTable() {
        val text = javaClass.getResource("/univ-timetable-export.md")!!.readText().replace("| 互通测试线代 |", "| 不应读取的表格课程 |")
        assertEquals("互通测试线代", ScheduleParser.parse(text, Campus.LIANHUA).courses.first().name)
    }

    @Test fun invalidCalendarAndThemeAreReportedWithoutDestroyingCourses() {
        val result = ScheduleParser.parse("""{"contract":"univ-timetable/v1","meta":{"semesterStart":"2026-09-08","totalWeeks":999},"theme":{"accentColor":"#123456"},"periods":[{"index":1,"start":"10:00","end":"09:00"}],"courses":[{"name":"课","dayOfWeek":1,"startPeriod":1,"weeks":[1]}]}""", Campus.LIANHUA)
        assertEquals(1, result.courses.size)
        assertNull(result.semesterStart)
        assertNull(result.totalWeeks)
        assertTrue(result.sectionTimes.isEmpty())
        assertTrue(result.warnings.size >= 3)
    }

    @Test fun invalidContractAndCompletelyInvalidFileNeverImport() {
        assertTrue(runCatching { ScheduleParser.parse("""{"contract":"univ-timetable/v999","courses":[]}""", Campus.LIANHUA) }.isFailure)
        assertTrue(runCatching { ScheduleParser.parse("# 没有课程", Campus.LIANHUA) }.isFailure)
    }

    @Test fun mergeUpdatesMatchingCourseRetainsIdAndKeepsOtherCampusSeparate() {
        val original = Course(id = "original", name = "课", teacher = "旧", day = 1, weeks = listOf(1), sections = listOf(1,2))
        val foreign = original.copy(id = "other-campus", campus = Campus.SONGSHAN)
        val incoming = original.copy(id = "incoming", teacher = "新", weeks = listOf(2))
        val merged = ScheduleImport.apply(PocketState(courses = listOf(original, foreign)), ScheduleParser.Result(listOf(incoming),0), ImportMode.MERGE, false, 100)
        assertEquals(2, merged.courses.size)
        assertEquals("original", merged.courses.first().id)
        assertEquals("新", merged.courses.first().teacher)
        assertEquals(listOf(2), merged.courses.first().weeks)
        assertEquals(foreign, merged.courses.last())
    }

    @Test fun replacementAppendAndCalendarChoiceHaveDistinctEffects() {
        val result = fixture()
        val old = PocketState(courses = listOf(Course(name = "旧课", day = 1, weeks = listOf(1), sections = listOf(1))), semesterStart = "2026-03-02")
        val replaced = ScheduleImport.apply(old,result,ImportMode.REPLACE,true,100)
        assertEquals(2,replaced.courses.size)
        assertEquals("2026-09-07",replaced.semesterStart)
        val appended = ScheduleImport.apply(old,result,ImportMode.APPEND,false,100)
        assertEquals(3,appended.courses.size)
        assertEquals("2026-03-02",appended.semesterStart)
        val again = ScheduleImport.apply(appended,result,ImportMode.APPEND,false,200)
        assertEquals(again.courses.size,again.courses.map { it.id }.distinct().size)
    }

    @Test fun exportSplitsDiscontinuousSectionsForTheActualOtherAppParser() {
        val imported = fixture()
        val courses = imported.courses.mapIndexed { index, c -> if (index == 0) c.copy(sections = listOf(1,2,5)) else c }
        val state = PocketState(courses = courses, semesterStart = imported.semesterStart!!, totalWeeks = imported.totalWeeks!!, sectionTimes = imported.sectionTimes)
        val output = TimetableExport.json(state,0)
        assertEquals(3,JSONObject(output).getJSONArray("courses").length())
        assertFalse(output.contains("studentId"))
        val roundTrip = ScheduleParser.parse(output,Campus.LIANHUA)
        assertEquals(listOf(1,2,5),roundTrip.courses.filter { it.name == courses.first().name }.flatMap { it.sections }.sorted())
        val path = System.getProperty("haut.interopOutput") ?: error("Missing interop test output path")
        File(path).apply { parentFile.mkdirs(); writeText(output) }
    }
}
