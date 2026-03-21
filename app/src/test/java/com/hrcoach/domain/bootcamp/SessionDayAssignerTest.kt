package com.hrcoach.domain.bootcamp

import org.junit.Assert.*
import org.junit.Test

class SessionDayAssignerTest {

    @Test
    fun `hard sessions are never on consecutive days`() {
        val sessions = listOf(
            PlannedSession(SessionType.EASY, 30, "zone2_base"),
            PlannedSession(SessionType.TEMPO, 25, "aerobic_tempo"),
            PlannedSession(SessionType.LONG, 45, "zone2_base")
        )
        val availableDays = listOf(4, 5, 6) // Thu, Fri, Sat — worst case consecutive
        val assigned = SessionDayAssigner.assign(sessions, availableDays)

        val hardDays = assigned
            .filter { it.first.type in SessionDayAssigner.HARD_TYPES }
            .map { it.second }
            .sorted()

        for (i in 0 until hardDays.size - 1) {
            assertTrue(
                "Hard sessions on days ${hardDays[i]} and ${hardDays[i+1]} are consecutive",
                hardDays[i+1] - hardDays[i] >= 2
            )
        }
    }

    @Test
    fun `long run bias day gets the LONG session`() {
        val sessions = listOf(
            PlannedSession(SessionType.EASY, 30, "zone2_base"),
            PlannedSession(SessionType.EASY, 30, "zone2_base"),
            PlannedSession(SessionType.LONG, 45, "zone2_base")
        )
        val availableDays = listOf(1, 3, 6) // Mon, Wed, Sat
        val longRunBiasDay = 6
        val assigned = SessionDayAssigner.assign(sessions, availableDays, longRunBiasDay)
        val longSession = assigned.first { it.first.type == SessionType.LONG }
        assertEquals("Long run should be on bias day", 6, longSession.second)
    }

    @Test
    fun `all sessions are assigned to available days`() {
        val sessions = listOf(
            PlannedSession(SessionType.EASY, 30, "zone2_base"),
            PlannedSession(SessionType.TEMPO, 25, "aerobic_tempo"),
            PlannedSession(SessionType.INTERVAL, 30, "norwegian_4x4"),
            PlannedSession(SessionType.LONG, 50, "zone2_base")
        )
        val availableDays = listOf(1, 3, 5, 7) // Well-spaced
        val assigned = SessionDayAssigner.assign(sessions, availableDays)
        assertEquals(sessions.size, assigned.size)
        assigned.forEach { assertTrue("Day ${it.second} should be available", it.second in availableDays) }
    }

    @Test
    fun `empty sessions returns empty list`() {
        val assigned = SessionDayAssigner.assign(emptyList(), listOf(1, 3, 5))
        assertTrue(assigned.isEmpty())
    }

    @Test
    fun `single session assigned to first available day`() {
        val sessions = listOf(PlannedSession(SessionType.EASY, 30, "zone2_base"))
        val assigned = SessionDayAssigner.assign(sessions, listOf(3, 5, 7))
        assertEquals(1, assigned.size)
        assertEquals(3, assigned[0].second)
    }

    @Test
    fun `well-spaced days keep hard sessions maximally spread`() {
        val sessions = listOf(
            PlannedSession(SessionType.EASY, 30, "zone2_base"),
            PlannedSession(SessionType.TEMPO, 25, "aerobic_tempo"),
            PlannedSession(SessionType.INTERVAL, 30, "norwegian_4x4"),
            PlannedSession(SessionType.LONG, 50, "zone2_base")
        )
        val availableDays = listOf(1, 3, 5, 7)
        val assigned = SessionDayAssigner.assign(sessions, availableDays)

        // Hard types: TEMPO, INTERVAL, LONG — all should be on non-consecutive days
        val hardDays = assigned
            .filter { it.first.type in SessionDayAssigner.HARD_TYPES }
            .map { it.second }
            .sorted()

        for (i in 0 until hardDays.size - 1) {
            assertTrue(
                "Hard sessions on days ${hardDays[i]} and ${hardDays[i+1]} should not be consecutive",
                hardDays[i+1] - hardDays[i] >= 2
            )
        }
    }
}
