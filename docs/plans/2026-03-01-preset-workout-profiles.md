# Preset Workout Profiles Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the freeform distance-profile segment builder with a scientifically-backed preset library of 8 workout types (Zone 2 base, tempo, Norwegian 4×4, HIIT, lactate threshold, hill repeats, half marathon prep, marathon prep), while adding time-based segment support to enable interval workouts.

**Architecture:** Extend `HrSegment` with nullable `durationSeconds` and `label` fields (making `distanceMeters` nullable), add `targetHrAtElapsedSeconds()` to `WorkoutConfig`, create a `PresetLibrary` that resolves presets to `WorkoutConfig` given the user's HRmax. A new `UserProfileRepository` persists HRmax. The service tracks `workoutStartMs` and dispatches to either time- or distance-based routing per tick. The Setup UI gains a preset card grid as the primary interaction surface.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room/GSON (backward-compatible via nullable field addition), SharedPreferences, JUnit 4.

---

## Scientific Basis for Presets

All targets expressed as % of HRmax (user-supplied). Sources: Norwegian 4×4 (Helgerud et al. 2007, VO2max improvements), Zone 2 (Seiler low-intensity polarized training), lactate threshold (Stöggl & Sperlich 2014), Tabata/HIIT (Tabata et al. 1996).

| Preset | Mode | Target HR | Duration/Distance |
|---|---|---|---|
| Zone 2 Base | Steady State | 68% HRmax | 45 min sustained |
| Aerobic Tempo | Steady State | 84% HRmax | 30 min sustained |
| Lactate Threshold | Steady State | 90% HRmax | 25 min sustained |
| Norwegian 4×4 | Time-based | 4×(4min@92% / 3min@65%) | ~35 min total |
| HIIT 30/30 | Time-based | 10×(30s@92% / 30s@62%) | ~20 min total |
| Hill Repeats | Time-based | 6×(90s@87% / 90s@63%) | ~25 min total |
| Half Marathon Prep | Distance-based | 72→80→85% progressive | 21.1 km |
| Marathon Prep | Distance-based | 70→75→78% progressive | 42.2 km |

---

## Task 1: Extend HrSegment model

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/model/HrSegment.kt`
- Test: `app/src/test/java/com/hrcoach/domain/engine/ZoneEngineTest.kt`

**Step 1: Write the failing test**

Add to `ZoneEngineTest.kt` after the last `@Test`:

```kotlin
@Test
fun `distance profile - segment with null distanceMeters is ignored by distance lookup`() {
    val config = WorkoutConfig(
        mode = WorkoutMode.DISTANCE_PROFILE,
        segments = listOf(
            HrSegment(durationSeconds = 240, targetHr = 160, label = "Interval 1")
        ),
        bufferBpm = 5
    )
    // Time-based segment: targetHrAtDistance should return null (no distance milestones)
    assertEquals(null, config.targetHrAtDistance(500f))
}
```

**Step 2: Run test to verify it fails**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.ZoneEngineTest.distance profile - segment with null distanceMeters is ignored by distance lookup" 2>&1 | tail -20
```

Expected: FAIL — `HrSegment` constructor doesn't accept `durationSeconds` yet.

**Step 3: Update HrSegment**

Replace the entire file content with:

```kotlin
package com.hrcoach.domain.model

/**
 * One segment of a guided workout.
 *
 * Distance-based: populate [distanceMeters] (cumulative end distance).
 * Time-based:    populate [durationSeconds] (duration of this segment).
 * Exactly one of the two should be non-null for a valid segment.
 *
 * @param distanceMeters cumulative end distance for this segment (e.g. 5000f = up to 5 km)
 * @param durationSeconds duration of this segment in seconds (for time-based/interval workouts)
 * @param targetHr target heart rate in BPM
 * @param label optional human-readable name shown on the workout screen
 */
data class HrSegment(
    val distanceMeters: Float? = null,
    val durationSeconds: Int? = null,
    val targetHr: Int,
    val label: String? = null
)
```

**Step 4: Fix call-sites that relied on non-nullable distanceMeters**

Search for all usages of `HrSegment(distanceMeters =` — there are two places:
- `SetupViewModel.kt` line ~352: `HrSegment(distanceMeters = meters, targetHr = hr)`  → no change needed (named arg, Float still works)
- `ZoneEngineTest.kt`: existing `HrSegment(distanceMeters = 5000f, targetHr = 140)` — no change needed (named args still compile)

