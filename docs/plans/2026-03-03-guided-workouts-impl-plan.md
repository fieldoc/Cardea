# Guided Workout Presets — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the freeform distance-profile segment builder with a preset library of 8 scientifically-backed workouts (Zone 2, Tempo, LT, Norwegian 4×4, HIIT 30/30, Hill Repeats, Half Marathon Prep, Marathon Prep), with Cardea-native glass cards, segment timeline visualization, lazy HRmax onboarding, and interval countdown in the active workout screen.

**Architecture:** `PresetLibrary` (domain object) resolves presets to `WorkoutConfig` given HRmax stored in `UserProfileRepository`. `WorkoutForegroundService` tracks `workoutStartMs` and routes time-based workouts by elapsed seconds. `CoachingEventRouter` detects time-based segment transitions. `WorkoutViewModel` derives `segmentLabel`/`segmentCountdown`/`nextSegmentLabel` from its existing `workoutConfig + elapsedSeconds` — no `WorkoutSnapshot` changes needed. `SetupScreen` shows a `PresetGrid` with `GlassCard` + gradient border on selection and a Canvas-drawn `SegmentTimelineStrip`. `AccountScreen` gets a maxHr profile row.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room/GSON, SharedPreferences, JUnit 4.

**Status of existing plan (`2026-03-01-preset-workout-profiles.md`):** Tasks 1–2 already done in commit `fd3d9d9`. This plan covers Tasks 3–13 with Approach B UX amendments.

---

## Task 3: ZoneEngine direct-target overload

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/engine/ZoneEngine.kt`
- Test: `app/src/test/java/com/hrcoach/domain/engine/ZoneEngineTest.kt`

**Step 1: Write failing test**

Add to `ZoneEngineTest.kt` after the last `@Test`:

```kotlin
@Test
fun `evaluate with explicit targetHr respects buffer`() {
    val config = WorkoutConfig(
        mode = WorkoutMode.STEADY_STATE,
        steadyStateTargetHr = 160,
        bufferBpm = 5
    )
    val engine = ZoneEngine(config)
    assertEquals(ZoneStatus.IN_ZONE,    engine.evaluate(hr = 155, targetHr = 160))
    assertEquals(ZoneStatus.IN_ZONE,    engine.evaluate(hr = 165, targetHr = 160))
    assertEquals(ZoneStatus.BELOW_ZONE, engine.evaluate(hr = 154, targetHr = 160))
    assertEquals(ZoneStatus.ABOVE_ZONE, engine.evaluate(hr = 166, targetHr = 160))
}
```

**Step 2: Run to verify it fails**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.ZoneEngineTest.evaluate with explicit targetHr respects buffer" 2>&1 | tail -10
```

Expected: FAIL — `evaluate(hr, targetHr)` overload doesn't exist.

**Step 3: Add overload to ZoneEngine.kt**

Replace the entire file:

```kotlin
package com.hrcoach.domain.engine

import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.ZoneStatus

class ZoneEngine(private val config: WorkoutConfig) {

    fun evaluate(hr: Int, distanceMeters: Float): ZoneStatus {
        val target = config.targetHrAtDistance(distanceMeters) ?: return ZoneStatus.NO_DATA
        return evaluate(hr, target)
    }

    fun evaluate(hr: Int, targetHr: Int): ZoneStatus {
        val low  = targetHr - config.bufferBpm
        val high = targetHr + config.bufferBpm
        return when {
            hr < low  -> ZoneStatus.BELOW_ZONE
            hr > high -> ZoneStatus.ABOVE_ZONE
            else      -> ZoneStatus.IN_ZONE
        }
    }
}
```

**Step 4: Run all tests**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.ZoneEngineTest" 2>&1 | tail -20
```

Expected: All PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/engine/ZoneEngine.kt \
        app/src/test/java/com/hrcoach/domain/engine/ZoneEngineTest.kt
git commit -m "feat: add direct-target evaluate overload to ZoneEngine"
```

---

## Task 4: UserProfileRepository

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt`

No Hilt binding needed — `@Inject constructor` with `@Singleton` auto-discovered like `MapsSettingsRepository`.

**Step 1: Create the file**

```kotlin
package com.hrcoach.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "hr_coach_user_profile"
        private const val PREF_MAX_HR = "max_hr"
        private const val UNSET = -1
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getMaxHr(): Int? {
        val stored = prefs.getInt(PREF_MAX_HR, UNSET)
        return if (stored == UNSET) null else stored
    }

    @Synchronized
    fun setMaxHr(maxHr: Int) {
        require(maxHr in 100..220) { "maxHr must be in 100..220" }
        prefs.edit().putInt(PREF_MAX_HR, maxHr).apply()
    }
}
```

**Step 2: Build to verify**

```
./gradlew assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt
git commit -m "feat: add UserProfileRepository for persisting user max HR"
```

---

## Task 5: Create PresetLibrary

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/preset/WorkoutPreset.kt`
- Create: `app/src/main/java/com/hrcoach/domain/preset/PresetLibrary.kt`
- Create: `app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt`

**Step 1: Write failing tests**

Create `app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt`:

```kotlin
package com.hrcoach.domain.preset

import com.hrcoach.domain.model.WorkoutMode
import org.junit.Assert.*
import org.junit.Test

class PresetLibraryTest {

    @Test
    fun `all presets resolve to valid WorkoutConfig given maxHr 180`() {
        val maxHr = 180
        PresetLibrary.ALL.forEach { preset ->
            val config = preset.buildConfig(maxHr)
            assertNotNull("Preset ${preset.id} returned null config", config)
            config!!
            when {
                config.mode == WorkoutMode.STEADY_STATE -> {
                    val hr = config.steadyStateTargetHr!!
                    assertTrue("${preset.id} steadyState HR $hr out of range", hr in 80..200)
                }
                config.isTimeBased() -> {
                    config.segments.forEach { seg ->
                        assertTrue("${preset.id} seg HR ${seg.targetHr} out of range",
                            seg.targetHr in 80..200)
                        assertTrue("${preset.id} seg must have durationSeconds",
                            seg.durationSeconds != null && seg.durationSeconds > 0)
                    }
                }
                else -> {
                    config.segments.forEach { seg ->
                        assertTrue("${preset.id} seg HR ${seg.targetHr} out of range",
                            seg.targetHr in 80..200)
                        assertNotNull("${preset.id} distance seg must have distanceMeters",
                            seg.distanceMeters)
                    }
                }
            }
        }
    }

    @Test
    fun `zone2 base - target HR is 68 percent of maxHr`() {
        val config = PresetLibrary.ALL.first { it.id == "zone2_base" }.buildConfig(180)!!
        assertEquals(WorkoutMode.STEADY_STATE, config.mode)
        assertEquals((180 * 0.68).toInt(), config.steadyStateTargetHr)
    }

    @Test
    fun `norwegian 4x4 - has 4 work intervals of 240s each`() {
        val config = PresetLibrary.ALL.first { it.id == "norwegian_4x4" }.buildConfig(180)!!
        assertTrue(config.isTimeBased())
        val intervals = config.segments.filter { it.durationSeconds == 240 }
        assertEquals(4, intervals.size)
        intervals.forEach { assertTrue(it.targetHr in 155..175) }
    }

    @Test
    fun `half marathon prep - last segment ends at 21100 meters`() {
        val config = PresetLibrary.ALL.first { it.id == "half_marathon_prep" }.buildConfig(180)!!
        assertFalse(config.isTimeBased())
        assertEquals(21100f, config.segments.last().distanceMeters)
    }

    @Test
    fun `preset IDs are unique`() {
        val ids = PresetLibrary.ALL.map { it.id }
        assertEquals(ids.distinct().size, ids.size)
    }
}
```

**Step 2: Run to verify they fail**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.PresetLibraryTest" 2>&1 | tail -10
```

Expected: FAIL — `PresetLibrary` doesn't exist.

**Step 3: Create WorkoutPreset.kt**

```kotlin
package com.hrcoach.domain.preset

import com.hrcoach.domain.model.WorkoutConfig

data class WorkoutPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val category: PresetCategory,
    val durationLabel: String,
    val intensityLabel: String,
    val buildConfig: (maxHr: Int) -> WorkoutConfig
)

enum class PresetCategory {
    BASE_AEROBIC,
    THRESHOLD,
    INTERVAL,
    RACE_PREP
}
```

**Step 4: Create PresetLibrary.kt**

```kotlin
package com.hrcoach.domain.preset

import com.hrcoach.domain.model.HrSegment
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import kotlin.math.roundToInt

object PresetLibrary {

    val ALL: List<WorkoutPreset> = listOf(
        zone2Base(), aeroTempo(), lactateThreshold(),
        norwegian4x4(), hiit3030(), hillRepeats(),
        halfMarathonPrep(), marathonPrep()
    )

    private fun zone2Base() = WorkoutPreset(
        id = "zone2_base",
        name = "Zone 2 Base",
        subtitle = "Aerobic foundation",
        description = "45–75 min at 68% max HR. Builds mitochondrial density and fat oxidation (Seiler polarized model).",
        category = PresetCategory.BASE_AEROBIC,
        durationLabel = "45–75 min",
        intensityLabel = "Easy",
        buildConfig = { maxHr ->
            WorkoutConfig(
                mode = WorkoutMode.STEADY_STATE,
                steadyStateTargetHr = (maxHr * 0.68).roundToInt(),
                bufferBpm = 5,
                presetId = "zone2_base"
            )
        }
    )

    private fun aeroTempo() = WorkoutPreset(
        id = "aerobic_tempo",
        name = "Aerobic Tempo",
        subtitle = "Comfortably hard",
        description = "30 min at 84% max HR. Raises lactate threshold and improves running economy.",
        category = PresetCategory.THRESHOLD,
        durationLabel = "30 min",
        intensityLabel = "Moderate",
        buildConfig = { maxHr ->
            WorkoutConfig(
                mode = WorkoutMode.STEADY_STATE,
                steadyStateTargetHr = (maxHr * 0.84).roundToInt(),
                bufferBpm = 4,
                presetId = "aerobic_tempo"
            )
        }
    )

    private fun lactateThreshold() = WorkoutPreset(
        id = "lactate_threshold",
        name = "Lactate Threshold",
        subtitle = "Threshold effort",
        description = "25 min at 90% max HR. Targets the lactate threshold directly (Stöggl & Sperlich).",
        category = PresetCategory.THRESHOLD,
        durationLabel = "25 min",
        intensityLabel = "Hard",
        buildConfig = { maxHr ->
            WorkoutConfig(
                mode = WorkoutMode.STEADY_STATE,
                steadyStateTargetHr = (maxHr * 0.90).roundToInt(),
                bufferBpm = 3,
                presetId = "lactate_threshold"
            )
        }
    )

