# Training Logic & UX Improvements Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Address 10 identified training-logic, UX, and missing-feature issues across the Bootcamp module without breaking existing behavior.

**Architecture:** Domain changes (SessionPresetArray, SessionSelector, GapAdvisor) → ViewModel / UiState wiring → Compose UI. Each task is self-contained and committed separately. UI tasks (T6, T10, T11) have no unit-testable logic and are committed after visual review only.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, JUnit 4 (`@Test` + `assertThat`/`assertEquals`)

---

## Task 1 — Fix double escalation in `tempoTier2`

**Problem:** Jumping from index 2 (30 min Z3) directly to index 3 (35 min Z4) violates the "one variable at a time" rule — both duration and intensity increase simultaneously.

**Fix:** Insert a bridge step: raise duration in Z3 first, *then* cross to Z4 at a conservative duration.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/preset/SessionPresetArray.kt`
- Test: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionPresetArrayTest.kt`

**Step 1: Write failing test**

Add to `SessionPresetArrayTest`:

```kotlin
@Test
fun `tempoTier2 never changes both duration and zone in a single step`() {
    val array = SessionPresetArray.tempoTier2()
    for (i in 1 until array.presets.size) {
        val prev = array.presets[i - 1]
        val curr = array.presets[i]
        val zoneChanged = prev.presetId != curr.presetId
        val durationIncreased = curr.durationMinutes > prev.durationMinutes
        assertFalse(
            "Step $i changes both zone (${prev.presetId}->${curr.presetId}) and duration (${prev.durationMinutes}->${curr.durationMinutes})",
            zoneChanged && durationIncreased
        )
    }
}
```

**Step 2: Run test — expect FAIL**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionPresetArrayTest.tempoTier2 never changes both duration and zone in a single step"
```

**Step 3: Fix `tempoTier2` in `SessionPresetArray.kt`**

Replace the companion function:

```kotlin
fun tempoTier2() = SessionPresetArray(
    sessionTypeName = "tempo",
    presets = listOf(
        PresetConfig("aerobic_tempo", 20, "2x8 min Z3 with rest"),
        PresetConfig("aerobic_tempo", 25, "20-min continuous Z3"),
        PresetConfig("aerobic_tempo", 30, "25-min continuous Z3"),
        PresetConfig("aerobic_tempo", 35, "30-min continuous Z3"),        // NEW: duration up, zone holds
        PresetConfig("lactate_threshold", 35, "30-min continuous Z4 (T2 cap)")  // zone crosses, duration holds
    )
)
```

**Step 4: Run test — expect PASS**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionPresetArrayTest"
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/preset/SessionPresetArray.kt \
        app/src/test/java/com/hrcoach/domain/bootcamp/SessionPresetArrayTest.kt
git commit -m "fix(preset): insert bridge step in tempoTier2 to prevent double escalation"
```

---

## Task 2 — Extend illness snooze from 3 days to 10 days

**Problem:** `BootcampViewModel` snoozes the illness flag for only 3 days after the runner confirms illness. A genuine respiratory infection typically takes 7–14 days. The app will nag the runner while still ill.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Test: `app/src/test/java/com/hrcoach/domain/bootcamp/GapAdvisorTest.kt`

**Step 1: Write a contract documentation test**

Add to `GapAdvisorTest.kt`:

```kotlin
@Test
fun `illness confirm snooze is at least 10 days`() {
    // Documents the business rule. The constant lives in BootcampViewModel.
    val ILLNESS_SNOOZE_DAYS = 10
    assertTrue("Illness snooze must be >= 10 days", ILLNESS_SNOOZE_DAYS >= 10)
}
```

**Step 2: Run test — expect PASS**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.GapAdvisorTest"
```

**Step 3: Change the constant in BootcampViewModel**

Add a private companion object near the top of the class:

```kotlin
private companion object {
    const val ILLNESS_CONFIRM_SNOOZE_DAYS = 10L
    const val ILLNESS_DISMISS_SNOOZE_DAYS = 1L
}
```

Replace the hardcoded `3` in `snoozeIllness` (around line 617):

```kotlin
// Before:
val snoozedUntil = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3)
// After:
val snoozedUntil = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(ILLNESS_CONFIRM_SNOOZE_DAYS)
```

Replace the `1` in the dismiss path (around line 633):

```kotlin
// Before:
val snoozedUntil = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
// After:
val snoozedUntil = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(ILLNESS_DISMISS_SNOOZE_DAYS)
```

**Step 4: Run all bootcamp tests**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.*"
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
        app/src/test/java/com/hrcoach/domain/bootcamp/GapAdvisorTest.kt
git commit -m "fix(bootcamp): extend illness snooze from 3 days to 10 days"
```

---

## Task 3 — Surface recovery week countdown on the dashboard

**Problem:** `PhaseEngine.isRecoveryWeek` fires every 3rd week at the domain layer, but the runner never sees a forward-looking message — they can't plan around it.

