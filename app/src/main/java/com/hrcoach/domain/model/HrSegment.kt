package com.hrcoach.domain.model

/**
 * One segment of a guided workout.
 *
 * Distance-based: populate [distanceMeters] (cumulative end distance).
 * Time-based:    populate [durationSeconds] (duration of this segment).
 * Exactly one of the two should be non-null for a valid segment.
 *
 * @param distanceMeters cumulative end distance for this segment (e.g. 5000f = up to 5 km)
 * @param durationSeconds duration of this segment in seconds (for time-based/interval workouts)
 * @param targetHr target heart rate in BPM
 * @param label optional human-readable name shown on the workout screen
 */
data class HrSegment(
    val distanceMeters: Float? = null,
    val durationSeconds: Int? = null,
    val targetHr: Int,
    val label: String? = null
)
