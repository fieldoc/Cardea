package com.hrcoach.domain.model

import org.junit.Assert.*
import org.junit.Test

class BootcampGoalTest {

    @Test
    fun `each goal has correct tier`() {
        assertEquals(1, BootcampGoal.CARDIO_HEALTH.tier)
        assertEquals(2, BootcampGoal.RACE_5K.tier)
        assertEquals(3, BootcampGoal.HALF_MARATHON.tier)
        assertEquals(4, BootcampGoal.MARATHON.tier)
    }

    @Test
    fun `each goal has correct suggested min minutes`() {
        assertEquals(20, BootcampGoal.CARDIO_HEALTH.suggestedMinMinutes)
        assertEquals(25, BootcampGoal.RACE_5K.suggestedMinMinutes)
        assertEquals(30, BootcampGoal.HALF_MARATHON.suggestedMinMinutes)
        assertEquals(45, BootcampGoal.MARATHON.suggestedMinMinutes)
    }

    @Test
    fun `each goal has correct warn-below minutes`() {
        assertEquals(15, BootcampGoal.CARDIO_HEALTH.warnBelowMinutes)
        assertEquals(20, BootcampGoal.RACE_5K.warnBelowMinutes)
        assertEquals(25, BootcampGoal.HALF_MARATHON.warnBelowMinutes)
        assertEquals(30, BootcampGoal.MARATHON.warnBelowMinutes)
    }

    @Test
    fun `cardio health has no peak or taper phase`() {
        val phases = BootcampGoal.CARDIO_HEALTH.phaseArc
        assertTrue(phases.any { it == TrainingPhase.BASE })
        assertTrue(phases.any { it == TrainingPhase.BUILD })
        assertFalse(phases.any { it == TrainingPhase.PEAK })
        assertFalse(phases.any { it == TrainingPhase.TAPER })
    }

    @Test
    fun `marathon has all four phases`() {
        val phases = BootcampGoal.MARATHON.phaseArc
        assertEquals(
            listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER),
            phases
        )
    }
}
