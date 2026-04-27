package com.hrcoach.domain.model

data class WorkoutConfig(
    val mode: WorkoutMode,
    val steadyStateTargetHr: Int? = null,
    val segments: List<HrSegment> = emptyList(),
    val bufferBpm: Int = 5,
    val alertDelaySec: Int = 15,
    val alertCooldownSec: Int = 30,
    /**
     * Seconds from workout start during which BELOW-zone SPEED_UP alerts and adaptive
     * PREDICTIVE_WARNING cues are suppressed — the runner's HR is naturally rising from rest
     * and a "speed up" prompt at second 15 is counterproductive. ABOVE-zone SLOW_DOWN is NOT
     * suppressed (going too hard early is still a real safety signal). Splits, RETURN_TO_ZONE,
     * IN_ZONE_CONFIRM, and pause/resume tones are also unaffected.
     *
     * Default 120s (2 min). When the preset's first segment is labelled "Warm-up" (case-insensitive
     * substring match) and time-based, [effectiveWarmupGraceSec] auto-derives the grace from that
     * segment's duration so prescribed warmups and cue gating stay in sync.
     */
    val warmupGraceSec: Int = 120,
    val presetId: String? = null,
    /** Planned duration in minutes — for display only (e.g. bootcamp timed sessions). */
    val plannedDurationMinutes: Int? = null,
    /** Human-readable session label — for display only (e.g. "Easy Run"). */
    val sessionLabel: String? = null,
    /** Bootcamp week number — for display only on the active workout screen. */
    val bootcampWeekNumber: Int? = null,
    /** True when this is a bootcamp recovery week — alert layer suppresses below-zone cues. */
    val isRecoveryWeek: Boolean = false,
    /** Tag for special guidance during workout (e.g. "strides" triggers stride protocol text). */
    val guidanceTag: String? = null
) {
    /**
     * Returns true when every segment is time-based (has [HrSegment.durationSeconds] set and
     * [HrSegment.distanceMeters] is null). Returns false for distance-based workouts or when
     * there are no segments.
     */
    fun isTimeBased(): Boolean =
        segments.isNotEmpty() && segments.all { it.durationSeconds != null && it.distanceMeters == null }

    fun targetHrAtDistance(distanceMeters: Float): Int? {
        return when (mode) {
            WorkoutMode.STEADY_STATE -> steadyStateTargetHr
            WorkoutMode.DISTANCE_PROFILE -> {
                val distanceSegments = segments.filter { it.distanceMeters != null }
                if (distanceSegments.isEmpty()) return null  // all time-based, no distance milestones
                distanceSegments.firstOrNull { seg ->
                    seg.distanceMeters?.let { d -> distanceMeters <= d } == true
                }?.targetHr ?: distanceSegments.lastOrNull()?.targetHr
            }
            WorkoutMode.FREE_RUN -> null
        }
    }

    fun segmentAtElapsed(elapsedSeconds: Long): Pair<Int, HrSegment>? {
        if (segments.isEmpty()) return null
        var cumulative = 0L
        segments.forEachIndexed { index, seg ->
            val dur = seg.durationSeconds?.toLong() ?: return@forEachIndexed
            cumulative += dur
            if (elapsedSeconds < cumulative) return index to seg
        }
        return segments.lastIndex to segments.last()
    }

    fun targetHrAtElapsedSeconds(elapsedSeconds: Long): Int? {
        return segmentAtElapsed(elapsedSeconds)?.second?.targetHr
    }

    /** Total target distance in meters, or null if not distance-based. */
    fun totalDistanceMeters(): Float? {
        if (mode == WorkoutMode.FREE_RUN) return null
        // distanceMeters on segments is an absolute marker (cumulative), so the last one is the total
        return segments.mapNotNull { it.distanceMeters }.maxOrNull()
    }

    /** Total target duration in seconds, or null if not time-based. */
    fun totalDurationSeconds(): Long? {
        val total = segments.mapNotNull { it.durationSeconds?.toLong() }.sum()
        if (total > 0) return total
        // Bootcamp sessions (STEADY_STATE, FREE_RUN) carry plannedDurationMinutes as
        // the time target when there are no time-based segments.
        return plannedDurationMinutes?.let { it.toLong() * 60 }
    }

    /**
     * Returns true when segments include both time-based (durationSeconds only) and
     * distance-based (distanceMeters only) entries. The bug fix for mixed configs routes
     * time-based segments by elapsed time, then hands off to distance routing.
     */
    fun hasMixedSegments(): Boolean =
        segments.any { it.durationSeconds != null && it.distanceMeters == null } &&
        segments.any { it.distanceMeters != null }

    /**
     * HR target for configs with a mix of time-based and distance-based segments.
     * Time-based segments (those with durationSeconds but no distanceMeters) act as a
     * preamble: they are matched by elapsed time. Once elapsed time exceeds all
     * time-based segments, distance routing takes over.
     */
    /**
     * Effective warmup grace in seconds. When the first segment is a time-based "Warm-up"
     * (label contains "warm", case-insensitive) we use that segment's duration so the cue gate
     * matches the prescribed warmup exactly. Otherwise the explicit [warmupGraceSec] is used.
     */
    fun effectiveWarmupGraceSec(): Int {
        val first = segments.firstOrNull()
        if (first != null &&
            first.durationSeconds != null &&
            first.distanceMeters == null &&
            first.label?.contains("warm", ignoreCase = true) == true
        ) {
            return first.durationSeconds
        }
        return warmupGraceSec
    }

    fun isCooldownAtElapsed(elapsedSeconds: Long): Boolean {
        val seg = segmentAtElapsed(elapsedSeconds)?.second ?: return false
        return seg.label?.contains("cool", ignoreCase = true) == true
    }

    fun targetHrForMixed(elapsedSeconds: Long, distanceMeters: Float): Int? {
        var cumulativeTime = 0L
        for (seg in segments) {
            if (seg.durationSeconds != null && seg.distanceMeters == null) {
                cumulativeTime += seg.durationSeconds
                if (elapsedSeconds < cumulativeTime) return seg.targetHr
            }
        }
        return targetHrAtDistance(distanceMeters)
    }
}
