package com.hrcoach.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.BuildConfig
import com.hrcoach.data.db.TrackPointEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.MapsSettingsRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.domain.engine.MetricsCalculator
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository,
    private val mapsSettingsRepository: MapsSettingsRepository
) : ViewModel() {

    val workouts: StateFlow<List<WorkoutEntity>> = repository.getAllWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedWorkout = MutableStateFlow<WorkoutEntity?>(null)
    val selectedWorkout: StateFlow<WorkoutEntity?> = _selectedWorkout.asStateFlow()

    private val _trackPoints = MutableStateFlow<List<TrackPointEntity>>(emptyList())
    val trackPoints: StateFlow<List<TrackPointEntity>> = _trackPoints.asStateFlow()

    private val _selectedMetrics = MutableStateFlow<WorkoutAdaptiveMetrics?>(null)
    val selectedMetrics: StateFlow<WorkoutAdaptiveMetrics?> = _selectedMetrics.asStateFlow()

    private val _isDetailLoading = MutableStateFlow(false)
    val isDetailLoading: StateFlow<Boolean> = _isDetailLoading.asStateFlow()

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    private val _isMapsEnabled = MutableStateFlow(hasAnyMapsApiKey())
    val isMapsEnabled: StateFlow<Boolean> = _isMapsEnabled.asStateFlow()

    fun loadWorkoutDetail(workoutId: Long) {
        viewModelScope.launch {
            _isDetailLoading.value = true
            _detailError.value = null
            _isMapsEnabled.value = hasAnyMapsApiKey()
            runCatching {
                val workout = repository.getWorkoutById(workoutId)
                val points = repository.getTrackPoints(workoutId)
                _selectedWorkout.value = workout
                _trackPoints.value = points
                _selectedMetrics.value = workout?.let { candidate ->
                    val stored = workoutMetricsRepository.getWorkoutMetrics(candidate.id)
                    stored ?: run {
                        val recordedAtMs = if (candidate.endTime > candidate.startTime) {
                            candidate.endTime
                        } else {
                            candidate.startTime
                        }
                        MetricsCalculator.deriveFullMetrics(
                            workoutId = candidate.id,
                            recordedAtMs = recordedAtMs,
                            trackPoints = points
                        )?.also { workoutMetricsRepository.saveWorkoutMetrics(it) }
                    }
                }
            }.onFailure {
                _selectedWorkout.value = null
                _trackPoints.value = emptyList()
                _selectedMetrics.value = null
                _detailError.value = "Unable to load workout details."
            }
            _isDetailLoading.value = false
        }
    }

    fun deleteWorkout(workoutId: Long) {
        viewModelScope.launch {
            repository.deleteWorkout(workoutId)
            workoutMetricsRepository.deleteWorkoutMetrics(workoutId)
            _selectedWorkout.value = null
            _trackPoints.value = emptyList()
            _selectedMetrics.value = null
        }
    }

    private fun hasAnyMapsApiKey(): Boolean {
        val runtimeKey = mapsSettingsRepository.getMapsApiKey()
        val builtIn = BuildConfig.MAPS_API_KEY
        return runtimeKey.isNotBlank() ||
            (builtIn.isNotBlank() && builtIn != "YOUR_API_KEY_HERE")
    }
}
