package com.hrcoach.service

import com.hrcoach.domain.model.ZoneStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    // Transient banner shown over the active workout screen for ~3.5 s whenever
    // a coaching event fires. Auto-cleared by flashCueBanner's own delay coroutine.
    val lastCueBanner: com.hrcoach.service.audio.CueBanner? = null,
)

object WorkoutState {
    private val _snapshot = MutableStateFlow(WorkoutSnapshot())
    val snapshot: StateFlow<WorkoutSnapshot> = _snapshot.asStateFlow()

    /*
     * Supervisor scope so a cancellation of one banner-clear coroutine doesn't
     * take down sibling workout-level coroutines. Default is appropriate — no
     * blocking IO, just delay + StateFlow.update.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var bannerClearJob: Job? = null

    private const val BANNER_VISIBLE_MS = 3500L

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
        bannerClearJob?.cancel()
        bannerClearJob = null
    }

    fun clearCompletedWorkoutId() {
        _snapshot.update { it.copy(completedWorkoutId = null) }
    }

    fun clearHrMaxUpdatedDelta() {
        _snapshot.update { it.copy(hrMaxUpdatedDelta = null) }
    }

    /**
     * Sets [WorkoutSnapshot.lastCueBanner] and schedules a clear after [BANNER_VISIBLE_MS].
     * Cancels any previous pending clear so the new banner always gets its full visibility
     * window regardless of what fired before it.
     *
     * **Caller contract:** must be invoked from a single thread (typically the WFS tick
     * pipeline). `bannerClearJob` mutation is not synchronized — concurrent callers can
     * race and orphan a pending clear. The `firedAtMs` equality guard inside the clear
     * coroutine catches most observable symptoms but isn't bulletproof at millisecond
     * resolution. Broaden callers only with a `synchronized(...)` fix applied here.
     */
    fun flashCueBanner(banner: com.hrcoach.service.audio.CueBanner) {
        _snapshot.update { it.copy(lastCueBanner = banner) }
        bannerClearJob?.cancel()
        bannerClearJob = scope.launch {
            delay(BANNER_VISIBLE_MS)
            // Only clear if this is still the banner we set (another may have replaced it).
            _snapshot.update { current ->
                if (current.lastCueBanner?.firedAtMs == banner.firedAtMs) current.copy(lastCueBanner = null)
                else current
            }
        }
    }
}
