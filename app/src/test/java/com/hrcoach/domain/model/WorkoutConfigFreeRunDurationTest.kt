package com.hrcoach.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutConfigFreeRunDurationTest {

    @Test
    fun `totalDurationSeconds returns planned duration for FREE_RUN with plannedDurationMinutes`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.FREE_RUN,
            plannedDurationMinutes = 31
        )
        assertEquals(31L * 60, config.totalDurationSeconds())
    }

    @Test
    fun `totalDurationSeconds returns null for FREE_RUN without plannedDurationMinutes`() {
        val config = WorkoutConfig(mode = WorkoutMode.FREE_RUN)
        assertEquals(null, config.totalDurationSeconds())
    }

    @Test
    fun `totalDurationSeconds returns planned duration for STEADY_STATE with plannedDurationMinutes`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 142,
            plannedDurationMinutes = 51
        )
        assertEquals(51L * 60, config.totalDurationSeconds())
    }

    @Test
    fun `totalDurationSeconds returns null for STEADY_STATE without duration or segments`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 142
        )
        assertEquals(null, config.totalDurationSeconds())
    }

    @Test
    fun `totalDurationSeconds prefers segments over plannedDurationMinutes`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 140,
            plannedDurationMinutes = 30,
            segments = listOf(
                HrSegment(durationSeconds = 1200, targetHr = 140)
            )
        )
        // Segments sum (1200s) takes precedence over plannedDurationMinutes (30*60=1800s)
        assertEquals(1200L, config.totalDurationSeconds())
    }
}
