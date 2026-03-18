package com.hrcoach.service.simulation

import com.hrcoach.domain.simulation.*
import org.junit.Assert.*
import org.junit.Test

class SimulatedLocationSourceTest {

    private val scenario = SimulationScenario(
        name = "test", durationSeconds = 600,
        hrProfile = listOf(HrDataPoint(0, 140)),
        paceProfile = listOf(PaceDataPoint(0, 6.0f)) // 6 min/km = ~2.778 m/s
    )

    @Test
    fun `initial state has zero distance`() {
        val clock = SimulationClock()
        val source = SimulatedLocationSource(clock, scenario)
        assertEquals(0f, source.distanceMeters.value, 0.01f)
        assertNull(source.currentLocation.value)
        assertNull(source.currentSpeed.value)
    }

    @Test
    fun `updateForTime accumulates distance from pace`() {
        val clock = SimulationClock()
        val source = SimulatedLocationSource(clock, scenario)
        source.start()
        // At 6 min/km = 2.778 m/s, after 60s should be ~166.7m
        source.updateForTime(60f)
        val dist = source.distanceMeters.value
        assertTrue("distance should be ~166.7m, was $dist", dist in 150f..180f)
    }

    @Test
    fun `setMoving false pauses distance accumulation`() {
        val clock = SimulationClock()
        val source = SimulatedLocationSource(clock, scenario)
        source.start()
        source.updateForTime(60f)
        val distBefore = source.distanceMeters.value
        source.setMoving(false)
        source.updateForTime(120f)
        assertEquals("distance should not change when not moving", distBefore, source.distanceMeters.value, 0.01f)
    }

    @Test
    fun `setMoving true resumes distance accumulation`() {
        val clock = SimulationClock()
        val source = SimulatedLocationSource(clock, scenario)
        source.start()
        source.updateForTime(60f)
        source.setMoving(false)
        source.updateForTime(90f)
        val distPaused = source.distanceMeters.value
        source.setMoving(true)
        source.updateForTime(150f)
        assertTrue("distance should increase after resuming", source.distanceMeters.value > distPaused)
    }

    @Test
    fun `GPS dropout sets null location and speed`() {
        val scenarioWithDropout = scenario.copy(
            events = listOf(SimEvent.GpsDropout(atSeconds = 60, durationSeconds = 30))
        )
        val clock = SimulationClock()
        val source = SimulatedLocationSource(clock, scenarioWithDropout)
        source.start()

        source.updateForTime(59f)
        assertNotNull(source.currentLocation.value)

        source.updateForTime(60f)
        assertNull(source.currentLocation.value)
        assertNull(source.currentSpeed.value)

        source.updateForTime(90f)
        assertNotNull(source.currentLocation.value)
    }

    @Test
    fun `stop event sets speed to zero`() {
        val scenarioWithStop = scenario.copy(
            events = listOf(SimEvent.Stop(atSeconds = 60, durationSeconds = 30))
        )
        val clock = SimulationClock()
        val source = SimulatedLocationSource(clock, scenarioWithStop)
        source.start()

        source.updateForTime(59f)
        assertTrue((source.currentSpeed.value ?: 0f) > 0f)

        source.updateForTime(65f)
        assertEquals(0f, source.currentSpeed.value ?: -1f, 0.01f)
    }
}
