# Adaptive Bootcamp Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Wire the existing adaptive fitness signal collection into the Bootcamp plan engine, making session selection continuously adjust within a tier (silently) and prompt the user to change tiers when their measured fitness significantly diverges from their current program.

**Architecture:** Pure-domain signal evaluation (`FitnessSignalEvaluator`) feeds two control paths: a `TuningDirection` that shifts a discrete preset index within the current session type (Level 1, silent), and a `TierPromptState` that surfaces a user-consented program change (Level 2, explicit). All heavy computation is unit-testable pure Kotlin — no Android dependencies except at the repository/service boundary.

**Tech Stack:** Kotlin, Room (SQLite), SharedPreferences + Gson (AdaptiveProfile), Hilt DI, Jetpack Compose, JUnit 4 unit tests.

**Design doc:** `docs/plans/2026-03-04-adaptive-bootcamp-design.md` — read it before starting any task.

---

## Task 1: DB Migration 5 — New Columns

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/db/WorkoutMetricsEntity.kt`
- Modify: `app/src/main/java/com/hrcoach/data/db/BootcampEnrollmentEntity.kt`
- Modify: `app/src/main/java/com/hrcoach/data/db/AppDatabase.kt`

**Context:** Room requires a migration for every schema change. Current version is 4. New columns use `DEFAULT` values so existing rows are valid immediately. `AdaptiveProfile` is stored in SharedPreferences as JSON — adding fields there requires no migration (Gson uses field defaults for missing keys).

**Step 1: Add new columns to `WorkoutMetricsEntity`**

```kotlin
data class WorkoutMetricsEntity(
    val workoutId: Long,
    val recordedAtMs: Long,
    val avgPaceMinPerKm: Float? = null,
    val avgHr: Float? = null,
    val hrAtSixMinPerKm: Float? = null,
    val settleDownSec: Float? = null,
    val settleUpSec: Float? = null,
    val longTermHrTrimBpm: Float = 0f,
    val responseLagSec: Float = 25f,
    val efficiencyFactor: Float? = null,
    val aerobicDecoupling: Float? = null,
    val efFirstHalf: Float? = null,
    val efSecondHalf: Float? = null,
    val heartbeatsPerKm: Float? = null,
    val paceAtRefHrMinPerKm: Float? = null,
    // New in v5
    val hrr1Bpm: Float? = null,
    val trimpScore: Float? = null,
    val trimpReliable: Boolean = true,
    val environmentAffected: Boolean = false
)
```

**Step 2: Add new columns to `BootcampEnrollmentEntity`**

```kotlin
data class BootcampEnrollmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalType: String,
    val targetMinutesPerRun: Int,
    val runsPerWeek: Int,
    val preferredDays: String,
    val startDate: Long,
    val currentPhaseIndex: Int = 0,
    val currentWeekInPhase: Int = 0,
    val status: String = STATUS_ACTIVE,
    // New in v5
    val tierIndex: Int = 0,
    val tierPromptSnoozedUntilMs: Long = 0L,
    val tierPromptDismissCount: Int = 0
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_PAUSED = "PAUSED"
        fun parsePreferredDays(json: String): List<Int> =
            json.removeSurrounding("[", "]").split(",").mapNotNull { it.trim().toIntOrNull() }
    }
}
```

**Step 3: Add MIGRATION_4_5 to `AppDatabase`**

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_metrics ADD COLUMN hrr1Bpm REAL")
        db.execSQL("ALTER TABLE workout_metrics ADD COLUMN trimpScore REAL")
        db.execSQL("ALTER TABLE workout_metrics ADD COLUMN trimpReliable INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE workout_metrics ADD COLUMN environmentAffected INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE bootcamp_enrollments ADD COLUMN tierIndex INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE bootcamp_enrollments ADD COLUMN tierPromptSnoozedUntilMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE bootcamp_enrollments ADD COLUMN tierPromptDismissCount INTEGER NOT NULL DEFAULT 0")
    }
}
```

Also update the `@Database` annotation: `version = 5` and add `MIGRATION_4_5` to wherever migrations are registered (check `di/AppModule.kt` for `.addMigrations(...)` call).

**Step 4: Run the build to verify Room compiles**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. If Room complains about schema mismatch, you forgot to update `version = 5` in `@Database`.

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/
git commit -m "feat(db): migration 5 - add TRIMP, HRR1, env flags, tier columns"
```

---

## Task 2: Extend `AdaptiveProfile` with Load and HR Calibration Fields

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/model/AdaptiveProfile.kt`

**Context:** `AdaptiveProfile` is serialized to JSON in SharedPreferences. Adding new fields with defaults is backward-compatible — Gson will deserialize old JSON and fill new fields with their Kotlin defaults. No migration needed.

**Step 1: Add fields**

```kotlin
data class AdaptiveProfile(
    val longTermHrTrimBpm: Float = 0f,
    val responseLagSec: Float = 25f,
    val paceHrBuckets: Map<Int, PaceHrBucket> = emptyMap(),
    val totalSessions: Int = 0,
    // Load tracking
    val ctl: Float = 0f,           // Chronic Training Load (42-day EWMA of TRIMP)
    val atl: Float = 0f,           // Acute Training Load (7-day EWMA)
    val lastTRIMP: Float = 0f,     // Most recent session TRIMP score
    // HR calibration
    val hrMax: Int = 0,            // 0 = not yet observed (onboarding sets initial estimate)
    val hrRest: Float = 60f        // Rolling minimum pre-workout HR
)
```

**Step 2: Run existing tests to confirm no regressions**

```bash
./gradlew testDebugUnitTest
```
Expected: all existing tests pass (AdaptiveProfile is a data class with defaults — no existing code breaks).

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/model/AdaptiveProfile.kt
git commit -m "feat(model): add CTL/ATL/TRIMP and HR calibration fields to AdaptiveProfile"
```

---

## Task 3: Banister TRIMP Calculation

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/engine/MetricsCalculator.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/engine/MetricsCalculatorTest.kt`

**Context:** Banister TRIMP = `duration_min × ΔHR × e^(b × ΔHR)` where `ΔHR = (avgHr - hrRest) / (hrMax - hrRest)` and `b = 1.92`. This is the per-session load unit that feeds CTL/ATL. It exponentially penalises high-intensity sessions.

**Step 1: Write failing test**

Add to `MetricsCalculatorTest`:

```kotlin
@Test
fun `calculateTRIMP returns expected value for moderate effort`() {
    // 60-min run, avg HR 150, rest 60, max 190
    // deltaHR = (150-60)/(190-60) = 90/130 = 0.692
    // TRIMP = 60 × 0.692 × e^(1.92 × 0.692) = 60 × 0.692 × e^1.329
    //       = 60 × 0.692 × 3.777 ≈ 156.8
    val result = MetricsCalculator.calculateTRIMP(
        durationMin = 60f,
        avgHr = 150f,
        hrRest = 60f,
        hrMax = 190
    )
    assertEquals(156.8f, result, 2f)
}

@Test
fun `calculateTRIMP returns 0 when hrMax equals hrRest`() {
    val result = MetricsCalculator.calculateTRIMP(
        durationMin = 60f,
        avgHr = 150f,
        hrRest = 190f,
        hrMax = 190
    )
    assertEquals(0f, result, 0.001f)
}

@Test
fun `calculateTRIMP scales exponentially - high intensity costs more than double easy`() {
    val easy = MetricsCalculator.calculateTRIMP(60f, avgHr = 130f, hrRest = 60f, hrMax = 190)
    val hard = MetricsCalculator.calculateTRIMP(60f, avgHr = 175f, hrRest = 60f, hrMax = 190)
    // Hard at 175/190 should cost more than 2× easy at 130/190
    assert(hard > easy * 2) { "hard=$hard easy=$easy" }
}
```

