# Bootcamp Audit Fixes — Progression, Safety & Onboarding Redesign

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the critical safety/logic issues found in the bootcamp audit and redesign the session intensity model + onboarding flow.

**Architecture:** Decouple session intensity from goal distance (tierIndex becomes sole intensity driver, goal drives only session structure). Replace `targetMinutesPerRun` as a duration driver with preset-driven durations capped by a user time availability ceiling. Add a swipeable onboarding carousel before first enrollment. Fix week-advancement dead ends, session status guards, and effort spacing.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, HorizontalPager (foundation.pager)

---

## File Map

### Domain layer (create/modify)

| File | Responsibility | Action |
|------|---------------|--------|
| `domain/bootcamp/SessionSelector.kt` | Week session generation | **Modify**: remove `effectiveTier` compound formula, use `tierIndex` directly, add goal-only structural branching |
| `domain/bootcamp/PhaseEngine.kt` | Phase progression | **Modify**: add graduation detection, fix off-by-one in `shouldAdvancePhase` |
| `domain/bootcamp/TierCtlRanges.kt` | CTL tier ranges | **Modify**: add overtraining ceiling warning threshold |
| `domain/bootcamp/BootcampSessionCompleter.kt` | Session completion | **Modify**: add status guard, make idempotent, fix week-complete logic for skipped/deferred |
| `domain/bootcamp/DurationScaler.kt` | Duration math | **Delete** after migration to presets |
| `domain/bootcamp/SessionDayAssigner.kt` | Hard-effort spacing | **Create**: assigns sessions to days with adjacency checks |
| `domain/bootcamp/FinishingTimeTierMapper.kt` | Tier mapping | **Deferred** — `validateTimeCommitment` removal and `availabilityWarning` will come in a future session |
| `domain/model/BootcampGoal.kt` | Goal enum | **Modify**: remove `tier` field, add `maxLongRunMinutes` per goal |
| `domain/preset/SessionPresetArray.kt` | Preset ladders | **Modify**: add missing presets (long run tier 3, tier 1 walk/run stubs) |

### Data layer (modify)

| File | Responsibility | Action |
|------|---------------|--------|
| `data/db/BootcampEnrollmentEntity.kt` | Enrollment entity | **Modify**: rename `targetMinutesPerRun` → `availableMinutesPerRun` semantically (keep column name for migration compat) |
| `data/db/BootcampDao.kt` | DAO queries | No changes needed |
| `data/repository/BootcampRepository.kt` | Repository | **Modify**: cap `runsPerWeek` to 2..6 in `createEnrollment` |

### UI layer (create/modify)

| File | Responsibility | Action |
|------|---------------|--------|
| `ui/bootcamp/BootcampOnboardingCarousel.kt` | 5-card intro carousel | **Create** |
| `ui/bootcamp/BootcampSetupFlow.kt` | Redesigned setup flow | **Create**: replaces onboarding steps in BootcampScreen |
| `ui/bootcamp/BootcampUiState.kt` | UI state | **Modify**: update onboarding fields for new flow |
| `ui/bootcamp/BootcampViewModel.kt` | ViewModel | **Modify**: update onboarding methods, add preview computation |
| `ui/bootcamp/BootcampSettingsScreen.kt` | Settings | **Modify**: reframe slider as availability ceiling |
| `ui/bootcamp/BootcampSettingsUiState.kt` | Settings state | **Modify**: remove DurationScaler-derived computed properties |
| `ui/bootcamp/BootcampSettingsViewModel.kt` | Settings VM | **Modify**: use preset lookups instead of DurationScaler |
| `ui/navigation/NavGraph.kt` | Navigation | **Modify**: add carousel route |

### Tests (create/modify)

| File | Action |
|------|--------|
| `test/.../SessionSelectorTest.kt` | **Create**: test new tierIndex-only intensity model |
| `test/.../SessionDayAssignerTest.kt` | **Create**: test hard-effort spacing |
| `test/.../BootcampSessionCompleterTest.kt` | **Modify**: add tests for status guard, skipped-week advancement |
| `test/.../PhaseEngineTest.kt` | **Modify**: add graduation test, off-by-one fix test |

---

## Task 1: Fix `effectiveTier` — Decouple Intensity from Goal

This is the highest-priority safety fix. A Marathon beginner currently gets VO2max intervals because `goal.tier` inflates `effectiveTier`.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/model/BootcampGoal.kt`
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt`
- Create: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt`

- [ ] **Step 1: Add failing tests to existing `SessionSelectorTest.kt`**

**IMPORTANT**: `SessionSelectorTest.kt` already exists with 13 tests. APPEND these new tests — do NOT overwrite the file. Some existing tests will need updating in Step 6.

Add to the existing class:

```kotlin
// ── New safety tests (append to existing class) ──────────────────

// CRITICAL: Marathon beginner must NOT get intervals
@Test
fun `marathon tier 0 gets only easy and long sessions in all phases`() {
    for (phase in TrainingPhase.entries) {
        val sessions = SessionSelector.weekSessions(
            phase = phase, goal = BootcampGoal.MARATHON,
            runsPerWeek = 4, targetMinutes = 40, tierIndex = 0
        )
        val types = sessions.map { it.type }.toSet()
        assertTrue("Marathon T0 $phase should have no TEMPO", SessionType.TEMPO !in types)
        assertTrue("Marathon T0 $phase should have no INTERVAL", SessionType.INTERVAL !in types)
        assertTrue("Marathon T0 $phase should have no RACE_SIM", SessionType.RACE_SIM !in types)
    }
}

