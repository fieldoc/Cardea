# Auto-Pause Detection Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically pause the workout timer, distance, and zone alerts when the user stops moving, resuming when they start again.

**Architecture:** A pure-Kotlin `AutoPauseDetector` (Schmitt-trigger on `Location.speed`) lives in `domain/engine/`. The service integrates it into the tick loop, gating `alertPolicy` and `coachingEventRouter`, and accumulates paused duration for elapsed-time math. A new `isAutoPaused` / `autoPauseEnabled` pair in `WorkoutSnapshot` drives UI dimming and the live toggle pill.

**Tech Stack:** Kotlin, Jetpack Compose, SharedPreferences (same pattern as `AudioSettingsRepository`), `Location.speed` from FusedLocationProvider.

---

## Chunk 1: Detection, State, GPS, Settings

### Task 1: `AutoPauseDetector` + unit tests

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/engine/AutoPauseDetector.kt`
- Create: `app/src/test/java/com/hrcoach/domain/engine/AutoPauseDetectorTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/hrcoach/domain/engine/AutoPauseDetectorTest.kt`:

```kotlin
package com.hrcoach.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AutoPauseDetectorTest {

    private lateinit var detector: AutoPauseDetector

    @Before
    fun setUp() {
        // Default thresholds: stop=0.5 m/s, resume=1.0 m/s, confirmWindow=3000ms
        detector = AutoPauseDetector()
    }

    // --- null speed ---

    @Test
    fun `null speed returns NONE and does not change state`() {
        assertEquals(AutoPauseEvent.NONE, detector.update(null, 0L))
        // Still not paused after fast-forward
        assertEquals(AutoPauseEvent.NONE, detector.update(null, 10_000L))
    }

    // --- speed above stop threshold ---

    @Test
    fun `speed above stop threshold returns NONE`() {
        assertEquals(AutoPauseEvent.NONE, detector.update(2.0f, 0L))
    }

    // --- confirmation window not yet elapsed ---

    @Test
    fun `speed below threshold but within window returns NONE`() {
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 0L))
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 1_000L))
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 2_999L))
    }

    // --- confirmation window elapsed → PAUSED ---

    @Test
    fun `speed below threshold for full window fires PAUSED`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 1_000L)
        assertEquals(AutoPauseEvent.PAUSED, detector.update(0.0f, 3_000L))
    }

    // --- after PAUSED, continued stops return NONE ---

    @Test
    fun `after PAUSED further stopped ticks return NONE`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 3_000L)
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 4_000L))
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 5_000L))
    }

    // --- Schmitt trigger: resume requires higher threshold ---

    @Test
    fun `speed below resume threshold while paused does not resume`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 3_000L) // PAUSED
        // Speed is 0.8 f/s — above stop (0.5) but below resume (1.0): should NOT resume
        assertEquals(AutoPauseEvent.NONE, detector.update(0.8f, 4_000L))
    }

    @Test
    fun `speed at or above resume threshold while paused fires RESUMED`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 3_000L) // PAUSED
        assertEquals(AutoPauseEvent.RESUMED, detector.update(1.0f, 4_000L))
    }

    // --- after RESUMED, returns NONE while moving ---

    @Test
    fun `after RESUMED further moving ticks return NONE`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 3_000L) // PAUSED
        detector.update(1.0f, 4_000L) // RESUMED
        assertEquals(AutoPauseEvent.NONE, detector.update(2.0f, 5_000L))
    }

    // --- can re-pause after resuming ---

    @Test
    fun `can stop again after resuming and fire PAUSED a second time`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 3_000L) // PAUSED
        detector.update(1.0f, 4_000L) // RESUMED
        // Stop again
        detector.update(0.0f, 5_000L)
        assertEquals(AutoPauseEvent.PAUSED, detector.update(0.0f, 8_000L))
    }

    // --- speed spike resets window ---

    @Test
    fun `brief speed above stop threshold resets the confirmation window`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 1_500L) // 1.5s stopped
        detector.update(2.0f, 2_000L) // moved — resets window
        // Now stopped again but only 2.5s since reset, not 3s
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 4_499L))
        // At 3s after the reset (2000+3000=5000), should fire
        assertEquals(AutoPauseEvent.PAUSED, detector.update(0.0f, 5_000L))
    }

    // --- reset() ---

    @Test
    fun `reset clears paused state so stopped ticks need new confirmation window`() {
        detector.update(0.0f, 0L)
        detector.update(0.0f, 3_000L) // PAUSED
        detector.reset()
        // After reset, even immediate stop needs full window
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 3_001L))
        assertEquals(AutoPauseEvent.NONE, detector.update(0.0f, 5_999L))
        assertEquals(AutoPauseEvent.PAUSED, detector.update(0.0f, 6_001L))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd "C:/Users/glm_6/AndroidStudioProjects/HRapp"
