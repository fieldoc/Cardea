package com.hrcoach.service.workout

import com.hrcoach.data.db.TrackPointEntity

class TrackPointRecorder(
    private val intervalMs: Long
) {
    private var lastTrackPointTime: Long = 0L

    fun reset() {
        lastTrackPointTime = 0L
    }

    suspend fun saveIfNeeded(
        workoutId: Long,
        timestampMs: Long,
        latitude: Double?,
        longitude: Double?,
        heartRate: Int,
        distanceMeters: Float,
        force: Boolean,
        save: suspend (TrackPointEntity) -> Unit
    ) {
        if (workoutId <= 0L) return
        val lat = latitude ?: return
        val lon = longitude ?: return
        if (!force && lastTrackPointTime > 0L && timestampMs - lastTrackPointTime < intervalMs) return

        save(
            TrackPointEntity(
                workoutId = workoutId,
                timestamp = timestampMs,
                latitude = lat,
                longitude = lon,
                heartRate = heartRate.coerceAtLeast(0),
                distanceMeters = distanceMeters
            )
        )
        lastTrackPointTime = timestampMs
    }
}
