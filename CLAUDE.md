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

**Worktree builds:** `local.properties` is gitignored and won't exist in worktrees. Copy from the main repo: `cp ../../local.properties .` (or from project root).

**Safe APK reinstall:** Use `adb install -r <apk-path>` — preserves Room DB and DataStore. Do NOT use `mobile_install_app` (unknown whether it does a replace or full uninstall).

**mobile-mcp taps:** Always call `mobile_list_elements_on_screen` to get exact device screen coordinates before tapping. Screenshot pixels ≠ screen coordinates (device is 1080×2340).

**Worktree build path-length issue (Windows):** KSP/AAPT can fail in worktrees due to Windows path-length limits on deeply-nested build directories (`.claude/worktrees/<name>/app/build/...`). Workaround: run tests from the main repo after copying changed files. **Do NOT use `subst` to map a drive letter** — the compiler writes back through the mapped path and silently reverts edits made via the original path. Prefer the copy-to-main-repo approach.

**Worktree merge with dirty main:** If `main` has unstaged changes when merging a worktree branch, use `git stash push -m "..."` → `git merge --ff-only` → `git stash pop`. Auto-merge usually resolves cleanly when touching the same line.

**`git worktree remove` permission denied:** Fails if shell cwd is inside the worktree being removed. `cd` to the repo root or any outside directory first. Use `git worktree prune` to clean stale registrations (leaves directories on disk but removes git tracking).

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
- **Audio stream** — All audio components use `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` to layer over music without requesting audio focus. Do NOT change to `NOTIFICATION_EVENT` — it would be ducked by music. `ToneGenerator` is only used for pause/resume feedback tones.

## Key Packages

- `data/db/` — Room database (`AppDatabase`), entities (`WorkoutEntity`, `TrackPointEntity`), DAOs
- `data/repository/` — `WorkoutRepository`, `WorkoutMetricsRepository`, `AdaptiveProfileRepository`, `AudioSettingsRepository`, `MapsSettingsRepository`, `BootcampRepository`, `AutoPauseSettingsRepository`, `OnboardingRepository`, `ThemePreferencesRepository`, `UserProfileRepository`
- `domain/model/` — Domain models: `WorkoutConfig`, `WorkoutMode`, `AdaptiveProfile`, `ZoneStatus`
- `domain/engine/` — `ZoneEngine` (static zone eval), `AdaptivePaceController` (predictive HR-pace modeling)
- `service/` — `WorkoutForegroundService`, `BleHrManager`, `GpsDistanceTracker`, `AlertManager`, `WorkoutState`
- `service/audio/` — `CoachingAudioManager`, `EarconPlayer`, `VoicePlayer`, `StartupSequencer`, `VoiceEventPriority`, `VibrationManager`, `EscalationTracker`
- `ui/account/` — Account & settings screen + ViewModel; includes Maps API key + audio settings
- `ui/bootcamp/` — Bootcamp dashboard, setup flow, settings, day-state logic
- `ui/components/` — Shared composables: `CardeaLogo`, `GlassCard`
- `ui/debug/` — Debug/diagnostic screens
- `ui/emblem/` — Achievement emblem composables
- `ui/history/` — History list + detail with Google Maps route heatmap
- `ui/home/` — Home dashboard screen + ViewModel
- `ui/navigation/` — `NavGraph.kt` (function: `HrCoachNavGraph`) with routes: `home`, `setup`, `workout`, `progress`, `history`, `history/{workoutId}`, `postrun/{workoutId}`, `account`
- `ui/onboarding/` — First-run onboarding flow
- `ui/postrun/` — Post-workout summary screen
- `ui/progress/` — Training progress/stats screens
- `ui/setup/` — Workout setup screen + ViewModel (config, BLE scanning); maps to "Workout" nav tab
- `ui/splash/` — Branded splash screen
- `ui/workout/` — Active workout display
- `di/` — Hilt `AppModule` providing Room database and DAOs

## DI & Entry Points

Hilt is the DI framework. `HrCoachApp` is `@HiltAndroidApp`. `MainActivity` and `WorkoutForegroundService` are `@AndroidEntryPoint`. ViewModels use `@HiltViewModel`. `AppModule` provides singleton-scoped Room database and DAOs.

## Database

