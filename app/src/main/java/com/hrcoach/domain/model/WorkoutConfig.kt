package com.hrcoach.domain.model

data class WorkoutConfig(
    val mode: WorkoutMode,
    val steadyStateTargetHr: Int? = null,
    val segments: List<HrSegment> = emptyList(),
    val bufferBpm: Int = 5,
    val alertDelaySec: Int = 15,
    val alertCooldownSec: Int = 30,
    val presetId: String? = null
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
        if (mode == WorkoutMode.FREE_RUN) return null
        val total = segments.mapNotNull { it.durationSeconds?.toLong() }.sum()
        return total.takeIf { it > 0 }
    }
}
