package com.hrcoach.domain.model

/**
 * One segment of a distance-profile workout.
 *
 * @param distanceMeters cumulative end distance for this segment (e.g. 5000m means "up to 5km")
 * @param targetHr target heart rate in BPM for this segment
 */
data class HrSegment(
    val distanceMeters: Float,
    val targetHr: Int
)
