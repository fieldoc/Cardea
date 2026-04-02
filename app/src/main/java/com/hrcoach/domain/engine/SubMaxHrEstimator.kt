package com.hrcoach.domain.engine

import kotlin.math.roundToInt

/** Lightweight HR sample for submaximal estimation. */
data class HrSample(val timestampMs: Long, val hr: Int)

/**
 * Estimates HRmax from submaximal workout data.
 *
 * Analyzes sustained peak HR (2-minute rolling average) and back-calculates
 * max HR based on estimated effort fraction. Only revises upward.
 */
object SubMaxHrEstimator {

    private const val MIN_DURATION_MS = 10 * 60 * 1_000L  // 10 minutes
    private const val ROLLING_WINDOW_MS = 2 * 60 * 1_000L // 2-minute rolling avg

    /**
     * Returns a new HRmax estimate, or null if no update is warranted.
     *
     * @param samples HR samples sorted by timestamp
     * @param currentHrMax current HRmax value
     * @param age user age for floor/ceiling calculation
     * @param isCalibrated true if HRmax was self-reported (skip inference)
     */
    fun estimate(
        samples: List<HrSample>,
        currentHrMax: Int,
        age: Int,
        isCalibrated: Boolean
    ): Int? {
        if (isCalibrated) return null
        if (samples.size < 2) return null

        val sorted = samples.sortedBy { it.timestampMs }
        val duration = sorted.last().timestampMs - sorted.first().timestampMs
        if (duration < MIN_DURATION_MS) return null

        val sustainedPeak = rollingAvgPeak(sorted) ?: return null

        val ratio = sustainedPeak.toFloat() / currentHrMax
        if (ratio < 0.70f) return null  // Too easy, not informative

        val effortFraction = when {
            ratio >= 0.88f -> 0.92f
            ratio >= 0.80f -> 0.85f
            else           -> 0.75f
        }

        val estimated = (sustainedPeak / effortFraction).roundToInt()

        val floor = 220 - age
        val ceiling = 220 - age + 20

        val capped = estimated.coerceIn(floor, ceiling)

        return if (capped > currentHrMax) capped else null
    }

    /**
     * Computes the highest 2-minute rolling average HR from sorted samples.
     */
    private fun rollingAvgPeak(sorted: List<HrSample>): Int? {
        if (sorted.size < 2) return null
        var maxAvg = 0.0
        var windowStart = 0

        for (windowEnd in sorted.indices) {
            // Advance windowStart so the window is <= ROLLING_WINDOW_MS
            while (windowStart < windowEnd &&
                sorted[windowEnd].timestampMs - sorted[windowStart].timestampMs > ROLLING_WINDOW_MS
            ) {
                windowStart++
            }
            if (sorted[windowEnd].timestampMs - sorted[windowStart].timestampMs >= ROLLING_WINDOW_MS / 2) {
                val avg = sorted.subList(windowStart, windowEnd + 1).map { it.hr }.average()
                if (avg > maxAvg) maxAvg = avg
            }
        }
        return if (maxAvg > 0) maxAvg.roundToInt() else null
    }
}