`CoachingEventRouter.kt` line 73: `config.segments.indexOfFirst { distanceMeters <= it.distanceMeters }` — this NOW fails because `it.distanceMeters` is `Float?`. Fix: change to `it.distanceMeters?.let { d -> distanceMeters <= d } == true`.

Apply that fix in `CoachingEventRouter.kt`:

```kotlin
// Old:
val segment = config.segments.indexOfFirst { distanceMeters <= it.distanceMeters }
// New:
val segment = config.segments.indexOfFirst { seg ->
    seg.distanceMeters?.let { d -> distanceMeters <= d } == true
}
```

**Step 5: Run all tests to verify nothing is broken**

```
./gradlew testDebugUnitTest 2>&1 | tail -30
```

Expected: All existing tests PASS.

**Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/model/HrSegment.kt \
        app/src/main/java/com/hrcoach/service/workout/CoachingEventRouter.kt \
        app/src/test/java/com/hrcoach/domain/engine/ZoneEngineTest.kt
git commit -m "feat: extend HrSegment with optional durationSeconds and label fields"
```

---

## Task 2: Add time-based routing to WorkoutConfig

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/model/WorkoutConfig.kt`
- Test: `app/src/test/java/com/hrcoach/domain/engine/ZoneEngineTest.kt`

**Step 1: Write failing tests**

Add to `ZoneEngineTest.kt`:

```kotlin
@Test
fun `time-based - targetHrAtElapsedSeconds returns correct segment`() {
    val config = WorkoutConfig(
        mode = WorkoutMode.DISTANCE_PROFILE,
        segments = listOf(
            HrSegment(durationSeconds = 300, targetHr = 125, label = "Warm-up"),    // 0-300s
            HrSegment(durationSeconds = 240, targetHr = 165, label = "Interval 1"), // 300-540s
            HrSegment(durationSeconds = 180, targetHr = 115, label = "Recovery 1")  // 540-720s
        )
    )
    assertEquals(125, config.targetHrAtElapsedSeconds(0L))
    assertEquals(125, config.targetHrAtElapsedSeconds(299L))
    assertEquals(165, config.targetHrAtElapsedSeconds(300L))
    assertEquals(165, config.targetHrAtElapsedSeconds(539L))
    assertEquals(115, config.targetHrAtElapsedSeconds(540L))
    assertEquals(115, config.targetHrAtElapsedSeconds(9999L)) // past end → last segment
}

@Test
fun `isTimeBased returns true when any segment has durationSeconds`() {
    val timeBased = WorkoutConfig(
        mode = WorkoutMode.DISTANCE_PROFILE,
        segments = listOf(HrSegment(durationSeconds = 240, targetHr = 160))
    )
    val distanceBased = WorkoutConfig(
        mode = WorkoutMode.DISTANCE_PROFILE,
        segments = listOf(HrSegment(distanceMeters = 5000f, targetHr = 140))
    )
    assertEquals(true, timeBased.isTimeBased())
    assertEquals(false, distanceBased.isTimeBased())
}
```

**Step 2: Run to verify they fail**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.ZoneEngineTest" 2>&1 | tail -20
```

Expected: FAIL — `targetHrAtElapsedSeconds` and `isTimeBased` don't exist.

**Step 3: Update WorkoutConfig.kt**

```kotlin
package com.hrcoach.domain.model

data class WorkoutConfig(
    val mode: WorkoutMode,
    val steadyStateTargetHr: Int? = null,
    val segments: List<HrSegment> = emptyList(),
    val bufferBpm: Int = 5,
    val alertDelaySec: Int = 15,
    val alertCooldownSec: Int = 30,
    val presetId: String? = null
) {
    /** True when this workout uses time-based segments (interval workouts). */
    fun isTimeBased(): Boolean = segments.any { it.durationSeconds != null }

    /** Returns the target HR for a distance-based workout at the given cumulative distance. */
    fun targetHrAtDistance(distanceMeters: Float): Int? {
        return when (mode) {
            WorkoutMode.STEADY_STATE -> steadyStateTargetHr
            WorkoutMode.DISTANCE_PROFILE -> {
                segments.firstOrNull { seg ->
                    seg.distanceMeters?.let { d -> distanceMeters <= d } == true
                }?.targetHr ?: segments.lastOrNull()?.targetHr
            }
        }
    }

    /**
     * Returns the target HR for a time-based workout at [elapsedSeconds] into the workout.
     * Iterates through cumulative segment durations. Returns last segment's HR if past the end.
     */
    fun targetHrAtElapsedSeconds(elapsedSeconds: Long): Int? {
        if (segments.isEmpty()) return steadyStateTargetHr
        var cumulative = 0L
        for (segment in segments) {
            val duration = segment.durationSeconds?.toLong() ?: continue
            cumulative += duration
            if (elapsedSeconds < cumulative) return segment.targetHr
        }
        return segments.lastOrNull()?.targetHr
    }

    /**
     * Returns the current segment label for time-based workouts, or null if no label.
     */
    fun segmentLabelAtElapsedSeconds(elapsedSeconds: Long): String? {
        var cumulative = 0L
        for (segment in segments) {
            val duration = segment.durationSeconds?.toLong() ?: continue
            cumulative += duration
            if (elapsedSeconds < cumulative) return segment.label
        }
        return segments.lastOrNull()?.label
    }

    /**
     * Returns the current segment label for distance-based workouts, or null if no label.
     */
    fun segmentLabelAtDistance(distanceMeters: Float): String? {
        return segments.firstOrNull { seg ->
            seg.distanceMeters?.let { d -> distanceMeters <= d } == true
        }?.label ?: segments.lastOrNull()?.label
    }
}
```

**Step 4: Run tests**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.ZoneEngineTest" 2>&1 | tail -30
```

