package com.hrcoach.service.simulation

import com.hrcoach.domain.simulation.HrDataSource
import com.hrcoach.domain.simulation.SimulationScenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Random

class SimulatedHrSource(
    private val scenario: SimulationScenario
) : HrDataSource {

    private val _heartRate = MutableStateFlow(0)
    override val heartRate: StateFlow<Int> = _heartRate

    private val _isConnected = MutableStateFlow(true)
    override val isConnected: StateFlow<Boolean> = _isConnected

    private val random = Random()
    private var spikeDecayRemaining = 0
    private var spikeAmount = 0

    fun updateForTime(elapsedSeconds: Float) {
        if (scenario.isSignalLost(elapsedSeconds)) {
            _isConnected.value = false
            _heartRate.value = 0
            spikeDecayRemaining = 0
            spikeAmount = 0
            return
        }

        _isConnected.value = true
        val baseHr = scenario.interpolateHr(elapsedSeconds)

        val noise = when {
            spikeDecayRemaining > 0 -> {
                spikeDecayRemaining--
                // Decay spike back toward 0 over remaining ticks
                if (spikeDecayRemaining == 1) spikeAmount / 2 else spikeAmount
            }
            random.nextFloat() < 0.05f -> {
                // 5% chance of a transient spike
                spikeAmount = if (random.nextBoolean()) 4 else -4
                spikeDecayRemaining = 2
                spikeAmount
            }
            random.nextFloat() < 0.10f -> if (random.nextBoolean()) 1 else -1 // ±1 step
            else -> 0
        }

        _heartRate.value = (baseHr + noise).coerceIn(30, 220)
    }
}