**Step 2: Run to verify failure**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.MetricsCalculatorTest" 2>&1 | tail -20
```
Expected: FAILED — `calculateTRIMP` does not exist.

**Step 3: Implement in `MetricsCalculator`**

```kotlin
fun calculateTRIMP(
    durationMin: Float,
    avgHr: Float,
    hrRest: Float,
    hrMax: Int
): Float {
    val range = hrMax - hrRest
    if (range <= 0f) return 0f
    val deltaHrRatio = (avgHr - hrRest) / range
    if (deltaHrRatio <= 0f) return 0f
    val b = 1.92f
    return durationMin * deltaHrRatio * exp((b * deltaHrRatio).toDouble()).toFloat()
}
```

**Step 4: Run tests**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.MetricsCalculatorTest"
```
Expected: all tests pass.

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/engine/MetricsCalculator.kt
git add app/src/test/java/com/hrcoach/domain/engine/MetricsCalculatorTest.kt
git commit -m "feat(metrics): add Banister TRIMP calculation"
```

---

## Task 4: CTL / ATL / TSB Update Logic

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/engine/FitnessLoadCalculator.kt`
- Create: `app/src/test/java/com/hrcoach/domain/engine/FitnessLoadCalculatorTest.kt`

**Context:** CTL and ATL are exponentially weighted moving averages of daily TRIMP. On rest days TRIMP = 0, so both decay. `TSB = CTL - ATL`. All three are stored in `AdaptiveProfile` and updated after each session.

The update formula per day since last session:
```
decay_factor(tau, days) = e^(-days / tau)
new_CTL = old_CTL * decay_factor(42, days) + TRIMP * (1 - decay_factor(42, days))
new_ATL = old_ATL * decay_factor(7,  days) + TRIMP * (1 - decay_factor(7,  days))
```

**Step 1: Write failing tests**

```kotlin
package com.hrcoach.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessLoadCalculatorTest {

    @Test
    fun `updateLoads after one session increases CTL and ATL`() {
        val result = FitnessLoadCalculator.updateLoads(
            currentCtl = 0f, currentAtl = 0f,
            trimpScore = 100f, daysSinceLast = 1
        )
        assertTrue("CTL should increase", result.ctl > 0f)
        assertTrue("ATL should increase", result.atl > 0f)
        assertTrue("ATL should be higher than CTL for single session", result.atl > result.ctl)
    }

    @Test
    fun `updateLoads TSB is negative after hard training`() {
        // Simulate steady training for a while then a hard day
        var ctl = 50f; var atl = 50f
        val r = FitnessLoadCalculator.updateLoads(ctl, atl, trimpScore = 200f, daysSinceLast = 1)
        // ATL spikes faster (7-day tau) than CTL (42-day tau), so TSB goes negative
        assertTrue("TSB negative after load spike: tsb=${r.tsb}", r.tsb < 0f)
    }

    @Test
    fun `updateLoads decays CTL after rest period`() {
        val startCtl = 80f
        val result = FitnessLoadCalculator.updateLoads(
            currentCtl = startCtl, currentAtl = 60f,
            trimpScore = 0f, daysSinceLast = 14
        )
        assertTrue("CTL should decay: was $startCtl, now ${result.ctl}", result.ctl < startCtl)
    }

    @Test
    fun `updateLoads after 6 weeks rest CTL halves`() {
        val startCtl = 100f
        val result = FitnessLoadCalculator.updateLoads(
            currentCtl = startCtl, currentAtl = 0f,
            trimpScore = 0f, daysSinceLast = 42
        )
        // e^(-42/42) = e^-1 ≈ 0.368; so CTL decays to ~36.8% in 42 days
        assertEquals(startCtl * Math.exp(-1.0).toFloat(), result.ctl, 1f)
    }

    @Test
    fun `tsb equals ctl minus atl`() {
        val r = FitnessLoadCalculator.updateLoads(50f, 40f, 80f, 1)
        assertEquals(r.ctl - r.atl, r.tsb, 0.001f)
    }
}
```

**Step 2: Run to verify failure**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.FitnessLoadCalculatorTest"
```
Expected: FAILED — class does not exist.

**Step 3: Implement**

```kotlin
package com.hrcoach.domain.engine

import kotlin.math.exp

object FitnessLoadCalculator {

    data class LoadResult(val ctl: Float, val atl: Float, val tsb: Float)

    private const val CTL_TAU = 42f
    private const val ATL_TAU = 7f

    fun updateLoads(
        currentCtl: Float,
        currentAtl: Float,
        trimpScore: Float,
        daysSinceLast: Int
    ): LoadResult {
        val days = daysSinceLast.coerceAtLeast(0).toFloat()
        val ctlDecay = exp(-days / CTL_TAU).toFloat()
        val atlDecay = exp(-days / ATL_TAU).toFloat()
        val newCtl = currentCtl * ctlDecay + trimpScore * (1f - ctlDecay)
        val newAtl = currentAtl * atlDecay + trimpScore * (1f - atlDecay)
        return LoadResult(ctl = newCtl, atl = newAtl, tsb = newCtl - newAtl)
    }
}
```

**Step 4: Run tests**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.FitnessLoadCalculatorTest"
```
Expected: all pass.

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/engine/FitnessLoadCalculator.kt
git add app/src/test/java/com/hrcoach/domain/engine/FitnessLoadCalculatorTest.kt
git commit -m "feat(engine): add CTL/ATL/TSB load calculation (Banister fitness-fatigue model)"
```

---

## Task 5: HRmax Auto-Detection Logic

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/engine/HrCalibrator.kt`
- Create: `app/src/test/java/com/hrcoach/domain/engine/HrCalibratorTest.kt`

**Context:** If a user's true HRmax is 20 bpm above the stored estimate, their TRIMP scores are severely underestimated (exponential sensitivity). Auto-detection: if HR is sustained above the stored `hrMax` for >8 consecutive seconds, update it. HRrest is maintained as a 30-day rolling minimum of pre-workout lowest-30s-average HR.

**Step 1: Write failing tests**

```kotlin
package com.hrcoach.domain.engine

import org.junit.Assert.*
import org.junit.Test

class HrCalibratorTest {

    @Test
    fun `detectNewHrMax returns new max when sustained above threshold`() {
        // 10 consecutive samples at 195, each 1 second apart
        val samples = List(10) { 195 }
        val result = HrCalibrator.detectNewHrMax(
            currentHrMax = 185,
            recentSamples = samples,
            windowSec = 8
        )
        assertEquals(195, result)
    }

    @Test
    fun `detectNewHrMax returns null when not sustained long enough`() {
        // Only 5 seconds at elevated HR
        val samples = List(5) { 195 } + List(5) { 170 }
        val result = HrCalibrator.detectNewHrMax(
            currentHrMax = 185,
            recentSamples = samples,
            windowSec = 8
        )
        assertNull(result)
    }

    @Test
    fun `detectNewHrMax returns null when below current max`() {
        val samples = List(10) { 180 }
        val result = HrCalibrator.detectNewHrMax(
            currentHrMax = 185,
            recentSamples = samples,
            windowSec = 8
        )
        assertNull(result)
    }

    @Test
    fun `updateHrRest returns lower value when candidate is lower by 2+`() {
        val updated = HrCalibrator.updateHrRest(currentHrRest = 62f, candidate = 58f)
        assertEquals(58f, updated, 0.01f)
    }

    @Test
    fun `updateHrRest ignores candidate within 2 bpm of current`() {
        val updated = HrCalibrator.updateHrRest(currentHrRest = 62f, candidate = 61f)
        assertEquals(62f, updated, 0.01f)
    }
}
```

