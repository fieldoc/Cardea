# Home Screen Content & Nav Fix — Design Spec

**Date:** 2026-03-19
**Status:** Approved
**Branch:** feature/home-screen-content

## Problem

The home screen is half-empty below the stat chips row. Additionally, navigating to Bootcamp from the home screen hero card creates a rogue back-stack entry that prevents returning to the Home tab via bottom navigation.

## Design

### Layout (below existing stat chips)

```
┌─────────────────┬─────────────────┐
│  BOOTCAMP       │  THIS WEEK      │
│  ┌──────┐       │  Distance       │
│  │ W3   │       │  ████████░░ 8/15│
│  │ of 12│       │  Time           │
│  └──────┘       │  ████████░░ 52/ │
│  25% complete   │  Runs           │
│                 │  ████░░░░░░ 2/4 │
└─────────────────┴─────────────────┘
┌───────────────────────────────────┐
│ 💡 Your Z2 pace improved 12%     │
│    Compared to 4 weeks ago...    │
└───────────────────────────────────┘
```

### 1. Bootcamp Progress Ring

- **Canvas-drawn** circular arc using `DrawScope` (matching existing chart style in `ui/charts/`)
- Arc fill uses Cardea gradient (#FF2DA6 → #5B5BFF)
- Center text: "W{currentWeek}" large, "of {totalWeeks}" small
- Percentage label below the ring
- **Visibility:** Only shown when `hasActiveBootcamp == true`
- **Without bootcamp:** This slot shows a "Total Distance" card instead (total km across all workouts), avoiding duplication with the existing "THIS WEEK" stat chip
- **Computing `totalWeeks`:** `BootcampRepository` already has `getGoalForEnrollment(enrollmentId)`. Use `PhaseEngine(goal).totalWeeks`. HomeViewModel needs to call this suspend function inside the existing `flatMapLatest` block.
- **Computing `percentComplete`:** `currentWeekNumber.toFloat() / totalWeeks` (week-based, not session-based — simpler and matches the ring visual)

### 2. Weekly Volume Card

- Three progress bars stacked vertically:
  - **Distance:** `totalDistanceThisWeekMeters / weeklyDistanceTarget` km — gradient #FF5A5F → #FF2DA6
  - **Time:** `totalTimeThisWeekMinutes / weeklyTimeTarget` min — gradient #5B5BFF → #00D1FF
  - **Runs:** reuse existing `workoutsThisWeek / weeklyTarget` (already in HomeUiState) — gradient #00D1FF → #4DFF88
- **Targets:**
  - With bootcamp: `enrollment.runsPerWeek` for run target (already used as `weeklyTarget`). Distance target = `sum(session.targetMinutes) * 0.15` km (conservative 9 km/hr easy pace estimate). Time target = `sum(session.targetMinutes)` for the week's scheduled sessions.
  - Without bootcamp: defaults (15.0 km, 90 min, 4 runs)
- **Data source:** `WorkoutRepository.getAllWorkouts()` provides the full workout list. HomeViewModel already filters to current week for `workoutsThisWeek` count. New: also sum `totalDistanceMeters.toDouble()` and `(endTime - startTime) / 60_000L` across this-week workouts in the same loop. These are **new computations** to add to the `flatMapLatest` block.

### 3. Coaching Insight Card

- Single card showing the highest-priority applicable tip
- **Icon** (gradient background, rounded square) + **Title** (bold, 13sp) + **Subtitle** (secondary, 11sp)
- Static rule engine in `domain/coaching/CoachingInsightEngine.kt`

#### Tip Templates (priority order)

| Priority | Condition | Title | Subtitle |
|----------|-----------|-------|----------|
| 1 | No workouts ever | Start your first run | Connect your HR monitor and hit the trail |
| 2 | Days since last run >= 7 | Time to get moving | It's been {n} days since your last run |
| 3 | 3+ consecutive hard sessions (Z4/tempo/interval) | Consider an easy day | {n} hard sessions in a row — an easy run helps recovery |
| 4 | Z2 pace improved >= 5% over 4 weeks | Z2 pace improved {n}% | Your aerobic base is growing — keep it up |
| 5 | Weekly run count met target | Weekly goal reached! | {n} runs this week — nice consistency |
| 6 | Has bootcamp, < 50% weekly target done, > halfway through week | Pick up the pace this week | {done}/{target} sessions done — {remaining} left to stay on track |
| 7 | Default/fallback | Consistency is key | Regular training builds a stronger aerobic base |

#### Data Inputs

- `workouts: List<WorkoutEntity>` — all workouts (for trend analysis, recent session types)
- `daysSinceLastRun: Int` — computed from `workouts.firstOrNull()?.endTime`
- `weeklyStats` — already in HomeUiState (workoutsThisWeek, weeklyTarget)
- **Z2 pace trend:** Compare average pace of Z2-tagged workouts from last 4 weeks vs. prior 4 weeks. `WorkoutEntity.targetConfig` is a JSON-serialized `WorkoutConfig`. Parse it with `Gson().fromJson(targetConfig, WorkoutConfig::class.java)` — if `mode == STEADY_STATE` and the zone name contains "Z2" or "EASY" or "AEROBIC" (case-insensitive), it's a Z2 workout. Pace proxy = `totalDistanceMeters / ((endTime - startTime) / 1000.0)` (m/s). Compare mean pace of Z2 workouts from [now-28d, now] vs. [now-56d, now-28d]. A higher m/s value = improvement.

### 4. Nav Bug Fix

**Root cause:** `NavGraph.kt` line 300-304, `onGoToBootcamp` navigates to `Routes.BOOTCAMP` with only `launchSingleTop = true`. Missing `popUpTo(Routes.HOME) { saveState = true }` and `restoreState = true`.

**Fix:** Match the same navigation pattern used by bottom bar tab clicks:
```kotlin
onGoToBootcamp = {
    navController.navigate(Routes.BOOTCAMP) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
```

**Also fix:** `onGoToProgress` (line 282-286) and `onGoToHistory` (line 288-292) have the same omission — they include `popUpTo` + `saveState` but are missing `restoreState = true`. Add `restoreState = true` to both for consistency with the bottom bar pattern.

## UI State Changes

### HomeUiState additions

```kotlin
// New fields (add to existing HomeUiState data class in HomeViewModel.kt)
val totalDistanceThisWeekMeters: Double = 0.0,   // sum of WorkoutEntity.totalDistanceMeters.toDouble()
val totalTimeThisWeekMinutes: Long = 0,           // sum of (endTime - startTime) / 60_000L
val weeklyDistanceTargetKm: Double = 15.0,        // bootcamp-derived or default
val weeklyTimeTargetMinutes: Long = 90,            // bootcamp-derived or default
val bootcampTotalWeeks: Int = 12,                  // from PhaseEngine(goal).totalWeeks
val bootcampPercentComplete: Float = 0f,           // currentWeekNumber.toFloat() / bootcampTotalWeeks
val coachingInsight: CoachingInsight? = null,
// NOTE: weeklyRunTarget is NOT added — reuse existing `weeklyTarget` field for runs-per-week target
```

### New domain class

```kotlin
// domain/coaching/CoachingInsight.kt
data class CoachingInsight(
    val title: String,
    val subtitle: String,
    val icon: CoachingIcon  // enum: LIGHTBULB, CHART_UP, TROPHY, WARNING, HEART
)
```

## Files to Create/Modify

| File | Action |
|------|--------|
| `domain/coaching/CoachingInsightEngine.kt` | **Create** — static rule engine |
| `domain/coaching/CoachingInsight.kt` | **Create** — data class + icon enum |
| `ui/home/HomeScreen.kt` | **Modify** — add BootcampProgressRing, WeeklyVolumeCard, CoachingInsightCard composables |
| `ui/home/HomeViewModel.kt` | **Modify** — compute new state fields, call CoachingInsightEngine |
| `ui/navigation/NavGraph.kt` | **Modify** — fix onGoToBootcamp + onGoToProgress + onGoToHistory navigation (add `restoreState = true`, add `popUpTo` to bootcamp) |

## Non-Goals

- No readiness/recovery gauge (dropped — coaching insight covers this contextually)
- No recent runs mini-list (History tab serves this)
- No fitness trend sparkline (deferred — could be added later)
- No quick-start free run button (deferred)
- Coaching insight engine is static rules only — no ML, no adaptive profile integration yet