./gradlew.bat testDebugUnitTest --tests "com.hrcoach.domain.engine.AutoPauseDetectorTest" 2>&1 | tail -20
```
Expected: FAILED — `AutoPauseEvent` and `AutoPauseDetector` not found.

- [ ] **Step 3: Create the implementation**

Create `app/src/main/java/com/hrcoach/domain/engine/AutoPauseDetector.kt`:

```kotlin
package com.hrcoach.domain.engine

enum class AutoPauseEvent { NONE, PAUSED, RESUMED }

/**
 * Detects when a workout runner stops moving and resumes, using a Schmitt trigger on GPS speed.
 *
 * A Schmitt trigger uses different thresholds for stopping vs. resuming, preventing bouncing
 * when GPS speed oscillates near a crosswalk or at a slow shuffle.
 *
 * The confirmation window (default 3 s) requires sustained stillness before triggering PAUSED,
 * absorbing brief GPS noise spikes to zero.
 */
class AutoPauseDetector(
    private val stopThresholdMs: Float = 0.5f,    // ~1.8 km/h — clearly stopped
    private val resumeThresholdMs: Float = 1.0f,  // ~3.6 km/h — clearly moving
    private val confirmWindowMs: Long = 3_000L    // must be below stop threshold this long
) {
    private var isAutoPaused: Boolean = false
    private var stoppedSinceMs: Long = 0L

    /**
     * Feed each GPS tick. Returns [AutoPauseEvent.PAUSED] or [AutoPauseEvent.RESUMED] on
     * a state transition, [AutoPauseEvent.NONE] otherwise.
     *
     * @param speedMs  Speed in m/s from [android.location.Location.speed], or null if unavailable.
     * @param nowMs    Current epoch time in milliseconds.
     */
    fun update(speedMs: Float?, nowMs: Long): AutoPauseEvent {
        if (speedMs == null) return AutoPauseEvent.NONE

        return if (isAutoPaused) {
            if (speedMs >= resumeThresholdMs) {
                isAutoPaused = false
                stoppedSinceMs = 0L
                AutoPauseEvent.RESUMED
            } else {
                AutoPauseEvent.NONE
            }
        } else {
            if (speedMs < stopThresholdMs) {
                if (stoppedSinceMs == 0L) stoppedSinceMs = nowMs
                if (nowMs - stoppedSinceMs >= confirmWindowMs) {
                    isAutoPaused = true
                    AutoPauseEvent.PAUSED
                } else {
                    AutoPauseEvent.NONE
                }
            } else {
                stoppedSinceMs = 0L
                AutoPauseEvent.NONE
            }
        }
    }

    fun reset() {
        isAutoPaused = false
        stoppedSinceMs = 0L
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew.bat testDebugUnitTest --tests "com.hrcoach.domain.engine.AutoPauseDetectorTest" 2>&1 | tail -20
```
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/engine/AutoPauseDetector.kt \
        app/src/test/java/com/hrcoach/domain/engine/AutoPauseDetectorTest.kt
git commit -m "feat(engine): add AutoPauseDetector with Schmitt-trigger speed detection"
```

---

### Task 2: Add `isAutoPaused` and `autoPauseEnabled` to `WorkoutSnapshot`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutState.kt`

- [ ] **Step 1: Add two fields to `WorkoutSnapshot`**

In `WorkoutState.kt`, add to the `WorkoutSnapshot` data class after `pendingBootcampSessionId`:

```kotlin
val isAutoPaused: Boolean = false,
val autoPauseEnabled: Boolean = true,   // whether feature is on for this session
```

The full updated data class (new fields at end):
```kotlin
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
    val avgHr: Int = 0,
    val pendingBootcampSessionId: Long? = null,
    val isAutoPaused: Boolean = false,
    val autoPauseEnabled: Boolean = true,
)
```

- [ ] **Step 2: Build to verify no compile errors**

```bash
./gradlew.bat assembleDebug 2>&1 | grep -E "error:|BUILD" | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutState.kt
git commit -m "feat(state): add isAutoPaused and autoPauseEnabled to WorkoutSnapshot"
```

---

### Task 3: Add `currentSpeed` and `setMoving` to `GpsDistanceTracker`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/GpsDistanceTracker.kt`

- [ ] **Step 1: Add speed StateFlow, `isMoving` flag, and `setMoving` method**

Replace the entire `GpsDistanceTracker.kt` file content. Note: `WorkoutTick` is NOT modified here — that lives in `WorkoutForegroundService.kt` and is updated in Task 5.

```kotlin
package com.hrcoach.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.HandlerThread
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Minimum acceptable horizontal accuracy (metres). Fixes worse than this are discarded. */
private const val MIN_ACCURACY_METERS = 20f

class GpsDistanceTracker(context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastLocation: Location? = null
    private var isRunning: Boolean = false

    /** When false, location fixes still update lastLocation and emit currentLocation,
     *  but do not accumulate distance. Prevents distance inflation during auto-pause. */
    @Volatile
    private var isMoving: Boolean = true

    private val _distanceMeters = MutableStateFlow(0f)
    val distanceMeters: StateFlow<Float> = _distanceMeters

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val _currentSpeed = MutableStateFlow<Float?>(null)
    val currentSpeed: StateFlow<Float?> = _currentSpeed

    /** Background thread that receives location callbacks off the main thread. */
    private var locationThread: HandlerThread? = null

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        2_000L
    ).setMinUpdateDistanceMeters(3f).build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            // Discard fixes with poor horizontal accuracy to prevent GPS drift accumulation.
            if (location.accuracy > MIN_ACCURACY_METERS) return
            _currentLocation.value = location
            _currentSpeed.value = if (location.hasSpeed()) location.speed else null
            val previous = lastLocation
            // Always update lastLocation so there is no distance spike on auto-pause resume.
            lastLocation = location
            if (isMoving && previous != null) {
                _distanceMeters.value += previous.distanceTo(location)
            }
        }
    }

    /** Call with false when auto-paused; true when moving again. */
    fun setMoving(moving: Boolean) {
        isMoving = moving
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        isRunning = true
        isMoving = true
        lastLocation = null
        _distanceMeters.value = 0f
        _currentLocation.value = null
        _currentSpeed.value = null

        // Run location callbacks on a dedicated background thread, not the main looper.
        val thread = HandlerThread("gps-distance-tracker").also {
            it.start()
            locationThread = it
        }

        val started = runCatching {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                thread.looper
            )
        }.isSuccess
        if (!started) {
            isRunning = false
            thread.quitSafely()
            locationThread = null
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        runCatching {
            fusedClient.removeLocationUpdates(locationCallback)
        }
        locationThread?.quitSafely()
        locationThread = null
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew.bat assembleDebug 2>&1 | grep -E "error:|BUILD" | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/GpsDistanceTracker.kt
git commit -m "feat(gps): add currentSpeed StateFlow and setMoving flag to GpsDistanceTracker"
```

---

### Task 4: `AutoPauseSettingsRepository`

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/repository/AutoPauseSettingsRepository.kt`

> **Note:** The spec document says "DataStore-backed" but this codebase uses SharedPreferences for all simple settings (see `AudioSettingsRepository`). SharedPreferences is used here for consistency. No `AppModule` entry is needed — `@Inject constructor` + `@ApplicationContext` is sufficient for Hilt.

- [ ] **Step 1: Create the repository**

Create `app/src/main/java/com/hrcoach/data/repository/AutoPauseSettingsRepository.kt`:

```kotlin
package com.hrcoach.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoPauseSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "hr_coach_auto_pause_settings"
        private const val PREF_AUTO_PAUSE_ENABLED = "auto_pause_enabled"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoPauseEnabled(): Boolean =
        prefs.getBoolean(PREF_AUTO_PAUSE_ENABLED, true)

    fun setAutoPauseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_PAUSE_ENABLED, enabled).apply()
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew.bat assembleDebug 2>&1 | grep -E "error:|BUILD" | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/repository/AutoPauseSettingsRepository.kt
git commit -m "feat(data): add AutoPauseSettingsRepository for persistent auto-pause preference"
```

---

## Chunk 2: Service Integration, ViewModel Timer, UI

### Task 5: `WorkoutForegroundService` — full integration

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

- [ ] **Step 1: Add imports and new fields**

At the top of the class body, after the existing private fields, add:

```kotlin
@Inject
lateinit var autoPauseSettingsRepository: AutoPauseSettingsRepository

private var autoPauseDetector: AutoPauseDetector? = null
private var sessionAutoPauseEnabled: Boolean = true
private var autoPauseStartMs: Long = 0L
private var totalAutoPausedMs: Long = 0L
```

Add import at top of file:
```kotlin
import com.hrcoach.data.repository.AutoPauseSettingsRepository
import com.hrcoach.domain.engine.AutoPauseDetector
import com.hrcoach.domain.engine.AutoPauseEvent
```

- [ ] **Step 2: Add `ACTION_TOGGLE_AUTO_PAUSE` constant**

In the `companion object`, add after `ACTION_RESCAN_BLE`:
```kotlin
const val ACTION_TOGGLE_AUTO_PAUSE = "com.hrcoach.ACTION_TOGGLE_AUTO_PAUSE"
```

- [ ] **Step 3: Handle the new intent in `onStartCommand`**

Add a new `when` branch after `ACTION_RESCAN_BLE`:
```kotlin
ACTION_TOGGLE_AUTO_PAUSE -> {
    sessionAutoPauseEnabled = !sessionAutoPauseEnabled
    if (!sessionAutoPauseEnabled) {
        // If currently auto-paused when toggled off, resume everything cleanly
        if (WorkoutState.snapshot.value.isAutoPaused) {
            totalAutoPausedMs += System.currentTimeMillis() - autoPauseStartMs
            autoPauseStartMs = 0L
            gpsTracker?.setMoving(true)
        }
        autoPauseDetector?.reset()
        WorkoutState.update { it.copy(isAutoPaused = false, autoPauseEnabled = false) }
    } else {
        WorkoutState.update { it.copy(autoPauseEnabled = true) }
    }
    return START_NOT_STICKY
}
```

- [ ] **Step 4: Initialize detector and settings in `startWorkout`**

In `startWorkout`, after `alertPolicy.reset()` line, add:
```kotlin
autoPauseDetector = AutoPauseDetector()
sessionAutoPauseEnabled = autoPauseSettingsRepository.isAutoPauseEnabled()
autoPauseStartMs = 0L
totalAutoPausedMs = 0L
```

In the `WorkoutState.set(WorkoutSnapshot(...))` block, add the new fields:
```kotlin
WorkoutState.set(
    WorkoutSnapshot(
        isRunning = true,
        isPaused = false,
        targetHr = workoutConfig.targetHrAtDistance(0f) ?: 0,
        guidanceText = "GET HR SIGNAL",
        adaptiveLagSec = adaptiveController?.currentLagSec() ?: 0f,
        autoPauseEnabled = sessionAutoPauseEnabled,   // NEW
    )
)
```

- [ ] **Step 5: Include `currentSpeed` in `observeWorkoutTicks` combine**

Replace the `combine(...)` call in `observeWorkoutTicks`:

```kotlin
combine(
    hrManager.heartRate,
    hrManager.isConnected,
    tracker.distanceMeters,
    tracker.currentLocation,
    tracker.currentSpeed
) { hr, connected, distance, location, speed ->
    WorkoutTick(
        hr = hr,
        connected = connected,
        distanceMeters = distance,
        location = location,
        speed = speed
    )
}
```

Update the `WorkoutTick` data class (at the bottom of the file):
```kotlin
private data class WorkoutTick(
    val hr: Int,
    val connected: Boolean,
    val distanceMeters: Float,
    val location: Location?,
    val speed: Float?
)
```

- [ ] **Step 6: Integrate auto-pause detection into `processTick`**

In `processTick`, replace the `elapsedSeconds` calculation line and add auto-pause detection immediately after it. Current:
```kotlin
val elapsedSeconds = if (workoutStartMs > 0L) (nowMs - workoutStartMs) / 1000L else 0L
```

Replace with:
```kotlin
// Run auto-pause detector before elapsed-time math so the decision is fresh
if (sessionAutoPauseEnabled) {
    val detector = autoPauseDetector
    if (detector != null) {
        when (detector.update(tick.speed, nowMs)) {
            AutoPauseEvent.PAUSED -> {
                autoPauseStartMs = nowMs
                gpsTracker?.setMoving(false)
                WorkoutState.update { it.copy(isAutoPaused = true) }
            }
            AutoPauseEvent.RESUMED -> {
                totalAutoPausedMs += nowMs - autoPauseStartMs
                autoPauseStartMs = 0L
                gpsTracker?.setMoving(true)
                WorkoutState.update { it.copy(isAutoPaused = false) }
            }
            AutoPauseEvent.NONE -> Unit
        }
    }
}

val isAutoPaused = WorkoutState.snapshot.value.isAutoPaused
val currentAutoPauseMs = if (isAutoPaused && autoPauseStartMs > 0L) nowMs - autoPauseStartMs else 0L
val elapsedSeconds = if (workoutStartMs > 0L) {
    (nowMs - workoutStartMs - totalAutoPausedMs - currentAutoPauseMs) / 1000L
} else 0L
```

- [ ] **Step 7: Gate alerts and update guidance text for auto-pause**

Find the section in `processTick` where `guidance` is assembled and `WorkoutState.update` is called. Replace:

```kotlin
val guidance = adaptiveResult?.guidance ?: when (zoneStatus) {
    ZoneStatus.ABOVE_ZONE -> "SLOW DOWN NOW"
    ZoneStatus.BELOW_ZONE -> "SPEED UP NOW"
    ZoneStatus.IN_ZONE -> "HOLD THIS PACE"
    ZoneStatus.NO_DATA -> "GET HR SIGNAL"
}
```

With:

```kotlin
val guidance = when {
    isAutoPaused -> "STOPPED \u2022 ALERTS PAUSED"
    adaptiveResult?.guidance != null -> adaptiveResult.guidance
    else -> when (zoneStatus) {
        ZoneStatus.ABOVE_ZONE -> "SLOW DOWN NOW"
        ZoneStatus.BELOW_ZONE -> "SPEED UP NOW"
        ZoneStatus.IN_ZONE -> "HOLD THIS PACE"
        ZoneStatus.NO_DATA -> "GET HR SIGNAL"
    }
}
```

Then gate `coachingEventRouter.route()` and `alertPolicy.handle()`. Wrap both calls:

```kotlin
if (!isAutoPaused) {
    coachingEventRouter.route(
        workoutConfig = workoutConfig,
        connected = tick.connected,
        distanceMeters = tick.distanceMeters,
        elapsedSeconds = elapsedSeconds,
        zoneStatus = zoneStatus,
        adaptiveResult = adaptiveResult,
        guidance = guidance,
        nowMs = nowMs,
        emitEvent = { event, eventGuidance ->
            coachingAudioManager?.fireEvent(event, eventGuidance)
        }
    )
    alertPolicy.handle(
        status = zoneStatus,
        nowMs = nowMs,
        alertDelaySec = workoutConfig.alertDelaySec,
        alertCooldownSec = workoutConfig.alertCooldownSec,
        guidanceText = guidance,
        onResetEscalation = { coachingAudioManager?.resetEscalation() },
        onAlert = { event, eventGuidance ->
            coachingAudioManager?.fireEvent(event, eventGuidance)
        }
    )
}
```

- [ ] **Step 8: Clean up on stop**

In `cleanupManagers()`, add after `trackPointRecorder.reset()`:
```kotlin
autoPauseDetector?.reset()
autoPauseDetector = null
autoPauseStartMs = 0L
totalAutoPausedMs = 0L
```

Also in `startWorkout`, reset these before setting up:
```kotlin
hrSampleBuffer.clear()
hrSessionSamples.clear()
cadenceLockSuspected = false
autoPauseStartMs = 0L        // NEW
totalAutoPausedMs = 0L       // NEW
```

- [ ] **Step 9: Build to verify**

```bash
./gradlew.bat assembleDebug 2>&1 | grep -E "error:|BUILD" | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(service): integrate AutoPauseDetector into workout tick loop"
```

---

### Task 6: `WorkoutViewModel` — auto-pause timer tracking

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/workout/WorkoutViewModel.kt`

- [ ] **Step 1: Add auto-pause accumulator fields**

After `private var pauseStartedAtMs: Long? = null`, add:
```kotlin
private var autoPausedAccumulatedMs: Long = 0L
private var autoPauseStartedAtMs: Long? = null
```

- [ ] **Step 2: Track auto-pause transitions in `handleSnapshot`**

In the `when` block inside `handleSnapshot`, extend the `!snapshot.isRunning` branch:
```kotlin
!snapshot.isRunning -> {
    workoutStartTimeMs = null
    pausedAccumulatedMs = 0L
    pauseStartedAtMs = null
    autoPausedAccumulatedMs = 0L   // NEW
    autoPauseStartedAtMs = null    // NEW
}
```

After the existing `isPaused` tracking block (the `if (snapshot.isPaused) { ... } else if ...` block), add:
```kotlin
// Auto-pause timer tracking — mirrors the manual pause tracking above
if (snapshot.isAutoPaused) {
    if (autoPauseStartedAtMs == null) autoPauseStartedAtMs = nowMs
} else if (autoPauseStartedAtMs != null) {
    autoPausedAccumulatedMs += nowMs - (autoPauseStartedAtMs ?: nowMs)
    autoPauseStartedAtMs = null
}
```

- [ ] **Step 3: Subtract auto-pause in `computeElapsedSeconds`**

Replace the current implementation:
```kotlin
private fun computeElapsedSeconds(nowMs: Long): Long {
    val startTimeMs = workoutStartTimeMs ?: return 0L
    val currentPauseMs = pauseStartedAtMs?.let { nowMs - it } ?: 0L
    val currentAutoPauseMs = autoPauseStartedAtMs?.let { nowMs - it } ?: 0L
    return ((nowMs - startTimeMs - pausedAccumulatedMs - currentPauseMs
            - autoPausedAccumulatedMs - currentAutoPauseMs).coerceAtLeast(0L) / 1_000L)
}
```

- [ ] **Step 4: Build to verify**

```bash
./gradlew.bat assembleDebug 2>&1 | grep -E "error:|BUILD" | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/WorkoutViewModel.kt
git commit -m "feat(viewmodel): subtract auto-pause duration from elapsed workout timer"
```

---

### Task 7: Account screen — persistent toggle

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`

- [ ] **Step 1: Add `autoPauseEnabled` to `AccountUiState`**

In `AccountViewModel.kt`, add to `AccountUiState`:
```kotlin
data class AccountUiState(
    val totalWorkouts: Int = 0,
    val mapsApiKey: String = "",
    val mapsApiKeySaved: Boolean = false,
    val earconVolume: Int = 80,
    val voiceVerbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL,
    val enableVibration: Boolean = true,
    val maxHr: Int? = null,
    val maxHrInput: String = "",
    val maxHrSaved: Boolean = false,
    val maxHrError: String? = null,
    val autoPauseEnabled: Boolean = true,   // NEW
)
```

- [ ] **Step 2: Inject `AutoPauseSettingsRepository` into `AccountViewModel`**

Add to the `@HiltViewModel` constructor:
```kotlin
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val audioRepo: AudioSettingsRepository,
    private val mapsRepo: MapsSettingsRepository,
    private val workoutRepo: WorkoutRepository,
    private val userProfileRepo: UserProfileRepository,
    private val autoPauseRepo: AutoPauseSettingsRepository,   // NEW
) : ViewModel() {
```

Add a backing flow:
```kotlin
private val _autoPauseEnabled = MutableStateFlow(true)
```

In `init { viewModelScope.launch { ... } }`, load the setting alongside the others:
```kotlin
_autoPauseEnabled.value = autoPauseRepo.isAutoPauseEnabled()
```

Add a public setter after `saveAudioSettings()`:
```kotlin
fun setAutoPauseEnabled(enabled: Boolean) {
    _autoPauseEnabled.value = enabled
    autoPauseRepo.setAutoPauseEnabled(enabled)
}
```

- [ ] **Step 3: Chain `_autoPauseEnabled` into `uiState`**

The `uiState` chain currently ends with `.stateIn(...)` after the second chained `.combine()`. Add a third chain link before `.stateIn(...)`.

Find this exact block (end of the existing chain):
```kotlin
    ) { base, parts ->
        base.copy(
            maxHr      = parts[0] as Int?,
            maxHrInput = parts[1] as String,
            maxHrSaved = parts[2] as Boolean,
            maxHrError = parts[3] as String?
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())
```

Replace with:
```kotlin
    ) { base, parts ->
        base.copy(
            maxHr      = parts[0] as Int?,
            maxHrInput = parts[1] as String,
            maxHrSaved = parts[2] as Boolean,
            maxHrError = parts[3] as String?
        )
    }.combine(_autoPauseEnabled) { base, autoPause ->
        base.copy(autoPauseEnabled = autoPause)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())
