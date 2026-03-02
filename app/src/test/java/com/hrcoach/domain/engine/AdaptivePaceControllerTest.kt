package com.hrcoach.domain.engine

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.PaceHrBucket
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePaceControllerTest {

    @Test
    fun `zone status stays tied to actual HR even when projection is above zone`() {
        val controller = AdaptivePaceController(
            config = steadyConfig(target = 145, buffer = 5),
            initialProfile = AdaptiveProfile(
                responseLagSec = 90f,
                totalSessions = 5,
                paceHrBuckets = mapOf(
                    24 to PaceHrBucket(avgHr = 180f, sampleCount = 120)
                )
            )
        )

        controller.evaluateTick(
            nowMs = 0L,
            hr = 110,
            connected = true,
            targetHr = 145,
            distanceMeters = 0f,
            actualZone = zoneFromActual(hr = 110, target = 145, buffer = 5)
        )
        controller.evaluateTick(
            nowMs = 60_000L,
            hr = 145,
            connected = true,
            targetHr = 145,
            distanceMeters = 167f,
            actualZone = zoneFromActual(hr = 145, target = 145, buffer = 5)
        )
        val result = controller.evaluateTick(
            nowMs = 120_000L,
            hr = 145,
            connected = true,
            targetHr = 145,
            distanceMeters = 334f,
            actualZone = zoneFromActual(hr = 145, target = 145, buffer = 5)
        )

        assertEquals(ZoneStatus.IN_ZONE, result.zoneStatus)
        assertEquals(ZoneStatus.ABOVE_ZONE, result.projectedZoneStatus)
        assertTrue(result.guidance.contains("drifting up"))
    }

    @Test
    fun `short term trim corrects prediction error without amplifying above-target HR`() {
        val controller = AdaptivePaceController(
            config = steadyConfig(target = 140, buffer = 5),
            initialProfile = AdaptiveProfile(totalSessions = 5)
        )

        var result = controller.evaluateTick(
            nowMs = 0L,
            hr = 150,
            connected = true,
            targetHr = 140,
            distanceMeters = 0f,
            actualZone = zoneFromActual(hr = 150, target = 140, buffer = 5)
        )

        repeat(12) { step ->
            result = controller.evaluateTick(
                nowMs = (step + 1) * 60_000L,
                hr = 150,
                connected = true,
                targetHr = 140,
                distanceMeters = 0f,
                actualZone = zoneFromActual(hr = 150, target = 140, buffer = 5)
            )
        }

        assertTrue(result.predictedHr in 149..151)
    }

    @Test
    fun `projection horizon remains short even with long learned response lag`() {
        val controller = AdaptivePaceController(
            config = steadyConfig(target = 200, buffer = 5),
            initialProfile = AdaptiveProfile(responseLagSec = 90f, totalSessions = 5)
        )

        controller.evaluateTick(
            nowMs = 0L,
            hr = 140,
            connected = true,
            targetHr = 200,
            distanceMeters = 0f,
            actualZone = zoneFromActual(hr = 140, target = 200, buffer = 5)
        )

        var nowMs = 60_000L
        var hr = 143
        var result = controller.evaluateTick(
            nowMs = nowMs,
            hr = hr,
            connected = true,
            targetHr = 200,
            distanceMeters = 0f,
            actualZone = zoneFromActual(hr = hr, target = 200, buffer = 5)
        )
        repeat(4) {
            nowMs += 60_000L
            hr += 3
            result = controller.evaluateTick(
                nowMs = nowMs,
                hr = hr,
                connected = true,
                targetHr = 200,
                distanceMeters = 0f,
                actualZone = zoneFromActual(hr = hr, target = 200, buffer = 5)
            )
        }

        assertTrue(result.predictedHr - hr <= 2)
    }

    @Test
    fun `cold start reports learning state and hides projection`() {
        val controller = AdaptivePaceController(
            config = steadyConfig(target = 140, buffer = 5),
            initialProfile = AdaptiveProfile()
        )

        val result = controller.evaluateTick(
            nowMs = 0L,
            hr = 140,
            connected = true,
            targetHr = 140,
            distanceMeters = 0f,
            actualZone = zoneFromActual(hr = 140, target = 140, buffer = 5)
        )

        assertFalse(result.hasProjectionConfidence)
        assertEquals(0, result.predictedHr)
        assertTrue(result.guidance.contains("Learning your patterns"))
    }

    @Test
    fun `finishSession populates efficiency and decoupling metrics`() {
        val controller = AdaptivePaceController(
            config = steadyConfig(target = 145, buffer = 5),
            initialProfile = AdaptiveProfile(totalSessions = 3)
        )

        val hrSequence = listOf(142, 143, 150, 152, 153)
        hrSequence.forEachIndexed { index, hr ->
            val nowMs = index * 60_000L
            val distanceMeters = index * 200f
            controller.evaluateTick(
                nowMs = nowMs,
                hr = hr,
                connected = true,
                targetHr = 145,
                distanceMeters = distanceMeters,
                actualZone = zoneFromActual(hr = hr, target = 145, buffer = 5)
            )
        }

        val sessionResult = controller.finishSession(
            workoutId = 200L,
            endedAtMs = 300_000L
        )

        assertNotNull(sessionResult.metrics.efficiencyFactor)
        assertNotNull(sessionResult.metrics.efFirstHalf)
        assertNotNull(sessionResult.metrics.efSecondHalf)
        assertNotNull(sessionResult.metrics.aerobicDecoupling)
        assertTrue((sessionResult.metrics.aerobicDecoupling ?: 0f) > 0f)
    }

    private fun steadyConfig(target: Int, buffer: Int): WorkoutConfig {
        return WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = target,
            bufferBpm = buffer
        )
    }

    private fun zoneFromActual(hr: Int, target: Int, buffer: Int): ZoneStatus {
        val low = target - buffer
        val high = target + buffer
        return when {
            hr < low -> ZoneStatus.BELOW_ZONE
            hr > high -> ZoneStatus.ABOVE_ZONE
            else -> ZoneStatus.IN_ZONE
        }
    }
}