Room database `hr_coach_db` with tables: `workouts`, `track_points` (FK to workout, CASCADE delete), `bootcamp_enrollments`, `bootcamp_sessions` (FK to enrollment, CASCADE delete), `achievements`, `workout_metrics`. `targetConfig` column stores workout zone configuration as JSON.

## Navigation

Four-tab bottom bar: **Home**, **Workout** (setup or bootcamp, depending on enrollment), **History** (also covers Progress), **Account**. Start destination after splash is `home`. Active workout screen hides the bottom bar. Navigation auto-transitions to workout screen when service starts; when workout ends it navigates to post-run summary (or back to `setup` if no completed workout ID). `onDone` from post-run summary navigates to **bootcamp dashboard** (bootcamp runs) or **history detail** (freestyle runs).

**Bootcamp session identity contract:** When the user taps "Start Run" on a bootcamp session, `prepareStartWorkout()` resolves the DB session ID and stores it in `WorkoutState.pendingBootcampSessionId` immediately. `onBootcampWorkoutStarting()` is a fallback only. Do NOT use heuristic "first uncompleted" matching — it picks the wrong session when sessions are started out of order.

**Simulation permission bypass:** NavGraph permission checks (`PermissionGate.hasAllRuntimePermissions`) are skipped when `SimulationController.isActive`. Sim workouts don't use BLE or GPS hardware. Both check sites (SetupScreen and BootcampScreen `onStartWorkout`) have this bypass.

## UI & Theme

- **Cardea design system** — Dark glass-morphic theme. Background `#050505` (radial gradient with `#0D0D0D`). All design tokens are in `ui/theme/Color.kt` as named constants. See `docs/plans/2026-03-02-cardea-ui-ux-design.md` for the authoritative spec. **Token drift rule: if the spec conflicts with `Color.kt`, the spec is wrong — fix the spec, not the code.**
- **`CardeaTheme`** — Primary theme function. `HrCoachTheme` is a backward-compat wrapper. `HrCoachThemeTokens` is a `typealias` for `CardeaThemeTokens`. Dynamic color is `false` — Cardea palette is always enforced. The app supports System/Light/Dark modes via the in-app Theme selector (`AccountScreen`); light mode is a valid user preference — do not flag it as a bug.
- **Three gradient variants — use the right one:**
  - `CardeaGradient` (4-stop, `#FF4D5A→#FF2DA6→#4D61FF→#00E5FF`, 135°) — ring foregrounds, gradient text, Tier 1 accent borders only. Do NOT alter the stops.
  - `CardeaCtaGradient` (Red→Pink, 2-stop) — ALL buttons, active chips, selection indicators, and day-selection dots. Audited 2026-04-11: all bootcamp/setup/onboarding chips now correct. Only ~3 legitimate `CardeaGradient` uses remain (progress bars, decorative arcs). Run `search_for_pattern "CardeaGradient"` before adding new ones.
  - `CardeaNavGradient` (Blue→Cyan, 2-stop) — active nav icons only.
