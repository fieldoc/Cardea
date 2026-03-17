package com.hrcoach.data.db

import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import org.junit.Assert.*
import org.junit.Test

class BootcampEntityTest {

    @Test
    fun `enrollment entity has correct defaults`() {
        val enrollment = BootcampEnrollmentEntity(
            goalType = "MARATHON",
            targetMinutesPerRun = 45,
            runsPerWeek = 4,
            preferredDays = listOf(1, 3, 5, 6).map { DayPreference(it, DaySelectionLevel.AVAILABLE) },
            startDate = 1000L
        )
        assertEquals(0L, enrollment.id)
        assertEquals("ACTIVE", enrollment.status)
        assertEquals(0, enrollment.currentPhaseIndex)
        assertEquals(0, enrollment.currentWeekInPhase)
    }

    @Test
    fun `session entity has correct defaults`() {
        val session = BootcampSessionEntity(
            enrollmentId = 1L,
            weekNumber = 1,
            dayOfWeek = 2,
            sessionType = "EASY",
            targetMinutes = 30
        )
        assertEquals(0L, session.id)
        assertEquals("SCHEDULED", session.status)
        assertNull(session.presetId)
        assertNull(session.completedWorkoutId)
    }
}
