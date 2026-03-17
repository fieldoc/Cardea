# Session Duration Scaling Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat `1.3x` long-run multiplier with a Daniels-derived percentage-of-weekly-volume model, reframe the UI slider as "easy run length" with computed long-run preview, and add minimum-time warnings when the user's chosen duration is too short for their goal distance.

**Architecture:** A new pure-function `DurationScaler` object computes per-session-type minutes from `(runsPerWeek, easyMinutes)` using coaching-science ratios. The UI slider is relabeled and gains a dynamic long-run preview line. `SessionSelector` delegates duration decisions to `DurationScaler` instead of computing them inline. `BootcampGoal` gains a `minLongRunMinutes` field for goal-distance feasibility warnings.

**Tech Stack:** Kotlin, Jetpack Compose, Room, existing Cardea design tokens

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `domain/bootcamp/DurationScaler.kt` | **Create** | Pure functions: compute LONG/EASY/TEMPO/INTERVAL minutes from weekly context |
| `domain/bootcamp/SessionSelector.kt` | **Modify** | Replace inline `longMinutes` formula with `DurationScaler` call |
| `domain/model/BootcampGoal.kt` | **Modify** | Add `minLongRunMinutes` field per goal for feasibility warnings |
| `ui/bootcamp/BootcampScreen.kt` | **Modify** | Relabel slider, add long-run preview line, update warning logic |
| `ui/bootcamp/BootcampUiState.kt` | **Modify** | Add `computedLongRunMinutes` and `longRunWarning` fields |
| `ui/bootcamp/BootcampViewModel.kt` | **Modify** | Compute long-run preview + feasibility warning on slider change |
| `ui/bootcamp/BootcampSettingsScreen.kt` | **Modify** | Add long-run preview line below Duration slider |
| `ui/bootcamp/BootcampSettingsUiState.kt` | **Modify** | Add `computedLongRunMinutes` and `longRunWarning` computed properties |
| `test/domain/bootcamp/DurationScalerTest.kt` | **Create** | Unit tests for the scaling logic |

---

## Task 1: Create DurationScaler with TDD

**Files:**
- Create: `app/src/test/java/com/hrcoach/domain/bootcamp/DurationScalerTest.kt`
- Create: `app/src/main/java/com/hrcoach/domain/bootcamp/DurationScaler.kt`

### Design

`DurationScaler` is a stateless object with one primary function:

```kotlin
object DurationScaler {
    data class WeekDurations(
        val easyMinutes: Int,
        val longMinutes: Int,
        val tempoMinutes: Int,
        val intervalMinutes: Int
    )

    fun compute(runsPerWeek: Int, easyMinutes: Int): WeekDurations
}
```

