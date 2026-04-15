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

**Worktree builds:** `local.properties` is gitignored and won't exist in worktrees. Copy from the main repo: `cp ../../../local.properties .` (worktrees live at `.claude/worktrees/<name>/`, so three levels up).

**Safe APK reinstall:** Use `adb install -r <apk-path>` — preserves Room DB and DataStore. Do NOT use `mobile_install_app` (unknown whether it does a replace or full uninstall).

**mobile-mcp taps:** Always call `mobile_list_elements_on_screen` to get exact device screen coordinates before tapping. Screenshot pixels ≠ screen coordinates (device is 1080×2340).

**Worktree build path-length issue (Windows):** KSP/AAPT can fail in worktrees due to Windows path-length limits on deeply-nested build directories (`.claude/worktrees/<name>/app/build/...`). Workaround: run tests from the main repo after copying changed files. **Do NOT use `subst` to map a drive letter** — the compiler writes back through the mapped path and silently reverts edits made via the original path. Prefer the copy-to-main-repo approach.

**KSP "Internal compiler error" masking:** When `kspDebugKotlin` or `compileDebugKotlin` fails with "Internal compiler error: Storage for file-to-id.tab already registered", this is a red herring. Run with `./gradlew assembleDebug --stacktrace` to surface the real `e:` compile errors underneath (e.g. unresolved references).

**Worktree merge with dirty main:** If `main` has unstaged changes when merging a worktree branch, use `git stash push -m "..."` → `git merge --ff-only` → `git stash pop`. Auto-merge usually resolves cleanly when touching the same line.

**Worktree merge with rewritten-file stash conflicts:** the `stash push → ff-merge → stash pop` procedure fails badly when (a) the popped WIP touches a file the incoming branch *rewrote* (not just tweaked), or (b) the popped WIP references untracked files not yet committed. Attempting to merge both versions produces a file that won't build on a fresh clone (references to untracked symbols). Recovery: `git checkout HEAD -- <conflicted-file>` to restore the merged version, `git reset HEAD` to unstage the popped index, `git stash drop` to discard the orphan stash entry. The WIP from the other session then remains as unstaged working-tree changes exactly as it was pre-merge, minus the orphan refactor tweak.

**Worktree branch diverged behind main:** If `main` received commits while a worktree was open, `--ff-only` fails with "Diverging branches." Fix: in the worktree, `git rebase main` → then in the main repo, `git merge --ff-only <branch>`. Stash main WIP first if needed. Do both steps back-to-back — if main gets another commit between the rebase and the merge, `--ff-only` fails again and a second rebase is needed.

**`git rebase --continue --no-edit` is invalid:** `--no-edit` is not a recognised flag for `git rebase --continue` and causes a hard error. Omit it; the commit message from the original commit is preserved automatically.

**`GIT_EDITOR=true git rebase --continue`** — `--no-edit` is NOT a valid flag for `git rebase --continue` and hard-errors. Use `GIT_EDITOR=true git rebase --continue` to accept the auto-generated message non-interactively.

**Untracked files block `git merge`:** If the incoming branch creates a file already present as untracked in the working tree, git refuses to merge. `rm` the untracked copy first, then merge.

**`git worktree remove` permission denied:** Fails if shell cwd is inside the worktree being removed. `cd` to the repo root or any outside directory first. Use `git worktree prune` to clean stale registrations (leaves directories on disk but removes git tracking).

**Build requirements:** JDK 17, Android SDK with compileSdk 35. Google Maps API key goes in `local.properties` as `MAPS_API_KEY=...` (falls back to `local.defaults.properties` placeholder).

## Architecture

**MVVM + Foreground Service** — three layers:

```
Compose UI (screens + ViewModels)
    ↕ StateFlow
WorkoutForegroundService (orchestrator)
    BleHrManager | GpsDistanceTracker | ZoneEngine | AlertPolicy | CoachingEventRouter | AdaptivePaceController
    ↓
Room Database + repositories (`WorkoutRepository`, `WorkoutMetricsRepository`, `AdaptiveProfileRepository`)
```

**Key architectural decisions:**

- **WorkoutState** (`service/WorkoutState.kt`) is a singleton `StateFlow` container shared between the foreground service and UI. All workout runtime state lives here — HR, zone status, distance, pace, guidance text.
- **WorkoutForegroundService** is the central orchestrator. It combines BLE HR and GPS flows, evaluates zones, triggers alerts, saves track points every 5s, and persists completed workouts to Room.
- **Three workout modes:** `STEADY_STATE` (single target HR ± buffer), `DISTANCE_PROFILE` (ordered distance segments each with a target HR), and `FREE_RUN` (no target, data collection only).
- **Adaptive learning** (`AdaptivePaceController`) tracks HR slope, pace-HR buckets, response lag, and trim offsets across sessions. Profile persists via `AdaptiveProfileRepository`.
- **Audio stream** — All audio components use `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` to layer over music without requesting audio focus. Do NOT change to `NOTIFICATION_EVENT` — it would be ducked by music. **Exception: `ToneGenerator` takes an `AudioManager.STREAM_*` constant** (not a `USAGE_*` constant) — use `AudioManager.STREAM_MUSIC`. The two constant spaces look similar but are completely different; passing a `USAGE_*` value as a stream type silently routes to the wrong channel.

## Key Packages