    private fun norwegian4x4() = WorkoutPreset(
        id = "norwegian_4x4",
        name = "Norwegian 4x4",
        subtitle = "VO2max booster",
        description = "4 x 4-min intervals at 90-95% max HR with 3-min active recovery (Helgerud et al. 2007).",
        category = PresetCategory.INTERVAL,
        durationLabel = "~35 min",
        intensityLabel = "Very High",
        buildConfig = { maxHr ->
            val intervalHr  = (maxHr * 0.92).roundToInt()
            val recoveryHr  = (maxHr * 0.65).roundToInt()
            val warmupHr    = (maxHr * 0.65).roundToInt()
            val cooldownHr  = (maxHr * 0.60).roundToInt()
            val segs = mutableListOf<HrSegment>()
            segs += HrSegment(durationSeconds = 600, targetHr = warmupHr,  label = "Warm-up")
            repeat(4) { i ->
                segs += HrSegment(durationSeconds = 240, targetHr = intervalHr, label = "Interval ${i+1} of 4")
                if (i < 3) segs += HrSegment(durationSeconds = 180, targetHr = recoveryHr, label = "Recovery ${i+1}")
            }
            segs += HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down")
            WorkoutConfig(mode = WorkoutMode.DISTANCE_PROFILE, segments = segs, bufferBpm = 5, presetId = "norwegian_4x4")
        }
    )

    private fun hiit3030() = WorkoutPreset(
        id = "hiit_30_30",
        name = "HIIT 30/30",
        subtitle = "Sprint intervals",
        description = "10 x 30-sec sprints at 92% max HR with 30-sec recoveries. High anaerobic stimulus.",
        category = PresetCategory.INTERVAL,
        durationLabel = "~20 min",
        intensityLabel = "Very High",
        buildConfig = { maxHr ->
            val sprintHr   = (maxHr * 0.92).roundToInt()
            val recoveryHr = (maxHr * 0.62).roundToInt()
            val warmupHr   = (maxHr * 0.65).roundToInt()
            val cooldownHr = (maxHr * 0.60).roundToInt()
            val segs = mutableListOf<HrSegment>()
            segs += HrSegment(durationSeconds = 300, targetHr = warmupHr,  label = "Warm-up")
            repeat(10) { i ->
                segs += HrSegment(durationSeconds = 30, targetHr = sprintHr,   label = "Sprint ${i+1} of 10")
                segs += HrSegment(durationSeconds = 30, targetHr = recoveryHr, label = "Recover")
            }
            segs += HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down")
            WorkoutConfig(mode = WorkoutMode.DISTANCE_PROFILE, segments = segs, bufferBpm = 5, presetId = "hiit_30_30")
        }
    )

    private fun hillRepeats() = WorkoutPreset(
        id = "hill_repeats",
        name = "Hill Repeats",
        subtitle = "Strength + power",
        description = "6 x 90-sec hill climbs at 87% max HR with 90-sec flat recovery.",
        category = PresetCategory.INTERVAL,
        durationLabel = "~25 min",
        intensityLabel = "High",
        buildConfig = { maxHr ->
            val climbHr    = (maxHr * 0.87).roundToInt()
            val recoveryHr = (maxHr * 0.63).roundToInt()
            val warmupHr   = (maxHr * 0.65).roundToInt()
            val cooldownHr = (maxHr * 0.60).roundToInt()
            val segs = mutableListOf<HrSegment>()
            segs += HrSegment(durationSeconds = 300, targetHr = warmupHr,  label = "Warm-up")
            repeat(6) { i ->
                segs += HrSegment(durationSeconds = 90, targetHr = climbHr,    label = "Hill ${i+1} of 6")
                segs += HrSegment(durationSeconds = 90, targetHr = recoveryHr, label = "Recover")
            }
            segs += HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down")
            WorkoutConfig(mode = WorkoutMode.DISTANCE_PROFILE, segments = segs, bufferBpm = 5, presetId = "hill_repeats")
        }
    )

    private fun halfMarathonPrep() = WorkoutPreset(
        id = "half_marathon_prep",
        name = "Half Marathon Prep",
        subtitle = "Race simulation",
        description = "3 progressive HR zones across 21.1 km: easy start, race pace, strong finish.",
        category = PresetCategory.RACE_PREP,
        durationLabel = "21.1 km",
        intensityLabel = "Moderate–Hard",
        buildConfig = { maxHr ->
            WorkoutConfig(
                mode = WorkoutMode.DISTANCE_PROFILE,
                segments = listOf(
                    HrSegment(distanceMeters = 3000f,  targetHr = (maxHr * 0.72).roundToInt(), label = "Easy Start"),
                    HrSegment(distanceMeters = 14000f, targetHr = (maxHr * 0.80).roundToInt(), label = "Race Pace"),
                    HrSegment(distanceMeters = 21100f, targetHr = (maxHr * 0.85).roundToInt(), label = "Strong Finish")
                ),
                bufferBpm = 4,
                presetId = "half_marathon_prep"
            )
        }
    )

    private fun marathonPrep() = WorkoutPreset(
        id = "marathon_prep",
        name = "Marathon Prep",
        subtitle = "Negative-split strategy",
        description = "Negative-split strategy across 42.2 km: conservative early, marathon pace, controlled push.",
        category = PresetCategory.RACE_PREP,
        durationLabel = "42.2 km",
        intensityLabel = "Moderate",
        buildConfig = { maxHr ->
            WorkoutConfig(
                mode = WorkoutMode.DISTANCE_PROFILE,
                segments = listOf(
                    HrSegment(distanceMeters = 10000f, targetHr = (maxHr * 0.70).roundToInt(), label = "Easy Start"),
                    HrSegment(distanceMeters = 32000f, targetHr = (maxHr * 0.75).roundToInt(), label = "Marathon Pace"),
                    HrSegment(distanceMeters = 42200f, targetHr = (maxHr * 0.78).roundToInt(), label = "Final Push")
                ),
                bufferBpm = 4,
                presetId = "marathon_prep"
            )
        }
    )
}
```

**Step 5: Run tests**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.PresetLibraryTest" 2>&1 | tail -20
```

Expected: All 5 tests PASS.

**Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/preset/ \
        app/src/test/java/com/hrcoach/domain/preset/
