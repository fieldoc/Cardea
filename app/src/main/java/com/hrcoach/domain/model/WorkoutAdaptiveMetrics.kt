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
    val trimpScore: Float? = null,
    val trimpReliable: Boolean = true,
    val environmentAffected: Boolean = false,
    // JSON map of CoachingEvent.name -> Int count of cues that fired and passed the
    // toggle filter during the workout. Null when no cues fired or not yet drained.
    // Populated by WFS at stop time from CoachingAudioManager.consumeCueCounts();
    // read by the post-run "Sounds heard today" recap.
    val cueCountsJson: String? = null,
)

