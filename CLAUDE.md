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
- **`CardeaGradient`** — `Brush.linearGradient` with exact 4-stop color stops (do NOT alter). Access via `HrCoachThemeTokens.gradient` in composables. **3-tier usage hierarchy:** Tier 1 = gradient text/borders for single most important metric per screen (18dp corners); Tier 2 = white on glass for supporting metrics (14dp corners); Tier 3 = secondary text on glass for ambient info (12dp corners). Gradient also used for primary CTA button and active nav icons. Do NOT apply gradient to every element — "colour pops not colour vomit".
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

## Ralph Loop (ralph-loop skill)

- **Windows caveat:** The `setup-ralph-loop.sh` script fails when the prompt contains special characters like `(`, `×`, `&` — bash eval syntax errors. Keep ralph loop prompts to plain ASCII only.
- **Invocation:** Use `Skill` tool with `skill: "ralph-loop:ralph-loop"` and a simple `args` string (no parens, no unicode). Example: `args: "Implement the guided workout feature per the plan in docs/plans/2026-03-01-preset-workout-profiles.md - run tests after each task - output DONE when all pass --completion-promise DONE"`
- **Not suitable for:** UX design decisions, brainstorming, tasks requiring human judgment. Use `superpowers:brainstorming` for those first.
- **Good for:** Well-defined TDD implementation tasks where the plan already specifies exact file changes and test commands.

## MCP Servers

Three MCP servers are registered in `.mcp.json`. All are manual (not plugin-managed).

### Serena (semantic Kotlin/LSP code search)

Serena is registered via `.mcp.json` with `--context claude-code` and explicit `--project` path. See `.claude/rules/serena.md` for when to use Serena vs Grep.

**If the LSP fails** ("language server manager is not initialized"):
1. Call `restart_language_server`, then verify with a real symbol operation
2. If still broken, fall back to Grep/Glob — don't spin on a broken LSP
3. `activate_project` can return false success — always verify after calling it
4. Never silently switch to Grep — note that Serena was unavailable

### mobile-mcp (Android device / emulator automation)

`mobile-mcp` (`@mobilenext/mobile-mcp`) replaces the old `android-debug-bridge-mcp`. It provides:
- **Screenshots** of the running app on device or emulator
- **Tap / swipe / type** interactions with UI elements
- **Accessibility tree snapshots** — structured view of what's on screen
- **App launch / install / clear** operations
- **ADB logcat** access

**When to use:** Any time a feature or UI change needs visual verification on device — take a screenshot after building and installing to confirm the composable renders correctly. See `.claude/rules/mobile-mcp.md` for playbook.

**Prerequisite:** An emulator must be running or a physical device connected via USB with developer mode enabled. Verify with `adb devices` before invoking mobile-mcp tools.

### GitHub MCP (repository & PR management)

Registered in `.mcp.json`. **Requires a GitHub Personal Access Token (PAT)** — replace the `REPLACE_WITH_YOUR_GITHUB_PAT` placeholder in `.mcp.json` before the server will connect.

**How to create a PAT:** GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic). Scopes needed: `repo`, `read:org`, `workflow`.

**When to use:**
- Creating PRs with proper descriptions after implementing a feature
- Checking GitHub Actions CI status after a push
- Searching issues/PRs for prior decisions or related work
- Creating issues from discovered bugs

See `.claude/rules/github-mcp.md` for tool reference.

### Figma MCP (design → code, code → design)

Connect via the Cowork registry (OAuth, no API key needed). Once connected, it enables:
- Pulling a Figma frame directly into Compose code generation
- Reading exact design tokens, component specs, and Auto Layout constraints
- Supercharges the `design` plugin's handoff and critique skills

**Not yet connected** — use the Connect button in Cowork's connector panel to authenticate.