- `data/db/` — Room database (`AppDatabase`), entities (`WorkoutEntity`, `TrackPointEntity`), DAOs
- `data/repository/` — `WorkoutRepository`, `WorkoutMetricsRepository`, `AdaptiveProfileRepository`, `AudioSettingsRepository`, `MapsSettingsRepository`, `BootcampRepository`, `AutoPauseSettingsRepository`, `OnboardingRepository`, `ThemePreferencesRepository`, `UserProfileRepository`
- `domain/model/` — Domain models: `WorkoutConfig`, `WorkoutMode`, `AdaptiveProfile`, `ZoneStatus`
- `domain/engine/` — `ZoneEngine` (static zone eval), `AdaptivePaceController` (predictive HR-pace modeling)
- `service/` — `WorkoutForegroundService`, `BleHrManager`, `GpsDistanceTracker`, `WorkoutState`
- `service/workout/` — `AlertPolicy` (zone alert timing/cooldown), `CoachingEventRouter` (informational cues: splits, halfway, segment changes, predictive warnings), `WorkoutNotificationHelper`, `TrackPointRecorder`
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
- **Bootcamp gradient audit complete (2026-04-11)** — `CardeaGradient` is fully removed from `BootcampScreen.kt`. All progress bars, step indicators, and selection borders now use `CardeaCtaGradient`. Do NOT reintroduce the 4-stop gradient in bootcamp code.
  - `CardeaNavGradient` (Blue→Cyan, 2-stop) — active nav icons only.
