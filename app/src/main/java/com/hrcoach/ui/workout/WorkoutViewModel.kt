package com.hrcoach.ui.workout

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.service.WorkoutSnapshot
import com.hrcoach.service.WorkoutState
import com.hrcoach.util.JsonCodec
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

data class ActiveWorkoutUiState(
    val snapshot: WorkoutSnapshot = WorkoutSnapshot(),
    val elapsedSeconds: Long = 0L,
    val workoutConfig: WorkoutConfig? = null,
    val segmentLabel: String? = null,
    val segmentCountdownSeconds: Long? = null,
    val nextSegmentLabel: String? = null
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ActiveWorkoutUiState(snapshot = WorkoutState.snapshot.value)
    )
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()

    private var workoutStartTimeMs: Long? = null
    private var pausedAccumulatedMs: Long = 0L
    private var pauseStartedAtMs: Long? = null
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
                    val newElapsed = computeElapsedSeconds(System.currentTimeMillis())
                    val (segLabel, segCountdown, nextLabel) = deriveSegmentInfo(current.workoutConfig, newElapsed)
                    current.copy(
                        elapsedSeconds = newElapsed,
                        segmentLabel = segLabel,
                        segmentCountdownSeconds = segCountdown,
                        nextSegmentLabel = nextLabel
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
            }
        }

        if (snapshot.isRunning) {
            if (snapshot.isPaused) {
                if (pauseStartedAtMs == null) {
                    pauseStartedAtMs = nowMs
                }
            } else if (pauseStartedAtMs != null) {
                pausedAccumulatedMs += nowMs - (pauseStartedAtMs ?: nowMs)
                pauseStartedAtMs = null
            }
        }

        _uiState.update { current ->
            val newElapsed = if (snapshot.isRunning) computeElapsedSeconds(nowMs) else 0L
            val config = if (snapshot.isRunning) current.workoutConfig else null
            val (segLabel, segCountdown, nextLabel) = deriveSegmentInfo(config, newElapsed)
            current.copy(
                snapshot = snapshot,
                elapsedSeconds = newElapsed,
                workoutConfig = config,
                segmentLabel = segLabel,
                segmentCountdownSeconds = segCountdown,
                nextSegmentLabel = nextLabel
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
            }.getOrNull()

            _uiState.update { current ->
                val newElapsed = computeElapsedSeconds(System.currentTimeMillis())
                val (segLabel, segCountdown, nextLabel) = deriveSegmentInfo(config, newElapsed)
                current.copy(
                    workoutConfig = config,
                    elapsedSeconds = newElapsed,
                    segmentLabel = segLabel,
                    segmentCountdownSeconds = segCountdown,
                    nextSegmentLabel = nextLabel
                )
            }
        } finally {
            loadingWorkoutMetadata = false
        }
    }

    private fun computeElapsedSeconds(nowMs: Long): Long {
        val startTimeMs = workoutStartTimeMs ?: return 0L
        val currentPauseMs = pauseStartedAtMs?.let { nowMs - it } ?: 0L
        return ((nowMs - startTimeMs - pausedAccumulatedMs - currentPauseMs).coerceAtLeast(0L) / 1_000L)
    }

    private fun deriveSegmentInfo(config: WorkoutConfig?, elapsed: Long): Triple<String?, Long?, String?> {
        if (config == null || !config.isTimeBased()) return Triple(null, null, null)
        var cumulative = 0L
        config.segments.forEachIndexed { index, seg ->
            val dur = seg.durationSeconds?.toLong() ?: return@forEachIndexed
            cumulative += dur
            if (elapsed < cumulative) {
                val countdown = cumulative - elapsed
                val nextLabel = config.segments.drop(index + 1).firstOrNull { it.label != null }?.label
                return Triple(seg.label, countdown, nextLabel)
            }
        }
        // Past end of all segments — show last segment with 0 countdown
        val last = config.segments.lastOrNull()
        return Triple(last?.label, 0L, null)
    }
}