@Test
fun `tier 1 BUILD gets tempo but not intervals for any goal`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.BUILD, goal = BootcampGoal.RACE_10K,
        runsPerWeek = 4, targetMinutes = 35, tierIndex = 1
    )
    val types = sessions.map { it.type }.toSet()
    assertTrue("T1 BUILD should have TEMPO", SessionType.TEMPO in types)
    assertTrue("T1 BUILD should NOT have INTERVAL", SessionType.INTERVAL !in types)
}

@Test
fun `tier 2 PEAK 5K gets intervals as primary quality session`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.PEAK, goal = BootcampGoal.RACE_5K,
        runsPerWeek = 4, targetMinutes = 35, tierIndex = 2
    )
    val types = sessions.map { it.type }.toSet()
    assertTrue("5K T2 PEAK should have INTERVAL", SessionType.INTERVAL in types)
}

@Test
fun `tier 2 PEAK marathon gets LT tempo as primary quality session`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.PEAK, goal = BootcampGoal.MARATHON,
        runsPerWeek = 4, targetMinutes = 50, tierIndex = 2
    )
    val types = sessions.map { it.type }.toSet()
    assertTrue("Marathon T2 PEAK should have TEMPO", SessionType.TEMPO in types)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest" 2>&1 | tail -20`
Expected: Multiple failures (marathon T0 currently gets intervals via effectiveTier=3)

- [ ] **Step 3: Remove `tier` from BootcampGoal, add `maxLongRunMinutes`**

In `BootcampGoal.kt`, remove the `tier` parameter and add `maxLongRunMinutes`:

```kotlin
enum class BootcampGoal(
    val suggestedMinMinutes: Int,
    val warnBelowMinutes: Int,
    val neverPrescribeBelowMinutes: Int,
    val minLongRunMinutes: Int,
    val maxLongRunMinutes: Int,
    val phaseArc: List<TrainingPhase>
) {
    CARDIO_HEALTH(
        suggestedMinMinutes = 20, warnBelowMinutes = 15,
        neverPrescribeBelowMinutes = 10,
        minLongRunMinutes = 20, maxLongRunMinutes = 60,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD)
    ),
    RACE_5K(
        suggestedMinMinutes = 25, warnBelowMinutes = 20,
        neverPrescribeBelowMinutes = 15,
        minLongRunMinutes = 30, maxLongRunMinutes = 60,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    ),
    RACE_10K(
        suggestedMinMinutes = 30, warnBelowMinutes = 20,
        neverPrescribeBelowMinutes = 15,
        minLongRunMinutes = 40, maxLongRunMinutes = 75,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    ),
    HALF_MARATHON(
        suggestedMinMinutes = 30, warnBelowMinutes = 25,
        neverPrescribeBelowMinutes = 20,
        minLongRunMinutes = 60, maxLongRunMinutes = 120,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    ),
    MARATHON(
        suggestedMinMinutes = 45, warnBelowMinutes = 30,
        neverPrescribeBelowMinutes = 20,
        minLongRunMinutes = 90, maxLongRunMinutes = 150,
        phaseArc = listOf(TrainingPhase.BASE, TrainingPhase.BUILD, TrainingPhase.PEAK, TrainingPhase.TAPER)
    )
}
```

- [ ] **Step 4: Fix all compile errors from `goal.tier` removal**

Search for all references to `goal.tier` and `BootcampGoal.*.tier` across the codebase. The main call site is `SessionSelector.kt:18`. Other references may exist in tests or the ViewModel — fix each one.

- [ ] **Step 5: Rewrite `SessionSelector.weekSessions` with tierIndex-only intensity**

**IMPORTANT**: Keep `tierIndex` default at `1` (not 0) to preserve existing caller behavior. Only callers that explicitly need tier 0 should pass it.

Replace the entire `weekSessions` function body:

```kotlin
fun weekSessions(
    phase: TrainingPhase,
    goal: BootcampGoal,
    runsPerWeek: Int,
    targetMinutes: Int,
    tierIndex: Int = 1,
    tuningDirection: TuningDirection = TuningDirection.HOLD
): List<PlannedSession> {
    val tuningFactor = when (tuningDirection) {
        TuningDirection.PUSH_HARDER -> 1.05f
        TuningDirection.EASE_BACK -> 0.90f
        TuningDirection.HOLD -> 1.0f
    }
    val effectiveMinutes = if (phase == TrainingPhase.TAPER) {
        (targetMinutes * 0.7f * tuningFactor).toInt()
    } else {
        (targetMinutes * tuningFactor).toInt()
    }
    val durations = DurationScaler.compute(runsPerWeek, effectiveMinutes)

    // tierIndex is the SOLE intensity driver
    return when {
        tierIndex <= 0 -> baseAerobicWeek(phase, goal, runsPerWeek, effectiveMinutes, durations)
        else -> periodizedWeek(phase, goal, runsPerWeek, effectiveMinutes, durations, tierIndex)
    }
}
```