**Step 2: Run to verify failure**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.HrCalibratorTest"
```

**Step 3: Implement**

```kotlin
package com.hrcoach.domain.engine

object HrCalibrator {

    /**
     * Returns a new hrMax if [recentSamples] contains a sustained run of values above
     * [currentHrMax] for at least [windowSec] consecutive samples (1 sample = 1 second).
     * Returns null if no new max is detected.
     */
    fun detectNewHrMax(
        currentHrMax: Int,
        recentSamples: List<Int>,
        windowSec: Int = 8
    ): Int? {
        var consecutiveCount = 0
        var peakObserved = 0
        for (sample in recentSamples) {
            if (sample > currentHrMax) {
                consecutiveCount++
                if (sample > peakObserved) peakObserved = sample
                if (consecutiveCount >= windowSec) return peakObserved
            } else {
                consecutiveCount = 0
                peakObserved = 0
            }
        }
        return null
    }

    /**
     * Returns an updated resting HR if [candidate] is more than 2 bpm below [currentHrRest].
     * Prevents noise from updating the baseline on a slightly elevated pre-workout HR.
     */
    fun updateHrRest(currentHrRest: Float, candidate: Float): Float =
        if (currentHrRest - candidate >= 2f) candidate else currentHrRest
}
```

**Step 4: Run tests**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.HrCalibratorTest"
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/engine/HrCalibrator.kt
git add app/src/test/java/com/hrcoach/domain/engine/HrCalibratorTest.kt
git commit -m "feat(engine): HRmax auto-detection and HRrest rolling update"
```

---

## Task 6: HR Artifact Rejection

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/engine/HrArtifactDetector.kt`
- Create: `app/src/test/java/com/hrcoach/domain/engine/HrArtifactDetectorTest.kt`

**Context:** Optical wrist HR monitors "cadence lock" — the sensor reads step rate (~160–180 bpm) instead of cardiac rate. Signature: HR jumps >25 bpm in one sample, then stays suspiciously flat (±3 bpm) for >8 seconds, at a pace consistent with easy running. When detected, the session's TRIMP is flagged unreliable and a pace-based estimate is used instead.

**Step 1: Write failing tests**

```kotlin
package com.hrcoach.domain.engine

import org.junit.Assert.*
import org.junit.Test

class HrArtifactDetectorTest {

    @Test
    fun `detects cadence lock pattern`() {
        val samples = buildList {
            repeat(5) { add(135) }   // normal easy HR
            add(165)                  // spike
            repeat(10) { add(163 + (it % 2)) } // flat at cadence rate
        }
        assertTrue(HrArtifactDetector.isCadenceLockSuspected(
            hrSamples = samples,
            jumpThreshold = 25,
            flatWindowSec = 8,
            flatToleranceBpm = 3
        ))
    }

    @Test
    fun `does not flag gradual HR increase`() {
        val samples = (130..160 step 1).map { it } + List(10) { 160 }
        assertFalse(HrArtifactDetector.isCadenceLockSuspected(
            hrSamples = samples,
            jumpThreshold = 25,
            flatWindowSec = 8,
            flatToleranceBpm = 3
        ))
    }

    @Test
    fun `does not flag brief spike that recovers`() {
        val samples = List(10) { 135 } + listOf(165) + List(10) { 137 }
        assertFalse(HrArtifactDetector.isCadenceLockSuspected(
            hrSamples = samples,
            jumpThreshold = 25,
            flatWindowSec = 8,
            flatToleranceBpm = 3
        ))
    }
}
```

**Step 2: Run to verify failure**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.HrArtifactDetectorTest"
```

**Step 3: Implement**

```kotlin
package com.hrcoach.domain.engine

object HrArtifactDetector {

    fun isCadenceLockSuspected(
        hrSamples: List<Int>,
        jumpThreshold: Int = 25,
        flatWindowSec: Int = 8,
        flatToleranceBpm: Int = 3
    ): Boolean {
        if (hrSamples.size < jumpThreshold + flatWindowSec) return false
        for (i in 1 until hrSamples.size - flatWindowSec) {
            val jump = hrSamples[i] - hrSamples[i - 1]
            if (jump >= jumpThreshold) {
                val window = hrSamples.subList(i, i + flatWindowSec)
                val windowMin = window.min()
                val windowMax = window.max()
                if (windowMax - windowMin <= flatToleranceBpm) return true
            }
        }
        return false
    }
}
```

**Step 4: Run tests**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.HrArtifactDetectorTest"
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/engine/HrArtifactDetector.kt
git add app/src/test/java/com/hrcoach/domain/engine/HrArtifactDetectorTest.kt
git commit -m "feat(engine): HR artifact (cadence lock) detection"
```

---

## Task 7: Environmental Session Flagging

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/engine/EnvironmentFlagDetector.kt`
- Create: `app/src/test/java/com/hrcoach/domain/engine/EnvironmentFlagDetectorTest.kt`

**Context:** Heat/hills corrupt EF and decoupling. Auto-flag a session as `environmentAffected` when decoupling >10% AND pace is slower than the runner's recent baseline at equivalent HR by >15 sec/km. When flagged, exclude EF and decoupling from the rolling fitness signal for that session.

**Step 1: Write failing tests**

```kotlin
package com.hrcoach.domain.engine

import org.junit.Assert.*
import org.junit.Test

class EnvironmentFlagDetectorTest {

    @Test
    fun `flags session when decoupling high and pace significantly slower than baseline`() {
        val result = EnvironmentFlagDetector.isEnvironmentAffected(
            aerobicDecoupling = 12f,
            sessionAvgGapPace = 6.5f,      // min/km
            baselineGapPaceAtEquivalentHr = 5.9f  // baseline is 6.5 - 5.9 = 0.6 min/km = 36 sec/km slower
        )
        assertTrue(result)
    }

    @Test
    fun `does not flag when decoupling high but pace normal`() {
        val result = EnvironmentFlagDetector.isEnvironmentAffected(
            aerobicDecoupling = 12f,
            sessionAvgGapPace = 6.1f,
            baselineGapPaceAtEquivalentHr = 6.0f  // only 6 sec/km off
        )
        assertFalse(result)
    }

    @Test
    fun `does not flag when pace slow but decoupling normal`() {
        val result = EnvironmentFlagDetector.isEnvironmentAffected(
            aerobicDecoupling = 4f,
            sessionAvgGapPace = 6.5f,
            baselineGapPaceAtEquivalentHr = 5.9f
        )
        assertFalse(result)
    }

    @Test
    fun `returns false when baseline is null (no history yet)`() {
        val result = EnvironmentFlagDetector.isEnvironmentAffected(
            aerobicDecoupling = 15f,
            sessionAvgGapPace = 7f,
            baselineGapPaceAtEquivalentHr = null
        )
        assertFalse(result)
    }
}
```

**Step 2: Run to verify failure**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.EnvironmentFlagDetectorTest"
```

**Step 3: Implement**

```kotlin
package com.hrcoach.domain.engine

