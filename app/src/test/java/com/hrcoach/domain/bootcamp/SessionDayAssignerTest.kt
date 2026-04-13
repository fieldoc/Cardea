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
    fun `forced consecutive days demotes hard session to EASY`() {
        // 3 hard sessions (TEMPO + INTERVAL + LONG) on 3 consecutive days — third can't fit the gap
        // LONG goes to day 3 (last), TEMPO goes to day 1 (gap=2 from 3), INTERVAL has no valid day
        // (day 2 is gap=1 from both 1 and 3) → INTERVAL demoted to EASY
        val sessions = listOf(
            PlannedSession(SessionType.TEMPO, 25, "aerobic_tempo"),
            PlannedSession(SessionType.INTERVAL, 30, "norwegian_4x4"),
            PlannedSession(SessionType.LONG, 45, "zone2_base")
        )
        val availableDays = listOf(1, 2, 3)
        val assigned = SessionDayAssigner.assign(sessions, availableDays)

        assertEquals("Should still have 3 sessions", 3, assigned.size)

        val hardDays = assigned
            .filter { it.first.type in SessionDayAssigner.HARD_TYPES }
            .map { it.second }
            .sorted()

        // Only LONG and TEMPO should survive as hard; INTERVAL demoted
        assertTrue(
            "At most 2 hard sessions after demotion, but got ${hardDays.size}: $hardDays",
            hardDays.size <= 2
        )
        // Verify the gap constraint holds for remaining hard sessions
        for (i in 0 until hardDays.size - 1) {
            assertTrue(
                "Hard sessions on ${hardDays[i]} and ${hardDays[i+1]} violate gap",
                hardDays[i+1] - hardDays[i] >= 2
            )
        }
        // The demoted session should be EASY with zone2_base preset
        val easySessions = assigned.filter { it.first.type == SessionType.EASY }
        assertTrue("Should have at least 1 EASY after demotion", easySessions.isNotEmpty())
        assertTrue(
            "Demoted sessions should use zone2_base preset",
            easySessions.all { it.first.presetId == "zone2_base" }
        )
    }

    @Test
    fun `impossible spacing with two hard sessions on two consecutive days demotes one`() {
        val sessions = listOf(
            PlannedSession(SessionType.TEMPO, 25, "aerobic_tempo"),
            PlannedSession(SessionType.LONG, 45, "zone2_base")
        )
        val availableDays = listOf(3, 4) // only 2 consecutive days
        val assigned = SessionDayAssigner.assign(sessions, availableDays)

        assertEquals(2, assigned.size)
        val hardCount = assigned.count { it.first.type in SessionDayAssigner.HARD_TYPES }
        // LONG is placed first (anchored), TEMPO can't find a gap ≥ 2, gets demoted
        assertTrue("One hard session should be demoted, got $hardCount hard", hardCount <= 1)
    }

    @Test
    fun `long run bias day conflict demotes tempo when gap violated`() {
        // bias=Tue(2), only Mon(1)/Tue(2)/Wed(3) → LONG on 2, TEMPO on 1 or 3 (gap=1), demoted
        val sessions = listOf(
            PlannedSession(SessionType.TEMPO, 25, "aerobic_tempo"),
            PlannedSession(SessionType.EASY, 30, "zone2_base"),
            PlannedSession(SessionType.LONG, 45, "zone2_base")
        )
        val availableDays = listOf(1, 2, 3)
        val assigned = SessionDayAssigner.assign(sessions, availableDays, longRunBiasDay = 2)

        val longSession = assigned.first { it.first.type == SessionType.LONG }
        assertEquals("Long run should be on bias day 2", 2, longSession.second)

        // TEMPO can only go on day 1 or 3, both are gap=1 from day 2 → demoted
        val tempoSessions = assigned.filter { it.first.type == SessionType.TEMPO }
        assertTrue("TEMPO should be demoted when gap cannot be satisfied", tempoSessions.isEmpty())
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
