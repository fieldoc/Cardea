# CLAUDE.md

Guidance for Claude Code working in this repo.

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

### Worktree build & git

- **`local.properties` missing:** worktrees are at `.claude/worktrees/<name>/` — `cp ../../../local.properties .`
- **Windows path-length:** KSP/AAPT fail in deeply-nested worktree build dirs. Copy changed files back to main and build there. Do NOT use `subst` — writes through the mapped drive silently revert edits made via the original path.
- **Merge worktree into dirty main:** run THREE separate commands (never chain with `&&`): `git stash push -m "..."` → `git merge --ff-only <branch>` → `git stash pop`. If `stash pop` exits 1 on chain, merge never runs.
- **Rewritten-file stash conflicts:** procedure above fails when popped WIP (a) touches a file the branch rewrote, or (b) references untracked files not yet committed. Recovery: `git checkout HEAD -- <file>` → `git reset HEAD` → `git stash drop`. WIP remains as unstaged changes.
- **Branch diverged behind main:** `--ff-only` fails. Fix: in worktree `git rebase main`, then in main `git merge --ff-only <branch>`. Back-to-back — second main commit forces another rebase. Note: `git fetch . main:main` from the worktree errors ("refusing to fetch into branch checked out at ...") — no need, `git rebase main` reads the local ref directly.
- **`git rebase --continue --no-edit` is invalid** and hard-errors. Use `GIT_EDITOR=true git rebase --continue` for non-interactive continue.
- **Untracked files block `git merge`:** if the incoming branch creates a file present as untracked, `rm` the untracked copy first.
- **`git worktree remove` permission denied:** shell cwd must be outside the worktree. Use `git worktree prune` to clear stale registrations.

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

- **WorkoutState** (`service/WorkoutState.kt`) — singleton `StateFlow` shared between service and UI; holds HR, zone, distance, pace, guidance.
- **WorkoutForegroundService** — orchestrator; combines BLE+GPS, evaluates zones, fires alerts, writes track points every 5s, persists on stop.
- **Workout modes:** `STEADY_STATE` (single target ± buffer), `DISTANCE_PROFILE` (ordered segments), `FREE_RUN` (no target).
- **AdaptivePaceController** — HR slope, pace-HR buckets, response lag, trim offsets. Persists via `AdaptiveProfileRepository`.
- **Audio stream:** all components use `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` to layer over music without audio focus. Do NOT use `NOTIFICATION_EVENT` (gets ducked). **Exception:** `ToneGenerator` takes `AudioManager.STREAM_*`, not `USAGE_*` — use `STREAM_MUSIC`. The two constant spaces are distinct; mis-passing silently routes wrong.

### Packages

- `data/db/` — Room `AppDatabase`, entities, DAOs. Tables: `workouts`, `track_points` (FK CASCADE), `bootcamp_enrollments`, `bootcamp_sessions` (FK CASCADE), `achievements`, `workout_metrics`. `targetConfig` is JSON.
- `data/repository/` — all repos (Workout, WorkoutMetrics, AdaptiveProfile, AudioSettings, MapsSettings, Bootcamp, AutoPauseSettings, Onboarding, ThemePreferences, UserProfile).
- `domain/model/` — `WorkoutConfig`, `WorkoutMode`, `AdaptiveProfile`, `ZoneStatus`, `DistanceUnit`.
- `domain/engine/` — `ZoneEngine`, `AdaptivePaceController`.
- `service/` — `WorkoutForegroundService`, `BleHrManager`, `GpsDistanceTracker`, `WorkoutState`.
- `service/workout/` — `AlertPolicy`, `CoachingEventRouter`, `WorkoutNotificationHelper`, `TrackPointRecorder`.
- `service/audio/` — `CoachingAudioManager`, `EarconPlayer`, `VoicePlayer`, `StartupSequencer`, `VoiceEventPriority`, `VibrationManager`, `EscalationTracker`.
- `ui/` — `account/`, `bootcamp/`, `components/` (CardeaLogo, GlassCard), `debug/`, `emblem/`, `history/`, `home/`, `navigation/` (`NavGraph.kt::HrCoachNavGraph`), `onboarding/`, `postrun/`, `progress/`, `setup/` (= "Workout" tab), `splash/`, `workout/`.
- `di/` — Hilt `AppModule` (Room + DAOs).

DI: `HrCoachApp` = `@HiltAndroidApp`; `MainActivity` + `WorkoutForegroundService` = `@AndroidEntryPoint`; VMs `@HiltViewModel`.

## Navigation

Four-tab bottom bar: **Home**, **Workout** (setup or bootcamp depending on enrollment), **History** (also Progress), **Account**. Start is `home` after splash. Active workout hides bottom bar. Auto-navigates to workout when service starts, to postrun on end (or back to `setup` if no completed ID). `onDone` from postrun → bootcamp dashboard (bootcamp) or history detail (freestyle).

- **Bootcamp session identity contract:** `prepareStartWorkout()` resolves DB session ID and sets `WorkoutState.pendingBootcampSessionId` immediately. `onBootcampWorkoutStarting()` is fallback only. Do NOT use "first uncompleted" heuristic — wrong when sessions started out of order.
- **Simulation permission bypass:** `PermissionGate.hasAllRuntimePermissions` checks skipped when `SimulationController.isActive`. Both call sites (SetupScreen + BootcampScreen `onStartWorkout`) have this.

## UI & Theme

