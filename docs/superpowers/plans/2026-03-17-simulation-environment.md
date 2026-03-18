# Simulation Environment Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an in-app simulation environment with fake HR/GPS data, time acceleration, preset scenarios, and batch mode for multi-run adaptation testing.

**Architecture:** Extract `HrDataSource` and `LocationDataSource` interfaces from existing concrete classes. Build simulated implementations driven by a `SimulationClock` that supports wall-clock scaling (live UI) and discrete stepping (batch). Wire via a `DataSourceFactory` that the service uses instead of direct construction.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, StateFlow, Room, coroutines

**Spec:** `docs/superpowers/specs/2026-03-17-simulation-environment-design.md`

---

## File Map

### New Files (domain/simulation/)
| File | Responsibility |
|------|---------------|
| `domain/simulation/HrDataSource.kt` | Interface: `heartRate: StateFlow<Int>`, `isConnected: StateFlow<Boolean>` |
| `domain/simulation/LocationDataSource.kt` | Interface: distance, location, speed flows + start/stop/setMoving |
| `domain/simulation/WorkoutClock.kt` | Interface `now(): Long` + `RealClock` implementation |
| `domain/simulation/SimulationScenario.kt` | Data model + 5 preset scenarios as companion constants |

### New Files (service/simulation/)
| File | Responsibility |
|------|---------------|
| `service/simulation/SimulationClock.kt` | Wall-clock-scaled + discrete-step clock implementing `WorkoutClock` |
| `service/simulation/SimulationController.kt` | Singleton state: active scenario, speed, clock attachment |
| `service/simulation/SimulatedHrSource.kt` | Fake HR emitter from scenario profile |
| `service/simulation/SimulatedLocationSource.kt` | Fake GPS emitter from scenario profile |
| `service/simulation/DataSourceFactory.kt` | Factory interface + `RealDataSourceFactory` + `SimulatedDataSourceFactory` |
| `service/simulation/BatchSimulator.kt` | Headless multi-run simulator for adaptation testing |

### New Files (ui/debug/)
| File | Responsibility |
|------|---------------|
| `ui/debug/DebugSimulationScreen.kt` | Scenario picker, speed control, batch mode UI |
| `ui/debug/DebugSimulationViewModel.kt` | Drives batch runs, manages UI state |
| `ui/debug/SimulationOverlay.kt` | Floating panel on workout screen during sim |

### New Test Files
| File | Tests |
|------|-------|
| `test/.../simulation/SimulationClockTest.kt` | Clock scaling, speed changes, discrete stepping |
| `test/.../simulation/SimulationScenarioTest.kt` | Linear interpolation, noise, event triggering |
| `test/.../simulation/SimulatedHrSourceTest.kt` | HR emission timing, signal loss events |
| `test/.../simulation/SimulatedLocationSourceTest.kt` | Distance accumulation, GPS dropout, setMoving |
| `test/.../simulation/BatchSimulatorTest.kt` | Multi-run profile evolution |

### Modified Files
| File | Change |
|------|--------|
| `service/BleHrManager.kt` | Add `: HrDataSource` interface declaration |
| `service/GpsDistanceTracker.kt` | Add `: LocationDataSource` interface declaration |
| `service/WorkoutForegroundService.kt` | Inject factory, add clock field, replace all `System.currentTimeMillis()`, skip perms in sim mode |
| `di/AppModule.kt` | Add `provideRealDataSourceFactory()` |
| `ui/workout/ActiveWorkoutScreen.kt` | Show `SimulationOverlay` when sim active |
| `ui/setup/SetupScreen.kt` | Show sim badge, skip BLE when sim active |
| `ui/setup/SetupViewModel.kt` | Skip BLE validation in sim mode |
| `ui/account/AccountScreen.kt` | Add "Simulation" nav item (debug builds) |
| `ui/navigation/NavGraph.kt` | Add simulation route |

---

## Task Dependency Graph

```
Task 1 (interfaces + clock)
    ├── Task 2 (production classes get interfaces)
    ├── Task 3 (SimulationClock)
    └── Task 4 (SimulationScenario)
            ├── Task 5 (SimulatedHrSource) ──────┐
            └── Task 6 (SimulatedLocationSource) ─┤
                                                   ├── Task 7 (SimulationController)
Task 2 ────────────────────────────────────────────┤
                                                   ├── Task 8 (DataSourceFactory)
                                                   │       └── Task 9 (Service refactor)
                                                   │               └── Task 10 (UI: overlay + setup + debug screen)
                                                   └── Task 11 (BatchSimulator)
                                                           └── Task 10 (debug screen batch section)
```

Tasks 3, 4 can run in parallel. Tasks 5, 6 can run in parallel. Tasks 10, 11 partially overlap.

---

## Task 1: Domain Interfaces and Clock

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/simulation/HrDataSource.kt`
- Create: `app/src/main/java/com/hrcoach/domain/simulation/LocationDataSource.kt`
- Create: `app/src/main/java/com/hrcoach/domain/simulation/WorkoutClock.kt`

- [ ] **Step 1: Create `HrDataSource.kt`**

```kotlin
package com.hrcoach.domain.simulation

import kotlinx.coroutines.flow.StateFlow

interface HrDataSource {
    val heartRate: StateFlow<Int>
    val isConnected: StateFlow<Boolean>
}
```

- [ ] **Step 2: Create `LocationDataSource.kt`**

```kotlin
package com.hrcoach.domain.simulation

import android.location.Location
import kotlinx.coroutines.flow.StateFlow

interface LocationDataSource {
    val distanceMeters: StateFlow<Float>
    val currentLocation: StateFlow<Location?>
    val currentSpeed: StateFlow<Float?>
    fun start()
    fun stop()
    fun setMoving(moving: Boolean)
}
```

- [ ] **Step 3: Create `WorkoutClock.kt`**

```kotlin
package com.hrcoach.domain.simulation

interface WorkoutClock {
    fun now(): Long
}

class RealClock : WorkoutClock {
    override fun now(): Long = System.currentTimeMillis()
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/simulation/
git commit -m "feat(sim): add HrDataSource, LocationDataSource, WorkoutClock interfaces"
```

---

## Task 2: Production Classes Implement Interfaces

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/BleHrManager.kt` (line 30)
- Modify: `app/src/main/java/com/hrcoach/service/GpsDistanceTracker.kt` (line 18)

- [ ] **Step 1: Add `HrDataSource` to `BleHrManager`**

In `BleHrManager.kt`, change line 30:
```kotlin
// FROM:
class BleHrManager(context: Context) {
// TO:
class BleHrManager(context: Context) : HrDataSource {
```
Add import: `import com.hrcoach.domain.simulation.HrDataSource`

- [ ] **Step 2: Add `LocationDataSource` to `GpsDistanceTracker`**

In `GpsDistanceTracker.kt`, change line 18:
```kotlin
// FROM:
class GpsDistanceTracker(context: Context) {
// TO:
class GpsDistanceTracker(context: Context) : LocationDataSource {
```
Add import: `import com.hrcoach.domain.simulation.LocationDataSource`

- [ ] **Step 3: Verify build**

Run: `./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL. The existing `heartRate`, `isConnected`, `distanceMeters`, `currentLocation`, `currentSpeed`, `start()`, `stop()`, `setMoving()` already satisfy the interfaces.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/BleHrManager.kt app/src/main/java/com/hrcoach/service/GpsDistanceTracker.kt
git commit -m "feat(sim): BleHrManager and GpsDistanceTracker implement data source interfaces"
```

---

## Task 3: SimulationClock

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/simulation/SimulationClock.kt`
- Create: `app/src/test/java/com/hrcoach/service/simulation/SimulationClockTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.hrcoach.service.simulation

import org.junit.Assert.*
import org.junit.Test

class SimulationClockTest {

