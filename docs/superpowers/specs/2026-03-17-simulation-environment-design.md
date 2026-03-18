# Simulation Environment Design

**Date:** 2026-03-17
**Status:** Approved

## Problem

Testing the full workout coaching pipeline requires physically running with a BLE HR monitor. Bugs in zone evaluation, adaptive predictions, alert timing, auto-pause, metrics derivation, and cross-session adaptation take many real runs to surface. A simulation environment that provides fake HR and GPS data through the real pipeline would dramatically reduce debug cycle time.

## Requirements

1. **In-app simulation mode** — tap "Start Simulated Run" and watch the full UI respond with fake data. The entire coaching pipeline runs live: zones evaluate, alerts fire, track points save, metrics derive.
2. **Time control** — accelerate within-run time (compress a 30-min run into 1-2 minutes) and support batch mode (rapid-fire N runs for adaptation testing).
3. **Scenario flexibility** — ship a few preset HR/GPS profiles (easy run, intervals, signal loss) and support custom profiles on demand.
4. **Normal UI flow** — simulation uses the same Setup -> Workout -> Post-run screens. A small overlay during simulation shows injected values and speed controls.
5. **Batch mode** — a debug screen button that runs N simulated workouts without UI, cranking through `finishSession()` + metrics for each, to test adaptive profile evolution.

## Architecture: Interface Extraction + Hilt Swap

### Core Interfaces

Two interfaces formalize the contract that `WorkoutForegroundService` already consumes:

```kotlin
// domain/simulation/HrDataSource.kt
interface HrDataSource {
    val heartRate: StateFlow<Int>
    val isConnected: StateFlow<Boolean>
}

// domain/simulation/LocationDataSource.kt
interface LocationDataSource {
    val distanceMeters: StateFlow<Float>
    val currentLocation: StateFlow<Location?>
    val currentSpeed: StateFlow<Float?>
    fun start()
    fun stop()
    fun setMoving(moving: Boolean)
}
```

### Production Implementations

`BleHrManager` implements `HrDataSource` (already matches the signature).
`GpsDistanceTracker` implements `LocationDataSource` (already matches the signature).

No behavioral changes to production code — just interface declarations.

### Simulation Implementations

```kotlin
// service/simulation/SimulatedHrSource.kt
class SimulatedHrSource(
    private val clock: SimulationClock,
    private val scenario: SimulationScenario
) : HrDataSource {
    // Emits HR values from scenario profile, paced by SimulationClock
    // Checks SimEvent.SignalLoss events to toggle isConnected off/on
}

// service/simulation/SimulatedLocationSource.kt
class SimulatedLocationSource(
    private val clock: SimulationClock,
    private val scenario: SimulationScenario
) : LocationDataSource {
    // Generates smooth GPS positions along a straight-line route
    // Pace from scenario profile -> speed -> distance accumulation
    // setMoving(false) pauses distance accumulation (same as real GPS tracker)
    // SimEvent.GpsDropout -> null location, null speed
    // SimEvent.Stop -> speed drops to 0 to trigger auto-pause
}
```

**`setMoving()` semantics in simulation:** When `setMoving(false)` is called (by the service during auto-pause), `SimulatedLocationSource` stops accumulating `distanceMeters` but continues updating `currentLocation` and `currentSpeed`. This mirrors `GpsDistanceTracker`'s exact behavior.

### Clock Interface

```kotlin
// domain/simulation/WorkoutClock.kt
interface WorkoutClock {
    fun now(): Long
}

// domain/simulation/RealClock.kt (or in same file)
class RealClock : WorkoutClock {
    override fun now(): Long = System.currentTimeMillis()
}
```

### SimulationClock

Single definition — implements `WorkoutClock` and supports both wall-clock-scaled mode (for live UI simulation) and discrete-step mode (for batch):