- **One gradient accent per screen** — the 3-tier hierarchy means exactly one composable per screen carries the gradient (text, border, or CTA button). A ring AND a button AND a tile all lit simultaneously is wrong. BootcampTile ring is plain white (0.55α), not gradient. **3-tier:** Tier 1 = gradient, 18dp corners; Tier 2 = white on glass, 14dp corners; Tier 3 = secondary text on glass, 12dp corners.
- **Glass surface pattern** — `GlassBorder = Color(0x1AFFFFFF)` (10% alpha), `GlassHighlight = Color(0x0AFFFFFF)`. Use `GlassCard` composable from `ui/components/GlassCard.kt` for all card surfaces.
- **`GlassCard.containerColor`** — previously silently ignored; now wired up (2026-04-09). Pass `containerColor = SomeColor.copy(alpha = 0.06f)` for subtle tinted-fill states (e.g. selected PresetCard). Also pass `borderColor = Color.Transparent` when using an outer `Modifier.border(brush=…)` to avoid double-borders.
- **`CardeaTextTertiary` is `#6B6B73`** — raised from `#52525B` (2026-04-09) for WCAG AA compliance on `#050505` background. Downstream aliases `DisabledGray`, `ThresholdLine` auto-update.
- **Zone status composables** — pills, indicators showing HR zone must be **minimum 12sp** with adequate padding. Zone info is safety-critical and must be readable at a glance mid-run. Do not style as decorative micro-labels.
- **`CardeaLogo`** — Canvas-drawn composable in `ui/components/CardeaLogo.kt`. Heart + ECG line + orbital ring with gradient fill. Two sizes: `LogoSize.LARGE` (splash, 180dp) and `LogoSize.SMALL` (nav badge, 32dp).
- **Gradient nav icons** — Active nav icons use `CompositingStrategy.Offscreen` + `BlendMode.SrcIn` with `CardeaNavGradient` to produce pixel-perfect gradient fill on any `ImageVector` icon.
- **Charts are custom Canvas-drawn** — `ui/charts/` (BarChart, PieChart, ScatterPlot) use `DrawScope` directly; no charting library. Styling changes require Canvas API edits.
- **`WorkoutSnapshot` has no elapsed time** — compute elapsed seconds in the ViewModel via a ticker flow when `isRunning && !isPaused`.
- **Maps settings** — Moved from a dialog in SetupScreen to `AccountScreen`. `SetupScreen` no longer contains any Maps API key UI.
- **`CardeaSlider` uses `GradientPink`** — thumb and active track are pink (matching CTA accent), not blue. Changed from `GradientBlue` (2026-04-11) to distinguish from Material3 defaults. Bootcamp sliders also use `GradientPink` directly.
- **Home screen gradient hierarchy** — `PulseHero` is the sole Tier 1 gradient element (gradient text headline). `GoalTile` is Tier 2 (glass border, white text). `BootcampTile` progress bar uses `ctaGradient`. `VolumeTile` progress bars use subtle inline gradient. Do not add gradient borders or gradient text to the stat tiles.

## Design Documents

- **Authoritative spec:** `docs/plans/2026-03-02-cardea-ui-ux-design.md` — unified 2026-04-09; tokens now match `Color.kt` exactly.
- **Design review mockup:** `docs/mockups/cardea-design-review.html` — HTML phone-frame mockups of Home/Workout/Account with token reference, type scale, and inline critique. Open in browser for visual reference.
- **Implementation plan:** `docs/plans/2026-03-02-cardea-ui-ux-plan.md`
- **Guided workouts UX design:** `docs/plans/2026-03-02-guided-workouts-ux-design.md` — Approach B: Cardea glass preset cards, segment timeline strip, HRmax onboarding, interval countdown.
- **Guided workouts implementation plan:** `docs/plans/2026-03-01-preset-workout-profiles.md` — 12-task TDD plan; Tasks 1–2 already done in commit fd3d9d9.
- Legacy: `docs/plans/2026-02-25-hr-coaching-app-design.md` — superseded; data model and alert behavior sections still valid, UI/UX sections replaced by the 2026-03-02 spec.
- **E2E happy path audit:** `docs/2026-04-11-e2e-happy-path-audit.md` — device-tested findings: bugs, design violations, UX observations, test coverage matrix.

## Audio Pipeline

Three-component layered audio system in `service/audio/`:

- **`EarconPlayer`** — SoundPool, plays short WAV clips for zone alerts. `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`.
- **`VoicePlayer`** — Android TTS for all spoken coaching: workout briefings, zone alerts (using adaptive guidance text), km splits, and informational cues. Priority-gated via `VoiceEventPriority` (CRITICAL > NORMAL > INFORMATIONAL). Uses `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` to layer over music.
- **`StartupSequencer`** — MediaPlayer playing `countdown_321_go.wav` (custom marimba 3-2-1-GO). Updates `WorkoutState.countdownSecondsRemaining` for UI countdown display.
- **`CoachingAudioManager`** — Orchestrator. Startup sequence: TTS briefing (await) → countdown WAV (await) → return (timer starts). `VoiceVerbosity.OFF` gates ALL audio (earcons + voice). `shouldPlayEarcon(verbosity)` is the single gate for earcon playback.
- **Pause/resume tones are exempt from verbosity gating** — `playPauseFeedback()` always plays regardless of `VoiceVerbosity` setting. These are safety-critical (runner needs to know autopause activated/deactivated).
- **`AudioSettings`** — `earconVolume` and `voiceVolume` are independent (both 0–100 int percent).
- **Verbosity levels:** OFF (silent), MINIMAL (earcons + voice for critical/normal events only), FULL (earcons + voice for all events including informational).
- **KM splits:** Simple "Kilometer N" for STEADY_STATE/DISTANCE_PROFILE. Rich "Kilometer N. Pace: X minutes Y." for FREE_RUN.