    @Test
    fun `now returns real time at 1x speed`() {
        val clock = SimulationClock()
        val before = System.currentTimeMillis()
        val simTime = clock.now()
        val after = System.currentTimeMillis()
        assertTrue("sim time should be between before and after", simTime in before..after)
    }

    @Test
    fun `advanceBy steps simulated time forward`() {
        val clock = SimulationClock()
        val start = clock.now()
        clock.advanceBy(10_000L) // 10 simulated seconds
        val after = clock.now()
        // Real time elapsed is < 100ms, but sim time jumped ~10s
        assertTrue("sim time should advance by ~10s", after - start in 9_900L..10_200L)
    }

    @Test
    fun `setSpeed re-anchors without time jumps`() {
        val clock = SimulationClock()
        val before = clock.now()
        clock.setSpeed(10f)
        val after = clock.now()
        // Immediately after setSpeed, no time should have jumped
        assertTrue("no time jump on speed change", after - before < 100L)
    }

    @Test
    fun `reset returns to real time`() {
        val clock = SimulationClock()
        clock.advanceBy(60_000L) // Jump ahead 60s
        clock.reset()
        val now = clock.now()
        val real = System.currentTimeMillis()
        assertTrue("reset should return to real time", kotlin.math.abs(now - real) < 100L)
    }
}
```

- [ ] **Step 2: Run tests, verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.simulation.SimulationClockTest" 2>&1 | tail -10`
Expected: FAIL (class not found)

- [ ] **Step 3: Implement `SimulationClock`**

```kotlin
package com.hrcoach.service.simulation

import com.hrcoach.domain.simulation.WorkoutClock
import kotlinx.coroutines.flow.MutableStateFlow

class SimulationClock(
    val speedMultiplier: MutableStateFlow<Float> = MutableStateFlow(1f)
) : WorkoutClock {
    private var anchorRealMs: Long = System.currentTimeMillis()
    private var anchorSimMs: Long = anchorRealMs

    override fun now(): Long {
        val realElapsed = System.currentTimeMillis() - anchorRealMs
        return anchorSimMs + (realElapsed * speedMultiplier.value).toLong()
    }

    fun setSpeed(multiplier: Float) {
        val currentSim = now()
        anchorRealMs = System.currentTimeMillis()
        anchorSimMs = currentSim
        speedMultiplier.value = multiplier
    }

    fun advanceBy(deltaMs: Long) {
        anchorSimMs += deltaMs
        anchorRealMs = System.currentTimeMillis()
    }

    fun reset() {
        anchorRealMs = System.currentTimeMillis()
        anchorSimMs = anchorRealMs
        speedMultiplier.value = 1f
    }
}
```

- [ ] **Step 4: Run tests, verify pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.simulation.SimulationClockTest" 2>&1 | tail -10`
Expected: 4 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/simulation/SimulationClock.kt app/src/test/java/com/hrcoach/service/simulation/SimulationClockTest.kt
git commit -m "feat(sim): add SimulationClock with wall-clock scaling and discrete step modes"
```

---

## Task 4: SimulationScenario Data Model + Presets

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/simulation/SimulationScenario.kt`
- Create: `app/src/test/java/com/hrcoach/domain/simulation/SimulationScenarioTest.kt`

- [ ] **Step 1: Write failing tests for interpolation logic**

```kotlin
package com.hrcoach.domain.simulation

import org.junit.Assert.*
import org.junit.Test

class SimulationScenarioTest {

    @Test
    fun `interpolateHr returns exact value at data point`() {
        val scenario = SimulationScenario(
            name = "test",
            durationSeconds = 60,
            hrProfile = listOf(HrDataPoint(0, 70), HrDataPoint(60, 140)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f))
        )
        assertEquals(70, scenario.interpolateHr(0f))
        assertEquals(140, scenario.interpolateHr(60f))
    }

    @Test
    fun `interpolateHr linearly interpolates between points`() {
        val scenario = SimulationScenario(
            name = "test",
            durationSeconds = 60,
            hrProfile = listOf(HrDataPoint(0, 100), HrDataPoint(60, 160)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f))
        )
        assertEquals(130, scenario.interpolateHr(30f))
    }

    @Test
    fun `interpolateHr clamps beyond last point`() {
        val scenario = SimulationScenario(
            name = "test",
            durationSeconds = 60,
            hrProfile = listOf(HrDataPoint(0, 100), HrDataPoint(60, 160)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f))
        )
        assertEquals(160, scenario.interpolateHr(120f))
    }

    @Test
    fun `interpolatePace returns exact value at data point`() {
        val scenario = SimulationScenario(
            name = "test",
            durationSeconds = 60,
            hrProfile = listOf(HrDataPoint(0, 100)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f), PaceDataPoint(60, 5.0f))
        )
        assertEquals(6.0f, scenario.interpolatePace(0f), 0.01f)
        assertEquals(5.0f, scenario.interpolatePace(60f), 0.01f)
    }

    @Test
    fun `isSignalLost returns true during signal loss event`() {
        val scenario = SimulationScenario(
            name = "test",
            durationSeconds = 120,
            hrProfile = listOf(HrDataPoint(0, 100)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f)),
            events = listOf(SimEvent.SignalLoss(atSeconds = 30, durationSeconds = 15))
        )
        assertFalse(scenario.isSignalLost(29f))
        assertTrue(scenario.isSignalLost(30f))
        assertTrue(scenario.isSignalLost(44f))
        assertFalse(scenario.isSignalLost(45f))
    }

    @Test
    fun `isGpsDropout returns true during GPS dropout event`() {
        val scenario = SimulationScenario(
            name = "test",
            durationSeconds = 120,
            hrProfile = listOf(HrDataPoint(0, 100)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f)),
            events = listOf(SimEvent.GpsDropout(atSeconds = 50, durationSeconds = 20))
        )
        assertFalse(scenario.isGpsDropout(49f))
        assertTrue(scenario.isGpsDropout(50f))
        assertTrue(scenario.isGpsDropout(69f))
        assertFalse(scenario.isGpsDropout(70f))
    }

    @Test
    fun `isStopped returns true during stop event`() {
        val scenario = SimulationScenario(
            name = "test",
            durationSeconds = 120,
            hrProfile = listOf(HrDataPoint(0, 100)),
            paceProfile = listOf(PaceDataPoint(0, 6.0f)),
            events = listOf(SimEvent.Stop(atSeconds = 60, durationSeconds = 30))
        )
        assertFalse(scenario.isStopped(59f))
        assertTrue(scenario.isStopped(60f))
        assertFalse(scenario.isStopped(90f))
    }

    @Test
    fun `preset EASY_STEADY_RUN has valid structure`() {
        val s = SimulationScenario.EASY_STEADY_RUN
        assertTrue(s.hrProfile.size >= 2)
        assertTrue(s.paceProfile.isNotEmpty())
        assertEquals(1200, s.durationSeconds) // 20 min
    }
}
```

- [ ] **Step 2: Run tests, verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.simulation.SimulationScenarioTest" 2>&1 | tail -10`
Expected: FAIL

- [ ] **Step 3: Implement `SimulationScenario`**

```kotlin
package com.hrcoach.domain.simulation

data class SimulationScenario(
    val name: String,
    val durationSeconds: Int,
    val hrProfile: List<HrDataPoint>,
    val paceProfile: List<PaceDataPoint>,
    val events: List<SimEvent> = emptyList()
) {
    fun interpolateHr(timeSeconds: Float): Int {
        if (hrProfile.isEmpty()) return 0
        if (timeSeconds <= hrProfile.first().timeSeconds) return hrProfile.first().hr
        if (timeSeconds >= hrProfile.last().timeSeconds) return hrProfile.last().hr
        val idx = hrProfile.indexOfLast { it.timeSeconds <= timeSeconds }
        val a = hrProfile[idx]
        val b = hrProfile.getOrNull(idx + 1) ?: return a.hr
        val t = (timeSeconds - a.timeSeconds) / (b.timeSeconds - a.timeSeconds)
        return (a.hr + t * (b.hr - a.hr)).toInt()
    }

    fun interpolatePace(timeSeconds: Float): Float {
        if (paceProfile.isEmpty()) return 6.0f
        if (timeSeconds <= paceProfile.first().timeSeconds) return paceProfile.first().paceMinPerKm
        if (timeSeconds >= paceProfile.last().timeSeconds) return paceProfile.last().paceMinPerKm
        val idx = paceProfile.indexOfLast { it.timeSeconds <= timeSeconds }
        val a = paceProfile[idx]
        val b = paceProfile.getOrNull(idx + 1) ?: return a.paceMinPerKm
        val t = (timeSeconds - a.timeSeconds) / (b.timeSeconds - a.timeSeconds)
        return a.paceMinPerKm + t * (b.paceMinPerKm - a.paceMinPerKm)
    }

    fun isSignalLost(timeSeconds: Float): Boolean =
        events.any { it is SimEvent.SignalLoss && timeSeconds >= it.atSeconds && timeSeconds < it.atSeconds + it.durationSeconds }

    fun isGpsDropout(timeSeconds: Float): Boolean =
        events.any { it is SimEvent.GpsDropout && timeSeconds >= it.atSeconds && timeSeconds < it.atSeconds + it.durationSeconds }

    fun isStopped(timeSeconds: Float): Boolean =
        events.any { it is SimEvent.Stop && timeSeconds >= it.atSeconds && timeSeconds < it.atSeconds + it.durationSeconds }

    companion object {
        val EASY_STEADY_RUN = SimulationScenario(
            name = "Easy Steady Run",
            durationSeconds = 1200, // 20 min
            hrProfile = listOf(
                HrDataPoint(0, 70),
                HrDataPoint(180, 140),  // ramp over 3 min
                HrDataPoint(1200, 142)
            ),
            paceProfile = listOf(
                PaceDataPoint(0, 6.0f)
            )
        )

        val ZONE_DRIFT = SimulationScenario(
            name = "Zone Drift",
            durationSeconds = 1200, // 20 min
            hrProfile = listOf(
                HrDataPoint(0, 70),
                HrDataPoint(120, 140),
                HrDataPoint(300, 155),   // drift above
                HrDataPoint(420, 138),   // recover
                HrDataPoint(600, 158),   // drift again
                HrDataPoint(720, 140),   // recover
                HrDataPoint(900, 160),   // drift higher
                HrDataPoint(1020, 142),  // recover
                HrDataPoint(1200, 140)
            ),
            paceProfile = listOf(
                PaceDataPoint(0, 5.5f)
            )
        )

        val INTERVAL_SESSION = SimulationScenario(
            name = "Interval Session",
            durationSeconds = 1500, // 25 min
            hrProfile = listOf(
                HrDataPoint(0, 70),
                HrDataPoint(120, 130),  // warm up 2 min
                // Interval 1: high 3min, recovery 2min
                HrDataPoint(180, 170), HrDataPoint(300, 172),
                HrDataPoint(360, 120), HrDataPoint(420, 118),
                // Interval 2
                HrDataPoint(480, 170), HrDataPoint(600, 174),
                HrDataPoint(660, 122), HrDataPoint(720, 118),
                // Interval 3
                HrDataPoint(780, 172), HrDataPoint(900, 175),
                HrDataPoint(960, 120), HrDataPoint(1020, 116),
                // Interval 4
                HrDataPoint(1080, 170), HrDataPoint(1200, 176),
                HrDataPoint(1260, 125),
                HrDataPoint(1500, 100) // cool down
            ),
            paceProfile = listOf(
                PaceDataPoint(0, 6.0f),
                PaceDataPoint(180, 4.5f), PaceDataPoint(300, 4.5f),
                PaceDataPoint(360, 6.5f), PaceDataPoint(420, 6.5f),
                PaceDataPoint(480, 4.5f), PaceDataPoint(600, 4.5f),
                PaceDataPoint(660, 6.5f), PaceDataPoint(720, 6.5f),
                PaceDataPoint(780, 4.5f), PaceDataPoint(900, 4.5f),
                PaceDataPoint(960, 6.5f), PaceDataPoint(1020, 6.5f),
                PaceDataPoint(1080, 4.5f), PaceDataPoint(1200, 4.5f),
                PaceDataPoint(1260, 6.0f), PaceDataPoint(1500, 7.0f)
            )
        )

        val SIGNAL_LOSS = SimulationScenario(
            name = "Signal Loss",
            durationSeconds = 900, // 15 min
            hrProfile = listOf(
                HrDataPoint(0, 70),
                HrDataPoint(120, 140),
                HrDataPoint(900, 145)
            ),
            paceProfile = listOf(PaceDataPoint(0, 5.5f)),
            events = listOf(
                SimEvent.SignalLoss(atSeconds = 240, durationSeconds = 30),
                SimEvent.SignalLoss(atSeconds = 540, durationSeconds = 60)
            )
        )

        val GPS_DROPOUT = SimulationScenario(
            name = "GPS Dropout",
            durationSeconds = 900, // 15 min
            hrProfile = listOf(
                HrDataPoint(0, 70),
                HrDataPoint(120, 140),
                HrDataPoint(900, 145)
            ),
            paceProfile = listOf(PaceDataPoint(0, 5.5f)),
            events = listOf(
                SimEvent.GpsDropout(atSeconds = 300, durationSeconds = 45)
            )
        )

        val ALL_PRESETS = listOf(EASY_STEADY_RUN, ZONE_DRIFT, INTERVAL_SESSION, SIGNAL_LOSS, GPS_DROPOUT)
    }
}

data class HrDataPoint(
    val timeSeconds: Int,
    val hr: Int
)

data class PaceDataPoint(
    val timeSeconds: Int,
    val paceMinPerKm: Float
)

sealed class SimEvent {
    abstract val atSeconds: Int
    abstract val durationSeconds: Int

    data class SignalLoss(override val atSeconds: Int, override val durationSeconds: Int) : SimEvent()
    data class GpsDropout(override val atSeconds: Int, override val durationSeconds: Int) : SimEvent()
    data class Stop(override val atSeconds: Int, override val durationSeconds: Int) : SimEvent()
}
```

- [ ] **Step 4: Run tests, verify pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.simulation.SimulationScenarioTest" 2>&1 | tail -10`
Expected: All tests PASSED

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/simulation/SimulationScenario.kt app/src/test/java/com/hrcoach/domain/simulation/SimulationScenarioTest.kt
git commit -m "feat(sim): add SimulationScenario data model with interpolation and 5 presets"
```

---

## Task 5: SimulatedHrSource

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/simulation/SimulatedHrSource.kt`
- Create: `app/src/test/java/com/hrcoach/service/simulation/SimulatedHrSourceTest.kt`

**Depends on:** Task 1 (interfaces), Task 3 (SimulationClock), Task 4 (SimulationScenario)

- [ ] **Step 1: Write failing tests**

```kotlin
package com.hrcoach.service.simulation

import com.hrcoach.domain.simulation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SimulatedHrSourceTest {

    private val scenario = SimulationScenario(
        name = "test",
        durationSeconds = 60,
        hrProfile = listOf(HrDataPoint(0, 100), HrDataPoint(60, 160)),
        paceProfile = listOf(PaceDataPoint(0, 6.0f))
    )

    @Test
    fun `initial state is connected with zero HR`() {
        val clock = SimulationClock()
        val source = SimulatedHrSource(clock, scenario)
        assertEquals(0, source.heartRate.value)
        assertTrue(source.isConnected.value)
    }

    @Test
    fun `updateForTime sets HR from scenario interpolation`() {
        val clock = SimulationClock()
        val source = SimulatedHrSource(clock, scenario)
        source.updateForTime(0f)
        assertEquals(100, source.heartRate.value)
        source.updateForTime(30f)
        assertEquals(130, source.heartRate.value)
    }

    @Test
    fun `signal loss event sets isConnected false and HR to 0`() {
        val scenarioWithLoss = scenario.copy(
            events = listOf(SimEvent.SignalLoss(atSeconds = 10, durationSeconds = 10))
        )
        val clock = SimulationClock()
        val source = SimulatedHrSource(clock, scenarioWithLoss)

        source.updateForTime(9f)
        assertTrue(source.isConnected.value)
        assertTrue(source.heartRate.value > 0)

        source.updateForTime(10f)
        assertFalse(source.isConnected.value)
        assertEquals(0, source.heartRate.value)

        source.updateForTime(20f)
        assertTrue(source.isConnected.value)
        assertTrue(source.heartRate.value > 0)
    }
}
```

- [ ] **Step 2: Run tests, verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.simulation.SimulatedHrSourceTest" 2>&1 | tail -10`
Expected: FAIL

- [ ] **Step 3: Implement `SimulatedHrSource`**

```kotlin
package com.hrcoach.service.simulation

import com.hrcoach.domain.simulation.HrDataSource
import com.hrcoach.domain.simulation.SimulationScenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

class SimulatedHrSource(
    private val clock: SimulationClock,
    private val scenario: SimulationScenario
) : HrDataSource {

    private val _heartRate = MutableStateFlow(0)
    override val heartRate: StateFlow<Int> = _heartRate

    private val _isConnected = MutableStateFlow(true)
    override val isConnected: StateFlow<Boolean> = _isConnected

    private var startTimeMs: Long = clock.now()

    fun updateForTime(elapsedSeconds: Float) {
        if (scenario.isSignalLost(elapsedSeconds)) {
            _isConnected.value = false
            _heartRate.value = 0
        } else {
            _isConnected.value = true
            val baseHr = scenario.interpolateHr(elapsedSeconds)
            // Add small Gaussian noise for realism (stddev ~2 BPM)
            val noise = (Random.nextGaussian() * 2).toInt()
            _heartRate.value = (baseHr + noise).coerceIn(30, 220)
        }
    }
}
```

- [ ] **Step 4: Run tests, verify pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.simulation.SimulatedHrSourceTest" 2>&1 | tail -10`
Expected: All tests PASSED

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/simulation/SimulatedHrSource.kt app/src/test/java/com/hrcoach/service/simulation/SimulatedHrSourceTest.kt
git commit -m "feat(sim): add SimulatedHrSource with scenario interpolation and signal loss"
```

---

## Task 6: SimulatedLocationSource

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/simulation/SimulatedLocationSource.kt`
- Create: `app/src/test/java/com/hrcoach/service/simulation/SimulatedLocationSourceTest.kt`

**Depends on:** Task 1, Task 3, Task 4

- [ ] **Step 1: Write failing tests**

```kotlin
package com.hrcoach.service.simulation

import com.hrcoach.domain.simulation.*
import org.junit.Assert.*
import org.junit.Test

class SimulatedLocationSourceTest {

    private val scenario = SimulationScenario(
        name = "test",
        durationSeconds = 600,
        hrProfile = listOf(HrDataPoint(0, 140)),
        paceProfile = listOf(PaceDataPoint(0, 6.0f)) // 6 min/km = 10 km/h = 2.778 m/s
    )

    @Test
    fun `initial state has zero distance`() {
        val clock = SimulationClock()
        val source = SimulatedLocationSource(clock, scenario)
        assertEquals(0f, source.distanceMeters.value, 0.01f)
        assertNull(source.currentLocation.value)
        assertNull(source.currentSpeed.value)
    }

    @Test
    fun `updateForTime accumulates distance from pace`() {
        val clock = SimulationClock()
        val source = SimulatedLocationSource(clock, scenario)
        source.start()
        // At 6 min/km = 2.778 m/s, after 60s should be ~166.7m
        source.updateForTime(60f)
        val dist = source.distanceMeters.value
        assertTrue("distance should be ~166.7m, was $dist", dist in 150f..180f)
    }

    @Test
    fun `setMoving false pauses distance accumulation`() {
        val clock = SimulationClock()
        val source = SimulatedLocationSource(clock, scenario)
        source.start()
        source.updateForTime(60f)
        val distBefore = source.distanceMeters.value
        source.setMoving(false)
        source.updateForTime(120f)
        assertEquals("distance should not change when not moving", distBefore, source.distanceMeters.value, 0.01f)
    }

    @Test
    fun `setMoving true resumes distance accumulation`() {
        val clock = SimulationClock()
        val source = SimulatedLocationSource(clock, scenario)
        source.start()
        source.updateForTime(60f)
        source.setMoving(false)
        source.updateForTime(90f)
        val distPaused = source.distanceMeters.value
        source.setMoving(true)
        source.updateForTime(150f)
        assertTrue("distance should increase after resuming", source.distanceMeters.value > distPaused)
    }

    @Test
    fun `GPS dropout sets null location and speed`() {
        val scenarioWithDropout = scenario.copy(
            events = listOf(SimEvent.GpsDropout(atSeconds = 60, durationSeconds = 30))
        )
        val clock = SimulationClock()
        val source = SimulatedLocationSource(clock, scenarioWithDropout)
        source.start()

        source.updateForTime(59f)
        assertNotNull(source.currentLocation.value)

        source.updateForTime(60f)
        assertNull(source.currentLocation.value)
        assertNull(source.currentSpeed.value)

        source.updateForTime(90f)
        assertNotNull(source.currentLocation.value)
    }

    @Test
    fun `stop event sets speed to zero`() {
        val scenarioWithStop = scenario.copy(
            events = listOf(SimEvent.Stop(atSeconds = 60, durationSeconds = 30))
        )
        val clock = SimulationClock()
        val source = SimulatedLocationSource(clock, scenarioWithStop)
        source.start()

        source.updateForTime(59f)
        assertTrue((source.currentSpeed.value ?: 0f) > 0f)

        source.updateForTime(65f)
        assertEquals(0f, source.currentSpeed.value ?: -1f, 0.01f)
    }

    @Test
    fun `stop resets running state`() {
        val clock = SimulationClock()
        val source = SimulatedLocationSource(clock, scenario)
        source.start()
        source.updateForTime(60f)
        source.stop()
        // After stop, state should be cleared
        // Source should not accumulate further
    }
}
```

- [ ] **Step 2: Run tests, verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.simulation.SimulatedLocationSourceTest" 2>&1 | tail -10`
Expected: FAIL

- [ ] **Step 3: Implement `SimulatedLocationSource`**

```kotlin
package com.hrcoach.service.simulation

import android.location.Location
import com.hrcoach.domain.simulation.LocationDataSource
import com.hrcoach.domain.simulation.SimulationScenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SimulatedLocationSource(
    private val clock: SimulationClock,
    private val scenario: SimulationScenario
) : LocationDataSource {

    private val _distanceMeters = MutableStateFlow(0f)
    override val distanceMeters: StateFlow<Float> = _distanceMeters

    private val _currentLocation = MutableStateFlow<Location?>(null)
    override val currentLocation: StateFlow<Location?> = _currentLocation

    private val _currentSpeed = MutableStateFlow<Float?>(null)
    override val currentSpeed: StateFlow<Float?> = _currentSpeed

    @Volatile
    private var isMoving: Boolean = true
    private var isRunning: Boolean = false
    private var lastUpdateSeconds: Float = 0f

    // Simulated route: straight line heading north from a start point
    private val startLat = 40.7128  // NYC
    private val startLon = -74.0060

    override fun start() {
        isRunning = true
        isMoving = true
        lastUpdateSeconds = 0f
        _distanceMeters.value = 0f
        _currentLocation.value = null
        _currentSpeed.value = null
    }

    override fun stop() {
        isRunning = false
        _currentLocation.value = null
        _currentSpeed.value = null
    }

    override fun setMoving(moving: Boolean) {
        isMoving = moving
    }

    fun updateForTime(elapsedSeconds: Float) {
        if (!isRunning) return

        if (scenario.isGpsDropout(elapsedSeconds)) {
            _currentLocation.value = null
            _currentSpeed.value = null
            lastUpdateSeconds = elapsedSeconds
            return
        }

        val paceMinPerKm = scenario.interpolatePace(elapsedSeconds)
        val speedMs = if (scenario.isStopped(elapsedSeconds)) {
            0f
        } else {
            if (paceMinPerKm > 0f) 1000f / (paceMinPerKm * 60f) else 0f
        }

        _currentSpeed.value = speedMs

        val dt = elapsedSeconds - lastUpdateSeconds
        if (dt > 0f && isMoving && speedMs > 0f) {
            _distanceMeters.value += speedMs * dt
        }
        lastUpdateSeconds = elapsedSeconds

        // Generate a location along a straight-line route heading north
        val totalDist = _distanceMeters.value
        val latOffset = totalDist / 111_111.0  // ~1 degree lat per 111km
        val loc = Location("simulation").apply {
            latitude = startLat + latOffset
            longitude = startLon
            accuracy = 5f
            time = clock.now()
            if (speedMs > 0f) {
                speed = speedMs
            }
        }
        _currentLocation.value = loc
    }
}
```

- [ ] **Step 4: Run tests, verify pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.simulation.SimulatedLocationSourceTest" 2>&1 | tail -10`
Expected: All tests PASSED

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/simulation/SimulatedLocationSource.kt app/src/test/java/com/hrcoach/service/simulation/SimulatedLocationSourceTest.kt
git commit -m "feat(sim): add SimulatedLocationSource with pace-based distance and GPS events"
```

---

## Task 7: SimulationController

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/simulation/SimulationController.kt`

**Depends on:** Task 3 (SimulationClock), Task 4 (SimulationScenario)

- [ ] **Step 1: Create `SimulationController`**

```kotlin
package com.hrcoach.service.simulation

import com.hrcoach.domain.simulation.SimulationScenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SimulationState(
    val isActive: Boolean = false,
    val scenario: SimulationScenario? = null,
    val speedMultiplier: Float = 1f
)

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

    fun attachClock(simClock: SimulationClock) {
        clock = simClock
    }

    fun setSpeed(speed: Float) {
        clock?.setSpeed(speed)
        _state.update { it.copy(speedMultiplier = speed) }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/simulation/SimulationController.kt
git commit -m "feat(sim): add SimulationController singleton state holder"
```

---

## Task 8: DataSourceFactory + Hilt Wiring

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/simulation/DataSourceFactory.kt`
- Modify: `app/src/main/java/com/hrcoach/di/AppModule.kt`

**Depends on:** Tasks 1-7

- [ ] **Step 1: Create `DataSourceFactory.kt`**

This file contains the interface, `RealDataSourceFactory`, and `SimulatedDataSourceFactory`:

```kotlin
package com.hrcoach.service.simulation

import android.content.Context
import com.hrcoach.domain.simulation.HrDataSource
import com.hrcoach.domain.simulation.LocationDataSource
import com.hrcoach.domain.simulation.RealClock
import com.hrcoach.domain.simulation.SimulationScenario
import com.hrcoach.domain.simulation.WorkoutClock
import com.hrcoach.service.BleConnectionCoordinator
import com.hrcoach.service.GpsDistanceTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface DataSourceFactory {
    fun createHrSource(): HrDataSource
    fun createLocationSource(): LocationDataSource
    fun getClock(): WorkoutClock
}

@Singleton
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

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

Note: `RealDataSourceFactory` uses `@Inject constructor` so Hilt can construct it directly — no `AppModule` `@Provides` method needed. Hilt discovers `@Singleton @Inject` classes automatically.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/simulation/DataSourceFactory.kt
git commit -m "feat(sim): add DataSourceFactory interface with Real and Simulated implementations"
```

---

## Task 9: WorkoutForegroundService Refactor

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

**Depends on:** Task 8

This is the critical integration task. Changes:
1. Inject `RealDataSourceFactory`
2. Add `clock` field
3. Replace all `System.currentTimeMillis()` with `clock.now()`
4. Use factory for data sources
5. Skip permission gate in sim mode
6. Store location source as `LocationDataSource` instead of `GpsDistanceTracker?`

- [ ] **Step 1: Add imports and inject factory**

Add to import section:
```kotlin
import com.hrcoach.domain.simulation.LocationDataSource
import com.hrcoach.domain.simulation.WorkoutClock
import com.hrcoach.domain.simulation.RealClock
import com.hrcoach.service.simulation.RealDataSourceFactory
import com.hrcoach.service.simulation.SimulatedDataSourceFactory
import com.hrcoach.service.simulation.SimulatedHrSource
import com.hrcoach.service.simulation.SimulatedLocationSource
import com.hrcoach.service.simulation.SimulationClock
import com.hrcoach.service.simulation.SimulationController
```

Add inject field after `bleCoordinator`:
```kotlin
@Inject
lateinit var realDataSourceFactory: RealDataSourceFactory
```

- [ ] **Step 2: Replace `gpsTracker` type and add `clock` field**

Change field declarations:
```kotlin
// FROM:
private var gpsTracker: GpsDistanceTracker? = null
// TO:
private var locationSource: LocationDataSource? = null

// ADD:
private var clock: WorkoutClock = RealClock()
private var hrSource: com.hrcoach.domain.simulation.HrDataSource? = null
```

- [ ] **Step 3: Refactor `startWorkout()` to use factory**

Replace the `gpsTracker = GpsDistanceTracker(this)` section with factory-based construction. The key block in `startWorkout()` becomes:

```kotlin
val factory = if (SimulationController.isActive) {
    val simState = SimulationController.state.value
    val simClock = SimulationClock(MutableStateFlow(simState.speedMultiplier))
    SimulationController.attachClock(simClock)
    SimulatedDataSourceFactory(simState.scenario!!, simClock)
} else {
    realDataSourceFactory
}

clock = factory.getClock()
hrSource = factory.createHrSource()
locationSource = factory.createLocationSource()
coachingAudioManager = CoachingAudioManager(this, audioSettingsRepository.getAudioSettings())
zoneEngine = ZoneEngine(workoutConfig)
```

Also replace `workoutStartMs = System.currentTimeMillis()` with `workoutStartMs = clock.now()` and `WorkoutEntity(startTime = System.currentTimeMillis())` with `WorkoutEntity(startTime = clock.now())`.

- [ ] **Step 4: Replace all `System.currentTimeMillis()` calls with `clock.now()`**

10 call sites per the spec audit table:
1. `startWorkout()` WorkoutEntity startTime
2. `startWorkout()` workoutStartMs assignment
3. `processTick()` nowMs
4. `pauseWorkout()` pauseStartMs
5. `resumeWorkout()` nowMs
6. `ACTION_TOGGLE_AUTO_PAUSE` totalAutoPausedMs calculation
7. `stopWorkout()` final track point timestampMs
8. `stopWorkout()` val now
9. `onDestroy()` endTime fallback

After all replacements, verify no remaining references:
Run: `grep -n "System.currentTimeMillis" app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`
Expected: No output (zero matches)

- [ ] **Step 5: Update `observeWorkoutTicks()` to use interfaces**

Replace:
```kotlin
val hrManager = bleCoordinator.managerForWorkout()
val tracker = gpsTracker ?: return
```
With:
```kotlin
val hr = hrSource ?: return
val loc = locationSource ?: return
```

And the combine block references `hr.heartRate`, `hr.isConnected`, `loc.distanceMeters`, `loc.currentLocation`, `loc.currentSpeed`.

- [ ] **Step 6: Add simulation tick driver in `observeWorkoutTicks()`**

After the existing `combine(...).collect {}` block, add a parallel coroutine that drives simulated sources. When in sim mode, launch a coroutine that periodically calls `updateForTime()` on the simulated sources:

```kotlin
// Drive simulated sources if in sim mode
if (hrSource is SimulatedHrSource && locationSource is SimulatedLocationSource) {
    val simHr = hrSource as SimulatedHrSource
    val simLoc = locationSource as SimulatedLocationSource
    lifecycleScope.launch(Dispatchers.IO) {
        while (true) {
            val elapsedSec = (clock.now() - workoutStartMs) / 1000f
            simHr.updateForTime(elapsedSec)
            simLoc.updateForTime(elapsedSec)
            // Tick interval: 100ms real time (at 10x = 1s sim time per tick)
            delay(100)
        }
    }
}
```

This runs BEFORE the `combine().collect()` so that simulated StateFlows emit values that the combine picks up.

- [ ] **Step 7: Update `gpsTracker` references throughout the service**

Replace all `gpsTracker?.` references with `locationSource?.`:
- `startWorkout()`: `gpsTracker?.start()` -> `locationSource?.start()`
- `processTick()`: `gpsTracker?.setMoving(false)` / `true` -> `locationSource?.setMoving(false)` / `true`
- `ACTION_TOGGLE_AUTO_PAUSE`: `gpsTracker?.setMoving(true)` -> `locationSource?.setMoving(true)`
- `stopWorkout()`: `gpsTracker?.stop()` -> `locationSource?.stop()`
- `cleanupManagers()`: `gpsTracker?.stop(); gpsTracker = null` -> `locationSource?.stop(); locationSource = null`

- [ ] **Step 8: Skip permission gate in sim mode**

In `onStartCommand()`, change:
```kotlin
// FROM:
if (!PermissionGate.hasAllRuntimePermissions(this)) {
// TO:
if (!SimulationController.isActive && !PermissionGate.hasAllRuntimePermissions(this)) {
```

- [ ] **Step 9: Skip BLE connection in sim mode**

In the `startupJob` lambda, wrap the BLE connection block:
```kotlin
if (!SimulationController.isActive) {
    val alreadyConnected = bleCoordinator.isConnected.value
    if (!alreadyConnected) {
        val connected = deviceAddress?.let { address ->
            bleCoordinator.connectToAddress(address)
        } ?: false
        if (!connected) {
            bleCoordinator.startScan()
        }
    }
}
```

Also skip `bleCoordinator.disconnect()` in `stopWorkout()` when in sim mode.

- [ ] **Step 10: Verify build**

Run: `./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: Verify existing tests still pass**

Run: `./gradlew testDebugUnitTest 2>&1 | tail -20`
Expected: All existing tests PASS

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(sim): refactor service to use DataSourceFactory and WorkoutClock"
```

---

## Task 10: UI — Simulation Overlay, Setup Badge, Debug Screen

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/debug/SimulationOverlay.kt`
- Create: `app/src/main/java/com/hrcoach/ui/debug/DebugSimulationScreen.kt`
- Create: `app/src/main/java/com/hrcoach/ui/debug/DebugSimulationViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`

**Depends on:** Task 7 (SimulationController), Task 9 (service refactor)

- [ ] **Step 1: Create `SimulationOverlay.kt`**

A glass-style floating panel shown at top of workout screen during simulation:

```kotlin
package com.hrcoach.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.service.simulation.SimulationController
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GlassHighlight

@Composable
fun SimulationOverlay(modifier: Modifier = Modifier) {
    val simState by SimulationController.state.collectAsState()
    if (!simState.isActive) return

    val speedOptions = listOf(1f, 5f, 10f, 50f)
    var speedIndex by remember {
        mutableIntStateOf(speedOptions.indexOf(simState.speedMultiplier).coerceAtLeast(0))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GlassHighlight)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "SIM: ${simState.scenario?.name ?: "Unknown"}",
                color = Color(0xFFFF5A5F),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "${simState.speedMultiplier.toInt()}x",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x33FF5A5F))
                .clickable {
                    speedIndex = (speedIndex + 1) % speedOptions.size
                    SimulationController.setSpeed(speedOptions[speedIndex])
                }
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}
```

- [ ] **Step 2: Add overlay to `ActiveWorkoutScreen.kt`**

In the main `Box` composable of `ActiveWorkoutScreen`, add at the top (after the `Box` opening, before the scrollable Column):

```kotlin
// At top of Box content, before the main Column
if (SimulationController.isActive) {
    SimulationOverlay(modifier = Modifier.align(Alignment.TopCenter).zIndex(10f))
}
```

Add imports:
```kotlin
import com.hrcoach.service.simulation.SimulationController
import com.hrcoach.ui.debug.SimulationOverlay
```

- [ ] **Step 3: Create `DebugSimulationViewModel.kt`**

```kotlin
package com.hrcoach.ui.debug

import androidx.lifecycle.ViewModel
import com.hrcoach.domain.simulation.SimulationScenario
import com.hrcoach.service.simulation.SimulationController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DebugSimUiState(
    val isSimActive: Boolean = false,
    val selectedScenarioIndex: Int = 0,
    val speedMultiplier: Float = 1f,
    val scenarios: List<SimulationScenario> = SimulationScenario.ALL_PRESETS
)

@HiltViewModel
class DebugSimulationViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(DebugSimUiState())
    val uiState: StateFlow<DebugSimUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = DebugSimUiState(isSimActive = SimulationController.isActive)
    }

    fun selectScenario(index: Int) {
        _uiState.value = _uiState.value.copy(selectedScenarioIndex = index)
    }

    fun setSpeed(speed: Float) {
        _uiState.value = _uiState.value.copy(speedMultiplier = speed)
        if (SimulationController.isActive) {
            SimulationController.setSpeed(speed)
        }
    }

    fun toggleSimulation() {
        if (SimulationController.isActive) {
            SimulationController.deactivate()
            _uiState.value = _uiState.value.copy(isSimActive = false)
        } else {
            val state = _uiState.value
            val scenario = state.scenarios[state.selectedScenarioIndex]
            SimulationController.activate(scenario, state.speedMultiplier)
            _uiState.value = state.copy(isSimActive = true)
        }
    }
}
```

- [ ] **Step 4: Create `DebugSimulationScreen.kt`**

A glass-themed debug screen with scenario picker and sim toggle. Uses existing `GlassCard` pattern from `ui/components/GlassCard.kt`. The screen structure follows `AccountScreen` patterns (scrollable Column, section headers, setting rows).

The composable takes `onNavigateToSetup: () -> Unit` so that when simulation is activated, user is directed to start a workout.

Key sections:
- Scenario dropdown (list of `SimulationScenario.ALL_PRESETS` by name)
- Speed slider (1x to 50x)
- Toggle button: "Enable Simulation" / "Disable Simulation"
- When activated, shows a message: "Simulation active. Go to Workout tab to start a simulated run."

- [ ] **Step 5: Add sim badge to `SetupScreen.kt`**

Near the top of the setup screen content, add a conditional badge:

```kotlin
if (SimulationController.isActive) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x33FF5A5F))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("SIM MODE", color = Color(0xFFFF5A5F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            SimulationController.state.collectAsState().value.scenario?.name ?: "",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
    }
}
```

- [ ] **Step 6: Skip BLE in `SetupViewModel.kt` when sim active**

In `SetupViewModel`, the BLE scanning and connection validation should be bypassed in sim mode. Add a check in `buildConfigOrNull()` to skip device address validation when `SimulationController.isActive`. The `onStartWorkout` callback should pass `null` as device address in sim mode.

- [ ] **Step 7: Add simulation route to `NavGraph.kt`**

In the `Routes` object, add:
```kotlin
const val SIMULATION = "simulation"
```

In the `NavHost`, add a new composable block:
```kotlin
composable(Routes.SIMULATION) {
    DebugSimulationScreen(
        onNavigateToSetup = { navController.navigate(Routes.SETUP) },
        onBack = { navController.popBackStack() }
    )
}
```

- [ ] **Step 8: Add "Simulation" item to `AccountScreen.kt`**

After the WORKOUT section (around line 346), add a SIMULATION section gated behind debug:

```kotlin
if (BuildConfig.DEBUG) {
    // SIMULATION section header
    Text("SIMULATION", ...)
    // Navigation row to simulation screen
    Row(modifier = Modifier.clickable { onNavigateToSimulation() }) {
        Text("Simulation Settings")
    }
}
```

Add `onNavigateToSimulation: () -> Unit` parameter to `AccountScreen` composable. Wire it in `NavGraph.kt` to navigate to `Routes.SIMULATION`.

- [ ] **Step 9: Verify build**

Run: `./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/debug/ app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt app/src/main/java/com/hrcoach/ui/setup/ app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(sim): add simulation overlay, debug screen, setup badge, and nav wiring"
```

---

## Task 11: BatchSimulator

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/simulation/BatchSimulator.kt`
- Create: `app/src/test/java/com/hrcoach/service/simulation/BatchSimulatorTest.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/debug/DebugSimulationScreen.kt` (add batch section)
- Modify: `app/src/main/java/com/hrcoach/ui/debug/DebugSimulationViewModel.kt` (add batch logic)

**Depends on:** Tasks 3-6

- [ ] **Step 1: Write failing test**

```kotlin
package com.hrcoach.service.simulation

import com.hrcoach.domain.simulation.*
import com.hrcoach.domain.engine.AdaptivePaceController
import com.hrcoach.domain.engine.ZoneEngine
import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import org.junit.Assert.*
import org.junit.Test

class BatchSimulatorTest {

    @Test
    fun `simulateSingleRun produces track of ticks`() {
        val scenario = SimulationScenario.EASY_STEADY_RUN
        val config = WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            targetHr = 140,
            bufferBpm = 5
        )
        val profile = AdaptiveProfile()

        val result = BatchSimulator.simulateSingleRun(
            scenario = scenario,
            config = config,
            initialProfile = profile
        )

        assertTrue("should have ticks", result.tickCount > 0)
        assertTrue("should have duration", result.durationSeconds > 0)
        assertNotNull("should have updated profile", result.updatedProfile)
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.simulation.BatchSimulatorTest" 2>&1 | tail -10`
Expected: FAIL

- [ ] **Step 3: Implement `BatchSimulator`**

The batch simulator drives domain logic synchronously using discrete clock steps:

```kotlin
package com.hrcoach.service.simulation

import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.engine.AdaptivePaceController
import com.hrcoach.domain.engine.MetricsCalculator
import com.hrcoach.domain.engine.ZoneEngine
import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.domain.simulation.SimulationScenario
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

data class SingleRunResult(
    val tickCount: Int,
    val durationSeconds: Int,
    val updatedProfile: AdaptiveProfile,
    val avgHr: Int,
    val finalDistance: Float
)

data class BatchResult(
    val runs: List<SingleRunResult>,
    val finalProfile: AdaptiveProfile
)

class BatchSimulator @Inject constructor(
    private val adaptiveProfileRepository: AdaptiveProfileRepository,
    private val workoutRepository: WorkoutRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository
) {
    companion object {
        private const val TICK_INTERVAL_MS = 2_000L

        /** Pure function: simulate one run without DB access. For unit testing. */
        fun simulateSingleRun(
            scenario: SimulationScenario,
            config: WorkoutConfig,
            initialProfile: AdaptiveProfile
        ): SingleRunResult {
            val clock = SimulationClock(MutableStateFlow(1f))
            val hrSource = SimulatedHrSource(clock, scenario)
            val locSource = SimulatedLocationSource(clock, scenario)

            val zoneEngine = ZoneEngine(config)
            val adaptive = AdaptivePaceController(config, initialProfile)

            locSource.start()
            val startMs = clock.now()
            var tickCount = 0
            var hrSum = 0L
            var hrCount = 0

            val totalTicks = (scenario.durationSeconds * 1000L) / TICK_INTERVAL_MS

            for (i in 0 until totalTicks) {
                clock.advanceBy(TICK_INTERVAL_MS)
                val elapsedSec = (clock.now() - startMs) / 1000f

                hrSource.updateForTime(elapsedSec)
                locSource.updateForTime(elapsedSec)

                val hr = hrSource.heartRate.value
                val connected = hrSource.isConnected.value
                val target = if (config.isTimeBased()) {
                    config.targetHrAtElapsedSeconds(elapsedSec.toLong())
                } else {
                    config.targetHrAtDistance(locSource.distanceMeters.value)
                }

                val zoneStatus = if (!connected || hr <= 0 || target == null || target == 0) {
                    ZoneStatus.NO_DATA
                } else {
                    zoneEngine.evaluate(hr, target)
                }

                adaptive.evaluateTick(
                    nowMs = clock.now(),
                    hr = hr,
                    connected = connected,
                    targetHr = target,
                    distanceMeters = locSource.distanceMeters.value,
                    actualZone = zoneStatus
                )

                if (connected && hr > 0) {
                    hrSum += hr
                    hrCount++
                }
                tickCount++
            }

            locSource.stop()
            val session = adaptive.finishSession(workoutId = 0, endedAtMs = clock.now())

            return SingleRunResult(
                tickCount = tickCount,
                durationSeconds = scenario.durationSeconds,
                updatedProfile = session?.updatedProfile ?: initialProfile,
                avgHr = if (hrCount > 0) (hrSum / hrCount).toInt() else 0,
                finalDistance = locSource.distanceMeters.value
            )
        }
    }

    suspend fun runBatch(
        scenarios: List<SimulationScenario>,
        config: WorkoutConfig,
        onProgress: (completed: Int, total: Int) -> Unit
    ): BatchResult {
        var profile = adaptiveProfileRepository.getProfile()
        val results = mutableListOf<SingleRunResult>()

        for ((index, scenario) in scenarios.withIndex()) {
            val result = simulateSingleRun(scenario, config, profile)
            profile = result.updatedProfile
            adaptiveProfileRepository.saveProfile(profile)
            results.add(result)
            onProgress(index + 1, scenarios.size)
        }

        return BatchResult(runs = results, finalProfile = profile)
    }
}
```

- [ ] **Step 4: Run test, verify passes**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.service.simulation.BatchSimulatorTest" 2>&1 | tail -10`
Expected: PASS

- [ ] **Step 5: Add batch section to debug screen and ViewModel**

Add to `DebugSimUiState`:
```kotlin
val batchCount: Int = 5,
val isBatchRunning: Boolean = false,
val batchProgress: Int = 0,
val batchResult: BatchResult? = null
```

Add to `DebugSimulationViewModel`:
```kotlin
@Inject lateinit var batchSimulator: BatchSimulator

fun runBatch() {
    val state = _uiState.value
    val scenario = state.scenarios[state.selectedScenarioIndex]
    val config = WorkoutConfig(mode = WorkoutMode.STEADY_STATE, targetHr = 140, bufferBpm = 5)
    _uiState.value = state.copy(isBatchRunning = true, batchProgress = 0)

    viewModelScope.launch {
        val result = batchSimulator.runBatch(
            scenarios = List(state.batchCount) { scenario },
            config = config,
            onProgress = { done, total ->
                _uiState.value = _uiState.value.copy(batchProgress = done)
            }
        )
        _uiState.value = _uiState.value.copy(
            isBatchRunning = false,
            batchResult = result
        )
    }
}
```

Add batch UI section to `DebugSimulationScreen`: count picker, "Run Batch" button, progress bar, results summary.

- [ ] **Step 6: Verify build**

Run: `./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/simulation/BatchSimulator.kt app/src/test/java/com/hrcoach/service/simulation/BatchSimulatorTest.kt app/src/main/java/com/hrcoach/ui/debug/
git commit -m "feat(sim): add BatchSimulator for headless multi-run adaptation testing"
```

---

## Task 12: Final Verification

- [ ] **Step 1: Run all unit tests**

Run: `./gradlew testDebugUnitTest 2>&1 | tail -20`
Expected: All tests PASS (existing + new simulation tests)

- [ ] **Step 2: Build debug APK**

Run: `./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify no `System.currentTimeMillis()` in service**

Run: `grep -rn "System.currentTimeMillis" app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`
Expected: No matches

- [ ] **Step 4: Run lint**

Run: `./gradlew lint 2>&1 | tail -10`
Expected: No new errors

- [ ] **Step 5: Commit any final fixes**

```bash
git add -A
git commit -m "feat(sim): final verification and cleanup"
```