- **Cardea design system** — dark glass-morphic, background `#050505` (radial with `#0D0D0D`). All tokens in `ui/theme/Color.kt`. Authoritative spec: `docs/plans/2026-03-02-cardea-ui-ux-design.md`. **Token drift rule:** spec conflicts with `Color.kt` → fix the spec.
- **`CardeaTheme`** is primary; `HrCoachTheme` = back-compat wrapper; `HrCoachThemeTokens` = typealias of `CardeaThemeTokens`. Dynamic color `false`. System/Light/Dark are user preferences — light mode is NOT a bug.
- **`cueBannerBorderColor(kind, alpha)`** (`ui/workout/CueBannerColors.kt`) — SSOT for cue-banner kind→hue. `CueBannerOverlay` uses α=0.5; `SoundLibraryScreen` list rows use α=0.4. Do not reintroduce per-screen `borderFor` helpers or hardcoded hex for banner tints.
- **Three gradients — use the right one:**
  - `CardeaGradient` (4-stop `#FF4D5A→#FF2DA6→#4D61FF→#00E5FF`, 135°) — ring foregrounds, gradient text, Tier 1 accent borders only. Do NOT alter stops.
  - `CardeaCtaGradient` (Red→Pink) — all buttons, active chips, selection indicators, day dots. Only ~3 legit `CardeaGradient` uses remain — grep before adding new ones. `BootcampScreen.kt` is fully purged; do NOT reintroduce the 4-stop there.
  - `CardeaNavGradient` (Blue→Cyan) — active nav icons only.