Update `baseAerobicWeek` to accept `goal` for long-run capping:
```kotlin
private fun baseAerobicWeek(
    phase: TrainingPhase,
    goal: BootcampGoal,
    runsPerWeek: Int,
    minutes: Int,
    durations: DurationScaler.WeekDurations
): List<PlannedSession> {
    val sessions = mutableListOf<PlannedSession>()
    val hasLong = runsPerWeek >= 3 && phase != TrainingPhase.BASE
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
```

Rewrite `periodizedWeek` with tier-only intensity branching:
```kotlin
private fun periodizedWeek(
    phase: TrainingPhase,
    goal: BootcampGoal,
    runsPerWeek: Int,
    minutes: Int,
    durations: DurationScaler.WeekDurations,
    tierIndex: Int
): List<PlannedSession> {
    val sessions = mutableListOf<PlannedSession>()
    val longMinutes = durations.longMinutes.coerceAtMost(goal.maxLongRunMinutes)

    // Phase gates WHEN quality work appears; tierIndex gates HOW MUCH
    val qualitySessions = when (phase) {
        TrainingPhase.BASE -> 0
        TrainingPhase.BUILD -> 1
        TrainingPhase.PEAK -> if (tierIndex >= 2 && runsPerWeek >= 4) 2 else 1
        TrainingPhase.TAPER -> 1
    }
    val includeStrides = phase == TrainingPhase.BUILD && tierIndex >= 2 && runsPerWeek >= 4
    val hasLong = runsPerWeek >= 3 && phase != TrainingPhase.TAPER

    val easyCount = (runsPerWeek - qualitySessions
        - (if (hasLong) 1 else 0)
        - (if (includeStrides) 1 else 0)).coerceAtLeast(1)

    // Easy runs
    repeat(easyCount) {
        sessions.add(PlannedSession(SessionType.EASY, durations.easyMinutes, "zone2_base"))
    }

    // Quality: tierIndex gates intensity, goal selects type
    when (phase) {
        TrainingPhase.BASE -> {} // No quality in BASE regardless of tier
        TrainingPhase.BUILD -> {
            // Tier 1+: aerobic tempo (Z3)
            sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
            if (includeStrides) {
                val preset = SessionPresetArray.stridesTier2().presetAt(0)
                sessions.add(PlannedSession(SessionType.STRIDES, preset.durationMinutes, preset.presetId))
            }
        }
        TrainingPhase.PEAK -> {
            // Goal determines type; tier 2 unlocks LT/VO2max, tier 1 stays aerobic
            if (tierIndex >= 2) {
                // Full intensity: goal determines primary quality session
                when (goal) {
                    BootcampGoal.RACE_5K -> {
                        sessions.add(PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, "norwegian_4x4"))
                        if (qualitySessions >= 2) sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "lactate_threshold"))
                    }
                    BootcampGoal.RACE_10K -> {
                        sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "lactate_threshold"))
                        if (qualitySessions >= 2) sessions.add(PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, "norwegian_4x4"))
                    }
                    BootcampGoal.HALF_MARATHON, BootcampGoal.MARATHON -> {
                        sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "lactate_threshold"))
                        if (qualitySessions >= 2) sessions.add(PlannedSession(SessionType.TEMPO, (durations.tempoMinutes * 0.8f).toInt(), "aerobic_tempo"))
                    }
                    BootcampGoal.CARDIO_HEALTH -> {
                        sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
                    }
                }
            } else {
                // Tier 1: aerobic tempo only, goal selects variant
                when (goal) {
                    BootcampGoal.RACE_5K -> sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
                    else -> sessions.add(PlannedSession(SessionType.TEMPO, durations.tempoMinutes, "aerobic_tempo"))
                }
            }
        }
        TrainingPhase.TAPER -> {
            sessions.add(PlannedSession(SessionType.TEMPO, (durations.tempoMinutes * 0.8f).toInt(), "aerobic_tempo"))
        }
    }

    // Long run: tier 2 PEAK gets race sim for race goals
    if (hasLong) {
        val isRaceSim = phase == TrainingPhase.PEAK && tierIndex >= 2 &&
            goal != BootcampGoal.CARDIO_HEALTH
        val longType = if (isRaceSim) SessionType.RACE_SIM else SessionType.LONG
        val longPreset = if (isRaceSim) null else "zone2_base"
        sessions.add(PlannedSession(longType, longMinutes, longPreset))
    }

    return sessions
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest" 2>&1 | tail -20`
Expected: All PASS

- [ ] **Step 7: Update existing SessionSelectorTest tests for new model**

Several existing tests relied on the old `effectiveTier = goal.tier + tierIndex - 1` behavior:

- `build phase for marathon includes tempo session` — currently passes with default tierIndex=1 because old effectiveTier=4. Under new model, tierIndex=1 still gets tempo in BUILD. **Should still pass.**
- `peak phase for marathon includes interval session` — currently passes because effectiveTier=4 gave intervals. Under new model, tierIndex=1 is **not** tier 2, so marathon PEAK at tier 1 gets aerobic_tempo, NOT intervals. **Update test**: change to `tierIndex = 2` or update assertion to expect TEMPO instead of INTERVAL.
- `build phase with four runs injects strides for tier two and above` — currently passes because RACE_5K default effectiveTier=2. Under new model, tierIndex=1 does NOT get strides (needs tierIndex >= 2). **Update test**: pass `tierIndex = 2` explicitly.
- `tierIndex 1 preserves current behavior for RACE_5K BUILD` — this test compared default to explicit tierIndex=1, which were identical under old model. Under new model they're still identical (default IS 1). **Should still pass.**
- `tierIndex 2 promotes RACE_5K to 2 quality sessions in PEAK` — old effectiveTier=3. New tierIndex=2 should still produce 2 quality sessions with `runsPerWeek >= 4`. **Should still pass.**

