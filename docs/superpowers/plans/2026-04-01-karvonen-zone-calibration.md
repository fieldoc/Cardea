# Karvonen Zone Calibration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Switch HR zone calculations from %HRmax to Karvonen (HR Reserve), add submaximal HRmax inference, BASE phase strides, and resting HR wiring.

**Architecture:** The `WorkoutPreset.buildConfig` lambda gains a `restHr` parameter. All 8 presets in `PresetLibrary` switch from `maxHr * pct` to `restHr + (maxHr - restHr) * pct`. A new `SubMaxHrEstimator` infers HRmax from early workout data. `ZoneEducationProvider` adds Karvonen-based BPM ranges. `SessionSelector` assigns strides in BASE phase.

**Tech Stack:** Kotlin, JUnit 5, no new dependencies.

**Spec:** `docs/superpowers/specs/2026-04-01-karvonen-zone-calibration-design.md`

---

### Task 1: Change WorkoutPreset.buildConfig Lambda Signature

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/preset/WorkoutPreset.kt`

- [ ] **Step 1: Update the lambda signature**

Change the `buildConfig` field from `(maxHr: Int) -> WorkoutConfig` to `(maxHr: Int, restHr: Int) -> WorkoutConfig`:

```kotlin
data class WorkoutPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val category: PresetCategory,
    val durationLabel: String,
    val intensityLabel: String,
    val buildConfig: (maxHr: Int, restHr: Int) -> WorkoutConfig
)
```

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew compileDebugKotlin 2>&1 | head -50`

Expected: Compilation errors in all callers of `buildConfig` — this is correct, we'll fix them in subsequent tasks.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/preset/WorkoutPreset.kt
git commit -m "refactor: change WorkoutPreset.buildConfig to take restHr parameter"
```

---

### Task 2: Add Karvonen Helper and Convert PresetLibrary

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/preset/PresetLibrary.kt`
- Create: `app/src/test/java/com/hrcoach/domain/preset/KarvonenTest.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt`

- [ ] **Step 1: Write the Karvonen helper test**

Create `app/src/test/java/com/hrcoach/domain/preset/KarvonenTest.kt`:

```kotlin
package com.hrcoach.domain.preset

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KarvonenTest {

    @Test
    fun `zone2 base at 68 pct with rest 60 max 191`() {
        // restHr + (maxHr - restHr) * pct = 60 + 131 * 0.68 = 60 + 89.08 = 149
        val target = karvonen(maxHr = 191, restHr = 60, pct = 0.68f)
        assertEquals(149, target)
    }

    @Test
    fun `lactate threshold at 90 pct with rest 60 max 191`() {
        // 60 + 131 * 0.90 = 60 + 117.9 = 178
        val target = karvonen(maxHr = 191, restHr = 60, pct = 0.90f)
        assertEquals(178, target)
    }

    @Test
    fun `rest equals zero degenerates to simple pct of max`() {
        val target = karvonen(maxHr = 200, restHr = 0, pct = 0.68f)
        assertEquals(136, target)
    }

    @Test
    fun `rest equals max returns max regardless of pct`() {
        val target = karvonen(maxHr = 180, restHr = 180, pct = 0.68f)
        assertEquals(180, target)
    }

    @Test
    fun `high rest low max still produces valid result`() {
        // Edge: rest=75, max=160, pct=0.68 → 75 + 85*0.68 = 75 + 57.8 = 133
        val target = karvonen(maxHr = 160, restHr = 75, pct = 0.68f)
        assertEquals(133, target)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.KarvonenTest" 2>&1 | tail -20`

Expected: FAIL — `karvonen` function doesn't exist yet.

- [ ] **Step 3: Add the karvonen helper to PresetLibrary**

At the top of `PresetLibrary.kt`, add the internal helper function (after the imports, before `object PresetLibrary`):

```kotlin
/** Karvonen / Heart Rate Reserve formula. */
internal fun karvonen(maxHr: Int, restHr: Int, pct: Float): Int =
    (restHr + (maxHr - restHr) * pct).roundToInt()
```

- [ ] **Step 4: Run Karvonen tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.KarvonenTest" 2>&1 | tail -20`

Expected: All 5 tests PASS.

- [ ] **Step 5: Convert all 8 presets in PresetLibrary to Karvonen**

Replace the full `PresetLibrary` object. Every lambda changes from `{ maxHr -> ... (maxHr * X).roundToInt() }` to `{ maxHr, restHr -> ... karvonen(maxHr, restHr, X) }`:

```kotlin
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
        description = "45–75 min at 68% HR reserve. Builds mitochondrial density and fat oxidation.",
        category = PresetCategory.BASE_AEROBIC,
        durationLabel = "45–75 min",
        intensityLabel = "Easy",
        buildConfig = { maxHr, restHr ->
            WorkoutConfig(
                mode = WorkoutMode.STEADY_STATE,
                steadyStateTargetHr = karvonen(maxHr, restHr, 0.68f),
                bufferBpm = 5,
                presetId = "zone2_base"
            )
        }
    )

    private fun aeroTempo() = WorkoutPreset(
        id = "aerobic_tempo",
        name = "Aerobic Tempo",
        subtitle = "Comfortably hard",
        description = "30 min at 84% HR reserve. Raises lactate threshold and improves running economy.",
        category = PresetCategory.THRESHOLD,
        durationLabel = "30 min",
        intensityLabel = "Moderate",
        buildConfig = { maxHr, restHr ->
            WorkoutConfig(
                mode = WorkoutMode.STEADY_STATE,
                steadyStateTargetHr = karvonen(maxHr, restHr, 0.84f),
                bufferBpm = 4,
                presetId = "aerobic_tempo"
            )
        }
    )

    private fun lactateThreshold() = WorkoutPreset(
        id = "lactate_threshold",
        name = "Lactate Threshold",
        subtitle = "Threshold effort",
        description = "25 min at 90% HR reserve. Targets the lactate threshold directly.",
        category = PresetCategory.THRESHOLD,
        durationLabel = "25 min",
        intensityLabel = "Hard",
        buildConfig = { maxHr, restHr ->
            WorkoutConfig(
                mode = WorkoutMode.STEADY_STATE,
                steadyStateTargetHr = karvonen(maxHr, restHr, 0.90f),
                bufferBpm = 3,
                presetId = "lactate_threshold"
            )
        }
    )

    private fun norwegian4x4() = WorkoutPreset(
        id = "norwegian_4x4",
        name = "Norwegian 4×4",
        subtitle = "VO₂max booster",
        description = "4 × 4-min intervals at 92% HR reserve with 3-min active recovery.",
        category = PresetCategory.INTERVAL,
        durationLabel = "~35 min",
        intensityLabel = "Very High",
        buildConfig = { maxHr, restHr ->
            val intervalHr = karvonen(maxHr, restHr, 0.92f)
            val recoveryHr = karvonen(maxHr, restHr, 0.65f)
            val cooldownHr = karvonen(maxHr, restHr, 0.60f)
            val segs = buildList {
                add(HrSegment(durationSeconds = 600, targetHr = recoveryHr, label = "Warm-up"))
                repeat(4) { i ->
                    add(HrSegment(durationSeconds = 240, targetHr = intervalHr, label = "Interval ${i+1} of 4"))
                    if (i < 3) add(HrSegment(durationSeconds = 180, targetHr = recoveryHr, label = "Recovery ${i+1}"))
                }
                add(HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down"))
            }
            WorkoutConfig(mode = WorkoutMode.DISTANCE_PROFILE, segments = segs, bufferBpm = 5, presetId = "norwegian_4x4")
        }
    )

    private fun hiit3030() = WorkoutPreset(
        id = "hiit_30_30",
        name = "HIIT 30/30",
        subtitle = "Sprint intervals",
        description = "10 × 30-sec sprints at 92% HR reserve with 30-sec recoveries.",
        category = PresetCategory.INTERVAL,
        durationLabel = "~20 min",
        intensityLabel = "Very High",
        buildConfig = { maxHr, restHr ->
            val sprintHr   = karvonen(maxHr, restHr, 0.92f)
            val recoveryHr = karvonen(maxHr, restHr, 0.62f)
            val warmupHr   = karvonen(maxHr, restHr, 0.65f)
            val cooldownHr = karvonen(maxHr, restHr, 0.60f)
            val segs = buildList {
                add(HrSegment(durationSeconds = 300, targetHr = warmupHr, label = "Warm-up"))
                repeat(10) { i ->
                    add(HrSegment(durationSeconds = 30, targetHr = sprintHr, label = "Sprint ${i+1} of 10"))
                    add(HrSegment(durationSeconds = 30, targetHr = recoveryHr, label = "Recover"))
                }
                add(HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down"))
            }
            WorkoutConfig(mode = WorkoutMode.DISTANCE_PROFILE, segments = segs, bufferBpm = 5, presetId = "hiit_30_30")
        }
    )

    private fun hillRepeats() = WorkoutPreset(
        id = "hill_repeats",
        name = "Hill Repeats",
        subtitle = "Strength + power",
        description = "6 × 90-sec hill climbs at 87% HR reserve with 90-sec flat recovery.",
        category = PresetCategory.INTERVAL,
        durationLabel = "~25 min",
        intensityLabel = "High",
        buildConfig = { maxHr, restHr ->
            val climbHr    = karvonen(maxHr, restHr, 0.87f)
            val recoveryHr = karvonen(maxHr, restHr, 0.63f)
            val warmupHr   = karvonen(maxHr, restHr, 0.65f)
            val cooldownHr = karvonen(maxHr, restHr, 0.60f)
            val segs = buildList {
                add(HrSegment(durationSeconds = 300, targetHr = warmupHr, label = "Warm-up"))
                repeat(6) { i ->
                    add(HrSegment(durationSeconds = 90, targetHr = climbHr, label = "Hill ${i+1} of 6"))
                    add(HrSegment(durationSeconds = 90, targetHr = recoveryHr, label = "Recover"))
                }
                add(HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down"))
            }
            WorkoutConfig(mode = WorkoutMode.DISTANCE_PROFILE, segments = segs, bufferBpm = 5, presetId = "hill_repeats")
        }
    )

    private fun halfMarathonPrep() = WorkoutPreset(
        id = "half_marathon_prep",
        name = "Half Marathon Prep",
        subtitle = "Race simulation",
        description = "3 progressive HR zones across 21.1 km.",
        category = PresetCategory.RACE_PREP,
        durationLabel = "21.1 km",
        intensityLabel = "Moderate–Hard",
        buildConfig = { maxHr, restHr ->
            WorkoutConfig(
                mode = WorkoutMode.DISTANCE_PROFILE,
                segments = listOf(
                    HrSegment(distanceMeters = 3000f,  targetHr = karvonen(maxHr, restHr, 0.72f), label = "Easy Start"),
                    HrSegment(distanceMeters = 14000f, targetHr = karvonen(maxHr, restHr, 0.80f), label = "Race Pace"),
                    HrSegment(distanceMeters = 21100f, targetHr = karvonen(maxHr, restHr, 0.85f), label = "Strong Finish")
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
        description = "Negative-split strategy across 42.2 km.",
        category = PresetCategory.RACE_PREP,
        durationLabel = "42.2 km",
        intensityLabel = "Moderate",
        buildConfig = { maxHr, restHr ->
            WorkoutConfig(
                mode = WorkoutMode.DISTANCE_PROFILE,
                segments = listOf(
                    HrSegment(distanceMeters = 10000f, targetHr = karvonen(maxHr, restHr, 0.70f), label = "Easy Start"),
                    HrSegment(distanceMeters = 32000f, targetHr = karvonen(maxHr, restHr, 0.75f), label = "Marathon Pace"),
                    HrSegment(distanceMeters = 42200f, targetHr = karvonen(maxHr, restHr, 0.78f), label = "Final Push")
                ),
                bufferBpm = 4,
                presetId = "marathon_prep"
            )
        }
    )
}
```

