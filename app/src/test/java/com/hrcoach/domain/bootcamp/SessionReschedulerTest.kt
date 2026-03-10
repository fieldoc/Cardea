package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import org.junit.Assert.*
import org.junit.Test

class SessionReschedulerTest {

    private fun enrollment(preferredDays: String = "1:AVAILABLE,3:AVAILABLE,6:AVAILABLE") =
        BootcampEnrollmentEntity(
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
            enrollment = enrollment("1:AVAILABLE,3:BLACKOUT,6:AVAILABLE"),
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
            enrollment = enrollment("1:AVAILABLE,3:AVAILABLE,6:AVAILABLE"),
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
            enrollment = enrollment("1:AVAILABLE,2:AVAILABLE,3:AVAILABLE"),
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

    @Test fun skips_none_level_days() {
        val req = RescheduleRequest(
            session = session(day = 1),
            enrollment = enrollment("1:AVAILABLE,4:NONE,6:AVAILABLE"),
            todayDayOfWeek = 1,
            occupiedDaysThisWeek = setOf(1),
            allSessionsThisWeek = listOf(session(1), session(6))
        )
        val result = SessionRescheduler.reschedule(req)
        assertEquals(6, (result as RescheduleResult.Moved).newDayOfWeek)
    }
}