Run after updates: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest" 2>&1 | tail -30`

- [ ] **Step 8: Run full test suite to check for regressions**

Run: `./gradlew testDebugUnitTest 2>&1 | tail -30`
Expected: Fix any remaining compile errors from `goal.tier` removal in other files (BootcampGoalTest, BootcampViewModelTest, etc.).

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "fix(bootcamp): decouple intensity from goal — tierIndex is sole intensity driver

BREAKING: BootcampGoal.tier removed. SessionSelector now uses tierIndex
directly. A Marathon beginner (tierIndex=0) gets all-easy sessions instead
of VO2max intervals. Goal only drives session structure (which types, not
how hard)."
```

---

## Task 2: Fix Week Advancement Dead End (Skipped/Deferred Sessions)

Sessions with SKIPPED or DEFERRED status permanently block week advancement. Fix the completion check and add session status guard.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/BootcampSessionCompleter.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/bootcamp/BootcampSessionCompleterTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `BootcampSessionCompleterTest.kt`, using the existing `FakeBootcampDao`, `makeEnrollment()`, and `makeSession()` helpers. Note: `makeSession` currently takes `(id, enrollmentId, weekNumber, dayOfWeek)` — add a `status` parameter with default `STATUS_SCHEDULED`:

First, update the existing `makeSession` helper to accept an optional `status` parameter:
```kotlin
private fun makeSession(
    id: Long = 10L,
    enrollmentId: Long = 1L,
    weekNumber: Int = 1,
    dayOfWeek: Int = 2,
    status: String = BootcampSessionEntity.STATUS_SCHEDULED
) = BootcampSessionEntity(
    id = id, enrollmentId = enrollmentId, weekNumber = weekNumber,
    dayOfWeek = dayOfWeek, sessionType = "EASY", targetMinutes = 30,
    status = status, completedWorkoutId = null, presetId = null
)
```

Then add these tests:

```kotlin
@Test
fun weekAdvancesWhenRemainingSessionsAreSkipped() = runTest {
    val dao = FakeBootcampDao(
        activeEnrollment = makeEnrollment(runsPerWeek = 3),
        sessionsByWeek = mutableMapOf(
            1 to mutableListOf(
                makeSession(id = 10L, dayOfWeek = 1, status = BootcampSessionEntity.STATUS_COMPLETED),
                makeSession(id = 11L, dayOfWeek = 3, status = BootcampSessionEntity.STATUS_SKIPPED),
                makeSession(id = 12L, dayOfWeek = 5)
            )
        )
    )
    val completer = makeCompleter(dao)
    val result = completer.complete(workoutId = 100L, pendingSessionId = 12L)
    assertTrue(result.completed)
    assertTrue("Week should advance with skipped session", result.weekComplete)
}

@Test
fun completingAlreadyCompletedSessionReturnsFalse() = runTest {
    val dao = FakeBootcampDao(
        activeEnrollment = makeEnrollment(),
        sessionsByWeek = mutableMapOf(
            1 to mutableListOf(
                makeSession(id = 10L, dayOfWeek = 2, status = BootcampSessionEntity.STATUS_COMPLETED)
            )
        )
    )
    val completer = makeCompleter(dao)
    val result = completer.complete(workoutId = 100L, pendingSessionId = 10L)
    assertFalse("Already-completed session should not re-complete", result.completed)
}

