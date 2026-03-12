# Achievement Cards Design Spec

## Overview

Celebratory achievement cards that mark real training milestones — moments a coach would stop and acknowledge. Not gamification (no XP, no unlock trees, no points). Cards surface on the post-run summary when earned and live permanently in a trophy gallery on the Profile screen.

## Design Decisions

- **Visual weight**: Tier drives prestige level (simple → refined → premium). Goal drives color/icon accent. No goal hierarchy — every goal is equally celebrated at its tier.
- **3-level prestige system**: Applies universally across all milestone categories. Higher thresholds within each category earn more prestigious cards.
- **Architecture**: ViewModel-triggered evaluation. `AchievementEvaluator` is called from `PostRunSummaryViewModel` after the workout is fully persisted — following the same pattern as `BootcampSessionCompleter`, which already runs in `PostRunSummaryViewModel.load()`. This keeps the foreground service focused on sensor/metric persistence and puts user-facing concerns in the presentation layer.
- **Local only**: No sync, no server-side evaluation. Fully offline-capable.

## Milestone Categories

### 1. Tier Graduation

Awarded when the user accepts a tier-up prompt (CTL sustained above tier threshold).

| Milestone | Prestige | Trigger |
|-----------|----------|---------|
| Tier 1 → Tier 2 | 2 (refined) | Tier-up prompt accepted |
| Tier 2 → Tier 3 | 3 (premium) | Tier-up prompt accepted |

Per goal — 4 goals (`CARDIO_HEALTH`, `RACE_5K_10K`, `HALF_MARATHON`, `MARATHON`) x 2 graduations = up to 8 cards. Note: `tierIndex` in `BootcampEnrollmentEntity` is 0-based (0, 1, 2). "Tier 1 → Tier 2" means `tierIndex` 0 → 1; "Tier 2 → Tier 3" means `tierIndex` 1 → 2. The `tier` column in `AchievementEntity` stores the new tier index (1 or 2).

Goal accent icons: heart (`CARDIO_HEALTH`), shoe (`RACE_5K_10K`), road (`HALF_MARATHON`), medal (`MARATHON`).

### 2. Cumulative Distance

Awarded when total lifetime distance crosses a threshold. Computed via a new DAO query `sumAllDistanceKm()` that returns `SUM(totalDistanceMeters) / 1000.0` across all `WorkoutEntity` rows (includes both bootcamp and free runs — all running counts).

| Milestone | Prestige |
|-----------|----------|
| 50 km | 1 (simple) |
| 100 km | 1 (simple) |
| 250 km | 2 (refined) |
| 500 km | 2 (refined) |
| 1,000 km | 3 (premium) |
| 2,500 km | 3 (premium) |

### 3. Consistency Streaks

Awarded for consecutive completed sessions with no misses. Reuses existing streak logic from `HomeSessionStreak.computeSessionStreak()`.

| Milestone | Prestige |
|-----------|----------|
| 5 sessions | 1 (simple) |
| 10 sessions | 1 (simple) |
| 20 sessions | 2 (refined) |
| 50 sessions | 2 (refined) |
| 100 sessions | 3 (premium) |

### 4. Weekly Goal Streaks

Awarded for consecutive weeks hitting the `runsPerWeek` target.

| Milestone | Prestige |
|-----------|----------|
| 4 weeks | 1 (simple) |
| 8 weeks | 2 (refined) |
| 12 weeks | 2 (refined) |
| 24 weeks | 3 (premium) |

**Algorithm**: Walk backward from the most recently completed ISO week (Monday–Sunday). For each week, count completed bootcamp sessions. Compare against `enrollment.runsPerWeek`. Stop at the first week that falls short. The current (incomplete) week is excluded — only fully elapsed weeks count. Deferred sessions do not count as completions for weekly goal purposes (they were rescheduled, not done). Weeks before the enrollment start date are excluded.

### 5. Bootcamp Graduation

Awarded when enrollment status transitions to `GRADUATED`.

| Milestone | Prestige |
|-----------|----------|
| Program complete | 3 (premium) |

Always prestige 3. Tier + goal accent still apply for visual differentiation.

## Data Model

### AchievementEntity (Room table)

| Column | Type | Purpose |
|--------|------|---------|
| `id` | Long (PK, auto) | Unique ID |
| `type` | String | `TIER_GRADUATION`, `DISTANCE_MILESTONE`, `STREAK_MILESTONE`, `BOOTCAMP_GRADUATION`, `WEEKLY_GOAL_STREAK` |
| `milestone` | String | Specific threshold key, e.g. `"100km"`, `"20_sessions"`, `"tier_2"`, `"8_weeks"` |
| `goal` | String? | Nullable — only for tier/bootcamp cards (e.g. `HALF_MARATHON`) |
| `tier` | Int? | Nullable — only for tier graduation (0, 1, 2) |
| `prestigeLevel` | Int | 1 (simple), 2 (refined), 3 (premium) |
| `earnedAtMs` | Long | Timestamp when achieved |
| `triggerWorkoutId` | Long? | Workout that triggered it (nullable for non-workout triggers) |
| `shown` | Boolean | False initially, set true after post-run display |

### AchievementDao

- `getUnshownAchievements(): List<AchievementEntity>` — for post-run display
- `getAllAchievements(): Flow<List<AchievementEntity>>` — for Profile gallery (ordered by `earnedAtMs` desc)
- `getAchievementsByType(type: String): Flow<List<AchievementEntity>>` — gallery filtering
- `hasAchievement(type: String, milestone: String): Boolean` — duplicate guard
- `markShown(ids: List<Long>)` — after displaying on post-run
- `insert(achievement: AchievementEntity)` — standard insert

### Design rationale