**Daniels-derived rules:**
- `weeklyTotal = runsPerWeek * easyMinutes`
- `longMinutes = (weeklyTotal * 0.25).coerceIn(easyMinutes, 150)`
  - Never shorter than the easy run (would be nonsensical)
  - Never longer than 150 min (Daniels' absolute cap for L runs)
- `tempoMinutes = (weeklyTotal * 0.10).coerceIn(15, 40)`
  - Daniels: single T workout max 10% weekly distance; 20 min steady or 30 min cruise
  - Floor of 15 prevents uselessly short tempo sessions
- `intervalMinutes = (weeklyTotal * 0.08).coerceIn(12, 35)`
  - Daniels: single I workout max 8% weekly distance
  - Floor of 12 prevents intervals too short to warm up
- `adjustedEasyMinutes` = redistribute: `(weeklyTotal - longMinutes) / (runsPerWeek - 1)` when there's a long run, else `easyMinutes`
  - Only applies when `runsPerWeek >= 3` (need at least 3 runs to have a distinct long run)
  - When `runsPerWeek < 3`, `longMinutes = easyMinutes` (no distinct long run)

The adjusted easy minutes ensures the weekly total stays constant — the long run "borrows" time from the easy runs.

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/hrcoach/domain/bootcamp/DurationScalerTest.kt`:

```kotlin
package com.hrcoach.domain.bootcamp

import org.junit.Assert.*
import org.junit.Test

class DurationScalerTest {

    @Test fun beginner_3x30_long_is_25pct_of_weekly() {
        // weekly = 90, 25% = 22.5 -> 22, but coerced to min easyMinutes (30)
        val d = DurationScaler.compute(runsPerWeek = 3, easyMinutes = 30)
        assertEquals(30, d.longMinutes) // can't be less than easy
    }

    @Test fun intermediate_4x45_long_scales_properly() {
        // weekly = 180, 25% = 45
        val d = DurationScaler.compute(runsPerWeek = 4, easyMinutes = 45)
        assertEquals(45, d.longMinutes)
    }

    @Test fun experienced_5x60_long_is_75min() {
        // weekly = 300, 25% = 75
        val d = DurationScaler.compute(runsPerWeek = 5, easyMinutes = 60)
        assertEquals(75, d.longMinutes)
    }

    @Test fun high_volume_long_capped_at_150() {
        // weekly = 5*90 = 450, 25% = 112 — below cap
        // But 3*90 = 270, 25% = 67
        // Try 7*90 = 630, 25% = 157 -> capped at 150
        val d = DurationScaler.compute(runsPerWeek = 7, easyMinutes = 90)
        assertEquals(150, d.longMinutes)
    }

    @Test fun two_runs_per_week_no_distinct_long() {
        val d = DurationScaler.compute(runsPerWeek = 2, easyMinutes = 30)
        assertEquals(30, d.longMinutes) // same as easy
    }

    @Test fun easy_minutes_redistributed_when_long_exceeds_easy() {
        // 5x60: weekly=300, long=75, remaining=225, 4 easy runs = 56 each
        val d = DurationScaler.compute(runsPerWeek = 5, easyMinutes = 60)
        assertEquals(56, d.easyMinutes)
        // Verify total is preserved: 56*4 + 75 = 224 + 75 = 299 (rounding)
        // Close enough — integer rounding
    }

    @Test fun tempo_is_10pct_clamped() {
        // 5x60: weekly=300, 10%=30
        val d = DurationScaler.compute(runsPerWeek = 5, easyMinutes = 60)
        assertEquals(30, d.tempoMinutes)
    }

    @Test fun tempo_floor_at_15() {
        // 3x30: weekly=90, 10%=9 -> clamped to 15
        val d = DurationScaler.compute(runsPerWeek = 3, easyMinutes = 30)
        assertEquals(15, d.tempoMinutes)
    }

    @Test fun interval_is_8pct_clamped() {
        // 5x60: weekly=300, 8%=24
        val d = DurationScaler.compute(runsPerWeek = 5, easyMinutes = 60)
        assertEquals(24, d.intervalMinutes)
    }

    @Test fun interval_floor_at_12() {
        // 3x30: weekly=90, 8%=7 -> clamped to 12
        val d = DurationScaler.compute(runsPerWeek = 3, easyMinutes = 30)
        assertEquals(12, d.intervalMinutes)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.DurationScalerTest"`
Expected: FAIL — `DurationScaler` does not exist yet

- [ ] **Step 3: Implement DurationScaler**

Create `app/src/main/java/com/hrcoach/domain/bootcamp/DurationScaler.kt`:

```kotlin
package com.hrcoach.domain.bootcamp

/**
 * Computes per-session-type durations from weekly training context.
 *
 * Based on Jack Daniels' Running Formula rules:
 * - Long run: 25% of weekly total, clamped [easyMinutes, 150]
 * - Tempo: 10% of weekly total, clamped [15, 40]
 * - Interval: 8% of weekly total, clamped [12, 35]
 * - Easy runs absorb the remainder to keep weekly total constant.
 */
object DurationScaler {

    data class WeekDurations(
        val easyMinutes: Int,
        val longMinutes: Int,
        val tempoMinutes: Int,
        val intervalMinutes: Int
    )

    /**
     * @param runsPerWeek number of runs in the week (2–7)
     * @param easyMinutes the user's chosen "easy run" duration — the anchor
     */
    fun compute(runsPerWeek: Int, easyMinutes: Int): WeekDurations {
        val weeklyTotal = runsPerWeek * easyMinutes

        // Long run: 25% of weekly total, at least as long as easy, max 150
        val hasLong = runsPerWeek >= 3
        val rawLong = (weeklyTotal * 0.25f).toInt()
        val longMinutes = if (hasLong) rawLong.coerceIn(easyMinutes, 150) else easyMinutes

        // Easy runs absorb the difference to keep weekly total ~constant
        val adjustedEasy = if (hasLong && runsPerWeek > 1) {
            (weeklyTotal - longMinutes) / (runsPerWeek - 1)
        } else {
            easyMinutes
        }

        // Quality session caps (Daniels)
        val tempoMinutes = (weeklyTotal * 0.10f).toInt().coerceIn(15, 40)
        val intervalMinutes = (weeklyTotal * 0.08f).toInt().coerceIn(12, 35)

        return WeekDurations(
            easyMinutes = adjustedEasy,
            longMinutes = longMinutes,
            tempoMinutes = tempoMinutes,
            intervalMinutes = intervalMinutes
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.DurationScalerTest"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/DurationScaler.kt \
       app/src/test/java/com/hrcoach/domain/bootcamp/DurationScalerTest.kt
git commit -m "feat(bootcamp): add DurationScaler with Daniels-derived session scaling"
```

---

## Task 2: Wire DurationScaler into SessionSelector

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt`

### What changes

Replace the inline `longMinutes` calculation with a `DurationScaler.compute()` call. The `targetMinutes` parameter to `weekSessions` becomes the "easy minutes" anchor. `DurationScaler` returns the appropriate long, tempo, and interval durations.

- [ ] **Step 1: Modify `weekSessions` to use DurationScaler**

In `SessionSelector.kt`, replace:
```kotlin
val longMinutes = (effectiveMinutes * 1.3f).toInt().coerceAtMost(effectiveMinutes + 20)
```

With:
```kotlin
val durations = DurationScaler.compute(runsPerWeek, effectiveMinutes)
val longMinutes = durations.longMinutes
```

Also update `baseAerobicWeek` and `periodizedWeek` to use `durations.easyMinutes` for easy runs instead of raw `minutes`:

In `weekSessions`, change the calls:
```kotlin
return when {
    goal.tier <= 1 -> baseAerobicWeek(phase, runsPerWeek, durations.easyMinutes, longMinutes)
    else -> periodizedWeek(phase, goal, runsPerWeek, durations.easyMinutes, longMinutes)
}
```

In `periodizedWeek`, update the TEMPO and INTERVAL `PlannedSession` constructors to use scaled durations. Replace each `PlannedSession(SessionType.TEMPO, minutes, ...)` with `PlannedSession(SessionType.TEMPO, durations.tempoMinutes, ...)`, and each `PlannedSession(SessionType.INTERVAL, minutes, ...)` with `PlannedSession(SessionType.INTERVAL, durations.intervalMinutes, ...)`.

To make `durations` available in both helper methods, pass it as a parameter or compute it once in `weekSessions` and thread it through. The simplest approach: add `durations: DurationScaler.WeekDurations` as a parameter to both `baseAerobicWeek` and `periodizedWeek`.

- [ ] **Step 2: Run existing tests**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: ALL PASS (existing PhaseEngine and SessionSelector tests should still pass; durations will shift but tests validate structure not exact minute values — verify)

- [ ] **Step 3: Compile check**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt
git commit -m "refactor(bootcamp): wire DurationScaler into SessionSelector"
```

---

## Task 3: Add `minLongRunMinutes` to BootcampGoal

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/model/BootcampGoal.kt`

### Design

Each goal needs a minimum long-run duration below which the user won't realistically reach the target distance. These are based on typical paces:

| Goal | Min Long Run | Rationale |
|------|-------------|-----------|
| CARDIO_HEALTH | 20 min | Any run builds health |
| RACE_5K_10K | 35 min | Need ~35 min to cover 5–6 km in training at easy pace |
| HALF_MARATHON | 60 min | Need 60+ min long runs to build to 15+ km |
| MARATHON | 90 min | Need 90+ min long runs to build to 25+ km |

- [ ] **Step 1: Add field to enum**

Add `val minLongRunMinutes: Int` to the `BootcampGoal` constructor and set values:

```kotlin
CARDIO_HEALTH(
    ...
    minLongRunMinutes = 20,
    ...
),
RACE_5K_10K(
    ...
    minLongRunMinutes = 35,
    ...
),
HALF_MARATHON(
    ...
    minLongRunMinutes = 60,
    ...
),
MARATHON(
    ...
    minLongRunMinutes = 90,
    ...
)
```

- [ ] **Step 2: Compile check**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/model/BootcampGoal.kt
git commit -m "feat(bootcamp): add minLongRunMinutes to BootcampGoal enum"
```

---

## Task 4: Update onboarding UI — slider relabel + long-run preview + feasibility warning

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

### Design

The onboarding time step currently shows:
```
Time per session
How long can you run each session?
[  30 min  ]
[===●============]
```

It becomes:
```
Easy run length
Set the length of a typical easy run.
[  30 min  ]
[===●============]
Long run: ~40 min · Weekly total: ~130 min
```

Plus a feasibility warning (amber, below the existing red goal warning) when the computed long run is below `goal.minLongRunMinutes`:
```
⚠ Your long run (~40 min) is shorter than recommended for
Half Marathon training (60 min). Consider increasing your
easy run length or adding a run day.
```

### 4a: UiState changes

- [ ] **Step 1: Add fields to BootcampUiState**

In `BootcampUiState.kt`, add after `onboardingTimeWarning`:

```kotlin
val onboardingLongRunMinutes: Int = 0,
val onboardingWeeklyTotal: Int = 0,
val onboardingLongRunWarning: String? = null,
```

### 4b: ViewModel changes

- [ ] **Step 2: Add long-run computation helper**

In `BootcampViewModel.kt`, add a private helper near `setOnboardingMinutes`:

```kotlin
private fun computeOnboardingDurationState(
    minutes: Int,
    runsPerWeek: Int,
    goal: BootcampGoal?
): Triple<Int, Int, String?> {
    val durations = DurationScaler.compute(runsPerWeek, minutes)
    val weeklyTotal = durations.easyMinutes * (runsPerWeek - if (runsPerWeek >= 3) 1 else 0) +
        durations.longMinutes
    val longRunWarning = if (goal != null && durations.longMinutes < goal.minLongRunMinutes) {
        "Your long run (~${durations.longMinutes} min) is shorter than recommended for " +
            "${goal.name.replace('_', ' ')} training (${goal.minLongRunMinutes} min). " +
            "Consider increasing your run length or adding a day."
    } else null
    return Triple(durations.longMinutes, weeklyTotal, longRunWarning)
}
```

- [ ] **Step 3: Wire into setOnboardingMinutes and setOnboardingRunsPerWeek**

Update `setOnboardingMinutes`:
```kotlin
fun setOnboardingMinutes(minutes: Int) {
    val state = _uiState.value
    val goal = state.onboardingGoal
    val warning = if (goal != null && minutes < goal.warnBelowMinutes) {
        "${goal.name.replace('_', ' ')} training typically needs at least ${goal.suggestedMinMinutes} min per session."
    } else null
    val (longRun, weekly, longWarning) = computeOnboardingDurationState(
        minutes, state.onboardingRunsPerWeek, goal
    )
    _uiState.update {
        it.copy(
            onboardingMinutes = minutes,
            onboardingTimeWarning = warning,
            onboardingLongRunMinutes = longRun,
            onboardingWeeklyTotal = weekly,
            onboardingLongRunWarning = longWarning
        )
    }
}
```

Similarly update `setOnboardingRunsPerWeek` to recompute long-run preview when runs/week changes.

Also update `setOnboardingGoal` to recompute the long-run warning (since changing goal changes `minLongRunMinutes`).

### 4c: Screen UI changes

- [ ] **Step 4: Update OnboardingStep2Time composable**

In `BootcampScreen.kt`, update the `OnboardingStep2Time` function:

1. Change the signature to add `longRunMinutes: Int, weeklyTotal: Int, longRunWarning: String?`

2. Change the title from `"Time per session"` to `"Easy run length"`

3. Change the subtitle from `"How long can you run each session?"` to `"Set the length of a typical easy run."`

4. After the slider range labels row (the `"15 min"` / `"90 min"` row), inside the same `GlassCard`, add the long-run preview:

```kotlin
HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 8.dp))
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
) {
    Text(
        text = "Long run: ~$longRunMinutes min",
        style = MaterialTheme.typography.bodySmall,
        color = CardeaTextSecondary
    )
    Text(
        text = "Weekly: ~$weeklyTotal min",
        style = MaterialTheme.typography.bodySmall,
        color = CardeaTextTertiary
    )
}
```

5. After the existing `warning` block (the red goal warning), add the long-run feasibility warning in amber:

```kotlin
if (longRunWarning != null) {
    Text(
        text = longRunWarning,
        style = MaterialTheme.typography.bodySmall,
        color = GradientAmber  // or Color(0xFFFFB74D) if GradientAmber doesn't exist
    )
}
```

6. Update the call site in the onboarding `when` block to pass the new params from `uiState`.

- [ ] **Step 5: Compile and smoke test**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampUiState.kt \
       app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt \
       app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt
git commit -m "feat(bootcamp): relabel onboarding slider, add long-run preview and feasibility warning"
```

---

## Task 5: Update settings UI — long-run preview below Duration slider

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsUiState.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsScreen.kt`

### Design

The settings Duration section currently shows just the slider. Add the same preview line below it.

### 5a: UiState

- [ ] **Step 1: Add computed properties to BootcampSettingsUiState**

Add to `BootcampSettingsUiState.kt`:

```kotlin
val editLongRunMinutes: Int
    get() = DurationScaler.compute(editRunsPerWeek, editTargetMinutesPerRun).longMinutes

val editWeeklyTotal: Int
    get() {
        val d = DurationScaler.compute(editRunsPerWeek, editTargetMinutesPerRun)
        val easyRuns = if (editRunsPerWeek >= 3) editRunsPerWeek - 1 else editRunsPerWeek
        return d.easyMinutes * easyRuns + d.longMinutes
    }

val longRunWarning: String?
    get() {
        val longRun = editLongRunMinutes
        return if (longRun < editGoal.minLongRunMinutes) {
            "Your long run (~$longRun min) is shorter than recommended for " +
                "${editGoal.name.replace('_', ' ')} training (${editGoal.minLongRunMinutes} min)."
        } else null
    }
```

Add import for `DurationScaler` at the top.

### 5b: Settings Screen

- [ ] **Step 2: Add preview line below Duration slider**

In `BootcampSettingsScreen.kt`, after the slider range labels row (the `"15 min"` / `"90 min"` row, around line 258), add:

```kotlin
Spacer(Modifier.height(6.dp))
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
) {
    Text(
        text = "Long run: ~${state.editLongRunMinutes} min",
        style = MaterialTheme.typography.bodySmall,
        color = CardeaTextSecondary
    )
    Text(
        text = "Weekly: ~${state.editWeeklyTotal} min",
        style = MaterialTheme.typography.bodySmall,
        color = CardeaTextTertiary
    )
}
```

And if warning exists, add it:
```kotlin
state.longRunWarning?.let { warning ->
    Spacer(Modifier.height(4.dp))
    Text(
        text = warning,
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFFFFB74D)
    )
}
```

- [ ] **Step 3: Relabel "Duration" to "Easy run length"**

Change the `"Duration"` label text (around line 222) to `"Easy run"`.

- [ ] **Step 4: Compile check**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsUiState.kt \
       app/src/main/java/com/hrcoach/ui/bootcamp/BootcampSettingsScreen.kt
git commit -m "feat(bootcamp): add long-run preview and feasibility warning to settings"
```

---

## Task 6: Final verification

- [ ] **Step 1: Run full test suite**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: ALL PASS

- [ ] **Step 2: Run compile**

Run: `./gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Spot-check duration math**

Verify in `DurationScalerTest` that the concrete examples match expectations:
- 3×30: long=30, tempo=15, interval=12 (floors active)
- 5×60: long=75, easy=56, tempo=30, interval=24
- Marathon goal + 3×30 → long=30 < minLongRun(90) → warning fires

---

## Appendix: Daniels' Rules Reference

Source: *Jack Daniels' Running Formula* (3rd ed.), summarized at run.wxm.be and fellrnr.com

| Session Type | % of Weekly Volume | Absolute Cap |
|---|---|---|
| Long (L) | 25–30% | 150 min |
| Tempo (T) | 10% | 20 min steady / 30 min cruise |
| Interval (I) | 8% | ~40 min including rest |
| Repetition (R) | 5% | ~2 min per rep |

**Long run feasibility by goal:**
- 5K/10K: need long runs ≥35 min to cover 5–6 km at easy pace
- Half marathon: need long runs ≥60 min to reach 12–15 km
- Marathon: need long runs ≥90 min to reach 22–25 km