@Test
fun completingSkippedSessionReturnsFalse() = runTest {
    val dao = FakeBootcampDao(
        activeEnrollment = makeEnrollment(),
        sessionsByWeek = mutableMapOf(
            1 to mutableListOf(
                makeSession(id = 10L, dayOfWeek = 2, status = BootcampSessionEntity.STATUS_SKIPPED)
            )
        )
    )
    val completer = makeCompleter(dao)
    val result = completer.complete(workoutId = 100L, pendingSessionId = 10L)
    assertFalse("Skipped session should not be completable", result.completed)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.BootcampSessionCompleterTest" 2>&1 | tail -20`

- [ ] **Step 3: Add status guard and fix week-complete logic**

In `BootcampSessionCompleter.kt`, after finding `targetSession` (line 40-41), add:

```kotlin
// Status guard: only SCHEDULED or DEFERRED sessions can be completed
if (targetSession.status != BootcampSessionEntity.STATUS_SCHEDULED &&
    targetSession.status != BootcampSessionEntity.STATUS_DEFERRED) {
    return CompletionResult(completed = false)
}
```

Change the week-complete check (line 52-53) from:
```kotlin
val weekComplete = simulatedWeek.isNotEmpty() &&
    simulatedWeek.all { it.status == BootcampSessionEntity.STATUS_COMPLETED }
```
To:
```kotlin
val weekComplete = simulatedWeek.isNotEmpty() &&
    simulatedWeek.all {
        it.status == BootcampSessionEntity.STATUS_COMPLETED ||
        it.status == BootcampSessionEntity.STATUS_SKIPPED ||
        it.status == BootcampSessionEntity.STATUS_DEFERRED
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.BootcampSessionCompleterTest" 2>&1 | tail -20`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "fix(bootcamp): fix week-advancement dead end and add session status guard

Skipped/deferred sessions no longer block week advancement. Sessions
can only be completed from SCHEDULED or DEFERRED status, preventing
double-completion race conditions."
```

---

## Task 3: Fix Phase Graduation for Race Goals

`advancePhase()` wraps back to phase 0 instead of graduating. A runner completing TAPER should be told they're race-ready.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/PhaseEngine.kt`
- Modify: `app/src/test/java/com/hrcoach/domain/bootcamp/PhaseEngineTest.kt`
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/BootcampSessionCompleter.kt`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `advancePhase returns null when final phase is complete for race goals`() {
    val engine = PhaseEngine(
        goal = BootcampGoal.RACE_5K,
        phaseIndex = 3, // TAPER (last phase)
        weekInPhase = 1 // past midpoint
    )
    assertTrue(engine.shouldAdvancePhase())
    assertNull("Should return null at end of arc, not wrap", engine.advancePhase())
}

@Test
fun `advancePhase wraps for CARDIO_HEALTH`() {
    val engine = PhaseEngine(
        goal = BootcampGoal.CARDIO_HEALTH,
        phaseIndex = 1, // BUILD (last phase for cardio health)
        weekInPhase = 5
    )
    assertTrue(engine.shouldAdvancePhase())
    val next = engine.advancePhase()
    assertNotNull("Cardio health should wrap", next)
    assertEquals(0, next!!.phaseIndex)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.PhaseEngineTest" 2>&1 | tail -20`

- [ ] **Step 3: Change `advancePhase()` return type to nullable**

```kotlin
fun advancePhase(): PhaseEngine? {
    val nextIndex = phaseIndex + 1
    return if (nextIndex >= goal.phaseArc.size) {
        if (goal == BootcampGoal.CARDIO_HEALTH) {
            copy(phaseIndex = 0, weekInPhase = 0) // Cardio Health cycles
        } else {
            null // Race goals graduate
        }
    } else {
        copy(phaseIndex = nextIndex, weekInPhase = 0)
    }
}
```

- [ ] **Step 4: Update BootcampSessionCompleter to handle graduation**

In the `weekComplete` branch of `complete()`, change:
```kotlin
val nextEngine = if (engine.shouldAdvancePhase()) {
    engine.advancePhase()
} else {
    engine.copy(weekInPhase = engine.weekInPhase + 1)
}
```
To:
```kotlin
val nextEngine = if (engine.shouldAdvancePhase()) {
    engine.advancePhase()
} else {
    engine.copy(weekInPhase = engine.weekInPhase + 1)
}

if (nextEngine == null) {
    // Race goal completed — graduate the enrollment
    bootcampRepository.completeSessionOnly(completedSession)
    bootcampRepository.graduateEnrollment(enrollment.id)
    return CompletionResult(
        completed = true,
        weekComplete = true,
        progressLabel = "Program complete! You're race-ready."
    )
}
```

- [ ] **Step 5: Fix all compile errors from nullable `advancePhase()`**

`advancePhase()` is also called in `lookaheadWeeks` (PhaseEngine.kt line 72-91). Update it to stop iterating when `null` is returned — return partial results:

```kotlin
fun lookaheadWeeks(count: Int, tierIndex: Int = 1): List<WeekLookahead> {
    if (count <= 0) return emptyList()
    val result = mutableListOf<WeekLookahead>()
    var cursor = this
    repeat(count) {
        cursor = if (cursor.shouldAdvancePhase()) {
            cursor.advancePhase() ?: return result // graduated — stop lookahead
        } else {
            cursor.copy(weekInPhase = cursor.weekInPhase + 1)
        }
        result.add(
            WeekLookahead(
                weekNumber = cursor.absoluteWeek,
                isRecovery = cursor.isRecoveryWeek,
                sessions = cursor.planCurrentWeek(tierIndex = tierIndex)
            )
        )
    }
    return result
}
```

Also check `BootcampSessionCompleter.complete()` — the null case was handled in Step 4. Search for any other callers of `advancePhase()` in the codebase with: `grep -rn "advancePhase" app/src/`

- [ ] **Step 6: Run all tests**

Run: `./gradlew testDebugUnitTest 2>&1 | tail -30`

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "fix(bootcamp): graduate race goals instead of wrapping back to BASE

advancePhase() returns null when the final phase is complete for race
goals, triggering enrollment graduation. Cardio Health still cycles
as designed."
```

---

## Task 4: Add Hard-Effort Spacing (SessionDayAssigner)

Sessions are currently assigned to preferred days by list index, allowing back-to-back hard sessions.

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionDayAssigner.kt`
- Create: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionDayAssignerTest.kt`
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/BootcampSessionCompleter.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.hrcoach.domain.bootcamp

import org.junit.Assert.*
import org.junit.Test

class SessionDayAssignerTest {

    @Test
    fun `hard sessions are never on consecutive days`() {
        val sessions = listOf(
            PlannedSession(SessionType.EASY, 30, "zone2_base"),
            PlannedSession(SessionType.TEMPO, 25, "aerobic_tempo"),
            PlannedSession(SessionType.LONG, 45, "zone2_base")
        )
        val availableDays = listOf(4, 5, 6) // Thu, Fri, Sat — worst case consecutive
        val assigned = SessionDayAssigner.assign(sessions, availableDays)

        val hardDays = assigned
            .filter { it.first.type in SessionDayAssigner.HARD_TYPES }
            .map { it.second }
            .sorted()

        for (i in 0 until hardDays.size - 1) {
            assertTrue(
                "Hard sessions on days ${hardDays[i]} and ${hardDays[i+1]} are consecutive",
                hardDays[i+1] - hardDays[i] >= 2
            )
        }
    }

    @Test
    fun `long run bias day gets the LONG session`() {
        val sessions = listOf(
            PlannedSession(SessionType.EASY, 30, "zone2_base"),
            PlannedSession(SessionType.EASY, 30, "zone2_base"),
            PlannedSession(SessionType.LONG, 45, "zone2_base")
        )
        val availableDays = listOf(1, 3, 6) // Mon, Wed, Sat
        val longRunBiasDay = 6
        val assigned = SessionDayAssigner.assign(sessions, availableDays, longRunBiasDay)
        val longSession = assigned.first { it.first.type == SessionType.LONG }
        assertEquals("Long run should be on bias day", 6, longSession.second)
    }

    @Test
    fun `all sessions are assigned to available days`() {
        val sessions = listOf(
            PlannedSession(SessionType.EASY, 30, "zone2_base"),
            PlannedSession(SessionType.TEMPO, 25, "aerobic_tempo"),
            PlannedSession(SessionType.INTERVAL, 30, "norwegian_4x4"),
            PlannedSession(SessionType.LONG, 50, "zone2_base")
        )
        val availableDays = listOf(1, 3, 5, 7) // Well-spaced
        val assigned = SessionDayAssigner.assign(sessions, availableDays)
        assertEquals(sessions.size, assigned.size)
        assigned.forEach { assertTrue(it.second in availableDays) }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail (class not found)**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionDayAssignerTest" 2>&1 | tail -10`

- [ ] **Step 3: Implement SessionDayAssigner**

```kotlin
package com.hrcoach.domain.bootcamp

object SessionDayAssigner {

    val HARD_TYPES = setOf(SessionType.TEMPO, SessionType.INTERVAL, SessionType.LONG, SessionType.RACE_SIM)

    /**
     * Assigns planned sessions to available days with hard-effort spacing.
     * Returns pairs of (session, dayOfWeek).
     *
     * Rules:
     * 1. LONG/RACE_SIM goes to longRunBiasDay if provided
     * 2. Hard sessions (TEMPO, INTERVAL, LONG, RACE_SIM) must have >= 1 day gap
     * 3. Easy sessions fill remaining days
     */
    fun assign(
        sessions: List<PlannedSession>,
        availableDays: List<Int>,
        longRunBiasDay: Int? = null
    ): List<Pair<PlannedSession, Int>> {
        if (sessions.isEmpty() || availableDays.isEmpty()) return emptyList()

        val sortedDays = availableDays.sorted()
        val result = mutableListOf<Pair<PlannedSession, Int>>()
        val usedDays = mutableSetOf<Int>()

        // Separate hard and easy sessions
        val longSession = sessions.firstOrNull { it.type == SessionType.LONG || it.type == SessionType.RACE_SIM }
        val hardSessions = sessions.filter { it.type in HARD_TYPES && it != longSession }
        val easySessions = sessions.filter { it.type !in HARD_TYPES }

        // 1. Place long run on bias day (or last available day)
        if (longSession != null) {
            val longDay = if (longRunBiasDay != null && longRunBiasDay in sortedDays) longRunBiasDay
                          else sortedDays.last()
            result.add(longSession to longDay)
            usedDays.add(longDay)
        }

        // 2. Place hard sessions with spacing
        for (hard in hardSessions) {
            val bestDay = sortedDays
                .filter { it !in usedDays }
                .maxByOrNull { day ->
                    // Score: minimum distance to any already-placed hard session
                    val hardDays = result.filter { it.first.type in HARD_TYPES }.map { it.second }
                    if (hardDays.isEmpty()) 0
                    else hardDays.minOf { kotlin.math.abs(it - day) }
                }
            if (bestDay != null) {
                result.add(hard to bestDay)
                usedDays.add(bestDay)
            }
        }

        // 3. Easy sessions fill remaining days
        val remainingDays = sortedDays.filter { it !in usedDays }.toMutableList()
        for (easy in easySessions) {
            val day = remainingDays.removeFirstOrNull() ?: sortedDays.first()
            result.add(easy to day)
        }

        return result.sortedBy { it.second }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionDayAssignerTest" 2>&1 | tail -20`

- [ ] **Step 5: Wire into BootcampSessionCompleter.buildNextWeekEntities**

Replace the index-based day assignment in `buildNextWeekEntities` (lines 108-121):

```kotlin
private fun buildNextWeekEntities(
    enrollmentId: Long,
    nextEngine: PhaseEngine,
    preferredDays: List<DayPreference>,
    tierIndex: Int,
    tuningDirection: TuningDirection
): List<BootcampSessionEntity> {
    val plannedSessions = nextEngine.planCurrentWeek(
        tierIndex = tierIndex,
        tuningDirection = tuningDirection,
        currentPresetIndices = emptyMap()
    )
    if (plannedSessions.isEmpty()) return emptyList()

    val availableDays = preferredDays
        .filter { it.level == DaySelectionLevel.AVAILABLE || it.level == DaySelectionLevel.LONG_RUN_BIAS }
        .map { it.day }
    val longRunBiasDay = preferredDays
        .firstOrNull { it.level == DaySelectionLevel.LONG_RUN_BIAS }
        ?.day

    val assigned = SessionDayAssigner.assign(plannedSessions, availableDays, longRunBiasDay)

    return assigned.map { (session, day) ->
        BootcampRepository.buildSessionEntity(
            enrollmentId = enrollmentId,
            weekNumber = nextEngine.absoluteWeek,
            dayOfWeek = day,
            sessionType = session.type.name,
            targetMinutes = session.minutes,
            presetId = session.presetId
        )
    }
}
```

- [ ] **Step 6: Run full test suite**

Run: `./gradlew testDebugUnitTest 2>&1 | tail -30`

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(bootcamp): add hard-effort spacing in session day assignment

SessionDayAssigner places TEMPO/INTERVAL/LONG sessions with maximum
spacing between hard efforts. Respects LONG_RUN_BIAS day preference.
Replaces naive index-based day mapping."
```

---

## Task 5: Cap runsPerWeek and Recovery Week Improvements

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt`
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/PhaseEngine.kt`

- [ ] **Step 1: Add runsPerWeek cap in BootcampRepository.createEnrollment**

Find the `createEnrollment` method and add `.coerceIn(2, 6)` to `runsPerWeek`. Also add the cap in `BootcampSettingsViewModel.setRunsPerWeek`.

- [ ] **Step 2: Increase recovery week volume reduction from 20% to 35%**

In `PhaseEngine.planCurrentWeek`, change:
```kotlin
val effectiveMinutes = if (isRecoveryWeek) {
    (targetMinutes * 0.8f).toInt()
```
To:
```kotlin
val effectiveMinutes = if (isRecoveryWeek) {
    (targetMinutes * 0.65f).toInt()
```

- [ ] **Step 3: Run tests, fix any assertions that hardcoded 80%**

Run: `./gradlew testDebugUnitTest 2>&1 | tail -30`

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "fix(bootcamp): cap runsPerWeek at 2-6, increase recovery deload to 35%

Prevents 7-day weeks with no rest. Recovery weeks now reduce volume
by 35% (was 20%), aligning with standard periodization guidance."
```

---

## Task 6: Onboarding Carousel (5-Card Intro)

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampOnboardingCarousel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`

- [ ] **Step 1: Add `showCarousel` flag to BootcampUiState**

Add field: `val showCarousel: Boolean = false`

- [ ] **Step 2: In BootcampViewModel, show carousel on first enrollment**

In the init/refresh logic, when `hasActiveEnrollment == false`, set `showCarousel = true` instead of immediately showing onboarding setup.

- [ ] **Step 3: Create BootcampOnboardingCarousel composable**

```kotlin
package com.hrcoach.ui.bootcamp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaCtaGradient
import com.hrcoach.ui.theme.CardeaTheme

// 5-card carousel: Hook, Problem, Phases, Adaptation, CTA

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BootcampOnboardingCarousel(
    onStartSetup: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 5 })

    Box(modifier = modifier.fillMaxSize().background(CardeaTheme.colors.bgPrimary)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> CarouselCardHook()
                1 -> CarouselCardProblem()
                2 -> CarouselCardPhases()
                3 -> CarouselCardAdaptation()
                4 -> CarouselCardCta(onStartSetup)
            }
        }

        // Skip button (top-right, all pages except last)
        // ... (standard TextButton)

        // Dot indicator (bottom-center)
        // ... (standard Row of dots with gradient active dot)
    }
}
```

Each card composable should be a full-screen Column with:
- Cardea dark glass background
- Large headline text (white, bold)
- Body text (textSecondary)
- Minimal visual element (gradient accents, phase blocks, rings)
- Short enough to read in 3-4 seconds

Card content exactly as designed in the conversation:
1. "Your comeback starts here." — heart/ECG visual
2. "The hardest part of coming back? Not doing too much." — diverging paths visual
3. Phase explanation — BASE/BUILD/PEAK/TAPER blocks
4. "Every run teaches Cardea about you." — three rings
5. "Setup takes about 2 minutes." — CTA button

- [ ] **Step 4: Wire carousel into BootcampScreen**

In the existing `BootcampScreen`, when `state.showCarousel` is true, show `BootcampOnboardingCarousel` instead of the current onboarding steps. When the user taps "Start Setup" on card 5, transition to `showCarousel = false, showOnboarding = true`.

- [ ] **Step 5: Test manually in emulator**

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(bootcamp): add 5-card onboarding carousel before first enrollment

Swipeable intro explaining bootcamp mode: adaptive coaching, phases,
training load monitoring. Only shown on first enrollment. Skip button
available on all pages."
```

