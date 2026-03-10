# Auto-Pause Detection — Design Spec

**Date:** 2026-03-10
**Status:** Approved

## Overview

Automatically pause the workout timer, distance accumulation, and heart rate zone alerts when the user stops moving (red light, dog break). Resumes automatically when they start running again. Mirrors GPS watch behavior (Garmin, Apple Watch).

---

## Goals

- Silence zone alerts while the user is involuntarily stopped
- Freeze the elapsed timer and distance during the stop
- Require zero manual interaction — fully automatic
- Support a live in-workout override toggle
- Support a persistent "off for all runs" setting in Account screen

---

## Detection: `AutoPauseDetector`

**Location:** `domain/engine/AutoPauseDetector.kt`

A pure Kotlin class (no Android dependencies) using a Schmitt trigger on GPS speed:

- **Stop threshold:** 0.5 m/s (~1.8 km/h) — clearly stopped
- **Resume threshold:** 1.0 m/s (~3.6 km/h) — clearly moving
- **Confirmation window:** 3 000 ms — must be below stop threshold continuously before triggering

Returns `AutoPauseEvent` enum: `NONE | PAUSED | RESUMED`

Input: `speedMs: Float?` from `Location.speed`, `nowMs: Long`

`GpsDistanceTracker` emits a new `currentSpeed: StateFlow<Float?>` alongside `currentLocation`.

---

## State: `WorkoutSnapshot`

New fields:

```kotlin
val isAutoPaused: Boolean = false
val autoPauseEnabled: Boolean = true   // whether feature is on this session
```

`isPaused` (manual) and `isAutoPaused` (automatic) are orthogonal. Alert suppression fires when either is true.

---

## Service Integration: `WorkoutForegroundService`

### Timer math

```
elapsedSeconds = (now - workoutStartMs - totalAutoPausedMs) / 1000
```

- `autoPauseStartMs`: recorded when `AutoPauseDetector` fires `PAUSED`
- `totalAutoPausedMs`: accumulated when `RESUMED` fires (`now - autoPauseStartMs`)

### Distance accumulation

`GpsDistanceTracker.setMoving(Boolean)`:
- When `false`: updates `lastLocation` (no spike on resume) and emits `currentLocation`, but does NOT add to `_distanceMeters`
- Called by service on every `PAUSED`/`RESUMED` event

### Alert suppression

`alertPolicy.handle()` and `coachingEventRouter.route()` are skipped when `isAutoPaused || isPaused`.

### Guidance text

Service writes `"STOPPED • ALERTS PAUSED"` to `guidanceText` while auto-paused. Normal zone guidance resumes on the next tick after movement detected.

### Track points

Continue recording during auto-pause — GPS breadcrumb trail must not have gaps.

### New intent

`ACTION_TOGGLE_AUTO_PAUSE` — flips `sessionAutoPauseEnabled` for the current run without writing to persistent settings. Resets detector state when disabling mid-run.

---

## Settings

### Persistent: `AutoPauseSettingsRepository`

- DataStore-backed, injected via Hilt
- Single key: `auto_pause_enabled: Boolean`, default `true`
- Read once at `startWorkout()` to initialize `sessionAutoPauseEnabled`

### Account screen

Toggle row in `AccountScreen` — label "Auto-pause when stopped", subtext "Silences alerts and pauses the timer at red lights or breaks."

### Live in-workout toggle

Small pill/chip on `ActiveWorkoutScreen` (below stat row):
- On: `"AUTO-PAUSE ON"` with pause icon
- Off: `"AUTO-PAUSE OFF"` muted, with crossed-out icon

Sends `ACTION_TOGGLE_AUTO_PAUSE` intent to service. State derived from `snapshot.autoPauseEnabled`.

---

## UI: Active Workout Screen

- **`HrRing`**: when `isAutoPaused`, render at 40% alpha via `graphicsLayer { alpha = 0.4f }`
- **Guidance text**: shows `"STOPPED • ALERTS PAUSED"` (service-written, no UI branch needed)
- **Live toggle pill**: below stat row, sends toggle intent

---

## Files to Create

- `domain/engine/AutoPauseDetector.kt`
- `data/repository/AutoPauseSettingsRepository.kt`
- `src/test/…/AutoPauseDetectorTest.kt`

## Files to Modify

- `service/GpsDistanceTracker.kt` — add `currentSpeed` StateFlow, `setMoving(Boolean)`
- `service/WorkoutState.kt` — add `isAutoPaused`, `autoPauseEnabled` to `WorkoutSnapshot`
- `service/WorkoutForegroundService.kt` — integrate detector, timer math, alert gating, new intent
- `di/AppModule.kt` — provide `AutoPauseSettingsRepository`
- `ui/account/AccountScreen.kt` — persistent toggle row
- `ui/workout/ActiveWorkoutScreen.kt` — live toggle pill
- `ui/workout/HrRing.kt` — alpha dim when auto-paused

---

## Non-Goals

- Accelerometer-based detection (GPS speed is sufficient and already available)
- Separate auto-pause duration display in post-run summary (future work)
- Mid-run preference change reactivity (read once at start)