- [ ] **Step 6: Update PresetLibraryTest to pass restHr**

In `app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt`, update all `buildConfig(maxHr)` calls to `buildConfig(maxHr, restHr)`. Use `restHr = 60` as a consistent test value alongside `maxHr = 180`.

For the zone2 base target assertion: the old test expects `(180 * 0.68).roundToInt() = 122`. The new Karvonen result is `60 + (120 * 0.68).roundToInt() = 60 + 82 = 142`. Update accordingly:

```kotlin
@Test
fun `zone2 base - target HR uses Karvonen formula`() {
    val config = PresetLibrary.ALL.first { it.id == "zone2_base" }.buildConfig(180, 60)
    // Karvonen: 60 + (180-60) * 0.68 = 60 + 81.6 = 142
    assertEquals(142, config.steadyStateTargetHr)
}
```

Update all other test methods similarly — replace `buildConfig(180)` with `buildConfig(180, 60)` and adjust any hardcoded expected HR values to match Karvonen math.

- [ ] **Step 7: Run all preset tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.*" 2>&1 | tail -20`

Expected: All tests PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/preset/PresetLibrary.kt \
       app/src/test/java/com/hrcoach/domain/preset/KarvonenTest.kt \
       app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt
git commit -m "feat: switch PresetLibrary to Karvonen (HR Reserve) formula"
```

---

### Task 3: Update All buildConfig Callers to Pass restHr

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt` (~line 429)
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt` (~line 1069)
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt` (~line 2969)
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt` (~line 1169)

Each caller currently calls `preset.buildConfig(maxHr)`. Each needs to become `preset.buildConfig(maxHr, restHr)` where `restHr` is sourced from the `AdaptiveProfile` or falls back to an age-based default.

- [ ] **Step 1: Add restHr default helper**

Create a top-level utility function. Add to the bottom of `app/src/main/java/com/hrcoach/domain/model/AdaptiveProfile.kt`:

```kotlin
/**
 * Age-based resting HR default when no measurement exists yet.
 * Conservative population estimate; errs slightly high (→ higher Karvonen targets).
 */
fun defaultRestHr(age: Int?): Int =
    if (age != null) (72 - 0.2 * age).roundToInt().coerceIn(55, 75) else 65
```

Add `import kotlin.math.roundToInt` to the file if not already present.

- [ ] **Step 2: Update SetupViewModel.buildConfigOrNull()**

The `SetupViewModel` has access to `UserProfileRepository` (for maxHr and age) and should also read `AdaptiveProfileRepository` for `hrRest`. Find the `buildConfigOrNull()` function (~line 429).

Change `preset.buildConfig(maxHr)` to:

```kotlin
val restHr = adaptiveProfileRepository.getProfile().hrRest?.roundToInt()
    ?: defaultRestHr(userProfileRepository.getAge())
