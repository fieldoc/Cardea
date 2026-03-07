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
    val adaptiveLagSec: Float = 0f,
    val projectionReady: Boolean = false,
    val completedWorkoutId: Long? = null,
    val isFreeRun: Boolean = false,
    val avgHr: Int = 0          // NEW — running average HR for the session
)

object WorkoutState {
    private val _snapshot = MutableStateFlow(WorkoutSnapshot())
    val snapshot: StateFlow<WorkoutSnapshot> = _snapshot.asStateFlow()

    /**
     * Holds the session ID of the bootcamp session the user is currently running.
     * Written by BootcampViewModel before navigating to the active workout screen,
     * cleared when the workout completes or is discarded.
     */
    @Volatile
    var pendingBootcampSessionId: Long? = null
        private set

    fun setPendingBootcampSessionId(id: Long?) {
        pendingBootcampSessionId = id
    }

    fun update(transform: (WorkoutSnapshot) -> WorkoutSnapshot) {
        _snapshot.update(transform)
    }

    fun set(snapshot: WorkoutSnapshot) {
        _snapshot.value = snapshot
    }

    fun reset() {
        _snapshot.update { current ->
            WorkoutSnapshot(completedWorkoutId = current.completedWorkoutId)
        }
    }

    fun clearCompletedWorkoutId() {
        _snapshot.update { it.copy(completedWorkoutId = null) }
    }
}