**Fix:** Add `weeksUntilNextRecovery: Int` to `PhaseEngine`, wire it through `BootcampUiState`, and show a one-line hint on the dashboard.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/PhaseEngine.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`
- Test: `app/src/test/java/com/hrcoach/domain/bootcamp/PhaseEngineTest.kt`

**Step 1: Write failing tests**

Add to `PhaseEngineTest.kt`:

```kotlin
@Test
fun `weeksUntilNextRecovery returns 0 when current week is recovery`() {
    // weekInPhase=2: (2+1)%3 == 0 -> IS recovery
    val engine = PhaseEngine(
        goal = BootcampGoal.CARDIO_HEALTH,
        phaseIndex = 0, weekInPhase = 2,
        runsPerWeek = 3, targetMinutes = 30
    )
    assertTrue(engine.isRecoveryWeek)
    assertEquals(0, engine.weeksUntilNextRecovery)
}

@Test
fun `weeksUntilNextRecovery returns 1 when next week is recovery`() {
    val engine = PhaseEngine(
        goal = BootcampGoal.CARDIO_HEALTH,
        phaseIndex = 0, weekInPhase = 1,
        runsPerWeek = 3, targetMinutes = 30
    )
    assertFalse(engine.isRecoveryWeek)
    assertEquals(1, engine.weeksUntilNextRecovery)
}

@Test
fun `weeksUntilNextRecovery returns 2 when two weeks away`() {
    val engine = PhaseEngine(
        goal = BootcampGoal.CARDIO_HEALTH,
        phaseIndex = 0, weekInPhase = 0,
        runsPerWeek = 3, targetMinutes = 30
    )
    assertEquals(2, engine.weeksUntilNextRecovery)
}
```

**Step 2: Run tests — expect FAIL**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.PhaseEngineTest"
```

**Step 3: Add `weeksUntilNextRecovery` to PhaseEngine**

In `PhaseEngine.kt`, add after `isRecoveryWeek`:

```kotlin
val weeksUntilNextRecovery: Int
    get() {
        if (isRecoveryWeek) return 0
        var w = weekInPhase + 1
        var steps = 0
        while (steps < 10) {
            if (w > 0 && (w + 1) % 3 == 0) return steps + 1
            w++
            steps++
        }
        return steps
    }
```

**Step 4: Run tests — expect PASS**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.PhaseEngineTest"
```

**Step 5: Add to BootcampUiState**

After `isRecoveryWeek`:

```kotlin
val weeksUntilNextRecovery: Int? = null,
```

**Step 6: Populate in BootcampViewModel**

In the block that sets `isRecoveryWeek = engine.isRecoveryWeek`, also set:

```kotlin
weeksUntilNextRecovery = engine.weeksUntilNextRecovery,
```

**Step 7: Show in BootcampScreen**

Below the week title in the current-week card:

```kotlin
val recoveryLabel = when (uiState.weeksUntilNextRecovery) {
    0    -> "This is your recovery week — lighter load, big adaptation."
    1    -> "Recovery week coming up next week."
    2    -> "Recovery week in 2 weeks."
    else -> null
}
recoveryLabel?.let {
    Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = CardeaTextSecondary,
        modifier = Modifier.padding(top = 4.dp)
    )
}
```

**Step 8: Run all phase engine tests**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.*"
```

**Step 9: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/PhaseEngine.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt \
        app/src/test/java/com/hrcoach/domain/bootcamp/PhaseEngineTest.kt
git commit -m "feat(bootcamp): surface recovery week countdown on dashboard"
```

---

## Task 4 — Replace mathy CTL/TSB evidence copy with human coaching language

**Problem:** `tierPromptEvidence` surfaces as `"CTL 36.2 above 35 for 2 weeks, trend +1.2, TSB +5.4."` — alienating to 95% of runners who don't know these terms.

**Fix:** Create a `CoachingCopyGenerator` object that translates the signal numbers into natural sentences. The raw numbers still exist internally but are never shown to the user.

**Files:**
- Create: `app/src/main/java/com/hrcoach/domain/bootcamp/CoachingCopyGenerator.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Test: `app/src/test/java/com/hrcoach/domain/bootcamp/CoachingCopyGeneratorTest.kt`

**Step 1: Write failing tests**

Create `app/src/test/java/com/hrcoach/domain/bootcamp/CoachingCopyGeneratorTest.kt`:

