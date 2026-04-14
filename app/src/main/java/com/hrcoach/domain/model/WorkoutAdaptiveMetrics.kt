package com.hrcoach.domain.model

data class WorkoutAdaptiveMetrics(
    val workoutId: Long,
    val recordedAtMs: Long,
    val avgPaceMinPerKm: Float? = null,
    val avgHr: Float? = null,
    val hrAtSixMinPerKm: Float? = null,
    val settleDownSec: Float? = null,
    val settleUpSec: Float? = null,
    val longTermHrTrimBpm: Float = 0f,
    // Default matches AdaptiveProfile.responseLagSec (38f). Previously 25f, which
    // makes the adaptive horizon (lag × 0.4f) hit the 10s minimum clamp, rendering
    // the predictive engine inert. Production callers always supply an explicit
    // value — this default is a safety net for future callers.
    val responseLagSec: Float = 38f,
    val efficiencyFactor: Float? = null,
    val aerobicDecoupling: Float? = null,
    val efFirstHalf: Float? = null,
    val efSecondHalf: Float? = null,
    val heartbeatsPerKm: Float? = null,
    val paceAtRefHrMinPerKm: Float? = null,
    val hrr1Bpm: Float? = null,
    val trimpScore: Float? = null,
    val trimpReliable: Boolean = true,
    val environmentAffected: Boolean = false,
)

