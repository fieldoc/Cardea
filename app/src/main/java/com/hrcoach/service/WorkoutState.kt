package com.hrcoach.service

import com.hrcoach.domain.model.ZoneStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WorkoutSnapshot(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentHr: Int = 0,
    val targetHr: Int = 0,
    val zoneStatus: ZoneStatus = ZoneStatus.NO_DATA,
    val distanceMeters: Float = 0f,
    val hrConnected: Boolean = false,
    val paceMinPerKm: Float = 0f,
    val predictedHr: Int = 0,
    val guidanceText: String = "GET HR SIGNAL",
    val projectionReady: Boolean = false,
    val completedWorkoutId: Long? = null,
    val isFreeRun: Boolean = false,
    val avgHr: Int = 0,
    val pendingBootcampSessionId: Long? = null,
    val isAutoPaused: Boolean = false,
    val autoPauseEnabled: Boolean = true,
    val countdownSecondsRemaining: Int? = null,
    val elapsedSeconds: Long = 0L,         // service-computed, sim-clock-aware
    // Populated at workout end if HrCalibrator detected a new max during this session.
    // Pair(previousMax, newMax). Preserved through reset() so PostRunSummaryViewModel
    // can display the calibration banner; explicitly cleared via clearHrMaxUpdatedDelta()
    // once the ViewModel has consumed it.
    val hrMaxUpdatedDelta: Pair<Int, Int>? = null,
)

object WorkoutState {
    private val _snapshot = MutableStateFlow(WorkoutSnapshot())
    val snapshot: StateFlow<WorkoutSnapshot> = _snapshot.asStateFlow()

    /**
     * Sets the session ID of the bootcamp session the user is about to run.
     * Written by BootcampViewModel before navigating to the active workout screen,
     * cleared when the workout completes or is discarded.
     * Stored inside [WorkoutSnapshot] so all observers receive the change reactively.
     */
    fun setPendingBootcampSessionId(id: Long?) {
        _snapshot.update { it.copy(pendingBootcampSessionId = id) }
    }

    fun update(transform: (WorkoutSnapshot) -> WorkoutSnapshot) {
        _snapshot.update(transform)
    }

    fun set(snapshot: WorkoutSnapshot) {
        _snapshot.value = snapshot
    }

    fun reset() {
        _snapshot.update { current ->
            WorkoutSnapshot(
                completedWorkoutId = current.completedWorkoutId,
                pendingBootcampSessionId = current.pendingBootcampSessionId,
                hrMaxUpdatedDelta = current.hrMaxUpdatedDelta
            )
        }
    }

    fun clearCompletedWorkoutId() {
        _snapshot.update { it.copy(completedWorkoutId = null) }
    }

    fun clearHrMaxUpdatedDelta() {
        _snapshot.update { it.copy(hrMaxUpdatedDelta = null) }
    }
}
