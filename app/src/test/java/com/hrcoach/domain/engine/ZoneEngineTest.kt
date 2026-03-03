package com.hrcoach.domain.engine

import com.hrcoach.domain.model.HrSegment
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoneEngineTest {

    private lateinit var engine: ZoneEngine

    @Test
    fun `steady state - in zone returns IN_ZONE`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 140,
            bufferBpm = 5
        )
        engine = ZoneEngine(config)
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 140, distanceMeters = 0f))
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 135, distanceMeters = 0f))
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 145, distanceMeters = 0f))
    }

    @Test
    fun `steady state - above zone returns ABOVE_ZONE`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 140,
            bufferBpm = 5
        )
        engine = ZoneEngine(config)
        assertEquals(ZoneStatus.ABOVE_ZONE, engine.evaluate(hr = 146, distanceMeters = 0f))
        assertEquals(ZoneStatus.ABOVE_ZONE, engine.evaluate(hr = 180, distanceMeters = 0f))
    }

    @Test
    fun `steady state - below zone returns BELOW_ZONE`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 140,
            bufferBpm = 5
        )
        engine = ZoneEngine(config)
        assertEquals(ZoneStatus.BELOW_ZONE, engine.evaluate(hr = 134, distanceMeters = 0f))
        assertEquals(ZoneStatus.BELOW_ZONE, engine.evaluate(hr = 100, distanceMeters = 0f))
    }

    @Test
    fun `distance profile - selects correct segment based on distance`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(distanceMeters = 5000f, targetHr = 140),
                HrSegment(distanceMeters = 7000f, targetHr = 160),
                HrSegment(distanceMeters = 8000f, targetHr = 180)
            ),
            bufferBpm = 5
        )
        engine = ZoneEngine(config)

        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 140, distanceMeters = 3000f))
        assertEquals(ZoneStatus.BELOW_ZONE, engine.evaluate(hr = 140, distanceMeters = 6000f))
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 180, distanceMeters = 7500f))
    }

    @Test
    fun `distance profile - past last segment uses last target`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(HrSegment(distanceMeters = 5000f, targetHr = 140)),
            bufferBpm = 5
        )
        engine = ZoneEngine(config)
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 140, distanceMeters = 10000f))
    }

    @Test
    fun `custom buffer width is respected`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = 150,
            bufferBpm = 10
        )
        engine = ZoneEngine(config)
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 140, distanceMeters = 0f))
        assertEquals(ZoneStatus.IN_ZONE, engine.evaluate(hr = 160, distanceMeters = 0f))
        assertEquals(ZoneStatus.BELOW_ZONE, engine.evaluate(hr = 139, distanceMeters = 0f))
        assertEquals(ZoneStatus.ABOVE_ZONE, engine.evaluate(hr = 161, distanceMeters = 0f))
    }

    @Test
    fun `distance profile - segment with null distanceMeters is ignored by distance lookup`() {
        val config = WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(durationSeconds = 240, targetHr = 160, label = "Interval 1")
            ),
            bufferBpm = 5
        )
        // Time-based segment: targetHrAtDistance should return null (no distance milestones)
        assertEquals(null, config.targetHrAtDistance(500f))
    }
}
