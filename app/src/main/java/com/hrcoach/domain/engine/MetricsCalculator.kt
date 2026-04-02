package com.hrcoach.domain.engine

import com.hrcoach.data.db.TrackPointEntity
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import kotlin.math.exp

object MetricsCalculator {
    data class PaceHrSample(
        val elapsedSec: Float,
        val paceMinPerKm: Float,
        val hr: Float,
        val weightSec: Float
    )

    data class EfficiencyMetrics(
        val efficiencyFactor: Float?,
        val aerobicDecoupling: Float?,
        val efFirstHalf: Float?,
        val efSecondHalf: Float?
    )

    fun deriveFullMetrics(
        workoutId: Long,
        recordedAtMs: Long,
        trackPoints: List<TrackPointEntity>,
        targetHr: Float? = null
    ): WorkoutAdaptiveMetrics? {
        val samples = buildSamples(trackPoints)
        if (samples.isEmpty()) return null
        return deriveFromPaceSamples(
            workoutId = workoutId,
            recordedAtMs = recordedAtMs,
            paceSamples = samples,
            targetHr = targetHr
        )
    }

    fun deriveFromPaceSamples(
        workoutId: Long,
        recordedAtMs: Long,
        paceSamples: List<PaceHrSample>,
        settleDownSec: Float? = null,
        settleUpSec: Float? = null,
        longTermHrTrimBpm: Float = 0f,
        responseLagSec: Float = 25f,
        targetHr: Float? = null
    ): WorkoutAdaptiveMetrics {
        val totalWeight = paceSamples.sumOf { it.weightSec.toDouble() }.toFloat()
        val avgPace = if (totalWeight > 0f) {
            paceSamples.sumOf { (it.paceMinPerKm * it.weightSec).toDouble() }.toFloat() / totalWeight
        } else {
            null
        }
        val avgHr = if (totalWeight > 0f) {
            paceSamples.sumOf { (it.hr * it.weightSec).toDouble() }.toFloat() / totalWeight
        } else {
            null
        }

        val hrAtSix = calculateHrAtReferencePace(paceSamples)
        val efficiencyMetrics = calculateEfficiencyMetrics(
            paceSamples = paceSamples,
            avgPaceMinPerKm = avgPace,
            avgHr = avgHr
        )
        val heartbeatsPerKm = if (avgPace != null && avgHr != null) calculateHeartbeatsPerKm(avgHr, avgPace) else null
        val paceAtRefHrMinPerKm = if (targetHr != null) calculatePaceAtHr(paceSamples, targetHr) else null

        return WorkoutAdaptiveMetrics(
            workoutId = workoutId,
            recordedAtMs = recordedAtMs,
            avgPaceMinPerKm = avgPace,
            avgHr = avgHr,
            hrAtSixMinPerKm = hrAtSix,
            settleDownSec = settleDownSec,
            settleUpSec = settleUpSec,
            longTermHrTrimBpm = longTermHrTrimBpm,
            responseLagSec = responseLagSec,
            efficiencyFactor = efficiencyMetrics.efficiencyFactor,
            aerobicDecoupling = efficiencyMetrics.aerobicDecoupling,
            efFirstHalf = efficiencyMetrics.efFirstHalf,
            efSecondHalf = efficiencyMetrics.efSecondHalf,
            heartbeatsPerKm = heartbeatsPerKm,
            paceAtRefHrMinPerKm = paceAtRefHrMinPerKm
        )
    }

    fun calculateEfficiencyMetrics(
        paceSamples: List<PaceHrSample>,
        avgPaceMinPerKm: Float?,
        avgHr: Float?
    ): EfficiencyMetrics {
        if (paceSamples.isEmpty()) {
            return EfficiencyMetrics(
                efficiencyFactor = efficiencyFrom(avgPaceMinPerKm, avgHr),
                aerobicDecoupling = null,
                efFirstHalf = null,
                efSecondHalf = null
            )
        }

        val maxElapsed = paceSamples.maxOfOrNull { it.elapsedSec } ?: 0f
        if (maxElapsed <= 0f) {
            return EfficiencyMetrics(
                efficiencyFactor = efficiencyFrom(avgPaceMinPerKm, avgHr),
                aerobicDecoupling = null,
                efFirstHalf = null,
                efSecondHalf = null
            )
        }

        val halfElapsed = maxElapsed / 2f
        val firstHalf = paceSamples.filter { it.elapsedSec <= halfElapsed }
        val secondHalf = paceSamples.filter { it.elapsedSec > halfElapsed }

        val efFirst = efficiencyForSamples(firstHalf)
        val efSecond = efficiencyForSamples(secondHalf)
        val decoupling = if (efFirst != null && efSecond != null && efFirst > 0f) {
            ((efFirst - efSecond) / efFirst) * 100f
        } else {
            null
        }

        return EfficiencyMetrics(
            efficiencyFactor = efficiencyFrom(avgPaceMinPerKm, avgHr),
            aerobicDecoupling = decoupling,
            efFirstHalf = efFirst,
            efSecondHalf = efSecond
        )
    }