```kotlin
// service/simulation/SimulationClock.kt
class SimulationClock(
    val speedMultiplier: MutableStateFlow<Float> = MutableStateFlow(1f)
) : WorkoutClock {
    private var anchorRealMs: Long = System.currentTimeMillis()
    private var anchorSimMs: Long = anchorRealMs

    /** Wall-clock-scaled mode: returns simulated time based on real elapsed time * speed. */
    override fun now(): Long {
        val realElapsed = System.currentTimeMillis() - anchorRealMs
        return anchorSimMs + (realElapsed * speedMultiplier.value).toLong()
    }

    fun setSpeed(multiplier: Float) {
        // Re-anchor before changing speed to prevent time jumps
        val currentSim = now()
        anchorRealMs = System.currentTimeMillis()
        anchorSimMs = currentSim
        speedMultiplier.value = multiplier
    }

    /** Discrete-step mode for batch: advance simulated time by a fixed delta. */
    fun advanceBy(deltaMs: Long) {
        anchorSimMs += deltaMs
        anchorRealMs = System.currentTimeMillis() // reset anchor to prevent drift
    }

    fun reset() {
        anchorRealMs = System.currentTimeMillis()
        anchorSimMs = anchorRealMs
    }
}
```

### SimulationScenario (Data Model)

```kotlin
// domain/simulation/SimulationScenario.kt
data class SimulationScenario(
    val name: String,
    val durationSeconds: Int,          // Total simulated duration
    val hrProfile: List<HrDataPoint>,  // Timestamp -> HR value (linearly interpolated)
    val paceProfile: List<PaceDataPoint>,  // Timestamp -> pace (min/km)
    val events: List<SimEvent> = emptyList()  // Signal loss, GPS dropout, etc.
)

data class HrDataPoint(
    val timeSeconds: Int,   // Seconds from start
    val hr: Int             // BPM
)

data class PaceDataPoint(
    val timeSeconds: Int,
    val paceMinPerKm: Float
)

sealed class SimEvent {
    data class SignalLoss(val atSeconds: Int, val durationSeconds: Int) : SimEvent()
    data class GpsDropout(val atSeconds: Int, val durationSeconds: Int) : SimEvent()
    data class Stop(val atSeconds: Int, val durationSeconds: Int) : SimEvent()  // speed -> 0 to trigger auto-pause
}
```

HR and pace values are linearly interpolated between data points, with optional Gaussian noise (stddev ~2 BPM for HR, ~0.1 min/km for pace) for realism.

### Preset Scenarios

1. **Easy Steady Run** — 20 min, HR ramps 70->140 over 3 min, holds ~140+/-5, pace 6:00 min/km steady
2. **Zone Drift** — 20 min, HR repeatedly drifts above zone (+15 BPM) then recovers. Tests alert timing and escalation.
3. **Interval Session** — 25 min, alternating 3-min high (170 BPM) / 2-min recovery (120 BPM). Tests segment transitions.
4. **Signal Loss** — 15 min steady run with two BLE disconnects (30s and 60s). Tests reconnection coaching events.
5. **GPS Dropout** — 15 min with a 45s period of null location and zero speed. Tests auto-pause behavior.

Preset scenarios are defined as companion object constants in `SimulationScenario`.

### Data Source Factory & Hilt Wiring

```kotlin
// service/simulation/DataSourceFactory.kt
interface DataSourceFactory {
    fun createHrSource(): HrDataSource
    fun createLocationSource(): LocationDataSource
    fun getClock(): WorkoutClock
}

class RealDataSourceFactory @Inject constructor(
    private val bleCoordinator: BleConnectionCoordinator,
    @ApplicationContext private val context: Context
) : DataSourceFactory {
    override fun createHrSource(): HrDataSource = bleCoordinator.managerForWorkout()
    override fun createLocationSource(): LocationDataSource = GpsDistanceTracker(context)
    override fun getClock(): WorkoutClock = RealClock()
}

class SimulatedDataSourceFactory(
    private val scenario: SimulationScenario,
    private val clock: SimulationClock
) : DataSourceFactory {
    override fun createHrSource(): HrDataSource = SimulatedHrSource(clock, scenario)
    override fun createLocationSource(): LocationDataSource = SimulatedLocationSource(clock, scenario)
    override fun getClock(): WorkoutClock = clock
}
```

**Hilt injection pattern:** The service gets `RealDataSourceFactory` injected by Hilt via `@Inject lateinit var realDataSourceFactory: RealDataSourceFactory`. In `startWorkout()`, a local `val factory: DataSourceFactory` is resolved:

```kotlin
val factory: DataSourceFactory = if (SimulationController.isActive) {
    val simState = SimulationController.state.value
    val clock = SimulationClock(MutableStateFlow(simState.speedMultiplier))
    SimulationController.attachClock(clock)
    SimulatedDataSourceFactory(simState.scenario!!, clock)
} else {
    realDataSourceFactory
}
```

`SimulatedDataSourceFactory` is NOT Hilt-provided — it is constructed at runtime with the current scenario and clock. Only `RealDataSourceFactory` is registered in `AppModule`.

`BleConnectionCoordinator.managerForWorkout()` keeps its return type as `BleHrManager`. Since `BleHrManager : HrDataSource`, the factory method `createHrSource(): HrDataSource` can accept it through the interface. Other callers of `BleConnectionCoordinator` that use the richer `BleHrManager` API (scan, connect, disconnect) are unaffected.

### Service Integration — Complete `System.currentTimeMillis()` Audit

Every `System.currentTimeMillis()` call in `WorkoutForegroundService` must be replaced with `clock.now()`. Here is the **exhaustive list**:

| Location | Code | Replacement |
|----------|------|-------------|
| `startWorkout()` line 222 | `WorkoutEntity(startTime = System.currentTimeMillis())` | `clock.now()` |
| `startWorkout()` line 226 | `workoutStartMs = System.currentTimeMillis()` | `clock.now()` |
| `processTick()` line 287 | `val nowMs = System.currentTimeMillis()` | `clock.now()` |
| `pauseWorkout()` line 450 | `pauseStartMs = System.currentTimeMillis()` | `clock.now()` |
| `resumeWorkout()` line 456 | `val nowMs = System.currentTimeMillis()` | `clock.now()` |
| `ACTION_TOGGLE_AUTO_PAUSE` line 174 | `totalAutoPausedMs += System.currentTimeMillis() - ...` | `clock.now()` |
| `stopWorkout()` line 493 | `timestampMs = System.currentTimeMillis()` (final track point) | `clock.now()` |
| `stopWorkout()` line 504 | `val now = System.currentTimeMillis()` (end time + metrics) | `clock.now()` |
| `stopWorkout()` line 576 | `val durationMin = (now - workoutStartMs) / 60_000f` | Already uses `now` local — just ensure `now` came from `clock.now()` |
| `onDestroy()` line 699 | `workout.copy(endTime = System.currentTimeMillis())` | `clock.now()` |

The `clock` field is set once in `startWorkout()` and stored as a `private var clock: WorkoutClock = RealClock()` instance field (same pattern as `gpsTracker`).

**Downstream clock consumers that are already correct:** `AutoPauseDetector.update(speed, nowMs)`, `CoachingEventRouter.route(..., nowMs)`, `AlertPolicy.handle(..., nowMs)`, and `TrackPointRecorder.saveIfNeeded(..., timestampMs)` all receive `nowMs` as a parameter from `processTick()`. As long as `processTick()` passes `clock.now()` as `nowMs`, all downstream components automatically use simulated time with no changes.

### PermissionGate Bypass for Simulation

`WorkoutForegroundService.onStartCommand()` calls `PermissionGate.hasAllRuntimePermissions(this)` before `startWorkout()`. In simulation mode, BLE and GPS permissions are not needed because no real hardware is accessed.

**Solution:** When `SimulationController.isActive`, skip the permission gate:

```kotlin
if (!SimulationController.isActive && !PermissionGate.hasAllRuntimePermissions(this)) {
    handleStartFailure("Missing required permissions.")
    return START_NOT_STICKY
}
```

This is the only permission change. The Setup screen already skips BLE scanning in sim mode, and `SimulatedLocationSource` does not use `FusedLocationProviderClient`.

### SimulationController (Singleton) — Thread Safety