```

- [ ] **Step 4: Add the toggle row to `AccountScreen`**

In `AccountScreen.kt`, find the audio settings `GlassCard` section (the one with earcon volume slider and voice verbosity). Add a new `GlassCard` section AFTER the audio settings card and BEFORE the HR calibration section:

```kotlin
// Auto-pause settings
GlassCard(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Workout",
            style = MaterialTheme.typography.labelMedium,
            color = CardeaTextSecondary
        )
        HorizontalDivider(color = GlassBorder)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-pause when stopped",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CardeaTextPrimary
                )
                Text(
                    text = "Silences alerts and pauses the timer at red lights or breaks",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTextSecondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            CardeaSwitch(
                checked = uiState.autoPauseEnabled,
                onCheckedChange = { viewModel.setAutoPauseEnabled(it) }
            )
        }
    }
}
```

You will need to add `import com.hrcoach.ui.theme.CardeaTextPrimary` if not already present.

- [ ] **Step 5: Build to verify**

```bash
./gradlew.bat assembleDebug 2>&1 | grep -E "error:|BUILD" | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt \
        app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt
git commit -m "feat(account): add auto-pause toggle to account settings screen"
```

---

### Task 8: Active workout screen — live toggle pill + HrRing dim

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`

- [ ] **Step 1: Add `onToggleAutoPause` parameter to `ActiveWorkoutScreen`**

