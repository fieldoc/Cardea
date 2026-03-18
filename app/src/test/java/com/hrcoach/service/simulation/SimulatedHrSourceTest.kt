package com.hrcoach.service.simulation

import com.hrcoach.domain.simulation.*
import org.junit.Assert.*
import org.junit.Test

class SimulatedHrSourceTest {

    private val scenario = SimulationScenario(
        name = "test", durationSeconds = 60,
        hrProfile = listOf(HrDataPoint(0, 100), HrDataPoint(60, 160)),
        paceProfile = listOf(PaceDataPoint(0, 6.0f))
    )

    @Test
    fun `initial state is connected with zero HR`() {
        val source = SimulatedHrSource(scenario)
        assertEquals(0, source.heartRate.value)
        assertTrue(source.isConnected.value)
    }

    @Test
    fun `updateForTime sets HR from scenario interpolation`() {
        val source = SimulatedHrSource(scenario)
        source.updateForTime(0f)
        assertTrue("HR at t=0 should be ~100, was ${source.heartRate.value}",
            source.heartRate.value in 94..106)
        source.updateForTime(30f)
        assertTrue("HR at t=30 should be ~130, was ${source.heartRate.value}",
            source.heartRate.value in 124..136)
    }

    @Test
    fun `signal loss event sets isConnected false and HR to 0`() {
        val scenarioWithLoss = scenario.copy(
            events = listOf(SimEvent.SignalLoss(atSeconds = 10, durationSeconds = 10))
        )
        val source = SimulatedHrSource(scenarioWithLoss)

        source.updateForTime(9f)
        assertTrue(source.isConnected.value)
        assertTrue(source.heartRate.value > 0)

        source.updateForTime(10f)
        assertFalse(source.isConnected.value)
        assertEquals(0, source.heartRate.value)

        source.updateForTime(20f)
        assertTrue(source.isConnected.value)
        assertTrue(source.heartRate.value > 0)
    }
}
