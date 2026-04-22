# CLAUDE.md

Guidance for Claude Code working in this repo. Keep this file under ~200 lines; deep invariants live in `docs/claude-rules/*.md` and are loaded on-demand (see **Rule Index** below).

## Project

Cardea — Android (Kotlin + Compose) real-time HR zone coaching during runs. BLE HR (Coospo H808S), GPS distance, audio alerts when HR drifts outside zones. Adaptive learning engine models pace-HR over time.

## Build & Test

```bash
./gradlew assembleDebug
./gradlew test
./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.ZoneEngineTest"
./gradlew connectedAndroidTest
./gradlew lint
```

**Requirements:** JDK 17, compileSdk 35. `MAPS_API_KEY=...` in `local.properties` (falls back to `local.defaults.properties`).

**Safe APK reinstall:** `adb install -r <apk>` — preserves Room DB + DataStore. Do NOT use `mobile_install_app` (unknown replace vs uninstall semantics).

**mobile-mcp taps:** Always call `mobile_list_elements_on_screen` first — screenshot pixels ≠ screen coords (device 1080×2340).

**KSP red herring:** `kspDebugKotlin` "Internal compiler error: Storage for file-to-id.tab already registered" masks the real error. Re-run with `--stacktrace` to surface `e:` lines.

## Architecture

MVVM + Foreground Service, three layers:

```
Compose UI (screens + ViewModels)
    ↕ StateFlow
WorkoutForegroundService (orchestrator)
    BleHrManager | GpsDistanceTracker | ZoneEngine | AlertPolicy | CoachingEventRouter | AdaptivePaceController
    ↓
Room + repositories (WorkoutRepository, WorkoutMetricsRepository, AdaptiveProfileRepository, ...)
```

- **WorkoutState** (`service/WorkoutState.kt`) — singleton `StateFlow` shared between service and UI.
- **WorkoutForegroundService** — orchestrator; combines BLE+GPS, evaluates zones, fires alerts, writes track points every 5s.
- **Workout modes:** `STEADY_STATE`, `DISTANCE_PROFILE`, `FREE_RUN`.
- **AdaptivePaceController** — per-workout, not singleton. Persists via `AdaptiveProfileRepository`.
- Packages: `data/db/` (Room), `data/repository/`, `domain/model/`, `domain/engine/`, `service/` (+ `service/workout/`, `service/audio/`), `ui/` (screens + `components/`, `theme/`, `navigation/`, `charts/`), `di/` (Hilt).
- DI: `HrCoachApp` = `@HiltAndroidApp`; `MainActivity` + `WorkoutForegroundService` = `@AndroidEntryPoint`; VMs `@HiltViewModel`.
- Room tables: `workouts`, `track_points` (FK CASCADE), `bootcamp_enrollments`, `bootcamp_sessions` (FK CASCADE), `achievements`, `workout_metrics`. `targetConfig` is JSON.

## Navigation

Four-tab bottom bar: **Home**, **Workout** (setup or bootcamp depending on enrollment), **History** (also Progress), **Account**. Start is `home` after splash. Active workout hides bottom bar. Auto-navigates to workout when service starts, to postrun on end. `onDone` from postrun → bootcamp dashboard (bootcamp) or history detail (freestyle).

- **Bootcamp session identity contract:** `prepareStartWorkout()` resolves DB session ID and sets `WorkoutState.pendingBootcampSessionId` immediately. `onBootcampWorkoutStarting()` is fallback only. Do NOT use "first uncompleted" heuristic.
- **Simulation permission bypass:** `PermissionGate.hasAllRuntimePermissions` checks skipped when `SimulationController.isActive`. Both call sites (SetupScreen + BootcampScreen `onStartWorkout`) have this.

## Distance Unit

- `DistanceUnit` (`domain/model/`) — enum `KM`/`MI` + conversions. Single source of truth.
- **Storage always meters;** convert at display via `metersToUnit()`.
- **`formatPace(paceMinPerKm, unit)`** always takes **min/km** and converts internally. Never pass pre-converted min/mi — causes double conversion.
- **Mile splits** fire every 1609m — threshold `DistanceUnit.METERS_PER_MILE` in `CoachingEventRouter`.

