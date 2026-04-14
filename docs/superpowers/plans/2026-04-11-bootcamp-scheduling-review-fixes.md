# Bootcamp Scheduling Review Fixes

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all 14 issues from the bootcamp scheduling code review — 4 concrete bugs, 3 orphaned/unfinished features, 3 science/UX gaps, and the CARDIO_HEALTH EVERGREEN phase redesign.

**Architecture:** Pure domain-layer changes. All files under `domain/bootcamp/`, `domain/preset/`, and `domain/model/` plus their tests. No UI changes — the existing ViewModel already reads session types/presets dynamically. The EVERGREEN phase is a new `TrainingPhase` enum value with dedicated `SessionSelector` logic that rotates quality sessions on a 4-week micro-cycle.

**Tech Stack:** Kotlin, JUnit 4, no external dependencies.

**Build/test command:** `./gradlew testDebugUnitTest` from the repo root. For single test class: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.ClassName"`

**Important:** Copy `local.properties` from the main repo if running in a worktree: `cp ../../local.properties .` (or from project root).

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `domain/bootcamp/SessionRescheduler.kt` | Modify | Fix `LONG_RUN`→`LONG`, add `LONG`/`RACE_SIM` to hardTypes |
| `domain/preset/PresetLibrary.kt` | Modify | Add `strides_20s`, `race_sim_5k`, `race_sim_10k` presets |
| `domain/bootcamp/SessionSelector.kt` | Modify | Fix RACE_SIM preset resolution, add EVERGREEN logic, add variety rotation, tier-dependent recovery |
| `domain/model/TrainingPhase.kt` | Modify | Add `EVERGREEN` enum value |
| `domain/model/BootcampGoal.kt` | Modify | Change CARDIO_HEALTH phaseArc to `[BASE, EVERGREEN]` |
| `domain/bootcamp/PhaseEngine.kt` | Modify | EVERGREEN wraps to self, fix `weeksUntilNextRecovery` at phase boundaries |
| `domain/bootcamp/BootcampSessionCompleter.kt` | Modify | Pass preset indices on week advance |
| `domain/bootcamp/DurationScaler.kt` | No change | Existing logic is sound |
| `domain/bootcamp/TierCtlRanges.kt` | No change | Existing logic is sound |
| Tests (7 files) | Modify | Update assertions, add new coverage |

All paths relative to `app/src/main/java/com/hrcoach/` (source) and `app/src/test/java/com/hrcoach/` (test).

---

### Task 1: Fix SessionRescheduler String Mismatches (Bugs #1, #4)

**Files:**
- Modify: `domain/bootcamp/SessionRescheduler.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionReschedulerTest.kt`

**Context:** `SessionRescheduler` uses raw strings for session type matching. Two problems:
1. `hardTypes` is `setOf("TEMPO", "INTERVAL", "INTERVALS")` — missing `"LONG"` and `"RACE_SIM"`, so the 2-day recovery gap isn't enforced for long runs or race sims during rescheduling.
2. `dropPriority` uses `"LONG_RUN"` but `SessionType.LONG.name` is `"LONG"`, so long runs get priority `1` (same as tempo) instead of `3` (most protected).

- [ ] **Step 1: Write failing tests for the string mismatches**

Add to `SessionReschedulerTest.kt`:

```kotlin
@Test fun long_run_has_highest_drop_priority() {
    // When forced to drop a session, LONG should be dropped last (highest priority)
    val easySession = session(day = 1, type = "EASY", status = "SCHEDULED")
    val tempoSession = session(day = 3, type = "TEMPO")
    val longSession = session(day = 6, type = "LONG")  // NOTE: "LONG" not "LONG_RUN"
    val req = RescheduleRequest(
        session = tempoSession,
        enrollment = enrollment(dayPrefs(
            1 to DaySelectionLevel.AVAILABLE,
            3 to DaySelectionLevel.AVAILABLE,
            5 to DaySelectionLevel.BLACKOUT,
            6 to DaySelectionLevel.AVAILABLE,
            7 to DaySelectionLevel.BLACKOUT
        )),
        todayDayOfWeek = 5,
        occupiedDaysThisWeek = setOf(1, 3, 6),
        allSessionsThisWeek = listOf(easySession, tempoSession, longSession)
    )
    val result = SessionRescheduler.reschedule(req) as RescheduleResult.Dropped
    // Should drop the EASY session (priority 0), NOT the LONG (priority 3)
    assertEquals("Should drop EASY, not LONG", easySession.id, result.droppedSessionId)
}

@Test fun race_sim_has_highest_drop_priority() {
    val easySession = session(day = 1, type = "EASY", status = "SCHEDULED")
    val tempoSession = session(day = 3, type = "TEMPO")
    val raceSimSession = session(day = 6, type = "RACE_SIM")
    val req = RescheduleRequest(
        session = tempoSession,
        enrollment = enrollment(dayPrefs(
            1 to DaySelectionLevel.AVAILABLE,
            3 to DaySelectionLevel.AVAILABLE,
            5 to DaySelectionLevel.BLACKOUT,
            6 to DaySelectionLevel.AVAILABLE,
            7 to DaySelectionLevel.BLACKOUT
        )),
        todayDayOfWeek = 5,
        occupiedDaysThisWeek = setOf(1, 3, 6),
        allSessionsThisWeek = listOf(easySession, tempoSession, raceSimSession)
    )
    val result = SessionRescheduler.reschedule(req) as RescheduleResult.Dropped
    assertEquals("Should drop EASY, not RACE_SIM", easySession.id, result.droppedSessionId)
}

@Test fun respects_48h_recovery_gap_for_long_runs() {
    // Rescheduling EASY from day 1. Day 3 has a LONG run.
    // Day 2 should be excluded (adjacent to day 3 hard session).
    val req = RescheduleRequest(
        session = session(day = 1, type = "EASY"),
        enrollment = enrollment(dayPrefs(
            1 to DaySelectionLevel.AVAILABLE,
            2 to DaySelectionLevel.AVAILABLE,
            3 to DaySelectionLevel.AVAILABLE,
            4 to DaySelectionLevel.BLACKOUT,
            5 to DaySelectionLevel.BLACKOUT,
            6 to DaySelectionLevel.BLACKOUT,
            7 to DaySelectionLevel.BLACKOUT
        )),
        todayDayOfWeek = 1,
        occupiedDaysThisWeek = setOf(1, 3),
        allSessionsThisWeek = listOf(
            session(1, "EASY"),
            session(3, "LONG")
        )
    )
    val days = SessionRescheduler.availableDays(req)
    assertFalse("Day 2 violates recovery gap with LONG on day 3", 2 in days)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionReschedulerTest"`
Expected: 3 new tests FAIL (LONG treated as priority 1, LONG not in hardTypes)

- [ ] **Step 3: Fix `hardTypes` and `dropPriority` in SessionRescheduler.kt**

Replace the `hardTypes` set (line 23) and `dropPriority` function (lines 65-71):

```kotlin
private val hardTypes = setOf("TEMPO", "INTERVAL", "INTERVALS", "LONG", "RACE_SIM")
```