```kotlin
// service/simulation/SimulationController.kt
object SimulationController {
    private val _state = MutableStateFlow(SimulationState())
    val state: StateFlow<SimulationState> = _state.asStateFlow()

    val isActive: Boolean get() = _state.value.isActive

    private var clock: SimulationClock? = null

    fun activate(scenario: SimulationScenario, speed: Float = 1f) {
        _state.value = SimulationState(
            isActive = true,
            scenario = scenario,
            speedMultiplier = speed
        )
    }

    fun deactivate() {
        clock = null
        _state.value = SimulationState()
    }

    /** Called by the service at workout start to attach the active clock for speed changes. */
    fun attachClock(simClock: SimulationClock) {
        clock = simClock
    }

    fun setSpeed(speed: Float) {
        clock?.setSpeed(speed)
        _state.update { it.copy(speedMultiplier = speed) }
    }
}

data class SimulationState(
    val isActive: Boolean = false,
    val scenario: SimulationScenario? = null,
    val speedMultiplier: Float = 1f
)
```

**Thread safety:** The service reads `SimulationController.isActive` and `state.value.scenario` atomically from a single `_state.value` read in `startWorkout()`. The `val simState = SimulationController.state.value` captures the snapshot, and `simState.scenario` is read from that captured snapshot — no TOCTOU race. `_state` is `MutableStateFlow` which provides atomic value reads.

### Batch Mode

```kotlin
// service/simulation/BatchSimulator.kt
class BatchSimulator @Inject constructor(
    private val adaptiveProfileRepository: AdaptiveProfileRepository,
    private val workoutRepository: WorkoutRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository
) {
    suspend fun runBatch(
        scenarios: List<SimulationScenario>,
        config: WorkoutConfig,
        onProgress: (completed: Int, total: Int) -> Unit
    ): BatchResult {
        // For each scenario:
        // 1. Create a discrete-step SimulationClock
        // 2. Create SimulatedHrSource + SimulatedLocationSource
        // 3. Create ZoneEngine + AdaptivePaceController (from current DB profile)
        // 4. Loop through ticks at TICK_INTERVAL_MS (2000ms simulated):
        //    clock.advanceBy(TICK_INTERVAL_MS)
        //    Read HR/location from simulated sources at clock.now()
        //    Call zone engine + adaptive controller
        //    Save track point
        // 5. Call AdaptivePaceController.finishSession(workoutId, endedAtMs = clock.now())
        // 6. Run metrics derivation (same logic as stopWorkout)
        //    - SKIP userProfileRepository.setMaxHr() — batch does not corrupt real user profile
        // 7. Save workout + metrics to DB
        // Report: final adaptive profile, per-run summaries
    }
}
```

**Discrete tick generation for batch mode:** The batch simulator does NOT rely on wall-clock time. Instead, it uses `SimulationClock.advanceBy(deltaMs)` to step simulated time forward by a fixed tick interval (2000ms) on each iteration. This ensures `AdaptivePaceController.evaluateTick()` receives properly spaced `nowMs` values regardless of how fast the CPU loop runs.

**UserProfileRepository excluded:** Batch mode does NOT inject or call `UserProfileRepository`. The `HrCalibrator.detectNewHrMax()` and `userProfileRepository.setMaxHr()` calls from the real `stopWorkout()` flow are skipped in batch mode to prevent simulated data from corrupting the user's real hrMax/hrRest settings. Batch mode only writes to `AdaptiveProfileRepository` (adaptation data), `WorkoutRepository` (workout records), and `WorkoutMetricsRepository` (derived metrics).

### UI Changes

#### Setup Screen
- When `SimulationController.isActive`, the "Start Workout" button label changes to "Start Simulated Workout" with a sim icon
- No BLE scanning required — skip the device connection step
- A "SIM" chip/badge appears near the top
- No device address is passed to the service intent

#### Simulation Overlay (during workout)
- Small semi-transparent glass panel at the top of the workout screen (only visible in sim mode)
- Shows: scenario name, sim speed (1x/5x/10x/50x), injected HR, injected pace
- Speed control: tap to cycle through multipliers (calls `SimulationController.setSpeed()`)
- "Inject Event" button: quick-fire signal loss, GPS dropout, or HR spike

#### Debug Screen (in Account/Settings)
- Accessible via a "Simulation" menu item (gated behind `BuildConfig.DEBUG`)
- Scenario picker (preset list)
- Speed slider (1x to 50x)
- "Start Simulation" toggle (activates SimulationController, then navigates to Setup)
- Batch mode section:
  - Pick scenario, set repeat count (N)
  - "Run Batch" button
  - Progress bar
  - Results: final CTL/ATL, adaptive trim values, per-run TRIMP scores

