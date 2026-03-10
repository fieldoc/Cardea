package com.hrcoach.domain.engine

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessSignalEvaluatorTest {

    private fun metrics(
        ef: Float?,
        decoupling: Float?,
        hrr1: Float?,
        daysAgo: Int = 3
    ): WorkoutAdaptiveMetrics {
        val nowMs = System.currentTimeMillis()
        return WorkoutAdaptiveMetrics(
            workoutId = daysAgo.toLong() + 1L,
            recordedAtMs = nowMs - daysAgo * 86_400_000L,
            efficiencyFactor = ef,
            aerobicDecoupling = decoupling,
            hrr1Bpm = hrr1,
            environmentAffected = false,
            trimpReliable = true
        )
    }

    @Test
    fun `PUSH_HARDER when TSB positive and EF rising`() {
        val profile = AdaptiveProfile(ctl = 50f, atl = 40f)
        val recentMetrics = listOf(
            metrics(ef = 1.0f, decoupling = 3f, hrr1 = 35f, daysAgo = 10),
            metrics(ef = 1.05f, decoupling = 3f, hrr1 = 37f, daysAgo = 6),
            metrics(ef = 1.1f, decoupling = 3f, hrr1 = 38f, daysAgo = 2)
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
    fun `EASE_BACK when HRR1 declining despite positive TSB`() {
        val profile = AdaptiveProfile(ctl = 50f, atl = 45f)
        val recentMetrics = listOf(
            metrics(ef = 1.0f, decoupling = 3f, hrr1 = 35f, daysAgo = 10),
            metrics(ef = 1.0f, decoupling = 3f, hrr1 = 28f, daysAgo = 6),
            metrics(ef = 0.92f, decoupling = 4f, hrr1 = 22f, daysAgo = 2)
        )

        val result = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
        assertEquals(TuningDirection.EASE_BACK, result.tuningDirection)
    }

    @Test
    fun `illness flag when metrics crash despite positive TSB`() {
        val profile = AdaptiveProfile(ctl = 60f, atl = 50f)
        val recentMetrics = listOf(
            metrics(ef = 1.1f, decoupling = 3f, hrr1 = 40f, daysAgo = 12),
            metrics(ef = 1.1f, decoupling = 3f, hrr1 = 38f, daysAgo = 8),
            metrics(ef = 0.95f, decoupling = 9f, hrr1 = 25f, daysAgo = 1)
        )

        val result = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
        assertEquals(IllnessSignalTier.FULL, result.illnessTier)
        assertTrue(result.illnessFlag)
    }

    @Test
    fun `soft illness tier when only HRR1 crashes`() {
        val profile = AdaptiveProfile(ctl = 60f, atl = 50f)
        val recentMetrics = listOf(
            metrics(ef = 1.00f, decoupling = 3f, hrr1 = 40f, daysAgo = 12),
            metrics(ef = 1.01f, decoupling = 3f, hrr1 = 36f, daysAgo = 8),
            metrics(ef = 1.00f, decoupling = 3f, hrr1 = 24f, daysAgo = 1)
        )

        val result = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
        assertEquals(IllnessSignalTier.SOFT, result.illnessTier)
        assertFalse(result.illnessFlag)
        assertEquals(TuningDirection.EASE_BACK, result.tuningDirection)
    }

    @Test
    fun `full illness tier uses relative EF crash`() {
        val profile = AdaptiveProfile(ctl = 60f, atl = 50f)
        val recentMetrics = listOf(
            metrics(ef = 0.50f, decoupling = 3f, hrr1 = 40f, daysAgo = 12),
            metrics(ef = 0.49f, decoupling = 3f, hrr1 = 38f, daysAgo = 8),
            metrics(ef = 0.43f, decoupling = 9f, hrr1 = 25f, daysAgo = 1)
        )

        val result = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
        assertEquals(IllnessSignalTier.FULL, result.illnessTier)
        assertTrue(result.illnessFlag)
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
            metrics(ef = 1.05f, decoupling = 3f, hrr1 = 36f, daysAgo = 5),
            metrics(ef = 1.08f, decoupling = 3f, hrr1 = 37f, daysAgo = 2),
            metrics(ef = 1.10f, decoupling = 3f, hrr1 = 38f, daysAgo = 1)
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
}
