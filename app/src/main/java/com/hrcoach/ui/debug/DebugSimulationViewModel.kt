package com.hrcoach.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.domain.simulation.SimulationScenario
import com.hrcoach.service.simulation.SimulationController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    init {
        viewModelScope.launch {
            SimulationController.state.collect { simState ->
                _uiState.update { it.copy(isSimActive = simState.isActive) }
            }
        }
    }

    fun selectScenario(index: Int) {
        _uiState.update { it.copy(selectedScenarioIndex = index) }
    }

    fun setSpeed(speed: Float) {
        _uiState.update { it.copy(speedMultiplier = speed) }
        if (SimulationController.isActive) {
            SimulationController.setSpeed(speed)
        }
    }

    fun toggleSimulation() {
        if (SimulationController.isActive) {
            SimulationController.deactivate()
            _uiState.update { it.copy(isSimActive = false) }
        } else {
            val state = _uiState.value
            val scenario = state.scenarios[state.selectedScenarioIndex]
            SimulationController.activate(scenario, state.speedMultiplier)
            _uiState.update { it.copy(isSimActive = true) }
        }
    }
}
