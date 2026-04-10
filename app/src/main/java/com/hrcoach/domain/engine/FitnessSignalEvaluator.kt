package com.hrcoach.domain.engine

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics

enum class TuningDirection { EASE_BACK, HOLD, PUSH_HARDER }
enum class TierPromptDirection { NONE, UP, DOWN }
enum class IllnessSignalTier { NONE, SOFT, FULL }

data class FitnessEvaluation(
    val tuningDirection: TuningDirection,
    val illnessTier: IllnessSignalTier,
    val illnessFlag: Boolean,
    val tsb: Float,
    val efTrend: Float?
)

object FitnessSignalEvaluator {

    private const val TSB_EASE_THRESHOLD = -25f
    private const val TSB_PUSH_THRESHOLD = 5f
    private const val EF_RISE_THRESHOLD = 0.04f
    private const val RECENCY_CUTOFF_DAYS = 42
    private const val MIN_RELIABLE_SESSIONS = 3

    fun evaluate(
        profile: AdaptiveProfile,
        recentMetrics: List<WorkoutAdaptiveMetrics>
    ): FitnessEvaluation {
        val cutoffMs = System.currentTimeMillis() - RECENCY_CUTOFF_DAYS * 86_400_000L
        val reliable = recentMetrics
            .filter { it.recordedAtMs >= cutoffMs && it.trimpReliable && !it.environmentAffected }
            .sortedBy { it.recordedAtMs }

        val tsb = profile.ctl - profile.atl
        if (tsb < TSB_EASE_THRESHOLD) {
            return FitnessEvaluation(
                tuningDirection = TuningDirection.EASE_BACK,
                illnessTier = IllnessSignalTier.NONE,
                illnessFlag = false,
                tsb = tsb,
                efTrend = null
            )
        }
        if (reliable.size < MIN_RELIABLE_SESSIONS || profile.ctl < 5f) {
            return FitnessEvaluation(
                tuningDirection = TuningDirection.HOLD,
                illnessTier = IllnessSignalTier.NONE,
                illnessFlag = false,
                tsb = tsb,
                efTrend = null
            )
        }

        val efValues = reliable.mapNotNull { it.efficiencyFactor }

        val efTrend = if (efValues.size >= 2) efValues.last() - efValues.first() else null

        // Note: illness detection (IllnessSignalTier.SOFT / FULL) previously relied on hrr1Bpm,
        // which is never computed or written. Illness tier is always NONE until HRR1 measurement
        // is implemented.

        val tuningDirection = when {
            tsb > TSB_PUSH_THRESHOLD &&
                efTrend != null &&
                efTrend > EF_RISE_THRESHOLD -> TuningDirection.PUSH_HARDER
            else -> TuningDirection.HOLD
        }

        return FitnessEvaluation(
            tuningDirection = tuningDirection,
            illnessTier = IllnessSignalTier.NONE,
            illnessFlag = false,
            tsb = tsb,
            efTrend = efTrend
        )
    }
}
