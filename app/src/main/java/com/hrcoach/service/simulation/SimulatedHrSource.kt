package com.hrcoach.service.simulation

import com.hrcoach.domain.simulation.HrDataSource
import com.hrcoach.domain.simulation.SimulationScenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Random

class SimulatedHrSource(
    private val clock: SimulationClock,
    private val scenario: SimulationScenario
) : HrDataSource {

    private val _heartRate = MutableStateFlow(0)
    override val heartRate: StateFlow<Int> = _heartRate

    private val _isConnected = MutableStateFlow(true)
    override val isConnected: StateFlow<Boolean> = _isConnected

    private val random = Random()

    fun updateForTime(elapsedSeconds: Float) {
        if (scenario.isSignalLost(elapsedSeconds)) {
            _isConnected.value = false
            _heartRate.value = 0
        } else {
            _isConnected.value = true
            val baseHr = scenario.interpolateHr(elapsedSeconds)
            val noise = (random.nextGaussian() * 2).toInt()
            _heartRate.value = (baseHr + noise).coerceIn(30, 220)
        }
    }
}