```kotlin
package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.engine.TierPromptDirection
import org.junit.Test
import org.junit.Assert.*

class CoachingCopyGeneratorTest {

    @Test
    fun `push harder copy contains no raw metrics`() {
        val copy = CoachingCopyGenerator.tierPromptCopy(
            direction = TierPromptDirection.PUSH_HARDER,
            aboveOrBelowWeeks = 3,
            ctlTrend = 1.2f,
            tsb = 5.4f
        )
        assertFalse("Copy must not mention CTL", copy.contains("CTL"))
        assertFalse("Copy must not mention TSB", copy.contains("TSB"))
        assertTrue("Copy must not be blank", copy.isNotBlank())
    }

    @Test
    fun `ease back copy contains no raw metrics`() {
        val copy = CoachingCopyGenerator.tierPromptCopy(
            direction = TierPromptDirection.EASE_BACK,
            aboveOrBelowWeeks = 2,
            ctlTrend = -0.8f,
            tsb = -28f
        )
        assertFalse(copy.contains("CTL"))
        assertFalse(copy.contains("TSB"))
        assertTrue(copy.isNotBlank())
    }

    @Test
    fun `copy references the number of weeks`() {
        val copy = CoachingCopyGenerator.tierPromptCopy(
            direction = TierPromptDirection.PUSH_HARDER,
            aboveOrBelowWeeks = 3,
            ctlTrend = 1.0f,
            tsb = 6.0f
        )
        assertTrue("Copy should reference the week count", copy.contains("3") || copy.contains("three"))
    }
}
```

**Step 2: Run tests — expect FAIL**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.CoachingCopyGeneratorTest"
```

**Step 3: Create CoachingCopyGenerator**

```kotlin
package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.engine.TierPromptDirection

object CoachingCopyGenerator {

    fun tierPromptCopy(
        direction: TierPromptDirection,
        aboveOrBelowWeeks: Int,
        ctlTrend: Float,
        tsb: Float
    ): String = when (direction) {
        TierPromptDirection.PUSH_HARDER -> pushHarderCopy(aboveOrBelowWeeks, ctlTrend, tsb)
        TierPromptDirection.EASE_BACK   -> easeBackCopy(aboveOrBelowWeeks, tsb)
        TierPromptDirection.NONE        -> ""
    }

    private fun pushHarderCopy(weeks: Int, ctlTrend: Float, tsb: Float): String {
        val weekPhrase = if (weeks == 1) "the past week" else "the past $weeks weeks"
        val trendPhrase = if (ctlTrend > 0.5f) "and it's still growing" else "and it's holding steady"
        val recoveryPhrase = if (tsb > 10f) "Your body is well-recovered — " else "You have good recovery headroom — "
        return "${recoveryPhrase}your heart rate has been staying remarkably calm during efforts over $weekPhrase, $trendPhrase. You're ready for a bigger challenge."
    }

    private fun easeBackCopy(weeks: Int, tsb: Float): String {
        val weekPhrase = if (weeks == 1) "this past week" else "the past $weeks weeks"
        return if (tsb < -20f) {
            "Your body is working hard. Effort has been building for $weekPhrase and recovery is lagging — the right call now is to dial back before digging a deeper hole."
        } else {
            "Your recent sessions have been tougher than usual for $weekPhrase. A lighter phase now sets you up for a stronger next block."
        }
    }
}
```

**Step 4: Run tests — expect PASS**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.CoachingCopyGeneratorTest"
```

**Step 5: Replace CTL/TSB strings in BootcampViewModel**

Find the two `evidence = "CTL ..."` lines (around lines 1023 and 1042).

Replace PUSH_HARDER:
```kotlin
evidence = CoachingCopyGenerator.tierPromptCopy(
    direction = TierPromptDirection.PUSH_HARDER,
    aboveOrBelowWeeks = aboveWeeks,
    ctlTrend = ctlTrend,
    tsb = tsb
)
```

Replace EASE_BACK:
```kotlin
evidence = CoachingCopyGenerator.tierPromptCopy(
    direction = TierPromptDirection.EASE_BACK,
    aboveOrBelowWeeks = belowWeeks,
    ctlTrend = ctlTrend,
    tsb = tsb
)
```

**Step 6: Run full domain test suite**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.*"
```

**Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/CoachingCopyGenerator.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
        app/src/test/java/com/hrcoach/domain/bootcamp/CoachingCopyGeneratorTest.kt
git commit -m "feat(bootcamp): replace CTL/TSB evidence strings with human coaching copy"
```

---

## Task 5 — Fix session label mismatch (Tempo shown for Threshold sessions)

**Problem:** `tempoTier2` uses `sessionTypeName = "tempo"` for all presets, including the Z4 `lactate_threshold` cap. The runner sees "Tempo" on the card but the audio coach targets Z4. This is the highest-trust-risk issue on the list.

**Fix:** Add a `displayLabelForPreset(presetId)` function to `SessionType` and use it when building `SessionUiItem`.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionType.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Test: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt`

**Step 1: Write failing test**

Add to `SessionSelectorTest.kt`:

```kotlin
@Test
fun `tempoTier2 final preset maps to Threshold label not Tempo`() {
    val array = SessionPresetArray.tempoTier2()
    val finalPreset = array.presets.last()
    assertEquals("lactate_threshold", finalPreset.presetId)
    val label = SessionType.displayLabelForPreset(finalPreset.presetId)
    assertEquals("Threshold", label)
}
```

**Step 2: Run test — expect FAIL**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest"
```