---

## Task 7: Redesign Setup Flow (Time-as-Ceiling, Preview)

Reorder and reframe the onboarding setup: goal → ability → runs/week → schedule → time availability → preview.

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSetupFlow.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`

- [ ] **Step 1: Update BootcampUiState onboarding fields**

Replace `onboardingMinutes` with `onboardingAvailableMinutes`. Add `onboardingPreviewSessions: List<PlannedSession>` for the live preview.

- [ ] **Step 2: Add preview computation to BootcampViewModel**

Add a method that computes a preview week from the current onboarding inputs:

```kotlin
private fun computePreviewWeek(
    goal: BootcampGoal,
    tierIndex: Int,
    runsPerWeek: Int,
    availableMinutes: Int
): List<PlannedSession> {
    // Use SessionSelector with BASE phase to show what week 1 looks like
    return SessionSelector.weekSessions(
        phase = TrainingPhase.BASE,
        goal = goal,
        runsPerWeek = runsPerWeek,
        targetMinutes = availableMinutes, // will be replaced by preset lookups later
        tierIndex = tierIndex
    )
}
```

Call this from each `setOnboarding*` method and store result in `onboardingPreviewSessions`.

- [ ] **Step 3: Create BootcampSetupFlow composable**

A vertically-scrollable single-page setup with sections in this order:

1. **Your Goal** — same GoalSelector (5 options)
2. **Current Ability** — finishing time slider for race goals, fitness level selector for cardio health. Reframed as "What could you run today?"
3. **How Many Days?** — [2] [3] [4] [5] [6] selector (was max 5, now 6)
4. **Which Days Work?** — same DayChipRow with long-run bias
5. **Time Per Run** — slider reframed as "How much time can you usually set aside?" with contextual guidance
6. **Your Profile** — HRmax input (optional)
7. **Program Preview** — GlassCard showing the first week's sessions with types, durations, zones
8. **Start** CTA button

- [ ] **Step 4: Wire BootcampSetupFlow into BootcampScreen**

Replace the multi-step onboarding (steps 0-4) with the new single-page flow.

- [ ] **Step 5: Update completeOnboarding to use new field names**

Change `state.onboardingMinutes` → `state.onboardingAvailableMinutes` in the `completeOnboarding()` method.

- [ ] **Step 6: Test manually in emulator**

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(bootcamp): redesign setup flow with time-as-ceiling and program preview

Reordered: goal → ability → days → schedule → time → preview.
Time slider reframed as availability ceiling. Live program preview
shows what week 1 will look like. Single scrollable page replaces
multi-step wizard."
```