Expected: All `ZoneEngineTest` tests PASS.

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/model/WorkoutConfig.kt \
        app/src/test/java/com/hrcoach/domain/engine/ZoneEngineTest.kt
git commit -m "feat: add time-based segment routing to WorkoutConfig"
```

---

## Task 3: Add direct-target ZoneEngine overload

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/engine/ZoneEngine.kt`
- Test: `app/src/test/java/com/hrcoach/domain/engine/ZoneEngineTest.kt`

**Step 1: Write failing test**

```kotlin
@Test
fun `evaluate with explicit targetHr respects buffer`() {
    val config = WorkoutConfig(
        mode = WorkoutMode.STEADY_STATE,
        steadyStateTargetHr = 160,
        bufferBpm = 5
    )
    val engine = ZoneEngine(config)
    // Direct overload — ignores config target, uses supplied targetHr
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

**Step 3: Add overload to ZoneEngine.kt**

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
        val low = targetHr - config.bufferBpm
        val high = targetHr + config.bufferBpm
        return when {
            hr < low -> ZoneStatus.BELOW_ZONE
            hr > high -> ZoneStatus.ABOVE_ZONE
            else -> ZoneStatus.IN_ZONE
        }
    }
}
```

**Step 4: Run all tests**

```
./gradlew testDebugUnitTest 2>&1 | tail -20
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/engine/ZoneEngine.kt \
        app/src/test/java/com/hrcoach/domain/engine/ZoneEngineTest.kt
git commit -m "feat: add direct-target evaluate overload to ZoneEngine"
```

---

## Task 4: Add UserProfileRepository

**Files:**
- Create: `app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt`

No new Hilt binding needed — `@Inject constructor` with `@Singleton` is auto-discovered like `MapsSettingsRepository`.

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
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/repository/UserProfileRepository.kt
git commit -m "feat: add UserProfileRepository for persisting user's max HR"
```

---

## Task 5: Create PresetLibrary

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/preset/WorkoutPreset.kt`
- Create: `app/src/main/java/com/hrcoach/domain/preset/PresetLibrary.kt`
- Create: `app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt`