```kotlin
/** Lower number = drop first */
private fun dropPriority(type: String): Int = when (type) {
    "EASY"                      -> 0
    "STRIDES"                   -> 0
    "TEMPO"                     -> 1
    "INTERVAL", "INTERVALS"     -> 2
    "LONG", "RACE_SIM"          -> 3
    else                        -> 1
}
```

- [ ] **Step 4: Also fix the existing test that uses `"LONG_RUN"`**

In `SessionReschedulerTest.kt`, the existing `drops_lowest_priority_when_no_slots_available` test (line 63) creates a session with `type = "LONG_RUN"`. Change it to `type = "LONG"`:

```kotlin
val longSession  = session(day = 6, type = "LONG")
```

- [ ] **Step 5: Run all rescheduler tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionReschedulerTest"`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/SessionRescheduler.kt \
       app/src/test/java/com/hrcoach/domain/bootcamp/SessionReschedulerTest.kt
git commit -m "fix(bootcamp): correct session type strings in SessionRescheduler

- LONG_RUN → LONG in dropPriority (matches SessionType.LONG.name)
- Add LONG, RACE_SIM to hardTypes for recovery-gap enforcement
- Add STRIDES to dropPriority (priority 0, expendable)
- Fix existing test that used LONG_RUN"
```

---

### Task 2: Add Missing Presets to PresetLibrary (Bugs #2, #3, #11)

**Files:**
- Modify: `domain/preset/PresetLibrary.kt`
- Test: `app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt` (create)

**Context:** Three presets are referenced but don't exist in `PresetLibrary.ALL`:
1. `strides_20s` — referenced by `SessionPresetArray` for all strides sessions. Falls through to FREE_RUN, losing the `guidanceTag = "strides"`.
2. `race_sim_5k` — needed for 5K RACE_SIM sessions (currently null presetId).
3. `race_sim_10k` — needed for 10K RACE_SIM sessions (currently null presetId).

- [ ] **Step 1: Write failing tests for preset existence and config correctness**

Create `app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt`:

```kotlin
package com.hrcoach.domain.preset

import com.hrcoach.domain.model.WorkoutMode
import org.junit.Assert.*
import org.junit.Test

class PresetLibraryTest {

    @Test
    fun `strides_20s preset exists and has strides guidance tag`() {
        val preset = PresetLibrary.ALL.firstOrNull { it.id == "strides_20s" }
        assertNotNull("strides_20s preset must exist", preset)
        val config = preset!!.buildConfig(maxHr = 190, restHr = 60)
        assertEquals(WorkoutMode.STEADY_STATE, config.mode)
        assertEquals("strides", config.guidanceTag)
        assertNotNull(config.steadyStateTargetHr)
    }

    @Test
    fun `race_sim_5k preset exists and has distance segments`() {
        val preset = PresetLibrary.ALL.firstOrNull { it.id == "race_sim_5k" }
        assertNotNull("race_sim_5k preset must exist", preset)
        val config = preset!!.buildConfig(maxHr = 190, restHr = 60)
        assertEquals(WorkoutMode.DISTANCE_PROFILE, config.mode)
        assertTrue(config.segments.isNotEmpty())
        // Final segment should end at 5000m
        assertEquals(5000f, config.segments.last().distanceMeters!!, 100f)
    }

    @Test
    fun `race_sim_10k preset exists and has distance segments`() {
        val preset = PresetLibrary.ALL.firstOrNull { it.id == "race_sim_10k" }
        assertNotNull("race_sim_10k preset must exist", preset)
        val config = preset!!.buildConfig(maxHr = 190, restHr = 60)
        assertEquals(WorkoutMode.DISTANCE_PROFILE, config.mode)
        assertTrue(config.segments.isNotEmpty())
        assertEquals(10000f, config.segments.last().distanceMeters!!, 100f)
    }

    @Test
    fun `all preset IDs are unique`() {
        val ids = PresetLibrary.ALL.map { it.id }
        assertEquals("Duplicate preset IDs found", ids.size, ids.distinct().size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.PresetLibraryTest"`
Expected: First 3 tests FAIL (presets not found)

- [ ] **Step 3: Add the three presets to PresetLibrary.kt**

Add to the `ALL` list (after `marathonPrep()`):

```kotlin
val ALL: List<WorkoutPreset> = listOf(
    zone2Base(), zone2WithStrides(), aeroTempo(), lactateThreshold(),
    norwegian4x4(), hiit3030(), hillRepeats(),
    halfMarathonPrep(), marathonPrep(),
    strides20s(), raceSim5k(), raceSim10k()
)
```

Add these private functions:

```kotlin
private fun strides20s() = WorkoutPreset(
    id = "strides_20s",
    name = "Easy + Strides",
    subtitle = "Neuromuscular activation",
    description = "Easy run with 4\u20136 \u00d7 20-sec strides. Improves running economy.",
    category = PresetCategory.BASE_AEROBIC,
    durationLabel = "20\u201326 min",
    intensityLabel = "Easy + bursts",
    buildConfig = { maxHr, restHr ->
        WorkoutConfig(
            mode = WorkoutMode.STEADY_STATE,
            steadyStateTargetHr = karvonen(maxHr, restHr, 0.68f),
            bufferBpm = 5,
            presetId = "strides_20s",
            sessionLabel = "Strides",
            guidanceTag = "strides"
        )
    }
)

private fun raceSim5k() = WorkoutPreset(
    id = "race_sim_5k",
    name = "5K Race Simulation",
    subtitle = "Race-day rehearsal",
    description = "Progressive 5 km: easy start \u2192 race pace \u2192 kick.",
    category = PresetCategory.RACE_PREP,
    durationLabel = "5 km",
    intensityLabel = "Moderate\u2013Hard",
    buildConfig = { maxHr, restHr ->
        WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(distanceMeters = 1000f, targetHr = karvonen(maxHr, restHr, 0.75f), label = "Easy Start"),
                HrSegment(distanceMeters = 4000f, targetHr = karvonen(maxHr, restHr, 0.88f), label = "Race Pace"),
                HrSegment(distanceMeters = 5000f, targetHr = karvonen(maxHr, restHr, 0.92f), label = "Kick")
            ),
            bufferBpm = 4,
            presetId = "race_sim_5k"
        )
    }
)

private fun raceSim10k() = WorkoutPreset(
    id = "race_sim_10k",
    name = "10K Race Simulation",
    subtitle = "Race-day rehearsal",
    description = "Progressive 10 km: easy start \u2192 race pace \u2192 strong finish.",
    category = PresetCategory.RACE_PREP,
    durationLabel = "10 km",
    intensityLabel = "Moderate\u2013Hard",
    buildConfig = { maxHr, restHr ->
        WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(distanceMeters = 2000f, targetHr = karvonen(maxHr, restHr, 0.72f), label = "Easy Start"),
                HrSegment(distanceMeters = 8000f, targetHr = karvonen(maxHr, restHr, 0.85f), label = "Race Pace"),
                HrSegment(distanceMeters = 10000f, targetHr = karvonen(maxHr, restHr, 0.90f), label = "Strong Finish")
            ),
            bufferBpm = 4,
            presetId = "race_sim_10k"
        )
    }
)
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.PresetLibraryTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/preset/PresetLibrary.kt \
       app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt
git commit -m "feat(preset): add strides_20s, race_sim_5k, race_sim_10k presets

strides_20s: fixes orphaned preset reference from SessionPresetArray
race_sim_5k/10k: enables structured HR zones for short-distance race sims"
```

---

### Task 3: Fix RACE_SIM Preset Resolution in SessionSelector (Bug #3)

**Files:**
- Modify: `domain/bootcamp/SessionSelector.kt:159-165`
- Modify: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt`

**Context:** `SessionSelector.periodizedWeek()` sets `presetId = null` for RACE_SIM sessions. The new presets from Task 2 exist, but the selector needs to pick the right one based on goal.

- [ ] **Step 1: Write failing test**

Add to `SessionSelectorTest.kt`:

```kotlin
@Test
fun `RACE_SIM session has goal-appropriate preset for 5K`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.PEAK,
        goal = BootcampGoal.RACE_5K,
        runsPerWeek = 4,
        targetMinutes = 35,
        tierIndex = 2
    )
    val raceSim = sessions.firstOrNull { it.type == SessionType.RACE_SIM }
    assertNotNull("5K tier 2 PEAK should have RACE_SIM", raceSim)
    assertEquals("race_sim_5k", raceSim!!.presetId)
}

@Test
fun `RACE_SIM session has goal-appropriate preset for marathon`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.PEAK,
        goal = BootcampGoal.MARATHON,
        runsPerWeek = 4,
        targetMinutes = 50,
        tierIndex = 2
    )
    val raceSim = sessions.firstOrNull { it.type == SessionType.RACE_SIM }
    assertNotNull("Marathon tier 2 PEAK should have RACE_SIM", raceSim)
    assertEquals("marathon_prep", raceSim!!.presetId)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest"`