return preset.buildConfig(maxHr, restHr).copy(
    bufferBpm = buffer,
    alertDelaySec = delay,
    alertCooldownSec = cooldown
)
```

If `adaptiveProfileRepository` is not already injected into `SetupViewModel`, add it as a constructor parameter.

- [ ] **Step 3: Update SetupScreen SegmentTimelineStrip**

The `SetupScreen` composable at ~line 1069 calls `preset.buildConfig(180)` with a hardcoded maxHr for preview rendering. Change to:

```kotlin
val config = remember(preset.id) { preset.buildConfig(180, 60) }
```

This is a preview-only context (showing segment layout), so hardcoded values are acceptable.

- [ ] **Step 4: Update BootcampScreen.buildConfigJson()**

At ~line 2969, the function takes `maxHr: Int?`. Add a `restHr: Int` parameter:

```kotlin
fun buildConfigJson(session: PlannedSession, maxHr: Int?, restHr: Int): String
```

Change `preset.buildConfig(maxHr)` to `preset.buildConfig(maxHr, restHr)`.

Update the caller of `buildConfigJson` to pass `restHr` from the same source as SetupViewModel (AdaptiveProfile or age default).

- [ ] **Step 5: Update BootcampViewModel.buildWorkoutConfig()**

At ~line 1169, the function takes `maxHr: Int`. Add `restHr: Int`:

```kotlin
fun buildWorkoutConfig(session: PlannedSession, maxHr: Int, restHr: Int): String
```

Change `preset.buildConfig(maxHr)` to `preset.buildConfig(maxHr, restHr)`.

Update the caller to pass `restHr` sourced from AdaptiveProfile or age-based default.

- [ ] **Step 6: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -30`

Expected: PASS — no compilation errors.

- [ ] **Step 7: Run all tests**

Run: `./gradlew testDebugUnitTest 2>&1 | tail -30`

Expected: All tests PASS (some SessionSelector tests may need restHr passthrough updates — fix any failures).

- [ ] **Step 8: Commit**

```bash
git add -u
git commit -m "refactor: pass restHr through all buildConfig call sites"
```

---

### Task 4: Update ZoneEducationProvider to Karvonen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/education/ZoneEducationProvider.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/education/ZoneEducationProviderTest.kt`

- [ ] **Step 1: Write the failing test**

Add a new test to `ZoneEducationProviderTest.kt`:

```kotlin
@Test
fun `BPM range uses Karvonen formula when restHr is provided`() {
    // Zone 2: 60-70% of reserve. maxHr=191, restHr=60, reserve=131
    // low: 60 + 131*0.60 = 139, high: 60 + 131*0.70 = 152
    // with buffer 5: "134–157 BPM"
    val content = ZoneEducationProvider.getContent(
        ZoneId.ZONE_2, ContentDensity.ONE_LINER, maxHr = 191, restHr = 60, bufferBpm = 5
    )
    assertTrue(content.contains("134–157 BPM"), "Expected Karvonen range, got: $content")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.education.ZoneEducationProviderTest" 2>&1 | tail -20`

Expected: FAIL — `restHr` parameter doesn't exist yet.

- [ ] **Step 3: Add restHr parameter to ZoneEducationProvider**

Update all public functions to accept `restHr: Int? = null`:

```kotlin
fun getContent(
    zoneId: ZoneId,
    density: ContentDensity,
    maxHr: Int? = null,
    restHr: Int? = null,
    bufferBpm: Int = 5
): String = when (density) {
    ContentDensity.BADGE -> badge(zoneId)
    ContentDensity.ONE_LINER -> oneLiner(zoneId, maxHr, restHr, bufferBpm)
    ContentDensity.FULL -> full(zoneId, maxHr, restHr, bufferBpm)
}
```

Update `forSessionType` similarly to pass `restHr` through.

Update `oneLiner`, `full`, and `bpmRange` to accept and use `restHr`:

```kotlin
private fun bpmRange(zoneId: ZoneId, maxHr: Int?, restHr: Int?, bufferBpm: Int): String? {
    if (maxHr == null) return null
    val (lowPct, highPct) = zonePercentages(zoneId)
    val rest = restHr ?: 0
    val targetLow = (rest + (maxHr - rest) * lowPct).roundToInt()
    val targetHigh = (rest + (maxHr - rest) * highPct).roundToInt()
    return "${targetLow - bufferBpm}\u2013${targetHigh + bufferBpm} BPM"
}
```

When `restHr` is null, it defaults to 0, which makes the formula degenerate to simple %HRmax — backward compatible.

- [ ] **Step 4: Update existing tests**

The existing test `BPM range uses buffer correctly` checks specific numbers based on %HRmax. Update its expected values or add `restHr = 0` to preserve old behavior for that specific test.

- [ ] **Step 5: Run all ZoneEducationProvider tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.education.ZoneEducationProviderTest" 2>&1 | tail -20`

Expected: All tests PASS.

- [ ] **Step 6: Update callers to pass restHr**