git commit -m "feat: add PresetLibrary with 8 scientifically-backed workout presets"
```

---

## Task 6: Add interval countdown to WorkoutViewModel

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/workout/WorkoutViewModel.kt`

`WorkoutViewModel` already has `workoutConfig: WorkoutConfig?` and `elapsedSeconds: Long`. We derive interval countdown state from them — no `WorkoutSnapshot` changes needed.

**Step 1: Extend `ActiveWorkoutUiState`**

Add three fields after `workoutConfig`:

```kotlin
data class ActiveWorkoutUiState(
    val snapshot: WorkoutSnapshot = WorkoutSnapshot(),
    val elapsedSeconds: Long = 0L,
    val workoutConfig: WorkoutConfig? = null,
    val segmentLabel: String? = null,          // current interval name, null for non-interval
    val segmentCountdownSeconds: Long? = null, // seconds until this segment ends
    val nextSegmentLabel: String? = null       // next segment name, null on last segment
)
```

**Step 2: Add helper function to WorkoutViewModel**

Add this private function inside `WorkoutViewModel`, after `computeElapsedSeconds`:

```kotlin
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
```

**Step 3: Update `_uiState` emissions to include segment info**

In `handleSnapshot`, replace the `_uiState.update` block (lines 94–100) with:

```kotlin
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
```

In the 1-second ticker loop (lines 52–59), replace the `_uiState.update` with:

```kotlin
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
```

In `loadActiveWorkoutMetadata`, after computing `config`, update the emit:

```kotlin
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
```

**Step 4: Build to verify**

```
./gradlew assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/WorkoutViewModel.kt
git commit -m "feat: derive segmentLabel and segmentCountdown in WorkoutViewModel"
```

---

## Task 7: WorkoutForegroundService — time-based HR routing

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

**Step 1: Add `workoutStartMs` field**

After line 83 (`private var workoutId: Long = 0L`), add:

```kotlin
private var workoutStartMs: Long = 0L
```

**Step 2: Set `workoutStartMs` in `startWorkout()`**

After line 164 (`workoutId = repository.createWorkout(...)`), add:

```kotlin
workoutStartMs = System.currentTimeMillis()
```

**Step 3: Update `processTick()` — replace target resolution**

Find (line 228):
```kotlin
val target = workoutConfig.targetHrAtDistance(tick.distanceMeters)
```

Replace with:
```kotlin
val elapsedSeconds = if (workoutStartMs > 0L) (nowMs - workoutStartMs) / 1000L else 0L
val target = if (workoutConfig.isTimeBased()) {
    workoutConfig.targetHrAtElapsedSeconds(elapsedSeconds)
} else {
    workoutConfig.targetHrAtDistance(tick.distanceMeters)
}
```

**Step 4: Update `processTick()` — use direct-target ZoneEngine overload**

Find (line 250):
```kotlin
engine.evaluate(tick.hr, tick.distanceMeters)
```

Replace with:
```kotlin
engine.evaluate(tick.hr, target ?: 0)
```

**Step 5: Pass `elapsedSeconds` to `coachingEventRouter.route()`**

Find the `coachingEventRouter.route(` call (line 292). Add `elapsedSeconds = elapsedSeconds,` as a new parameter after `distanceMeters = tick.distanceMeters,`.

**Step 6: Build to verify**

```
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL (will show compile error on `route()` until Task 8 adds the param).

**Step 7: Commit after Task 8 — hold this commit until Task 8 is done.**

---

## Task 8: CoachingEventRouter — time-based segment transitions

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/workout/CoachingEventRouter.kt`
- Modify: `app/src/test/java/com/hrcoach/service/workout/CoachingEventRouterTest.kt`

**Step 1: Write failing test**

Add to `CoachingEventRouterTest.kt`:

```kotlin
@Test
fun `time-based workout emits SEGMENT_CHANGE on interval transition`() {
    val router = CoachingEventRouter()
    val events = mutableListOf<CoachingEvent>()
    val config = WorkoutConfig(
        mode = WorkoutMode.DISTANCE_PROFILE,
        segments = listOf(
            HrSegment(durationSeconds = 300, targetHr = 120, label = "Warm-up"),
            HrSegment(durationSeconds = 240, targetHr = 165, label = "Interval 1")
        )
    )
    // elapsed=0: first segment, no change yet
    router.route(workoutConfig = config, connected = true, distanceMeters = 0f,
        elapsedSeconds = 0L, zoneStatus = ZoneStatus.IN_ZONE,
        adaptiveResult = null, guidance = "hold", nowMs = 0L,
        emitEvent = { e, _ -> events += e })
    assertTrue(CoachingEvent.SEGMENT_CHANGE !in events)

    // elapsed=300: crosses into segment 1
    router.route(workoutConfig = config, connected = true, distanceMeters = 0f,
        elapsedSeconds = 300L, zoneStatus = ZoneStatus.IN_ZONE,
        adaptiveResult = null, guidance = "hold", nowMs = 300_000L,
        emitEvent = { e, _ -> events += e })
    assertTrue(CoachingEvent.SEGMENT_CHANGE in events)
}
```

**Step 2: Run to verify it fails**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.service.workout.CoachingEventRouterTest" 2>&1 | tail -10
```

Expected: FAIL — `route()` has no `elapsedSeconds` parameter.

**Step 3: Update `CoachingEventRouter.kt`**

Replace the entire file:

```kotlin
package com.hrcoach.service.workout

import com.hrcoach.domain.engine.AdaptivePaceController
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus

class CoachingEventRouter {
    private var wasHrConnected: Boolean = false
    private var previousZoneStatus: ZoneStatus = ZoneStatus.NO_DATA
    private var lastSegmentIndex: Int = -1
    private var lastPredictiveWarningTime: Long = 0L

