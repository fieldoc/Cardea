package com.hrcoach.service.simulation

import com.hrcoach.domain.simulation.WorkoutClock
import kotlinx.coroutines.flow.MutableStateFlow

class SimulationClock(
    val speedMultiplier: MutableStateFlow<Float> = MutableStateFlow(1f)
) : WorkoutClock {
    private var anchorRealMs: Long = System.currentTimeMillis()
    private var anchorSimMs: Long = anchorRealMs

    override fun now(): Long {
        val realElapsed = System.currentTimeMillis() - anchorRealMs
        return anchorSimMs + (realElapsed * speedMultiplier.value).toLong()
    }

    fun setSpeed(multiplier: Float) {
        val currentSim = now()
        anchorRealMs = System.currentTimeMillis()
        anchorSimMs = currentSim
        speedMultiplier.value = multiplier
    }

    fun advanceBy(deltaMs: Long) {
        anchorSimMs += deltaMs
        anchorRealMs = System.currentTimeMillis()
    }

    fun reset() {
        anchorRealMs = System.currentTimeMillis()
        anchorSimMs = anchorRealMs
        speedMultiplier.value = 1f
    }
}
