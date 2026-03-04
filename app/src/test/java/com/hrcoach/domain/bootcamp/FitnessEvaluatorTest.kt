package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.PaceHrBucket
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import org.junit.Assert.*
import org.junit.Test

class FitnessEvaluatorTest {

    @Test
    fun `no profile data returns UNKNOWN`() {
        val level = FitnessEvaluator.assess(AdaptiveProfile(), emptyList())
        assertEquals(FitnessLevel.UNKNOWN, level)
    }

    @Test
    fun `fewer than 3 sessions returns UNKNOWN`() {
        val profile = AdaptiveProfile(totalSessions = 2)
        val level = FitnessEvaluator.assess(profile, emptyList())
        assertEquals(FitnessLevel.UNKNOWN, level)
    }

    @Test
    fun `high efficiency factor indicates good fitness`() {
        val profile = AdaptiveProfile(
            totalSessions = 5,
            paceHrBuckets = mapOf(24 to PaceHrBucket(140f, 10))
        )
        val metrics = listOf(
            buildMetrics(efficiencyFactor = 1.2f, aerobicDecoupling = 3f)
        )
        val level = FitnessEvaluator.assess(profile, metrics)
        assertTrue(level == FitnessLevel.INTERMEDIATE || level == FitnessLevel.ADVANCED)
    }

    @Test
    fun `low efficiency factor indicates beginner fitness`() {
        val profile = AdaptiveProfile(
            totalSessions = 3,
            paceHrBuckets = mapOf(32 to PaceHrBucket(170f, 5))
        )
        val metrics = listOf(
            buildMetrics(efficiencyFactor = 0.7f, aerobicDecoupling = 12f)
        )
        val level = FitnessEvaluator.assess(profile, metrics)
        assertEquals(FitnessLevel.BEGINNER, level)
    }

    @Test
    fun `hasEnoughData returns true with 3+ sessions`() {
        val profile = AdaptiveProfile(totalSessions = 3)
        assertTrue(FitnessEvaluator.hasEnoughData(profile))
    }

    @Test
    fun `hasEnoughData returns false with fewer than 3 sessions`() {
        val profile = AdaptiveProfile(totalSessions = 2)
        assertFalse(FitnessEvaluator.hasEnoughData(profile))
    }

    private fun buildMetrics(
        efficiencyFactor: Float = 1.0f,
        aerobicDecoupling: Float = 5f
    ) = WorkoutAdaptiveMetrics(
        workoutId = 1L,
        recordedAtMs = System.currentTimeMillis(),
        efficiencyFactor = efficiencyFactor,
        aerobicDecoupling = aerobicDecoupling,
        avgPaceMinPerKm = 6f,
        avgHr = 150f,
        hrAtSixMinPerKm = 150f,
        settleDownSec = 20f,
        settleUpSec = 15f,
        longTermHrTrimBpm = 0f,
        responseLagSec = 25f,
        efFirstHalf = efficiencyFactor,
        efSecondHalf = efficiencyFactor * 0.95f,
        heartbeatsPerKm = 900f,
        paceAtRefHrMinPerKm = 6f
    )
}
