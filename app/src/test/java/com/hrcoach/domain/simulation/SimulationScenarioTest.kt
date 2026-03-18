package com.hrcoach.domain.simulation

import org.junit.Assert.*
import org.junit.Test

class SimulationScenarioTest {

    @Test
    fun `interpolateHr returns exact value at data point`() {
        val scenario = SimulationScenario(
            name = "test", durationSeconds = 60,
            hrProfile = listOf(HrDataPoint(0, 70), HrDataPoint(60, 140)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f))
        )
        assertEquals(70, scenario.interpolateHr(0f))
        assertEquals(140, scenario.interpolateHr(60f))
    }

    @Test
    fun `interpolateHr linearly interpolates between points`() {
        val scenario = SimulationScenario(
            name = "test", durationSeconds = 60,
            hrProfile = listOf(HrDataPoint(0, 100), HrDataPoint(60, 160)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f))
        )
        assertEquals(130, scenario.interpolateHr(30f))
    }

    @Test
    fun `interpolateHr clamps beyond last point`() {
        val scenario = SimulationScenario(
            name = "test", durationSeconds = 60,
            hrProfile = listOf(HrDataPoint(0, 100), HrDataPoint(60, 160)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f))
        )
        assertEquals(160, scenario.interpolateHr(120f))
    }

    @Test
    fun `interpolatePace returns exact value at data point`() {
        val scenario = SimulationScenario(
            name = "test", durationSeconds = 60,
            hrProfile = listOf(HrDataPoint(0, 100)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f), PaceDataPoint(60, 5.0f))
        )
        assertEquals(6.0f, scenario.interpolatePace(0f), 0.01f)
        assertEquals(5.0f, scenario.interpolatePace(60f), 0.01f)
    }

    @Test
    fun `isSignalLost returns true during signal loss event`() {
        val scenario = SimulationScenario(
            name = "test", durationSeconds = 120,
            hrProfile = listOf(HrDataPoint(0, 100)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f)),
            events = listOf(SimEvent.SignalLoss(atSeconds = 30, durationSeconds = 15))
        )
        assertFalse(scenario.isSignalLost(29f))
        assertTrue(scenario.isSignalLost(30f))
        assertTrue(scenario.isSignalLost(44f))
        assertFalse(scenario.isSignalLost(45f))
    }

    @Test
    fun `isGpsDropout returns true during GPS dropout event`() {
        val scenario = SimulationScenario(
            name = "test", durationSeconds = 120,
            hrProfile = listOf(HrDataPoint(0, 100)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f)),
            events = listOf(SimEvent.GpsDropout(atSeconds = 50, durationSeconds = 20))
        )
        assertFalse(scenario.isGpsDropout(49f))
        assertTrue(scenario.isGpsDropout(50f))
        assertTrue(scenario.isGpsDropout(69f))
        assertFalse(scenario.isGpsDropout(70f))
    }

    @Test
    fun `isStopped returns true during stop event`() {
        val scenario = SimulationScenario(
            name = "test", durationSeconds = 120,
            hrProfile = listOf(HrDataPoint(0, 100)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f)),
            events = listOf(SimEvent.Stop(atSeconds = 60, durationSeconds = 30))
        )
        assertFalse(scenario.isStopped(59f))
        assertTrue(scenario.isStopped(60f))
        assertFalse(scenario.isStopped(90f))
    }

    @Test
    fun `preset EASY_STEADY_RUN has valid structure`() {
        val s = SimulationScenario.EASY_STEADY_RUN
        assertTrue(s.hrProfile.size >= 2)
        assertTrue(s.paceProfile.isNotEmpty())
        assertEquals(1200, s.durationSeconds)
    }

    @Test
    fun `ALL_PRESETS contains 5 scenarios`() {
        assertEquals(5, SimulationScenario.ALL_PRESETS.size)
    }
}
