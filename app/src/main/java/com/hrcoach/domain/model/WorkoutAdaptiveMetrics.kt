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
    val responseLagSec: Float = 25f,
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