**Step 1: Write failing tests**

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
            // HR targets must be in valid range
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
        val config = PresetLibrary.ALL
            .first { it.id == "zone2_base" }
            .buildConfig(180)!!
        assertEquals(WorkoutMode.STEADY_STATE, config.mode)
        assertEquals((180 * 0.68).toInt(), config.steadyStateTargetHr)
    }

    @Test
    fun `norwegian 4x4 - has 4 intervals and correct structure`() {
        val config = PresetLibrary.ALL
            .first { it.id == "norwegian_4x4" }
            .buildConfig(180)!!
        assertTrue(config.isTimeBased())
        val intervals = config.segments.filter { it.durationSeconds == 240 }
        assertEquals(4, intervals.size)
        // All interval target HRs should be ~92% of 180 = 165
        intervals.forEach { assertTrue(it.targetHr in 155..175) }
    }

    @Test
    fun `half marathon prep - last segment ends at 21100 meters`() {
        val config = PresetLibrary.ALL
            .first { it.id == "half_marathon_prep" }
            .buildConfig(180)!!
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
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.PresetLibraryTest" 2>&1 | tail -20
```

**Step 3: Create WorkoutPreset.kt**

```kotlin
package com.hrcoach.domain.preset

import com.hrcoach.domain.model.WorkoutConfig

/**
 * Descriptor for a scientifically-backed preset workout profile.
 *
 * [buildConfig] takes the user's max HR and returns a fully resolved [WorkoutConfig].
 */
data class WorkoutPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val category: PresetCategory,
    val durationLabel: String,   // e.g. "35 min" or "21.1 km"
    val intensityLabel: String,  // e.g. "High Intensity" or "Easy"
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
        zone2Base(),
        aeroTempo(),
        lactateThreshold(),
        norwegian4x4(),
        hiit3030(),
        hillRepeats(),
        halfMarathonPrep(),
        marathonPrep()
    )

    // ── Base aerobic ─────────────────────────────────────────────────────────

    private fun zone2Base() = WorkoutPreset(
        id = "zone2_base",
        name = "Zone 2 Base",
        subtitle = "Aerobic foundation",
        description = "60–75 min at 68% max HR. Builds mitochondrial density and fat oxidation (Seiler polarized model).",
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

    // ── Threshold ─────────────────────────────────────────────────────────────

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

    // ── Interval ──────────────────────────────────────────────────────────────

    private fun norwegian4x4() = WorkoutPreset(
        id = "norwegian_4x4",
        name = "Norwegian 4×4",
        subtitle = "VO₂max booster",
        description = "4 × 4-min intervals at 90–95% max HR with 3-min active recovery. The most VO₂max-effective protocol (Helgerud et al. 2007).",
        category = PresetCategory.INTERVAL,
        durationLabel = "~35 min",
        intensityLabel = "Very High",
        buildConfig = { maxHr ->
            val intervalHr = (maxHr * 0.92).roundToInt()
            val recoveryHr = (maxHr * 0.65).roundToInt()
            val warmupHr   = (maxHr * 0.65).roundToInt()
            val cooldownHr = (maxHr * 0.60).roundToInt()
            val segments = mutableListOf<HrSegment>()
            segments += HrSegment(durationSeconds = 600, targetHr = warmupHr,   label = "Warm-up")
            repeat(4) { i ->
                segments += HrSegment(durationSeconds = 240, targetHr = intervalHr, label = "Interval ${i + 1} of 4")
                if (i < 3) {
                    segments += HrSegment(durationSeconds = 180, targetHr = recoveryHr, label = "Recovery ${i + 1}")
                }
            }
            segments += HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down")
            WorkoutConfig(
                mode = WorkoutMode.DISTANCE_PROFILE,
                segments = segments,
                bufferBpm = 5,
                presetId = "norwegian_4x4"
            )
        }
    )

    private fun hiit3030() = WorkoutPreset(
        id = "hiit_30_30",
        name = "HIIT 30/30",
        subtitle = "Sprint intervals",
        description = "10 × 30-sec sprints at 92% max HR with 30-sec recoveries. High anaerobic stimulus in minimal time.",
        category = PresetCategory.INTERVAL,
        durationLabel = "~20 min",
        intensityLabel = "Very High",
        buildConfig = { maxHr ->
            val sprintHr   = (maxHr * 0.92).roundToInt()
            val recoveryHr = (maxHr * 0.62).roundToInt()
            val warmupHr   = (maxHr * 0.65).roundToInt()
            val cooldownHr = (maxHr * 0.60).roundToInt()
            val segments = mutableListOf<HrSegment>()
            segments += HrSegment(durationSeconds = 300, targetHr = warmupHr,   label = "Warm-up")
            repeat(10) { i ->
                segments += HrSegment(durationSeconds = 30,  targetHr = sprintHr,   label = "Sprint ${i + 1} of 10")
                segments += HrSegment(durationSeconds = 30,  targetHr = recoveryHr, label = "Recover")
            }
            segments += HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down")
            WorkoutConfig(
                mode = WorkoutMode.DISTANCE_PROFILE,
                segments = segments,
                bufferBpm = 5,
                presetId = "hiit_30_30"
            )
        }
    )

    private fun hillRepeats() = WorkoutPreset(
        id = "hill_repeats",
        name = "Hill Repeats",
        subtitle = "Strength + power",
        description = "6 × 90-sec hill climbs at 87% max HR with 90-sec flat recovery. Builds leg strength and running economy.",
        category = PresetCategory.INTERVAL,
        durationLabel = "~25 min",
        intensityLabel = "High",
        buildConfig = { maxHr ->
            val climbHr    = (maxHr * 0.87).roundToInt()
            val recoveryHr = (maxHr * 0.63).roundToInt()
            val warmupHr   = (maxHr * 0.65).roundToInt()
            val cooldownHr = (maxHr * 0.60).roundToInt()
            val segments = mutableListOf<HrSegment>()
            segments += HrSegment(durationSeconds = 300, targetHr = warmupHr,   label = "Warm-up")
            repeat(6) { i ->
                segments += HrSegment(durationSeconds = 90,  targetHr = climbHr,    label = "Hill ${i + 1} of 6")
                segments += HrSegment(durationSeconds = 90,  targetHr = recoveryHr, label = "Recover")
            }
            segments += HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down")
            WorkoutConfig(
                mode = WorkoutMode.DISTANCE_PROFILE,
                segments = segments,
                bufferBpm = 5,
                presetId = "hill_repeats"
            )
        }
    )

    // ── Race prep ─────────────────────────────────────────────────────────────

    private fun halfMarathonPrep() = WorkoutPreset(
        id = "half_marathon_prep",
        name = "Half Marathon Prep",
        subtitle = "Race simulation",
        description = "3 progressive HR zones across 21.1 km: easy start, race pace mid-section, strong finish.",
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
        description = "Negative-split strategy across 42.2 km: conservative early, marathon pace mid, controlled push late.",
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
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.PresetLibraryTest" 2>&1 | tail -30
```

Expected: All 5 `PresetLibraryTest` tests PASS.

**Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/preset/ \
        app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt
git commit -m "feat: add PresetLibrary with 8 scientifically-backed workout presets"
```

---

## Task 6: Update WorkoutSnapshot with elapsed time and segment label

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutState.kt`

**Step 1: Add fields to WorkoutSnapshot**

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
    val elapsedSeconds: Long = 0L,      // NEW: for time-based workouts
    val segmentLabel: String? = null    // NEW: current segment name
)
```

No test needed — this is a data class addition with default values; all existing `.copy()` calls remain valid.

**Step 2: Build to verify**

```
./gradlew assembleDebug 2>&1 | tail -10
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutState.kt
git commit -m "feat: add elapsedSeconds and segmentLabel to WorkoutSnapshot"
```

---

## Task 7: Update WorkoutForegroundService for time-based workouts

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

**Step 1: Add `workoutStartMs` field** (near the other instance variables, around line 80)

Add after `private var workoutId: Long = 0L`:

```kotlin
private var workoutStartMs: Long = 0L
```

**Step 2: Set `workoutStartMs` in `startWorkout()`** (in the `startupJob` coroutine, after `workoutId = repository.createWorkout(...)`)

```kotlin
workoutStartMs = System.currentTimeMillis()
```

**Step 3: Update `processTick()` to handle time-based routing**

Locate the line (around line 218):
```kotlin
val target = workoutConfig.targetHrAtDistance(tick.distanceMeters) ?: 0
```

Replace with:
```kotlin
val elapsedSeconds = if (workoutStartMs > 0L) (nowMs - workoutStartMs) / 1000L else 0L
val target = if (workoutConfig.isTimeBased()) {
    workoutConfig.targetHrAtElapsedSeconds(elapsedSeconds) ?: 0
} else {
    workoutConfig.targetHrAtDistance(tick.distanceMeters) ?: 0
}
val segmentLabel = if (workoutConfig.isTimeBased()) {
    workoutConfig.segmentLabelAtElapsedSeconds(elapsedSeconds)
} else {
    workoutConfig.segmentLabelAtDistance(tick.distanceMeters)
}
```

**Step 4: Update the `ZoneEngine.evaluate()` call** (around line 239)

```kotlin
// Old:
val zoneStatus = if (!tick.connected || tick.hr <= 0 || target == 0) {
    ZoneStatus.NO_DATA
} else {
    engine.evaluate(tick.hr, tick.distanceMeters)
}
// New: use direct-target overload (avoids double lookup):
val zoneStatus = if (!tick.connected || tick.hr <= 0 || target == 0) {
    ZoneStatus.NO_DATA
} else {
    engine.evaluate(tick.hr, target)
}
```

**Step 5: Include `elapsedSeconds` and `segmentLabel` in `WorkoutState.update`**

In the `WorkoutState.update { current -> current.copy(...) }` block in `processTick()`, add:
```kotlin
elapsedSeconds = elapsedSeconds,
segmentLabel = segmentLabel,
```

Also update the `initialSet` in `startWorkout()` to include `elapsedSeconds = 0L, segmentLabel = null`.

**Step 6: Build and verify**

```
./gradlew assembleDebug 2>&1 | tail -20
```

**Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat: route time-based workouts by elapsed seconds in WorkoutForegroundService"
```

---

## Task 8: Update CoachingEventRouter for time-based segment transitions

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/workout/CoachingEventRouter.kt`
- Test: `app/src/test/java/com/hrcoach/service/workout/CoachingEventRouterTest.kt`

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
    // At elapsed=0 (segment 0, warm-up), no change
    router.route(
        workoutConfig = config, connected = true, distanceMeters = 0f,
        elapsedSeconds = 0L, zoneStatus = ZoneStatus.IN_ZONE,
        adaptiveResult = null, guidance = "hold", nowMs = 0L,
        emitEvent = { e, _ -> events += e }
    )
    assertTrue(CoachingEvent.SEGMENT_CHANGE !in events)

    // At elapsed=300 (crosses into segment 1, interval), expect SEGMENT_CHANGE
    router.route(
        workoutConfig = config, connected = true, distanceMeters = 0f,
        elapsedSeconds = 300L, zoneStatus = ZoneStatus.IN_ZONE,
        adaptiveResult = null, guidance = "hold", nowMs = 300_000L,
        emitEvent = { e, _ -> events += e }
    )
    assertTrue(CoachingEvent.SEGMENT_CHANGE in events)
}
```

**Step 2: Run to verify it fails**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.service.workout.CoachingEventRouterTest" 2>&1 | tail -20
```

Expected: FAIL — `route()` doesn't have `elapsedSeconds` parameter yet.

**Step 3: Update CoachingEventRouter.kt**

Update the `route()` signature to include `elapsedSeconds: Long`:

```kotlin
fun route(
    workoutConfig: WorkoutConfig,
    connected: Boolean,
    distanceMeters: Float,
    elapsedSeconds: Long,       // NEW
    zoneStatus: ZoneStatus,
    adaptiveResult: AdaptivePaceController.TickResult?,
    guidance: String,
    nowMs: Long,
    emitEvent: (CoachingEvent, String?) -> Unit
)
```

Update the segment-change logic:

```kotlin
if (workoutConfig.mode == WorkoutMode.DISTANCE_PROFILE) {
    val segmentIndex = if (workoutConfig.isTimeBased()) {
        currentSegmentIndexByTime(workoutConfig, elapsedSeconds)
    } else {
        currentSegmentIndexByDistance(workoutConfig, distanceMeters)
    }
    if (lastSegmentIndex >= 0 && segmentIndex >= 0 && segmentIndex != lastSegmentIndex) {
        emitEvent(CoachingEvent.SEGMENT_CHANGE, null)
    }
    lastSegmentIndex = segmentIndex
} else {
    lastSegmentIndex = -1
}
```

Add the new private helper:

```kotlin
private fun currentSegmentIndexByTime(config: WorkoutConfig, elapsedSeconds: Long): Int {
    if (config.segments.isEmpty()) return -1
    var cumulative = 0L
    config.segments.forEachIndexed { index, seg ->
        val duration = seg.durationSeconds?.toLong() ?: return@forEachIndexed
        cumulative += duration
        if (elapsedSeconds < cumulative) return index
    }
    return config.segments.lastIndex
}

// Rename the existing private helper:
private fun currentSegmentIndexByDistance(config: WorkoutConfig, distanceMeters: Float): Int {
    if (config.segments.isEmpty()) return -1
    val segment = config.segments.indexOfFirst { seg ->
        seg.distanceMeters?.let { d -> distanceMeters <= d } == true
    }
    return if (segment >= 0) segment else config.segments.lastIndex
}
```

**Step 4: Update the call-site in WorkoutForegroundService.kt**

Find the `coachingEventRouter.route(...)` call and add `elapsedSeconds = elapsedSeconds` to it.

**Step 5: Update existing test** — the existing `CoachingEventRouterTest` calls `router.route(...)` without `elapsedSeconds`. Add `elapsedSeconds = 0L` to all existing call sites in the test.

**Step 6: Run all tests**

```
./gradlew testDebugUnitTest 2>&1 | tail -30
```

Expected: All tests PASS.

**Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/workout/CoachingEventRouter.kt \
        app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt \
        app/src/test/java/com/hrcoach/service/workout/CoachingEventRouterTest.kt
git commit -m "feat: handle time-based segment transitions in CoachingEventRouter"
```

---

## Task 9: Update SetupViewModel for preset selection

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt`

**Step 1: Add new fields to `SetupUiState`**

After `val connectionError: String? = null,` add:

```kotlin
val selectedPresetId: String? = null,
val showHrMaxDialog: Boolean = false,
val maxHrInput: String = "",
val maxHr: Int? = null,
val pendingPresetId: String? = null,  // preset waiting for HRmax confirmation
```

**Step 2: Add `UserProfileRepository` injection to `SetupViewModel`**

Update the constructor:

```kotlin
@HiltViewModel
class SetupViewModel @Inject constructor(
    private val audioSettingsRepository: AudioSettingsRepository,
    private val mapsSettingsRepository: MapsSettingsRepository,
    private val bleCoordinator: BleConnectionCoordinator,
    private val userProfileRepository: UserProfileRepository  // NEW
) : ViewModel() {
```

**Step 3: Load maxHr in `init`**

In the `init` block, add after loading audio settings:

```kotlin
val storedMaxHr = userProfileRepository.getMaxHr()
_uiState.value = _uiState.value.copy(maxHr = storedMaxHr)
```

**Step 4: Add preset-selection functions**

```kotlin
/** Called when user taps a preset card. */
fun selectPreset(presetId: String) {
    val maxHr = _uiState.value.maxHr
    if (maxHr == null) {
        // Need HRmax first — store pending preset and show dialog
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

/** Called when user confirms HRmax in the dialog. Returns true if valid. */
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

/** Long-press: load preset segments into the custom segment editor. */
fun loadPresetForEditing(presetId: String) {
    val maxHr = _uiState.value.maxHr ?: return
    val preset = PresetLibrary.ALL.firstOrNull { it.id == presetId } ?: return
    val config = preset.buildConfig(maxHr)
    if (config.isTimeBased()) return // Time-based presets not editable in segment UI
    val segmentInputs = config.segments.map { seg ->
        SegmentInput(
            distanceKm = ((seg.distanceMeters ?: 0f) / 1000f).toString(),
            targetHr = seg.targetHr.toString()
        )
    }
    _uiState.value = _uiState.value.copy(
        mode = WorkoutMode.DISTANCE_PROFILE,
        segments = segmentInputs
    )
    recomputeValidation()
}
```

**Step 5: Update `buildConfigOrNull()` to resolve preset configs**

At the top of `buildConfigOrNull()`, add a fast path:

```kotlin
fun buildConfigOrNull(): WorkoutConfig? {
    val state = _uiState.value
    val buffer = state.bufferBpm.toIntOrNull() ?: return null
    val delay = state.alertDelaySec.toIntOrNull() ?: return null
    val cooldown = state.alertCooldownSec.toIntOrNull() ?: return null
    if (buffer !in 1..30 || delay !in 5..300 || cooldown !in 5..300) return null

    // Fast path: resolve from preset
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

    // Existing logic for STEADY_STATE and custom DISTANCE_PROFILE...
    return when (state.mode) { ... }
}
```

**Step 6: Update `recomputeValidation()` to allow preset configs as valid**

In `recomputeValidation()`, add a check: if `selectedPresetId != null && selectedPresetId != "custom" && maxHr != null`, treat as valid (no field errors, `canStartWorkout = true`).

**Step 7: Build to verify**

```
./gradlew assembleDebug 2>&1 | tail -20
```

**Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt
git commit -m "feat: add preset selection and HRmax onboarding to SetupViewModel"
```

---

## Task 10: Update SetupScreen UI — Preset Grid

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`

**Step 1: Update the mode segmented button** (replace "Distance Profile" label)

Change:
```kotlin
Text("Distance Profile")
```
To:
```kotlin
Text("Guided Workout")
```

**Step 2: Replace the DISTANCE_PROFILE branch with preset grid**

The current `SetupScreen` shows a segment-list editor when `state.mode == WorkoutMode.DISTANCE_PROFILE`. Replace this entire branch with:

```kotlin
WorkoutMode.DISTANCE_PROFILE -> {
    PresetGrid(
        presets = PresetLibrary.ALL,
        selectedPresetId = state.selectedPresetId,
        onSelectPreset = { viewModel.selectPreset(it.id) },
        onLongPressPreset = { viewModel.loadPresetForEditing(it.id) }
    )
    // "Custom" option — navigates back to segment editor
    if (state.selectedPresetId == "custom") {
        SegmentEditor(state = state, viewModel = viewModel)
    }
    TextButton(
        onClick = {
            viewModel.selectPreset("custom")
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Custom segment editor…")
    }
}
```

**Step 3: Implement `PresetGrid` composable** (add below `SetupScreen` in the same file)

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetGrid(
    presets: List<WorkoutPreset>,
    selectedPresetId: String?,
    onSelectPreset: (WorkoutPreset) -> Unit,
    onLongPressPreset: (WorkoutPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val categories = PresetCategory.entries
        categories.forEach { category ->
            val catPresets = presets.filter { it.category == category }
            if (catPresets.isEmpty()) return@forEach
            Text(
                text = category.displayName(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            catPresets.forEach { preset ->
                PresetCard(
                    preset = preset,
                    isSelected = preset.id == selectedPresetId,
                    onClick = { onSelectPreset(preset) },
                    onLongClick = { onLongPressPreset(preset) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetCard(
    preset: WorkoutPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(preset.subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(preset.durationLabel, style = MaterialTheme.typography.labelSmall)
                Text(preset.intensityLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary)
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

**Step 4: Add HRmax dialog** (above the `Scaffold` close in `SetupScreen`)

```kotlin
if (state.showHrMaxDialog) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissHrMaxDialog() },
        title = { Text("Your Max Heart Rate") },
        text = {
            Column {
                Text("Presets use % of your max HR. Enter your max HR (bpm) to personalise targets.")
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
                    "Common formula: 220 – your age. Or use a lab test value.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.confirmMaxHr() }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissHrMaxDialog() }) { Text("Cancel") }
        }
    )
}
```

**Step 5: Add required imports** for `ExperimentalFoundationApi`, `combinedClickable`, `AlertDialog`, `PresetLibrary`, `PresetCategory`, `WorkoutPreset`.

**Step 6: Build to verify**

```
./gradlew assembleDebug 2>&1 | tail -20
```

**Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt
git commit -m "feat: replace distance profile builder with preset card grid in SetupScreen"
```

---

## Task 11: Show segment label and elapsed time in Active Workout Screen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt`

**Step 1: Read what the current workout screen shows for distance/target**

Use the Read tool to find where `targetHr` or `distanceMeters` are displayed in `ActiveWorkoutScreen.kt`.

**Step 2: Add elapsed time display for time-based workouts**

In the workout screen's stats area, add:

```kotlin
// Show elapsed time when segmentLabel is available (time-based workout)
val segmentLabel = state.segmentLabel
val elapsedSeconds = state.elapsedSeconds
if (segmentLabel != null) {
    Text(
        text = segmentLabel,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val secs = elapsedSeconds % 60
    val timeStr = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
                  else "%d:%02d".format(minutes, secs)
    Text(
        text = timeStr,
        style = MaterialTheme.typography.bodyMedium
    )
}
```

**Step 3: Build and verify**

```
./gradlew assembleDebug 2>&1 | tail -10
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt
git commit -m "feat: display segment label and elapsed time in active workout screen"
```

---

## Task 12: Full test sweep

**Step 1: Run all unit tests**

```
./gradlew testDebugUnitTest 2>&1 | tail -40
```

Expected: All tests PASS. Verify test count includes new PresetLibraryTest and ZoneEngineTest additions.

**Step 2: Build release APK**

```
./gradlew assembleDebug 2>&1 | tail -20
```

**Step 3: Fix any remaining compilation errors**

If any compilation errors, fix them (likely import additions or signature mismatches).

**Step 4: Final commit**

```bash
git add -A
git commit -m "chore: finalize preset workout profiles feature"
```

---

## Implementation Notes

### GSON backward compatibility
Existing stored `WorkoutConfig` JSON in Room has `"distanceMeters": 1500.0` in segments. When `distanceMeters: Float` becomes `distanceMeters: Float?`, GSON still deserializes `1500.0` as a non-null Float (GSON handles JSON number → Kotlin `Float?` correctly). Old workouts continue to work.

### No new WorkoutMode enum value
The design uses `DISTANCE_PROFILE` for both distance-based and time-based guided workouts. `isTimeBased()` distinguishes them at runtime. This avoids breaking existing WorkoutEntity records that store `mode = "DISTANCE_PROFILE"`.

### Preset config with custom buffer/delay
The `buildConfigOrNull()` fast path applies the user's custom buffer/delay/cooldown settings on top of the preset's default config. This means the advanced settings still apply.

### Long-press editing for time-based presets
Time-based presets (Norwegian 4×4, HIIT, hill repeats) are NOT editable via the segment editor (too complex). Long-pressing them does nothing. Only distance-based presets (half marathon, marathon) can be loaded into the editor.