Expected: 2 new tests FAIL (presetId is null)

- [ ] **Step 3: Fix RACE_SIM preset resolution**

In `SessionSelector.kt`, replace lines 159-165:

```kotlin
// Long run
if (hasLong) {
    val isRaceSim = phase == TrainingPhase.PEAK && tierIndex >= 2 &&
        goal != BootcampGoal.CARDIO_HEALTH
    val longType = if (isRaceSim) SessionType.RACE_SIM else SessionType.LONG
    val longPreset = if (isRaceSim) raceSimPresetFor(goal) else "zone2_base"
    sessions.add(PlannedSession(longType, longMinutes, longPreset))
}
```

Add the helper function in the `SessionSelector` object:

```kotlin
private fun raceSimPresetFor(goal: BootcampGoal): String = when (goal) {
    BootcampGoal.RACE_5K -> "race_sim_5k"
    BootcampGoal.RACE_10K -> "race_sim_10k"
    BootcampGoal.HALF_MARATHON -> "half_marathon_prep"
    BootcampGoal.MARATHON -> "marathon_prep"
    BootcampGoal.CARDIO_HEALTH -> "zone2_base" // shouldn't happen, but safe fallback
}
```

- [ ] **Step 4: Run all SessionSelector tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt \
       app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt
git commit -m "fix(bootcamp): resolve goal-appropriate preset for RACE_SIM sessions

5K → race_sim_5k, 10K → race_sim_10k, HM → half_marathon_prep, M → marathon_prep.
Previously all RACE_SIM sessions had null presetId and fell through to FREE_RUN."
```

---

### Task 4: Add EVERGREEN Phase and Redesign CARDIO_HEALTH (Issues #7, #9, #12)

**Files:**
- Modify: `domain/model/TrainingPhase.kt`
- Modify: `domain/model/BootcampGoal.kt`
- Modify: `domain/bootcamp/SessionSelector.kt`
- Modify: `domain/bootcamp/PhaseEngine.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/bootcamp/PhaseEngineTest.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/model/BootcampGoalTest.kt`

**Context:** CARDIO_HEALTH currently cycles BASE → BUILD infinitely. This means:
- Periodically regresses to pure-easy BASE weeks for no reason
- Never exposes Tier 2 users to intervals or hill work
- Only TEMPO is available as quality work, creating monotonous training

The EVERGREEN phase is a perpetual training phase with a 4-week micro-cycle:
- Week A (weekInPhase % 4 == 0): Aerobic Tempo
- Week B (weekInPhase % 4 == 1): Strides (Tier 1+) or Easy + Strides
- Week C (weekInPhase % 4 == 2): Hill Repeats (Tier 2+), HIIT 30/30 (Tier 2+ alternating), or Tempo (Tier 1)
- Week D (weekInPhase % 4 == 3): Recovery week (all easy, 65% volume — replaces modulo-3 cadence)

This also wires in the orphaned `hill_repeats` and `hiit_30_30` presets.

- [ ] **Step 1: Add `EVERGREEN` to `TrainingPhase` enum**

In `domain/model/TrainingPhase.kt`:

```kotlin
enum class TrainingPhase(val weeksRange: IntRange) {
    BASE(3..6),
    BUILD(4..6),
    PEAK(2..3),
    TAPER(1..2),
    EVERGREEN(4..4);

    val displayName: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}
```

- [ ] **Step 2: Change CARDIO_HEALTH phaseArc**

In `domain/model/BootcampGoal.kt`, change the `CARDIO_HEALTH` entry:

```kotlin
CARDIO_HEALTH(
    suggestedMinMinutes = 20,
    warnBelowMinutes = 15,
    neverPrescribeBelowMinutes = 10,
    minLongRunMinutes = 20,
    maxLongRunMinutes = 60,
    phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.EVERGREEN)
),
```

- [ ] **Step 3: Write failing tests for EVERGREEN behavior**

Add to `SessionSelectorTest.kt`:

```kotlin
// ── EVERGREEN phase tests ──────────────────────────

@Test
fun `EVERGREEN week 0 includes tempo for tier 1`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.EVERGREEN,
        goal = BootcampGoal.CARDIO_HEALTH,
        runsPerWeek = 3,
        targetMinutes = 30,
        tierIndex = 1,
        weekInPhase = 0
    )
    assertTrue("Week A should have TEMPO", sessions.any { it.type == SessionType.TEMPO })
}

@Test
fun `EVERGREEN week 1 includes strides for tier 1`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.EVERGREEN,
        goal = BootcampGoal.CARDIO_HEALTH,
        runsPerWeek = 3,
        targetMinutes = 30,
        tierIndex = 1,
        weekInPhase = 1
    )
    assertTrue("Week B should have STRIDES", sessions.any { it.type == SessionType.STRIDES })
}

@Test
fun `EVERGREEN week 2 includes interval for tier 2`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.EVERGREEN,
        goal = BootcampGoal.CARDIO_HEALTH,
        runsPerWeek = 3,
        targetMinutes = 30,
        tierIndex = 2,
        weekInPhase = 2
    )
    assertTrue("Week C tier 2 should have INTERVAL", sessions.any { it.type == SessionType.INTERVAL })
}