---

## Task 8: Update Settings Screen for New Model

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsViewModel.kt`

- [ ] **Step 1: Reframe the duration slider in BootcampSettingsScreen**

Change label from "Easy run" to "Time per run". Remove `DurationScaler`-based computed properties (`editLongRunMinutes`, `editWeeklyTotal`, `longRunWarning`). Add contextual text showing the preset-prescribed duration vs the ceiling.

- [ ] **Step 2: Update BootcampSettingsUiState**

Remove `editLongRunMinutes`, `editWeeklyTotal`, `longRunWarning` computed properties that depend on `DurationScaler`. Replace with `presetEasyMinutes: Int` and `presetLongMinutes: Int` that come from the preset arrays.

- [ ] **Step 3: Update BootcampSettingsViewModel**

Replace `DurationScaler` usage with preset lookups. The `saveSettings` method should save `targetMinutesPerRun` (which now semantically means `availableMinutesPerRun`).

- [ ] **Step 4: Add runsPerWeek = 6 option**

In `BootcampSettingsScreen`, change `runOptions` from `listOf(2, 3, 4, 5)` to `listOf(2, 3, 4, 5, 6)` and `runLabels` from `listOf("2", "3", "4", "5+")` to `listOf("2", "3", "4", "5", "6")`.

- [ ] **Step 5: Run app in emulator, verify settings screen**

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "refactor(bootcamp): update settings screen for time-as-ceiling model

Duration slider reframed as availability. Removed DurationScaler
dependency from settings. Added 6 runs/week option."
```

