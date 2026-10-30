package cn.hautpocket.app.domain

import org.junit.Assert.*
import org.junit.Test

class FocusCompletionTest {
    @Test fun foregroundAndAlarmCompleteOnlyOnceWithoutChangingPreferences() {
        val initial = PocketState(focusEndsAt = 1000, focusMinutes = 25, campus = Campus.SONGSHAN, textColor = "#123456")
        val first = FocusCompletion.finish(initial, 1000, 1000)
        assertEquals(listOf(FocusSession(1000,25)), first.focusSessions)
        assertEquals(0L, first.focusEndsAt)
        assertEquals(Campus.SONGSHAN, first.campus)
        assertEquals("#123456", first.textColor)
        assertEquals(first, FocusCompletion.finish(first,1000,2000))
    }
    @Test fun canceledStaleAndEarlyAlarmsDoNotCompleteAnotherTimer() {
        val newer = PocketState(focusEndsAt = 2000)
        assertEquals(newer, FocusCompletion.finish(newer,1000,3000))
        assertEquals(newer, FocusCompletion.finish(newer,2000,1999))
        val stopped = newer.copy(focusEndsAt = 0)
        assertEquals(stopped, FocusCompletion.finish(stopped,2000,3000))
    }
    @Test fun existingCompletionIsNotDuplicatedDuringRecovery() {
        val state = PocketState(focusEndsAt = 1000, focusSessions = listOf(FocusSession(1000,25)))
        assertEquals(1, FocusCompletion.finish(state,1000,3000).focusSessions.size)
    }
}
