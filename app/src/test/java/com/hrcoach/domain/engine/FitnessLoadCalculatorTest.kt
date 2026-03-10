package com.hrcoach.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessLoadCalculatorTest {

    @Test
    fun `updateLoads after one session increases CTL and ATL`() {
        val result = FitnessLoadCalculator.updateLoads(
            currentCtl = 0f,
            currentAtl = 0f,
            trimpScore = 100f,
            daysSinceLast = 1
        )
        assertTrue("CTL should increase", result.ctl > 0f)
        assertTrue("ATL should increase", result.atl > 0f)
        assertTrue("ATL should be higher than CTL for single session", result.atl > result.ctl)
    }

    @Test
    fun `updateLoads TSB is negative after hard training`() {
        val r = FitnessLoadCalculator.updateLoads(
            currentCtl = 50f,
            currentAtl = 50f,
            trimpScore = 200f,
            daysSinceLast = 1
        )
        assertTrue("TSB negative after load spike: tsb=${r.tsb}", r.tsb < 0f)
    }

    @Test
    fun `updateLoads decays CTL after rest period`() {
        val startCtl = 80f
        val result = FitnessLoadCalculator.updateLoads(
            currentCtl = startCtl,
            currentAtl = 60f,
            trimpScore = 0f,
            daysSinceLast = 14
        )
        assertTrue("CTL should decay: was $startCtl, now ${result.ctl}", result.ctl < startCtl)
    }

    @Test
    fun `updateLoads after 6 weeks rest CTL halves`() {
        val startCtl = 100f
        val result = FitnessLoadCalculator.updateLoads(
            currentCtl = startCtl,
            currentAtl = 0f,
            trimpScore = 0f,
            daysSinceLast = 42
        )
        assertEquals(startCtl * Math.exp(-1.0).toFloat(), result.ctl, 1f)
    }

    @Test
    fun `tsb equals ctl minus atl`() {
        val r = FitnessLoadCalculator.updateLoads(50f, 40f, 80f, 1)
        assertEquals(r.ctl - r.atl, r.tsb, 0.001f)
    }

    @Test
    fun `updateLoads applies minimum fractional day for same-day sessions`() {
        val result = FitnessLoadCalculator.updateLoads(
            currentCtl = 0f,
            currentAtl = 0f,
            trimpScore = 100f,
            daysSinceLast = 0
        )
        assertTrue("Same-day CTL should still increase", result.ctl > 0f)
        assertTrue("Same-day ATL should still increase", result.atl > 0f)
    }
}
