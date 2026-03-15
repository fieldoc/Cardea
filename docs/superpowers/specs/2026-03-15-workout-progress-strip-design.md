# Workout Progress Strip — Design Spec

**Date:** 2026-03-15
**Status:** Approved

## Problem

During active workouts, the UI doesn't communicate "how long is this run?" at a glance. Users can't tell if they're doing a 20-min or 60-min session, whether it's time-based or distance-based, or how much is left.

## Solution: Labeled Progress Strip

Add a labeled progress strip between the header row and the HR ring on the ActiveWorkoutScreen. It appears in all modes with mode-appropriate content.

### Time-based (STEADY_STATE with time segments)

- **Left label:** "12:34 elapsed" (tertiary color)
- **Right label:** "12:26 remaining" (secondary color, bolder — this is what runners care about)
- **Bar:** CardeaGradient, 4px, fills left-to-right based on elapsed/total ratio
- **Center label below bar:** "25 min · Steady-state" (tertiary, subtle)

### Distance-based (DISTANCE_PROFILE)

- **Left label:** "3.0 km covered" (tertiary)
- **Right label:** "2.0 km to go" (secondary, bolder)
- **Bar:** CardeaGradient, 4px, fills based on distance/totalDistance ratio
- **Center label:** "5.0 km · Distance profile" (tertiary)

### Free Run (FREE_RUN)

- No progress bar, no left/right labels
- Just the center label: "Open-ended · No target" (tertiary)
- Same glass container, same vertical position — keeps layout consistent

## Data Flow

`ActiveWorkoutUiState` gets new fields:
- `totalDurationSeconds: Long?` — sum of all segment durationSeconds (time-based only)
- `totalDistanceMeters: Float?` — last segment's cumulative distanceMeters (distance-based only)
- `workoutTypeLabel: String?` — e.g. "25 min · Steady-state", "5.0 km · Distance profile", "Open-ended · No target"

These are derived from `WorkoutConfig` in `handleSnapshot` / the 1-second tick loop.

## UI Component

New `WorkoutProgressStrip` composable in `ActiveWorkoutScreen.kt`. Placed between the header Row and `if (showDistanceProfileProgress(...))` block (which gets replaced by this new component).

## Files Changed

1. `ActiveWorkoutUiState` — add 3 fields
2. `WorkoutViewModel.handleSnapshot` / tick loop — compute new fields
3. `ActiveWorkoutScreen.kt` — add `WorkoutProgressStrip`, remove old `GradientProgressBar` usage for distance profile
