package com.hrcoach.domain.engine

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            hrr1Bpm = null,
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
    fun `illness tier always NONE until HRR1 measurement is implemented`() {
        val profile = AdaptiveProfile(ctl = 60f, atl = 50f)
        val recentMetrics = listOf(
            metrics(ef = 1.1f, decoupling = 3f, daysAgo = 12),
            metrics(ef = 1.1f, decoupling = 3f, daysAgo = 8),
            metrics(ef = 0.95f, decoupling = 9f, daysAgo = 1)
        )

        val result = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
        assertEquals(IllnessSignalTier.NONE, result.illnessTier)
        assertFalse(result.illnessFlag)
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
}