**Step 3: Add `displayLabelForPreset` to SessionType**

```kotlin
companion object {
    fun displayLabelForPreset(presetId: String?): String? = when (presetId) {
        "lactate_threshold" -> "Threshold"
        "norwegian_4x4"    -> "Intervals"
        "aerobic_tempo"    -> "Tempo"
        "zone2_base"       -> null  // null = use default session type label
        else               -> null
    }
}
```

**Step 4: Run test — expect PASS**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest"
```

**Step 5: Use the label override when building SessionUiItem in BootcampViewModel**

Find the `SessionUiItem(...)` construction. Override `typeName` when a preset-specific label exists:

```kotlin
val labelOverride = SessionType.displayLabelForPreset(session.presetId)
SessionUiItem(
    typeName = labelOverride ?: session.type.name.lowercase().replaceFirstChar { it.uppercase() },
    // ... other fields unchanged
)
```

**Step 6: Run full bootcamp tests**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.*"
```

**Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/SessionType.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
        app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt
git commit -m "fix(bootcamp): derive session display label from presetId to prevent Tempo/Threshold mismatch"
```

---

## Task 6 — Improve HRR 120s post-run messaging

**Problem:** The 120-second cooldown walk for HRR measurement feels socially awkward with no context. The copy needs to actively sell the value of the wait.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt` (or `ActiveWorkoutScreen.kt` — check with grep first)

**Note:** Pure copy change — no unit test needed.

**Step 1: Find the HRR cooldown section**

```bash
grep -n "HRR\|120\|cooldown\|recovery.*score\|walk" app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt
```

**Step 2: Replace static copy**

Update header and body text:

- Header: `"Calculating your 30-day recovery index…"`
- Body: `"Walk slowly for 120 seconds. This single measurement tells us more about your adaptation than any individual workout."`
- Progress label (if present): `"${secondsRemaining}s remaining"`

**Step 3: Build and visually verify**

```
./gradlew assembleDebug
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "copy(postrun): improve HRR 120s cooldown messaging to sell recovery score value"
```

---

## Task 7 — Add STRIDES session type and inject into 5K/10K plans

**Problem:** Strides (short 15–20s fast bursts tacked onto an easy run) are essential neuromuscular work for 5K/10K plans. `SessionType` has no STRIDES entry and they are absent from all preset arrays.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionType.kt`
- Modify: `app/src/main/java/com/hrcoach/domain/preset/SessionPresetArray.kt`
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt`
- Test: `app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt`

**Step 1: Write failing tests**

Add to `SessionSelectorTest.kt`:

```kotlin
@Test
fun `5K 10K goal in BUILD phase with 4 runs per week includes a strides session`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.BUILD,
        goal = BootcampGoal.RACE_5K_10K,
        runsPerWeek = 4,
        targetMinutes = 35,
        tierIndex = 1
    )
    assertTrue(
        "5K/10K BUILD weeks with 4+ runs should include strides",
        sessions.any { it.type == SessionType.STRIDES }
    )
}

@Test
fun `CARDIO_HEALTH goal never includes strides`() {
    val sessions = SessionSelector.weekSessions(
        phase = TrainingPhase.BUILD,
        goal = BootcampGoal.CARDIO_HEALTH,
        runsPerWeek = 4,
        targetMinutes = 35
    )
    assertFalse(sessions.any { it.type == SessionType.STRIDES })
}
```

**Step 2: Run tests — expect FAIL**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest"
```

**Step 3: Add STRIDES to SessionType**

```kotlin
enum class SessionType(val presetCategory: PresetCategory?) {
    EASY(PresetCategory.BASE_AEROBIC),
    LONG(PresetCategory.BASE_AEROBIC),
    TEMPO(PresetCategory.THRESHOLD),
    INTERVAL(PresetCategory.INTERVAL),
    STRIDES(null),          // short neuromuscular bursts; no HR zone; appended to easy run block
    RACE_SIM(PresetCategory.RACE_PREP),
    DISCOVERY(null),
    CHECK_IN(null)
}
```

Also add `"strides_4x"`, `"strides_6x"`, `"strides_8x"`, `"strides_10x"` to `displayLabelForPreset` (from Task 5):

```kotlin
"strides_4x", "strides_6x", "strides_8x", "strides_10x" -> "Strides"
```

**Step 4: Add strides preset arrays to SessionPresetArray**

```kotlin
fun stridesTier2() = SessionPresetArray(
    sessionTypeName = "strides",
    presets = listOf(
        PresetConfig("strides_4x", 32, "Easy 25 min + 4x20s strides"),
        PresetConfig("strides_6x", 35, "Easy 25 min + 6x20s strides"),
        PresetConfig("strides_8x", 40, "Easy 30 min + 8x20s strides (T2 cap)")
    )
)

