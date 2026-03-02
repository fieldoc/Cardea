package com.hrcoach.service.workout

import com.hrcoach.data.db.TrackPointEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackPointRecorderTest {

    @Test
    fun `records only when interval elapsed unless forced`() = runTest {
        val recorder = TrackPointRecorder(intervalMs = 5_000L)
        val saved = mutableListOf<TrackPointEntity>()

        recorder.saveIfNeeded(
            workoutId = 1L,
            timestampMs = 1_000L,
            latitude = 40.0,
            longitude = -74.0,
            heartRate = 150,
            distanceMeters = 120f,
            force = false,
            save = { saved += it }
        )
        recorder.saveIfNeeded(
            workoutId = 1L,
            timestampMs = 2_000L,
            latitude = 40.0,
            longitude = -74.0,
            heartRate = 151,
            distanceMeters = 130f,
            force = false,
            save = { saved += it }
        )
        recorder.saveIfNeeded(
            workoutId = 1L,
            timestampMs = 2_500L,
            latitude = 40.0,
            longitude = -74.0,
            heartRate = 152,
            distanceMeters = 135f,
            force = true,
            save = { saved += it }
        )

        assertEquals(2, saved.size)
        assertEquals(120f, saved.first().distanceMeters)
        assertEquals(135f, saved.last().distanceMeters)
    }
}
