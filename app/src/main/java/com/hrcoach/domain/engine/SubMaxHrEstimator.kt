package com.hrcoach.domain.engine

/** Lightweight HR sample for submaximal estimation. */
data class HrSample(val timestampMs: Long, val hr: Int)

/**
 * Revises HRmax upward when a workout produces a sustained 2-minute rolling HR
 * that exceeds the current HRmax estimate by a safety margin.
 *
 * ## Philosophy
 *
 *  - **Only revise upward.** A downward revision would risk chasing a noisy low day.
 *  - **Only accept sustained evidence.** Single-beat spikes are rejected by using a
 *    2-minute rolling average; moderate-effort runs are rejected by requiring the
 *    peak to exceed current HRmax by [EVIDENCE_MARGIN_BPM] bpm. If you didn't push
 *    near your current max, we cannot prove your real max is higher.
 *  - **Small upward step.** When evidence is observed, set new HRmax to observed
 *    peak + [UPWARD_STEP_BPM]. Don't over-shoot — real max may still be a few bpm
 *    higher, but the next qualifying run will catch it.
 *  - **Ceiling** at 220 − age + 20 bounds runaway drift from sustained measurement
 *    noise (e.g., cadence lock).
 *
 * ## History
 *
 * Prior to 2026-04-14, this estimator used observed-peak / effort-fraction buckets
 * (ratio ≥ 0.88 → 0.92; ≥ 0.80 → 0.85; else → 0.75) to back-calculate HRmax from
 * sub-maximal efforts. That approach biased HRmax upward on moderate runs — the
 * lowest-confidence bucket produced the most aggressive revision (peak / 0.75 =
 * peak × 1.33). Science-fidelity audit 2026-04-14 flagged the buckets as
 * unsourced. See `docs/plans/2026-04-14-science-constants-register.md` entry
 * "SubMax effort-fraction buckets" for the decision trail.
 */
object SubMaxHrEstimator {

    /** 10 min minimum session length — shorter sessions can't produce a reliable peak. */
    private const val MIN_DURATION_MS = 10 * 60 * 1_000L

    /**
     * 2-minute rolling average window. Shorter windows let single-beat spikes
     * dominate; longer windows wash out legitimate interval peaks.
     */
    private const val ROLLING_WINDOW_MS = 2 * 60 * 1_000L

    /**
     * Minimum window fraction: rolling sub-windows are accepted as short as
     * [MIN_WINDOW_FRACTION] × [ROLLING_WINDOW_MS] (= 60 s at 2-min window) so short
     * interval peaks can contribute without requiring 2 full minutes at peak.
     */
    private const val MIN_WINDOW_FRACTION = 0.5f

    /**
     * Required evidence: sustained peak must exceed current HRmax by this many bpm
     * before the estimator revises. Prevents single-bpm flicker on noisy data.
     */
    private const val EVIDENCE_MARGIN_BPM = 2

    /**
     * Upward step once evidence is observed. Sets new HRmax to `sustainedPeak +
     * UPWARD_STEP_BPM` — a small acknowledgement that the runner pushed past the
     * current estimate, without over-shooting.
     */
    private const val UPWARD_STEP_BPM = 1

    /**
     * Returns a new HRmax estimate, or null if no update is warranted.
     *
     * @param samples HR samples (timestamp + bpm) from the workout
     * @param currentHrMax current HRmax estimate
     * @param age user age — used to compute the 220−age+20 plausibility ceiling
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

        // Gate: require sustained peak to exceed current HRmax by a safety margin.
        // If the runner didn't push near their current max, there's no evidence to
        // revise HRmax upward — moderate-effort runs are silently ignored.
        if (sustainedPeak < currentHrMax + EVIDENCE_MARGIN_BPM) return null

        val proposed = sustainedPeak + UPWARD_STEP_BPM
        val ceiling = 220 - age + 20
        val capped = proposed.coerceAtMost(ceiling)

        return if (capped > currentHrMax) capped else null
    }

    /**
     * Computes the highest rolling-average HR over [ROLLING_WINDOW_MS].
     *
     * Sub-windows down to [MIN_WINDOW_FRACTION] × [ROLLING_WINDOW_MS] contribute
     * so short interval peaks can still update the estimate.
     */
    private fun rollingAvgPeak(sorted: List<HrSample>): Int? {
        if (sorted.size < 2) return null
        var maxAvg = 0.0
        var windowStart = 0
        val minWindowMs = (ROLLING_WINDOW_MS * MIN_WINDOW_FRACTION).toLong()

        for (windowEnd in sorted.indices) {
            while (windowStart < windowEnd &&
                sorted[windowEnd].timestampMs - sorted[windowStart].timestampMs > ROLLING_WINDOW_MS
            ) {
                windowStart++
            }
            if (sorted[windowEnd].timestampMs - sorted[windowStart].timestampMs >= minWindowMs) {
                val avg = sorted.subList(windowStart, windowEnd + 1).map { it.hr }.average()
                if (avg > maxAvg) maxAvg = avg
            }
        }
        return if (maxAvg > 0) maxAvg.toInt() else null
    }
}