- `prestigeLevel` denormalized at insert time — UI reads it directly, no recomputation
- `shown` flag decouples "earned" from "displayed" — survives app kill between earn and display
- `milestone` as String (not enum) — new thresholds can be added without schema migration

## Evaluation Logic

### AchievementEvaluator

Domain-layer class with `@Inject constructor`. Called from `PostRunSummaryViewModel.load()` after workout and metrics are fully persisted — same integration point as `BootcampSessionCompleter`.

```
evaluatePostWorkout(workoutId, enrollmentId?)
  ├─ DISTANCE: sumAllDistanceKm() → check [50, 100, 250, 500, 1000, 2500]
  ├─ STREAK: computeSessionStreak(sessions) → check [5, 10, 20, 50, 100]
  └─ WEEKLY GOAL: computeWeeklyGoalStreak(sessions, runsPerWeek) → check [4, 8, 12, 24]

evaluateTierGraduation(newTierIndex, goal)
  └─ called explicitly when user accepts tier-up prompt in BootcampViewModel

evaluateBootcampGraduation(enrollmentId)
  └─ called explicitly when BootcampSessionCompleter transitions status → GRADUATED
```

**Idempotent**: `hasAchievement(type, milestone)` guard prevents duplicates on re-evaluation.

**Streak logic shared**: Extract `computeSessionStreak()` from `HomeSessionStreak.kt` to `domain/achievement/StreakCalculator.kt`, reusable by both Home and the evaluator.

**Tier/graduation are explicit calls**, wired at their specific trigger points:
- Tier graduation: wherever the tier-up prompt acceptance updates `enrollmentEntity.tierIndex` (currently in `BootcampViewModel` or `BootcampSettingsViewModel` — the implementation plan will identify the exact callsite)
- Bootcamp graduation: in `BootcampSessionCompleter` where enrollment status transitions to `GRADUATED`

## UI Surfaces

### Post-Run Summary

Achievement cards appear inline in `PostRunSummaryScreen` after the existing workout stats. Flow:

1. `PostRunSummaryViewModel.load()` calls `AchievementEvaluator.evaluatePostWorkout()` after existing workout loading
2. ViewModel queries `getUnshownAchievements()` and exposes them in `PostRunUiState`
3. Cards render with "Achievement Unlocked" header; multiple achievements stack vertically
4. `markShown(ids)` is called from `PostRunSummaryViewModel.onCleared()` — this ensures achievements are marked shown even if the user navigates away via any route (`onDone`, `onBack`, `onViewProgress`, etc.) or the app is killed. If `onCleared` doesn't fire (process death), the cards simply show again next time — acceptable and even desirable.

### Profile Gallery

A 2-column grid in the Profile/Account screen, inserted between the ProfileHeroCard and the Configuration section. Shows all earned achievements sorted by most recent. Each card is a compact version of the same `AchievementCard` composable.

Header: "Achievements" with count badge (e.g. "7 earned"). Section is hidden when no achievements have been earned.

### Shared AchievementCard Composable

Single composable used in both contexts with a `compact` parameter for gallery mode.

**Prestige visual treatment:**

| Level | Color | Border | Background |
|-------|-------|--------|------------|
| 1 (Simple) | Slate (#94a3b8) | Subtle, 15% opacity | 8% → 2% gradient |
| 2 (Refined) | Sky-blue (#7dd3fc) | Medium, 25% opacity | 8% → 2% gradient |
| 3 (Premium) | Gold (#facc15) | Prominent, 30% opacity | 12% → 3% gradient |

Prestige dots (1-3 filled circles) provide at-a-glance difficulty signal.

Prestige colors should be added to `ui/theme/Color.kt` as named tokens: `AchievementSlate`, `AchievementSky`, `AchievementGold` (plus border/background variants).

Goal accent determines the icon: heart (`CARDIO_HEALTH`), shoe (`RACE_5K_10K`), road (`HALF_MARATHON`), medal (`MARATHON`). Non-goal milestones use category icons: runner (distance), flame (streak), target (weekly goal), graduation cap (bootcamp).

## File Changes

### New files

| File | Purpose |
|------|---------|
| `data/db/AchievementEntity.kt` | Room entity + AchievementType enum |
| `data/db/AchievementDao.kt` | DAO queries |
| `domain/achievement/AchievementEvaluator.kt` | Milestone evaluation logic |
| `domain/achievement/MilestoneDefinitions.kt` | Threshold constants + prestige mappings |
| `ui/account/AchievementGallery.kt` | Profile gallery composable |
| `ui/components/AchievementCard.kt` | Shared card composable |

### Modified files

| File | Change |
|------|--------|
| `data/db/AppDatabase.kt` | Add entity, bump version, add migration |
| `di/AppModule.kt` | Provide `AchievementDao` (`AchievementEvaluator` uses `@Inject constructor`, no `@Provides` needed) |
| `ui/postrun/PostRunSummaryViewModel.kt` | Query unshown achievements, mark shown |
| `ui/postrun/PostRunSummaryScreen.kt` | Render achievement cards inline |
| `ui/account/ProfileScreen.kt` | Add achievements gallery section |
| `ui/home/HomeSessionStreak.kt` | Extract streak logic to shared domain utility |
| `domain/bootcamp/BootcampSessionCompleter.kt` | Call `evaluateBootcampGraduation()` when status → GRADUATED |
| `ui/theme/Color.kt` | Add `AchievementSlate`, `AchievementSky`, `AchievementGold` color tokens |

### Untouched

- `WorkoutForegroundService`, `BleHrManager`, `GpsDistanceTracker` — no sensor/service changes
- `ZoneEngine` — no workout execution changes
- `NavGraph.kt` — no new routes (gallery is a section, not a screen)
- Bootcamp scheduling/session system — read-only access
