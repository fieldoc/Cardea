package com.hrcoach.service.simulation

import com.hrcoach.domain.simulation.SimulationScenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SimulationState(
    val isActive: Boolean = false,
    val scenario: SimulationScenario? = null,
    val speedMultiplier: Float = 1f
)

object SimulationController {
    private val _state = MutableStateFlow(SimulationState())
    val state: StateFlow<SimulationState> = _state.asStateFlow()

    val isActive: Boolean get() = _state.value.isActive

    private var clock: SimulationClock? = null

    fun activate(scenario: SimulationScenario, speed: Float = 1f) {
        _state.value = SimulationState(
            isActive = true,
            scenario = scenario,
            speedMultiplier = speed
        )
    }

    fun deactivate() {
        clock = null
        _state.value = SimulationState()
    }

    fun attachClock(simClock: SimulationClock) {
        clock = simClock
    }

    fun setSpeed(speed: Float) {
        clock?.setSpeed(speed)
        _state.update { it.copy(speedMultiplier = speed) }
    }
}
