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
        // "climbing" (HIGH confidence) or "trending up" (MEDIUM) — 2026-04-17 phrasing update.
        assertTrue(result.guidance.contains("climbing") || result.guidance.contains("trending up"))
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

    // ── BUG 1: hrSlopeBpmPerMin not clamped ──────────────────────────────────
    @Test
    fun `BLE spike does not produce wildly inflated projected HR`() {
        val controller = AdaptivePaceController(
            config = steadyConfig(target = 145, buffer = 5),
            initialProfile = AdaptiveProfile(totalSessions = 5, responseLagSec = 25f)
        )
        val t0 = 1_000_000L
        // Baseline tick — initialises slope tracker
        controller.evaluateTick(
            nowMs = t0, hr = 145, connected = true, targetHr = 145,
            distanceMeters = 0f, actualZone = ZoneStatus.IN_ZONE
        )
        // BLE artifact: HR jumps 40 BPM in 3 seconds (deltaMin = 0.05)
        // Unclamped: instSlope = 40/0.05 = 800 BPM/min → hrSlopeBpmPerMin ≈ 200
        // Projection contribution at 15 s horizon: 200 * (15/60) = 50 BPM → predictedHr ≈ 237
        val result = controller.evaluateTick(
            nowMs = t0 + 3_000L, hr = 185, connected = true, targetHr = 145,
            distanceMeters = 15f, actualZone = ZoneStatus.ABOVE_ZONE
        )
        // With fix (instSlope clamped to ±30 BPM/min): hrSlopeBpmPerMin ≈ 7.5
        // Projection: 185 + 7.5*(15/60) ≈ 187 → predictedHr ≤ 195
        assertTrue(
            "BLE spike produced predictedHr=${result.predictedHr}, expected ≤ 195",
            result.predictedHr <= 195
        )
    }

    // ── Settle-lag: direction-equal averaging ────────────────────────────────
    @Test
    fun `finishSession averages settle direction means equally so slow build-ups are not drowned out`() {
        val controller = AdaptivePaceController(
            config = steadyConfig(target = 145, buffer = 5),
            initialProfile = AdaptiveProfile(responseLagSec = 25f)
        )
        var t = 1_000_000L
        fun tick(zone: ZoneStatus) = controller.evaluateTick(
            nowMs = t,
            hr = when (zone) { ZoneStatus.ABOVE_ZONE -> 155; ZoneStatus.BELOW_ZONE -> 135; else -> 145 },
            connected = true, targetHr = 145, distanceMeters = 0f, actualZone = zone
        )
        // 3 settle-down events of 10 s each (ABOVE → IN_ZONE)
        repeat(3) {
            tick(ZoneStatus.ABOVE_ZONE); t += 10_000L
            tick(ZoneStatus.IN_ZONE);   t += 30_000L
        }
        // 1 settle-up event of 90 s (BELOW → IN_ZONE)
        tick(ZoneStatus.BELOW_ZONE); t += 90_000L
        tick(ZoneStatus.IN_ZONE)

        val result = controller.finishSession(workoutId = 1L, endedAtMs = t)

        // Direction-equal average: down avg = 10s, up avg = 90s → blend = (10+90)/2 = 50s
        // → EMA: 25×0.85 + 50×0.15 = 28.75s
        // The slow build-up (90s) gets a full vote rather than being diluted by 3 quick
        // corrections — this gives the projection engine a more conservative horizon
        // that catches slow HR climbs before they overshoot.
        assertTrue(
            "responseLagSec should be ≈28.75 (direction-equal), got ${result.updatedProfile.responseLagSec}",
            result.updatedProfile.responseLagSec in 27f..31f
        )
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