    fun reset() {
        wasHrConnected = false
        previousZoneStatus = ZoneStatus.NO_DATA
        lastSegmentIndex = -1
        lastPredictiveWarningTime = 0L
    }

    fun route(
        workoutConfig: WorkoutConfig,
        connected: Boolean,
        distanceMeters: Float,
        elapsedSeconds: Long,
        zoneStatus: ZoneStatus,
        adaptiveResult: AdaptivePaceController.TickResult?,
        guidance: String,
        nowMs: Long,
        emitEvent: (CoachingEvent, String?) -> Unit
    ) {
        if (wasHrConnected && !connected) {
            emitEvent(CoachingEvent.SIGNAL_LOST, null)
        } else if (!wasHrConnected && connected) {
            emitEvent(CoachingEvent.SIGNAL_REGAINED, null)
        }

        if (previousZoneStatus != ZoneStatus.IN_ZONE && zoneStatus == ZoneStatus.IN_ZONE) {
            emitEvent(CoachingEvent.RETURN_TO_ZONE, guidance)
        }

        if (workoutConfig.mode == WorkoutMode.DISTANCE_PROFILE) {
            val segmentIndex = if (workoutConfig.isTimeBased()) {
                segmentIndexByTime(workoutConfig, elapsedSeconds)
            } else {
                segmentIndexByDistance(workoutConfig, distanceMeters)
            }
            if (lastSegmentIndex >= 0 && segmentIndex >= 0 && segmentIndex != lastSegmentIndex) {
                emitEvent(CoachingEvent.SEGMENT_CHANGE, null)
            }
            lastSegmentIndex = segmentIndex
        } else {
            lastSegmentIndex = -1
        }

        val projectedDrift = adaptiveResult?.projectedZoneStatus == ZoneStatus.ABOVE_ZONE ||
            adaptiveResult?.projectedZoneStatus == ZoneStatus.BELOW_ZONE
        if (zoneStatus == ZoneStatus.IN_ZONE &&
            adaptiveResult?.hasProjectionConfidence == true &&
            projectedDrift &&
            nowMs - lastPredictiveWarningTime >= 60_000L
        ) {
            emitEvent(CoachingEvent.PREDICTIVE_WARNING, guidance)
            lastPredictiveWarningTime = nowMs
        }

        wasHrConnected = connected
        previousZoneStatus = zoneStatus
    }

    private fun segmentIndexByTime(config: WorkoutConfig, elapsedSeconds: Long): Int {
        if (config.segments.isEmpty()) return -1
        var cumulative = 0L
        config.segments.forEachIndexed { index, seg ->
            val dur = seg.durationSeconds?.toLong() ?: return@forEachIndexed
            cumulative += dur
            if (elapsedSeconds < cumulative) return index
        }
        return config.segments.lastIndex
    }

    private fun segmentIndexByDistance(config: WorkoutConfig, distanceMeters: Float): Int {
        if (config.segments.isEmpty()) return -1
        val index = config.segments.indexOfFirst { seg ->
            seg.distanceMeters?.let { d -> distanceMeters <= d } == true
        }
        return if (index >= 0) index else config.segments.lastIndex
    }
}
```

**Step 4: Update existing test call sites** — add `elapsedSeconds = 0L` to every existing `router.route(...)` call in `CoachingEventRouterTest.kt` that doesn't already have it.

**Step 5: Run all tests**

```
./gradlew testDebugUnitTest 2>&1 | tail -30
```

Expected: All PASS.

**Step 6: Commit Tasks 7 and 8 together**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt \
        app/src/main/java/com/hrcoach/service/workout/CoachingEventRouter.kt \
        app/src/test/java/com/hrcoach/service/workout/CoachingEventRouterTest.kt
git commit -m "feat: route time-based workouts by elapsed seconds in service and router"
```

---

