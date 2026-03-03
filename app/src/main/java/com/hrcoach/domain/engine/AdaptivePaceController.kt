package com.hrcoach.domain.engine

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.PaceHrBucket
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.ZoneStatus
import kotlin.math.roundToInt

data class AdaptiveTuningConfig(
    val shortTermTrimDecay: Float = 0.86f,
    val shortTermTrimErrorWeight: Float = 0.14f,
    val shortTermTrimProjectionWeight: Float = 0.35f,
    val longTermTrimDecay: Float = 0.92f,
    val longTermTrimSessionWeight: Float = 0.08f,
    val lagBlendCurrentWeight: Float = 0.85f,
    val lagBlendObservedWeight: Float = 0.15f,
    val paceSmoothingPreviousWeight: Float = 0.72f,
    val paceSmoothingInstantWeight: Float = 0.28f,
    val slopeSmoothingPreviousWeight: Float = 0.75f,
    val slopeSmoothingInstantWeight: Float = 0.25f,
    val projectionHorizonLagFactor: Float = 0.4f,
    val projectionHorizonMinSec: Float = 10f,
    val projectionHorizonMaxSec: Float = 15f,
    val paceBiasFromTargetFactor: Float = 0.2f,
    val paceBiasMinBpm: Float = -6f,
    val paceBiasMaxBpm: Float = 6f
)