object EnvironmentFlagDetector {

    private const val DECOUPLING_THRESHOLD = 10f     // percent
    private const val PACE_DELTA_THRESHOLD = 0.25f   // min/km = 15 sec/km

    fun isEnvironmentAffected(
        aerobicDecoupling: Float?,
        sessionAvgGapPace: Float?,
        baselineGapPaceAtEquivalentHr: Float?
    ): Boolean {
        if (aerobicDecoupling == null || sessionAvgGapPace == null || baselineGapPaceAtEquivalentHr == null) return false
        val decouplingHigh = aerobicDecoupling > DECOUPLING_THRESHOLD
        val paceSlower = (sessionAvgGapPace - baselineGapPaceAtEquivalentHr) > PACE_DELTA_THRESHOLD
        return decouplingHigh && paceSlower
    }
}
```

**Step 4: Run tests**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.EnvironmentFlagDetectorTest"
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/engine/EnvironmentFlagDetector.kt
git add app/src/test/java/com/hrcoach/domain/engine/EnvironmentFlagDetectorTest.kt
git commit -m "feat(engine): environmental session flagging (heat/hills detection)"
```

---

## Task 8: `FitnessSignalEvaluator` — Core Adaptation Engine

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/engine/FitnessSignalEvaluator.kt`
- Create: `app/src/test/java/com/hrcoach/domain/engine/FitnessSignalEvaluatorTest.kt`

**Context:** This replaces `FitnessEvaluator` (which was called with `emptyList()` — effectively broken). It consumes `AdaptiveProfile` (CTL/ATL/TSB) and recent `WorkoutAdaptiveMetrics` (EF trend, HRR1, decoupling) and outputs three things: a `TuningDirection` for silent session adjustment, a `TierPromptState` for user-consented tier change, and an `IllnessFlag` for anomaly detection. All pure Kotlin — no Android imports.

**Step 1: Create supporting enums/classes**

```kotlin
// app/src/main/java/com/hrcoach/domain/engine/FitnessSignalEvaluator.kt
package com.hrcoach.domain.engine

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics

enum class TuningDirection { EASE_BACK, HOLD, PUSH_HARDER }
enum class TierPromptDirection { NONE, UP, DOWN }

data class FitnessEvaluation(
    val tuningDirection: TuningDirection,
    val tierPromptDirection: TierPromptDirection,
    val illnessFlag: Boolean,
    val tsb: Float,
    val efTrend: Float?   // positive = improving, negative = declining, null = insufficient data
)
```

**Step 2: Write failing tests**

```kotlin
package com.hrcoach.domain.engine

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import org.junit.Assert.*
import org.junit.Test

class FitnessSignalEvaluatorTest {

    private fun metrics(ef: Float?, decoupling: Float?, hrr1: Float?, daysAgo: Int = 3): WorkoutAdaptiveMetrics {
        val nowMs = System.currentTimeMillis()
        return WorkoutAdaptiveMetrics(
            workoutId = 1L,
            recordedAtMs = nowMs - daysAgo * 86_400_000L,
            efficiencyFactor = ef,
            aerobicDecoupling = decoupling,
            hrr1Bpm = hrr1,
            environmentAffected = false,
            trimpReliable = true
        )
    }

    @Test
    fun `PUSH_HARDER when TSB positive and EF rising`() {
        val profile = AdaptiveProfile(ctl = 50f, atl = 40f) // TSB = +10
        val recentMetrics = listOf(
            metrics(ef = 1.0f, decoupling = 3f, hrr1 = 35f, daysAgo = 10),
            metrics(ef = 1.05f, decoupling = 3f, hrr1 = 37f, daysAgo = 6),
            metrics(ef = 1.1f, decoupling = 3f, hrr1 = 38f, daysAgo = 2)
        )
        val result = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
        assertEquals(TuningDirection.PUSH_HARDER, result.tuningDirection)
    }

    @Test
    fun `EASE_BACK when TSB very negative`() {
        val profile = AdaptiveProfile(ctl = 60f, atl = 90f) // TSB = -30
        val result = FitnessSignalEvaluator.evaluate(profile, emptyList())
        assertEquals(TuningDirection.EASE_BACK, result.tuningDirection)
    }

    @Test
    fun `EASE_BACK when HRR1 declining despite positive TSB`() {
        val profile = AdaptiveProfile(ctl = 50f, atl = 45f) // TSB = +5
        val recentMetrics = listOf(
            metrics(ef = 1.0f, decoupling = 3f, hrr1 = 35f, daysAgo = 10),
            metrics(ef = 1.0f, decoupling = 3f, hrr1 = 28f, daysAgo = 6),
            metrics(ef = 0.92f, decoupling = 4f, hrr1 = 22f, daysAgo = 2)  // HRR1 crashed >10 bpm
        )
        val result = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
        assertEquals(TuningDirection.EASE_BACK, result.tuningDirection)
    }

    @Test
    fun `illness flag when metrics crash despite positive TSB`() {
        val profile = AdaptiveProfile(ctl = 60f, atl = 50f) // TSB = +10 (should feel fresh)
        val recentMetrics = listOf(
            metrics(ef = 1.1f, decoupling = 3f, hrr1 = 40f, daysAgo = 12),
            metrics(ef = 1.1f, decoupling = 3f, hrr1 = 38f, daysAgo = 8),
            metrics(ef = 0.95f, decoupling = 9f, hrr1 = 25f, daysAgo = 1) // sudden crash
        )
        val result = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
        assertTrue("Should flag illness when metrics crash while TSB is positive", result.illnessFlag)
    }

    @Test
    fun `environment-affected sessions excluded from EF trend`() {
        val profile = AdaptiveProfile(ctl = 50f, atl = 45f)
        val hotDayMetrics = WorkoutAdaptiveMetrics(
            workoutId = 1L, recordedAtMs = System.currentTimeMillis() - 86_400_000L,
            efficiencyFactor = 0.8f, // looks terrible — heat
            aerobicDecoupling = 14f,
            environmentAffected = true, // flagged
            trimpReliable = true
        )
        val goodMetrics = listOf(
            metrics(ef = 1.05f, decoupling = 3f, hrr1 = 36f, daysAgo = 5),
            metrics(ef = 1.08f, decoupling = 3f, hrr1 = 37f, daysAgo = 2)
        )
        val result = FitnessSignalEvaluator.evaluate(profile, goodMetrics + hotDayMetrics)
        // Hot day should be excluded; tuning should reflect the good sessions
        assertNotEquals(TuningDirection.EASE_BACK, result.tuningDirection)
    }

    @Test
    fun `HOLD when insufficient reliable data`() {
        val profile = AdaptiveProfile(ctl = 0f, atl = 0f)
        val result = FitnessSignalEvaluator.evaluate(profile, emptyList())
        assertEquals(TuningDirection.HOLD, result.tuningDirection)
    }
}
```

**Step 3: Run to verify failure**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.FitnessSignalEvaluatorTest"
```

**Step 4: Implement**

