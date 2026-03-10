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
    // HRR1 uses a walking cool-down protocol (safer: prevents venous pooling). Active recovery
    // blunts the vagal reactivation signal by ~2-4 bpm vs. passive standing, so the 10 bpm
    // threshold is intentionally conservative to remain meaningful under walking conditions.
    private const val HRR1_DROP_THRESHOLD = 10f
    private const val EF_CRASH_RELATIVE_THRESHOLD = 0.08f
    private const val HRR1_CRASH_THRESHOLD = 10f
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
        val hrr1Values = reliable.mapNotNull { it.hrr1Bpm }

        val efTrend = if (efValues.size >= 2) efValues.last() - efValues.first() else null
        val efTrendRelative = if (efValues.size >= 2 && efValues.first() > 0f) {
            (efValues.last() - efValues.first()) / efValues.first()
        } else {
            null
        }
        val hrr1Trend = if (hrr1Values.size >= 2) hrr1Values.last() - hrr1Values.first() else null

        val efCrash = efTrendRelative != null && efTrendRelative < -EF_CRASH_RELATIVE_THRESHOLD
        val hrr1Crash = hrr1Trend != null && hrr1Trend < -HRR1_CRASH_THRESHOLD
        val illnessTier = when {
            tsb > 0f && hrr1Crash && efCrash -> IllnessSignalTier.FULL
            tsb > 0f && hrr1Crash -> IllnessSignalTier.SOFT
            else -> IllnessSignalTier.NONE
        }
        val illnessFlag = illnessTier == IllnessSignalTier.FULL

        val tuningDirection = when {
            tsb < TSB_EASE_THRESHOLD -> TuningDirection.EASE_BACK
            illnessTier != IllnessSignalTier.NONE -> TuningDirection.EASE_BACK
            hrr1Trend != null && hrr1Trend < -HRR1_DROP_THRESHOLD -> TuningDirection.EASE_BACK
            tsb > TSB_PUSH_THRESHOLD &&
                efTrend != null &&
                efTrend > EF_RISE_THRESHOLD -> TuningDirection.PUSH_HARDER
            else -> TuningDirection.HOLD
        }

        return FitnessEvaluation(
            tuningDirection = tuningDirection,
            illnessTier = illnessTier,
            illnessFlag = illnessFlag,
            tsb = tsb,
            efTrend = efTrend
        )
    }
}