    private fun buildSamples(trackPoints: List<TrackPointEntity>): List<PaceHrSample> {
        if (trackPoints.size < 2) return emptyList()
        val sorted = trackPoints.sortedBy { it.timestamp }
        val startTime = sorted.first().timestamp
        val samples = mutableListOf<PaceHrSample>()

        for (index in 1 until sorted.size) {
            val previous = sorted[index - 1]
            val current = sorted[index]
            val deltaDistance = (current.distanceMeters - previous.distanceMeters).coerceAtLeast(0f)
            val deltaSec = ((current.timestamp - previous.timestamp).coerceAtLeast(0L)) / 1000f
            if (deltaDistance < 3f || deltaSec <= 0f) continue

            val paceMinPerKm = (deltaSec / 60f) / (deltaDistance / 1000f)
            if (paceMinPerKm !in 2f..20f) continue

            val hr = ((previous.heartRate + current.heartRate) / 2f).coerceAtLeast(0f)
            if (hr <= 0f) continue

            samples += PaceHrSample(
                elapsedSec = ((current.timestamp - startTime).coerceAtLeast(0L)) / 1000f,
                paceMinPerKm = paceMinPerKm,
                hr = hr,
                weightSec = deltaSec
            )
        }
        return samples
    }

    private fun calculateHrAtReferencePace(
        samples: List<PaceHrSample>,
        referencePaceMinPerKm: Float = 6f,
        sigma: Float = 0.35f
    ): Float? {
        if (samples.isEmpty()) return null
        var weightedHr = 0f
        var totalWeight = 0f

        samples.forEach { sample ->
            val paceDelta = sample.paceMinPerKm - referencePaceMinPerKm
            val gaussian = exp(-((paceDelta * paceDelta) / (2f * sigma * sigma))).toFloat()
            val weight = gaussian * sample.weightSec
            weightedHr += sample.hr * weight
            totalWeight += weight
        }

        return if (totalWeight > 0f) weightedHr / totalWeight else null
    }

    private fun efficiencyForSamples(samples: List<PaceHrSample>): Float? {
        if (samples.isEmpty()) return null
        val totalWeight = samples.sumOf { it.weightSec.toDouble() }.toFloat()
        if (totalWeight <= 0f) return null
        val avgPace = samples.sumOf { (it.paceMinPerKm * it.weightSec).toDouble() }.toFloat() / totalWeight
        val avgHr = samples.sumOf { (it.hr * it.weightSec).toDouble() }.toFloat() / totalWeight
        return efficiencyFrom(avgPace, avgHr)
    }

    private fun efficiencyFrom(avgPaceMinPerKm: Float?, avgHr: Float?): Float? {
        if (avgPaceMinPerKm == null || avgHr == null) return null
        if (avgPaceMinPerKm <= 0f || avgHr <= 0f) return null
        return (1000f / avgPaceMinPerKm) / avgHr
    }

    fun computeRestingHrProxy(trackPoints: List<TrackPointEntity>): Float? {
        if (trackPoints.size < 2) return null
        val sorted = trackPoints.sortedBy { it.timestamp }
        val startTime = sorted.first().timestamp
        val earlyPoints = sorted.filter { it.timestamp - startTime <= 60_000L && it.heartRate > 0 }
        if (earlyPoints.size < 3) return null
        val hrValues = earlyPoints.map { it.heartRate.toFloat() }
        val minHr = hrValues.minOrNull()!!
        val maxHr = hrValues.maxOrNull()!!
        val firstHr = hrValues.first()
        if (maxHr - minHr > 15f || firstHr > 100f) return null
        return (minHr - 5f).coerceAtLeast(40f)
    }

    private fun calculateHeartbeatsPerKm(avgHr: Float, avgPaceMinPerKm: Float): Float =
        avgHr * avgPaceMinPerKm

    private fun calculatePaceAtHr(samples: List<PaceHrSample>, targetHr: Float): Float? {
        if (samples.isEmpty()) return null
        val sigmaHr = 8.0f
        var weightedPace = 0f
        var sumWeight = 0f
        samples.forEach { sample ->
            val hrDelta = sample.hr - targetHr
            val gaussian = exp(-((hrDelta * hrDelta) / (2f * sigmaHr * sigmaHr))).toFloat()
            val weight = gaussian * sample.weightSec
            weightedPace += sample.paceMinPerKm * weight
            sumWeight += weight
        }
        return if (sumWeight > 0.1f) weightedPace / sumWeight else null
    }
}
