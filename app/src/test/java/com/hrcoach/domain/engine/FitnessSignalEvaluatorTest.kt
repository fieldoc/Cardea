package com.hrcoach.domain.engine

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FitnessSignalEvaluatorTest {

    private fun metrics(
        ef: Float?,
        decoupling: Float?,
        daysAgo: Int = 3
    ): WorkoutAdaptiveMetrics {
        val nowMs = System.currentTimeMillis()
        return WorkoutAdaptiveMetrics(
            workoutId = daysAgo.toLong() + 1L,
            recordedAtMs = nowMs - daysAgo * 86_400_000L,
            efficiencyFactor = ef,
            aerobicDecoupling = decoupling,
            environmentAffected = false,
            trimpReliable = true
        )
    }

    @Test
    fun `PUSH_HARDER when TSB positive and EF rising`() {
        val profile = AdaptiveProfile(ctl = 50f, atl = 40f)
        val recentMetrics = listOf(
            metrics(ef = 1.0f, decoupling = 3f, daysAgo = 10),
            metrics(ef = 1.05f, decoupling = 3f, daysAgo = 6),
            metrics(ef = 1.1f, decoupling = 3f, daysAgo = 2)
        )

        val result = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
        assertEquals(TuningDirection.PUSH_HARDER, result.tuningDirection)
    }

    @Test
    fun `EASE_BACK when TSB very negative`() {
        val profile = AdaptiveProfile(ctl = 60f, atl = 90f)
        val result = FitnessSignalEvaluator.evaluate(profile, emptyList())
        assertEquals(TuningDirection.EASE_BACK, result.tuningDirection)
    }

    @Test
    fun `environment-affected sessions excluded from EF trend`() {
        val profile = AdaptiveProfile(ctl = 50f, atl = 45f)
        val hotDay = WorkoutAdaptiveMetrics(
            workoutId = 1L,
            recordedAtMs = System.currentTimeMillis() - 86_400_000L,
            efficiencyFactor = 0.8f,
            aerobicDecoupling = 14f,
            environmentAffected = true,
            trimpReliable = true
        )
        val recent = listOf(
            metrics(ef = 1.05f, decoupling = 3f, daysAgo = 5),
            metrics(ef = 1.08f, decoupling = 3f, daysAgo = 2),
            metrics(ef = 1.10f, decoupling = 3f, daysAgo = 1)
        )

        val result = FitnessSignalEvaluator.evaluate(profile, recent + hotDay)
        assertNotEquals(TuningDirection.EASE_BACK, result.tuningDirection)
    }

    @Test
    fun `HOLD when insufficient reliable data`() {
        val profile = AdaptiveProfile(ctl = 0f, atl = 0f)
        val result = FitnessSignalEvaluator.evaluate(profile, emptyList())
        assertEquals(TuningDirection.HOLD, result.tuningDirection)
    }

    @Test
    fun `EF trend regression resists single outlier at start`() {
        // Endpoint delta: 1.10 - 1.15 = -0.05 (negative, misleading)
        // Regression slope across 5 points should recognize the outlier
        val profile = AdaptiveProfile(ctl = 50f, atl = 40f) // TSB = 10
        val recentMetrics = listOf(
            metrics(ef = 1.15f, decoupling = 3f, daysAgo = 20), // outlier high start
            metrics(ef = 1.02f, decoupling = 3f, daysAgo = 15),
            metrics(ef = 1.05f, decoupling = 3f, daysAgo = 10),
            metrics(ef = 1.08f, decoupling = 3f, daysAgo = 5),
            metrics(ef = 1.10f, decoupling = 3f, daysAgo = 1)
        )

        val result = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
        // Regression slope is slightly negative due to the high outlier start
        // so despite TSB > 5, efTrend is not > 0.04 → HOLD
        assertEquals(TuningDirection.HOLD, result.tuningDirection)
    }

    @Test
    fun `EF trend regression detects steady improvement`() {
        val profile = AdaptiveProfile(ctl = 50f, atl = 40f) // TSB = 10
        val recentMetrics = listOf(
            metrics(ef = 1.00f, decoupling = 3f, daysAgo = 20),
            metrics(ef = 1.03f, decoupling = 3f, daysAgo = 15),
            metrics(ef = 1.05f, decoupling = 3f, daysAgo = 10),
            metrics(ef = 1.08f, decoupling = 3f, daysAgo = 5),
            metrics(ef = 1.12f, decoupling = 3f, daysAgo = 1)
        )

        val result = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
        // Regression total change ~ 0.116, well above 0.04 threshold
        // TSB = 10 > 5, so → PUSH_HARDER
        assertEquals(TuningDirection.PUSH_HARDER, result.tuningDirection)
    }
}