## Task 9: SetupViewModel — preset selection + HRmax

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt`

**Step 1: Add new fields to `SetupUiState`**

After `val connectionError: String? = null,`, add:

```kotlin
val selectedPresetId: String? = null,
val showHrMaxDialog: Boolean = false,
val maxHrInput: String = "",
val maxHr: Int? = null,
val pendingPresetId: String? = null,
```

**Step 2: Add `UserProfileRepository` to constructor**

```kotlin
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val audioSettingsRepository: AudioSettingsRepository,
    private val mapsSettingsRepository: MapsSettingsRepository,
    private val bleCoordinator: BleConnectionCoordinator,
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {
```

**Step 3: Load maxHr in `init`**

After the line loading audio settings, add:

```kotlin
val storedMaxHr = userProfileRepository.getMaxHr()
_uiState.value = _uiState.value.copy(maxHr = storedMaxHr)
```

**Step 4: Add preset-selection functions**

Add these after `toggleAdvancedSettings()`:

```kotlin
fun selectPreset(presetId: String) {
    val maxHr = _uiState.value.maxHr
    if (maxHr == null) {
        _uiState.value = _uiState.value.copy(
            pendingPresetId = presetId,
            showHrMaxDialog = true,
            maxHrInput = ""
        )
    } else {
        _uiState.value = _uiState.value.copy(selectedPresetId = presetId)
        recomputeValidation()
    }
}

fun setMaxHrInput(value: String) {
    _uiState.value = _uiState.value.copy(maxHrInput = value)
}

fun confirmMaxHr(): Boolean {
    val value = _uiState.value.maxHrInput.toIntOrNull() ?: return false
    if (value !in 100..220) return false
    userProfileRepository.setMaxHr(value)
    val pendingId = _uiState.value.pendingPresetId
    _uiState.value = _uiState.value.copy(
        maxHr = value,
        showHrMaxDialog = false,
        pendingPresetId = null,
        selectedPresetId = pendingId,
        maxHrInput = ""
    )
    recomputeValidation()
    return true
}

fun dismissHrMaxDialog() {
    _uiState.value = _uiState.value.copy(
        showHrMaxDialog = false,
        pendingPresetId = null,
        maxHrInput = ""
    )
}
```

**Step 5: Update `buildConfigOrNull()` — add preset fast path**

At the top of `buildConfigOrNull()`, before the existing mode-switch, add:

```kotlin
// Fast path: preset is selected
val presetId = state.selectedPresetId
val maxHr = state.maxHr
if (presetId != null && presetId != "custom" && maxHr != null) {
    val preset = PresetLibrary.ALL.firstOrNull { it.id == presetId } ?: return null
    return preset.buildConfig(maxHr).copy(
        bufferBpm = buffer,
        alertDelaySec = delay,
        alertCooldownSec = cooldown
    )
}
```

**Step 6: Update `recomputeValidation()` — preset shortcut**

At the top of `recomputeValidation()`, before computing field errors, add:

```kotlin
val presetId = state.selectedPresetId
val maxHr = state.maxHr
if (presetId != null && presetId != "custom" && maxHr != null) {
    _uiState.value = state.copy(
        validation = SetupValidationState(canStartWorkout = true)
    )
    return
}
```

**Step 7: Build to verify**

```
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

**Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt
git commit -m "feat: add preset selection and HRmax onboarding to SetupViewModel"
```

---

## Task 10: SetupScreen — PresetGrid with Cardea glass cards + segment timeline

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`

**Step 1: Update mode selector label**

Find in `ModeSelector`:
```kotlin
Triple(WorkoutMode.DISTANCE_PROFILE, Icons.Default.Route, "Distance"),
```
Change `"Distance"` to `"Guided"`.

**Step 2: Replace the `DISTANCE_PROFILE` branch in `TargetCard`**

Find and replace the entire `WorkoutMode.DISTANCE_PROFILE -> { ... }` block with:

```kotlin
WorkoutMode.DISTANCE_PROFILE -> {
    PresetGrid(
        presets = PresetLibrary.ALL,
        selectedPresetId = state.selectedPresetId,
        onSelectPreset = { viewModel.selectPreset(it.id) }
    )
    if (state.selectedPresetId == "custom") {
        SegmentEditor(state = state, viewModel = viewModel)
    }
    TextButton(
        onClick = { viewModel.selectPreset("custom") },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Custom segment editor…") }
}
```

**Step 3: Extract segment editor into private composable**

Add this private composable (extracts the existing DISTANCE_PROFILE editing code):

```kotlin
@Composable
private fun SegmentEditor(state: SetupUiState, viewModel: SetupViewModel) {
    val segmentColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary
    )
    state.segments.forEachIndexed { index, segment ->
        val segmentError = state.validation.segments.getOrNull(index) ?: SegmentInputError()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, HrCoachThemeTokens.glassBorder, RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(88.dp)
                    .background(segmentColors[index % segmentColors.size], RoundedCornerShape(999.dp))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Segment ${index + 1}", style = MaterialTheme.typography.bodySmall,
                    color = HrCoachThemeTokens.subtleText)
                OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = segment.distanceKm,
                    onValueChange = { viewModel.updateSegmentDistance(index, it) },
                    singleLine = true, label = { Text("Distance (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = segmentError.distanceKm != null,
                    supportingText = { segmentError.distanceKm?.let { Text(it) } })
                OutlinedTextField(modifier = Modifier.fillMaxWidth(), value = segment.targetHr,
                    onValueChange = { viewModel.updateSegmentTarget(index, it) },
                    singleLine = true, label = { Text("HR (bpm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = segmentError.targetHr != null,
                    supportingText = { segmentError.targetHr?.let { Text(it) } })
            }
            if (state.segments.size > 1) {
                IconButton(onClick = { viewModel.removeSegment(index) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove segment",
                        tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    TextButton(onClick = viewModel::addSegment) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add Segment")
    }
}
```

**Step 4: Add `PresetGrid` composable**

```kotlin
@Composable
private fun PresetGrid(
    presets: List<WorkoutPreset>,
    selectedPresetId: String?,
    onSelectPreset: (WorkoutPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PresetCategory.entries.forEach { category ->
            val catPresets = presets.filter { it.category == category }
            if (catPresets.isEmpty()) return@forEach
            Text(
                text = category.displayName(),
                style = MaterialTheme.typography.labelSmall,
                color = HrCoachThemeTokens.subtleText,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
            catPresets.forEach { preset ->
                PresetCard(
                    preset = preset,
                    isSelected = preset.id == selectedPresetId,
                    onClick = { onSelectPreset(preset) }
                )
            }
        }
    }
}

private fun PresetCategory.displayName() = when (this) {
    PresetCategory.BASE_AEROBIC -> "BASE AEROBIC"
    PresetCategory.THRESHOLD    -> "THRESHOLD"
    PresetCategory.INTERVAL     -> "INTERVALS"
    PresetCategory.RACE_PREP    -> "RACE PREP"
}
```

**Step 5: Add `PresetCard` composable (GlassCard + gradient border on selection)**

```kotlin
@Composable
private fun PresetCard(
    preset: WorkoutPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderModifier = if (isSelected) {
        Modifier.border(
            width = 2.dp,
            brush = CardeaGradient,
            shape = RoundedCornerShape(18.dp)
        )
    } else Modifier

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clickable(onClick = onClick)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTextPrimary
                    )
                    Text(
                        preset.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = HrCoachThemeTokens.subtleText
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(preset.durationLabel, style = MaterialTheme.typography.labelSmall,
                        color = HrCoachThemeTokens.subtleText)
                    Text(preset.intensityLabel, style = MaterialTheme.typography.labelSmall,
                        color = CardeaTextPrimary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SegmentTimelineStrip(preset = preset)
        }
    }
}
```

**Step 6: Add `SegmentTimelineStrip` composable (Canvas-drawn)**

```kotlin
@Composable
private fun SegmentTimelineStrip(preset: WorkoutPreset) {
    // Use canonical maxHr=180 just for proportions and color mapping
    val config = remember(preset.id) { preset.buildConfig(180) }
    val segmentColor = remember(preset.id) { buildSegmentColors(config) }
    val totalDuration = remember(preset.id) {
        when {
            config.isTimeBased() -> config.segments.sumOf { it.durationSeconds?.toLong() ?: 0L }.toFloat()
            config.segments.isNotEmpty() -> config.segments.last().distanceMeters ?: 1f
            else -> 1f
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
    ) {
        if (totalDuration <= 0f) return@Canvas
        val gap = 2.dp.toPx()

        when {
            config.mode == WorkoutMode.STEADY_STATE -> {
                // Single solid bar
                val color = segmentColor.firstOrNull() ?: Color(0xFF2B8C6E)
                drawRoundRect(color = color, cornerRadius = CornerRadius(3.dp.toPx()))
            }
            config.isTimeBased() -> {
                var x = 0f
                config.segments.forEachIndexed { i, seg ->
                    val dur = seg.durationSeconds?.toLong() ?: return@forEachIndexed
                    val w = (dur / totalDuration) * size.width - if (i < config.segments.size - 1) gap else 0f
                    val color = segmentColor.getOrElse(i) { Color(0xFF2B8C6E) }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, 0f),
                        size = Size(w.coerceAtLeast(2f), size.height),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                    x += w + gap
                }
            }
            else -> {
                // Distance-based: blocks proportional to segment spans
                var prevDist = 0f
                config.segments.forEachIndexed { i, seg ->
                    val endDist = seg.distanceMeters ?: return@forEachIndexed
                    val span = endDist - prevDist
                    val w = (span / totalDuration) * size.width - if (i < config.segments.size - 1) gap else 0f
                    val x = (prevDist / totalDuration) * size.width
                    val color = segmentColor.getOrElse(i) { Color(0xFF2B8C6E) }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, 0f),
                        size = Size(w.coerceAtLeast(2f), size.height),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                    prevDist = endDist
                }
            }
        }
    }
}

/** Map segment targetHr (vs canonical 180) to a display color. */
private fun buildSegmentColors(config: WorkoutConfig): List<Color> {
    val canonicalMaxHr = 180f
    return when {
        config.mode == WorkoutMode.STEADY_STATE ->
            listOf(hrPercentColor((config.steadyStateTargetHr ?: 120) / canonicalMaxHr))
        else -> config.segments.map { seg -> hrPercentColor(seg.targetHr / canonicalMaxHr) }
    }
}

private fun hrPercentColor(pct: Float) = when {
    pct >= 0.85f -> Color(0xFFFF5A5F)  // high — Cardea red
    pct >= 0.75f -> Color(0xFFE8A838)  // moderate — amber
    else         -> Color(0xFF2B8C6E)  // low — green
}
```

**Step 7: Add HRmax dialog to `SetupScreen`**

Inside `SetupScreen`, before the closing `}` of the `Scaffold` block, add:

```kotlin
if (state.showHrMaxDialog) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissHrMaxDialog() },
        title = { Text("Your Max Heart Rate") },
        text = {
            Column {
                Text("Presets use % of your max HR to personalise targets.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.maxHrInput,
                    onValueChange = { viewModel.setMaxHrInput(it) },
                    label = { Text("Max HR (bpm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    placeholder = { Text("e.g. 185") }
                )
                Text(
                    text = "Tip: 220 − age is a rough guide. A field test gives better results.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HrCoachThemeTokens.subtleText,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardeaGradient)
                    .clickable { viewModel.confirmMaxHr() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("Confirm", color = CardeaTextPrimary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissHrMaxDialog() }) { Text("Cancel") }
        }
    )
}
```

**Step 8: Add required imports**

Add to SetupScreen.kt imports:
```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import com.hrcoach.domain.preset.PresetCategory
import com.hrcoach.domain.preset.PresetLibrary
import com.hrcoach.domain.preset.WorkoutPreset
```

**Step 9: Build to verify**

```
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL. Fix any missing imports.

**Step 10: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt
git commit -m "feat: preset grid with Cardea glass cards and segment timeline in SetupScreen"
```

---

## Task 11: ActiveWorkoutScreen — interval countdown panel

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt`

**Step 1: Add interval countdown panel**

In `ActiveWorkoutScreen`, `uiState` already has `segmentLabel`, `segmentCountdownSeconds`, `nextSegmentLabel`. Add the interval panel just before the `GlassCard` stats row (after `GuidanceCard`), around line 192:

```kotlin
// Interval countdown panel — only for time-based workouts
if (uiState.segmentLabel != null) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        val mm = (uiState.segmentCountdownSeconds ?: 0L) / 60
        val ss = (uiState.segmentCountdownSeconds ?: 0L) % 60
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = uiState.segmentLabel!!,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTextPrimary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Timer,
                    contentDescription = null,
                    tint = HrCoachThemeTokens.subtleText,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "%d:%02d remaining".format(mm, ss),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CardeaTextPrimary
                )
            }
            uiState.nextSegmentLabel?.let { next ->
                Text(
                    text = "next › $next",
                    style = MaterialTheme.typography.bodySmall,
                    color = HrCoachThemeTokens.subtleText
                )
            }
        }
    }
}
```

Note: `Icons.Default.Timer` requires `import androidx.compose.material.icons.Icons` and `import androidx.compose.material.icons.filled.Timer` — add these to the imports.

**Step 2: Build to verify**

```
./gradlew assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt
git commit -m "feat: show interval countdown and next segment in active workout screen"
```

---

## Task 12: AccountScreen — maxHr profile row

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`

