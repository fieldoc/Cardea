package com.hrcoach.service.simulation

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class SimulationClockTest {

    @Test
    fun `now returns real time at 1x speed`() {
        val clock = SimulationClock()
        val before = System.currentTimeMillis()
        val simTime = clock.now()
        val after = System.currentTimeMillis()
        assertTrue("sim time should be between before and after", simTime in before..after)
    }

    @Test
    fun `advanceBy steps simulated time forward`() {
        val clock = SimulationClock()
        val start = clock.now()
        clock.advanceBy(10_000L)
        val after = clock.now()
        assertTrue("sim time should advance by ~10s, was ${after - start}", after - start in 9_900L..10_200L)
    }

    @Test
    fun `setSpeed re-anchors without time jumps`() {
        val clock = SimulationClock()
        val before = clock.now()
        clock.setSpeed(10f)
        val after = clock.now()
        assertTrue("no time jump on speed change, delta was ${after - before}", after - before < 100L)
    }

    @Test
    fun `reset returns to real time`() {
        val clock = SimulationClock()
        clock.advanceBy(60_000L)
        clock.reset()
        val now = clock.now()
        val real = System.currentTimeMillis()
        assertTrue("reset should return to real time, delta was ${abs(now - real)}", abs(now - real) < 100L)
    }
}