---

## Task 9: Update Existing Tests for All Changes

**Files:**
- Modify: `app/src/test/java/com/hrcoach/domain/model/BootcampGoalTest.kt`
- Modify: `app/src/test/java/com/hrcoach/ui/bootcamp/BootcampViewModelTest.kt`

- [ ] **Step 1: Fix BootcampGoalTest for removed `tier` field**

Remove any assertions on `goal.tier`. Add assertions for `maxLongRunMinutes`.

- [ ] **Step 2: Fix BootcampViewModelTest for renamed onboarding fields**

Update references from `onboardingMinutes` to `onboardingAvailableMinutes` and related field name changes.

- [ ] **Step 3: Run full test suite**

Run: `./gradlew testDebugUnitTest 2>&1 | tail -30`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "test: update existing tests for bootcamp audit fixes

Fixed BootcampGoalTest (tier field removed), BootcampViewModelTest
(onboarding field renames), and other tests affected by the refactor."
```

---

## Task 10: Final Verification

- [ ] **Step 1: Run full test suite**

Run: `./gradlew testDebugUnitTest 2>&1 | tail -40`

- [ ] **Step 2: Build debug APK**

Run: `./gradlew assembleDebug 2>&1 | tail -10`

- [ ] **Step 3: Verify key safety scenarios manually**

In emulator:
1. Enroll as Marathon beginner (slow finishing time) → verify no intervals in week 1
2. Open settings, try to set tier to Hard → verify it saves (CTL validation is a future task)
3. Skip a session → verify week still advances
4. Complete all phases as 5K → verify graduation message appears

- [ ] **Step 4: Commit any final fixes**

```bash
git add -A && git commit -m "chore: final verification and cleanup for bootcamp audit fixes"
```