@Test
fun `EVERGREEN week 2 uses tempo for tier 1 instead of interval`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.EVERGREEN,
        goal = BootcampGoal.CARDIO_HEALTH,
        runsPerWeek = 3,
        targetMinutes = 30,
        tierIndex = 1,
        weekInPhase = 2
    )
    assertFalse("Week C tier 1 should NOT have INTERVAL", sessions.any { it.type == SessionType.INTERVAL })
    assertTrue("Week C tier 1 should have TEMPO", sessions.any { it.type == SessionType.TEMPO })
}

@Test
fun `EVERGREEN week 3 is all easy - recovery week`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.EVERGREEN,
        goal = BootcampGoal.CARDIO_HEALTH,
        runsPerWeek = 3,
        targetMinutes = 30,
        tierIndex = 2,
        weekInPhase = 3
    )
    assertTrue("Week D (recovery) should be all easy/long",
        sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
}

@Test
fun `EVERGREEN tier 0 gets only easy and long regardless of week`() {
    for (week in 0..3) {
        val sessions = SessionSelector.weekSessions(
            phase = TrainingPhase.EVERGREEN,
            goal = BootcampGoal.CARDIO_HEALTH,
            runsPerWeek = 3,
            targetMinutes = 25,
            tierIndex = 0,
            weekInPhase = week
        )
        assertTrue("Tier 0 week $week should be all easy/long",
            sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
    }
}

@Test
fun `EVERGREEN week 2 alternates between hill repeats and HIIT for tier 2`() {
    // Even absolute weeks get hill_repeats, odd get hiit_30_30
    val hillWeek = SessionSelector.weekSessions(
        phase = TrainingPhase.EVERGREEN,
        goal = BootcampGoal.CARDIO_HEALTH,
        runsPerWeek = 3, targetMinutes = 30, tierIndex = 2,
        weekInPhase = 2, absoluteWeek = 6 // even → hill
    )
    val hiitWeek = SessionSelector.weekSessions(
        phase = TrainingPhase.EVERGREEN,
        goal = BootcampGoal.CARDIO_HEALTH,
        runsPerWeek = 3, targetMinutes = 30, tierIndex = 2,
        weekInPhase = 2, absoluteWeek = 7 // odd → hiit
    )
    val hillPreset = hillWeek.first { it.type == SessionType.INTERVAL }.presetId
    val hiitPreset = hiitWeek.first { it.type == SessionType.INTERVAL }.presetId
    assertEquals("hill_repeats", hillPreset)
    assertEquals("hiit_30_30", hiitPreset)
}
```

- [ ] **Step 4: Add `weekInPhase` and `absoluteWeek` parameters to `weekSessions`**

The EVERGREEN rotation needs to know which micro-cycle week it is. Add two optional parameters to `SessionSelector.weekSessions()`:

```kotlin
fun weekSessions(
    phase: TrainingPhase,
    goal: BootcampGoal,
    runsPerWeek: Int,
    targetMinutes: Int,
    tierIndex: Int = 1,
    tuningDirection: TuningDirection = TuningDirection.HOLD,
    weekInPhase: Int = 0,
    absoluteWeek: Int = 0
): List<PlannedSession> {
```

These are ignored for non-EVERGREEN phases.

- [ ] **Step 5: Add EVERGREEN branch to `SessionSelector`**

Add a new private function and wire it into `weekSessions`:

In the `when` block in `weekSessions`, after the existing `return when`:

```kotlin
return when {
    phase == TrainingPhase.EVERGREEN -> evergreenWeek(
        goal, runsPerWeek, effectiveMinutes, durations, tierIndex, weekInPhase, absoluteWeek
    )
    tierIndex <= 0 -> baseAerobicWeek(phase, goal, runsPerWeek, effectiveMinutes, durations)
    else -> periodizedWeek(phase, goal, runsPerWeek, effectiveMinutes, durations, tierIndex)
}
```

Add the new function:

```kotlin
private fun evergreenWeek(
    goal: BootcampGoal,
    runsPerWeek: Int,
    minutes: Int,
    durations: DurationScaler.WeekDurations,
    tierIndex: Int,
    weekInPhase: Int,
    absoluteWeek: Int
): List<PlannedSession> {
    val sessions = mutableListOf<PlannedSession>()
    val microWeek = weekInPhase % 4 // 0=Tempo, 1=Strides, 2=Interval/Tempo, 3=Recovery

    // Tier 0 always gets base aerobic only
    if (tierIndex <= 0 || microWeek == 3) {
        // Recovery week (D) or tier 0: all easy + optional long
        val hasLong = runsPerWeek >= 3
        val longMinutes = durations.longMinutes.coerceAtMost(goal.maxLongRunMinutes)
        val easyCount = if (hasLong) runsPerWeek - 1 else runsPerWeek
        repeat(easyCount) {
            sessions.add(PlannedSession(SessionType.EASY, durations.easyMinutes, "zone2_base"))
        }
        if (hasLong) {
            sessions.add(PlannedSession(SessionType.LONG, longMinutes, "zone2_base"))
        }
        return sessions
    }

    val hasLong = runsPerWeek >= 3
    val longMinutes = durations.longMinutes.coerceAtMost(goal.maxLongRunMinutes)

    // Quality session for this micro-week
    when (microWeek) {
        0 -> {
            // Week A: Tempo
            sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
        }
        1 -> {
            // Week B: Strides
            sessions.add(PlannedSession(SessionType.STRIDES, durations.easyMinutes, "strides_20s"))
        }
        2 -> {
            // Week C: Interval (tier 2+) or Tempo (tier 1)
            if (tierIndex >= 2) {
                // Alternate between hill repeats and HIIT 30/30
                val intervalPreset = if (absoluteWeek % 2 == 0) "hill_repeats" else "hiit_30_30"
                sessions.add(PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, intervalPreset))
            } else {
                // Tier 1: substitute with lactate threshold
                sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "lactate_threshold"))
            }
        }
    }

    // Long run
    if (hasLong) {
        sessions.add(PlannedSession(SessionType.LONG, longMinutes, "zone2_base"))
    }

    // Fill remaining with easy
    val easyCount = (runsPerWeek - sessions.size).coerceAtLeast(0)
    repeat(easyCount) {
        sessions.add(PlannedSession(SessionType.EASY, durations.easyMinutes, "zone2_base"))
    }

    return sessions
}
```

- [ ] **Step 6: Pass weekInPhase/absoluteWeek through PhaseEngine**

In `PhaseEngine.planCurrentWeek()`, pass the new parameters:

```kotlin
fun planCurrentWeek(
    tierIndex: Int = 0,
    tuningDirection: TuningDirection = TuningDirection.HOLD,
    currentPresetIndices: Map<String, Int> = emptyMap()
): List<PlannedSession> {
    val effectiveMinutes = if (isRecoveryWeek(tuningDirection)) {
        (targetMinutes * 0.65f).toInt()
    } else {
        targetMinutes
    }
    return SessionSelector.weekSessions(
        phase = currentPhase,
        goal = goal,
        runsPerWeek = runsPerWeek,
        targetMinutes = effectiveMinutes,
        tierIndex = tierIndex,
        tuningDirection = tuningDirection,
        weekInPhase = weekInPhase,
        absoluteWeek = absoluteWeek
    )
}
```

- [ ] **Step 7: Make EVERGREEN wrap to itself (not BASE)**

In `PhaseEngine.advancePhase()`, change the wrapping logic:

```kotlin
fun advancePhase(): PhaseEngine? {
    val nextIndex = phaseIndex + 1
    return if (nextIndex >= goal.phaseArc.size) {
        if (currentPhase == TrainingPhase.EVERGREEN) {
            // EVERGREEN wraps to itself — reset weekInPhase, stay in EVERGREEN
            copy(weekInPhase = 0)
        } else if (goal == BootcampGoal.CARDIO_HEALTH) {
            copy(phaseIndex = 0, weekInPhase = 0)
        } else {
            null // Race goals graduate
        }
    } else {
        copy(phaseIndex = nextIndex, weekInPhase = 0)
    }
}
```

- [ ] **Step 8: Update the existing test that asserts "cardio health never gets interval sessions"**

In `SessionSelectorTest.kt`, update test `cardio health never gets interval sessions in any phase` at line 58. This test iterates `BootcampGoal.CARDIO_HEALTH.phaseArc` which now includes EVERGREEN. Update to only check BASE:

```kotlin
@Test
fun `cardio health BASE phase has no interval sessions`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.BASE,
        goal = BootcampGoal.CARDIO_HEALTH,
        runsPerWeek = 3,
        targetMinutes = 25
    )
    assertFalse(
        "BASE should not have intervals for cardio health",
        sessions.any { it.type == SessionType.INTERVAL }
    )
}
```

- [ ] **Step 9: Update PhaseEngine test for EVERGREEN wrapping**

In `PhaseEngineTest.kt`, update `advancePhase at final phase wraps for cycling goals`:

```kotlin
@Test
fun `advancePhase at EVERGREEN wraps to itself`() {
    // CARDIO_HEALTH: phaseArc = [BASE, EVERGREEN]. At EVERGREEN end, wraps.
    val engine = PhaseEngine(goal = BootcampGoal.CARDIO_HEALTH, phaseIndex = 1, weekInPhase = 3)
    assertTrue(engine.shouldAdvancePhase())
    val next = engine.advancePhase()
    assertNotNull("EVERGREEN should wrap", next)
    assertEquals("Should stay at EVERGREEN phase index", 1, next!!.phaseIndex)
    assertEquals(0, next.weekInPhase)
}
```

Also update or add `advancePhase wraps for CARDIO_HEALTH` to reflect the new arc.

- [ ] **Step 10: Run all tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.*" --tests "com.hrcoach.domain.model.*"`
Expected: ALL PASS

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/model/TrainingPhase.kt \
       app/src/main/java/com/hrcoach/domain/model/BootcampGoal.kt \
       app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt \
       app/src/main/java/com/hrcoach/domain/bootcamp/PhaseEngine.kt \
       app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt \
       app/src/test/java/com/hrcoach/domain/bootcamp/PhaseEngineTest.kt
