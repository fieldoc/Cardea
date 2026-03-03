package com.hrcoach.domain.model

data class WorkoutConfig(
    val mode: WorkoutMode,
    val steadyStateTargetHr: Int? = null,
    val segments: List<HrSegment> = emptyList(),
    val bufferBpm: Int = 5,
    val alertDelaySec: Int = 15,
    val alertCooldownSec: Int = 30
) {
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
