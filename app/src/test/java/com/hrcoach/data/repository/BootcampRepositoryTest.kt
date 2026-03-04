package com.hrcoach.data.repository

import com.hrcoach.domain.model.BootcampGoal
import org.junit.Assert.*
import org.junit.Test

class BootcampRepositoryTest {

    @Test
    fun `createEnrollment builds entity with correct fields`() {
        val entity = BootcampRepository.buildEnrollmentEntity(
            goal = BootcampGoal.MARATHON,
            targetMinutesPerRun = 45,
            runsPerWeek = 4,
            preferredDays = listOf(1, 3, 5, 6),
            startDate = 1709424000000L
        )
        assertEquals("MARATHON", entity.goalType)
        assertEquals(45, entity.targetMinutesPerRun)
        assertEquals(4, entity.runsPerWeek)
        assertEquals("[1,3,5,6]", entity.preferredDays)
        assertEquals("ACTIVE", entity.status)
        assertEquals(0, entity.currentPhaseIndex)
        assertEquals(0, entity.currentWeekInPhase)
    }

    @Test
    fun `buildSessionEntity creates session with correct defaults`() {
        val entity = BootcampRepository.buildSessionEntity(
            enrollmentId = 1L,
            weekNumber = 2,
            dayOfWeek = 3,
            sessionType = "TEMPO",
            targetMinutes = 30,
            presetId = "aerobic_tempo"
        )
        assertEquals(1L, entity.enrollmentId)
        assertEquals(2, entity.weekNumber)
        assertEquals(3, entity.dayOfWeek)
        assertEquals("TEMPO", entity.sessionType)
        assertEquals(30, entity.targetMinutes)
        assertEquals("aerobic_tempo", entity.presetId)
        assertEquals("SCHEDULED", entity.status)
        assertNull(entity.completedWorkoutId)
    }
}