**Step 1: Update `AccountUiState`**

Add after `val mapsApiKey`:

```kotlin
val maxHr: Int? = null,
val maxHrInput: String = "",
val maxHrSaved: Boolean = false,
```

**Step 2: Update `AccountViewModel`**

Add `UserProfileRepository` to constructor:

```kotlin
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val audioRepo: AudioSettingsRepository,
    private val mapsRepo: MapsSettingsRepository,
    private val workoutRepo: WorkoutRepository,
    private val userProfileRepo: UserProfileRepository
) : ViewModel() {
```

Add a `MutableStateFlow` for maxHr:

```kotlin
private val _maxHr      = MutableStateFlow<Int?>(null)
private val _maxHrInput = MutableStateFlow("")
private val _maxHrSaved = MutableStateFlow(false)
```

In `init`, add after loading audio settings:

```kotlin
_maxHr.value = userProfileRepo.getMaxHr()
_maxHrInput.value = _maxHr.value?.toString() ?: ""
```

Update `uiState` combine to include `_maxHr`, `_maxHrInput`, `_maxHrSaved` — add them to the combine or use a separate `map`. Simplest: combine currently uses 5 flows; restructure using `combine` with 5 + map:

Replace the `uiState` definition:

```kotlin
val uiState: StateFlow<AccountUiState> = combine(
    workoutRepo.getAllWorkouts().map { it.size },
    _mapsKey, _mapsKeySaved, _volume, _verbosity
) { count, key, saved, vol, verb ->
    AccountUiState(
        totalWorkouts   = count,
        mapsApiKey      = key,
        mapsApiKeySaved = saved,
        earconVolume    = vol,
        voiceVerbosity  = verb,
        enableVibration = _vibration.value,
        maxHr           = _maxHr.value,
        maxHrInput      = _maxHrInput.value,
        maxHrSaved      = _maxHrSaved.value
    )
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())
```