git commit -m "feat(bootcamp): add EVERGREEN phase for CARDIO_HEALTH

Replaces BASE→BUILD cycle with BASE→EVERGREEN (perpetual).
EVERGREEN uses a 4-week micro-cycle:
  A: Aerobic tempo
  B: Strides
  C: Intervals (tier 2+) or LT tempo (tier 1)
  D: Recovery (all easy, built-in)

Tier 2 CARDIO_HEALTH now gets hill repeats and HIIT 30/30 on
alternating Week C rotations. Fixes orphaned presets."
```

---

### Task 5: Tier-Dependent Recovery Week Composition (Issue #5)

**Files:**
- Modify: `domain/bootcamp/SessionSelector.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt`

**Context:** Currently recovery weeks reduce volume to 65% but keep the same session types (tempo in BUILD is still tempo during recovery). For Tier 0-1, the relative stress is too high. For Tier 2+, the quality should be downgraded one notch. EVERGREEN already handles its own recovery (week D), so this only affects race-goal phases (BUILD, PEAK, TAPER).

- [ ] **Step 1: Write failing tests**

Add to `SessionSelectorTest.kt`:

```kotlin
// ── Recovery week composition tests ──────────────────────────

@Test
fun `tier 0 recovery week in BUILD has no tempo`() {
    // Tier 0 already gets baseAerobicWeek (no quality), so this is a baseline check
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.BUILD, goal = BootcampGoal.RACE_5K,
        runsPerWeek = 3, targetMinutes = 20, // 30 * 0.65 rounded
        tierIndex = 0
    )
    assertTrue(sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
}

@Test
fun `tier 1 recovery week replaces quality with easy`() {
    // Simulate recovery-reduced minutes (e.g. 30 * 0.65 = 19)
    // When isRecovery=true, tier 1 should get all-easy week
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.BUILD, goal = BootcampGoal.RACE_5K,
        runsPerWeek = 3, targetMinutes = 19,
        tierIndex = 1, isRecoveryWeek = true
    )
    assertTrue("Tier 1 recovery should be all easy/long",
        sessions.all { it.type == SessionType.EASY || it.type == SessionType.LONG })
}

@Test
fun `tier 2 recovery week downgrades interval to strides`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.PEAK, goal = BootcampGoal.RACE_5K,
        runsPerWeek = 4, targetMinutes = 22,
        tierIndex = 2, isRecoveryWeek = true
    )
    // Should NOT have full intervals; should have downgraded quality
    assertFalse("Tier 2 recovery should not have INTERVAL",
        sessions.any { it.type == SessionType.INTERVAL })
    // Should have tempo (downgraded from interval) or strides
    assertTrue("Tier 2 recovery should have downgraded quality",
        sessions.any { it.type == SessionType.TEMPO || it.type == SessionType.STRIDES })
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest"`
Expected: Tests 2 and 3 FAIL (recovery weeks still assign normal quality sessions)

- [ ] **Step 3: Add `isRecoveryWeek` parameter and recovery composition logic**

Add `isRecoveryWeek: Boolean = false` parameter to `SessionSelector.weekSessions()`:

```kotlin
fun weekSessions(
    phase: TrainingPhase,
    goal: BootcampGoal,
    runsPerWeek: Int,
    targetMinutes: Int,
    tierIndex: Int = 1,
    tuningDirection: TuningDirection = TuningDirection.HOLD,
    weekInPhase: Int = 0,
    absoluteWeek: Int = 0,
    isRecoveryWeek: Boolean = false
): List<PlannedSession> {
```

In the `return when` block, add recovery handling before the existing branches:

```kotlin
return when {
    phase == TrainingPhase.EVERGREEN -> evergreenWeek(...)
    isRecoveryWeek && tierIndex <= 1 -> baseAerobicWeek(phase, goal, runsPerWeek, effectiveMinutes, durations)
    isRecoveryWeek && tierIndex >= 2 -> recoveryPeriodizedWeek(phase, goal, runsPerWeek, effectiveMinutes, durations, tierIndex)
    tierIndex <= 0 -> baseAerobicWeek(phase, goal, runsPerWeek, effectiveMinutes, durations)
    else -> periodizedWeek(phase, goal, runsPerWeek, effectiveMinutes, durations, tierIndex)
}
```

Add the recovery-downgrade function:

