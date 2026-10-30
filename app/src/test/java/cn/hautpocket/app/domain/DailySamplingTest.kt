package cn.hautpocket.app.domain

import cn.hautpocket.app.data.DailySampling
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class DailySamplingTest {
    private fun date(value: String) = ZonedDateTime.parse(value).withZoneSameInstant(DailySampling.zone)
    private fun device(name: String, value: String) = OnlineDevice(name, "电", "100", value, "", "")

    @Test fun crossesMidnightWithoutAssigningNewDayToPreviousDay() {
        val before = date("2026-10-29T23:56:00+08:00")
        val after = date("2026-10-30T00:01:00+08:00")
        assertFalse(DailySampling.sameDay(before.toInstant().toEpochMilli(), after.toInstant().toEpochMilli()))
        val room = OnlineRoom("r", "房间", listOf(device("照明", "3.2 度")), 1)
        assertEquals("2026-10-29", DailySampling.samples(listOf(room), before.toInstant().toEpochMilli()).single().date)
        assertEquals("2026-10-30", DailySampling.samples(listOf(room), after.toInstant().toEpochMilli()).single().date)
    }
    @Test fun savesDaytimeSamplesAndChecksMoreOftenAtNight() {
        fun ms(value: String) = date(value).toInstant().toEpochMilli()
        val morning = ms("2026-10-30T10:00:00+08:00")
        assertFalse(DailySampling.due(morning, morning - 30 * 60_000L))
        assertTrue(DailySampling.due(morning, morning - 60 * 60_000L))
        val night = ms("2026-10-30T23:15:00+08:00")
        assertTrue(DailySampling.due(night, night - 15 * 60_000L))
        assertFalse(DailySampling.due(night, night - 10 * 60_000L))
        assertTrue(DailySampling.due(ms("2026-10-31T00:01:00+08:00"), ms("2026-10-30T23:59:00+08:00")))
        assertTrue(DailySampling.due(morning, 0))
        val room = OnlineRoom("r", "房间", listOf(device("照明", "1.2")), 1)
        assertEquals("2026-10-30", DailySampling.samples(listOf(room), morning).single().date)
    }
    @Test fun skipsFailedAmbiguousDevicesAndInvalidOrMonetaryValues() {
        val time = date("2026-10-29T23:56:00+08:00").toInstant().toEpochMilli()
        val room = OnlineRoom("a", "房间", listOf(device("重复", "1"), device("重复", "2"), device("空调", "2元"), device("照明", "3.2 kWh")), 1)
        val failed = OnlineRoom("b", "失败房间", listOf(device("照明", "8")), 1, failed = true)
        assertEquals(listOf("照明"), DailySampling.samples(listOf(room, failed),time).map { it.device })
        assertNull(DailySampling.value("—")); assertNull(DailySampling.value("NaN")); assertNull(DailySampling.value("-1"))
        assertEquals("kWh", DailySampling.value("2.5")!!.second)
        assertEquals("kWh", DailySampling.value("2.5 kWh")!!.second)
        assertEquals(2.5 to "kWh", DailySampling.value("2.5 度"))
        assertEquals(2.5 to "kWh", DailySampling.value("2.5 kW·h"))
    }
}