```kotlin
package com.hrcoach.domain.engine

import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics

object FitnessSignalEvaluator {

    private const val TSB_EASE_THRESHOLD = -25f
    private const val TSB_PUSH_THRESHOLD = 5f
    private const val EF_RISE_THRESHOLD = 0.04f       // 4% improvement over window
    private const val HRR1_DROP_THRESHOLD = 10f        // bpm decline triggers ease-back
    private const val EF_CRASH_THRESHOLD = 0.08f       // 8% drop = illness signal
    private const val HRR1_CRASH_THRESHOLD = 10f
    private const val RECENCY_CUTOFF_DAYS = 42
    private const val MIN_RELIABLE_SESSIONS = 3

    fun evaluate(
        profile: AdaptiveProfile,
        recentMetrics: List<WorkoutAdaptiveMetrics>
    ): FitnessEvaluation {
        val cutoffMs = System.currentTimeMillis() - RECENCY_CUTOFF_DAYS * 86_400_000L
        val reliable = recentMetrics
            .filter { it.recordedAtMs >= cutoffMs && it.trimpReliable && !it.environmentAffected }
            .sortedBy { it.recordedAtMs }

        val tsb = profile.ctl - profile.atl

        if (reliable.size < MIN_RELIABLE_SESSIONS || profile.ctl < 5f) {
            return FitnessEvaluation(TuningDirection.HOLD, TierPromptDirection.NONE, false, tsb, null)
        }

        val efValues = reliable.mapNotNull { it.efficiencyFactor }
        val hrr1Values = reliable.mapNotNull { it.hrr1Bpm }

        val efTrend = if (efValues.size >= 2) efValues.last() - efValues.first() else null
        val hrr1Trend = if (hrr1Values.size >= 2) hrr1Values.last() - hrr1Values.first() else null

        // Illness: metrics crash while TSB is positive (should feel fresh)
        val illnessFlag = tsb > 0f &&
            efTrend != null && efTrend < -EF_CRASH_THRESHOLD &&
            hrr1Trend != null && hrr1Trend < -HRR1_CRASH_THRESHOLD

        val tuning = when {
            tsb < TSB_EASE_THRESHOLD -> TuningDirection.EASE_BACK
            hrr1Trend != null && hrr1Trend < -HRR1_DROP_THRESHOLD -> TuningDirection.EASE_BACK
            tsb > TSB_PUSH_THRESHOLD && efTrend != null && efTrend > EF_RISE_THRESHOLD -> TuningDirection.PUSH_HARDER
            else -> TuningDirection.HOLD
        }

        // Tier prompts require 3+ weeks of sustained signal — assessed by caller (BootcampViewModel)
        return FitnessEvaluation(tuning, TierPromptDirection.NONE, illnessFlag, tsb, efTrend)
    }
}
```

**Step 5: Run tests**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.FitnessSignalEvaluatorTest"
```

**Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/engine/FitnessSignalEvaluator.kt
git add app/src/test/java/com/hrcoach/domain/engine/FitnessSignalEvaluatorTest.kt
git commit -m "feat(engine): FitnessSignalEvaluator - tuning direction, illness flag, TSB"
```

---

## Task 9: Discrete Preset Index Arrays

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/preset/SessionPresetArray.kt`
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt`
- Create: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionPresetArrayTest.kt`

**Context:** Silent tuning increments or decrements an index into an ordered array of preset configurations for each session type. The session type label shown to the user never changes. Zones never change. Only duration or structure changes. Hard caps at array bounds prevent runaway escalation.

**Step 1: Write failing tests**

```kotlin
package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.preset.SessionPresetArray
import org.junit.Assert.*
import org.junit.Test

class SessionPresetArrayTest {

    @Test
    fun `PUSH_HARDER increments index within bounds`() {
        val array = SessionPresetArray.easyRunTier2()
        val baseIndex = 1
        val result = array.tune(baseIndex, TuningDirection.PUSH_HARDER)
        assertEquals(2, result)
    }

    @Test
    fun `EASE_BACK decrements index within bounds`() {
        val array = SessionPresetArray.easyRunTier2()
        val result = array.tune(1, TuningDirection.EASE_BACK)
        assertEquals(0, result)
    }

    @Test
    fun `PUSH_HARDER at ceiling returns ceiling`() {
        val array = SessionPresetArray.easyRunTier2()
        val ceiling = array.presets.size - 1
        val result = array.tune(ceiling, TuningDirection.PUSH_HARDER)
        assertEquals(ceiling, result)
    }

    @Test
    fun `EASE_BACK at floor returns 0`() {
        val array = SessionPresetArray.easyRunTier2()
        val result = array.tune(0, TuningDirection.EASE_BACK)
        assertEquals(0, result)
    }

    @Test
    fun `HOLD returns same index`() {
        val array = SessionPresetArray.easyRunTier2()
        val result = array.tune(2, TuningDirection.HOLD)
        assertEquals(2, result)
    }

    @Test
    fun `all tier 2 session arrays have at least 3 presets`() {
        listOf(
            SessionPresetArray.easyRunTier2(),
            SessionPresetArray.tempoTier2(),
            SessionPresetArray.longRunTier2()
        ).forEach { array ->
            assertTrue("${array.sessionTypeName} needs 3+ presets", array.presets.size >= 3)
        }
    }
}
```

**Step 2: Run to verify failure**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionPresetArrayTest"
```

**Step 3: Implement `SessionPresetArray`**

```kotlin
package com.hrcoach.domain.preset

import com.hrcoach.domain.engine.TuningDirection

data class PresetConfig(
    val presetId: String,
    val durationMinutes: Int,
    val description: String  // human-readable, for debugging/logging only
)

data class SessionPresetArray(
    val sessionTypeName: String,
    val presets: List<PresetConfig>
) {
    fun tune(currentIndex: Int, direction: TuningDirection): Int = when (direction) {
        TuningDirection.PUSH_HARDER -> (currentIndex + 1).coerceAtMost(presets.size - 1)
        TuningDirection.EASE_BACK   -> (currentIndex - 1).coerceAtLeast(0)
        TuningDirection.HOLD        -> currentIndex
    }

    fun presetAt(index: Int): PresetConfig = presets[index.coerceIn(presets.indices)]

    companion object {
        // Tier 1 arrays
        fun easyRunTier1() = SessionPresetArray("easy", listOf(
            PresetConfig("zone2_base", 20, "20-min easy walk/run"),
            PresetConfig("zone2_base", 30, "30-min easy run"),
            PresetConfig("zone2_base", 38, "38-min easy run")
        ))

        // Tier 2 arrays
        fun easyRunTier2() = SessionPresetArray("easy", listOf(
            PresetConfig("zone2_base", 28, "28-min easy"),
            PresetConfig("zone2_base", 35, "35-min easy"),
            PresetConfig("zone2_base", 42, "42-min easy"),
            PresetConfig("zone2_base", 50, "50-min easy (T2 cap)")
        ))

        fun tempoTier2() = SessionPresetArray("tempo", listOf(
            PresetConfig("aerobic_tempo", 20, "2×8 min Z3 with rest"),
            PresetConfig("aerobic_tempo", 25, "20-min continuous Z3"),
            PresetConfig("aerobic_tempo", 30, "25-min continuous Z3"),
            PresetConfig("lactate_threshold", 35, "30-min continuous Z4 (T2 cap)")
        ))

        fun longRunTier2() = SessionPresetArray("long", listOf(
            PresetConfig("zone2_base", 45, "45-min long run"),
            PresetConfig("zone2_base", 60, "60-min long run"),
            PresetConfig("zone2_base", 75, "75-min long run"),
            PresetConfig("zone2_base", 90, "90-min long run (T2 cap)")
        ))

        // Tier 3 arrays
        fun easyRunTier3() = SessionPresetArray("easy", listOf(
            PresetConfig("zone2_base", 35, "35-min easy"),
            PresetConfig("zone2_base", 45, "45-min easy"),
            PresetConfig("zone2_base", 55, "55-min easy"),
            PresetConfig("zone2_base", 65, "65-min easy (T3 cap)")
        ))

        fun vo2maxTier3() = SessionPresetArray("interval", listOf(
            PresetConfig("norwegian_4x4", 30, "3×4 min Z5, long recovery"),
            PresetConfig("norwegian_4x4", 35, "4×4 min Z5 (baseline)"),
            PresetConfig("norwegian_4x4", 40, "5×4 min Z5"),
            PresetConfig("norwegian_4x4", 45, "6×4 min Z5 (T3 cap)")
        ))

        fun thresholdTier3() = SessionPresetArray("tempo", listOf(
            PresetConfig("lactate_threshold", 30, "2×10 min Z4 cruise"),
            PresetConfig("lactate_threshold", 35, "3×10 min Z4 cruise"),
            PresetConfig("lactate_threshold", 40, "40-min continuous Z4"),
            PresetConfig("lactate_threshold", 50, "50-min continuous Z4 (T3 cap)")
        ))
    }
}
```

