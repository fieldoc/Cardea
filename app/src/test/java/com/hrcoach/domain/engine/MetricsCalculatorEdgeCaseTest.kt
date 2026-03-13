package com.hrcoach.domain.engine

import com.hrcoach.data.db.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricsCalculatorEdgeCaseTest {

    @Test
    fun `restingHrProxy returns null for empty track points`() {
        assertNull(MetricsCalculator.computeRestingHrProxy(emptyList()))
    }

    @Test
    fun `restingHrProxy returns null when first HR is above 100`() {
        val points = listOf(
            point(timestamp = 0L, hr = 110),
            point(timestamp = 10_000L, hr = 110),
            point(timestamp = 20_000L, hr = 110)
        )
        assertNull(MetricsCalculator.computeRestingHrProxy(points))
    }

    @Test
    fun `restingHrProxy returns null when HR range exceeds 15 bpm`() {
        val points = listOf(
            point(timestamp = 0L, hr = 60),
            point(timestamp = 10_000L, hr = 62),
            point(timestamp = 20_000L, hr = 80),
            point(timestamp = 30_000L, hr = 62),
            point(timestamp = 40_000L, hr = 60)
        )
        assertNull(MetricsCalculator.computeRestingHrProxy(points))
    }

    @Test
    fun `restingHrProxy returns valid proxy for stable low HR`() {
        val points = listOf(
            point(timestamp = 0L, hr = 65),
            point(timestamp = 10_000L, hr = 66),
            point(timestamp = 20_000L, hr = 67),
            point(timestamp = 30_000L, hr = 65),
            point(timestamp = 40_000L, hr = 66),
            point(timestamp = 50_000L, hr = 67)
        )
        val proxy = MetricsCalculator.computeRestingHrProxy(points)
        assertEquals(55f, proxy!!, 0.01f)
    }

    @Test
    fun `restingHrProxy clamps to 30 minimum`() {
        val points = listOf(
            point(timestamp = 0L, hr = 35),
            point(timestamp = 10_000L, hr = 35),
            point(timestamp = 20_000L, hr = 35)
        )
        val proxy = MetricsCalculator.computeRestingHrProxy(points)
        assertEquals(30f, proxy!!, 0.01f)
    }

    @Test
    fun `deriveFullMetrics returns null for single track point`() {
        val points = listOf(
            point(timestamp = 0L, hr = 140)
        )
        assertNull(
            MetricsCalculator.deriveFullMetrics(
                workoutId = 1L,
                recordedAtMs = 0L,
                trackPoints = points
            )
        )
    }

    @Test
    fun `deriveFullMetrics returns null for empty track points`() {
        assertNull(
            MetricsCalculator.deriveFullMetrics(
                workoutId = 1L,
                recordedAtMs = 0L,
                trackPoints = emptyList()
            )
        )
    }

    private fun point(timestamp: Long, hr: Int): TrackPointEntity {
        return TrackPointEntity(
            workoutId = 1L,
            timestamp = timestamp,
            latitude = 0.0,
            longitude = 0.0,
            heartRate = hr,
            distanceMeters = 0f
        )
    }
}