## DataStore / Slider

Never call DataStore `edit {}` inside `onValueChange` (fires hundreds of times/s). Persist in `onValueChangeFinished`; update in-memory `StateFlow` in `onValueChange` for smooth UI.

## Ralph Loop

Windows: `setup-ralph-loop.sh` fails on `(`, `×`, `&` — plain ASCII prompts only. Good for well-defined TDD tasks with explicit file changes + test commands. Not for UX/brainstorming (use `superpowers:brainstorming` first).

## MCP Servers

Registered in `.mcp.json`. **mobile-mcp** for on-device UI verification (prereq: `adb devices`). **GitHub MCP** for PRs/CI status — requires PAT with `repo`/`read:org`/`workflow`. **Firebase MCP** for RTDB. Detailed playbooks in `docs/claude-rules/mobile-mcp.md` and `docs/claude-rules/github-mcp.md`.

---

## Rule Index — load on-demand via Read

These files are **not** auto-loaded (deliberate — keeps per-session context slim). When a task matches a trigger, Read the file before making changes.

| File | Load when touching… |
|---|---|
| [`docs/claude-rules/ui-theme.md`](docs/claude-rules/ui-theme.md) | anything under `ui/`, `ui/theme/`, Compose composables, gradients, splash, charts, or font/sizing choices |
| [`docs/claude-rules/audio-pipeline.md`](docs/claude-rules/audio-pipeline.md) | `service/audio/`, `AlertPolicy`, `CoachingEventRouter`, audio settings plumbing, TTS/earcon behaviour |
| [`docs/claude-rules/wfs-lifecycle.md`](docs/claude-rules/wfs-lifecycle.md) | `WorkoutForegroundService`, `WorkoutState`, notification helper, auto-pause, or anything called from `processTick`/`onHrTick` |
| [`docs/claude-rules/adaptive-engine.md`](docs/claude-rules/adaptive-engine.md) | `AdaptivePaceController`, `FitnessSignalEvaluator`, `SubMaxHrEstimator`, `PhaseEngine`, `MetricsCalculator`, `AdaptiveProfile*`, any physiological constant |
| [`docs/claude-rules/bootcamp-scheduling.md`](docs/claude-rules/bootcamp-scheduling.md) | `BootcampRepository`, `BootcampViewModel`, `BootcampScreen`, `SessionSelector`, `PhaseEngine`, `BootcampSessionCompleter`, session generation |
| [`docs/claude-rules/firebase-rtdb.md`](docs/claude-rules/firebase-rtdb.md) | `CloudBackupManager`, Firebase rules, partner/invite flows, App Distribution, `/users/{uid}/backup` paths |
| [`docs/claude-rules/adb-data-backup.md`](docs/claude-rules/adb-data-backup.md) | **critical — data loss risk.** Any time you back up, restore, or pull the Room DB via adb |
| [`docs/claude-rules/error-handling.md`](docs/claude-rules/error-handling.md) | coroutine code (especially Firebase), `runCatching`/`withTimeout` wrapping, `stopWorkout`, `collectAsState` |
| [`docs/claude-rules/worktree-git.md`](docs/claude-rules/worktree-git.md) | working inside a git worktree, merging back to main, rebasing, or worktree-specific build issues |
| [`docs/claude-rules/test-fakes.md`](docs/claude-rules/test-fakes.md) | adding DAO methods, writing/fixing unit tests, investigating pre-existing lint errors |
| [`docs/claude-rules/mobile-mcp.md`](docs/claude-rules/mobile-mcp.md) | driving the emulator/device via `mcp__mobile-mcp__*` tools |
| [`docs/claude-rules/github-mcp.md`](docs/claude-rules/github-mcp.md) | PR/issue lifecycle management via `mcp__github__*` tools |

**Loading rule:** if unsure whether a task touches a rules file's domain, prefer to Read it. Cost is small; acting on stale or missing invariants (e.g. the TRIMP formula, the pause-race guards, adb CR/LF corruption) can corrupt data or regress safety-critical behaviour.