fun stridesTier3() = SessionPresetArray(
    sessionTypeName = "strides",
    presets = listOf(
        PresetConfig("strides_6x", 35, "Easy 25 min + 6x20s strides"),
        PresetConfig("strides_8x", 40, "Easy 30 min + 8x20s strides"),
        PresetConfig("strides_10x", 45, "Easy 35 min + 10x20s strides (T3 cap)")
    )
)
```

**Step 5: Wire into SessionSelector**

In `periodizedWeek`, in the `BUILD` branch, add strides for qualifying goals:

```kotlin
TrainingPhase.BUILD -> {
    sessions.add(PlannedSession(SessionType.TEMPO, minutes, "aerobic_tempo"))
    if (goal.tier >= 2 && runsPerWeek >= 4) {
        sessions.add(PlannedSession(SessionType.STRIDES, minutes, "strides_4x"))
    }
}
```

In `presetArrayFor`, add STRIDES to tier 1 and tier 2+ branches:

```kotlin
1 -> when (type) {
    SessionType.EASY    -> SessionPresetArray.easyRunTier2()
    SessionType.TEMPO   -> SessionPresetArray.tempoTier2()
    SessionType.LONG    -> SessionPresetArray.longRunTier2()
    SessionType.STRIDES -> SessionPresetArray.stridesTier2()
    else -> null
}
else -> when (type) {
    SessionType.EASY     -> SessionPresetArray.easyRunTier3()
    SessionType.INTERVAL -> SessionPresetArray.vo2maxTier3()
    SessionType.TEMPO    -> SessionPresetArray.thresholdTier3()
    SessionType.STRIDES  -> SessionPresetArray.stridesTier3()
    else -> null
}
```

**Step 6: Run tests — expect PASS**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.SessionSelectorTest"
```

**Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/SessionType.kt \
        app/src/main/java/com/hrcoach/domain/preset/SessionPresetArray.kt \
        app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt \
        app/src/test/java/com/hrcoach/domain/bootcamp/SessionSelectorTest.kt
git commit -m "feat(preset): add STRIDES session type and inject into BUILD phase for 5K/10K plans"
```

---

## Task 8 — Add "Rest today instead" swap button

**Problem:** `skipMissedSession` frames a schedule change as a failure. Runners need a neutral, positive way to swap a day for rest without feeling penalized.

**Fix:** Add `swapTodayForRest()` in the ViewModel that reschedules the next session by +1 day and marks it as `SWAPPED` (not `SKIPPED`). Gap logic ignores SWAPPED status.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/db/BootcampSessionEntity.kt` (add SWAPPED status)
- Modify: `app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`
- Test: `app/src/test/java/com/hrcoach/data/repository/BootcampRepositoryTest.kt`

**Step 1: Write failing test**

Add to `BootcampRepositoryTest.kt`:

```kotlin
@Test
fun `swapSessionToNextDay increments scheduled date by one day and sets status to SWAPPED`() {
    // Arrange: insert an enrollment and a session for today's epoch ms
    // Act:
    bootcampRepository.swapSessionToNextDay(testSessionId)
    // Assert:
    val session = bootcampDao.getSessionByIdBlocking(testSessionId)
    assertEquals("SWAPPED", session?.status)
    val expectedMs = originalDateMs + TimeUnit.DAYS.toMillis(1)
    assertEquals(expectedMs, session?.scheduledDate)
}
```

**Step 2: Run test — expect FAIL**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.data.repository.BootcampRepositoryTest"
```

**Step 3: Add STATUS_SWAPPED constant to BootcampSessionEntity**

```kotlin
companion object {
    // existing constants...
    const val STATUS_SWAPPED = "SWAPPED"
}
```

**Step 4: Add `swapSessionToNextDay` to BootcampRepository**

```kotlin
suspend fun swapSessionToNextDay(sessionId: Long) {
    val session = bootcampDao.getSessionById(sessionId) ?: return
    val nextDay = session.scheduledDate + TimeUnit.DAYS.toMillis(1)
    bootcampDao.updateSession(session.copy(scheduledDate = nextDay, status = STATUS_SWAPPED))
}
```

**Step 5: Add UiState field**

```kotlin
val swapRestMessage: String? = null,
```

**Step 6: Add ViewModel method**

```kotlin
fun swapTodayForRest() {
    val sessionId = _uiState.value.nextSession?.sessionId ?: return
    viewModelScope.launch {
        bootcampRepository.swapSessionToNextDay(sessionId)
        _uiState.update { it.copy(swapRestMessage = "Resting today. Session moved to tomorrow.") }
        refreshDashboard()
    }
}
```

**Step 7: Add button to BootcampScreen**

In the next-session card, below the primary launch button:

```kotlin
TextButton(onClick = { viewModel.swapTodayForRest() }) {
    Text(
        text = "Rest today instead",
        style = MaterialTheme.typography.bodySmall,
        color = CardeaTextSecondary
    )
}
uiState.swapRestMessage?.let {
    Text(it, style = MaterialTheme.typography.bodySmall, color = CardeaTextSecondary)
}
```

**Step 8: Run tests**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.data.repository.BootcampRepositoryTest"
```

