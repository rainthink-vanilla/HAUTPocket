package cn.hautpocket.app.domain

import cn.hautpocket.app.data.StateCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DomainTest {
    @Test fun semesterBoundsDoNotPretendToKnowAnUnsetCalendar() {
        assertNull(semesterWeek("", LocalDate.parse("2026-10-27"), 20))
        assertNull(semesterWeek("2026-09-07", LocalDate.parse("2026-09-06"), 20))
        assertEquals(1, semesterWeek("2026-09-07", LocalDate.parse("2026-09-13"), 20))
        assertEquals(2, semesterWeek("2026-09-07", LocalDate.parse("2026-09-14"), 20))
        assertNull(semesterWeek("2026-09-07", LocalDate.parse("2027-08-01"), 20))
    }
    @Test fun consumptionAccountsForTopUpsAndUnknownRecharge() {
        val previous = Reading(meterId = "a", timestamp = 100, remaining = 10.0)
        assertEquals(5.0, consumption(previous, Reading(meterId = "a", timestamp = 200, remaining = 25.0, addedSincePrevious = 20.0))!!, 0.00001)
        assertEquals(2.0, consumption(previous, Reading(meterId = "a", timestamp = 200, remaining = 8.0, addedSincePrevious = 0.0))!!, 0.00001)
        assertNull(consumption(previous, Reading(meterId = "a", timestamp = 200, remaining = 8.0)))
        assertNull(consumption(previous, Reading(meterId = "b", timestamp = 200, remaining = 8.0, addedSincePrevious = 0.0)))
        assertNull(consumption(previous, Reading(meterId = "a", timestamp = 90, remaining = 8.0, addedSincePrevious = 0.0)))
        assertNull(consumption(previous, Reading(meterId = "a", timestamp = 200, remaining = 20.0, addedSincePrevious = 0.0)))
    }
    @Test fun readingRejectsNonFiniteValues() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            assertTrue(runCatching { Reading(meterId = "a", timestamp = 1, remaining = value) }.isFailure)
        }
    }
    @Test fun removingAnIntermediateReadingInvalidatesOnlyTheNextTopUpInterval() {
        val first = Reading(id = "one", meterId = "a", timestamp = 1, remaining = 10.0)
        val second = Reading(id = "two", meterId = "a", timestamp = 2, remaining = 9.0, addedSincePrevious = 0.0)
        val third = Reading(id = "three", meterId = "a", timestamp = 3, remaining = 12.0, addedSincePrevious = 5.0)
        val other = Reading(id = "other", meterId = "b", timestamp = 4, remaining = 2.0, addedSincePrevious = 0.0)
        val changed = removeReading(listOf(first, second, third, other), "two")
        assertEquals(listOf("one", "three", "other"), changed.map { it.id })
        assertNull(changed[1].addedSincePrevious)
        assertEquals(0.0, changed[2].addedSincePrevious!!, 0.001)
        assertNull(consumption(changed[0], changed[1]))
    }
    @Test fun separatesTwoCampusesThroughPersistence() {
        val a = Meter(campus = Campus.LIANHUA, building = "测试楼", room = "101", kind = MeterKind.ELECTRICITY)
        val b = Meter(campus = Campus.SONGSHAN, building = "测试楼", room = "101", kind = MeterKind.WATER)
        val state = PocketState(campus = Campus.SONGSHAN, meters = listOf(a, b), readings = listOf(
            Reading(meterId = a.id, timestamp = 1, remaining = 12.0),
            Reading(meterId = b.id, timestamp = 2, remaining = 3.0, addedSincePrevious = 0.0)),
            courses = listOf(Course(name = "课程", day = 1, weeks = listOf(1, 3), sections = listOf(1, 2))),
            sectionTimes = listOf(SectionTime(1, "08:30", "09:15")),
            focusEndsAt = 1000, focusMinutes = 15, focusSessions = listOf(FocusSession(500, 25)),
            onlineRooms = listOf(OnlineRoom("hashed-id", "示例房间", listOf(OnlineDevice("测试表", "电", "10元", "1kWh", "100元", "正常")), 999)),
            onlineRoomCampuses = mapOf("hashed-id" to Campus.SONGSHAN))
        val decoded = StateCodec.decode(StateCodec.encode(state))
        assertEquals(state, decoded)
        assertEquals(12.0, decoded.readings.single { it.meterId == a.id }.remaining, 0.001)
        assertEquals(3.0, decoded.readings.single { it.meterId == b.id }.remaining, 0.001)
    }
    @Test fun unknownSchemaAndOrphanReadingsAreRejected() {
        assertTrue(runCatching { StateCodec.decode("{\"version\":2}") }.isFailure)
        assertTrue(runCatching { StateCodec.decode("""{"version":1,"readings":[{"id":"r","meterId":"missing","timestamp":1,"remaining":1}]}""") }.isFailure)
    }
    @Test fun officialUrlPolicyRejectsImpersonationAndUntrustedSchemes() {
        assertTrue(OfficialServices.canBrowse(OfficialServices.PORTAL))
        assertTrue(OfficialServices.canImportSchedule(OfficialServices.TEACHING))
        for (url in listOf("http://jwglxt.haut.edu.cn/jwglxt/", "https://jwglxt.haut.edu.cn.evil.com/jwglxt/",
            "https://evil.com/?next=https://haut.edu.cn", "javascript:alert(1)", "file:///tmp/private", "https://evil@jwglxt.haut.edu.cn/jwglxt/",
            "https://jwglxt.haut.edu.cn:444/jwglxt/")) {
            assertFalse(url, OfficialServices.canBrowse(url))
            assertFalse(url, OfficialServices.canImportSchedule(url))
        }
        assertFalse(OfficialServices.canImportSchedule(OfficialServices.LIBRARY))
    }
}
