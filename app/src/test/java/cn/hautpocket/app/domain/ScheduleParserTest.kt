package cn.hautpocket.app.domain

import org.junit.Assert.*
import org.junit.Test

class ScheduleParserTest {
    @Test fun parsesOddEvenAndSeparateWeekRanges() {
        assertEquals(listOf(1, 3, 5, 7, 10, 12), ScheduleParser.weeks("1-8周(单),10-12周(双)"))
        assertEquals(listOf(1, 2, 3, 9, 10, 12), ScheduleParser.weeks("1-3周，9-10周、12周"))
    }
    @Test fun preservesNonContiguousSections() {
        assertEquals(listOf(1, 2, 5, 7, 8), ScheduleParser.sections("1-2,5,7-8节"))
        assertEquals("1–2、5、7–8 节", sectionSummary(listOf(1, 2, 5, 7, 8)))
    }
    @Test fun rejectsReversedOrExcessiveRanges() {
        for (text in listOf("16-1周", "1-99999周", "0-3周", "", "全部周")) {
            assertTrue(text, runCatching { ScheduleParser.weeks(text) }.isFailure)
        }
        assertTrue(runCatching { ScheduleParser.sections("1-100000") }.isFailure)
    }
    @Test fun interpretsZhengfangAndDetectsCampusWithoutOverwritingLocation() {
        val result = ScheduleParser.parse("""{"kbList":[
            {"kcmc":"线性代数","xqj":"2","zcd":"1-16周(双)","jc":"1-2","cdmc":"嵩山路 1号楼101","xm":"教师甲"},
            {"kcmc":"大学物理","xqj":3,"zcd":"1-8周","jcs":"3-4","cdmc":"莲花街 2号楼102"}
        ]}""", Campus.LIANHUA)
        assertEquals(2, result.courses.size)
        assertEquals(Campus.SONGSHAN, result.courses[0].campus)
        assertEquals("嵩山路 1号楼101", result.courses[0].location)
        assertEquals((2..16 step 2).toList(), result.courses[0].weeks)
        assertEquals(Campus.LIANHUA, result.courses[1].campus)
    }
    @Test fun reportsRejectedRowsAndRemovesExactDuplicates() {
        val row = """{"kcmc":"测试课","xqj":"1","zcd":"1-2周","jc":"1-2"}"""
        val result = ScheduleParser.parse("""{"kbList":[$row,$row,{"kcmc":"坏数据"}]}""", Campus.SONGSHAN)
        assertEquals(1, result.courses.size)
        assertEquals(1, result.skipped)
        assertEquals(Campus.SONGSHAN, result.courses[0].campus)
    }
    @Test fun readsPortableFormat() {
        val result = ScheduleParser.parse("""{"courses":[{"name":"课程","day":7,"weeks":[1,3],"sections":[3,5],"campus":"SONGSHAN"}]}""", Campus.LIANHUA)
        assertEquals(listOf(3, 5), result.courses.single().sections)
        assertEquals(Campus.SONGSHAN, result.courses.single().campus)
    }
    @Test fun rejectsLoginPagesAndEmptyResults() {
        for (text in listOf("<html>请登录</html>", "{}", "{\"kbList\":[]}")) {
            assertTrue(runCatching { ScheduleParser.parse(text, Campus.LIANHUA) }.isFailure)
        }
    }
    @Test fun coursesFilterByDayAndActualWeek() {
        val odd = Course(name = "单周课", day = 1, weeks = listOf(1, 3), sections = listOf(3, 4))
        val early = Course(name = "早课", day = 1, weeks = listOf(1, 2), sections = listOf(1, 2))
        assertEquals(listOf(early, odd), coursesOn(listOf(odd, early), 1, 1))
        assertEquals(listOf(early), coursesOn(listOf(odd, early), 1, 2))
        assertTrue(coursesOn(listOf(odd, early), 2, 1).isEmpty())
    }
}
