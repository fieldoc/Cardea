package com.hrcoach.ui.workout

import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.model.DistanceUnit
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.service.WorkoutSnapshot
import com.hrcoach.service.WorkoutState
import com.hrcoach.util.JsonCodec
import com.hrcoach.util.metersToUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class SegmentInfo(
    val label: String? = null,
    val countdownSeconds: Long? = null,
    val nextLabel: String? = null
)

private data class ProgressInfo(
    val totalDurationSeconds: Long? = null,
    val totalDistanceMeters: Float? = null,
    val workoutTypeLabel: String? = null,
    val bootcampWeekNumber: Int? = null,
    val rawSessionType: String? = null
)

data class ActiveWorkoutUiState(
    val snapshot: WorkoutSnapshot = WorkoutSnapshot(),
    val elapsedSeconds: Long = 0L,
    val workoutConfig: WorkoutConfig? = null,
    val segmentLabel: String? = null,
    val segmentCountdownSeconds: Long? = null,
    val nextSegmentLabel: String? = null,
    val totalDurationSeconds: Long? = null,
    val totalDistanceMeters: Float? = null,
    val workoutTypeLabel: String? = null,
    val bootcampWeekNumber: Int? = null,
    val remainingSeconds: Long? = null,
    val rawSessionType: String? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.KM
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val distanceUnit = DistanceUnit.fromString(userProfileRepository.getDistanceUnit())

    private val _uiState = MutableStateFlow(
        ActiveWorkoutUiState(snapshot = WorkoutState.snapshot.value)
    )
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()

    private var workoutStartTimeMs: Long? = null
    private var pausedAccumulatedMs: Long = 0L
    private var pauseStartedAtMs: Long? = null
    private var autoPausedAccumulatedMs: Long = 0L
    private var autoPauseStartedAtMs: Long? = null
    private var loadingWorkoutMetadata: Boolean = false

    init {
        if (WorkoutState.snapshot.value.isRunning) {
            viewModelScope.launch { loadActiveWorkoutMetadata() }
        }

        viewModelScope.launch {
            WorkoutState.snapshot.collect(::handleSnapshot)
        }

        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val snapshot = _uiState.value.snapshot
                if (!snapshot.isRunning) continue
                _uiState.update { current ->
                    val snap = current.snapshot
                    val newElapsed = if (snap.elapsedSeconds > 0L) snap.elapsedSeconds
                                     else computeElapsedSeconds(System.currentTimeMillis())
                    val seg = deriveSegmentInfo(current.workoutConfig, newElapsed)
                    val prog = deriveProgressInfo(current.workoutConfig)
                    current.copy(
                        elapsedSeconds = newElapsed,
                        segmentLabel = seg.label,
                        segmentCountdownSeconds = seg.countdownSeconds,
                        nextSegmentLabel = seg.nextLabel,
                        totalDurationSeconds = prog.totalDurationSeconds,
                        totalDistanceMeters = prog.totalDistanceMeters,
                        workoutTypeLabel = prog.workoutTypeLabel,
                        bootcampWeekNumber = prog.bootcampWeekNumber,
                        remainingSeconds = prog.totalDurationSeconds?.let { (it - newElapsed).coerceAtLeast(0L) },
                        rawSessionType = prog.rawSessionType,
                        distanceUnit = distanceUnit
                    )
                }
            }
        }
    }

    private fun handleSnapshot(snapshot: WorkoutSnapshot) {
        val nowMs = System.currentTimeMillis()

        when {
            snapshot.isRunning && workoutStartTimeMs == null -> {
                workoutStartTimeMs = nowMs
                viewModelScope.launch { loadActiveWorkoutMetadata() }
            }

            snapshot.isRunning && _uiState.value.workoutConfig == null -> {
                viewModelScope.launch { loadActiveWorkoutMetadata() }
            }

            !snapshot.isRunning -> {
                workoutStartTimeMs = null
                pausedAccumulatedMs = 0L
                pauseStartedAtMs = null
                autoPausedAccumulatedMs = 0L
                autoPauseStartedAtMs = null
            }
        }

        if (snapshot.isRunning) {
            if (snapshot.isPaused) {
                if (pauseStartedAtMs == null) pauseStartedAtMs = nowMs
            } else if (pauseStartedAtMs != null) {
                pausedAccumulatedMs += nowMs - (pauseStartedAtMs ?: nowMs)
                pauseStartedAtMs = null
            }

            if (snapshot.isAutoPaused) {
                if (autoPauseStartedAtMs == null) autoPauseStartedAtMs = nowMs
            } else if (autoPauseStartedAtMs != null) {
                autoPausedAccumulatedMs += nowMs - (autoPauseStartedAtMs ?: nowMs)
                autoPauseStartedAtMs = null
            }
        }

        _uiState.update { current ->
            val newElapsed = when {
                !snapshot.isRunning -> 0L
                snapshot.elapsedSeconds > 0L -> snapshot.elapsedSeconds
                else -> computeElapsedSeconds(nowMs)
            }
            val config = if (snapshot.isRunning) current.workoutConfig else null
            val seg = deriveSegmentInfo(config, newElapsed)
            val prog = deriveProgressInfo(config)
            current.copy(
                snapshot = snapshot,
                elapsedSeconds = newElapsed,
                workoutConfig = config,
                segmentLabel = seg.label,
                segmentCountdownSeconds = seg.countdownSeconds,
                nextSegmentLabel = seg.nextLabel,
                totalDurationSeconds = prog.totalDurationSeconds,
                totalDistanceMeters = prog.totalDistanceMeters,
                workoutTypeLabel = prog.workoutTypeLabel,
                bootcampWeekNumber = prog.bootcampWeekNumber,
                remainingSeconds = prog.totalDurationSeconds?.let { (it - newElapsed).coerceAtLeast(0L) },
                rawSessionType = prog.rawSessionType,
                distanceUnit = distanceUnit
            )
        }
    }

    private suspend fun loadActiveWorkoutMetadata() {
        if (loadingWorkoutMetadata) return
        loadingWorkoutMetadata = true
        try {
            val activeWorkout = workoutRepository
                .getAllWorkoutsOnce()
                .firstOrNull { it.endTime == 0L }
                ?: return

            workoutStartTimeMs = activeWorkout.startTime.takeIf { it > 0L } ?: workoutStartTimeMs
            val config = runCatching {
                JsonCodec.gson.fromJson(activeWorkout.targetConfig, WorkoutConfig::class.java)
            }.onFailure { e ->
                Log.w("WorkoutVM", "Failed to parse workout config JSON", e)
            }.getOrNull()

            _uiState.update { current ->
                val newElapsed = computeElapsedSeconds(System.currentTimeMillis())
                val seg = deriveSegmentInfo(config, newElapsed)
                val prog = deriveProgressInfo(config)
                current.copy(
                    workoutConfig = config,
                    elapsedSeconds = newElapsed,
                    segmentLabel = seg.label,
                    segmentCountdownSeconds = seg.countdownSeconds,
                    nextSegmentLabel = seg.nextLabel,
                    totalDurationSeconds = prog.totalDurationSeconds,
                    totalDistanceMeters = prog.totalDistanceMeters,
                    workoutTypeLabel = prog.workoutTypeLabel,
                    bootcampWeekNumber = prog.bootcampWeekNumber,
                    remainingSeconds = prog.totalDurationSeconds?.let { (it - newElapsed).coerceAtLeast(0L) },
                    rawSessionType = prog.rawSessionType,
                    distanceUnit = distanceUnit
                )
            }
        } finally {
            loadingWorkoutMetadata = false
        }
    }

    private fun computeElapsedSeconds(nowMs: Long): Long {
        val startTimeMs = workoutStartTimeMs ?: return 0L
        val currentPauseMs = pauseStartedAtMs?.let { nowMs - it } ?: 0L
        val currentAutoPauseMs = autoPauseStartedAtMs?.let { nowMs - it } ?: 0L
        return ((nowMs - startTimeMs - pausedAccumulatedMs - currentPauseMs
                - autoPausedAccumulatedMs - currentAutoPauseMs).coerceAtLeast(0L) / 1_000L)
    }

    private fun deriveSegmentInfo(config: WorkoutConfig?, elapsed: Long): SegmentInfo {
        if (config == null || !config.isTimeBased()) return SegmentInfo()
        val result = config.segmentAtElapsed(elapsed) ?: return SegmentInfo()
        val (index, seg) = result
        var cumulative = 0L
        for (i in 0..index) {
            cumulative += config.segments[i].durationSeconds?.toLong() ?: 0L
        }
        val nextLabel = config.segments.drop(index + 1).firstOrNull { it.label != null }?.label
        return SegmentInfo(seg.label, cumulative - elapsed, nextLabel)
    }

    private fun deriveProgressInfo(config: WorkoutConfig?): ProgressInfo {
        if (config == null) return ProgressInfo()
        val week = config.bootcampWeekNumber
        val rawType = config.sessionLabel?.uppercase()
        return when {
            config.mode == WorkoutMode.FREE_RUN -> {
                val duration = config.plannedDurationMinutes
                val label = config.sessionLabel
                when {
                    duration != null && label != null ->
                        ProgressInfo(
                            totalDurationSeconds = duration.toLong() * 60,
                            workoutTypeLabel = "$duration min \u00b7 $label",
                            bootcampWeekNumber = week,
                            rawSessionType = rawType
                        )
                    duration != null ->
                        ProgressInfo(
                            totalDurationSeconds = duration.toLong() * 60,
                            workoutTypeLabel = "$duration min \u00b7 Timed run",
                            bootcampWeekNumber = week,
                            rawSessionType = rawType
                        )
                    label != null ->
                        ProgressInfo(workoutTypeLabel = label, bootcampWeekNumber = week, rawSessionType = rawType)
                    else ->
                        ProgressInfo(workoutTypeLabel = "Open-ended \u00b7 No target", rawSessionType = rawType)
                }
            }

            config.isTimeBased() -> {
                val total = config.segments.sumOf { it.durationSeconds?.toLong() ?: 0L }
                val mins = total / 60
                ProgressInfo(
                    totalDurationSeconds = total,
                    workoutTypeLabel = "${mins} min \u00b7 Steady-state",
                    bootcampWeekNumber = week,
                    rawSessionType = rawType
                )
            }

            config.mode == WorkoutMode.DISTANCE_PROFILE && config.segments.isNotEmpty() -> {
                val totalDist = config.segments.lastOrNull()?.distanceMeters
                val distVal = totalDist?.let { "%.1f".format(metersToUnit(it, distanceUnit)) } ?: "?"
                val unitLabel = if (distanceUnit == DistanceUnit.MI) "mi" else "km"
                ProgressInfo(
                    totalDistanceMeters = totalDist,
                    workoutTypeLabel = "$distVal $unitLabel \u00b7 Distance profile",
                    bootcampWeekNumber = week,
                    rawSessionType = rawType
                )
            }

            else -> ProgressInfo()
        }
    }
}