In the function signature, add after `onConnectHr`:
```kotlin
onToggleAutoPause: () -> Unit,
```

- [ ] **Step 2: Dim `HrRing` when auto-paused**

Find the `HrRing(...)` call (around line 159) and add a `modifier` parameter:

```kotlin
HrRing(
    hr = state.currentHr,
    isConnected = state.hrConnected,
    zoneColor = zoneColor,
    pulseScale = pulseScale,
    onConnectHr = onConnectHr,
    modifier = Modifier.graphicsLayer { alpha = if (state.isAutoPaused) 0.4f else 1f }
)
```

- [ ] **Step 3: Fix `GuidanceCard` `isActive` to handle auto-pause**

Find the `GuidanceCard` call:
```kotlin
GuidanceCard(
    guidance = if (state.isPaused) "Workout Paused" else state.guidanceText,
    zoneColor = zoneColor,
    isActive = state.guidanceText.isNotBlank() && !state.isPaused
)
```

Replace with:
```kotlin
GuidanceCard(
    guidance = if (state.isPaused) "Workout Paused" else state.guidanceText,
    zoneColor = zoneColor,
    isActive = state.guidanceText.isNotBlank() && !state.isPaused && !state.isAutoPaused
)
```

- [ ] **Step 4: Add the auto-pause toggle pill**

