package cn.hautpocket.app.domain

/** A late or duplicate alarm must never finish a different session. */
object FocusCompletion {
    fun finish(state: PocketState, expectedEnd: Long, now: Long): PocketState {
        if (expectedEnd <= 0 || state.focusEndsAt != expectedEnd || now < expectedEnd) return state
        val sessions = if (state.focusSessions.any { it.finishedAt == expectedEnd }) state.focusSessions
            else state.focusSessions + FocusSession(expectedEnd, state.focusMinutes)
        return state.copy(focusEndsAt = 0, focusSessions = sessions)
    }
}
