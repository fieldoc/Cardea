package com.hrcoach.domain.model

data class PaceHrBucket(
    val avgHr: Float = 0f,
    val sampleCount: Int = 0
)

data class AdaptiveProfile(
    val longTermHrTrimBpm: Float = 0f,
    val responseLagSec: Float = 25f,
    val paceHrBuckets: Map<Int, PaceHrBucket> = emptyMap(),
    val totalSessions: Int = 0,
    val ctl: Float = 0f,
    val atl: Float = 0f,
    val hrMax: Int? = null,
    val hrMaxIsCalibrated: Boolean = false,
)