Search for all callers of `ZoneEducationProvider.getContent` and `forSessionType`. Pass `restHr` where available (from AdaptiveProfile), or omit (defaults to null → old behavior for education-only contexts where restHr isn't critical).

- [ ] **Step 7: Commit**

```bash
git add -u
git commit -m "feat: switch ZoneEducationProvider BPM ranges to Karvonen"
```

---

### Task 5: Adjust MetricsCalculator Resting HR Proxy

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/engine/MetricsCalculator.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/engine/MetricsCalculatorEdgeCaseTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `MetricsCalculatorEdgeCaseTest.kt`:

```kotlin
@Test
fun `restingHrProxy uses minus-5 offset and clamps to 40`() {
    // min HR = 62 in first 60s → (62 - 5) = 57
    val points = listOf(
        trackPoint(0L, 62), trackPoint(20_000L, 64), trackPoint(40_000L, 65)
    )
    val result = MetricsCalculator.computeRestingHrProxy(points)
    assertEquals(57f, result)
}

@Test
fun `restingHrProxy clamps to 40 minimum`() {
    val points = listOf(
        trackPoint(0L, 42), trackPoint(20_000L, 43), trackPoint(40_000L, 44)
    )
    val result = MetricsCalculator.computeRestingHrProxy(points)
    assertEquals(40f, result)  // (42 - 5) = 37, clamped to 40
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.MetricsCalculatorEdgeCaseTest" 2>&1 | tail -20`

Expected: FAIL — current code uses -10 offset and clamps to 30.

- [ ] **Step 3: Update the offset and floor**

In `MetricsCalculator.kt`, change line ~208:

```kotlin
// Before:
return (minHr - 10f).coerceAtLeast(30f)

// After:
return (minHr - 5f).coerceAtLeast(40f)
```

- [ ] **Step 4: Update existing test for clamp-to-30 → clamp-to-40**

The existing test `restingHrProxy clamps to 30 minimum` needs updating. Change the expected value from 30f to 40f and adjust the test name:

```kotlin
@Test
fun `restingHrProxy clamps to 40 minimum`() {
    // ... existing test body with updated assertion
}
```

Also update `restingHrProxy returns valid proxy for stable low HR` — the expected result changes from `(minHr - 10)` to `(minHr - 5)`.

- [ ] **Step 5: Run all MetricsCalculator tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.MetricsCalculator*" 2>&1 | tail -20`

Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/engine/MetricsCalculator.kt \
       app/src/test/java/com/hrcoach/domain/engine/MetricsCalculatorEdgeCaseTest.kt
git commit -m "fix: adjust resting HR proxy offset from -10 to -5, floor from 30 to 40"
```

---

### Task 6: Create SubMaxHrEstimator

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/engine/SubMaxHrEstimator.kt`
- Create: `app/src/test/java/com/hrcoach/domain/engine/SubMaxHrEstimatorTest.kt`

- [ ] **Step 1: Write the tests**

Create `app/src/test/java/com/hrcoach/domain/engine/SubMaxHrEstimatorTest.kt`:

```kotlin
package com.hrcoach.domain.engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SubMaxHrEstimatorTest {

    @Test
    fun `returns null when session too short`() {
        // 5 minutes of HR data (need 10)
        val hrSamples = (1..60).map { HrSample(it * 5_000L, 155) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 191, age = 29, isCalibrated = false)
        assertNull(result)
    }

    @Test
    fun `returns null when already calibrated`() {
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 170) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 191, age = 29, isCalibrated = true)
        assertNull(result)
    }

    @Test
    fun `returns null when sustained peak too low`() {
        // Peak at 130 = 68% of 191 → below 70% threshold → skip
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 130) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 191, age = 29, isCalibrated = false)
        assertNull(result)
    }

    @Test
    fun `returns null when estimate is below current max`() {
        // Peak at 145 = 76% of 191 → effort 0.75 → estimate 145/0.75 = 193 → 193 > 191 → would update
        // But peak at 140 = 73% of 191 → effort 0.75 → estimate 140/0.75 = 187 → 187 < 191 → null
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 140) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 191, age = 29, isCalibrated = false)
        assertNull(result)
    }

    @Test
    fun `estimates higher max from moderate effort`() {
        // Sustained peak ~160, current max 180 (from 220-40)
        // 160/180 = 0.89 → effort fraction 0.92
        // estimate = 160/0.92 = 174 → 174 < 180 → null
        // Try: sustained peak 170, current max 180
        // 170/180 = 0.94 → effort 0.92 → estimate = 170/0.92 = 185 → 185 > 180 ✓
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 170) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 180, age = 40, isCalibrated = false)
        assertEquals(185, result)
    }

    @Test
    fun `caps estimate at 220-age+20`() {
        // Sustained peak 190, current max 180, age 40
        // 190/180 = 1.06 → effort 0.92 → estimate = 190/0.92 = 207
        // ceiling = 220-40+20 = 200 → capped to 200
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 190) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 180, age = 40, isCalibrated = false)
        assertEquals(200, result)
    }

    @Test
    fun `never estimates below 220-age`() {
        // Sustained peak 145, current max 150, age 29
        // 145/150 = 0.97 → effort 0.92 → estimate = 145/0.92 = 158
        // floor = 220-29 = 191 → 158 < 191 → null (below floor AND below current)
        val hrSamples = (1..240).map { HrSample(it * 5_000L, 145) }
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 150, age = 29, isCalibrated = false)
        assertNull(result)
    }

    @Test
    fun `uses 2-minute rolling average not spike`() {
        // 10 min of data at 140 BPM with a single spike to 200
        val hrSamples = (1..120).map { i ->
            val hr = if (i == 60) 200 else 140
            HrSample(i * 5_000L, hr)
        }
        // 2-min rolling avg won't be pushed much by a single spike
        // Sustained peak should be ~141, not 200
        // 141/191 = 0.74 → effort 0.75 → estimate = 141/0.75 = 188 → 188 < 191 → null
        val result = SubMaxHrEstimator.estimate(hrSamples, currentHrMax = 191, age = 29, isCalibrated = false)
        assertNull(result)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.SubMaxHrEstimatorTest" 2>&1 | tail -20`

Expected: FAIL — class doesn't exist.

- [ ] **Step 3: Implement SubMaxHrEstimator**

Create `app/src/main/java/com/hrcoach/domain/engine/SubMaxHrEstimator.kt`:

```kotlin
package com.hrcoach.domain.engine

import kotlin.math.roundToInt

/** Lightweight HR sample for submaximal estimation. */
data class HrSample(val timestampMs: Long, val hr: Int)

/**
 * Estimates HRmax from submaximal workout data.
 *
 * Analyzes sustained peak HR (2-minute rolling average) and back-calculates
 * max HR based on estimated effort fraction. Only revises upward.
 */
object SubMaxHrEstimator {

    private const val MIN_DURATION_MS = 10 * 60 * 1_000L  // 10 minutes
    private const val ROLLING_WINDOW_MS = 2 * 60 * 1_000L // 2-minute rolling avg

    /**
     * Returns a new HRmax estimate, or null if no update is warranted.
     *
     * @param samples HR samples sorted by timestamp
     * @param currentHrMax current HRmax value
     * @param age user age for floor/ceiling calculation
     * @param isCalibrated true if HRmax was self-reported (skip inference)
     */
    fun estimate(
        samples: List<HrSample>,
        currentHrMax: Int,
        age: Int,
        isCalibrated: Boolean
    ): Int? {
        if (isCalibrated) return null
        if (samples.size < 2) return null

        val sorted = samples.sortedBy { it.timestampMs }
        val duration = sorted.last().timestampMs - sorted.first().timestampMs
        if (duration < MIN_DURATION_MS) return null

        val sustainedPeak = rollingAvgPeak(sorted)
        if (sustainedPeak == null) return null

        val ratio = sustainedPeak.toFloat() / currentHrMax
        if (ratio < 0.70f) return null  // Too easy, not informative

        val effortFraction = when {
            ratio >= 0.88f -> 0.92f
            ratio >= 0.80f -> 0.85f
            else           -> 0.75f
        }

        val estimated = (sustainedPeak / effortFraction).roundToInt()

        val floor = 220 - age
        val ceiling = 220 - age + 20

        val capped = estimated.coerceIn(floor, ceiling)

        return if (capped > currentHrMax) capped else null
    }

    /**
     * Computes the highest 2-minute rolling average HR from sorted samples.
     */
    private fun rollingAvgPeak(sorted: List<HrSample>): Int? {
        if (sorted.size < 2) return null
        var maxAvg = 0.0
        var windowStart = 0

        for (windowEnd in sorted.indices) {
            // Advance windowStart so the window is ≤ ROLLING_WINDOW_MS
            while (windowStart < windowEnd &&
                sorted[windowEnd].timestampMs - sorted[windowStart].timestampMs > ROLLING_WINDOW_MS
            ) {
                windowStart++
            }
            if (sorted[windowEnd].timestampMs - sorted[windowStart].timestampMs >= ROLLING_WINDOW_MS / 2) {
                val avg = sorted.subList(windowStart, windowEnd + 1).map { it.hr }.average()
                if (avg > maxAvg) maxAvg = avg
            }
        }
        return if (maxAvg > 0) maxAvg.roundToInt() else null
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.SubMaxHrEstimatorTest" 2>&1 | tail -20`

Expected: All tests PASS. If any fail, adjust the rolling average window logic or test expectations.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/engine/SubMaxHrEstimator.kt \
       app/src/test/java/com/hrcoach/domain/engine/SubMaxHrEstimatorTest.kt
git commit -m "feat: add SubMaxHrEstimator for submaximal HRmax inference"
```

---

### Task 7: Integrate SubMaxHrEstimator into AdaptiveProfileRebuilder

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/engine/AdaptiveProfileRebuilder.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/engine/AdaptiveProfileRebuilderTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `AdaptiveProfileRebuilderTest.kt`:

```kotlin
@Test
fun `submaximal inference updates hrMax when sustained peak suggests higher max`() {
    // User age 40, 220-40=180 as initial HRmax
    // Workout with sustained HR around 170 for 15 minutes
    // 170/180 = 0.94 → effort 0.92 → estimate 170/0.92 = 185
    // 185 > 180 → update
    val workout = makeWorkout(id = 1L, startTime = 1000L, durationSec = 900)
    val points = (1..180).map { i ->
        makeTrackPoint(workoutId = 1L, timestamp = 1000L + i * 5_000L, heartRate = 170)
    }
    val profile = AdaptiveProfileRebuilder.rebuild(
        workouts = listOf(workout),
        trackPointsByWorkout = mapOf(1L to points),
        metricsByWorkout = emptyMap(),
        isWorkoutRunning = { false },
        age = 40
    )
    assertTrue(profile.hrMax!! > 180, "Expected hrMax > 180 from submaximal inference, got ${profile.hrMax}")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.AdaptiveProfileRebuilderTest" 2>&1 | tail -20`

Expected: FAIL — `rebuild` doesn't accept `age` parameter yet, and submaximal inference isn't integrated.

- [ ] **Step 3: Add age parameter to rebuild and integrate SubMaxHrEstimator**

In `AdaptiveProfileRebuilder.rebuild()`, add `age: Int? = null` parameter. After the existing hrMax tracking logic (~line 120), add:

```kotlin
// Submaximal HRmax inference (first 3 qualifying workouts)
if (age != null && profile.totalSessions <= 3 && !profile.hrMaxIsCalibrated) {
    val hrSamples = points.sortedBy { it.timestamp }.map {
        HrSample(it.timestamp, it.heartRate)
    }
    val currentMax = profile.hrMax ?: (220 - age)
    val subMaxEstimate = SubMaxHrEstimator.estimate(
        samples = hrSamples,
        currentHrMax = currentMax,
        age = age,
        isCalibrated = profile.hrMaxIsCalibrated
    )
    if (subMaxEstimate != null) {
        profile = profile.copy(hrMax = subMaxEstimate)
    }
}
```

- [ ] **Step 4: Update existing tests**

Add `age = 29` (or appropriate value) to existing `rebuild()` calls in `AdaptiveProfileRebuilderTest.kt` to match the new signature. Existing tests should still pass since `age` defaults to null and the new logic is guarded by `age != null`.

- [ ] **Step 5: Run all tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.AdaptiveProfileRebuilderTest" 2>&1 | tail -20`

Expected: All tests PASS.

- [ ] **Step 6: Update the caller of rebuild**

Find where `AdaptiveProfileRebuilder.rebuild()` is called (likely in a repository or service). Pass the user's age from `UserProfileRepository.getAge()`.

- [ ] **Step 7: Commit**

```bash
git add -u
git commit -m "feat: integrate SubMaxHrEstimator into AdaptiveProfileRebuilder"
```

---

### Task 8: Add guidanceTag to WorkoutConfig and zone2_with_strides Preset

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/model/WorkoutConfig.kt`
- Modify: `app/src/main/java/com/hrcoach/domain/preset/PresetLibrary.kt`

- [ ] **Step 1: Add guidanceTag field to WorkoutConfig**

Add after `bootcampWeekNumber`:

```kotlin
val guidanceTag: String? = null
```

- [ ] **Step 2: Add zone2_with_strides preset to PresetLibrary**

Add a new private function and include it in the `ALL` list:

```kotlin
val ALL: List<WorkoutPreset> = listOf(
    zone2Base(), zone2WithStrides(), aeroTempo(), lactateThreshold(),
    norwegian4x4(), hiit3030(), hillRepeats(),
    halfMarathonPrep(), marathonPrep()
)

private fun zone2WithStrides() = WorkoutPreset(
    id = "zone2_with_strides",
    name = "Easy + Strides",
    subtitle = "Aerobic base with pickups",
    description = "45–75 min easy run with 4–6 × 20-sec strides after 20 minutes.",
    category = PresetCategory.BASE_AEROBIC,
    durationLabel = "45–75 min",
    intensityLabel = "Easy",
    buildConfig = { maxHr, restHr ->
        WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = karvonen(maxHr, restHr, 0.68f),
            bufferBpm = 5,
            presetId = "zone2_with_strides",
            sessionLabel = "Easy + Strides",
            guidanceTag = "strides"
        )
    }
)
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -20`

Expected: PASS.

- [ ] **Step 4: Add test for the new preset**

Add to `PresetLibraryTest.kt`:

```kotlin
@Test
fun `zone2 with strides has strides guidance tag`() {
    val config = PresetLibrary.ALL.first { it.id == "zone2_with_strides" }.buildConfig(180, 60)
    assertEquals("strides", config.guidanceTag)
    assertEquals("Easy + Strides", config.sessionLabel)
    assertEquals(142, config.steadyStateTargetHr)  // Same as zone2_base Karvonen
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.PresetLibraryTest" 2>&1 | tail -20`

Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add -u
git commit -m "feat: add zone2_with_strides preset and guidanceTag field"
```

---

### Task 9: Assign Strides in BASE Phase via SessionSelector

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt`

- [ ] **Step 1: Write the failing test**

Add to `SessionSelectorTest.kt`:

```kotlin
@Test
fun `BASE phase tier 1 assigns one strides session per week`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.BASE,
        goal = BootcampGoal.RACE_10K,
        runsPerWeek = 3,
        targetMinutes = 45,
        tierIndex = 1
    )
    val stridesSessions = sessions.filter { it.presetId == "zone2_with_strides" }
    assertEquals(1, stridesSessions.size, "Expected exactly 1 strides session in BASE tier 1")
}

@Test
fun `BASE phase tier 0 has no strides sessions`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.BASE,
        goal = BootcampGoal.RACE_10K,
        runsPerWeek = 3,
        targetMinutes = 45,
        tierIndex = 0
    )
    val stridesSessions = sessions.filter { it.presetId == "zone2_with_strides" }
    assertEquals(0, stridesSessions.size, "Tier 0 should have no strides in BASE")
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest" 2>&1 | tail -20`

Expected: First test FAIL (no strides assigned yet), second test PASS (already no strides).

- [ ] **Step 3: Update SessionSelector.baseAerobicWeek()**

In the `baseAerobicWeek()` function, after building the list of all-easy sessions, replace the last easy run with strides for Tier 1+:

```kotlin
private fun baseAerobicWeek(
    runsPerWeek: Int,
    targetMinutes: Int,
    tierIndex: Int,
    // ... existing params
): List<PlannedSession> {
    // ... existing easy/long session logic ...

    // Add strides to last easy run for Tier 1+
    if (tierIndex >= 1) {
        val lastEasyIndex = sessions.indexOfLast { it.type == SessionType.EASY }
        if (lastEasyIndex >= 0) {
            sessions[lastEasyIndex] = sessions[lastEasyIndex].copy(
                type = SessionType.STRIDES,
                presetId = "zone2_with_strides"
            )
        }
    }

    return sessions
}
```

Note: The exact edit depends on how `baseAerobicWeek` currently builds its list. The implementing agent should read the function, understand its structure, and insert the strides logic at the end before the return.

- [ ] **Step 4: Run all SessionSelector tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest" 2>&1 | tail -20`

Expected: All tests PASS including the two new ones.

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "feat: assign strides to one BASE phase session per week for tier 1+"
```

---

### Task 10: Onboarding Copy and Guidance Text

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/onboarding/OnboardingPages.kt`
- Modify: Workout guidance display (find the composable that renders `WorkoutState.guidanceText` or similar)

- [ ] **Step 1: Update onboarding HRmax label**

In `OnboardingPages.kt`, find the estimated HRmax display on Page 1 (Profile page). Add a subtitle/caption beneath the HRmax value:

```kotlin
Text(
    text = "Estimated — improves after your first run",
    style = MaterialTheme.typography.bodySmall,
    color = Color.White.copy(alpha = 0.5f)
)
```

- [ ] **Step 2: Add Zone 2 guidance text**

Find where workout guidance/coaching text is displayed during an active workout. When the current preset is `zone2_base` or `zone2_with_strides`, include:

```kotlin
"Easy pace builds your aerobic engine. It should feel comfortable enough to hold a conversation."
```

- [ ] **Step 3: Add strides guidance text**

When `WorkoutConfig.guidanceTag == "strides"` and elapsed time ≥ 20 minutes, display:

```kotlin
"Time for strides! Run 4–6 × 20-second accelerations at a fast but smooth effort. Jog easy for 60–90 seconds between each."
```

The implementing agent should find the composable or service that manages guidance text and add these conditions. The exact location depends on how the existing guidance system works — check `WorkoutForegroundService` and the active workout UI.

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileDebugKotlin 2>&1 | tail -20`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -u
git commit -m "feat: add onboarding HRmax label and workout guidance text"
```

---

### Task 11: Final Integration Test and Cleanup

**Files:**
- All modified files

- [ ] **Step 1: Run full test suite**

Run: `./gradlew testDebugUnitTest 2>&1 | tail -40`

Expected: All tests PASS.

- [ ] **Step 2: Run compilation check**

Run: `./gradlew assembleDebug 2>&1 | tail -20`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run lint**

Run: `./gradlew lint 2>&1 | tail -20`

Expected: No new errors introduced.

- [ ] **Step 4: Verify Karvonen math end-to-end**

Write a quick integration-style test that exercises the full flow: preset lookup → buildConfig with Karvonen → verify target HR. Add to `PresetLibraryTest.kt`:

```kotlin
@Test
fun `end-to-end Karvonen - fit 29yo gets realistic Zone 2 target`() {
    // Age 29, HRmax 191, resting HR 60
    val config = PresetLibrary.ALL.first { it.id == "zone2_base" }.buildConfig(191, 60)
    // Karvonen: 60 + (131 * 0.68) = 60 + 89.08 = 149
    assertEquals(149, config.steadyStateTargetHr)
    // This is a real easy-run target, not the old 130 BPM shuffle
}

@Test
fun `end-to-end Karvonen - sedentary user gets appropriate Zone 2 target`() {
    // Age 50, HRmax 170, resting HR 75
    val config = PresetLibrary.ALL.first { it.id == "zone2_base" }.buildConfig(170, 75)
    // Karvonen: 75 + (95 * 0.68) = 75 + 64.6 = 140
    assertEquals(140, config.steadyStateTargetHr)
}
```

- [ ] **Step 5: Run final tests**

Run: `./gradlew testDebugUnitTest 2>&1 | tail -30`

Expected: All tests PASS.

- [ ] **Step 6: Final commit**

```bash
git add -u
git commit -m "test: add end-to-end Karvonen integration tests"
```