Find the `GlassCard` stat row (Distance / Pace / Avg HR). Directly after its closing `}`, before the `Spacer(modifier = Modifier.weight(1f))`, add:

```kotlin
// Auto-pause live toggle pill — only shown when feature is enabled for this session
if (state.autoPauseEnabled || state.isAutoPaused) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (state.autoPauseEnabled) GlassHighlight else Color.Transparent
                )
                .border(
                    1.dp,
                    if (state.autoPauseEnabled) GlassBorder else GlassBorder.copy(alpha = 0.4f),
                    RoundedCornerShape(50)
                )
                .clickable(onClick = onToggleAutoPause)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.autoPauseEnabled) "AUTO-PAUSE ON" else "AUTO-PAUSE OFF",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.autoPauseEnabled) CardeaTextPrimary else CardeaTextTertiary
            )
        }
    }
}
```

Imports needed (add if missing):
```kotlin
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.GlassHighlight
```

- [ ] **Step 5: Wire `onToggleAutoPause` in `NavGraph.kt`**

Find the `ActiveWorkoutScreen(...)` call in `NavGraph.kt` and add:

```kotlin
onToggleAutoPause = {
    val intent = Intent(context, WorkoutForegroundService::class.java).apply {
        action = WorkoutForegroundService.ACTION_TOGGLE_AUTO_PAUSE
    }
    context.startService(intent)
},
```

- [ ] **Step 6: Build to verify**

```bash
./gradlew.bat assembleDebug 2>&1 | grep -E "error:|BUILD" | tail -20
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Run all unit tests**

```bash
./gradlew.bat test 2>&1 | grep -E "FAILED|PASSED|BUILD" | tail -20
```
Expected: All tests PASS, BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt \
        app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(ui): add auto-pause live toggle pill and HrRing dim to active workout screen"
```