## Notification Stop Gate

`WorkoutNotificationHelper.stop()` must be called before every `stopForeground(STOP_FOREGROUND_REMOVE)` call. Without it, a late `processTick()` notification update races past `stopForeground()` and re-posts the notification. The `@Volatile stopped` flag short-circuits `update()`. Three call sites in `WorkoutForegroundService`: normal stop, short-run discard, and error handler.

## WorkoutState / Same-Tick Race Pattern

Never read `WorkoutState.snapshot.value` in the same function that called `WorkoutState.update{}` and expect the new value — `MutableStateFlow.update` may not have propagated yet. Use a local variable for same-tick decisions. Fixed in `onHrTick()` for autopause (2026-04-10).

## DataStore / Slider Pattern

Never call DataStore `edit {}` inside a slider's `onValueChange` — it fires on every drag frame (hundreds of times). Use `onValueChangeFinished` for persistence; update in-memory `StateFlow` in `onValueChange` for smooth UI.

## Recent Architectural Changes (2026-04-11)

- **`PhaseEngine.isRecoveryWeek()`** accepts optional `TuningDirection`. Cadence: EASE_BACK=2w, HOLD/null=3w, PUSH_HARDER=4w. `weeksUntilNextRecovery()` simulates phase transitions (walks through `advancePhase()` boundaries); for EVERGREEN it computes distance to week D of the 4-week micro-cycle. All call sites in `BootcampViewModel` pass `fitnessSignals.tuningDirection`.
- **`FitnessSignalEvaluator.efTrend`** uses least-squares regression slope scaled to total span (not endpoint delta). More robust against single-session outliers. Threshold (0.04) is unchanged.
- **`PostRunSummaryViewModel`** now injects `AdaptiveProfileRepository` and forwards `lastTuningDirection` to `bootcampSessionCompleter.complete()`. Previously defaulted to HOLD.
- **HRmax fallback** in `WorkoutForegroundService` is now `220 - age` when age is known (via `userProfileRepository.getAge()`), falling back to 180 only when age is also null.
- **hrMax dual-store sync is complete** — all 6 write sites (Service, Setup, Bootcamp, BootcampSettings, PostRunSummary, Onboarding) now sync to both `UserProfileRepository` and `AdaptiveProfileRepository`. No gaps remain.
- **MainActivity permission handling** — `registerForActivityResult` callback handles permanent denial (Settings redirect) and temporary denial (Toast). `PermissionGate.missingRuntimePermissions()` checked in `onCreate`.

## Error Handling & Crash Resilience (audited 2026-04-11)

- **Audit plan:** `docs/superpowers/plans/2026-04-11-error-handling-audit.md` — health scorecard + deferred items
- **stopWorkout() essential/best-effort split** — essential ops (save workout, stop GPS/BLE) wrapped in individual `runCatching`; best-effort ops (metrics, achievements) grouped separately. Both log on failure.
- **All `collectAsState()` calls are now lifecycle-aware** — `collectAsStateWithLifecycle()` used everywhere.
- **Deferred (not yet fixed):** Per-operation BLE permission checks (8 `@SuppressLint`), mid-session permission revocation, Room migration tests, full state restoration (`START_REDELIVER_INTENT`).

## Bootcamp Scheduling Architecture

- **`TrainingPhase` enum:** BASE, BUILD, PEAK, TAPER, EVERGREEN. Race goals use BASE→BUILD→PEAK→TAPER; CARDIO_HEALTH uses BASE→EVERGREEN.
- **EVERGREEN phase:** Perpetual 4-week micro-cycle (A: Tempo, B: Strides, C: Intervals/Hills tier 2+, D: Recovery). Wraps to itself on `advancePhase()` — never graduates.
- **`SessionSelector.weekSessions()`** accepts `weekInPhase`, `absoluteWeek`, and `isRecoveryWeek` params (used by EVERGREEN rotation and tier-dependent recovery composition).
- **Interval variety:** PEAK and EVERGREEN alternate presets based on `absoluteWeek % 2` (norwegian_4x4/hill_repeats for race goals; hill_repeats/hiit_30_30 for EVERGREEN).
- **Recovery week composition:** Tier 0-1 get all-easy weeks; Tier 2+ get downgraded quality (interval→tempo, tempo→strides). EVERGREEN handles its own recovery on week D.
- **Tempo presets** (`aerobic_tempo`, `lactate_threshold`) use `DISTANCE_PROFILE` with 10-min warm-up, 20-min main block, 5-min cool-down segments.

