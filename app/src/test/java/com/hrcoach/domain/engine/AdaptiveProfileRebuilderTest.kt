package com.hrcoach.domain.engine

import com.hrcoach.data.db.TrackPointEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.util.JsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveProfileRebuilderTest {

    private fun steadyStateConfig(targetHr: Int = 140, buffer: Int = 5): String =
        JsonCodec.gson.toJson(
            WorkoutConfig(
                mode = WorkoutMode.STEADY_STATE,
                steadyStateTargetHr = targetHr,
                bufferBpm = buffer
            )
        )

    private fun workout(
        id: Long,
        startTime: Long = 1_000_000L + id * 3_600_000L,
        endTime: Long = startTime + 1_800_000L,
        config: String = steadyStateConfig()
    ) = WorkoutEntity(
        id = id,
        startTime = startTime,
        endTime = endTime,
        totalDistanceMeters = 5000f,
        mode = "STEADY_STATE",
        targetConfig = config,
        isSimulated = false
    )

    private fun trackPoints(workoutId: Long, startTime: Long, count: Int = 20): List<TrackPointEntity> =
        (0 until count).map { i ->
            TrackPointEntity(
                id = workoutId * 100 + i,
                workoutId = workoutId,
                timestamp = startTime + i * 5_000L,
                latitude = 40.7128 + i * 0.0001,
                longitude = -74.0060,
                heartRate = 138 + (i % 5),
                distanceMeters = i * 250f
            )
        }

    @Test
    fun `empty history produces default profile`() {
        val result = AdaptiveProfileRebuilder.rebuild(
            workouts = emptyList(),
            trackPointsByWorkout = emptyMap(),
            metricsByWorkout = emptyMap(),
            isWorkoutRunning = { false }
        )
        assertEquals(AdaptiveProfile(), result)
    }

    @Test
    fun `single workout increases totalSessions to 1`() {
        val w = workout(id = 1)
        val result = AdaptiveProfileRebuilder.rebuild(
            workouts = listOf(w),
            trackPointsByWorkout = mapOf(1L to trackPoints(1, w.startTime)),
            metricsByWorkout = emptyMap(),
            isWorkoutRunning = { false }
        )
        assertEquals(1, result.totalSessions)
    }

    @Test
    fun `two workouts produce totalSessions 2`() {
        val w1 = workout(id = 1)
        val w2 = workout(id = 2)
        val result = AdaptiveProfileRebuilder.rebuild(
            workouts = listOf(w1, w2),
            trackPointsByWorkout = mapOf(
                1L to trackPoints(1, w1.startTime),
                2L to trackPoints(2, w2.startTime)
            ),
            metricsByWorkout = emptyMap(),
            isWorkoutRunning = { false }
        )
        assertEquals(2, result.totalSessions)
    }

    @Test
    fun `trimp score from metrics updates CTL and ATL above zero`() {
        val w = workout(id = 1)
        val metrics = WorkoutAdaptiveMetrics(
            workoutId = 1L,
            recordedAtMs = w.endTime,
            trimpScore = 50f
        )
        val result = AdaptiveProfileRebuilder.rebuild(
            workouts = listOf(w),
            trackPointsByWorkout = mapOf(1L to trackPoints(1, w.startTime)),
            metricsByWorkout = mapOf(1L to metrics),
            isWorkoutRunning = { false }
        )
        assertTrue("CTL should increase from 0", result.ctl > 0f)
        assertTrue("ATL should increase from 0", result.atl > 0f)
    }

    @Test
    fun `isWorkoutRunning lambda is called during rebuild`() {
        var called = false
        AdaptiveProfileRebuilder.rebuild(
            workouts = emptyList(),
            trackPointsByWorkout = emptyMap(),
            metricsByWorkout = emptyMap(),
            isWorkoutRunning = { called = true; false }
        )
        assertTrue("isWorkoutRunning lambda must be called", called)
    }

    @Test
    fun `workout with no track points is skipped gracefully`() {
        val w = workout(id = 1)
        val result = AdaptiveProfileRebuilder.rebuild(
            workouts = listOf(w),
            trackPointsByWorkout = emptyMap(),
            metricsByWorkout = emptyMap(),
            isWorkoutRunning = { false }
        )
        assertEquals(0, result.totalSessions)
    }

    @Test
    fun `hrMax set to highest observed HR across track points`() {
        val w = workout(id = 1)
        val points = trackPoints(1, w.startTime, count = 5).mapIndexed { i, p ->
            p.copy(heartRate = 140 + i * 5) // 140, 145, 150, 155, 160
        }
        val result = AdaptiveProfileRebuilder.rebuild(
            workouts = listOf(w),
            trackPointsByWorkout = mapOf(1L to points),
            metricsByWorkout = emptyMap(),
            isWorkoutRunning = { false }
        )
        assertEquals(160, result.hrMax)
    }
}