class AdaptivePaceController(
    private val config: WorkoutConfig,
    initialProfile: AdaptiveProfile,
    private val tuning: AdaptiveTuningConfig = AdaptiveTuningConfig()
) {
    private data class RunningBucket(
        var hrSum: Float = 0f,
        var sampleCount: Int = 0
    )

    data class TickResult(
        val zoneStatus: ZoneStatus,
        val projectedZoneStatus: ZoneStatus,
        val predictedHr: Int,
        val currentPaceMinPerKm: Float?,
        val guidance: String,
        val hasProjectionConfidence: Boolean
    )

    data class SessionResult(
        val updatedProfile: AdaptiveProfile,
        val metrics: WorkoutAdaptiveMetrics
    )

    private var shortTermTrimBpm = 0f
    private var longTermTrimBpm = initialProfile.longTermHrTrimBpm
    private var responseLagSec = initialProfile.responseLagSec.coerceIn(8f, 90f)
    private val initialTotalSessions = initialProfile.totalSessions

    private val baselineBuckets = initialProfile.paceHrBuckets
    private val sessionBuckets = mutableMapOf<Int, RunningBucket>()

    private var lastHr = 0
    private var lastHrTimeMs = 0L
    private var hrSlopeBpmPerMin = 0f

    private var lastPaceDistanceMeters = 0f
    private var lastPaceTimeMs = 0L
    private var smoothedPaceMinPerKm = 0f

    private var weightedDurationSec = 0f
    private var weightedHrSum = 0f
    private var weightedTargetSum = 0f

    private var sampleElapsedSec = 0f
    private val paceSamples = mutableListOf<MetricsCalculator.PaceHrSample>()

    private var transitionStartMs = 0L
    private var transitionDirection: ZoneStatus? = null
    private val settleDownSamplesMs = mutableListOf<Long>()
    private val settleUpSamplesMs = mutableListOf<Long>()
    private var lastProjectedHr: Float? = null

    private enum class ProjectionConfidence {
        LOW,
        MEDIUM,
        HIGH
    }

    fun currentLagSec(): Float = responseLagSec

    fun evaluateTick(
        nowMs: Long,
        hr: Int,
        connected: Boolean,
        targetHr: Int?,
        distanceMeters: Float,
        actualZone: ZoneStatus
    ): TickResult {
        val pace = updatePace(nowMs, distanceMeters, hr, targetHr)
        updateHrSlope(nowMs, hr)

        // FREE_RUN: no zone target — collect data and return HR trend text
        if (targetHr == null) {
            return TickResult(
                zoneStatus = ZoneStatus.NO_DATA,
                projectedZoneStatus = ZoneStatus.NO_DATA,
                predictedHr = 0,
                currentPaceMinPerKm = pace,
                guidance = trendGuidance(),
                hasProjectionConfidence = false
            )
        }

        if (!connected || hr <= 0 || targetHr <= 0 || actualZone == ZoneStatus.NO_DATA) {
            trackSettling(nowMs, ZoneStatus.NO_DATA)
            shortTermTrimBpm *= 0.9f
            lastProjectedHr = null
            return TickResult(
                zoneStatus = ZoneStatus.NO_DATA,
                projectedZoneStatus = ZoneStatus.NO_DATA,
                predictedHr = hr.coerceAtLeast(0),
                currentPaceMinPerKm = pace,
                guidance = "GET HR SIGNAL",
                hasProjectionConfidence = false
            )
        }

        val predictionError = lastProjectedHr?.let { previous -> hr - previous } ?: 0f
        shortTermTrimBpm = (
            (shortTermTrimBpm * tuning.shortTermTrimDecay) +
                (predictionError * tuning.shortTermTrimErrorWeight)
            ).coerceIn(-20f, 20f)
        val paceTrendBias = pace?.let { lookupPaceBias(it, targetHr) } ?: 0f
        val projectedHr = hr +
            (hrSlopeBpmPerMin * (projectionHorizonSec() / 60f)) +
            (shortTermTrimBpm * tuning.shortTermTrimProjectionWeight) +
            longTermTrimBpm +
            paceTrendBias
        lastProjectedHr = projectedHr

        val low = targetHr - config.bufferBpm
        val high = targetHr + config.bufferBpm
        val projectedZone = when {
            projectedHr < low -> ZoneStatus.BELOW_ZONE
            projectedHr > high -> ZoneStatus.ABOVE_ZONE
            else -> ZoneStatus.IN_ZONE
        }

        trackSettling(nowMs, actualZone)
        val confidence = projectionConfidence()
        val showProjection = confidence != ProjectionConfidence.LOW

        val guidance = buildGuidance(
            actualZone = actualZone,
            projectedZone = projectedZone,
            confidence = confidence,
            paceTrendBias = paceTrendBias
        )

        return TickResult(
            zoneStatus = actualZone,
            projectedZoneStatus = projectedZone,
            predictedHr = projectedHr.roundToInt().coerceAtLeast(0).takeIf { showProjection } ?: 0,
            currentPaceMinPerKm = pace,
            guidance = guidance,
            hasProjectionConfidence = showProjection
        )
    }

    fun finishSession(workoutId: Long, endedAtMs: Long): SessionResult {
        val avgHr = if (weightedDurationSec > 0f) weightedHrSum / weightedDurationSec else null
        val avgTarget = if (weightedDurationSec > 0f) weightedTargetSum / weightedDurationSec else null

        if (avgHr != null && avgTarget != null) {
            val sessionError = avgHr - avgTarget
            longTermTrimBpm = (
                (longTermTrimBpm * tuning.longTermTrimDecay) +
                    (sessionError * tuning.longTermTrimSessionWeight)
                ).coerceIn(-20f, 20f)
        }

        val settleCombinedSec = buildList {
            settleDownSamplesMs.takeIf { it.isNotEmpty() }?.let { add(it.average().toFloat() / 1000f) }
            settleUpSamplesMs.takeIf { it.isNotEmpty() }?.let { add(it.average().toFloat() / 1000f) }
        }
        if (settleCombinedSec.isNotEmpty()) {
            val observedLagSec = settleCombinedSec.average().toFloat()
            responseLagSec = (
                (responseLagSec * tuning.lagBlendCurrentWeight) +
                    (observedLagSec * tuning.lagBlendObservedWeight)
                ).coerceIn(8f, 90f)
        }

        val mergedBuckets = mergePaceBuckets(baselineBuckets, sessionBuckets)
        val updatedProfile = AdaptiveProfile(
            longTermHrTrimBpm = longTermTrimBpm,
            responseLagSec = responseLagSec,
            paceHrBuckets = mergedBuckets,
            totalSessions = initialTotalSessions + 1
        )

        val metrics = MetricsCalculator.deriveFromPaceSamples(
            workoutId = workoutId,
            recordedAtMs = endedAtMs,
            settleDownSec = settleDownSamplesMs.takeIf { it.isNotEmpty() }?.average()?.toFloat()?.div(1000f),
            settleUpSec = settleUpSamplesMs.takeIf { it.isNotEmpty() }?.average()?.toFloat()?.div(1000f),
            longTermHrTrimBpm = longTermTrimBpm,
            responseLagSec = responseLagSec,
            paceSamples = paceSamples
        )
        return SessionResult(updatedProfile = updatedProfile, metrics = metrics)
    }

    private fun updatePace(
        nowMs: Long,
        distanceMeters: Float,
        hr: Int,
        targetHr: Int?
    ): Float? {
        if (lastPaceTimeMs == 0L) {
            lastPaceTimeMs = nowMs
            lastPaceDistanceMeters = distanceMeters
            return null
        }

        val deltaDistance = (distanceMeters - lastPaceDistanceMeters).coerceAtLeast(0f)
        val deltaSec = ((nowMs - lastPaceTimeMs).coerceAtLeast(0L)) / 1000f

        if (deltaSec <= 0f) return smoothedPaceMinPerKm.takeIf { it > 0f }

        if (deltaDistance >= 3f && deltaSec >= 2f) {
            val instPace = (deltaSec / 60f) / (deltaDistance / 1000f)
            if (instPace in 2f..20f) {
                smoothedPaceMinPerKm = if (smoothedPaceMinPerKm <= 0f) {
                    instPace
                } else {
                    (smoothedPaceMinPerKm * tuning.paceSmoothingPreviousWeight) +
                        (instPace * tuning.paceSmoothingInstantWeight)
                }

                if (hr > 0) {
                    val weight = deltaSec.coerceAtMost(10f)
                    sampleElapsedSec += deltaSec
                    paceSamples += MetricsCalculator.PaceHrSample(
                        elapsedSec = sampleElapsedSec,
                        paceMinPerKm = smoothedPaceMinPerKm,
                        hr = hr.toFloat(),
                        weightSec = weight
                    )

                    val bucket = (smoothedPaceMinPerKm * 4f).roundToInt()
                    val running = sessionBuckets.getOrPut(bucket) { RunningBucket() }
                    running.hrSum += hr
                    running.sampleCount += 1

                    if (targetHr != null && targetHr > 0) {
                        weightedDurationSec += weight
                        weightedHrSum += hr * weight
                        weightedTargetSum += targetHr * weight
                    }
                }
            }
            lastPaceTimeMs = nowMs
            lastPaceDistanceMeters = distanceMeters
        } else if (deltaSec > 12f) {
            lastPaceTimeMs = nowMs
            lastPaceDistanceMeters = distanceMeters
        }

        return smoothedPaceMinPerKm.takeIf { it > 0f }
    }

    private fun updateHrSlope(nowMs: Long, hr: Int) {
        if (hr <= 0) return
        if (lastHrTimeMs == 0L) {
            lastHrTimeMs = nowMs
            lastHr = hr
            return
        }

        val deltaMin = (nowMs - lastHrTimeMs) / 60_000f
        if (deltaMin in 0.05f..1.5f) {
            val instSlope = (hr - lastHr) / deltaMin
            hrSlopeBpmPerMin = (hrSlopeBpmPerMin * tuning.slopeSmoothingPreviousWeight) +
                (instSlope * tuning.slopeSmoothingInstantWeight)
        }
        lastHr = hr
        lastHrTimeMs = nowMs
    }

    private fun projectionHorizonSec(): Float {
        return (responseLagSec * tuning.projectionHorizonLagFactor)
            .coerceIn(tuning.projectionHorizonMinSec, tuning.projectionHorizonMaxSec)
    }

    private fun projectionConfidence(): ProjectionConfidence {
        val baselineSamples = baselineBuckets.values.sumOf { it.sampleCount }
        val sessionSamples = sessionBuckets.values.sumOf { it.sampleCount }
        val totalSamples = baselineSamples + sessionSamples
        val effectiveSessions = initialTotalSessions + if (sessionSamples >= 20) 1 else 0
        return when {
            effectiveSessions >= 4 || totalSamples >= 180 -> ProjectionConfidence.HIGH
            effectiveSessions >= 2 || totalSamples >= 80 -> ProjectionConfidence.MEDIUM
            else -> ProjectionConfidence.LOW
        }
    }

    private fun buildGuidance(
        actualZone: ZoneStatus,
        projectedZone: ZoneStatus,
        confidence: ProjectionConfidence,
        paceTrendBias: Float
    ): String {
        return when (actualZone) {
            ZoneStatus.ABOVE_ZONE -> {
                if (hrSlopeBpmPerMin <= -0.8f) {
                    "HR settling back - hold steady"
                } else {
                    "Above zone - ease off now"
                }
            }

            ZoneStatus.BELOW_ZONE -> {
                if (hrSlopeBpmPerMin >= 0.8f) {
                    "HR climbing back - hold steady"
                } else {
                    "Below zone - build pace now"
                }
            }

            ZoneStatus.NO_DATA -> "GET HR SIGNAL"
            ZoneStatus.IN_ZONE -> {
                if (confidence == ProjectionConfidence.LOW) {
                    "Learning your patterns - hold steady"
                } else {
                    when (projectedZone) {
                        ZoneStatus.ABOVE_ZONE -> {
                            val qualifier = if (confidence == ProjectionConfidence.MEDIUM) {
                                "may drift up"
                            } else {
                                "drifting up"
                            }
                            val paceHint = if (paceTrendBias >= 1.5f) {
                                " This pace usually runs high."
                            } else {
                                ""
                            }
                            "HR $qualifier - ease off slightly.$paceHint"
                        }

                        ZoneStatus.BELOW_ZONE -> {
                            val qualifier = if (confidence == ProjectionConfidence.MEDIUM) {
                                "may drift low"
                            } else {
                                "drifting low"
                            }
                            val paceHint = if (paceTrendBias <= -1.5f) {
                                " This pace usually runs low."
                            } else {
                                ""
                            }
                            "HR $qualifier - add a touch of pace.$paceHint"
                        }

                        else -> {
                            when {
                                hrSlopeBpmPerMin <= -0.8f -> "HR settling back - hold steady"
                                hrSlopeBpmPerMin >= 0.8f -> "HR rising - hold steady"
                                else -> "Pace looks good - hold steady"
                            }
                        }
                    }
                }
            }
        }
    }

    private fun trendGuidance(): String {
        return when {
            hrSlopeBpmPerMin > 2f -> "HR rising"
            hrSlopeBpmPerMin < -2f -> "HR easing down"
            else -> "HR steady"
        }
    }

    private fun trackSettling(nowMs: Long, zone: ZoneStatus) {
        when (zone) {
            ZoneStatus.ABOVE_ZONE, ZoneStatus.BELOW_ZONE -> {
                if (transitionDirection != zone) {
                    transitionDirection = zone
                    transitionStartMs = nowMs
                }
            }

            ZoneStatus.IN_ZONE -> {
                val direction = transitionDirection
                if (direction != null && transitionStartMs > 0L) {
                    val elapsed = nowMs - transitionStartMs
                    if (elapsed in 2_000L..300_000L) {
                        if (direction == ZoneStatus.ABOVE_ZONE) {
                            settleDownSamplesMs += elapsed
                        } else if (direction == ZoneStatus.BELOW_ZONE) {
                            settleUpSamplesMs += elapsed
                        }
                    }
                }
                transitionDirection = null
                transitionStartMs = 0L
            }

            ZoneStatus.NO_DATA -> {
                transitionDirection = null
                transitionStartMs = 0L
            }
        }
    }

    private fun lookupPaceBias(paceMinPerKm: Float, targetHr: Int): Float {
        if (baselineBuckets.isEmpty()) return 0f
        val baseBucket = (paceMinPerKm * 4f).roundToInt()
        val neighbors = listOf(baseBucket - 1, baseBucket, baseBucket + 1)
            .mapNotNull { bucket -> baselineBuckets[bucket] }
            .filter { it.sampleCount > 0 }

        if (neighbors.isEmpty()) return 0f
        val avgBucketHr = neighbors.sumOf { it.avgHr.toDouble() }.toFloat() / neighbors.size
        val deltaFromTarget = avgBucketHr - targetHr
        return (deltaFromTarget * tuning.paceBiasFromTargetFactor)
            .coerceIn(tuning.paceBiasMinBpm, tuning.paceBiasMaxBpm)
    }

    private fun mergePaceBuckets(
        baseline: Map<Int, PaceHrBucket>,
        session: Map<Int, RunningBucket>
    ): Map<Int, PaceHrBucket> {
        val merged = baseline.toMutableMap()
        session.forEach { (bucket, running) ->
            if (running.sampleCount <= 0) return@forEach
            val sessionAvg = running.hrSum / running.sampleCount
            val current = merged[bucket]
            if (current == null || current.sampleCount <= 0) {
                merged[bucket] = PaceHrBucket(
                    avgHr = sessionAvg,
                    sampleCount = running.sampleCount
                )
            } else {
                val count = (current.sampleCount + running.sampleCount).coerceAtMost(10_000)
                val avg = (
                    (current.avgHr * current.sampleCount) +
                        (sessionAvg * running.sampleCount)
                    ) / (current.sampleCount + running.sampleCount)
                merged[bucket] = PaceHrBucket(avgHr = avg, sampleCount = count)
            }
        }
        return merged
    }
}
