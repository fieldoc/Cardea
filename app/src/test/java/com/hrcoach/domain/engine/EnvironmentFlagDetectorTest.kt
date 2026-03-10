package com.hrcoach.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentFlagDetectorTest {

    @Test
    fun `flags session when decoupling high and pace significantly slower than baseline`() {
        val result = EnvironmentFlagDetector.isEnvironmentAffected(
            aerobicDecoupling = 12f,
            sessionAvgGapPace = 6.5f,
            baselineGapPaceAtEquivalentHr = 5.9f
        )

        assertTrue(result)
    }

    @Test
    fun `does not flag when decoupling high but pace normal`() {
        val result = EnvironmentFlagDetector.isEnvironmentAffected(
            aerobicDecoupling = 12f,
            sessionAvgGapPace = 6.1f,
            baselineGapPaceAtEquivalentHr = 6.0f
        )

        assertFalse(result)
    }

    @Test
    fun `does not flag when pace slow but decoupling normal`() {
        val result = EnvironmentFlagDetector.isEnvironmentAffected(
            aerobicDecoupling = 4f,
            sessionAvgGapPace = 6.5f,
            baselineGapPaceAtEquivalentHr = 5.9f
        )

        assertFalse(result)
    }

    @Test
    fun `returns false when baseline is null (no history yet)`() {
        val result = EnvironmentFlagDetector.isEnvironmentAffected(
            aerobicDecoupling = 15f,
            sessionAvgGapPace = 7f,
            baselineGapPaceAtEquivalentHr = null
        )

        assertFalse(result)
    }
}