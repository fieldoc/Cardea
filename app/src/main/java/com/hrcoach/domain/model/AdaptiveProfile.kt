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
 *
 * Population mean resting HR for healthy adults is ~72 bpm in young adults and
 * declines ~0.2 bpm per year of age across a broad population (source:
 * Framingham/MESA cohort data). Clamped to [55, 75] to avoid extreme values from
 * out-of-range ages, and fallback to 65 when age is unknown.
 *
 * Intentionally conservative — errs slightly high, which produces higher Karvonen
 * target HRs (safer for new users whose true resting HR we don't yet know). Once
 * the real resting HR is measured, this default is replaced.
 */
fun defaultRestHr(age: Int?): Int =
    if (age != null) (72 - 0.2 * age).roundToInt().coerceIn(55, 75) else 65

