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

    // ── effectiveWarmupGraceSec (added 2026-04-24) ────────────────────────

    @Test
    fun `effectiveWarmupGraceSec returns explicit field when no warm-up segment`() {
        val config = WorkoutConfig(mode = WorkoutMode.STEADY_STATE, steadyStateTargetHr = 140)
        assertEquals(120, config.effectiveWarmupGraceSec())
    }

    @Test
    fun `effectiveWarmupGraceSec respects custom warmupGraceSec`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 140,
            warmupGraceSec = 60
        )
        assertEquals(60, config.effectiveWarmupGraceSec())
    }

    @Test
    fun `effectiveWarmupGraceSec auto-derives from time-based Warm-up segment`() {
        // Aerobic Tempo preset: 600s Warm-up segment first → grace should match.
        val config = WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(durationSeconds = 600, targetHr = 130, label = "Warm-up"),
                HrSegment(durationSeconds = 1200, targetHr = 165, label = "Tempo")
            )
        )
        assertEquals(600, config.effectiveWarmupGraceSec())
    }

    @Test
    fun `effectiveWarmupGraceSec auto-derive is case-insensitive`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(durationSeconds = 300, targetHr = 130, label = "WARM UP")
            )
        )
        assertEquals(300, config.effectiveWarmupGraceSec())
    }

    @Test
    fun `effectiveWarmupGraceSec ignores distance-based warmup segments`() {
        // Race-prep presets use distance-based segments. Don't auto-derive — fall back to
        // the explicit warmupGraceSec default (120) because elapsed-distance ≠ elapsed-time.
        val config = WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(distanceMeters = 1000f, targetHr = 130, label = "Warm-up"),
                HrSegment(distanceMeters = 5000f, targetHr = 160, label = "Race Pace")
            )
        )
        assertEquals(120, config.effectiveWarmupGraceSec())
    }

    @Test
    fun `effectiveWarmupGraceSec returns default for first segment without warm-up label`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(durationSeconds = 300, targetHr = 130, label = "Easy Start"),
                HrSegment(durationSeconds = 600, targetHr = 160, label = "Tempo")
            )
        )
        assertEquals(120, config.effectiveWarmupGraceSec())
    }
}
