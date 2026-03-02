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
                segments.firstOrNull { distanceMeters <= it.distanceMeters }?.targetHr
                    ?: segments.lastOrNull()?.targetHr
            }
        }
    }
}
