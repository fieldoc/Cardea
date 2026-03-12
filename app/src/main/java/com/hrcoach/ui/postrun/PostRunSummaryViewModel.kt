package com.hrcoach.ui.postrun

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.AchievementEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.domain.achievement.AchievementEvaluator
import com.hrcoach.domain.achievement.StreakCalculator
import com.hrcoach.domain.bootcamp.BootcampSessionCompleter
import com.hrcoach.domain.engine.MetricsCalculator
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import com.hrcoach.service.WorkoutState
import com.hrcoach.ui.common.MetricLabels
import com.hrcoach.util.formatDuration
import com.hrcoach.util.formatWorkoutDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val isHrrActive: Boolean = false,
    val hrrSecondsRemaining: Int = 120,
    val isBootcampRun: Boolean = false,
    val achievements: List<AchievementEntity> = emptyList()
)

@HiltViewModel
class PostRunSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository,
    private val bootcampSessionCompleter: BootcampSessionCompleter,
    private val achievementEvaluator: AchievementEvaluator,
    private val achievementDao: AchievementDao,
    private val bootcampRepository: BootcampRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostRunSummaryUiState())
    val uiState: StateFlow<PostRunSummaryUiState> = _uiState.asStateFlow()

    private val workoutId: Long? = savedStateHandle.get<Long>("workoutId")
    private val isFreshWorkout: Boolean = savedStateHandle.get<Boolean>("fresh") ?: false

    init {
        load()
        startHrrCountdownIfNeeded()
    }

    private fun startHrrCountdownIfNeeded() {
        viewModelScope.launch {
            // Wait for load to settle
            _uiState.collect { state ->
                if (state.isHrrActive && !state.isLoading) {
                    // Start the countdown
                    runCatching {
                        for (remaining in 119 downTo 0) {
                            kotlinx.coroutines.delay(1_000L)
                            _uiState.update { it.copy(hrrSecondsRemaining = remaining) }
                        }
                        // Countdown finished — close the HRR window
                        _uiState.update { it.copy(isHrrActive = false) }
                    }
                    return@collect  // stop observing after countdown starts
                }
            }
        }
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
                    comparisons = comparisons,
                    isHrrActive = isFreshWorkout
                )

                if (isFreshWorkout) {
                    runCatching {
                        bootcampSessionCompleter.complete(
                            workoutId = id,
                            pendingSessionId = WorkoutState.snapshot.value.pendingBootcampSessionId
                        )
                    }.getOrNull()?.let { result ->
                        if (result.completed) {
                            WorkoutState.setPendingBootcampSessionId(null)
                            _uiState.update {
                                it.copy(
                                    bootcampProgressLabel = result.progressLabel,
                                    bootcampWeekComplete = result.weekComplete,
                                    isBootcampRun = true
                                )
                            }
                        }
                    }

                    // Achievement evaluation — runs after workout is fully persisted
                    try {
                        val workoutId = id
                        // Distance milestone
                        val totalKm = workoutRepository.sumAllDistanceKm()
                        achievementEvaluator.evaluateDistance(totalKm, workoutId)

                        // Streak + weekly goal (only if bootcamp enrollment exists)
                        bootcampRepository.getActiveEnrollmentOnce()?.let { enrollment ->
                            val sessions = bootcampRepository.getSessionsForEnrollmentOnce(enrollment.id)
                            val streak = StreakCalculator.computeSessionStreak(sessions, enrollment.startDate)
                            achievementEvaluator.evaluateStreak(streak, workoutId)

                            val weeklyStreak = StreakCalculator.computeWeeklyGoalStreak(
                                sessions, enrollment.runsPerWeek, enrollment.startDate
                            )
                            achievementEvaluator.evaluateWeeklyGoalStreak(weeklyStreak, workoutId)
                        }

                        // Load unshown achievements for display
                        val unshown = achievementDao.getUnshownAchievements()
                        _uiState.update { it.copy(achievements = unshown) }
                    } catch (e: Exception) {
                        // Achievement evaluation is non-critical — don't break post-run flow
                    }
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Unable to load post-run summary."
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val ids = _uiState.value.achievements.map { it.id }
        if (ids.isNotEmpty()) {
            viewModelScope.launch {
                withContext(NonCancellable) {
                    try { achievementDao.markShown(ids) } catch (_: Exception) {}
                }
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
