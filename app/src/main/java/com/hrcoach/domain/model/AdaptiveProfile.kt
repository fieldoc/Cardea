package com.hrcoach.domain.model

import com.hrcoach.domain.engine.TuningDirection
import kotlin.math.roundToInt

data class PaceHrBucket(
    val avgHr: Float = 0f,
    val sampleCount: Int = 0
)

data class AdaptiveProfile(
    val longTermHrTrimBpm: Float = 0f,
    val responseLagSec: Float = 38f,
    val paceHrBuckets: Map<Int, PaceHrBucket> = emptyMap(),
    val totalSessions: Int = 0,
    val ctl: Float = 0f,
    val atl: Float = 0f,
    val hrMax: Int? = null,
    val hrMaxIsCalibrated: Boolean = false,
    val hrMaxCalibratedAtMs: Long? = null,
    val hrRest: Float? = null,
    val lastTuningDirection: TuningDirection? = null,
)

/**
 * Age-based resting HR default when no measurement exists yet.
 * Conservative population estimate; errs slightly high (-> higher Karvonen targets).
 */
fun defaultRestHr(age: Int?): Int =
    if (age != null) (72 - 0.2 * age).roundToInt().coerceIn(55, 75) else 65

