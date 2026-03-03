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
}
