package com.hrcoach.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HrCalibratorTest {

    @Test
    fun `detectNewHrMax returns new max when sustained above threshold`() {
        val samples = List(10) { 195 }
        val result = HrCalibrator.detectNewHrMax(
            currentHrMax = 185,
            recentSamples = samples,
            windowSec = 8
        )
        assertEquals(195, result)
    }

    @Test
    fun `detectNewHrMax returns null when not sustained long enough`() {
        val samples = List(5) { 195 } + List(5) { 170 }
        val result = HrCalibrator.detectNewHrMax(
            currentHrMax = 185,
            recentSamples = samples,
            windowSec = 8
        )
        assertNull(result)
    }

    @Test
    fun `detectNewHrMax returns null when below current max`() {
        val samples = List(10) { 180 }
        val result = HrCalibrator.detectNewHrMax(
            currentHrMax = 185,
            recentSamples = samples,
            windowSec = 8
        )
        assertNull(result)
    }

    @Test
    fun `updateHrRest returns lower value when candidate is lower by 2+`() {
        val updated = HrCalibrator.updateHrRest(currentHrRest = 62f, candidate = 58f)
        assertEquals(58f, updated, 0.01f)
    }

    @Test
    fun `updateHrRest ignores candidate within 2 bpm of current`() {
        val updated = HrCalibrator.updateHrRest(currentHrRest = 62f, candidate = 61f)
        assertEquals(62f, updated, 0.01f)
    }
}