Add functions:

```kotlin
fun setMaxHrInput(value: String) {
    _maxHrInput.value = value
    _maxHrSaved.value = false
}

fun saveMaxHr() {
    val value = _maxHrInput.value.toIntOrNull() ?: return
    if (value !in 100..220) return
    userProfileRepo.setMaxHr(value)
    _maxHr.value = value
    _maxHrSaved.value = true
}
```

**Step 3: Add maxHr row to AccountScreen**

In `AccountScreen.kt`, add a new `GlassCard` block **after** the Maps card (after line 135):

```kotlin
// Profile — Max HR
GlassCard(modifier = Modifier.fillMaxWidth()) {
    Text("Profile", style = MaterialTheme.typography.titleMedium, color = Color.White)
    Spacer(modifier = Modifier.height(4.dp))
    Text("Max Heart Rate", style = MaterialTheme.typography.bodyMedium, color = Color.White)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = state.maxHrInput,
            onValueChange = viewModel::setMaxHrInput,
            singleLine = true,
            label = { Text("Max HR (bpm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("e.g. 185") }
        )
        TextButton(onClick = viewModel::saveMaxHr) { Text("Save") }
    }
    Text(
        text = if (state.maxHrSaved) "Saved." else "Used to personalise all preset HR targets.",
        style = MaterialTheme.typography.bodySmall,
        color = CardeaTextSecondary
    )
}
```

Add missing import in AccountScreen.kt:
```kotlin
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
```

**Step 4: Build to verify**

```
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt \
        app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt
git commit -m "feat: add max heart rate profile row to AccountScreen"
```

---

## Task 13: Full test sweep + build verification

**Step 1: Run all unit tests**

```
./gradlew testDebugUnitTest 2>&1 | tail -40
```

Expected: ALL PASS. New tests: `PresetLibraryTest` (5), `ZoneEngineTest` additions (2), `CoachingEventRouterTest` addition (1). Fix any failures before proceeding.

**Step 2: Build debug APK**

```
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

**Step 3: Fix any compilation errors**

Common issues to watch for:
- Missing `import androidx.compose.material.icons.filled.Timer`
- `combine` in `AccountViewModel` only accepts 5 flows max — if adding more breaks it, use `.combine(...).combine(...)` chaining
- `GlassCard` `modifier` parameter — check the composable signature accepts a `Modifier` param

**Step 4: Final commit**

```bash
git add -A
git commit -m "chore: finalize guided workout presets feature"
```

---

## Implementation Notes

### WorkoutConfig.presetId field
`WorkoutConfig` needs a `presetId: String? = null` field for `WorkoutViewModel` to look up the config from `PresetLibrary`. Verify this was added in commit `fd3d9d9` by checking `WorkoutConfig.kt`. If missing, add it.

### GSON backward compatibility
Existing stored `WorkoutConfig` JSON in Room has `"distanceMeters": 1500.0` in segments. Nullable `Float?` is deserialized correctly by GSON. Old workouts continue to work.

### No new WorkoutMode enum value
`DISTANCE_PROFILE` covers both distance-based and time-based guided workouts. `isTimeBased()` distinguishes them at runtime. This avoids breaking existing `WorkoutEntity` records.

### Combine flow limit
Kotlin `combine` maxes at 5 flows. `AccountViewModel` currently combines 5. Adding `_maxHr`, `_maxHrInput`, `_maxHrSaved` exceeds this. Chain combines: `combine(combine(a,b,c,d,e) {...}, _maxHr, _maxHrInput) { base, hr, input -> ... }`.