- **One gradient accent per screen** — 3-tier hierarchy: Tier 1 gradient @ 18dp, Tier 2 white-on-glass @ 14dp, Tier 3 secondary-on-glass @ 12dp. BootcampTile ring is plain white 0.55α.
- **Glass surfaces:** `GlassBorder = 0x1AFFFFFF` (10%), `GlassHighlight = 0x0AFFFFFF`. Use `ui/components/GlassCard.kt` for all cards. `GlassCard.containerColor` IS wired — pass `borderColor = Color.Transparent` when using an outer `Modifier.border(brush=…)` to avoid double-borders. Same when adding tinted borders (e.g. `ZoneGreen.copy(alpha = 0.15f)`).
- **`DarkGlassFillBrush`** = 8%/4% white (strengthened from 6%/2%) so glass is visible on `#050505`.
- **`CardeaTextTertiary`** = `#6B6B73` (raised from `#52525B` for WCAG AA on `#050505`); `DisabledGray` and `ThresholdLine` follow.
- **Zone-status text** (HR pills/indicators) is safety-critical — min 12sp with adequate padding. Never decorative micro-labels.
- **Min font size 10sp** globally (9sp absolute floor for micro-labels only). Known remaining violations: `HomeScreen.kt` (8sp ~722; 9sp ~509/550/666/751), `BootcampSettingsScreen.kt` (9sp ~811), `CalendarHeatmap.kt` (9sp ~90), `MissionCard.kt` (9sp ~135).
- **Typography scale completeness** — `HrCoachTypography` must define every style used; undefined styles silently fall back to M3 defaults. Defined: `displaySmall` 34 Bold, `headlineSmall` 22 SemiBold, `titleSmall` 15 SemiBold, `labelMedium` 12 Medium. Add to `Type.kt` before use.
- **`CardeaLogo`** — Canvas heart+ECG+orbital ring with gradient fill. `LARGE` (splash 180dp, though splash now uses its own Canvas animation), `SMALL` (nav 32dp).
- **App icon "Pure Signal"** — single ECG P-QRS-T trace bleeding off both edges; gradient left→right; bg `#0D0D14`; QRS spike near-full-height, needle-sharp. No heart shape. Mipmaps generated via PIL (`gen_cardea_icon.py`, `/c/Python314/python.exe`). Sizes: mdpi 48, hdpi 72, xhdpi 96, xxhdpi 144, xxxhdpi 192. Both `ic_launcher.png` and `ic_launcher_round.png` per density.
- **Splash screen** (`SplashScreen.kt`) — full Canvas animation via `withFrameMillis` loop (no `CardeaLogo`, no `animateFloatAsState`). Phases: 1600ms draw-on / 500ms hold / 500ms fade. `onFinished()` fires once. Do NOT add Compose animation APIs. Capture on device: `adb shell am force-stop com.hrcoach && adb shell monkey -p com.hrcoach -c android.intent.category.LAUNCHER 1 >/dev/null & sleep 0.9 && adb shell screencap //data/local/tmp/s.png && adb pull //data/local/tmp/s.png /tmp/s.png`.
- **Splash freeze-at-hold** — loop caps at `cycleIdx >= 1` and freezes `elapsed` at `PHASE_DRAW_MS + PHASE_HOLD_MS - 1` (fully-drawn, pre-fade). Both the `withFrameMillis` block AND the `Canvas` block MUST apply the same cap — they re-derive `elapsed` independently. Without both, slow cloud restore either re-animates from scratch or fades to black.
- **NavGraph splash contract:** navigation fires only when **both** `animationFinished` (from `onFinished`) AND `destination != null` (from `autoCompleteForExistingUser` + `checkAndRestoreIfNeeded`) are true. Falling back to `ONBOARDING` on null destination misroutes existing users whose cloud restore is slow.
- **Countdown overlay invariants** (`ActiveWorkoutScreen.kt`): `countdownActive = countdownSecondsRemaining != null`. While active: (1) `CueBannerOverlay` is suppressed (BLE/GPS cues would stack over "3-2-1-GO"), (2) the stat-card `Column` is entirely hidden (zeroed values bleed through 0.92α overlay), (3) backdrop is a radial gradient `Black(0.92) → bgPrimary(0.82)`, not flat black. Don't regress any of these.
- **Canvas glow in Compose** — use `drawIntoCanvas { canvas -> canvas.drawPath(path, Paint().also { it.asFrameworkPaint().maskFilter = BlurMaskFilter(...) }) }`. No CSS-blur equivalent exists.
- **Gradient nav icons** — `CompositingStrategy.Offscreen` + `BlendMode.SrcIn` with `CardeaNavGradient` for pixel-perfect gradient fill on any `ImageVector`.
- **Charts are custom Canvas** (`ui/charts/`) — BarChart, PieChart, ScatterPlot use `DrawScope` directly. Must use `CardeaTheme.colors` (M3-free as of 2026-04-12). `HrCoachThemeTokens.subtleText` is legacy — use `CardeaTheme.colors.textSecondary`. Canvas sizing must use `X.dp.toPx()`, not raw float px.
- **`BarChart.color`** is a solid fill with alpha modulation (latest=1.0, others=0.55). Don't reintroduce hardcoded gradients. `SectionHeader` = uppercase labelMedium Bold, 2sp letterSpacing, `textSecondary`; subtitle `textTertiary`.
- **`hrPercentColor()`** (SetupScreen) uses `ZoneRed`/`ZoneAmber`/`ZoneGreen` tokens — do NOT hardcode hex.
- **`CardeaSlider`** thumb/active track = `GradientPink` (distinct from M3 and from CTA layering). Bootcamp sliders also `GradientPink` directly.
- **Segmented button accent** is neutral: `cardeaSegmentedButtonColors()` uses `textPrimary`/`textSecondary`, not `GradientBlue`. Active stands out via white text + border.
- **`FlowRow`** requires `@OptIn(ExperimentalLayoutApi::class)`.
- **M3 button text color leak:** `OutlinedButton`/`TextButton` default text → `colorScheme.primary` (= `GradientBlue`). Always pass explicit `color = CardeaTheme.colors.textPrimary` (or `textSecondary`) to inner `Text()`. `CardeaButton` is exempt.
- **`OutlinedTextField` dark mode** — always set focused/unfocused `containerColor` + `placeholderColor` in `OutlinedTextFieldDefaults.colors()`. Recommended: `Color(0x14FFFFFF)` focused, `Color(0x0AFFFFFF)` unfocused.
- **`TabRow` vs `ScrollableTabRow`** — ≤3 fixed tabs → `TabRow` (equal distribution). Custom indicator via `Box(Modifier.tabIndicatorOffset(tabPositions[selected]).height(2.dp).background(GradientPink))`. In M3 BOM ≥ 2024.12.01, `tabIndicatorOffset` is a member of `TabRowDefaults`; wrap: `with(TabRowDefaults) { ... }`.
- **`CardeaButton` default `innerPadding = 0.dp`** — for wrap-content width, always pass `PaddingValues(horizontal = 24.dp, vertical = 14.dp)` to keep ≥44dp touch target.
- **Single green** — `ZoneGreen` (#22C55E) everywhere including `PartnerNudgeBanner`. `NudgeGreen` (#4ADE80) is removed — do not reintroduce.
- **`AllCaughtUpCard`** — 40dp Canvas ring, `ZoneGreen` arc (800ms tween) + Check icon. Title `headlineMedium` 26sp; CTA wrap-content w/ `innerPadding`.
- **Compose `remember` rule** — `remember`, `rememberInfiniteTransition`, `rememberCoroutineScope` NEVER in conditional branches. Call unconditionally, use the result conditionally. Violating crashes when the condition toggles.
- **Active workout screen spacing** — 12dp vertical (`Arrangement.spacedBy`). Radius tiers: 16dp MissionCard / 14dp hero stats, guidance, buttons, projected pill / 12dp tertiary stats, zone badge. No 10/20dp radii.
- **Material icons availability** — only a subset ships in `Icons.Default`. Verify via grep before use. Confirmed: `Home`, `Map`, `Favorite`, `FavoriteBorder`, `VolumeUp`, `Mic`, `Settings`, `Notifications`, `Group`, `Timer`, `Person`, `Check`, `Close`, `Add`, `Search`, `Bluetooth`, `ExpandLess`, `ExpandMore`, `ArrowUpward`, `ArrowDownward`, `ChevronRight`.
- **Home screen gradient hierarchy** — `PulseHero` = sole Tier 1 (gradient text, conditional on `isToday`; future sessions use `textSecondary`, ECG α drops 0.45→0.20). `GoalTile` = Tier 2. `BootcampTile` progress bar uses `ctaGradient`; `VolumeTile` uses subtle inline gradient. No gradient borders/text on stat tiles.
- **BootcampEntryCard CTA** = ghost outlined (no fill) so "Start Run" is the sole gradient primary CTA on the Training tab.

## Design docs

- Authoritative spec: `docs/plans/2026-03-02-cardea-ui-ux-design.md` (tokens match `Color.kt`).
- Design review: `docs/mockups/cardea-design-review.html` (phone-frame mockups).
- Polish proposals: `docs/mockups/cardea-ui-polish-2026-04-11.html`.
- UI plan: `docs/plans/2026-03-02-cardea-ui-ux-plan.md`.
- Guided workouts UX: `docs/plans/2026-03-02-guided-workouts-ux-design.md`.
- Guided workouts impl: `docs/plans/2026-03-01-preset-workout-profiles.md`.
- E2E audit: `docs/2026-04-11-e2e-happy-path-audit.md`.
- Legacy (superseded): `docs/plans/2026-02-25-hr-coaching-app-design.md` — data model + alert sections still valid.

## Alert & Coaching Event Architecture

Two separate systems feed `CoachingAudioManager` per tick — they do not share state:

- **`AlertPolicy`** (`service/workout/`) — zone alert timing. Fires `SPEED_UP`/`SLOW_DOWN` after `alertDelaySec` continuous out-of-zone; then `alertCooldownSec` between repeats. Escalation resets on zone return via `onResetEscalation` → `CoachingAudioManager.resetEscalation()`. Cooldown persists across direction flips (ABOVE→BELOW) — intentional, prevents spam at threshold.
- **`CoachingEventRouter`** (`service/workout/`) — informational cues: splits, halfway, segment changes, RETURN_TO_ZONE, IN_ZONE_CONFIRM, predictive warnings. Tracks `lastVoiceCueTimeMs` to gate the 3-min IN_ZONE_CONFIRM silence window.
- **Bridging contract:** `AlertPolicy.onAlert` fires through `CoachingAudioManager` directly — router never sees these. After any `alertPolicy.onAlert`, call `coachingEventRouter.noteExternalAlert(nowMs)` or IN_ZONE_CONFIRM can fire within 3 min of an alert.
- **Live audio settings:** mid-workout changes go via `WorkoutForegroundService.ACTION_RELOAD_AUDIO_SETTINGS` (`startService`). `AccountViewModel.saveAudioSettings()` does this. `CoachingAudioManager.applySettings()` receives — do not call from outside the service.
- **`AlertPolicy.handle()` only runs when `!isAutoPaused`** (WFS processTick guard). So walk-break suppression, self-correction suppression, and any future AlertPolicy gate is a no-op when auto-pause is engaged — intentional and non-conflicting, but new gates don't need to re-check auto-pause state.
- **`AudioSettings` has two persistence paths** — local (Gson JSON blob in SharedPreferences, missing fields auto-default) and cloud (field-by-field map in `CloudBackupManager.syncSettings` + field-by-field read in `CloudRestoreManager.restoreSettings`). Adding a new field requires touching BOTH cloud paths; local storage handles it automatically.

## Audio Pipeline

Three-component layered audio in `service/audio/`:

- **`EarconPlayer`** — SoundPool, short WAV zone alerts. `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`.
- **`VoicePlayer`** — TTS for briefings, zone alerts (using adaptive guidance text), splits, cues. Priority-gated via `VoiceEventPriority` (CRITICAL > NORMAL > INFORMATIONAL). Layers over music.
- **`StartupSequencer`** — MediaPlayer plays `countdown_321_go.wav` (custom marimba). Updates `WorkoutState.countdownSecondsRemaining` for UI.
- **`CoachingAudioManager`** — Orchestrator. Startup: TTS briefing (await) → countdown WAV (await) → return (timer starts). No delay between briefing and countdown — removed 500ms dead air that felt like a hang. `VoiceVerbosity.OFF` gates ALL audio (earcons + voice). `shouldPlayEarcon(verbosity)` is the single earcon gate.
- **`CoachingAudioManager.skipNextBriefing`** — `@Volatile` companion flag, one-shot. Set by primer-dismiss handlers in `SetupViewModel.dismissPrimerThenProceed` AND `BootcampViewModel.dismissPrimerThenProceed` so the very next `playStartSequence` skips TTS briefing (primer just explained the audio system — briefing on top feels redundant). Cleared on read. This is the canonical cross-layer signal — do not plumb a parameter through Intent/Service layers to replicate.
- **Pause/resume tones exempt from verbosity** — `playPauseFeedback()` plays regardless. Safety-critical.
- **`AudioSettings`:** `earconVolume` and `voiceVolume` independent (0–100).
- **Verbosity:** OFF silent; MINIMAL = critical/normal only; FULL = all events including informational.
- **KM splits:** "Kilometer N" (STEADY_STATE/DISTANCE_PROFILE); "Kilometer N. Pace: X minutes Y." (FREE_RUN).

## Distance Unit

- `DistanceUnit` (`domain/model/`) — enum `KM`/`MI` + conversions. Single source of truth.
- **Storage always meters;** convert at display via `metersToUnit()`.
- **`formatPace(paceMinPerKm, unit)`** always takes **min/km** and converts internally. Never pass pre-converted min/mi — causes double conversion. Compute `durationMin / (meters / 1000f)` and pass through.
- `UserProfileRepository.getDistanceUnit()` → `"km"` or `"mi"`; read synchronously at VM construction.
- **Mile splits** fire every 1609m — threshold `DistanceUnit.METERS_PER_MILE` in `CoachingEventRouter`.

## Notification Stop Gate

`WorkoutNotificationHelper.stop()` MUST be called before every `stopForeground(STOP_FOREGROUND_REMOVE)`. Without it a late `processTick()` re-posts the notification. `@Volatile stopped` flag short-circuits `update()`. Three call sites in WFS: normal stop, short-run discard, error handler.

## WorkoutState / Same-Tick Race

- Never read `WorkoutState.snapshot.value` in the same function that called `WorkoutState.update{}` — `MutableStateFlow.update` may not have propagated. Use a local var. (Fixed in `onHrTick()` autopause 2026-04-10.)
- **`MutableStateFlow.update` import** — `import kotlinx.coroutines.flow.update` is NOT pulled in by `kotlinx.coroutines.flow.*` in some Kotlin versions; import explicitly.
- **`pauseWorkout()` ordering** — capture `pauseStartMs = clock.now()` BEFORE `WorkoutState.update { isPaused = true }`. If captured after, IO-thread `processTick()` can see `isPaused=true` while `pauseStartMs=0` → resume skips accumulation → stale timestamp on next pause.

## Dual Pause Overlap (WFS)

Manual pause and auto-pause can be simultaneous. Three guards keep `elapsedSeconds` correct; removing any one → double-subtraction:

1. **`pauseWorkout()`:** if `isAutoPaused`, latch `totalAutoPausedMs += nowMs - autoPauseStartMs` and zero `autoPauseStartMs`.
2. **`resumeWorkout()`:** if `isAutoPaused` still true on manual resume, restart `autoPauseStartMs = nowMs`.
3. **`AutoPauseEvent.RESUMED` handler:** guard `if (autoPauseStartMs > 0L)` before accumulating — `pauseWorkout()` may have zeroed it.

## Auto-pause Startup Gates (WFS)

Auto-pause firing in the first seconds of a run is user-hostile. Three AND-gated conditions must all be true before `AutoPauseDetector.update()` is consulted:

1. **`sessionAutoPauseEnabled`** — user preference (`AutoPauseSettingsRepository`).
2. **`nowMs >= autoPauseGraceUntilMs`** — wall-time grace, `AUTO_PAUSE_GRACE_MS = 20_000L` (raised from 15s 2026-04-20; covers stop-and-go starts like crossing traffic).
3. **`hasMovedSinceStart`** — latches true on first `tick.speed > 0.5f` (≈1.8 km/h). "Paused from movement" is only meaningful after actual movement; prevents pause tones while the runner is still tying shoes or pocketing the phone.

All three reset in `startWorkout()`. Also: `autoPauseCountThisSession` counter — first auto-pause of a session plays tone + banner but skips the "Run autopaused" TTS announcement (surprise management). Subsequent pauses announce normally.

## Startup Guidance Branching (WFS)

Initial `WorkoutState.guidanceText` set after the countdown block in `startWorkout()`:
- `SimulationController.isActive` → "SIM STARTING"
- `bleCoordinator.isConnected.value` (true at this point for users who connected on Setup) → "Get set"
- Otherwise → "Searching for HR signal"

Unconditionally setting "Searching for HR signal" causes a one-frame flash for users who connected pre-run — the first `processTick` then overwrites with real guidance. Do not regress the branch.

## WorkoutTick nullables

`WorkoutTick.speed` is `Float?` (nullable). Comparisons need `?: 0f` coercion — `tick.speed > 0.5f` is a compile error. `tick.hr` is non-nullable `Int`; `tick.connected` is `Boolean`.

## DataStore / Slider

Never call DataStore `edit {}` inside `onValueChange` (fires hundreds of times/s). Persist in `onValueChangeFinished`; update in-memory `StateFlow` in `onValueChange` for smooth UI.

## Error Handling & Crash Resilience

Audit: `docs/superpowers/plans/2026-04-11-error-handling-audit.md`.

- **`stopWorkout()` essential/best-effort split** — essential ops (save workout, stop GPS/BLE) in individual `runCatching`; best-effort (metrics, achievements) grouped. Both log.
- **Firebase typed exceptions** — never return `null` for multiple failure conditions. Throw subclasses (`ExpiredInviteException`, `PartnerLimitException`); catch individually. Collapsing to null = ambiguous errors.
- **`runCatching { withTimeout {} }` is unsafe** — `TimeoutCancellationException extends CancellationException`; `runCatching` swallows it, turning timeout into no-op. Use `try/catch` and rethrow: `} catch (e: CancellationException) { throw e }`. **Same trap for inner `catch (e: Exception)`** nested inside a `withTimeout` lambda. Add `catch (e: CancellationException) { throw e }` before every `catch (e: Exception)` in Firebase coroutine code.
- All `collectAsState()` → `collectAsStateWithLifecycle()`.
- **Deferred:** per-op BLE permission checks (8 `@SuppressLint`), mid-session permission revocation, Room migration tests, full state restoration (`START_REDELIVER_INTENT`).

## Bootcamp Scheduling

- **`TrainingPhase` enum:** BASE, BUILD, PEAK, TAPER, EVERGREEN. Race goals: BASE→BUILD→PEAK→TAPER. CARDIO_HEALTH: BASE→EVERGREEN.
- **EVERGREEN** — perpetual 4-week micro-cycle (A Tempo, B Strides, C Intervals/Hills for tier 2+, D Recovery). Never graduates.
- **`SessionSelector.weekSessions()`** accepts `weekInPhase`, `absoluteWeek`, `isRecoveryWeek` (EVERGREEN rotation + tier-dependent recovery).
- **Interval variety:** PEAK and EVERGREEN alternate by `absoluteWeek % 2` (race: norwegian_4x4/hill_repeats; evergreen: hill_repeats/hiit_30_30).
- **Recovery composition:** tier 0-1 all-easy; tier 2+ downgrade (interval→tempo, tempo→strides). EVERGREEN handles its own recovery on week D.
- **Tempo presets** (`aerobic_tempo`, `lactate_threshold`) use DISTANCE_PROFILE: 10min WU / 20min main / 5min CD.
- **`PhaseEngine.isRecoveryWeek()`** accepts optional `TuningDirection`. Cadence: EASE_BACK=2w, HOLD/null=3w, PUSH_HARDER=4w. `weeksUntilNextRecovery()` simulates phase transitions (walks `advancePhase()` boundaries); EVERGREEN → distance to week D of the 4-week cycle. All call sites in `BootcampViewModel` pass `fitnessSignals.tuningDirection`.
- **`getNextSession()` is date-unaware** — returns earliest SCHEDULED/DEFERRED by weekNumber+dayOfWeek. HomeViewModel uses `getScheduledAndDeferredSessions()` + computed-date filtering. Do not regress to `getNextSession()` for display.
- **Session date:** `session.dayOfWeek` is ISO (1=Mon, 7=Sun), NOT positional offset. Compute via `enrollStartDate.with(DayOfWeek.MONDAY).plusWeeks(weekNumber-1).plusDays(dayOfWeek-1)`. The old `enrollStartDate + ((weekNumber-1)*7 + (dayOfWeek-1))` was wrong when enrollment didn't start Monday. Fixed 2026-04-12.
- **Manual-run CTA** — inline "Manual run →" in `RestDay` and `RunDone` hero states only. No global catch-all (caused duplication on rest days).
- **Sim workout bootcamp cleanup** — for sim, call `WorkoutState.setPendingBootcampSessionId(null)` immediately after `bootcampSessionCompleter.complete()`, before `WorkoutState.reset()`. `reset()` preserves `pendingBootcampSessionId`.
- **`PostRunSummaryViewModel`** injects `AdaptiveProfileRepository` and forwards `lastTuningDirection` to `bootcampSessionCompleter.complete()` (previously defaulted to HOLD).
- **Three `bootcampSessionCompleter.complete()` call sites:** PostRunSummaryViewModel (reads from AdaptiveProfileRepository), WFS sim path (reads from saved profile), BootcampViewModel.onWorkoutCompleted (reads from UI state). All three pass tuningDirection.

## Adaptive Engine Invariants

- **`AdaptivePaceController` is per-workout, not singleton** — new instance each run; state resets.
- **HRmax fallback** in WFS = `220 - age` when age known (`userProfileRepository.getAge()`); falls back to 180 only when age null.
- **hrMax dual-store sync complete** — all 6 write sites (Service, Setup, Bootcamp, BootcampSettings, PostRunSummary, Onboarding) sync both `UserProfileRepository` and `AdaptiveProfileRepository`. No gaps.
- **`hrSlopeBpmPerMin` clamp** = ±50 BPM/min (`slopeSampleClampBpmPerMin`, raised from ±30 on 2026-04-13 — sprint-onset slopes legitimately hit 47 BPM/min, and 3s minimum window already rejects BLE glitches). Do not lower below 40.
- **`hrSlopeBpmPerMin` decay on long gaps** — ×0.5 when `deltaMin > 1.5` (walk breaks, GPS-only, power saving). Prevents stale climb slope through long walks. Do not remove.
- **Slope EMA weights 0.60/0.40** (prev/inst). Do NOT revert to 0.75/0.25 — caused ~24s lag behind direction changes, giving wrong guidance during corrections.
- **Predictive coaching warmup gate = 90 s** — `PREDICTIVE_WARNING` suppressed for `elapsedSeconds < 90`. Slope EMA accumulates cardiovascular warmup as positive trend; without gate, returning runners hear "ease off" within 30s every run. Do not shorten.
- **PREDICTIVE_WARNING zone-entry grace (60s conditional reset)** — on any zone transition into IN_ZONE, if the 60s predictive cooldown was already expired, reset `lastPredictiveWarningTime = nowMs`. Prevents simultaneous firing with zone entry. **Conditional** — if cooldown has NOT expired, leave it alone so rapid oscillators (<60s cycles) retain their timer. 90s warmup gate is a separate independent guard.
- **`shortTermTrimBpm` error source = `lastBaseProjectedHr`** — NOT `lastProjectedHr`. Base = slope + longTermTrim + paceBias only (no shortTermTrim). Merging both causes compounding bias.
- **`responseLagSec` default = 38f in all three sites** — `AdaptiveProfile`, `WorkoutAdaptiveMetrics`, and `MetricsCalculator.deriveFromPaceSamples` param default. Previously latter two defaulted 25f (inert-horizon landmine: `lag × 0.4f = 10f` = minimum clamp). Do not revert.
- **`SubMaxHrEstimator` uses guarded upward-only margin** — sustained 2-min rolling peak must exceed current HRmax by `EVIDENCE_MARGIN_BPM = 2` before revision; new HRmax = `peak + 1`, capped at `HrMaxEstimator.inferenceCeiling(age)`. Old effort-fraction buckets (0.92/0.85/0.75) removed 2026-04-14 — they biased HRmax upward on moderate runs. Do NOT reintroduce buckets.
- **TRIMP formula** = `duration * avgHR * (avgHR/HRmax)^2` (non-standard — not Bannister's exponential). Consistent across codebase; don't "fix" to literature values.
- **`MetricsCalculator.trimpFrom(durationMin, avgHr, hrMax)`** is the named home. WFS is authoritative invoker (only it has active-run-time duration = pauses subtracted). Do not inline elsewhere.
- **TRIMP fallback `durationMin` uses active run time** — `(now - workoutStartMs - totalPausedMs - totalAutoPausedMs).coerceAtLeast(0) / 60_000f`. Do NOT revert to wall-clock.
- **`FitnessSignalEvaluator.efTrend`** uses least-squares regression slope scaled to total span (not endpoint delta). Slope = `(n*sumXY - sumX*sumY) / (n*sumX2 - sumX*sumX)`, then `× (n-1)` → total estimated change; comparable to old threshold 0.04. Robust to single-session outliers.
- **TSB thresholds are conjunctive-gated** — `TSB_PUSH_THRESHOLD = 5f` alone looks aggressive, but PUSH_HARDER requires BOTH `tsb > 5` AND `efTrend > 0.04` (3+ reliable sessions / 42 days). EF requirement is the real filter. Don't flag +5 as "aggressive" in isolation. EASE at -25 is unilateral (Friel's "overreached").
- **`TierCtlRanges`** is 0-indexed internal (Foundation/Development/Performance → spec T1/T2/T3). Intentionally extends spec at both ends: Foundation floor = 0 (new users with CTL<10), Performance ceiling = 200 (terminal, no promotion). Documented in `TierCtlRanges.kt` KDoc.
- **HRR1 pathway dormant** — illness tier permanently `NONE` because `hrr1Bpm` never computed (120s post-workout cool-down hold not implemented). BootcampVM/Screen illness UI is dead code tagged `TODO(HRR1)`. Don't delete schema fields — needed when feature lands.
- **Settle cap = 10 min** — `trackSettling()` window `2_000L..600_000L` ms. Do not lower to 5 min — structured interval excursions commonly run 6-10 min; settle data was silently dropped, preventing `responseLagSec` calibration for interval runners.
- **Settle-time averaging is direction-equal** — `responseLagSec = (settleDownAvg + settleUpAvg) / 2`. Each physiological direction equal weight regardless of event count. Do not switch to count-weighted — 8 quick corrections would drown 1 slow build-up, under-estimating upward lag.
- **`lookupPaceBias`** uses sampleCount-weighted neighbour average (not simple mean over up to 3 neighbours). A 1-sample anomalous bucket must not equal a 500-sample baseline.
- **`AdaptiveProfileRepository.resetLongTermTrim()`** zeroes `longTermHrTrimBpm` only; other fields unchanged. Not yet UI-wired. Use after heat-block/overtraining recovery when trim drifted to environmental baseline.
- **`CoachingEventRouter` has two alert-reset methods** — `noteExternalAlert(nowMs)` resets IN_ZONE_CONFIRM window; `resetPredictiveWarningTimer()` sets `lastPredictiveWarningTime = 0L`. Both called from `AlertPolicy.onAlert` in WFS. Do NOT conflate. `resetPredictiveWarningTimer()` is effectively overridden by the zone-entry conditional reset in realistic scenarios; retained for call-site clarity.
- **`TickResult.guidance` is non-nullable `String`** — guard `adaptiveResult?.guidance != null` is always true when `adaptiveResult` non-null. Preset overrides (strides, zone2) MUST appear BEFORE `adaptiveResult != null` in the WFS guidance `when` or they're dead code.
- **`finishSession` uses `savedInitialProfile.copy()`** — carries all `AdaptiveProfile` fields the controller doesn't own (hrMax, ctl, atl, hrRest, hrMaxIsCalibrated, hrMaxCalibratedAtMs, lastTuningDirection). Callers patch computed values on top. Do NOT construct a bare `AdaptiveProfile(longTermHrTrimBpm=..., ...)` — silently zeros un-named fields.
- **Cadence lock check uses explicit counter** — `hrSamplesSinceLastArtifactCheck`, NOT `hrSampleBuffer.size % 10`. Buffer cap is 120 (=12×10), so `size % 10 == 0` is permanently true at steady state, firing every tick.
- **`hrSampleSum`/`hrSampleCount` reset in `startWorkout()`** — not only `stopWorkout()`. `stopWorkout()` zeroes them before `observationJob` cancels; a race can add a sample to the zeroed sum and carry stale value into next session.
- **Science Constants Register** — every physiological constant/coefficient/threshold/formula in the adaptive engine has a provenance entry in `docs/plans/2026-04-14-science-constants-register.md`. Valid sources: `published`, `internal-rationale`, `intentional-non-standard`, `unsourced` (failure state). If you introduce/change/move a science constant, update the register in the same commit. Procedure + history: `docs/plans/2026-04-14-science-fidelity-audit-findings.md`.

## Test Fakes

- **`FakeBootcampDao`** (`BootcampSessionCompleterTest.kt`) directly implements `BootcampDao`. Adding DAO methods requires fake updates.
- **`AchievementDao` has TWO fakes:** `FakeAchievementDao` class in `AchievementEvaluatorTest.kt`, and an inline anonymous object in `BootcampSessionCompleterTest.kt:27`. Update both on DAO changes.
- **Room `@Insert` returns row ID** — declare `suspend fun insert(...): Long` when you need the auto-generated ID (e.g. for cloud sync). Entity in memory still has `id=0`.
- **Test timezone safety** — tests building epoch-millis via `LocalDate.atStartOfDay(ZoneId.of("UTC"))` must pass that zone to the function under test. Relying on `ZoneId.systemDefault()` fails west of UTC.

## Known Pre-existing Lint / Compile Errors

Not regressions:

- `BleHrManager.kt` MissingPermission; `WorkoutForegroundService.kt` MissingSuperCall; `NavGraph.kt` NewApi ×2; `build.gradle.kts` WrongGradleMethod.
- `PartnerSection.kt:294` — `WindowInsets` unresolved + `@Composable` scope errors. Blocks `assembleDebug`. Unrelated to UI polish work.
- `MainActivity` permission handling — `registerForActivityResult` handles permanent denial (Settings redirect) + temporary denial (Toast). `PermissionGate.missingRuntimePermissions()` checked in `onCreate`.

## Ralph Loop

- **Windows caveat:** `setup-ralph-loop.sh` fails on `(`, `×`, `&` — bash eval errors. Plain ASCII prompts only.
- **Invocation:** `Skill` with `skill: "ralph-loop:ralph-loop"`, simple `args` string (no parens/unicode). e.g. `args: "Implement feature per docs/plans/... - run tests after each task - output DONE --completion-promise DONE"`.
- Good for: well-defined TDD tasks with explicit file changes + test commands. Not for: UX design, brainstorming, human-judgment work (use `superpowers:brainstorming` first).

## ADB Data Backup Safety (CRITICAL — data loss risk)

**NEVER use `adb shell run-as ... cat <binary> > /tmp/local`** — Git Bash CR/LF translation silently corrupts SQLite DBs. File looks valid (correct size, SQLite header) but B-tree pages are mangled and unrecoverable.

**Safe backup:**
1. `adb shell "run-as com.hrcoach cp databases/hr_coach_db /data/local/tmp/hr_coach_backup.db"` (also `-shm`, `-wal`)
2. `adb pull //data/local/tmp/hr_coach_backup.db /tmp/hr_coach_backup.db` (double-slash prevents Git Bash path translation)
3. Verify: `sqlite3 /tmp/hr_coach_backup.db "SELECT COUNT(*) FROM workouts"`
4. Cleanup: `adb shell "rm /data/local/tmp/hr_coach_backup.db*"`

**Safe restore:**
1. `adb shell am force-stop com.hrcoach`
2. `adb push /tmp/hr_coach_backup.db //data/local/tmp/hr_coach_restore.db` (also `-shm`, `-wal`)
3. `adb shell "run-as com.hrcoach cp /data/local/tmp/hr_coach_restore.db databases/hr_coach_db"` (also `-shm`, `-wal`)
4. Cleanup, launch, verify.

**Git Bash path gotchas:**
- `adb push/pull`: prefix `//data/` (double-slash) — otherwise `/data/` → `C:/Program Files/Git/data/`.
- `firebase database:get /path`: prefix `MSYS_NO_PATHCONV=1`.
- `adb shell "..."` commands run on-device — unaffected.

## Firebase RTDB

Project: `cardea-1c8fc`. CLI: `MSYS_NO_PATHCONV=1 firebase database:get /path --project cardea-1c8fc --pretty`. Add `--shallow` for child keys only (survey large nodes). Write: `firebase database:set -d "value" -f /path`. Delete: `firebase database:remove -f /path` — use `-f` (force), not `--confirm` (doesn't exist).

Schema:
- `/users/{uid}` — `displayName`, `emblemId`, `fcmToken`, `partners: {uid: true}`, `activity: {lastRunDate, lastRunDurationMin, lastRunPhase, weeklyRunCount, currentStreak}`.
- `/users/{uid}/backup` — cloud backup root. `backupComplete: true` written last (absence = partial or pre-fix). Subtrees: `profile/`, `settings/`, `adaptive/` (key `paceHrBuckets`, not `buckets`), `workouts/`, `trackPoints/`, `metrics/`, `bootcamp/enrollment`, `bootcamp/sessions/`, `achievements/`. Track point keys abbreviated: `id`, `ts`, `lat`, `lng`, `hr`, `dist`, `alt` (see `CloudBackupManager.TpKeys`).
- `/invites/{code}` — `userId`, `displayName`, `createdAt`, `expiresAt`.
- Partner connections bidirectional: both users must have each other in `partners`.

**App Distribution:** `./gradlew assembleDebug appDistributionUploadDebug`. Testers via `testers` group in Firebase console (not hardcoded). `firebase login --reauth` needs interactive terminal.

**Versioning:** `versionCode` +1 per release, `versionName` semver (`0.x.0` pre-release). Both in `app/build.gradle.kts`. Release notes in `debug { firebaseAppDistribution { releaseNotes = "..." } }`.

**Deploy rules:** `firebase deploy --only database --project cardea-1c8fc` — no `.firebaserc`, always pass `--project`.

**Rules scope:** use `$wildcardVar !== $uid` (not `auth.uid`) when constraining by node identity. `auth.uid` breaks bidirectional writes where caller writes their own UID as a key under another user's node.

## MCP Servers

Registered in `.mcp.json`. See `.claude/rules/mobile-mcp.md` and `.claude/rules/github-mcp.md` for playbooks.

- **mobile-mcp** (`@mobilenext/mobile-mcp`) — screenshots, tap/swipe/type, accessibility tree, app install/launch/clear, logcat. Prereq: `adb devices` must show a device. Use after builds to verify UI.
- **GitHub MCP** — requires PAT with `repo`, `read:org`, `workflow` scopes in `.mcp.json` (replace placeholder). Use for PRs, CI status (`get_pull_request_status`), issue search; use local `git` for commits/branches/diffs.
- **Firebase MCP** — see Firebase RTDB section above.
- **Figma MCP** — via Cowork registry (OAuth). Not yet connected.