**Step 4: Run tests**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionPresetArrayTest"
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/preset/SessionPresetArray.kt
git add app/src/test/java/com/hrcoach/domain/bootcamp/SessionPresetArrayTest.kt
git commit -m "feat(preset): discrete preset index arrays for silent tuning"
```

---

## Task 10: Wire Tuning Direction into `SessionSelector`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt`

**Context:** `SessionSelector.weekSessions()` gains a `tuningDirection` and `presetIndexOverrides` parameter. Internally it looks up the correct `SessionPresetArray` for each session slot and applies the tuning index. The session type name exposed to the UI never changes.

**Step 1: Read the existing `SessionSelectorTest` to understand the test pattern**

```bash
# Just read it first — look at existing test structure before writing new ones
```

**Step 2: Write new failing tests** (add to `SessionSelectorTest`)

```kotlin
@Test
fun `PUSH_HARDER selects longer duration easy run for Tier 2`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.BUILD,
        goal = BootcampGoal.RACE_5K_10K,
        runsPerWeek = 3,
        targetMinutes = 35,
        tierIndex = 1,
        tuningDirection = TuningDirection.PUSH_HARDER,
        currentPresetIndices = mapOf("easy" to 1)
    )
    val easySession = sessions.first { it.type == SessionType.EASY }
    // Index was 1 (35 min), PUSH_HARDER moves to index 2 (42 min)
    assertTrue("Duration should be longer with PUSH_HARDER", easySession.minutes > 35)
}

@Test
fun `EASE_BACK on tempo breaks into intervals`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.BUILD,
        goal = BootcampGoal.RACE_5K_10K,
        runsPerWeek = 4,
        targetMinutes = 35,
        tierIndex = 1,
        tuningDirection = TuningDirection.EASE_BACK,
        currentPresetIndices = mapOf("tempo" to 1)
    )
    val tempoSession = sessions.first { it.type == SessionType.TEMPO }
    // Index was 1 (20-min continuous), EASE_BACK moves to index 0 (2×8 min split)
    assertEquals("aerobic_tempo", tempoSession.presetId)
}
```

**Step 3: Update `SessionSelector` signature and implementation**

Add `tierIndex: Int = 0, tuningDirection: TuningDirection = TuningDirection.HOLD, currentPresetIndices: Map<String, Int> = emptyMap()` to `weekSessions()`. Internally use `SessionPresetArray` to resolve the preset and duration for each slot. Existing callers that omit the new params continue to work with defaults.

**Step 4: Run all bootcamp tests**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.*"
```
Expected: all pass.

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt
git add app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt
git commit -m "feat(bootcamp): wire TuningDirection into SessionSelector with preset index arrays"
```

---

## Task 11: Wire CTL/ATL/TRIMP into `AdaptivePaceController.finishSession`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/engine/AdaptivePaceController.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/engine/AdaptivePaceControllerTest.kt`

**Context:** `finishSession()` already produces a `SessionResult` with an `updatedProfile`. It must now also compute TRIMP (using `calculateTRIMP`) and update CTL/ATL/TSB (using `FitnessLoadCalculator`) in the returned `updatedProfile`. The `daysSinceLast` must be passed in by the caller (the service knows the timestamp of the previous session).

**Step 1: Update `AdaptivePaceController` constructor and `finishSession` signature**

```kotlin
fun finishSession(
    workoutId: Long,
    endedAtMs: Long,
    daysSinceLastSession: Int = 1   // caller provides this; default 1 avoids division issues
): SessionResult
```

Inside `finishSession`, after the existing trim/lag update:
```kotlin
val durationMin = (sampleElapsedSec / 60f).coerceAtLeast(0f)
val avgHr = if (weightedDurationSec > 0f) weightedHrSum / weightedDurationSec else null
val trimp = if (avgHr != null && initialProfile.hrMax > 0) {
    MetricsCalculator.calculateTRIMP(
        durationMin = durationMin,
        avgHr = avgHr,
        hrRest = initialProfile.hrRest,
        hrMax = initialProfile.hrMax
    )
} else 0f

val loadResult = FitnessLoadCalculator.updateLoads(
    currentCtl = initialProfile.ctl,
    currentAtl = initialProfile.atl,
    trimpScore = trimp,
    daysSinceLast = daysSinceLastSession
)

val updatedProfile = AdaptiveProfile(
    longTermHrTrimBpm = longTermTrimBpm,
    responseLagSec = responseLagSec,
    paceHrBuckets = mergedBuckets,
    totalSessions = initialTotalSessions + 1,
    ctl = loadResult.ctl,
    atl = loadResult.atl,
    lastTRIMP = trimp,
    hrMax = initialProfile.hrMax,
    hrRest = initialProfile.hrRest
)
```

**Step 2: Write new test**

```kotlin
@Test
fun `finishSession updates CTL and ATL from zero after first session`() {
    val profile = AdaptiveProfile(hrMax = 190, hrRest = 60f)
    val controller = AdaptivePaceController(config = steadyStateConfig(), initialProfile = profile)
    // simulate 30 minutes of HR samples
    val nowMs = System.currentTimeMillis()
    repeat(30) { i ->
        controller.evaluateTick(nowMs + i * 60_000L, hr = 155, connected = true,
            targetHr = 155, distanceMeters = i * 180f, actualZone = ZoneStatus.IN_ZONE)
    }
    val result = controller.finishSession(workoutId = 1L, endedAtMs = nowMs + 1_800_000L, daysSinceLastSession = 1)
    assertTrue("CTL should increase from 0", result.updatedProfile.ctl > 0f)
    assertTrue("ATL should increase from 0", result.updatedProfile.atl > 0f)
    assertTrue("lastTRIMP should be positive", result.updatedProfile.lastTRIMP > 0f)
}
```

**Step 3: Run tests**

```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.AdaptivePaceControllerTest"
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/engine/AdaptivePaceController.kt
git add app/src/test/java/com/hrcoach/domain/engine/AdaptivePaceControllerTest.kt
git commit -m "feat(engine): compute TRIMP and update CTL/ATL in finishSession"
```

---