## Adaptive Engine Invariants

- **`AdaptivePaceController` is per-workout, not a singleton** — new instance created each workout; state (`sessionBuckets`, settle lists, `lastProjectedHr`) resets.
- **`hrSlopeBpmPerMin` clamp** — `instSlope` is clamped to `±30 BPM/min` before EMA blend. Do not widen; BLE at 1 Hz with 3 s minimum window can produce 800 BPM/min from a single glitch.
- **TRIMP formula is non-standard** — uses `duration * avgHR * (avgHR/HRmax)^2`, not Bannister's exponential. Consistent across the codebase; don't "fix" it to match literature values.
- **Three `bootcampSessionCompleter.complete()` call sites:** (1) PostRunSummaryViewModel (reads lastTuningDirection from AdaptiveProfileRepository), (2) WorkoutForegroundService sim path (reads from saved profile), (3) BootcampViewModel.onWorkoutCompleted (reads from UI state). All three pass tuningDirection.
- **`efTrend` regression math** — slope is `(n*sumXY - sumX*sumY) / (n*sumX2 - sumX*sumX)`, then multiplied by `(n-1)` to give total estimated change across the window. Comparable to the old endpoint delta and the existing 0.04 threshold.

## Known Pre-existing Lint Errors (do not treat as regressions)

`BleHrManager.kt` MissingPermission, `WorkoutForegroundService.kt` MissingSuperCall, `NavGraph.kt` NewApi (×2), `build.gradle.kts` WrongGradleMethod — all pre-date this codebase's Claude sessions.

## Ralph Loop (ralph-loop skill)

- **Windows caveat:** The `setup-ralph-loop.sh` script fails when the prompt contains special characters like `(`, `×`, `&` — bash eval syntax errors. Keep ralph loop prompts to plain ASCII only.
- **Invocation:** Use `Skill` tool with `skill: "ralph-loop:ralph-loop"` and a simple `args` string (no parens, no unicode). Example: `args: "Implement the guided workout feature per the plan in docs/plans/2026-03-01-preset-workout-profiles.md - run tests after each task - output DONE when all pass --completion-promise DONE"`
- **Not suitable for:** UX design decisions, brainstorming, tasks requiring human judgment. Use `superpowers:brainstorming` for those first.
- **Good for:** Well-defined TDD implementation tasks where the plan already specifies exact file changes and test commands.

## MCP Servers

Two MCP servers are registered in `.mcp.json`. All are manual (not plugin-managed).

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

### Firebase (Realtime Database rules & deploy)

**App Distribution:** `./gradlew assembleDebug appDistributionUploadDebug` — builds debug APK and uploads to Firebase App Distribution. Testers are managed via the `testers` group in Firebase console (not hardcoded). `firebase login --reauth` requires an interactive terminal if auth expires.

**Versioning:** `versionCode` increments by 1 each release, `versionName` uses semver (`0.x.0` during pre-release). Both live in `app/build.gradle.kts` `defaultConfig`. Release notes are in `debug { firebaseAppDistribution { releaseNotes = "..." } }`.

**Deploy:** `firebase deploy --only database --project cardea-1c8fc` — no `.firebaserc` exists, always pass `--project`. Project ID also in `app/google-services.json`.

**Security rules — validate scope:** Use `$wildcardVar !== $uid` (not `$wildcardVar !== auth.uid`) when the constraint is about node identity. `auth.uid` breaks bidirectional writes where the caller writes their own UID as a key under another user's node.

### Figma MCP (design → code, code → design)

Connect via the Cowork registry (OAuth, no API key needed). Once connected, it enables:
- Pulling a Figma frame directly into Compose code generation
- Reading exact design tokens, component specs, and Auto Layout constraints
- Supercharges the `design` plugin's handoff and critique skills

**Not yet connected** — use the Connect button in Cowork's connector panel to authenticate.