- **One gradient accent per screen** — the 3-tier hierarchy means exactly one composable per screen carries the gradient (text, border, or CTA button). A ring AND a button AND a tile all lit simultaneously is wrong. BootcampTile ring is plain white (0.55α), not gradient. **3-tier:** Tier 1 = gradient, 18dp corners; Tier 2 = white on glass, 14dp corners; Tier 3 = secondary text on glass, 12dp corners.
- **Glass surface pattern** — `GlassBorder = Color(0x1AFFFFFF)` (10% alpha), `GlassHighlight = Color(0x0AFFFFFF)`. Use `GlassCard` composable from `ui/components/GlassCard.kt` for all card surfaces.
- **`GlassCard.containerColor`** — previously silently ignored; now wired up (2026-04-09). Pass `containerColor = SomeColor.copy(alpha = 0.06f)` for subtle tinted-fill states (e.g. selected PresetCard). Also pass `borderColor = Color.Transparent` when using an outer `Modifier.border(brush=…)` to avoid double-borders.
- **`GlassCard` semantic borders** — When adding colored tinted borders (e.g. `ZoneGreen.copy(alpha = 0.15f)`) via outer `Modifier.border()`, ALWAYS pass `borderColor = Color.Transparent` to GlassCard. Applies to conditional cards (Graduation, Illness, Missed) and selected-state cards (goal selection). Audited 2026-04-11.
- **`CardeaTextTertiary` is `#6B6B73`** — raised from `#52525B` (2026-04-09) for WCAG AA compliance on `#050505` background. Downstream aliases `DisabledGray`, `ThresholdLine` auto-update.
- **Zone status composables** — pills, indicators showing HR zone must be **minimum 12sp** with adequate padding. Zone info is safety-critical and must be readable at a glance mid-run. Do not style as decorative micro-labels.
- **Minimum font size** — Never use font sizes below 9sp. Material recommends 10sp minimum; 9sp is the floor for micro-labels (e.g. week strip type labels). The 7sp previously used in WeekDayPill was a readability failure — fixed 2026-04-11.
- **Typography scale completeness** — `HrCoachTypography` must define ALL styles used in the codebase. Undefined styles silently fall back to Material3 defaults (different font config). Added in 2026-04-11: `displaySmall` (34sp Bold), `headlineSmall` (22sp SemiBold), `titleSmall` (15sp SemiBold), `labelMedium` (12sp Medium). If adding a new text style, define it in `Type.kt` first.
- **`CardeaLogo`** — Canvas-drawn composable in `ui/components/CardeaLogo.kt`. Heart + ECG line + orbital ring with gradient fill. Two sizes: `LogoSize.LARGE` (splash, 180dp) and `LogoSize.SMALL` (nav badge, 32dp). Note: splash screen no longer uses `CardeaLogo` — it has its own Canvas animation.
- **Icon design — "Pure Signal" (2026-04-12)** — App icon is a single ECG P-QRS-T trace with no heart shape, bleeding off both edges of the frame. Gradient left→right: #FF4D5A→#FF2DA6→#4D61FF→#00E5FF. Background #0D0D14. QRS spike near-full-height, needle-sharp. Do NOT reintroduce a heart shape.
- **Launcher icon PNG generation** — Mipmaps at all densities generated via Python/PIL (`gen_cardea_icon.py`, run with `"/c/Python314/python.exe"`). Densities: mdpi=48, hdpi=72, xhdpi=96, xxhdpi=144, xxxhdpi=192. Both `ic_launcher.png` and `ic_launcher_round.png` required per density folder.
- **Splash screen architecture (2026-04-12)** — `SplashScreen.kt` is a full Canvas animation driven by a `withFrameMillis` loop — no `CardeaLogo`, no `animateFloatAsState`. Three phases: 1600ms draw-on | 500ms hold | 500ms fade-out. `onFinished()` fires after one full cycle. Do NOT add Compose animation APIs here — the frame loop drives everything.
- **Canvas glow in Compose** — For blurred glow passes use `drawIntoCanvas { canvas -> canvas.drawPath(path, Paint().also { it.asFrameworkPaint().maskFilter = BlurMaskFilter(...) }) }`. There is no Compose equivalent of CSS `filter: blur()` — `BlurMaskFilter` is the correct translation.
- **Capturing splash screen on device** — Splash completes before `mobile_take_screenshot` fires. Use: `adb shell am force-stop com.hrcoach && adb shell monkey -p com.hrcoach -c android.intent.category.LAUNCHER 1 >/dev/null & sleep 0.9 && adb shell screencap //data/local/tmp/s.png && adb pull //data/local/tmp/s.png /tmp/s.png`. The 0.9s sleep catches mid-draw-on.
- **Gradient nav icons** — Active nav icons use `CompositingStrategy.Offscreen` + `BlendMode.SrcIn` with `CardeaNavGradient` to produce pixel-perfect gradient fill on any `ImageVector` icon.
- **Charts are custom Canvas-drawn** — `ui/charts/` (BarChart, PieChart, ScatterPlot) use `DrawScope` directly; no charting library. Styling changes require Canvas API edits.
- **`hrPercentColor()` in SetupScreen** uses `ZoneRed`/`ZoneAmber`/`ZoneGreen` theme tokens. Do NOT reintroduce hardcoded hex colors for zone indicators — the tokens are light-mode aware.
- **Chart components are M3-free (purged 2026-04-12)** — All `ui/charts/` files use `CardeaTheme.colors` exclusively. Do NOT use `MaterialTheme.colorScheme` in chart code. `HrCoachThemeTokens.subtleText` is a legacy alias — use `CardeaTheme.colors.textSecondary` instead.
- **Canvas dp gotcha** — Custom `DrawScope` code must use `X.dp.toPx()` for sizes, not raw float pixels (`5f`). Raw pixels produce different visual sizes across screen densities.
- **`BarChart.color` is the fill color** — each caller passes a distinct color (`GradientRed`, `GradientBlue`, `GradientPink`). Solid fill with alpha modulation (latest bar = 1.0, others = 0.55). Do not reintroduce a hardcoded gradient.
- **`SectionHeader` (charts)** — uppercase `labelMedium` + `Bold` + `2.sp` letterSpacing + `textSecondary` color. Subtitle uses `textTertiary`. Subtle chapter break, not a prominent heading.
- **`FlowRow` requires `@OptIn(ExperimentalLayoutApi::class)`** — used in AccountScreen (voice mode tags) and may be needed elsewhere for wrapping chip layouts.
- **`WorkoutSnapshot` has no elapsed time** — compute elapsed seconds in the ViewModel via a ticker flow when `isRunning && !isPaused`.
- **Maps settings** — Moved from a dialog in SetupScreen to `AccountScreen`. `SetupScreen` no longer contains any Maps API key UI.
- **`CardeaSlider` uses `GradientPink`** — thumb and active track are pink (matching CTA accent), not blue. Changed from `GradientBlue` (2026-04-11) to distinguish from Material3 defaults. Bootcamp sliders also use `GradientPink` directly.
- **Segmented button accent is neutral** — `cardeaSegmentedButtonColors()` uses `textPrimary`/`textSecondary` (not `GradientBlue`). Changed 2026-04-12 to eliminate competing blue accent. Active segment stands out via white text + visible border, deferring to the CTA gradient hierarchy.
- **`SectionLabel` is 10sp** — used on AccountScreen and potentially other settings pages. At 10sp with `textTertiary` color, these are borderline for scannability. If redesigning, consider 11–12sp with wider letter spacing.
- **`DarkGlassFillBrush`** — 8%/4% white (was 6%/2%). Strengthened 2026-04-11 so GlassCards are visible against `#050505` background.
- **BootcampEntryCard CTA** — ghost/outlined button (`glassBorder` border, `textSecondary` text, no fill). Demoted from `CardeaCtaGradient` 2026-04-11 so "Start Run" is the sole gradient primary CTA on the Training tab.
- **Home screen gradient hierarchy** — `PulseHero` is the sole Tier 1 gradient element (gradient text headline). `GoalTile` is Tier 2 (glass border, white text). `BootcampTile` progress bar uses `ctaGradient`. `VolumeTile` progress bars use subtle inline gradient. Do not add gradient borders or gradient text to the stat tiles.
- **Material3 button text color leak** — `OutlinedButton` and `TextButton` default text color to `colorScheme.primary` (= `GradientBlue`). Always pass explicit `color = CardeaTheme.colors.textPrimary` (or `textSecondary` for tertiary actions) to `Text()` inside these buttons. `CardeaButton` is exempt (custom composable).
- **`OutlinedTextField` dark mode** — always set `focusedContainerColor`, `unfocusedContainerColor`, `focusedPlaceholderColor`, `unfocusedPlaceholderColor` in `OutlinedTextFieldDefaults.colors()`. Without them, placeholder and field boundary are nearly invisible on `#050505`. Use `Color(0x14FFFFFF)` focused / `Color(0x0AFFFFFF)` unfocused as container fill.
- **`TabRow` vs `ScrollableTabRow`** — for ≤3 fixed tabs always use `TabRow` (equal distribution). `ScrollableTabRow` left-aligns. Custom indicator: `Box(Modifier.tabIndicatorOffset(tabPositions[selected]).height(2.dp).background(GradientPink))` — in M3 BOM ≥ 2024.12.01 `tabIndicatorOffset` is a member extension of `TabRowDefaults`, not a top-level import. Use `import androidx.compose.material3.TabRowDefaults` and wrap with `with(TabRowDefaults) { … }`.
- **10sp minimum text size** — Established 2026-04-12. All user-facing text must be ≥ 10sp for WCAG readability on dark background. Known remaining violations: `HomeScreen.kt` (8sp line ~722, 9sp lines ~509/550/666/751), `BootcampSettingsScreen.kt` (9sp ~811), `CalendarHeatmap.kt` (9sp ~90), `MissionCard.kt` (9sp ~135).
- **`CardeaButton` default `innerPadding` is `0.dp`** — when using wrap-content width (no `fillMaxWidth`), always pass `innerPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)` or similar to ensure ≥ 44dp touch target height.
- **PulseHero gradient title is conditional** — gradient text via `SrcIn` only when `isToday = true`. Future sessions: title uses `textSecondary` (no gradient) for upcoming sessions, ECG alpha drops from 0.45f to 0.20f. Three dimming mechanisms: background alpha, text color switch, ECG alpha.
- **One green in the palette** — `ZoneGreen` (#22C55E) is used everywhere including `PartnerNudgeBanner`. The old `NudgeGreen` (#4ADE80) was removed (2026-04-11). Do not introduce a second green without design rationale.
- **`AllCaughtUpCard` has an animated checkmark ring** — 40dp Canvas ring with `ZoneGreen` arc (800ms tween) + Check icon. Shown when all weekly bootcamp sessions are complete. Title uses `headlineMedium` (26sp), CTA is wrap-content with `innerPadding`.
- **Compose `remember` rules** — `remember`, `rememberInfiniteTransition`, `rememberCoroutineScope`, etc. must NEVER be called inside conditional branches (`if`/`when`). Call unconditionally and use the result conditionally. Violating this crashes at runtime when the condition toggles.
- **Active workout screen spacing** — Unified 12dp vertical spacing (`Arrangement.spacedBy`). Three radius tiers: 16dp (MissionCard), 14dp (hero stat cards, guidance, buttons, projected pill), 12dp (tertiary stats, zone badge). Do not introduce 10dp or 20dp radii.
- **Material Icons availability** — `Icons.Default` only includes a subset of Material icons. Before using an icon, verify it compiles by checking existing usage in the codebase (`grep "Icons.Default\."`). Icons confirmed working: `Home`, `Map`, `Favorite`, `FavoriteBorder`, `VolumeUp`, `Mic`, `Settings`, `Notifications`, `Group`, `Timer`, `Person`, `Check`, `Close`, `Add`, `Search`, `Bluetooth`, `ExpandLess`, `ExpandMore`, `ArrowUpward`, `ArrowDownward`, `ChevronRight`.

## Design Documents

- **Authoritative spec:** `docs/plans/2026-03-02-cardea-ui-ux-design.md` — unified 2026-04-09; tokens now match `Color.kt` exactly.
- **Design review mockup:** `docs/mockups/cardea-design-review.html` — HTML phone-frame mockups of Home/Workout/Account with token reference, type scale, and inline critique. Open in browser for visual reference.
- **UI polish proposals:** `docs/mockups/cardea-ui-polish-2026-04-11.html` — before/after phone-frame mockups for Home pre-bootcamp, Bootcamp dashboard, Day picker, and Voice coaching settings.
- **Implementation plan:** `docs/plans/2026-03-02-cardea-ui-ux-plan.md`
- **Guided workouts UX design:** `docs/plans/2026-03-02-guided-workouts-ux-design.md` — Approach B: Cardea glass preset cards, segment timeline strip, HRmax onboarding, interval countdown.
- **Guided workouts implementation plan:** `docs/plans/2026-03-01-preset-workout-profiles.md` — 12-task TDD plan; Tasks 1–2 already done in commit fd3d9d9.
- Legacy: `docs/plans/2026-02-25-hr-coaching-app-design.md` — superseded; data model and alert behavior sections still valid, UI/UX sections replaced by the 2026-03-02 spec.
- **E2E happy path audit:** `docs/2026-04-11-e2e-happy-path-audit.md` — device-tested findings: bugs, design violations, UX observations, test coverage matrix.

## Alert & Coaching Event Architecture

Two separate systems feed into `CoachingAudioManager` per tick — they do not share state:

- **`AlertPolicy`** (`service/workout/`) — owns zone alert timing: fires `SPEED_UP`/`SLOW_DOWN` after `alertDelaySec` of continuous out-of-zone, then respects `alertCooldownSec` between repeats. Escalation resets when zone returns via `onResetEscalation` callback → `CoachingAudioManager.resetEscalation()`. Cooldown persists across direction flips (ABOVE→BELOW) intentionally — prevents rapid alert spam when HR oscillates at the threshold.
- **`CoachingEventRouter`** (`service/workout/`) — owns informational cues: splits, halfway, segment changes, RETURN_TO_ZONE, IN_ZONE_CONFIRM, predictive warnings. Tracks `lastVoiceCueTimeMs` to gate the 3-minute IN_ZONE_CONFIRM silence window.
- **Critical bridging contract:** `AlertPolicy.onAlert` fires through `CoachingAudioManager` directly — the router never sees these events. After any `alertPolicy.onAlert` fires, call `coachingEventRouter.noteExternalAlert(nowMs)` to update `lastVoiceCueTimeMs`. Without this, IN_ZONE_CONFIRM can fire within 3 minutes of a zone alert.
- **Live audio settings:** Mid-workout settings changes (volume, verbosity, vibration) are applied by sending `WorkoutForegroundService.ACTION_RELOAD_AUDIO_SETTINGS` via `startService`. `AccountViewModel.saveAudioSettings()` does this automatically. `CoachingAudioManager.applySettings()` is the receiver — do not call it directly from outside the service.

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

## Distance Unit Architecture

- **`DistanceUnit`** (`domain/model/DistanceUnit.kt`) — enum `KM`/`MI` with conversion constants. Single source of truth.
- **Internal storage is always meters** — conversion to km/mi happens at display time only via `metersToUnit()`.
- **`formatPace(paceMinPerKm, unit)`** always takes pace in **min/km** and converts internally for imperial. Do NOT pass pre-converted min/mi values — causes double conversion. Compute pace as `durationMin / (meters / 1000f)`, then let `formatPace` handle the rest.
- **`UserProfileRepository.getDistanceUnit()`** returns `"km"` or `"mi"`. Read synchronously at ViewModel construction time.
- **Mile splits** fire every 1609m (not 1000m) — threshold is `DistanceUnit.METERS_PER_MILE` in `CoachingEventRouter`.

## Notification Stop Gate

`WorkoutNotificationHelper.stop()` must be called before every `stopForeground(STOP_FOREGROUND_REMOVE)` call. Without it, a late `processTick()` notification update races past `stopForeground()` and re-posts the notification. The `@Volatile stopped` flag short-circuits `update()`. Three call sites in `WorkoutForegroundService`: normal stop, short-run discard, and error handler.

## WorkoutState / Same-Tick Race Pattern

Never read `WorkoutState.snapshot.value` in the same function that called `WorkoutState.update{}` and expect the new value — `MutableStateFlow.update` may not have propagated yet. Use a local variable for same-tick decisions. Fixed in `onHrTick()` for autopause (2026-04-10).

**`MutableStateFlow.update` requires explicit import:** `import kotlinx.coroutines.flow.update` is NOT pulled in by the usual `kotlinx.coroutines.flow.*` star import in some Kotlin versions. Add it explicitly when using `_uiState.update { it.copy(…) }` in ViewModels.

Also: in `pauseWorkout()`, `pauseStartMs = clock.now()` is captured **before** `WorkoutState.update { isPaused = true }` fires. If captured after, the IO-thread `processTick()` can observe `isPaused=true` while `pauseStartMs` is still 0 — resume then skips accumulation and writes a stale timestamp on the next pause cycle.

## Dual Pause Overlap (WorkoutForegroundService)

Manual pause and auto-pause can be active simultaneously. Three guards keep `elapsedSeconds` correct — removing any one causes double-subtraction:

1. **`pauseWorkout()`** — if `isAutoPaused` is already true, latches `totalAutoPausedMs += nowMs - autoPauseStartMs` and zeroes `autoPauseStartMs` so the overlapping period isn't re-counted when auto-pause resolves.
2. **`resumeWorkout()`** — if `isAutoPaused` is still true on manual resume, restarts `autoPauseStartMs = nowMs` so only the post-resume period is counted.
3. **`AutoPauseEvent.RESUMED` handler** — guards `if (autoPauseStartMs > 0L)` before accumulating, because `pauseWorkout()` may have already zeroed it.

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
- **Firebase typed exceptions** — don't return `null` for multiple distinct failure conditions (e.g. expired invite vs not found). Throw specific subclasses (`ExpiredInviteException`, `PartnerLimitException`); catch individually in UI. Returning null collapses all failures into one ambiguous error message.
- **`runCatching { withTimeout {} }` is unsafe** — `TimeoutCancellationException` extends `CancellationException`; `runCatching` catches it silently, turning the timeout into a no-op. Use `try/catch` and rethrow cancellations: `} catch (e: CancellationException) { throw e }`. **Inner `catch (e: Exception)` has the same trap**: a nested `try { someAwait() } catch (e: Exception) { fallback }` inside a `withTimeout` lambda also swallows the timeout cancellation before it can propagate. Add `catch (e: CancellationException) { throw e }` before every `catch (e: Exception)` in Firebase coroutine code.
- **All `collectAsState()` calls are now lifecycle-aware** — `collectAsStateWithLifecycle()` used everywhere.
- **Deferred (not yet fixed):** Per-operation BLE permission checks (8 `@SuppressLint`), mid-session permission revocation, Room migration tests, full state restoration (`START_REDELIVER_INTENT`).

## Bootcamp Scheduling Architecture

- **`TrainingPhase` enum:** BASE, BUILD, PEAK, TAPER, EVERGREEN. Race goals use BASE→BUILD→PEAK→TAPER; CARDIO_HEALTH uses BASE→EVERGREEN.
- **EVERGREEN phase:** Perpetual 4-week micro-cycle (A: Tempo, B: Strides, C: Intervals/Hills tier 2+, D: Recovery). Wraps to itself on `advancePhase()` — never graduates.
- **`SessionSelector.weekSessions()`** accepts `weekInPhase`, `absoluteWeek`, and `isRecoveryWeek` params (used by EVERGREEN rotation and tier-dependent recovery composition).
- **Interval variety:** PEAK and EVERGREEN alternate presets based on `absoluteWeek % 2` (norwegian_4x4/hill_repeats for race goals; hill_repeats/hiit_30_30 for EVERGREEN).
- **Recovery week composition:** Tier 0-1 get all-easy weeks; Tier 2+ get downgraded quality (interval→tempo, tempo→strides). EVERGREEN handles its own recovery on week D.
- **Tempo presets** (`aerobic_tempo`, `lactate_threshold`) use `DISTANCE_PROFILE` with 10-min warm-up, 20-min main block, 5-min cool-down segments.
- **`getNextSession()` is date-unaware** — returns earliest SCHEDULED/DEFERRED by weekNumber+dayOfWeek, even if that day already passed. HomeViewModel uses `getScheduledAndDeferredSessions()` + computed-date filtering to match bootcamp screen behavior. Don't regress to `getNextSession()` for UI display.
- **Session date computation** — `session.dayOfWeek` is ISO (1=Mon, 7=Sun), NOT a positional offset from enrollment start. To compute the calendar date: `enrollStartDate.with(DayOfWeek.MONDAY).plusWeeks(weekNumber-1).plusDays(dayOfWeek-1)`. The old formula `enrollStartDate + ((weekNumber-1)*7 + (dayOfWeek-1))` was wrong when enrollment didn't start on Monday — it mapped to the wrong calendar day. Fixed 2026-04-12.
- **Manual-run CTA** — contextual inline "Manual run →" in `RestDay` and `RunDone` hero states only. No global catch-all at the bottom of the bootcamp page (removed: caused duplication on rest days).
- **Sim workout bootcamp cleanup** — for sim workouts, call `WorkoutState.setPendingBootcampSessionId(null)` immediately after `bootcampSessionCompleter.complete()`, before `WorkoutState.reset()`. `reset()` preserves `pendingBootcampSessionId`; skipping the explicit clear causes the bootcamp screen to re-trigger session completion on next load.

## Adaptive Engine Invariants

- **`AdaptivePaceController` is per-workout, not a singleton** — new instance created each workout; state (`sessionBuckets`, settle lists, `lastProjectedHr`) resets.
- **`hrSlopeBpmPerMin` clamp** — `instSlope` is clamped to `±50 BPM/min` (`slopeSampleClampBpmPerMin` in `AdaptiveTuningConfig`; raised from ±30 on 2026-04-13 — sprint-onset slopes legitimately hit 47 BPM/min, and the 3s minimum window already rejects BLE glitches). Do not lower below 40.
- **`hrSlopeBpmPerMin` decay on long gaps** — multiplied by `0.5` when `deltaMin > 1.5` (> 90 s gap: walk breaks, GPS-only mode, power saving). Intentional — prevents a stale climb slope persisting through a long walk. Do not remove.
- **Slope EMA weights are 0.60/0.40** — (`slopeSmoothingPreviousWeight`/`slopeSmoothingInstantWeight` in `AdaptiveTuningConfig`). Do NOT revert to 0.75/0.25 — that caused guidance to lag ~24s behind direction changes, giving wrong advice during zone corrections.
- **Predictive coaching warmup gate is 90 s** — `PREDICTIVE_WARNING` suppressed for `elapsedSeconds < 90` in `CoachingEventRouter`. The slope EMA accumulates the cardiovascular warmup rise as a positive trend; without this gate, returning runners (≥2 sessions) hear "ease off" within 30s every run. Do not shorten.
- **`shortTermTrimBpm` error source is `lastBaseProjectedHr`** — NOT `lastProjectedHr`. `lastBaseProjectedHr` = slope + longTermTrim + paceBias only (no shortTermTrim contribution). Merging both into one stored variable causes both trims to compound the same bias direction.
- **`responseLagSec` default is 38f in all three sites** — `AdaptiveProfile`, `WorkoutAdaptiveMetrics`, and `MetricsCalculator.deriveFromPaceSamples` parameter default. Previously `WorkoutAdaptiveMetrics` and `MetricsCalculator` defaulted to 25f (inert-horizon landmine: `lag × 0.4f = 10f` = minimum clamp). Production callers always pass explicit values but the default drift is a regression vector — do not revert.
- **`SubMaxHrEstimator` uses guarded upward-only margin rule** — sustained 2-min rolling peak must exceed current HRmax by `EVIDENCE_MARGIN_BPM = 2` before revision fires, then `new HRmax = peak + 1`, capped at `HrMaxEstimator.inferenceCeiling(age)`. The old effort-fraction buckets (0.92/0.85/0.75) were removed 2026-04-14 — they biased HRmax upward on moderate runs. Do NOT reintroduce bucket-based back-calculation.
- **`MetricsCalculator.trimpFrom(durationMin, avgHr, hrMax)`** is the named home of the TRIMP formula. `WorkoutForegroundService` is the authoritative invoker because only it has active-run-time duration (pauses subtracted). Do not inline the formula elsewhere.
- **`FitnessSignalEvaluator` TSB thresholds are conjunctive-gated** — `TSB_PUSH_THRESHOLD = 5f` alone looks aggressive, but PUSH_HARDER requires BOTH `tsb > 5` AND `efTrend > 0.04` (3+ reliable sessions over 42 days). The EF requirement is the real filter. Don't flag +5 as "aggressive" in isolation — it's half of a gate. EASE at -25 is unilateral (Friel's "overreached" zone).
- **`TierCtlRanges` uses 0-indexed internal tiers** — Foundation/Development/Performance via `TierInfo`, mapping to the spec's 1-indexed T1/T2/T3. Code intentionally extends the spec at both ends: Foundation floor = 0 (handles new users with CTL < 10), Performance ceiling = 200 (terminal tier, no promotion above). Both divergences are documented in the `TierCtlRanges.kt` class KDoc.
- **HRR1 pathway is dormant** — `FitnessSignalEvaluator` illness tier is permanently `NONE` because `hrr1Bpm` is never computed (120s post-workout cool-down hold not implemented). `BootcampViewModel`/`BootcampScreen` illness-flag UI is dead code tagged `TODO(HRR1)`. Don't delete the schema fields — they'll be needed when the cool-down feature lands.
- **Settle cap is 10 min** — `trackSettling()` window `2_000L..600_000L` ms. Do not lower to 5 min — structured interval excursions commonly run 6-10 min and their settle data was silently dropped, preventing `responseLagSec` from ever calibrating for interval runners.
- **`AdaptiveProfileRepository.resetLongTermTrim()`** — zeroes `longTermHrTrimBpm` only; other profile fields unchanged. Not yet wired to any UI. Use after heat-block or overtraining recovery when trim has drifted to an environmental baseline.
- **`CoachingEventRouter` has two alert-reset methods** — `noteExternalAlert(nowMs)` resets IN_ZONE_CONFIRM silence window; `resetPredictiveWarningTimer()` sets `lastPredictiveWarningTime = 0L`. Both are called from `AlertPolicy.onAlert` in WFS. Do NOT conflate them. Note: `resetPredictiveWarningTimer()` is effectively overridden by the zone re-entry conditional reset (see below) in all realistic post-alert scenarios; retained for call-site clarity.
- **PREDICTIVE_WARNING zone-entry grace (60s conditional reset)** — on any zone transition into IN_ZONE (first entry or re-entry), if the 60s predictive cooldown was already expired, `lastPredictiveWarningTime` is reset to `nowMs`. This prevents PREDICTIVE_WARNING from firing simultaneously with zone entry (first entry: returning runners with prior-session confidence; re-entry: drift still projected after correction). The reset is conditional — if the cooldown has NOT yet expired, it is left alone so rapid oscillators (< 60s cycles) retain their running timer and receive warnings once 60s elapses. The 90s warmup gate is a separate, independent guard evaluated after the zone-entry block.
- **`TickResult.guidance` is non-nullable `String`** — a `when` guard `adaptiveResult?.guidance != null` is always true when `adaptiveResult` is non-null. Preset overrides (strides, zone2) MUST appear before `adaptiveResult != null` in the WFS guidance `when` block or they are permanently dead code.
- **`finishSession` uses `savedInitialProfile.copy()`** — carries all `AdaptiveProfile` fields the controller doesn't own (hrMax, ctl, atl, hrRest, hrMaxIsCalibrated, hrMaxCalibratedAtMs, lastTuningDirection). Callers patch freshly-computed values on top. Do NOT construct a bare `AdaptiveProfile(longTermHrTrimBpm=..., ...)` inside finishSession — silently zeroes un-named fields including calibrated hrMax and fitness load.
- **Settle-time averaging is direction-equal** — `responseLagSec` updated from `(settleDownAvg + settleUpAvg) / 2`. Each physiological direction gets equal weight regardless of event count. Do not switch to count-weighted — 8 quick corrections would drown out 1 slow build-up, under-estimating upward response lag.
- **`lookupPaceBias` uses sampleCount-weighted neighbour average** — not a simple mean over up to 3 neighbours. A 1-sample anomalous bucket must not get equal vote to a 500-sample baseline.
- **TRIMP formula is non-standard** — uses `duration * avgHR * (avgHR/HRmax)^2`, not Bannister's exponential. Consistent across the codebase; don't "fix" it to match literature values.
- **Three `bootcampSessionCompleter.complete()` call sites:** (1) PostRunSummaryViewModel (reads lastTuningDirection from AdaptiveProfileRepository), (2) WorkoutForegroundService sim path (reads from saved profile), (3) BootcampViewModel.onWorkoutCompleted (reads from UI state). All three pass tuningDirection.
- **`efTrend` regression math** — slope is `(n*sumXY - sumX*sumY) / (n*sumX2 - sumX*sumX)`, then multiplied by `(n-1)` to give total estimated change across the window. Comparable to the old endpoint delta and the existing 0.04 threshold.
- **TRIMP fallback `durationMin` uses active run time** — `(now - workoutStartMs - totalPausedMs - totalAutoPausedMs).coerceAtLeast(0) / 60_000f`. Do NOT revert to wall-clock `now - workoutStartMs` — it overstates training load in proportion to pause fraction.
- **Cadence lock check uses explicit counter** — `hrSamplesSinceLastArtifactCheck`, NOT `hrSampleBuffer.size % 10`. The buffer cap is 120 (= 12×10), so `size % 10 == 0` is permanently true at steady state and fires on every tick. The counter fires every 10 samples as intended.
- **`hrSampleSum`/`hrSampleCount` reset in `startWorkout()`** — not only in `stopWorkout()`. `stopWorkout()` zeroes them before `observationJob` is cancelled; a race can add a sample to the freshly-zeroed sum and carry that stale value into the next session.

## Science Constants Register

Every physiological constant, coefficient, threshold, or formula in the adaptive engine has (or must have) a provenance entry in `docs/plans/2026-04-14-science-constants-register.md`. Valid sources: `published`, `internal-rationale`, `intentional-non-standard`, `unsourced`. `unsourced` is the only failure state.

**Rule:** if you introduce, change, or move a science constant, update the register entry in the same commit. Audit procedure and findings history live in `docs/plans/2026-04-14-science-fidelity-audit-findings.md`.

## Test Fakes

- **`FakeBootcampDao`** in `BootcampSessionCompleterTest.kt` directly implements `BootcampDao`. Adding/changing DAO methods requires updating this fake or tests won't compile.
- **`AchievementDao`** has TWO fakes: `FakeAchievementDao` class in `AchievementEvaluatorTest.kt` and an inline anonymous object in `BootcampSessionCompleterTest.kt:27`. Changing `AchievementDao` requires updating both.
- **Room `@Insert` returns row ID** — declare `suspend fun insert(...): Long` (not `Unit`) when you need the auto-generated ID immediately after insert (e.g. to pass to cloud sync). The entity object in memory retains `id=0` after the call; use the returned `Long`.
- **Test timezone safety** — tests that build epoch-millis via `LocalDate.atStartOfDay(ZoneId.of("UTC"))` must pass that zone explicitly to any function under test. Relying on `ZoneId.systemDefault()` causes failures on machines west of UTC.

## Known Pre-existing Lint Errors (do not treat as regressions)

`BleHrManager.kt` MissingPermission, `WorkoutForegroundService.kt` MissingSuperCall, `NavGraph.kt` NewApi (×2), `build.gradle.kts` WrongGradleMethod — all pre-date this codebase's Claude sessions.

**Pre-existing compile error:** `PartnerSection.kt:294` — `WindowInsets` unresolved reference + `@Composable` scope errors. Blocks `assembleDebug`. Unrelated to UI polish work.

## Ralph Loop (ralph-loop skill)

- **Windows caveat:** The `setup-ralph-loop.sh` script fails when the prompt contains special characters like `(`, `×`, `&` — bash eval syntax errors. Keep ralph loop prompts to plain ASCII only.
- **Invocation:** Use `Skill` tool with `skill: "ralph-loop:ralph-loop"` and a simple `args` string (no parens, no unicode). Example: `args: "Implement the guided workout feature per the plan in docs/plans/2026-03-01-preset-workout-profiles.md - run tests after each task - output DONE when all pass --completion-promise DONE"`
- **Not suitable for:** UX design decisions, brainstorming, tasks requiring human judgment. Use `superpowers:brainstorming` for those first.
- **Good for:** Well-defined TDD implementation tasks where the plan already specifies exact file changes and test commands.

## ADB Data Backup Safety (CRITICAL — data loss risk)

**NEVER use `adb shell run-as ... cat <binary-file> > /tmp/local`** — Git Bash CR/LF translation silently corrupts SQLite databases. The file looks valid (correct size, SQLite header) but B-tree pages are mangled and unrecoverable.

**Safe backup procedure:**
1. `adb shell "run-as com.hrcoach cp databases/hr_coach_db /data/local/tmp/hr_coach_backup.db"` (also `-shm` and `-wal`)
2. `adb pull //data/local/tmp/hr_coach_backup.db /tmp/hr_coach_backup.db` (double-slash prevents Git Bash path translation)
3. Verify: `sqlite3 /tmp/hr_coach_backup.db "SELECT COUNT(*) FROM workouts"`
4. Clean up: `adb shell "rm /data/local/tmp/hr_coach_backup.db*"`

**Safe restore procedure:**
1. `adb shell am force-stop com.hrcoach`
2. `adb push /tmp/hr_coach_backup.db //data/local/tmp/hr_coach_restore.db` (also `-shm`, `-wal`)
3. `adb shell "run-as com.hrcoach cp /data/local/tmp/hr_coach_restore.db databases/hr_coach_db"` (also `-shm`, `-wal`)
4. Clean up temp files, launch app, verify

**Windows Git Bash path gotchas:**
- `adb push/pull` paths: use `//data/` prefix (double-slash) to prevent `/data/` → `C:/Program Files/Git/data/` translation
- `firebase database:get /path`: prefix with `MSYS_NO_PATHCONV=1`
- `adb shell "..."` commands run on-device and are unaffected by Git Bash translation

## Firebase RTDB Data Model

Firebase project: `cardea-1c8fc`. CLI: `MSYS_NO_PATHCONV=1 firebase database:get /path --project cardea-1c8fc --pretty`. Add `--shallow` to get child keys as `true` without downloading all data (useful for surveying large nodes). Write/delete: `firebase database:set -d "value" -f /path` and `firebase database:remove -f /path` — use `-f` (force), not `--confirm` (doesn't exist).
- `/users/{uid}` — `displayName`, `emblemId`, `fcmToken`, `partners: {partnerUid: true}`, `activity: {lastRunDate, lastRunDurationMin, lastRunPhase, weeklyRunCount, currentStreak}`
- `/users/{uid}/backup` — cloud backup root. `backupComplete: true` written last (absence = partial or pre-fix backup). Subtrees: `profile/`, `settings/`, `adaptive/` (key `paceHrBuckets`, not `buckets`), `workouts/`, `trackPoints/`, `metrics/`, `bootcamp/enrollment`, `bootcamp/sessions/`, `achievements/`. Track point keys are abbreviated: `id`, `ts`, `lat`, `lng`, `hr`, `dist`, `alt`. See `CloudBackupManager.TpKeys`.
- `/invites/{code}` — invite codes with `userId`, `displayName`, `createdAt`, `expiresAt`
- Partner connections are bidirectional: both users must have the other's UID in their `partners` map

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