**Step 9: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/BootcampSessionEntity.kt \
        app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt \
        app/src/test/java/com/hrcoach/data/repository/BootcampRepositoryTest.kt
git commit -m "feat(bootcamp): add rest-day swap to replace negative skip framing"
```

---

## Task 9 — Goal graduation flow

**Problem:** Finishing a 5K plan and wanting to start a Half Marathon requires ending the current enrollment, which feels like failure and loses session context. There is no "level up" path.

**Fix:** Add `graduateEnrollment(enrollmentId, newGoal)` to the repository. It marks the old enrollment `GRADUATED`, keeps all sessions, then creates a new enrollment at phase 0 copying training parameters from the old one.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/data/db/BootcampEnrollmentEntity.kt`
- Modify: `app/src/main/java/com/hrcoach/data/db/BootcampDao.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`
- Test: `app/src/test/java/com/hrcoach/data/repository/BootcampRepositoryTest.kt`

**Step 1: Write failing test**

Add to `BootcampRepositoryTest.kt`:

```kotlin
@Test
fun `graduateEnrollment marks old enrollment GRADUATED and creates new active enrollment`() {
    // Arrange: active enrollment for RACE_5K_10K
    // Act:
    bootcampRepository.graduateEnrollment(oldEnrollmentId, BootcampGoal.HALF_MARATHON)
    // Assert:
    val old = bootcampDao.getEnrollmentByIdBlocking(oldEnrollmentId)
    assertEquals("GRADUATED", old?.status)
    val newActive = bootcampRepository.getActiveEnrollmentOnceBlocking()
    assertNotNull(newActive)
    assertEquals("HALF_MARATHON", newActive?.goalType)
    assertEquals(0, newActive?.currentPhaseIndex)
    // Old sessions untouched:
    val sessions = bootcampDao.getSessionsForEnrollmentBlocking(oldEnrollmentId)
    assertTrue(sessions.isNotEmpty())
}
```

**Step 2: Run test — expect FAIL**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.data.repository.BootcampRepositoryTest"
```

**Step 3: Add STATUS_GRADUATED to BootcampEnrollmentEntity**

```kotlin
companion object {
    const val STATUS_ACTIVE     = "ACTIVE"
    const val STATUS_PAUSED     = "PAUSED"
    const val STATUS_COMPLETED  = "COMPLETED"
    const val STATUS_GRADUATED  = "GRADUATED"   // NEW
}
```

**Step 4: Verify BootcampDao.getActiveEnrollment does NOT include GRADUATED**

Check the query in `BootcampDao.kt`:

```kotlin
@Query("SELECT * FROM bootcamp_enrollments WHERE status IN ('ACTIVE', 'PAUSED') LIMIT 1")
```

GRADUATED must not appear in this list — confirm it doesn't.

**Step 5: Add `graduateEnrollment` to BootcampRepository**

```kotlin
suspend fun graduateEnrollment(enrollmentId: Long, newGoal: BootcampGoal) {
    val old = bootcampDao.getEnrollmentById(enrollmentId) ?: return
    // Mark old as graduated
    bootcampDao.updateEnrollment(old.copy(status = STATUS_GRADUATED))
    // Create new enrollment at phase 0, inheriting training parameters
    val newEnrollment = old.copy(
        id = 0,   // Room will auto-generate
        goalType = newGoal.name,
        status = STATUS_ACTIVE,
        currentPhaseIndex = 0,
        currentWeekInPhase = 0,
        startDate = System.currentTimeMillis(),
        completedAt = null,
        tierPromptDismissCount = 0,
        tierPromptSnoozedUntilMs = 0L,
        illnessPromptSnoozedUntilMs = 0L
    )
    bootcampDao.insertEnrollment(newEnrollment)
}
```

**Step 6: Add UiState fields**

```kotlin
val showGraduationPrompt: Boolean = false,
val graduationGoalOptions: List<BootcampGoal> = emptyList(),
```

**Step 7: Add ViewModel methods**

```kotlin
fun showGraduationOptions() {
    val currentTier = _uiState.value.goal?.tier ?: return
    val options = BootcampGoal.entries.filter { it.tier > currentTier }
    _uiState.update { it.copy(showGraduationPrompt = true, graduationGoalOptions = options) }
}

fun confirmGraduation(newGoal: BootcampGoal) {
    viewModelScope.launch {
        val enrollmentId = bootcampRepository.getActiveEnrollmentOnce()?.id ?: return@launch
        bootcampRepository.graduateEnrollment(enrollmentId, newGoal)
        _uiState.update { it.copy(showGraduationPrompt = false) }
        refreshDashboard()
    }
}

