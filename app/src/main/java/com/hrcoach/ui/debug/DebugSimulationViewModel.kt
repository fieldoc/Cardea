package com.hrcoach.ui.debug

import androidx.lifecycle.ViewModel
import com.hrcoach.domain.simulation.SimulationScenario
import com.hrcoach.service.simulation.SimulationController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DebugSimUiState(
    val isSimActive: Boolean = false,
    val selectedScenarioIndex: Int = 0,
    val speedMultiplier: Float = 1f,
    val scenarios: List<SimulationScenario> = SimulationScenario.ALL_PRESETS
)

@HiltViewModel
class DebugSimulationViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(DebugSimUiState(isSimActive = SimulationController.isActive))
    val uiState: StateFlow<DebugSimUiState> = _uiState.asStateFlow()

    fun selectScenario(index: Int) {
        _uiState.value = _uiState.value.copy(selectedScenarioIndex = index)
    }

    fun setSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(speedMultiplier = speed)
        if (SimulationController.isActive) {
            SimulationController.setSpeed(speed)
        }
    }

    fun toggleSimulation() {
        if (SimulationController.isActive) {
            SimulationController.deactivate()
            _uiState.value = _uiState.value.copy(isSimActive = false)
        } else {
            val state = _uiState.value
            val scenario = state.scenarios[state.selectedScenarioIndex]
            SimulationController.activate(scenario, state.speedMultiplier)
            _uiState.value = state.copy(isSimActive = true)
        }
    }
}