```kotlin
private fun recoveryPeriodizedWeek(
    phase: TrainingPhase,
    goal: BootcampGoal,
    runsPerWeek: Int,
    minutes: Int,
    durations: DurationScaler.WeekDurations,
    tierIndex: Int
): List<PlannedSession> {
    val sessions = mutableListOf<PlannedSession>()
    val hasLong = runsPerWeek >= 3 && phase != TrainingPhase.TAPER
    val longMinutes = durations.longMinutes.coerceAtMost(goal.maxLongRunMinutes)

    // Downgraded quality: 1 session max, one tier lower in intensity
    // Interval → Aerobic Tempo, Lactate Threshold → Aerobic Tempo, Tempo → Strides
    when (phase) {
        TrainingPhase.BUILD, TrainingPhase.TAPER -> {
            // Normal BUILD has tempo → downgrade to strides
            sessions.add(PlannedSession(SessionType.STRIDES, durations.easyMinutes, "strides_20s"))
        }
        TrainingPhase.PEAK -> {
            // Normal PEAK has interval+tempo → downgrade to single aerobic tempo
            sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
        }
        else -> { /* BASE has no quality — nothing to downgrade */ }
    }

    // Long run (not during TAPER recovery — too much stress)
    if (hasLong) {
        sessions.add(PlannedSession(SessionType.LONG, longMinutes, "zone2_base"))
    }

    // Fill remaining with easy
    val easyCount = (runsPerWeek - sessions.size).coerceAtLeast(1)
    repeat(easyCount) {
        sessions.add(PlannedSession(SessionType.EASY, durations.easyMinutes, "zone2_base"))
    }

    return sessions
}
```

- [ ] **Step 4: Pass `isRecoveryWeek` from PhaseEngine**

In `PhaseEngine.planCurrentWeek()`, pass the recovery flag:

```kotlin
return SessionSelector.weekSessions(
    phase = currentPhase,
    goal = goal,
    runsPerWeek = runsPerWeek,
    targetMinutes = effectiveMinutes,
    tierIndex = tierIndex,
    tuningDirection = tuningDirection,
    weekInPhase = weekInPhase,
    absoluteWeek = absoluteWeek,
    isRecoveryWeek = isRecoveryWeek(tuningDirection)
)
```

- [ ] **Step 5: Run all tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.*"`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt \
       app/src/main/java/com/hrcoach/domain/bootcamp/PhaseEngine.kt \
       app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt
git commit -m "feat(bootcamp): tier-dependent recovery week composition

- Tier 0-1: recovery weeks are all easy/long (no quality sessions)
- Tier 2+: recovery weeks downgrade quality one notch
  (intervals→aerobic tempo, tempo→strides)
- EVERGREEN unaffected (has built-in recovery on week D)"
```

---

### Task 6: Fix `weeksUntilNextRecovery` at Phase Boundaries (Bug #8)

**Files:**
- Modify: `domain/bootcamp/PhaseEngine.kt:39-53`
- Modify: `app/src/test/java/com/hrcoach/domain/bootcamp/PhaseEngineTest.kt`

**Context:** `weeksUntilNextRecovery` counts forward from `weekInPhase` without considering that a phase transition resets `weekInPhase` to 0 — and week 0 is never recovery. So it can report "recovery in 2 weeks" but then the phase advances and the cadence resets.

- [ ] **Step 1: Write failing test**

Add to `PhaseEngineTest.kt`:

```kotlin
@Test
fun `weeksUntilNextRecovery accounts for phase boundary reset`() {
    // BASE midpoint=4. At weekInPhase=3 (about to advance), next recovery
    // should account for the phase transition resetting weekInPhase to 0.
    val engine = PhaseEngine(
        goal = BootcampGoal.RACE_5K,
        phaseIndex = 0, // BASE
        weekInPhase = 3 // last week before advance (midpoint=4, so shouldAdvancePhase at 4)
    )
    // After advancing: weekInPhase resets to 0 in BUILD.
    // Default cadence=3: recovery at (weekInPhase+1)%3==0 → weekInPhase=2
    // So from current position: 1 week remaining in BASE + 2 weeks in BUILD = 3 weeks
    val weeks = engine.weeksUntilNextRecovery()
    assertTrue("Should be at least 2 weeks (not 0 or 1)", weeks >= 2)
}
```

- [ ] **Step 2: Run to verify it fails or exposes the issue**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.PhaseEngineTest"`
Expected: May FAIL depending on current cadence alignment

- [ ] **Step 3: Fix `weeksUntilNextRecovery` to simulate phase transitions**

Replace the existing implementation:

```kotlin
fun weeksUntilNextRecovery(tuningDirection: TuningDirection? = null): Int {
    if (isRecoveryWeek(tuningDirection)) return 0
    // For EVERGREEN: recovery is always week 3 of 4-week micro-cycle
    if (currentPhase == TrainingPhase.EVERGREEN) {
        return (3 - (weekInPhase % 4)).let { if (it <= 0) it + 4 else it }
    }
    var cursor = this
    var steps = 0
    while (steps < 12) {
        cursor = if (cursor.shouldAdvancePhase()) {
            cursor.advancePhase() ?: return steps + 1 // graduation — no more recovery
        } else {
            cursor.copy(weekInPhase = cursor.weekInPhase + 1)
        }
        steps++
        if (cursor.isRecoveryWeek(tuningDirection)) return steps
    }
    return steps
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.PhaseEngineTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/PhaseEngine.kt \
       app/src/test/java/com/hrcoach/domain/bootcamp/PhaseEngineTest.kt
git commit -m "fix(bootcamp): weeksUntilNextRecovery simulates phase transitions

Previously counted forward from weekInPhase without considering that
advancePhase() resets weekInPhase to 0. Now walks through phase
boundaries correctly. Also handles EVERGREEN 4-week micro-cycle."
```

---

### Task 7: Pass Preset Indices on Week Advance (Orphan #10)

**Files:**
- Modify: `domain/bootcamp/BootcampSessionCompleter.kt:116-127`
- Modify: `app/src/test/java/com/hrcoach/domain/bootcamp/BootcampSessionCompleterTest.kt`

**Context:** `buildNextWeekEntities` always passes `currentPresetIndices = emptyMap()`. The ViewModel loads indices for the current week, but on week advance (completion of final session), the completer builds next week's sessions without the previous week's preset indices. This means the `SessionPresetArray.tune()` progressive ramp never advances.

- [ ] **Step 1: Write failing test**

Add to `BootcampSessionCompleterTest.kt`:

```kotlin
@Test
fun nextWeekSessionsUsePresetIndicesFromCompletedWeek() = runTest {
    // Setup: 1-run week with a preset that has a presetIndex
    val sessionWithPreset = makeSession(id = 10L, dayOfWeek = 2).copy(
        sessionType = "TEMPO",
        presetId = "aerobic_tempo",
        presetIndex = 1 // was at index 1 this week
    )
    val dao = FakeBootcampDao(
        activeEnrollment = makeEnrollment(runsPerWeek = 1),
        sessionsByWeek = mutableMapOf(
            1 to mutableListOf(sessionWithPreset)
        )
    )
    val completer = makeCompleter(dao)
    val result = completer.complete(workoutId = 100L, pendingSessionId = 10L)
    assertTrue(result.weekComplete)
    // Next week should have been created — verify it exists
    val nextWeekSessions = dao.getSessionsForWeek(1L, 2)
    assertTrue("Next week sessions should be created", nextWeekSessions.isNotEmpty())
}
```