fun dismissGraduationPrompt() {
    _uiState.update { it.copy(showGraduationPrompt = false) }
}
```

**Step 8: Add graduation CTA to BootcampScreen**

On the completed-program card (where `hasCompletedProgram` is true), add a "Level up" button:

```kotlin
if (uiState.hasCompletedProgram && uiState.graduationGoalOptions.isNotEmpty()) {
    CardeaButton(
        text = "Level up your goal",
        onClick = { viewModel.showGraduationOptions() }
    )
}

if (uiState.showGraduationPrompt) {
    // Simple dialog or bottom sheet listing graduationGoalOptions
    AlertDialog(
        onDismissRequest = { viewModel.dismissGraduationPrompt() },
        title = { Text("Choose your next goal") },
        text = {
            Column {
                uiState.graduationGoalOptions.forEach { goal ->
                    TextButton(onClick = { viewModel.confirmGraduation(goal) }) {
                        Text(goal.name)
                    }
                }
            }
        },
        confirmButton = {}
    )
}
```

**Step 9: Run tests**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.data.repository.BootcampRepositoryTest"
```

**Step 10: Commit**

```bash
git add app/src/main/java/com/hrcoach/data/db/BootcampEnrollmentEntity.kt \
        app/src/main/java/com/hrcoach/data/db/BootcampDao.kt \
        app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt \
        app/src/test/java/com/hrcoach/data/repository/BootcampRepositoryTest.kt
git commit -m "feat(bootcamp): add goal graduation flow to level up without losing history"
```

---

## Task 10 — Surface preferred days directly on the dashboard

**Problem:** Preferred training days are buried in the settings overflow menu. During active program use, they function as the primary steering wheels and should be immediately visible.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

**Note:** UI-only task — no unit tests needed.

**Step 1: Add `activePreferredDays` to BootcampUiState**

```kotlin
val activePreferredDays: List<DayPreference> = emptyList(),
```

**Step 2: Populate in BootcampViewModel**

In the active-enrollment loading block, alongside setting `currentWeekSessions`, set:

```kotlin
activePreferredDays = BootcampEnrollmentEntity.parseDayPreferences(enrollment.preferredDays),
```

**Step 3: Add compact read-only day strip to the dashboard**

