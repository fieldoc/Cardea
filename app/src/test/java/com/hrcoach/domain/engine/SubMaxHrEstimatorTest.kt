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
    fun `returns null when sustained peak too low`() {
        // Peak at 130 = 68% of 191 -> below 70% threshold -> skip
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 130) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 191, age = 29, isCalibrated = false)
        assertNull(result)
    }

    @Test
    fun `returns null when estimate is below current max`() {
        // Peak at 140 = 73% of 191 -> effort 0.75 -> estimate 140/0.75 = 187 -> 187 < 191 -> null
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 140) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 191, age = 29, isCalibrated = false)
        assertNull(result)
    }

    @Test
    fun `estimates higher max from moderate effort`() {
        // Sustained peak 170, current max 180 (from 220-40)
        // 170/180 = 0.94 -> effort 0.92 -> estimate = 170/0.92 = 185
        // 185 > 180 -> update. ceiling = 220-40+20 = 200. 185 < 200 -> OK
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 170) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 180, age = 40, isCalibrated = false)
        assertEquals(185, result)
    }

    @Test
    fun `caps estimate at 220-age+20`() {
        // Sustained peak 190, current max 180, age 40
        // 190/180 = 1.06 -> effort 0.92 -> estimate = 190/0.92 = 207
        // ceiling = 220-40+20 = 200 -> capped to 200
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 190) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 180, age = 40, isCalibrated = false)
        assertEquals(200, result)
    }

    @Test
    fun `never estimates below 220-age`() {
        // Sustained peak 145, current max 150, age 29
        // 145/150 = 0.97 -> effort 0.92 -> estimate = 145/0.92 = 158
        // floor = 220-29 = 191 -> capped to 191. 191 > 150 -> update to 191
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 145) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 150, age = 29, isCalibrated = false)
        assertEquals(191, result)
    }

    @Test
    fun `uses 2-minute rolling average not spike`() {
        // 10 min of data at 140 BPM with a single spike to 200
        val hrSamples = (1..120).map { i ->
            val hr = if (i == 60) 200 else 140
            HrSample(i * 5_000L, hr)
        }
        // 2-min rolling avg won't be pushed much by a single spike
        // Sustained peak should be ~141, not 200
        // 141/191 = 0.74 -> effort 0.75 -> estimate = 141/0.75 = 188 -> 188 < 191 -> null
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 191, age = 29, isCalibrated = false)
        assertNull(result)
    }
}