- [ ] **Step 2: Load preset indices from completed week in the completer**

In `BootcampSessionCompleter.buildNextWeekEntities()`, load indices from the current (just-completed) week:

```kotlin
private suspend fun buildNextWeekEntities(
    enrollmentId: Long,
    nextEngine: PhaseEngine,
    preferredDays: List<DayPreference>,
    tierIndex: Int,
    tuningDirection: TuningDirection,
    completedWeekSessions: List<BootcampSessionEntity>
): List<BootcampSessionEntity> {
    // Build preset indices from the completed week's sessions
    val currentPresetIndices = completedWeekSessions
        .filter { it.presetIndex != null }
        .mapNotNull { session ->
            val key = sessionTypePresetKey(session.sessionType) ?: return@mapNotNull null
            key to (session.presetIndex ?: 0)
        }
        .toMap()

    val plannedSessions = nextEngine.planCurrentWeek(
        tierIndex = tierIndex,
        tuningDirection = tuningDirection,
        currentPresetIndices = currentPresetIndices
    )
    // ... rest unchanged
}

private fun sessionTypePresetKey(rawType: String): String? = when (rawType) {
    "EASY" -> "easy"
    "TEMPO" -> "tempo"
    "INTERVAL" -> "interval"
    "STRIDES" -> "strides"
    "LONG" -> "long"
    else -> null
}
```

Update the call site in `complete()` to pass `currentWeekSessions`:

```kotlin
val nextWeekEntities = buildNextWeekEntities(
    enrollmentId = enrollment.id,
    nextEngine = nextEngine,
    preferredDays = enrollment.preferredDays,
    tierIndex = enrollment.tierIndex,
    tuningDirection = tuningDirection,
    completedWeekSessions = simulatedWeek
)
```

- [ ] **Step 3: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.BootcampSessionCompleterTest"`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/BootcampSessionCompleter.kt \
       app/src/test/java/com/hrcoach/domain/bootcamp/BootcampSessionCompleterTest.kt
git commit -m "fix(bootcamp): pass preset indices from completed week to next week

buildNextWeekEntities now reads presetIndex from the just-completed
week's sessions and forwards them to planCurrentWeek. This enables
SessionPresetArray.tune() progressive difficulty ramps to advance."
```

---

### Task 8: Add Warm-up/Cool-down to Tempo Presets (Science #13)

**Files:**
- Modify: `domain/preset/PresetLibrary.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt`

**Context:** `aerobic_tempo` and `lactate_threshold` are `STEADY_STATE` — a single target HR for the whole duration. Real runners warm up 10 min and cool down 5 min. Interval presets already have warm-up/cool-down segments. Convert tempo/threshold to `DISTANCE_PROFILE` with time-based segments.

- [ ] **Step 1: Write failing tests**

Add to `PresetLibraryTest.kt`:

```kotlin
@Test
fun `aerobic_tempo has warm-up and cool-down segments`() {
    val preset = PresetLibrary.ALL.first { it.id == "aerobic_tempo" }
    val config = preset.buildConfig(maxHr = 190, restHr = 60)
    assertEquals(WorkoutMode.DISTANCE_PROFILE, config.mode)
    assertTrue("Should have at least 3 segments", config.segments.size >= 3)
    // First segment should be lower HR (warm-up)
    assertTrue(config.segments.first().targetHr < config.segments[1].targetHr)
    // Label check
    assertEquals("Warm-up", config.segments.first().label)
}