Add below the week title in the current-week card. Tapping opens `BootcampSettingsScreen`.

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { onNavigateToSettings() }
        .padding(vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Text(
        "Your run days:",
        style = MaterialTheme.typography.labelSmall,
        color = CardeaTextTertiary
    )
    uiState.activePreferredDays.forEach { pref ->
        val letter = listOf("M","T","W","T","F","S","S")[pref.day - 1]
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(CardeaGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(letter, style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
    Icon(
        Icons.Default.Edit,
        contentDescription = "Edit days",
        tint = CardeaTextTertiary,
        modifier = Modifier.size(12.dp)
    )
}
```

**Step 4: Build and visually verify**

```
./gradlew assembleDebug
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt
git commit -m "feat(bootcamp): surface preferred training days on dashboard"
```

---

## Task 11 — Add 2-week forward-looking calendar

**Problem:** `BootcampUiState` only tracks past weeks. Runners need to see upcoming sessions to plan their lives — "Is my long run on my sister's wedding day?"

**Fix:** Add `lookaheadWeeks(count)` to `PhaseEngine` to project future week states, populate `upcomingWeeks` in `BootcampUiState`, and show a "Coming Up" section on the dashboard.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/PhaseEngine.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`
- Test: `app/src/test/java/com/hrcoach/domain/bootcamp/PhaseEngineTest.kt`

**Step 1: Write failing test**

Add to `PhaseEngineTest.kt`:

```kotlin
@Test
fun `lookaheadWeeks returns requested number of future week states`() {
    val engine = PhaseEngine(
        goal = BootcampGoal.CARDIO_HEALTH,
        phaseIndex = 0, weekInPhase = 0,
        runsPerWeek = 3, targetMinutes = 30
    )
    val lookahead = engine.lookaheadWeeks(2)
    assertEquals(2, lookahead.size)
}

@Test
fun `lookaheadWeeks absolute weeks increase monotonically`() {
    val engine = PhaseEngine(
        goal = BootcampGoal.CARDIO_HEALTH,
        phaseIndex = 0, weekInPhase = 0,
        runsPerWeek = 3, targetMinutes = 30
    )
    val lookahead = engine.lookaheadWeeks(3)
    for (i in 1 until lookahead.size) {
        assertTrue(lookahead[i].absoluteWeek > lookahead[i - 1].absoluteWeek)
    }
}
```

**Step 2: Run tests — expect FAIL**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.PhaseEngineTest"
```

**Step 3: Add `WeekLookahead` data class and `lookaheadWeeks` to PhaseEngine**

Add just above the `Companion` object in `PhaseEngine.kt`:

```kotlin
data class WeekLookahead(
    val absoluteWeek: Int,
    val phase: TrainingPhase,
    val isRecoveryWeek: Boolean,
    val weekLabel: String
)

fun lookaheadWeeks(count: Int): List<WeekLookahead> {
    val result = mutableListOf<WeekLookahead>()
    var engine = this
    repeat(count) {
        val nextWeekInPhase = engine.weekInPhase + 1
        engine = if (nextWeekInPhase >= engine.currentPhaseDurationWeeks() && !engine.shouldCompleteEnrollmentOnAdvance()) {
            PhaseEngine(engine.goal, (engine.phaseIndex + 1).coerceAtMost(engine.goal.phaseArc.lastIndex), 0, engine.runsPerWeek, engine.targetMinutes)
        } else {
            PhaseEngine(engine.goal, engine.phaseIndex, nextWeekInPhase, engine.runsPerWeek, engine.targetMinutes)
        }
        result.add(
            WeekLookahead(
                absoluteWeek = engine.absoluteWeek,
                phase = engine.currentPhase,
                isRecoveryWeek = engine.isRecoveryWeek,
                weekLabel = if (engine.isRecoveryWeek) "Recovery Week" else "Week ${engine.absoluteWeek}"
            )
        )
    }
    return result
}
```

**Step 4: Run tests — expect PASS**

```
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.PhaseEngineTest"
```

**Step 5: Add UiState fields**

```kotlin
val upcomingWeeks: List<UpcomingWeekItem> = emptyList(),
```

Add data class in the same file:

```kotlin
data class UpcomingWeekItem(
    val weekLabel: String,
    val phaseLabel: String,
    val isRecoveryWeek: Boolean,
    val sessions: List<SessionUiItem>
)
```

**Step 6: Populate in BootcampViewModel**

After computing `currentWeekSessions`, project the next 2 weeks:

```kotlin
val upcomingWeeks = engine.lookaheadWeeks(2).map { lookahead ->
    val plannedSessions = SessionSelector.weekSessions(
        phase = lookahead.phase,
        goal = goal,
        runsPerWeek = enrollment.runsPerWeek,
        targetMinutes = enrollment.targetMinutesPerRun,
        tierIndex = enrollment.tierIndex
    )
    UpcomingWeekItem(
        weekLabel = lookahead.weekLabel,
        phaseLabel = lookahead.phase.name.lowercase().replaceFirstChar { it.uppercase() },
        isRecoveryWeek = lookahead.isRecoveryWeek,
        sessions = plannedSessions.map { session ->
            SessionUiItem(
                dayLabel = "",   // no scheduled day yet — just show type
                typeName = SessionType.displayLabelForPreset(session.presetId)
                    ?: session.type.name.lowercase().replaceFirstChar { it.uppercase() },
                minutes = session.minutes,
                isCompleted = false,
                isToday = false
            )
        }
    )
}
_uiState.update { it.copy(upcomingWeeks = upcomingWeeks) }
```

**Step 7: Add "Coming Up" section to BootcampScreen**

Below the current-week card, add a collapsible upcoming section:

```kotlin
if (uiState.upcomingWeeks.isNotEmpty()) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Coming Up",
            style = MaterialTheme.typography.titleSmall,
            color = CardeaTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        uiState.upcomingWeeks.forEach { week ->
            Text(
                text = "${week.weekLabel} · ${week.phaseLabel}${if (week.isRecoveryWeek) " · Recovery" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = if (week.isRecoveryWeek) Color(0xFF00D1FF) else CardeaTextPrimary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            week.sessions.forEach { session ->
                Text(
                    "  ${session.typeName} · ${session.minutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTextSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
```

**Step 8: Run full test suite**

```
./gradlew testDebugUnitTest
```

**Step 9: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/PhaseEngine.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
        app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt \
        app/src/test/java/com/hrcoach/domain/bootcamp/PhaseEngineTest.kt
git commit -m "feat(bootcamp): add 2-week forward-looking calendar to dashboard"
```

---

## Summary

| # | Issue | Severity | Primary Files | Domain Test |
|---|---|---|---|---|
| T1 | Double escalation in tempoTier2 | High | SessionPresetArray.kt | Yes |
| T2 | Illness snooze 3 → 10 days | High | BootcampViewModel.kt | Yes (contract) |
| T3 | Recovery week countdown | Medium | PhaseEngine.kt, BootcampScreen | Yes |
| T4 | Mathy CTL/TSB copy → human language | High | CoachingCopyGenerator.kt | Yes |
| T5 | Session label mismatch (Tempo/Threshold) | Critical | SessionType.kt, BootcampViewModel | Yes |
| T6 | HRR 120s copy polish | Low | PostRunSummaryScreen.kt | No (copy) |
| T7 | Add STRIDES session type | Medium | SessionType.kt, SessionSelector.kt | Yes |
| T8 | Rest day swap | Medium | BootcampRepository.kt, BootcampViewModel | Yes |
| T9 | Goal graduation flow | High | BootcampRepository.kt, BootcampScreen | Yes |
| T10 | Preferred days on dashboard | Low | BootcampScreen.kt | No (UI) |
| T11 | 2-week lookahead calendar | Medium | PhaseEngine.kt, BootcampScreen | Yes |
