package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import org.junit.Assert.*
import org.junit.Test

class SessionReschedulerTest {

    private fun dayPrefs(vararg specs: Pair<Int, DaySelectionLevel>): List<DayPreference> =
        specs.map { DayPreference(it.first, it.second) }

    private fun enrollment(
        preferredDays: List<DayPreference> = dayPrefs(
            1 to DaySelectionLevel.AVAILABLE,
            3 to DaySelectionLevel.AVAILABLE,
            6 to DaySelectionLevel.AVAILABLE
        )
    ) = BootcampEnrollmentEntity(
        id = 1L, goalType = "CARDIO_HEALTH", targetMinutesPerRun = 30,
        runsPerWeek = 3, preferredDays = preferredDays, startDate = 0L
    )

    private fun session(day: Int, type: String = "EASY", status: String = "SCHEDULED") =
        BootcampSessionEntity(
            id = day.toLong(), enrollmentId = 1L, weekNumber = 1,
            dayOfWeek = day, sessionType = type, targetMinutes = 30, status = status
        )

    @Test fun moves_to_next_available_day() {
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(3), session(6))
        )
        val result = SessionRescheduler.reschedule(req)
        assertTrue(result is RescheduleResult.Moved)
        // Day 2 (Tue) is now available even though it's not a preferred day
        assertEquals(2, (result as RescheduleResult.Moved).newDayOfWeek)
    }

    @Test fun skips_blackout_days() {
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                2 to DaySelectionLevel.BLACKOUT,
                3 to DaySelectionLevel.BLACKOUT,
                4 to DaySelectionLevel.BLACKOUT,
                5 to DaySelectionLevel.BLACKOUT,
                6 to DaySelectionLevel.AVAILABLE
            )),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(6))
        )
        val result = SessionRescheduler.reschedule(req)
        assertEquals(6, (result as RescheduleResult.Moved).newDayOfWeek)
    }

    @Test fun drops_lowest_priority_when_no_slots_available() {
        val easySession  = session(day = 1, type = "EASY",  status = "SKIPPED")
        val tempoSession = session(day = 3, type = "TEMPO")
        val longSession  = session(day = 6, type = "LONG")
        // All remaining days (5,6,7) are either occupied or blackout
        val req = RescheduleRequest(
            session = tempoSession,
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.AVAILABLE,
                5 to DaySelectionLevel.BLACKOUT,
                6 to DaySelectionLevel.AVAILABLE,
                7 to DaySelectionLevel.BLACKOUT
            )),
            todayDayOfWeek = 5,
            occupiedDaysThisWeek = setOf(1, 3, 6),
            allSessionsThisWeek = listOf(easySession, tempoSession, longSession)
        )
        val result = SessionRescheduler.reschedule(req)
        assertTrue(result is RescheduleResult.Dropped)
    }

    @Test fun defer_returns_deferred() {
        val result = SessionRescheduler.defer()
        assertTrue(result is RescheduleResult.Deferred)
    }

    @Test fun respects_48h_recovery_gap_for_hard_sessions() {
        // Rescheduling a TEMPO from day 1. Day 2 has a TEMPO, so day 3 violates
        // recovery gap (adjacent to day 2 hard session). Days 4-7 blackout.
        val req = RescheduleRequest(
            session = session(day = 1, type = "TEMPO"),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                2 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.AVAILABLE,
                4 to DaySelectionLevel.BLACKOUT,
                5 to DaySelectionLevel.BLACKOUT,
                6 to DaySelectionLevel.BLACKOUT,
                7 to DaySelectionLevel.BLACKOUT
            )),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1, 2),
            allSessionsThisWeek = listOf(
                session(1, "TEMPO"),
                session(2, "TEMPO"),
                session(3, "EASY")
            )
        )
        val result = SessionRescheduler.reschedule(req)
        assertTrue(result is RescheduleResult.Dropped)
    }

    @Test fun offers_today_when_not_occupied() {
        val req = RescheduleRequest(
            session = session(day = 4),       // rescheduling Thursday's session
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.AVAILABLE,   // today (Wed)
                4 to DaySelectionLevel.AVAILABLE,
                6 to DaySelectionLevel.AVAILABLE
            )),
            todayDayOfWeek = 3,               // it's Wednesday
            occupiedDaysThisWeek = setOf(1, 4, 6), // Mon, Thu, Sat have sessions
            allSessionsThisWeek = listOf(session(1), session(4), session(6))
        )
        val result = SessionRescheduler.availableDays(req)
        assertTrue("Today (3) should be offered", 3 in result)
    }

    @Test fun excludes_today_when_occupied() {
        val req = RescheduleRequest(
            session = session(day = 4),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.AVAILABLE,
                4 to DaySelectionLevel.AVAILABLE,
                6 to DaySelectionLevel.AVAILABLE
            )),
            todayDayOfWeek = 3,
            occupiedDaysThisWeek = setOf(1, 3, 4, 6), // today IS occupied
            allSessionsThisWeek = listOf(session(1), session(3), session(4), session(6))
        )
        val result = SessionRescheduler.availableDays(req)
        assertFalse("Today (3) should NOT be offered when occupied", 3 in result)
    }

    @Test fun allows_non_preferred_days_for_reschedule() {
        // Preferred days are Mon(1), Wed(3), Sat(6) — but Tue(2) should still
        // be offered because rescheduling allows any non-BLACKOUT day.
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(),  // prefs: 1, 3, 6
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(3), session(6))
        )
        val result = SessionRescheduler.availableDays(req)
        assertTrue("Non-preferred day 2 (Tue) should be available", 2 in result)
        assertTrue("Non-preferred day 4 (Thu) should be available", 4 in result)
        assertTrue("Non-preferred day 5 (Fri) should be available", 5 in result)
    }

    @Test fun still_skips_none_level_days_that_are_blackout() {
        // NONE-level days are now allowed, but BLACKOUT days are still excluded.
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                4 to DaySelectionLevel.BLACKOUT,
                6 to DaySelectionLevel.AVAILABLE
            )),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(6))
        )
        val result = SessionRescheduler.availableDays(req)
        assertFalse("BLACKOUT day 4 should NOT be offered", 4 in result)
    }

    @Test fun long_run_has_highest_drop_priority() {
        val easySession = session(day = 1, type = "EASY", status = "SCHEDULED")
        val tempoSession = session(day = 3, type = "TEMPO")
        val longSession = session(day = 6, type = "LONG")
        val req = RescheduleRequest(
            session = tempoSession,
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.AVAILABLE,
                5 to DaySelectionLevel.BLACKOUT,
                6 to DaySelectionLevel.AVAILABLE,
                7 to DaySelectionLevel.BLACKOUT
            )),
            todayDayOfWeek = 5,
            occupiedDaysThisWeek = setOf(1, 3, 6),
            allSessionsThisWeek = listOf(easySession, tempoSession, longSession)
        )
        val result = SessionRescheduler.reschedule(req) as RescheduleResult.Dropped
        assertEquals("Should drop EASY, not LONG", easySession.id, result.droppedSessionId)
    }

    @Test fun race_sim_has_highest_drop_priority() {
        val easySession = session(day = 1, type = "EASY", status = "SCHEDULED")
        val tempoSession = session(day = 3, type = "TEMPO")
        val raceSimSession = session(day = 6, type = "RACE_SIM")
        val req = RescheduleRequest(
            session = tempoSession,
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.AVAILABLE,
                5 to DaySelectionLevel.BLACKOUT,
                6 to DaySelectionLevel.AVAILABLE,
                7 to DaySelectionLevel.BLACKOUT
            )),
            todayDayOfWeek = 5,
            occupiedDaysThisWeek = setOf(1, 3, 6),
            allSessionsThisWeek = listOf(easySession, tempoSession, raceSimSession)
        )
        val result = SessionRescheduler.reschedule(req) as RescheduleResult.Dropped
        assertEquals("Should drop EASY, not RACE_SIM", easySession.id, result.droppedSessionId)
    }

    @Test fun respects_48h_recovery_gap_for_long_runs() {
        val req = RescheduleRequest(
            session = session(day = 1, type = "EASY"),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                2 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.AVAILABLE,
                4 to DaySelectionLevel.BLACKOUT,
                5 to DaySelectionLevel.BLACKOUT,
                6 to DaySelectionLevel.BLACKOUT,
                7 to DaySelectionLevel.BLACKOUT
            )),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1, 3),
            allSessionsThisWeek = listOf(
                session(1, "EASY"),
                session(3, "LONG")
            )
        )
        val days = SessionRescheduler.availableDays(req)
        assertFalse("Day 2 violates recovery gap with LONG on day 3", 2 in days)
    }
}