### Build Configuration

Simulation code is always compiled but the debug screen entry point is gated behind `BuildConfig.DEBUG`. The overlay and "Simulated Workout" label only appear when `SimulationController.isActive`. No simulation code runs in production unless explicitly activated — zero performance cost.

### File Layout

```
domain/simulation/
    HrDataSource.kt            -- interface
    LocationDataSource.kt      -- interface
    WorkoutClock.kt            -- interface + RealClock
    SimulationScenario.kt      -- data model + preset companion constants

service/simulation/
    SimulatedHrSource.kt       -- fake HR emitter (implements HrDataSource)
    SimulatedLocationSource.kt -- fake GPS emitter (implements LocationDataSource)
    SimulationClock.kt         -- acceleratable clock (implements WorkoutClock)
    SimulationController.kt    -- singleton state holder
    DataSourceFactory.kt       -- factory interface + RealDataSourceFactory + SimulatedDataSourceFactory
    BatchSimulator.kt          -- headless multi-run simulator

ui/debug/
    DebugSimulationScreen.kt   -- scenario picker + batch mode UI
    DebugSimulationViewModel.kt -- drives batch runs, holds UI state
    SimulationOverlay.kt       -- floating panel on workout screen
```

### Changes to Existing Files

| File | Change |
|------|--------|
| `BleHrManager.kt` | Add `: HrDataSource` to class declaration. Import interface. |
| `GpsDistanceTracker.kt` | Add `: LocationDataSource` to class declaration. Import interface. |
| `BleConnectionCoordinator.kt` | No return type change. `managerForWorkout()` still returns `BleHrManager`. |
| `WorkoutForegroundService.kt` | Inject `RealDataSourceFactory`. Add `clock` field. Replace all `System.currentTimeMillis()` with `clock.now()`. Use factory in `startWorkout()`. Skip permission gate in sim mode. Replace direct `GpsDistanceTracker(this)` and `bleCoordinator.managerForWorkout()` with factory calls. Store `locationSource` as `LocationDataSource` instead of `GpsDistanceTracker?`. |
| `WorkoutScreen.kt` | Conditionally show `SimulationOverlay` when `SimulationController.isActive` |
| `SetupScreen.kt` / `SetupViewModel.kt` | Show sim badge; skip BLE scanning when sim active; pass no device address |
| `AccountScreen.kt` | Add "Simulation" nav item (debug builds only) |
| `NavGraph.kt` | Add `debug/simulation` route |
| `AppModule.kt` | Provide `RealDataSourceFactory` as singleton |
| `PermissionGate.kt` | No change — bypass is in the service's `onStartCommand()` |

### What This Does NOT Change

- `WorkoutState` — unchanged, receives the same updates
- `ZoneEngine`, `AdaptivePaceController` — unchanged, receive the same ticks
- `AlertPolicy`, `CoachingEventRouter`, `CoachingAudioManager` — unchanged, receive `nowMs` from `processTick()`
- `TrackPointRecorder` — unchanged, receives `timestampMs` from `processTick()`
- `AutoPauseDetector` — unchanged, receives `nowMs` from `processTick()`
- Room database schema — unchanged, no migration needed
- All metrics derivation logic — unchanged
- `UserProfileRepository` — unchanged, not called in sim/batch paths

### Risk Assessment

- **Low risk:** Interface extraction is additive — existing code gains interface declarations but no behavioral change
- **Medium risk:** `System.currentTimeMillis()` replacement in the service — all 10 call sites enumerated above. A missed call would cause time inconsistency during accelerated simulation. Mitigation: the audit table above is exhaustive; implementation should search for any remaining `System.currentTimeMillis()` references in the service file.
- **Low risk:** PermissionGate bypass — only active when `SimulationController.isActive`, which is only true when user explicitly enables simulation from the debug screen.
- **Low risk:** Simulation overlay and debug screen are isolated UI — no impact on existing screens.
- **Low risk:** Batch mode writes to DB but skips `UserProfileRepository` writes — no corruption of user-facing profile settings.
