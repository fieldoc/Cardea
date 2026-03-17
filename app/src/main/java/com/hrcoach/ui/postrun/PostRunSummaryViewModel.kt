package com.hrcoach.ui.postrun

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.domain.achievement.AchievementEvaluator
import com.hrcoach.domain.bootcamp.BootcampSessionCompleter
import com.hrcoach.domain.engine.MetricsCalculator
import com.hrcoach.service.WorkoutState
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import com.hrcoach.util.formatDuration
import com.hrcoach.util.formatWorkoutDate
import com.hrcoach.util.metersToKm
import com.hrcoach.util.recordedAtMs
import com.hrcoach.ui.common.MetricLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
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
    val comparisons: List<PostRunComparison> = emptyList(),
    val bootcampProgressLabel: String? = null,
    val bootcampWeekComplete: Boolean = false,
    val isBootcampRun: Boolean = false,
    val isHrrActive: Boolean = false,
    val newAchievements: List<AchievementEntity> = emptyList(),
)

@HiltViewModel
class PostRunSummaryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository,
    private val bootcampSessionCompleter: BootcampSessionCompleter,
    private val achievementEvaluator: AchievementEvaluator,
    private val achievementDao: AchievementDao
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

                val isHrrActive = System.currentTimeMillis() - workout.endTime < 180_000L

                _uiState.value = PostRunSummaryUiState(
                    isLoading = false,
                    errorMessage = null,
                    titleText = formatWorkoutDate(workout.startTime),
                    distanceText = String.format("%.2f km", metersToKm(workout.totalDistanceMeters)),
                    durationText = formatDuration(workout.startTime, workout.endTime),
                    avgHrText = avgHr?.let { "${it.toInt()} bpm" } ?: "--",
                    similarRunCount = similar.size,
                    comparisons = comparisons,
                    isHrrActive = isHrrActive,
                )

                // Wire bootcamp session completion
                val fresh = savedStateHandle.get<Boolean>("fresh") ?: false
                if (fresh) {
                    val pendingId = WorkoutState.snapshot.value.pendingBootcampSessionId
                    if (pendingId != null) {
                        val result = bootcampSessionCompleter.complete(
                            workoutId = id,
                            pendingSessionId = pendingId
                        )
                        if (result.completed) {
                            WorkoutState.setPendingBootcampSessionId(null)
                            _uiState.value = _uiState.value.copy(
                                isBootcampRun = true,
                                bootcampProgressLabel = result.progressLabel,
                                bootcampWeekComplete = result.weekComplete
                            )
                        }
                    }

                    // Evaluate achievements
                    val allWorkouts = workoutRepository.getAllWorkoutsOnce()
                    val totalKm = allWorkouts.sumOf { it.totalDistanceMeters.toDouble() } / 1000.0
                    achievementEvaluator.evaluateDistance(totalKm, id)

                    val streak = computeWorkoutStreak(allWorkouts)
                    achievementEvaluator.evaluateStreak(streak, id)

                    // Surface newly earned achievements
                    val newAchievements = achievementDao.getUnshownAchievements()
                    if (newAchievements.isNotEmpty()) {
                        achievementDao.markShown(newAchievements.map { it.id })
                        _uiState.value = _uiState.value.copy(
                            newAchievements = newAchievements
                        )
                    }
                }
            }.onFailure {
                Log.e("PostRunSummaryVM", "Failed to load post-run summary", it)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = it.message ?: "Unable to load post-run summary."
                )
            }
        }
    }

    private suspend fun getOrDeriveMetrics(workout: WorkoutEntity): WorkoutAdaptiveMetrics? {
        val stored = workoutMetricsRepository.getWorkoutMetrics(workout.id)
        if (stored != null) return stored

        val trackPoints = workoutRepository.getTrackPoints(workout.id)
        val derived = MetricsCalculator.deriveFullMetrics(
            workoutId = workout.id,
            recordedAtMs = workout.recordedAtMs,
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

    internal companion object {
        fun computeWorkoutStreak(workouts: List<WorkoutEntity>): Int {
            val sorted = workouts.sortedByDescending { it.startTime }
            if (sorted.isEmpty()) return 0
            var streak = 1
            for (i in 0 until sorted.lastIndex) {
                val gapDays = (sorted[i].startTime - sorted[i + 1].startTime) / 86_400_000L
                if (gapDays > 10) break
                streak++
            }
            return streak
        }
    }
}
