# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Cardea — an Android app (Kotlin, Jetpack Compose) for real-time heart rate zone coaching during runs. Connects to BLE heart rate monitors (targeting Coospo H808S), tracks GPS distance, and plays audio alerts when HR drifts outside target zones. Includes an adaptive learning engine that models pace-HR relationships over time.

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
- **Three workout modes:** `STEADY_STATE` (single target HR ± buffer), `DISTANCE_PROFILE` (ordered distance segments each with a target HR), and `FREE_RUN` (no target, data collection only).
- **Adaptive learning** (`AdaptivePaceController`) tracks HR slope, pace-HR buckets, response lag, and trim offsets across sessions. Profile persists via `AdaptiveProfileRepository`.
- **Alerts** use `ToneGenerator` on `STREAM_NOTIFICATION` to layer tones over music without requesting audio focus.

## Key Packages

- `data/db/` — Room database (`AppDatabase`), entities (`WorkoutEntity`, `TrackPointEntity`), DAOs
- `data/repository/` — `WorkoutRepository`, `WorkoutMetricsRepository`, `AdaptiveProfileRepository`, `AudioSettingsRepository`, `MapsSettingsRepository`
- `domain/model/` — Domain models: `WorkoutConfig`, `WorkoutMode`, `AdaptiveProfile`, `ZoneStatus`
- `domain/engine/` — `ZoneEngine` (static zone eval), `AdaptivePaceController` (predictive HR-pace modeling)
- `service/` — `WorkoutForegroundService`, `BleHrManager`, `GpsDistanceTracker`, `AlertManager`, `WorkoutState`
- `ui/home/` — Home dashboard screen + ViewModel (new)
- `ui/setup/` — Workout setup screen + ViewModel (config, BLE scanning); maps to "Workout" nav tab
- `ui/workout/` — Active workout display
- `ui/history/` — History list + detail with Google Maps route heatmap
- `ui/account/` — Account & settings screen + ViewModel (new); includes Maps API key + audio settings
- `ui/components/` — Shared composables: `CardeaLogo`, `GlassCard`
- `ui/splash/` — Branded splash screen
- `ui/navigation/` — `NavGraph.kt` (function: `HrCoachNavGraph`) with routes: `home`, `setup`, `workout`, `progress`, `history`, `history/{workoutId}`, `postrun/{workoutId}`, `account`
- `di/` — Hilt `AppModule` providing Room database and DAOs

## DI & Entry Points

Hilt is the DI framework. `HrCoachApp` is `@HiltAndroidApp`. `MainActivity` and `WorkoutForegroundService` are `@AndroidEntryPoint`. ViewModels use `@HiltViewModel`. `AppModule` provides singleton-scoped Room database and DAOs.

## Database

Room database `hr_coach_db` with two tables: `workouts` and `track_points` (FK to workout with CASCADE delete). `targetConfig` column stores workout zone configuration as JSON.

## Navigation

Five-tab bottom bar: **Home**, **Workout** (setup), **History**, **Progress**, **Account**. Start destination after splash is `home`. Active workout screen hides the bottom bar. Navigation auto-transitions to workout screen when service starts; when workout ends it navigates back to `setup` (or post-run summary). `onDone` from post-run summary returns to `home`.

## UI & Theme

- **Cardea design system** — Dark glass-morphic theme. Background `#0B0F17` (radial gradient with `#0F1623`). Cardea gradient: `#FF5A5F→#FF2DA6→#5B5BFF→#00D1FF` at 135°. All design tokens are in `ui/theme/Color.kt` as named constants. See `docs/plans/2026-03-02-cardea-ui-ux-design.md` for the authoritative spec.
- **`CardeaTheme`** — Primary theme function. `HrCoachTheme` is a backward-compat wrapper. `HrCoachThemeTokens` is a `typealias` for `CardeaThemeTokens`. Dynamic color is `false` — Cardea palette is always enforced.
- **`CardeaGradient`** — `Brush.linearGradient` with exact 4-stop color stops (do NOT alter). Use for CTAs, active nav icons, ring elements. Access via `HrCoachThemeTokens.gradient` in composables.
- **Glass surface pattern** — `GlassBorder = Color(0x0FFFFFFF)`, `GlassHighlight = Color(0x14FFFFFF)`. Use `GlassCard` composable from `ui/components/GlassCard.kt` for all card surfaces.
- **`CardeaLogo`** — Canvas-drawn composable in `ui/components/CardeaLogo.kt`. Heart + ECG line + orbital ring with gradient fill. Two sizes: `LogoSize.LARGE` (splash, 180dp) and `LogoSize.SMALL` (nav badge, 32dp).
- **Gradient nav icons** — Active nav icons use `CompositingStrategy.Offscreen` + `BlendMode.SrcIn` with `CardeaGradient` to produce pixel-perfect gradient fill on any `ImageVector` icon.
- **Charts are custom Canvas-drawn** — `ui/charts/` (BarChart, PieChart, ScatterPlot) use `DrawScope` directly; no charting library. Styling changes require Canvas API edits.
- **`WorkoutSnapshot` has no elapsed time** — compute elapsed seconds in the ViewModel via a ticker flow when `isRunning && !isPaused`.
- **Maps settings** — Moved from a dialog in SetupScreen to `AccountScreen`. `SetupScreen` no longer contains any Maps API key UI.

