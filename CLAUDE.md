# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

HR Coach — an Android app (Kotlin, Jetpack Compose) for real-time heart rate zone coaching during runs. Connects to BLE heart rate monitors (targeting Coospo H808S), tracks GPS distance, and plays audio alerts when HR drifts outside target zones. Includes an adaptive learning engine that models pace-HR relationships over time.

## Build & Test Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run a single test class
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.ZoneEngineTest"

# Run instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean assembleDebug

# Check/lint
./gradlew lint
```

**Build requirements:** JDK 17, Android SDK with compileSdk 35. Google Maps API key goes in `local.properties` as `MAPS_API_KEY=...` (falls back to `local.defaults.properties` placeholder).

## Architecture

**MVVM + Foreground Service** — three layers:

```
Compose UI (screens + ViewModels)
    ↕ StateFlow
WorkoutForegroundService (orchestrator)
    BleHrManager | GpsDistanceTracker | ZoneEngine | AlertManager | AdaptivePaceController
    ↓
Room Database + repositories (`WorkoutRepository`, `WorkoutMetricsRepository`, `AdaptiveProfileRepository`)
```

**Key architectural decisions:**

- **WorkoutState** (`service/WorkoutState.kt`) is a singleton `StateFlow` container shared between the foreground service and UI. All workout runtime state lives here — HR, zone status, distance, pace, guidance text.
- **WorkoutForegroundService** is the central orchestrator. It combines BLE HR and GPS flows, evaluates zones, triggers alerts, saves track points every 5s, and persists completed workouts to Room.
- **Two workout modes:** `STEADY_STATE` (single target HR ± buffer) and `DISTANCE_PROFILE` (ordered distance segments each with a target HR).
- **Adaptive learning** (`AdaptivePaceController`) tracks HR slope, pace-HR buckets, response lag, and trim offsets across sessions. Profile persists via `AdaptiveProfileRepository`.
- **Alerts** use `ToneGenerator` on `STREAM_NOTIFICATION` to layer tones over music without requesting audio focus.

## Key Packages

- `data/db/` — Room database (`AppDatabase`), entities (`WorkoutEntity`, `TrackPointEntity`), DAOs
- `data/repository/` — `WorkoutRepository`, `WorkoutMetricsRepository`, `AdaptiveProfileRepository`, `AudioSettingsRepository`, `MapsSettingsRepository`
- `domain/model/` — Domain models: `WorkoutConfig`, `WorkoutMode`, `AdaptiveProfile`, `ZoneStatus`
- `domain/engine/` — `ZoneEngine` (static zone eval), `AdaptivePaceController` (predictive HR-pace modeling)
- `service/` — `WorkoutForegroundService`, `BleHrManager`, `GpsDistanceTracker`, `AlertManager`, `WorkoutState`
- `ui/setup/` — Setup screen + ViewModel (config, BLE scanning)
- `ui/workout/` — Active workout display
- `ui/history/` — History list + detail with Google Maps route heatmap
- `ui/navigation/` — `NavGraph` with routes: `setup`, `workout`, `history`, `history/{workoutId}`
- `di/` — Hilt `AppModule` providing Room database and DAOs

## DI & Entry Points

Hilt is the DI framework. `HrCoachApp` is `@HiltAndroidApp`. `MainActivity` and `WorkoutForegroundService` are `@AndroidEntryPoint`. ViewModels use `@HiltViewModel`. `AppModule` provides singleton-scoped Room database and DAOs.

## Database

Room database `hr_coach_db` with two tables: `workouts` and `track_points` (FK to workout with CASCADE delete). `targetConfig` column stores workout zone configuration as JSON.

## Navigation

Bottom bar with Setup and History tabs. Active workout screen hides the bottom bar. Navigation auto-transitions to workout screen when service starts and back to setup when it ends.

## Design Document

See `docs/plans/2026-02-25-hr-coaching-app-design.md` for the full design specification including alert behavior, data model details, and scope boundaries.