## Task 12: HRR1 Cool-Down Collection in `WorkoutForegroundService`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt` (add cool-down state)
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutState.kt` (add cooldown state)

**Context:** After the user stops the workout, enter a 120-second cool-down state. Keep BLE connection alive. Sample HR at T=0 (cessation), T=60s, T=120s. Compute `hrr1 = hr(0) - hr(60)`. If the user exits the cool-down early, store `hrr1 = null`. The UI must display "Walk slowly for 2 minutes" during this window — this is required for measurement validity.

**Step 1: Add cool-down state to `WorkoutState`**

```kotlin
// In WorkoutState, add:
data class CoolDownState(
    val active: Boolean = false,
    val secondsRemaining: Int = 120,
    val hrAtCessation: Int = 0,
    val hrr1Bpm: Float? = null   // populated at T+60s
)
```

**Step 2: Add cool-down phase to `WorkoutForegroundService`**

When `stopWorkout()` is called:
1. Set `WorkoutState.coolDown = CoolDownState(active = true, hrAtCessation = lastHr)`
2. Start a 120-second coroutine that samples HR at T=60 and T=120
3. At T=60: `hrr1 = hrAtCessation - currentHr`; update `CoolDownState.hrr1Bpm`
4. At T=120: close cool-down, proceed to post-run summary with `hrr1` stored
5. If the user manually exits early: store `hrr1 = null`

**Step 3: Update `ActiveWorkoutScreen`**

When `coolDown.active == true`, show a distinct cool-down panel:
```
"Walk slowly for 2 minutes"
[120 → 0 countdown]
[Skip — I'll miss the recovery score]
```

**Note:** This task modifies Android service/UI code which cannot be unit-tested without a device. Write the logic as a pure function in a companion object where possible, then call it from the service. Run a manual smoke test on device/emulator after implementing.

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/
git add app/src/main/java/com/hrcoach/ui/workout/
git commit -m "feat(service): HRR1 cool-down collection with 2-min walking protocol"
```

---

## Task 13: HRmax Auto-Detection in `WorkoutForegroundService`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/AdaptiveProfileRepository.kt`

**Context:** The service already receives per-second HR samples. Route the last 30 HR samples into `HrCalibrator.detectNewHrMax()` on each tick. If a new max is detected: update `AdaptiveProfile.hrMax`, save it, and emit a notification. Also compute a 30-second rolling min during the first 5 minutes and feed into `HrCalibrator.updateHrRest()`.

**Step 1: In `WorkoutForegroundService`, add a rolling HR sample buffer**

```kotlin
private val hrSampleBuffer = ArrayDeque<Int>(maxSize = 30)

// On each HR sample received:
hrSampleBuffer.addLast(hr)
if (hrSampleBuffer.size > 30) hrSampleBuffer.removeFirst()

val newMax = HrCalibrator.detectNewHrMax(profile.hrMax, hrSampleBuffer.toList())
if (newMax != null) {
    val updated = profile.copy(hrMax = newMax)
    adaptiveProfileRepository.saveProfile(updated)
    showHrMaxNotification(newMax)
}
```

**Step 2: Pre-workout HRrest sampling**

During the first 300 seconds of a workout, maintain a 30-second rolling min. At T=300s:
```kotlin
val candidate = preWorkoutHrBuffer.min()
val updatedRest = HrCalibrator.updateHrRest(profile.hrRest, candidate.toFloat())
if (updatedRest != profile.hrRest) {
    adaptiveProfileRepository.saveProfile(profile.copy(hrRest = updatedRest))
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(service): wire HRmax auto-detection and HRrest rolling update"
```

---

## Task 14: Wire `FitnessSignalEvaluator` into `BootcampViewModel`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/WorkoutMetricsRepository.kt` (add recent metrics query if missing)

**Context:** `BootcampViewModel.refreshFromEnrollment()` currently calls `FitnessEvaluator.assess(profile, emptyList())` — this is the broken line that always returns `UNKNOWN`. Replace it with `FitnessSignalEvaluator.evaluate(profile, recentMetrics)` where `recentMetrics` comes from `WorkoutMetricsRepository`.

**Step 1: Add recent metrics query to `WorkoutMetricsRepository`** (if not present)

```kotlin
suspend fun getRecentMetrics(limitDays: Int = 42): List<WorkoutAdaptiveMetrics>
```

This should query the last N days of `workout_metrics`, ordered by `recordedAtMs` descending, mapped to `WorkoutAdaptiveMetrics`.

**Step 2: Replace the broken call in `BootcampViewModel.refreshFromEnrollment()`**

```kotlin
// BEFORE (broken):
val fitnessLevel = FitnessEvaluator.assess(profile, emptyList<WorkoutAdaptiveMetrics>())

// AFTER:
val recentMetrics = workoutMetricsRepository.getRecentMetrics(limitDays = 42)
val fitnessEval = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
```

**Step 3: Add `fitnessEval` to `BootcampUiState`**

```kotlin
data class BootcampUiState(
    // ... existing fields ...
    val tuningDirection: TuningDirection = TuningDirection.HOLD,
    val illnessFlag: Boolean = false,
    val tierPromptDirection: TierPromptDirection = TierPromptDirection.NONE
)
```

**Step 4: Pass `tuningDirection` into session planning**

In `PhaseEngine.planCurrentWeek()`, propagate `tuningDirection` and `currentPresetIndices` from the enrollment through to `SessionSelector.weekSessions()`.

**Step 5: Run the full test suite**

```bash
./gradlew testDebugUnitTest
```
Expected: all pass. If `FitnessEvaluator` tests break, update them to test the new evaluator.

**Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/
git add app/src/main/java/com/hrcoach/data/repository/WorkoutMetricsRepository.kt
git commit -m "feat(bootcamp): wire FitnessSignalEvaluator - fix broken emptyList() call"
```

---

## Task 15: Tier Prompt Logic in `BootcampViewModel`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

**Context:** Tier-change prompts require 3+ consecutive weeks of sustained signal AND a clear directional difference from the current tier's CTL range. The prompt appears in the Bootcamp dashboard (not a modal). It explains the signal and what changes. The user accepts or dismisses. Snooze: 2 → 3 → 4 weeks on repeated dismissal (hard cap at 4 weeks).

**Step 1: Add tier prompt evaluation**

```kotlin
private fun shouldPromptTierChange(
    profile: AdaptiveProfile,
    enrollment: BootcampEnrollmentEntity,
    goal: BootcampGoal
): TierPromptDirection {
    val nowMs = System.currentTimeMillis()
    if (nowMs < enrollment.tierPromptSnoozedUntilMs) return TierPromptDirection.NONE
    if (profile.totalSessions < 9) return TierPromptDirection.NONE  // need 3+ weeks of data

    val currentTierCtlRange = TierCtlRanges.rangeFor(goal, enrollment.tierIndex)
    return when {
        profile.ctl > currentTierCtlRange.last && enrollment.tierIndex < 2 -> TierPromptDirection.UP
        profile.ctl < currentTierCtlRange.first && enrollment.tierIndex > 0 -> TierPromptDirection.DOWN
        else -> TierPromptDirection.NONE
    }
}
```

**Step 2: Add `TierCtlRanges` object**

```kotlin
// app/src/main/java/com/hrcoach/domain/bootcamp/TierCtlRanges.kt
object TierCtlRanges {
    // [floor, ceiling] — Tier 0, Tier 1, Tier 2
    private val CARDIO = listOf(0..30, 30..55, 55..200)
    private val RACE_5K_10K = listOf(0..35, 35..65, 65..200)
    private val HALF_MARATHON = listOf(0..45, 45..75, 75..200)
    private val MARATHON = listOf(0..55, 55..90, 90..200)

    fun rangeFor(goal: BootcampGoal, tierIndex: Int): IntRange = when (goal) {
        BootcampGoal.CARDIO_HEALTH -> CARDIO
        BootcampGoal.RACE_5K_10K  -> RACE_5K_10K
        BootcampGoal.HALF_MARATHON -> HALF_MARATHON
        BootcampGoal.MARATHON      -> MARATHON
    }[tierIndex.coerceIn(0, 2)]
}
```

**Step 3: Add prompt UI to `BootcampScreen`**

When `uiState.tierPromptDirection != NONE`, show a card below the weekly schedule:

```
[↑ Ready to level up?]
"Your recent runs show your aerobic fitness is above the [current tier] program.
Moving to [next tier] introduces [key session change]."
[Move up]  [Not now]
```

Mirror for `DOWN` direction with appropriate messaging.

**Step 4: Add dismiss handler in `BootcampViewModel`**

```kotlin
fun dismissTierPrompt() {
    viewModelScope.launch {
        val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
        val dismissCount = enrollment.tierPromptDismissCount + 1
        val snoozeWeeks = when (dismissCount) {
            1 -> 2; 2 -> 3; else -> 4  // hard cap at 4 weeks
        }
        val snoozedUntil = System.currentTimeMillis() + snoozeWeeks * 7 * 86_400_000L
        bootcampRepository.updateEnrollment(
            enrollment.copy(
                tierPromptDismissCount = dismissCount,
                tierPromptSnoozedUntilMs = snoozedUntil
            )
        )
    }
}

fun acceptTierChange(direction: TierPromptDirection) {
    viewModelScope.launch {
        val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
        val newTier = (enrollment.tierIndex + if (direction == TierPromptDirection.UP) 1 else -1)
            .coerceIn(0, 2)
        bootcampRepository.updateEnrollment(
            enrollment.copy(
                tierIndex = newTier,
                tierPromptDismissCount = 0,
                tierPromptSnoozedUntilMs = 0L
            )
        )
    }
}
```

**Step 5: Run full test suite**

```bash
./gradlew testDebugUnitTest
```

**Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/
git add app/src/main/java/com/hrcoach/domain/bootcamp/TierCtlRanges.kt
git commit -m "feat(bootcamp): tier prompt with accept/dismiss and snooze escalation"
```

---

## Task 16: Illness Prompt in Bootcamp Dashboard

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`

**Context:** When `fitnessEval.illnessFlag == true`, show a third prompt type distinct from tier-change prompts. If confirmed, the next scheduled session is replaced with a recovery session and the flagged run is excluded from CTL/ATL accumulation.

**Step 1: Add illness prompt card to `BootcampScreen`**

Only shown when `uiState.illnessFlag == true` and no tier prompt is showing. Card text:
```
"Your heart rate is responding differently than usual.
Are you feeling under the weather or dealing with extra stress?"
[Yes, ease off]  [No, I'm fine]
```

**Step 2: Add handlers in `BootcampViewModel`**

```kotlin
fun confirmIllness() {
    // Replace next scheduled session with a recovery (easy run, lowest preset index)
    // Mark the triggering session as anomalous in WorkoutMetrics (exclude from rolling signal)
    _uiState.update { it.copy(illnessFlag = false) }
}

fun dismissIllness() {
    _uiState.update { it.copy(illnessFlag = false) }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/
git commit -m "feat(bootcamp): illness/anomaly detection prompt with recovery session override"
```

---

## Task 17: Walk/Run as First-Class Session Type

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`
- Modify: `app/src/main/java/com/hrcoach/domain/model/WorkoutConfig.kt`
- Modify: `app/src/main/java/com/hrcoach/domain/model/HrSegment.kt`

**Context:** Tier 1 programs prescribe alternating run/walk intervals. The app currently has no coached walk interval concept. Add a `walkIntervalSec` and `runIntervalSec` field to `WorkoutConfig` (or `HrSegment`). When set, `WorkoutForegroundService` alternates between run and walk phases, enforcing HR targets during run and allowing HR recovery during walk.

**Step 1: Add walk/run fields to `HrSegment`**

```kotlin
data class HrSegment(
    val durationSeconds: Int? = null,
    val distanceMeters: Float? = null,
    val targetHr: Int,
    val label: String = "",
    // Walk/run interval fields — null means continuous running
    val runIntervalSec: Int? = null,
    val walkIntervalSec: Int? = null
)
```

**Step 2: Add walk/run presets to `PresetLibrary`**

```kotlin
private fun walkRun3020() = WorkoutPreset(
    id = "walk_run_30_20",
    name = "Walk/Run 3:2",
    // ...
    buildConfig = { maxHr ->
        val zone2 = (maxHr * 0.67f).roundToInt()
        WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = zone2,
            bufferBpm = 8,
            // single segment with walk/run structure
            segments = listOf(HrSegment(
                durationSeconds = 1800,
                targetHr = zone2,
                label = "Run 3 min / Walk 2 min",
                runIntervalSec = 180,
                walkIntervalSec = 120
            )),
            presetId = "walk_run_30_20"
        )
    }
)
```

**Step 3: Handle walk intervals in `WorkoutForegroundService`**

When a segment has `runIntervalSec != null`, alternate between run phase (normal zone enforcement) and walk phase (suppress zone alerts; emit walk coaching cue; resume run when interval expires OR HR returns below recovery target, whichever comes first).

**Step 4: Run full test suite and build**

```bash
./gradlew testDebugUnitTest && ./gradlew assembleDebug
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/
git commit -m "feat(preset): walk/run intervals as first-class Tier 1 session type"
```

---

## Task 18: Final Integration Test Pass

**Step 1: Run all unit tests**

```bash
./gradlew testDebugUnitTest 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL, 0 failures.

**Step 2: Build debug APK**

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

**Step 3: Smoke test on emulator/device**
- Enroll in Bootcamp (any goal)
- Complete 2–3 runs
- Verify Bootcamp dashboard shows session plan
- Verify cool-down screen appears after stopping a workout
- Verify `AdaptiveProfile` CTL/ATL/TSB update (log in BootcampViewModel)

**Step 4: Final commit**

```bash
git add .
git commit -m "feat(bootcamp): adaptive tier system - integration complete"
```

---

## Implementation Order Summary

```
Task 1  → DB migration 5
Task 2  → AdaptiveProfile new fields
Task 3  → Banister TRIMP
Task 4  → CTL/ATL/TSB (FitnessLoadCalculator)
Task 5  → HRmax/HRrest auto-detection (HrCalibrator)
Task 6  → HR artifact rejection (HrArtifactDetector)
Task 7  → Environmental flagging (EnvironmentFlagDetector)
Task 8  → FitnessSignalEvaluator (core engine)
Task 9  → Discrete preset arrays (SessionPresetArray)
Task 10 → TuningDirection in SessionSelector
Task 11 → CTL/ATL wired into AdaptivePaceController.finishSession
Task 12 → HRR1 cool-down collection (service + UI)
Task 13 → HRmax auto-detection wired into service
Task 14 → FitnessSignalEvaluator wired into BootcampViewModel
Task 15 → Tier prompt logic
Task 16 → Illness prompt
Task 17 → Walk/run session type
Task 18 → Integration test pass
```

Tasks 1–11 are pure Kotlin with full unit test coverage. Tasks 12–13 touch Android service code (manual testing only). Tasks 14–18 are ViewModel/UI wiring.
