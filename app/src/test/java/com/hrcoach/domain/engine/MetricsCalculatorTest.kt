package com.hrcoach.domain.engine

import com.hrcoach.data.db.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MetricsCalculatorTest {

    @Test
    fun `deriveFullMetrics computes efficiency and decoupling`() {
        val points = listOf(
            point(id = 1, timestamp = 0L, distance = 0f, hr = 140),
            point(id = 2, timestamp = 60_000L, distance = 200f, hr = 142),
            point(id = 3, timestamp = 120_000L, distance = 400f, hr = 142),
            point(id = 4, timestamp = 180_000L, distance = 600f, hr = 150),
            point(id = 5, timestamp = 240_000L, distance = 800f, hr = 152)
        )

        val metrics = MetricsCalculator.deriveFullMetrics(
            workoutId = 10L,
            recordedAtMs = 240_000L,
            trackPoints = points
        )

        assertNotNull(metrics)
        assertEquals(5f, metrics?.avgPaceMinPerKm ?: 0f, 0.01f)
        assertEquals(145f, metrics?.avgHr ?: 0f, 0.6f)
        assertEquals(1.37f, metrics?.efficiencyFactor ?: 0f, 0.03f)
        assertEquals(1.41f, metrics?.efFirstHalf ?: 0f, 0.04f)
        assertEquals(1.35f, metrics?.efSecondHalf ?: 0f, 0.04f)
        assertEquals(4.7f, metrics?.aerobicDecoupling ?: 0f, 1f)
    }

    @Test
    fun `deriveFullMetrics returns null with insufficient valid samples`() {
        val points = listOf(
            point(id = 1, timestamp = 0L, distance = 0f, hr = 140),
            point(id = 2, timestamp = 2_000L, distance = 1f, hr = 141)
        )

        val metrics = MetricsCalculator.deriveFullMetrics(
            workoutId = 11L,
            recordedAtMs = 2_000L,
            trackPoints = points
        )

        assertNull(metrics)
    }

    @Test
    fun `trimpFrom returns null on null inputs`() {
        assertNull(MetricsCalculator.trimpFrom(durationMin = null, avgHr = 150f, hrMax = 200f))
        assertNull(MetricsCalculator.trimpFrom(durationMin = 30f, avgHr = null, hrMax = 200f))
        assertNull(MetricsCalculator.trimpFrom(durationMin = 30f, avgHr = 150f, hrMax = null))
    }

    @Test
    fun `trimpFrom returns null on non-positive inputs`() {
        assertNull(MetricsCalculator.trimpFrom(durationMin = 0f, avgHr = 150f, hrMax = 200f))
        assertNull(MetricsCalculator.trimpFrom(durationMin = 30f, avgHr = 0f, hrMax = 200f))
        assertNull(MetricsCalculator.trimpFrom(durationMin = 30f, avgHr = 150f, hrMax = 0f))
        assertNull(MetricsCalculator.trimpFrom(durationMin = -1f, avgHr = 150f, hrMax = 200f))
    }

    @Test
    fun `trimpFrom computes the Cardea quadratic intensity formula`() {
        // 30 min at 150 bpm, HRmax 200 → intensity = 0.75
        // TRIMP = 30 × 150 × 0.75² = 30 × 150 × 0.5625 = 2531.25
        val trimp = MetricsCalculator.trimpFrom(durationMin = 30f, avgHr = 150f, hrMax = 200f)
        assertNotNull(trimp)
        assertEquals(2531.25f, trimp!!, 0.01f)
    }

    @Test
    fun `trimpFrom scales quadratically with intensity`() {
        // At intensity 1.0 (HRmax), TRIMP = duration × HRmax × 1 = full contribution
        val hot = MetricsCalculator.trimpFrom(durationMin = 10f, avgHr = 200f, hrMax = 200f)!!
        // At intensity 0.5, TRIMP = duration × (0.5 × HRmax) × 0.25
        val cool = MetricsCalculator.trimpFrom(durationMin = 10f, avgHr = 100f, hrMax = 200f)!!
        // Ratio should be 1 / (0.5 × 0.25) = 8
        assertEquals(8f, hot / cool, 0.01f)
    }

    private fun point(
        id: Long,
        timestamp: Long,
        distance: Float,
        hr: Int
    ): TrackPointEntity {
        return TrackPointEntity(
            id = id,
            workoutId = 99L,
            timestamp = timestamp,
            latitude = 0.0,
            longitude = 0.0,
            heartRate = hr,
            distanceMeters = distance
        )
    }
}
