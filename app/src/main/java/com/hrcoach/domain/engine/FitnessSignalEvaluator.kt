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

    // TSB (Training Stress Balance) = CTL − ATL. See Friel, _The Training Bible_
    // (4th ed., ch. 12) "Performance Management Chart" and Coggan's TSS/PMC model.
    //
    // TSB_EASE_THRESHOLD: drop below this and unilaterally recommend EASE_BACK.
    // Friel's "transition/overreached" zone starts around −10 and "very fatigued"
    // around −30. We use −25 as a conservative mid-point — catches runners in the
    // fatigued zone before they land in Friel's worst bucket.
    private const val TSB_EASE_THRESHOLD = -25f

    // TSB_PUSH_THRESHOLD: above this, the athlete is "fresh enough for quality"
    // (Friel's +5 to +25 "optimal" window). Note this is one half of a CONJUNCTIVE
    // gate — PUSH_HARDER requires BOTH `tsb > TSB_PUSH_THRESHOLD` AND a rising EF
    // trend (see `EF_RISE_THRESHOLD`). The EF requirement is a strong secondary
    // filter; +5 alone would be aggressive, +5 with a ≥ 3-session rising EF trend
    // is conservative.
    private const val TSB_PUSH_THRESHOLD = 5f

    // EF_RISE_THRESHOLD: minimum total EF change across the evaluation window for
    // the rising-trend signal to fire. Unit is "EF units per the full window span."
    // 0.04 chosen after 2026-04-11 switch from endpoint-delta to least-squares
    // regression slope × (n−1); old endpoint-delta used the same 0.04.
    private const val EF_RISE_THRESHOLD = 0.04f

    // Only consider sessions within the last CTL time constant (42 days per
    // Bannister). Matches the training-fitness window used for CTL itself.
    private const val RECENCY_CUTOFF_DAYS = 42

    // Three points is the minimum for a least-squares slope that isn't degenerate.
    // Fewer sessions force HOLD to avoid PUSH_HARDER from noise.
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

        val efTrend = if (efValues.size >= 2) {
            // Least-squares regression slope scaled to total span,
            // more robust than endpoint delta against outliers
            val n = efValues.size
            val sumX = n * (n - 1) / 2.0
            val sumX2 = n * (n - 1) * (2 * n - 1) / 6.0
            val sumY = efValues.sumOf { it.toDouble() }
            val sumXY = efValues.withIndex().sumOf { (i, v) -> i * v.toDouble() }
            val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
            (slope * (n - 1)).toFloat()
        } else null

        // TODO(HRR1): illness detection (IllnessSignalTier.SOFT / FULL) is designed to key off
        // `hrr1Bpm`, the post-workout heart rate recovery measurement. HRR1 is never computed
        // — the design-spec'd 120s post-workout BLE hold is not implemented. See
        // `docs/plans/2026-04-14-science-constants-register.md` entry "HRR1 post-workout
        // measurement window". Until that feature lands, illness tier is permanently NONE and
        // the BootcampViewModel/BootcampScreen illness-flag UI path is dead code.

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