## Design Documents

- **Authoritative spec:** `docs/plans/2026-03-02-cardea-ui-ux-design.md`
- **Implementation plan:** `docs/plans/2026-03-02-cardea-ui-ux-plan.md`
- **Guided workouts UX design:** `docs/plans/2026-03-02-guided-workouts-ux-design.md` — Approach B: Cardea glass preset cards, segment timeline strip, HRmax onboarding, interval countdown.
- **Guided workouts implementation plan:** `docs/plans/2026-03-01-preset-workout-profiles.md` — 12-task TDD plan; Tasks 1–2 already done in commit fd3d9d9.
- Legacy: `docs/plans/2026-02-25-hr-coaching-app-design.md` — superseded; data model and alert behavior sections still valid, UI/UX sections replaced by the 2026-03-02 spec.

## Pre-Commit Checklist (Anti-AI-Pitfall Guard)

REQUIRED before every commit, merge, or push. If asked to commit/merge/push and this hasn't been run in the current session, respond: **"Before we save — want me to run the development checklist first?"** and wait for confirmation.

Verify by **reading actual files**, not from memory. Report as a table: `Check | Finding | Verdict (PASS / FAIL / NOTE)`. Any FAIL blocks the commit.

1. **UI wired, not decorative** — Every new trigger (button, gesture, toggle) in Compose has a real callback that reaches a ViewModel function. No `onClick = {}` no-ops, no TODO lambdas, no orphan composables that render but do nothing. If `isWideLayout` affects the screen, verify the feature works in both branches.

2. **Real logic, not a stub** — The ViewModel function contains actual business logic inside `viewModelScope.launch { ... }`. Data flows end-to-end: **Compose UI → ViewModel → Repository → Room DAO**. Watch for functions that update `_uiState` optimistically but never call a repository method to persist the change.

3. **Data saved and UI refreshed from source of truth** — Writes go through a Repository to Room (e.g., `bootcampRepository.createEnrollment()`, `workoutDao.update()`). After the write, UI refreshes from the database — either via automatic Flow re-emission (the `getActiveEnrollment()` collector pattern) or an explicit reload like `refreshFromEnrollment()` / `loadBootcampState()`. Flag any pattern that updates `_uiState` locally without a corresponding DB write — that state vanishes on process death.

4. **Errors surfaced, not swallowed** — Every `try/catch` and failure path either: sets a user-visible error field (`loadError`, `connectionError`, `maxHrGateError`, `swapRestMessage`), shows a Snackbar, or has an explicit design reason to fail silently (document it in a comment). Flag any bare `catch (e: Exception) { }` or `catch` that only logs. `CancellationException` must always be re-thrown.

5. **No phantom state** — New fields added to a `UiState` data class are actually read somewhere in the corresponding Screen composable. New ViewModel functions are actually called from UI code. Dead fields and unreachable functions are classic AI-generated bloat — delete them.

6. **Filter/query logic tested** — Any new Room DAO query, domain filter (like `SessionRescheduler.availableDays`), or list transformation has at least one unit test covering the happy path and one covering the key exclusion/edge case. AI-generated filter predicates are especially prone to off-by-one and inverted logic.

## Ralph Loop (ralph-loop skill)

- **Windows caveat:** The `setup-ralph-loop.sh` script fails when the prompt contains special characters like `(`, `×`, `&` — bash eval syntax errors. Keep ralph loop prompts to plain ASCII only.
- **Invocation:** Use `Skill` tool with `skill: "ralph-loop:ralph-loop"` and a simple `args` string (no parens, no unicode). Example: `args: "Implement the guided workout feature per the plan in docs/plans/2026-03-01-preset-workout-profiles.md - run tests after each task - output DONE when all pass --completion-promise DONE"`
- **Not suitable for:** UX design decisions, brainstorming, tasks requiring human judgment. Use `superpowers:brainstorming` for those first.
- **Good for:** Well-defined TDD implementation tasks where the plan already specifies exact file changes and test commands.
