package com.hrcoach.domain.engine

import org.junit.Assert.*
import org.junit.Test

class SubMaxHrEstimatorTest {

    @Test
    fun `returns null when session too short`() {
        // 5 minutes of HR data (need 10)
        val hrSamples = (1..60).map { HrSample(it * 5_000L, 155) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 191, age = 29, isCalibrated = false)
        assertNull(result)
    }

    @Test
    fun `returns null when already calibrated`() {
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 170) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 191, age = 29, isCalibrated = true)
        assertNull(result)
    }

    @Test
    fun `returns null when sustained peak is well below current max`() {
        // Peak 130, current max 191. 130 < 191+2 → no evidence.
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 130) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 191, age = 29, isCalibrated = false)
        assertNull(result)
    }

    @Test
    fun `returns null when moderate effort - the pre-2026-04-14 mushing case`() {
        // Peak 170, current max 180. 170 < 180+2 → no evidence, no revision.
        // (Old bucket algorithm returned 185 here, biasing HRmax upward on moderate runs.)
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 170) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 180, age = 40, isCalibrated = false)
        assertNull(result)
    }

    @Test
    fun `returns null when peak is just below margin`() {
        // Peak = current + 1 (below 2-bpm margin)
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 181) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 180, age = 40, isCalibrated = false)
        assertNull(result)
    }

    @Test
    fun `updates when peak is exactly at margin`() {
        // Peak = current + 2 → proposed = peak + 1 = current + 3
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 182) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 180, age = 40, isCalibrated = false)
        assertEquals(183, result)
    }

    @Test
    fun `updates conservatively when peak exceeds current max`() {
        // Peak 185, current 180, age 40 → proposed = 185 + 1 = 186, ceiling = 200
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 185) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 180, age = 40, isCalibrated = false)
        assertEquals(186, result)
    }

    @Test
    fun `caps estimate at 220-age+20`() {
        // Peak 210, current 180, age 40 → proposed = 211, ceiling = 200 → cap to 200
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 210) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 180, age = 40, isCalibrated = false)
        assertEquals(200, result)
    }

    @Test
    fun `uses 2-minute rolling average not spike`() {
        // 10 min of data at 140 bpm with a single spike to 200.
        // 2-min rolling avg won't be pushed much by a single spike.
        val hrSamples = (1..120).map { i ->
            val hr = if (i == 60) 200 else 140
            HrSample(i * 5_000L, hr)
        }
        // Sustained peak ~141, current 191 → 141 < 193 → no revision.
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 191, age = 29, isCalibrated = false)
        assertNull(result)
    }
}
