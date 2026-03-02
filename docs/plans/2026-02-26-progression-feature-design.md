# Progression Feature Design

**Date:** 2026-02-26

## Purpose

Surface longitudinal fitness progress to the user. Show how cardiovascular fitness improves over weeks/months using established sports science metrics — computed from HR + pace data only (no HRV, compatible with $50 BLE chest straps).

## Metrics (Sports Science Basis)

### Efficiency Factor (EF)

Ratio of speed to heart rate. Higher = fitter.

```
EF = (1000 / avgPaceMinPerKm) / avgHr
```

A recreational runner typically sees EF 1.0-1.5. Rising EF over similar workouts = aerobic improvement.

Sources: TrainingPeaks Efficiency Factor, Joe Friel methodology.

### Aerobic Decoupling (Pa:HR)

Measures cardiac drift within a single workout by comparing EF in first half vs second half (split by elapsed time).

```
Pa:HR = ((EF_firstHalf - EF_secondHalf) / EF_firstHalf) * 100
```

Below 5% = solid aerobic endurance. Lower over time = improving fitness. Positive means HR drifted up or pace dropped in the second half.

Sources: TrainingPeaks, Uphill Athlete heart rate drift test methodology.

### HR at Reference Pace

Already computed as `hrAtSixMinPerKm` using Gaussian-weighted kernel. Lower over time = fitter at the same effort level.

### HR Recovery Speed

Already tracked as settle-down/settle-up times. Faster settling = better cardiovascular responsiveness.

## Architecture

### Data Flow

```
WorkoutForegroundService
  -> AdaptivePaceController.finishSession()
    -> computes EF + decoupling from accumulated paceSamples
    -> stores in WorkoutAdaptiveMetrics (SharedPreferences JSON)
  -> sets WorkoutState.completedWorkoutId
  -> resets WorkoutState.isRunning

NavGraph LaunchedEffect detects !isRunning
  -> navigates to PostRunSummary/{workoutId}

PostRunSummaryScreen
  -> loads current workout metrics
  -> finds similar past workouts (same mode, ±20% distance)
  -> shows at-a-glance comparison
  -> user taps "View Progress" -> Progress tab, or "Done" -> Setup

ProgressScreen
  -> loads last 30 workouts with metrics
  -> backfills legacy workouts via MetricsCalculator
  -> renders summary cards + trend charts
```

### New Files

| File | Purpose |
|------|---------|
| `domain/engine/MetricsCalculator.kt` | Pure computation: EF, decoupling, full metrics derivation from track points |
| `ui/progress/ProgressScreen.kt` | Progress tab UI — summary cards + trend charts |
| `ui/progress/ProgressViewModel.kt` | Data loading, trend computation, filtering |
| `ui/postrun/PostRunSummaryScreen.kt` | Post-run at-a-glance summary with comparisons |
| `ui/postrun/PostRunSummaryViewModel.kt` | Similarity matching, comparison computation |

### Modified Files

| File | Change |
|------|--------|
| `domain/model/WorkoutAdaptiveMetrics.kt` | Add efficiencyFactor, aerobicDecoupling, efFirstHalf, efSecondHalf |
| `domain/engine/AdaptivePaceController.kt` | Accumulate paceSamples, compute EF/decoupling in finishSession() |
| `service/WorkoutState.kt` | Add completedWorkoutId StateFlow |
| `service/WorkoutForegroundService.kt` | Set completedWorkoutId before reset |
| `ui/navigation/NavGraph.kt` | Add Progress tab, PostRunSummary route, rewire post-workout flow |
| `data/db/WorkoutDao.kt` | Add getAllWorkoutsOnce() suspend query |
| `ui/history/HistoryViewModel.kt` | Delegate deriveMetrics to MetricsCalculator, remove progress chart code |
| `ui/history/HistoryListScreen.kt` | Remove progress charts (moved to Progress tab) |
| `ui/theme/Color.kt` | Add ProgressGreen, ProgressAmber, ThresholdLine colors |

### Workout Similarity Matching

"Similar" = same `WorkoutMode` + total distance within ±20%. Used for post-run comparisons.

### Navigation

Bottom bar: Workout | Progress | History (3 tabs).
Post-run flow: Workout -> PostRunSummary/{workoutId} -> (Done -> Setup) or (View Progress -> Progress tab).
PostRunSummary and active Workout screens hide the bottom bar.

### Charts

Canvas-based composables following the existing `LineChartCard` pattern in `HistoryListScreen.kt`. No new chart library. Decoupling chart adds a dashed horizontal threshold line at 5%.

### Legacy Backfill

Workouts recorded before this feature get metrics computed on first access via `MetricsCalculator.deriveFullMetrics()` from their `TrackPointEntity` rows, then saved.

## Post-Run Summary Screen Layout

```
+--------------------------------------+
|           Run Complete!              |
|                                      |
|   [distance]  [duration]  [avg HR]  |
|                                      |
|   ---- vs. Similar Runs ----        |
|                                      |
|   HR @ 6:00/km: 142 bpm             |
|   5 bpm lower than avg              |
|                                      |
|   Efficiency: 1.32                   |
|   +0.08 vs similar                  |
|                                      |
|   Decoupling: 3.2%                   |
|   Good aerobic endurance             |
|                                      |
|   [View Progress]    [Done]          |
+--------------------------------------+
```

Only show comparisons when delta is meaningful (exceeds minimum threshold). Skip metrics with insufficient data.

## Progress Tab Layout

1. Summary cards (horizontal scroll): HR@6:00/km, EF, Decoupling, Recovery Speed — each with latest value + trend arrow
2. Trend charts (vertical stack): one per metric, line charts over last 20-30 workouts
3. Expandable "What do these mean?" section with plain-English explanations
4. Filter toggle: All / Steady State / Distance Profile
