package com.hrcoach.domain.model

import org.junit.Assert.*
import org.junit.Test

class WorkoutConfigTest {

    // Segments: 5-min time-based warm-up (120 bpm) + 5 km distance (150 bpm)
    private val mixedConfig = WorkoutConfig(
        mode = WorkoutMode.DISTANCE_PROFILE,
        segments = listOf(
            HrSegment(durationSeconds = 300, distanceMeters = null, targetHr = 120),
            HrSegment(durationSeconds = null, distanceMeters = 5000f, targetHr = 150)
        )
    )

    private val pureTimeConfig = WorkoutConfig(
        mode = WorkoutMode.STEADY_STATE,
        steadyStateTargetHr = 140,
        segments = listOf(
            HrSegment(durationSeconds = 600, distanceMeters = null, targetHr = 140),
            HrSegment(durationSeconds = 600, distanceMeters = null, targetHr = 145)
        )
    )

    private val pureDistConfig = WorkoutConfig(
        mode = WorkoutMode.DISTANCE_PROFILE,
        segments = listOf(
            HrSegment(durationSeconds = null, distanceMeters = 3000f, targetHr = 145),
            HrSegment(durationSeconds = null, distanceMeters = 6000f, targetHr = 155)
        )
    )

    @Test
    fun `hasMixedSegments returns true when both time and distance segments exist`() {
        assertTrue(mixedConfig.hasMixedSegments())
    }

    @Test
    fun `hasMixedSegments returns false for pure time config`() {
        assertFalse(pureTimeConfig.hasMixedSegments())
    }

    @Test
    fun `hasMixedSegments returns false for pure distance config`() {
        assertFalse(pureDistConfig.hasMixedSegments())
    }

    @Test
    fun `hasMixedSegments returns false for empty segments`() {
        assertFalse(WorkoutConfig(mode = WorkoutMode.FREE_RUN).hasMixedSegments())
    }

    @Test
    fun `targetHrForMixed returns time-based target during warm-up period`() {
        // At t=0, distance=0: should be in warm-up (120 bpm), NOT distance zone (150 bpm)
        val target = mixedConfig.targetHrForMixed(elapsedSeconds = 0L, distanceMeters = 0f)
        assertEquals(120, target)
    }

    @Test
    fun `targetHrForMixed returns time-based target mid warm-up`() {
        val target = mixedConfig.targetHrForMixed(elapsedSeconds = 150L, distanceMeters = 200f)
        assertEquals(120, target)
    }

    @Test
    fun `targetHrForMixed switches to distance target after warm-up ends`() {
        // At t=300 (warm-up done), 0m distance: now in distance zone
        val target = mixedConfig.targetHrForMixed(elapsedSeconds = 300L, distanceMeters = 0f)
        assertEquals(150, target)
    }

    @Test
    fun `targetHrForMixed uses distance routing once past all time segments`() {
        val target = mixedConfig.targetHrForMixed(elapsedSeconds = 600L, distanceMeters = 3000f)
        assertEquals(150, target)
    }
}
