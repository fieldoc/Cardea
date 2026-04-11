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
}