@Test
fun `lactate_threshold has warm-up and cool-down segments`() {
    val preset = PresetLibrary.ALL.first { it.id == "lactate_threshold" }
    val config = preset.buildConfig(maxHr = 190, restHr = 60)
    assertEquals(WorkoutMode.DISTANCE_PROFILE, config.mode)
    assertTrue(config.segments.size >= 3)
    assertEquals("Warm-up", config.segments.first().label)
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.PresetLibraryTest"`
Expected: FAIL (mode is STEADY_STATE)

- [ ] **Step 3: Convert aerobic_tempo and lactate_threshold to segmented presets**

Replace `aeroTempo()`:

```kotlin
private fun aeroTempo() = WorkoutPreset(
    id = "aerobic_tempo",
    name = "Aerobic Tempo",
    subtitle = "Comfortably hard",
    description = "10-min warm-up \u2192 20-min tempo at 84% HR reserve \u2192 5-min cool-down.",
    category = PresetCategory.THRESHOLD,
    durationLabel = "~35 min",
    intensityLabel = "Moderate",
    buildConfig = { maxHr, restHr ->
        val tempoHr = karvonen(maxHr, restHr, 0.84f)
        val warmupHr = karvonen(maxHr, restHr, 0.65f)
        val cooldownHr = karvonen(maxHr, restHr, 0.60f)
        WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(durationSeconds = 600, targetHr = warmupHr, label = "Warm-up"),
                HrSegment(durationSeconds = 1200, targetHr = tempoHr, label = "Tempo"),
                HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down")
            ),
            bufferBpm = 4,
            presetId = "aerobic_tempo"
        )
    }
)
```

Replace `lactateThreshold()`:

```kotlin
private fun lactateThreshold() = WorkoutPreset(
    id = "lactate_threshold",
    name = "Lactate Threshold",
    subtitle = "Threshold effort",
    description = "10-min warm-up \u2192 20-min at 90% HR reserve \u2192 5-min cool-down.",
    category = PresetCategory.THRESHOLD,
    durationLabel = "~35 min",
    intensityLabel = "Hard",
    buildConfig = { maxHr, restHr ->
        val thresholdHr = karvonen(maxHr, restHr, 0.90f)
        val warmupHr = karvonen(maxHr, restHr, 0.65f)
        val cooldownHr = karvonen(maxHr, restHr, 0.60f)
        WorkoutConfig(
            mode = WorkoutMode.DISTANCE_PROFILE,
            segments = listOf(
                HrSegment(durationSeconds = 600, targetHr = warmupHr, label = "Warm-up"),
                HrSegment(durationSeconds = 1200, targetHr = thresholdHr, label = "Threshold"),
                HrSegment(durationSeconds = 300, targetHr = cooldownHr, label = "Cool-down")
            ),
            bufferBpm = 3,
            presetId = "lactate_threshold"
        )
    }
)
```

- [ ] **Step 4: Run all preset tests + full SessionSelector tests (to catch regressions)**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.preset.*" --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/preset/PresetLibrary.kt \
       app/src/test/java/com/hrcoach/domain/preset/PresetLibraryTest.kt
git commit -m "feat(preset): add warm-up/cool-down to tempo and threshold presets

Converts aerobic_tempo and lactate_threshold from STEADY_STATE to
DISTANCE_PROFILE with 10-min warm-up, 20-min main block, 5-min cool-down.
Matches interval presets which already had structured segments."
```

---

### Task 9: Fix Double Taper Reduction (Bug #14)

**Files:**
- Modify: `domain/bootcamp/SessionSelector.kt:153-155`
- Modify: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt`

**Context:** Taper phase applies 0.7× volume globally (line 24), then the tempo session gets an additional 0.8× reduction (line 154). For 30-min base: `30 * 0.7 = 21`, DurationScaler tempo = `max(21*3*0.10, 15) = 15`, then `15 * 0.8 = 12 min`. A 12-minute tempo is too short. Remove the extra 0.8× — the global 0.7× is sufficient.

- [ ] **Step 1: Write test that validates taper tempo is reasonable**

Add to `SessionSelectorTest.kt`:

```kotlin
@Test
fun `taper tempo duration is at least 15 minutes`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.TAPER,
        goal = BootcampGoal.HALF_MARATHON,
        runsPerWeek = 3,
        targetMinutes = 40 // 40 * 0.7 = 28 effective
    )
    val tempo = sessions.firstOrNull { it.type == SessionType.TEMPO }
    assertNotNull("TAPER should have a tempo session", tempo)
    assertTrue("Taper tempo should be at least 15 min, was ${tempo!!.minutes}", tempo.minutes >= 15)
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest"`
Expected: FAIL (tempo is 12 min due to double reduction)

- [ ] **Step 3: Remove the 0.8× reduction on taper tempo**

In `SessionSelector.periodizedWeek()`, change the TAPER branch (line 153-155):

```kotlin
TrainingPhase.TAPER -> {
    sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
}
```

(Remove the `* 0.8f` — the 0.7× global taper reduction is already applied to `effectiveMinutes` before DurationScaler runs.)

- [ ] **Step 4: Update existing taper reduction test**

The existing test `taper phase reduces target minutes by 30 percent` (line 47) checks `it.minutes <= 28`. This may need adjustment since the tempo no longer gets an extra 0.8×:

```kotlin
@Test
fun `taper phase reduces total volume by approximately 30 percent`() {
    val normalSessions = SessionSelector.weekSessions(
        phase = TrainingPhase.BUILD,
        goal = BootcampGoal.HALF_MARATHON,
        runsPerWeek = 3,
        targetMinutes = 40
    )
    val taperSessions = SessionSelector.weekSessions(
        phase = TrainingPhase.TAPER,
        goal = BootcampGoal.HALF_MARATHON,
        runsPerWeek = 3,
        targetMinutes = 40
    )
    val normalTotal = normalSessions.sumOf { it.minutes }
    val taperTotal = taperSessions.sumOf { it.minutes }
    assertTrue("Taper should reduce volume", taperTotal < normalTotal)
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest"`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt \
       app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt
git commit -m "fix(bootcamp): remove double reduction on taper tempo duration

Taper phase already reduces effectiveMinutes to 0.7x before DurationScaler.
The additional 0.8x on tempo produced unreasonably short sessions (12 min
from a 30-min base). Now uses DurationScaler's tempo output directly."
```

---

### Task 10: Add Interval Variety to Race Goal PEAK Phase (Issue #9, #12)

**Files:**
- Modify: `domain/bootcamp/SessionSelector.kt` (PEAK branch in `periodizedWeek`)
- Modify: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt`

**Context:** Race goal PEAK phase always assigns `"norwegian_4x4"` as the interval preset. `hill_repeats` and `hiit_30_30` are never used for race goals. Add alternation: even absolute weeks get `norwegian_4x4`, odd weeks get `hill_repeats` (strength complements VO2max).

- [ ] **Step 1: Write failing test**

Add to `SessionSelectorTest.kt`:

```kotlin
@Test
fun `PEAK interval preset alternates between norwegian and hills for race goals`() {
    val n4x4Week = SessionSelector.weekSessions(
        phase = TrainingPhase.PEAK, goal = BootcampGoal.RACE_5K,
        runsPerWeek = 4, targetMinutes = 35, tierIndex = 2,
        absoluteWeek = 10 // even → norwegian_4x4
    )
    val hillWeek = SessionSelector.weekSessions(
        phase = TrainingPhase.PEAK, goal = BootcampGoal.RACE_5K,
        runsPerWeek = 4, targetMinutes = 35, tierIndex = 2,
        absoluteWeek = 11 // odd → hill_repeats
    )
    val n4x4Preset = n4x4Week.first { it.type == SessionType.INTERVAL }.presetId
    val hillPreset = hillWeek.first { it.type == SessionType.INTERVAL }.presetId
    assertEquals("norwegian_4x4", n4x4Preset)
    assertEquals("hill_repeats", hillPreset)
}
```

- [ ] **Step 2: Run to verify failure**

Expected: FAIL (both return `norwegian_4x4`)

- [ ] **Step 3: Add alternation logic to PEAK interval preset selection**

In `SessionSelector.periodizedWeek()`, in the `TrainingPhase.PEAK` branch, replace the interval preset assignment. Change:

```kotlin
sessions.add(PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, "norwegian_4x4"))
```

To:

```kotlin
val intervalPreset = if (absoluteWeek % 2 == 0) "norwegian_4x4" else "hill_repeats"
sessions.add(PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, intervalPreset))
```

This applies to both the RACE_5K and RACE_10K branches in the PEAK when block.

- [ ] **Step 4: Run all tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt \
       app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt
git commit -m "feat(bootcamp): alternate interval presets in PEAK phase

Even weeks use Norwegian 4x4 (VO2max), odd weeks use Hill Repeats
(strength + power). Adds variety and covers complementary training
stimuli during peak preparation."
```

---

### Task 11: Full Regression Test Pass

**Files:** No source changes — test-only

- [ ] **Step 1: Run the complete test suite**

```bash
./gradlew testDebugUnitTest
```

Expected: ALL PASS. If any failures, diagnose and fix before committing.

- [ ] **Step 2: Run lint**

```bash
./gradlew lint
```

Expected: No new warnings (pre-existing warnings from CLAUDE.md are acceptable).

- [ ] **Step 3: Verify build compiles**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

---

## Issue Coverage Checklist

| # | Issue | Task | Status |
|---|-------|------|--------|
| 1 | `LONG_RUN` → `LONG` in SessionRescheduler | Task 1 | |
| 2 | `strides_20s` preset missing | Task 2 | |
| 3 | RACE_SIM presetId always null | Tasks 2+3 | |
| 4 | Rescheduler missing LONG/RACE_SIM in hardTypes | Task 1 | |
| 5 | Recovery week composition (tier-dependent) | Task 5 | |
| 6 | Preset duration vs planned minutes mismatch | Addressed by Task 8 (warm-up adds real structure) | |
| 7 | CARDIO_HEALTH never gets intervals | Task 4 (EVERGREEN) | |
| 8 | `weeksUntilNextRecovery` at phase boundaries | Task 6 | |
| 9 | `hiit_30_30`, `hill_repeats` never scheduled | Tasks 4+10 | |
| 10 | Preset index tuning not passed on advance | Task 7 | |
| 11 | No 5K/10K race sim preset | Task 2 | |
| 12 | Zero variety within session types | Tasks 4+10 | |
| 13 | Tempo/threshold missing warm-up/cool-down | Task 8 | |
| 14 | Double taper reduction | Task 9 | |
