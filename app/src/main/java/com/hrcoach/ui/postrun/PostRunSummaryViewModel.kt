package com.hrcoach.ui.postrun

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.domain.engine.MetricsCalculator
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import com.hrcoach.util.formatDuration
import com.hrcoach.util.formatWorkoutDate
import com.hrcoach.ui.common.MetricLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

data class PostRunComparison(
    val title: String,
    val value: String,
    val delta: String? = null,
    val insight: String? = null,
    val positive: Boolean? = null
)

data class PostRunSummaryUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val titleText: String = "Post-run Summary",
    val distanceText: String = "--",
    val durationText: String = "--",
    val avgHrText: String = "--",
    val similarRunCount: Int = 0,
    val comparisons: List<PostRunComparison> = emptyList()
)

@HiltViewModel
class PostRunSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostRunSummaryUiState())
    val uiState: StateFlow<PostRunSummaryUiState> = _uiState.asStateFlow()

    private val workoutId: Long? = savedStateHandle.get<Long>("workoutId")

    init {
        load()
    }

    private fun load() {
        val id = workoutId
        if (id == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Workout summary unavailable."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val workout = workoutRepository.getWorkoutById(id)
                    ?: error("Workout not found.")
                val currentMetrics = getOrDeriveMetrics(workout)
                val similar = loadSimilarWorkouts(workout)
                val comparisons = buildComparisons(
                    currentMetrics = currentMetrics,
                    similarMetrics = similar.mapNotNull { it.second }
                )
                val avgHr = currentMetrics?.avgHr ?: workoutRepository.getTrackPoints(workout.id)
                    .map { it.heartRate }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.toFloat()

                _uiState.value = PostRunSummaryUiState(
                    isLoading = false,
                    errorMessage = null,
                    titleText = formatWorkoutDate(workout.startTime),
                    distanceText = String.format("%.2f km", workout.totalDistanceMeters / 1000f),
                    durationText = formatDuration(workout.startTime, workout.endTime),
                    avgHrText = avgHr?.let { "${it.toInt()} bpm" } ?: "--",
                    similarRunCount = similar.size,
                    comparisons = comparisons
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Unable to load post-run summary."
                )
            }
        }
    }

    private suspend fun getOrDeriveMetrics(workout: WorkoutEntity): WorkoutAdaptiveMetrics? {
        val stored = workoutMetricsRepository.getWorkoutMetrics(workout.id)
        if (stored != null) return stored

        val trackPoints = workoutRepository.getTrackPoints(workout.id)
        val recordedAtMs = if (workout.endTime > workout.startTime) workout.endTime else workout.startTime
        val derived = MetricsCalculator.deriveFullMetrics(
            workoutId = workout.id,
            recordedAtMs = recordedAtMs,
            trackPoints = trackPoints
        )
        if (derived != null) {
            workoutMetricsRepository.saveWorkoutMetrics(derived)
        }
        return derived
    }

    private suspend fun loadSimilarWorkouts(workout: WorkoutEntity): List<Pair<WorkoutEntity, WorkoutAdaptiveMetrics?>> {
        if (workout.totalDistanceMeters <= 0f) return emptyList()
        val all = workoutRepository.getAllWorkoutsOnce()
        val similar = all.filter { candidate ->
            candidate.id != workout.id &&
                candidate.mode == workout.mode &&
                candidate.totalDistanceMeters > 0f &&
                isSimilarDistance(workout.totalDistanceMeters, candidate.totalDistanceMeters)
        }
        return similar.map { candidate ->
            candidate to getOrDeriveMetrics(candidate)
        }
    }

    private fun isSimilarDistance(baseMeters: Float, candidateMeters: Float): Boolean {
        if (baseMeters <= 0f) return false
        val ratio = abs(candidateMeters - baseMeters) / baseMeters
        return ratio <= 0.2f
    }

    private fun buildComparisons(
        currentMetrics: WorkoutAdaptiveMetrics?,
        similarMetrics: List<WorkoutAdaptiveMetrics>
    ): List<PostRunComparison> {
        if (currentMetrics == null) return emptyList()
        val items = mutableListOf<PostRunComparison>()

        val similarHrAtSix = similarMetrics.mapNotNull { it.hrAtSixMinPerKm }
        currentMetrics.hrAtSixMinPerKm?.let { current ->
            val delta = similarHrAtSix.takeIf { it.isNotEmpty() }?.let { current - it.average().toFloat() }
            val deltaText = delta?.takeIf { abs(it) >= 2f }?.let {
                val absValue = abs(it).toInt()
                if (it < 0f) "$absValue bpm lower than similar average" else "$absValue bpm higher than similar average"
            }
            items += PostRunComparison(
                title = "HR @ 6:00/km",
                value = "${current.toInt()} bpm",
                delta = deltaText,
                positive = delta?.let { it < 0f }
            )
        }

        val similarEf = similarMetrics.mapNotNull { it.efficiencyFactor }
        currentMetrics.efficiencyFactor?.let { current ->
            val delta = similarEf.takeIf { it.isNotEmpty() }?.let { current - it.average().toFloat() }
            val deltaText = delta?.takeIf { abs(it) >= 0.03f }?.let {
                val sign = if (it >= 0f) "+" else ""
                "$sign${String.format("%.2f", it)} vs similar average"
            }
            items += PostRunComparison(
                title = MetricLabels.EFFICIENCY_FACTOR,
                value = String.format("%.2f", current),
                delta = deltaText,
                positive = delta?.let { it >= 0f }
            )
        }

        val similarDecoupling = similarMetrics.mapNotNull { it.aerobicDecoupling }
        currentMetrics.aerobicDecoupling?.let { current ->
            val delta = similarDecoupling.takeIf { it.isNotEmpty() }?.let { current - it.average().toFloat() }
            val deltaText = delta?.takeIf { abs(it) >= 0.8f }?.let {
                val sign = if (it >= 0f) "+" else ""
                "$sign${String.format("%.1f", it)}% vs similar average"
            }
            val insight = if (current < 5f) {
                "Good aerobic endurance"
            } else {
                "Aerobic endurance can improve"
            }
            items += PostRunComparison(
                title = MetricLabels.AEROBIC_DECOUPLING,
                value = String.format("%.1f%%", current),
                delta = deltaText,
                insight = insight,
                positive = current < 5f
            )
        }

        if (currentMetrics.responseLagSec > 0f) {
            items += PostRunComparison(
                title = "Recovery Lag",
                value = "${currentMetrics.responseLagSec.toInt()} s",
                insight = "Estimated delay between pace change and HR response."
            )
        }

        if (currentMetrics.longTermHrTrimBpm != 0f) {
            val trim = currentMetrics.longTermHrTrimBpm
            val sign = if (trim >= 0f) "+" else ""
            items += PostRunComparison(
                title = "Adaptive Trim",
                value = "$sign${String.format("%.1f", trim)} bpm",
                insight = "Long-term personalization offset currently applied to projections."
            )
        }

        if (currentMetrics.efFirstHalf != null && currentMetrics.efSecondHalf != null) {
            items += PostRunComparison(
                title = "Efficiency Split",
                value = "${String.format("%.2f", currentMetrics.efFirstHalf)} -> ${String.format("%.2f", currentMetrics.efSecondHalf)}",
                insight = "Efficiency by half helps explain drift and endurance consistency."
            )
        }

        return items
    }
}
