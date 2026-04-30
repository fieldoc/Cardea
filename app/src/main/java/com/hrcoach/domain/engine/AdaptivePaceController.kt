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
    val slopeSmoothingPreviousWeight: Float = 0.60f,
    val slopeSmoothingInstantWeight: Float = 0.40f,
    val projectionHorizonLagFactor: Float = 0.4f,
    val projectionHorizonMinSec: Float = 10f,
    val projectionHorizonMaxSec: Float = 15f,
    val paceBiasFromTargetFactor: Float = 0.2f,
    val paceBiasMinBpm: Float = -6f,
    val paceBiasMaxBpm: Float = 6f,
    // Clamped to ±50 BPM/min (raised from ±30 in the 2026-04-13 engine audit).
    //
    // The 3 s min-deltaMin gate in updateHrSlope is a rate-limiter, NOT a
    // glitch filter — a single bad BLE sample still propagates through with
    // instSlope ≈ glitch_magnitude / 0.05 min, so this clamp is the last line
    // of defense. The ±30 cap was chosen on that basis, but observation of
    // interval-workout ramps showed it was silently truncating legitimate
    // sprint-onset rises that exceed 30 BPM/min instantaneous. At ±50 a
    // pathological glitch still contributes at most 0.40 × 50 = 20 BPM/min
    // to the EMA, which decays out over 2–3 subsequent samples, while real
    // interval ramps now pass through unclipped.
    //
    // Do NOT revert to ±30 without re-verifying against real interval data.
    val slopeSampleClampBpmPerMin: Float = 50f
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
        val hasProjectionConfidence: Boolean,
        // Exposed so AlertPolicy can suppress SLOW_DOWN/SPEED_UP when HR is self-correcting.
        // EMA-smoothed (0.60/0.40), clamped ±50 bpm/min, decayed ×0.5 on gaps > 1.5 min.
        val hrSlopeBpmPerMin: Float = 0f
    )

    data class SessionResult(
        val updatedProfile: AdaptiveProfile,
        val metrics: WorkoutAdaptiveMetrics
    )

    // Stored so finishSession can propagate fields it doesn't manage (hrMax, ctl, atl, etc.)
    private val savedInitialProfile = initialProfile

    private var shortTermTrimBpm = 0f
    private var longTermTrimBpm = initialProfile.longTermHrTrimBpm
    private var responseLagSec = initialProfile.responseLagSec.coerceIn(8f, 90f)
    private val initialTotalSessions = initialProfile.totalSessions

    private val baselineBuckets = initialProfile.paceHrBuckets
    private val sessionBuckets = mutableMapOf<Int, RunningBucket>()

    private var lastHr = 0
    private var lastHrTimeMs = 0L
    private var slopeTrackerInitialized = false
    private var hrSlopeBpmPerMin = 0f

    private var lastPaceDistanceMeters = 0f
    private var lastPaceTimeMs = 0L
    private var paceTrackerInitialized = false
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
    private var lastBaseProjectedHr: Float? = null

    private enum class ProjectionConfidence {
        LOW,
        MEDIUM,
        HIGH
    }

    fun evaluateTick(
        nowMs: Long,
        hr: Int,
        connected: Boolean,
        targetHr: Int?,
        distanceMeters: Float,
        actualZone: ZoneStatus
    ): TickResult {
        val pace = updatePace(nowMs, distanceMeters, hr, targetHr)

        // FREE_RUN: no zone target — collect data and return HR trend text.
        // Slope is still maintained (via updateHrSlope below) when HR is valid.
        if (targetHr == null) {
            if (connected && hr > 0) updateHrSlope(nowMs, hr)
            return TickResult(
                zoneStatus = ZoneStatus.NO_DATA,
                projectedZoneStatus = ZoneStatus.NO_DATA,
                predictedHr = 0,
                currentPaceMinPerKm = pace,
                guidance = trendGuidance(),
                hasProjectionConfidence = false,
                hrSlopeBpmPerMin = hrSlopeBpmPerMin
            )
        }

        if (!connected || hr <= 0 || targetHr <= 0 || actualZone == ZoneStatus.NO_DATA) {
            // Intentionally do NOT call updateHrSlope here. Disconnected or
            // sentinel-zero ticks would otherwise pollute the slope EMA with
            // huge artificial instSlope values on the reconnect transition.
            trackSettling(nowMs, ZoneStatus.NO_DATA)
            shortTermTrimBpm *= 0.9f
            lastBaseProjectedHr = null
            lastProjectedHr = null
            return TickResult(
                zoneStatus = ZoneStatus.NO_DATA,
                projectedZoneStatus = ZoneStatus.NO_DATA,
                predictedHr = hr.coerceAtLeast(0),
                currentPaceMinPerKm = pace,
                guidance = "Searching for HR signal",
                hasProjectionConfidence = false,
                hrSlopeBpmPerMin = hrSlopeBpmPerMin
            )
        }

        // Slope is updated here (after the disconnect/invalid guards) so we never
        // blend stale or zero HR values into the EMA.
        updateHrSlope(nowMs, hr)

        // Measure error against base projection (excluding shortTermTrim) so the two trim
        // terms learn from independent error signals rather than compounding the same bias.
        val predictionError = lastBaseProjectedHr?.let { previous -> hr - previous } ?: 0f
        shortTermTrimBpm = (
            (shortTermTrimBpm * tuning.shortTermTrimDecay) +
                (predictionError * tuning.shortTermTrimErrorWeight)
            ).coerceIn(-20f, 20f)
        val paceTrendBias = pace?.let { lookupPaceBias(it, targetHr) } ?: 0f
        val baseProjectedHr = hr +
            (hrSlopeBpmPerMin * (projectionHorizonSec() / 60f)) +
            longTermTrimBpm +
            paceTrendBias
        val projectedHr = baseProjectedHr + (shortTermTrimBpm * tuning.shortTermTrimProjectionWeight)
        lastBaseProjectedHr = baseProjectedHr
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
            confidence = confidence
        )

        return TickResult(
            zoneStatus = actualZone,
            projectedZoneStatus = projectedZone,
            predictedHr = projectedHr.roundToInt().coerceAtLeast(0).takeIf { showProjection } ?: 0,
            currentPaceMinPerKm = pace,
            guidance = guidance,
            hasProjectionConfidence = showProjection,
            hrSlopeBpmPerMin = hrSlopeBpmPerMin
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

        // Average settle-down and settle-up times separately, then blend equally between directions.
        // Down (HR returning from above zone) and up (HR returning from below zone) have different
        // physiological timescales — a count-weighted mix lets many quick corrections drown out
        // one slow build-up, which would under-estimate the runner's real response lag.
        val downAvgMs = settleDownSamplesMs.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val upAvgMs = settleUpSamplesMs.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        val observedLagSec = when {
            downAvgMs != null && upAvgMs != null -> ((downAvgMs + upAvgMs) / 2f) / 1000f
            downAvgMs != null -> downAvgMs / 1000f
            upAvgMs != null -> upAvgMs / 1000f
            else -> null
        }
        if (observedLagSec != null) {
            responseLagSec = (
                (responseLagSec * tuning.lagBlendCurrentWeight) +
                    (observedLagSec * tuning.lagBlendObservedWeight)
                ).coerceIn(8f, 90f)
        }

        val mergedBuckets = mergePaceBuckets(baselineBuckets, sessionBuckets)

        // Carry over all fields that AdaptivePaceController doesn't manage (hrMax, ctl, atl,
        // hrRest, lastTuningDirection, etc.) so callers never see them silently zeroed.
        // Callers that compute fresh values for these fields (WorkoutForegroundService,
        // AdaptiveProfileRebuilder) patch them on top after this call.
        val updatedProfile = savedInitialProfile.copy(
            longTermHrTrimBpm = longTermTrimBpm,
            responseLagSec = responseLagSec,
            paceHrBuckets = mergedBuckets,
            totalSessions = initialTotalSessions + 1
        )

        val metrics = MetricsCalculator.deriveFromPaceSamples(
            workoutId = workoutId,
            recordedAtMs = endedAtMs,
            settleDownSec = downAvgMs?.div(1000f),
            settleUpSec = upAvgMs?.div(1000f),
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
        // Use a boolean flag instead of checking lastPaceTimeMs == 0L so that a workout
        // starting at epoch zero (nowMs = 0) doesn't break the second tick's delta computation.
        if (!paceTrackerInitialized) {
            paceTrackerInitialized = true
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

        // Use a boolean flag so epoch-zero timestamps don't re-trigger initialisation on tick 2.
        if (!slopeTrackerInitialized) {
            slopeTrackerInitialized = true
            lastHrTimeMs = nowMs
            lastHr = hr
            return
        }

        val deltaMin = (nowMs - lastHrTimeMs) / 60_000f
        if (deltaMin in 0.05f..1.5f) {
            val instSlope = ((hr - lastHr) / deltaMin).coerceIn(-tuning.slopeSampleClampBpmPerMin, tuning.slopeSampleClampBpmPerMin)
            hrSlopeBpmPerMin = (hrSlopeBpmPerMin * tuning.slopeSmoothingPreviousWeight) +
                (instSlope * tuning.slopeSmoothingInstantWeight)
        } else if (deltaMin > 1.5f) {
            // Long gap (walk break, GPS-only mode, power saving) — decay the stored slope
            // toward zero rather than freezing it. A stale +20 BPM/min climb from before a
            // 3-minute walk would otherwise keep telling the runner to ease off indefinitely.
            hrSlopeBpmPerMin *= 0.5f
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
        confidence: ProjectionConfidence
    ): String {
        // Phrasing format: "[current zone state] - [HR trend]" or "[zone] - [action]".
        // SLOPE_THRESHOLD: |hrSlopeBpmPerMin| >= SLOPE_PHRASING_THRESHOLD_BPM_PER_MIN (1.5).
        //   See companion-object kdoc for the rationale. Aligned with AlertPolicy's
        //   SELF_CORRECTION_THRESHOLD_BPM_PER_MIN so phrasing and suppression agree on what
        //   "HR is correcting" means — eliminates the contradictory "alert + 'HR falling'"
        //   pairing that was happening in the EMA-residual band (0.8 ≤ |slope| < 1.5).
        // MEDIUM_VS_HIGH: uses distinct verbs (trending/climbing, trending/dropping) rather
        //   than a single-syllable modifier so the runner can actually hear the confidence tier.
        // PACE_TREND_BIAS: historically appended "This pace usually runs high/low" as a
        //   diagnostic. Removed 2026-04-17 — the projection already factors in bias, so the
        //   sentence repeated information and burned audio budget. paceTrendBias is still
        //   used for projectedHr computation at the call site (line 167).
        return when (actualZone) {
            ZoneStatus.ABOVE_ZONE -> {
                if (hrSlopeBpmPerMin <= -SLOPE_PHRASING_THRESHOLD_BPM_PER_MIN) "Above zone - HR falling"
                else                                                          "Above zone - ease off now"
            }

            ZoneStatus.BELOW_ZONE -> {
                if (hrSlopeBpmPerMin >= SLOPE_PHRASING_THRESHOLD_BPM_PER_MIN) "Below zone - HR rising"
                else                                                         "Below zone - pick up pace now"
            }

            ZoneStatus.NO_DATA -> "Searching for HR signal"

            ZoneStatus.IN_ZONE -> {
                // LOW confidence normally shows calibration phrasing, but when slope is strong
                // we surface the trend — slope detection has no bucket dependency, so it's safe
                // for first-session users who'd otherwise get zero warning ahead of zone exit.
                if (confidence == ProjectionConfidence.LOW) {
                    when {
                        hrSlopeBpmPerMin <= -SLOPE_PHRASING_THRESHOLD_BPM_PER_MIN -> "In zone - HR falling"
                        hrSlopeBpmPerMin >= SLOPE_PHRASING_THRESHOLD_BPM_PER_MIN -> "In zone - HR rising"
                        else -> "Learning your patterns - hold steady"
                    }
                } else {
                    when (projectedZone) {
                        ZoneStatus.ABOVE_ZONE -> {
                            // MEDIUM: "trending up" (softer). HIGH: "climbing" (confident).
                            val trend = if (confidence == ProjectionConfidence.MEDIUM) "trending up" else "climbing"
                            "HR $trend - ease off slightly"
                        }
                        ZoneStatus.BELOW_ZONE -> {
                            // MEDIUM: "trending low". HIGH: "dropping" (verb-matches "climbing").
                            val trend = if (confidence == ProjectionConfidence.MEDIUM) "trending low" else "dropping"
                            "HR $trend - pick up slightly"
                        }
                        else -> {
                            when {
                                hrSlopeBpmPerMin <= -SLOPE_PHRASING_THRESHOLD_BPM_PER_MIN -> "In zone - HR falling"
                                hrSlopeBpmPerMin >= SLOPE_PHRASING_THRESHOLD_BPM_PER_MIN -> "In zone - HR rising"
                                else -> "Pace looks good - hold steady"
                            }
                        }
                    }
                }
            }
        }
    }

    // FREE_RUN (no zone target) — no direction action to take, just a status report.
    // Thresholds ±2 bpm/min here are wider than buildGuidance's ±0.8 because without a
    // zone target there's no urgency; only call out a clearly sustained trend.
    private fun trendGuidance(): String {
        return when {
            hrSlopeBpmPerMin > 2f -> "HR rising"
            hrSlopeBpmPerMin < -2f -> "HR falling"
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
                    if (elapsed in 2_000L..600_000L) {
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

        // Weight each neighbor bucket by its sample count so that one anomalous
        // single-session bucket can't dominate a bucket backed by hundreds of runs.
        val totalSamples = neighbors.sumOf { it.sampleCount }
        val avgBucketHr = neighbors.sumOf { (it.avgHr * it.sampleCount).toDouble() }.toFloat() / totalSamples

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
                val uncappedCount = current.sampleCount + running.sampleCount
                val count = uncappedCount.coerceAtMost(10_000)
                val avg = (
                    (current.avgHr * current.sampleCount) +
                        (sessionAvg * running.sampleCount)
                    ) / uncappedCount
                merged[bucket] = PaceHrBucket(avgHr = avg, sampleCount = count)
            }
        }
        return merged
    }

    companion object {
        // Slope magnitude (bpm/min) at or above which buildGuidance phrases the trend
        // explicitly ("HR falling" / "HR rising") rather than direction-only ("ease off now"
        // / "pick up pace now"). Same threshold gates the IN_ZONE_CONFIRM slope skip in
        // CoachingEventRouter, which references this constant.
        //
        // TUNING: 1.5 bpm/min. Aligned intentionally with
        // AlertPolicy.SELF_CORRECTION_THRESHOLD_BPM_PER_MIN — phrasing and alert-suppression
        // share one definition of "HR is meaningfully correcting." Below this magnitude,
        // slope is dominated by EMA residual (60/40 blend; 10–15 s decay after a reversal),
        // so older 0.8 threshold produced false "HR falling" callouts when HR was actually
        // steady or climbing — see the 2026-04-28 run log: HR 160→169 over 30 s read as
        // slope -0.9, triggering "HR falling" while HR was unmistakably rising.
        //
        // Raise to 2.0 for stricter trend phrasing (only call out clear movement); lower to
        // 1.0 to surface earlier — but be aware that anything below 1.5 reintroduces the
        // alert+phrasing contradiction in the EMA-residual band.
        const val SLOPE_PHRASING_THRESHOLD_BPM_PER_MIN: Float = 1.5f
    }
}
