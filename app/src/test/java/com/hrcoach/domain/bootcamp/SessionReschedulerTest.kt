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

    @Test fun moves_to_next_available_preferred_day() {
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(3), session(6))
        )
        val result = SessionRescheduler.reschedule(req)
        assertTrue(result is RescheduleResult.Moved)
        assertEquals(3, (result as RescheduleResult.Moved).newDayOfWeek)
    }

    @Test fun skips_blackout_days() {
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.BLACKOUT,
                6 to DaySelectionLevel.AVAILABLE
            )),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(3), session(6))
        )
        val result = SessionRescheduler.reschedule(req)
        assertEquals(6, (result as RescheduleResult.Moved).newDayOfWeek)
    }

    @Test fun drops_lowest_priority_when_no_slots_available() {
        val easySession  = session(day = 1, type = "EASY",  status = "SKIPPED")
        val tempoSession = session(day = 3, type = "TEMPO")
        val longSession  = session(day = 6, type = "LONG_RUN")
        val req = RescheduleRequest(
            session = tempoSession,
            enrollment = enrollment(),
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
        val req = RescheduleRequest(
            session = session(day = 1, type = "TEMPO"),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                2 to DaySelectionLevel.AVAILABLE,
                3 to DaySelectionLevel.AVAILABLE
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

    @Test fun skips_none_level_days() {
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment(dayPrefs(
                1 to DaySelectionLevel.AVAILABLE,
                4 to DaySelectionLevel.NONE,
                6 to DaySelectionLevel.AVAILABLE
            )),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(6))
        )
        val result = SessionRescheduler.reschedule(req)
        assertEquals(6, (result as RescheduleResult.Moved).newDayOfWeek)
    }
}
