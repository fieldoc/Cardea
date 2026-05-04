# WorkoutForegroundService Lifecycle & Races

Load when touching `WorkoutForegroundService`, `WorkoutState`, `WorkoutNotificationHelper`, `AutoPauseDetector`, `PauseTracker`, or anything called from `processTick`/`onHrTick`.

## PauseTracker owns all pause math

`domain/engine/PauseTracker.kt` is the single source of truth for `pauseStartMs`, `totalPausedMs`, `autoPauseStartMs`, `totalAutoPausedMs`, and the `activeMs(now)` formula used for the live elapsed display, end-of-run `activeDurationSeconds`, and TRIMP `durationMin`. WFS never touches those fields directly — it calls `pauseTracker.manualPause/manualResume/autoPause/autoResume/disableAutoPause` and reads `activeSeconds(now)` / `isAutoPaused`. Tests live in `PauseTrackerTest.kt`; if you change pause behaviour, change them there first.

`isManuallyPaused` gates auto events. `autoPause()` and `autoResume()` are no-ops while the user is manually paused — auto-pause windows that overlap a manual pause have no meaning (the manual pause already covers the whole span) and processing them double-subtracts at manual resume. Workout #33 (2026-05-03) hit this: an 88-minute run reported `activeDurationSeconds=221` because the detector was firing during long manual pauses. Don't re-introduce auto accumulation while manually paused.

In `pauseWorkout()`, `autoPauseDetector?.reset()` is called after `pauseTracker.manualPause()` so the detector starts a clean confirmation window when the manual pause ends — otherwise a stale `isAutoPaused=true` inside the detector would fire RESUMED on the very next tick after manual resume.

## Notification stop gate

`WorkoutNotificationHelper.stop()` MUST be called before every `stopForeground(STOP_FOREGROUND_REMOVE)`. Without it a late `processTick()` re-posts the notification. `@Volatile stopped` flag short-circuits `update()`. Three call sites in WFS: normal stop, short-run discard, error handler.

## WorkoutState / Same-Tick Race

- Never read `WorkoutState.snapshot.value` in the same function that called `WorkoutState.update{}` — `MutableStateFlow.update` may not have propagated. Use a local var. (Fixed in `onHrTick()` autopause 2026-04-10.)
- **`MutableStateFlow.update` import** — `import kotlinx.coroutines.flow.update` is NOT pulled in by `kotlinx.coroutines.flow.*` in some Kotlin versions; import explicitly.
- **`pauseWorkout()` ordering** — capture `pauseStartMs = clock.now()` BEFORE `WorkoutState.update { isPaused = true }`. If captured after, IO-thread `processTick()` can see `isPaused=true` while `pauseStartMs=0` → resume skips accumulation → stale timestamp on next pause.

## Dual Pause Overlap

Manual pause and auto-pause can be simultaneous. PauseTracker enforces all four invariants — removing any one → double-subtraction or worse:

1. **`manualPause()` Guard 1:** if `isAutoPaused`, latch `totalAutoPausedMs += nowMs - autoPauseStartMs`, zero `autoPauseStartMs`, **and clear `isAutoPaused=false`**. Without the flag clear, `disableAutoPause()` from a later toggle-off reads stale state and (without guard #4) computes `clock.now() - 0` ≈ 1.78 trillion ms.
2. **`manualResume()` Guard 2:** if `isAutoPaused` still true on manual resume, restart `autoPauseStartMs = nowMs` so the eventual auto-resume doesn't re-count time already inside the manual window.
3. **`autoResume()` Guard 3:** check `if (autoPauseStartMs > 0L)` before accumulating — Guard 1 may have zeroed it.
4. **`autoPause()` / `autoResume()` early return:** both no-op when `isManuallyPaused` is true. The autopause-detection block in `processTick` is *also* gated on `!pauseTracker.isManuallyPaused` so the detector itself doesn't churn through PAUSED/RESUMED while the user is on a bench.
5. **`disableAutoPause()` (toggle-off) guard:** check `if (isAutoPaused && autoPauseStartMs > 0L)` before accumulating. Calling unguarded after Guard 1 has zeroed `autoPauseStartMs` adds clock.now() to the running total and clamps `activeMs` to 0 for the rest of the run.

## Auto-pause Startup Gates

Auto-pause firing in the first seconds of a run is user-hostile. Three AND-gated conditions must all be true before `AutoPauseDetector.update()` is consulted:

1. **`sessionAutoPauseEnabled`** — user preference (`AutoPauseSettingsRepository`).
2. **`nowMs >= autoPauseGraceUntilMs`** — wall-time grace, `AUTO_PAUSE_GRACE_MS = 20_000L` (raised from 15s 2026-04-20; covers stop-and-go starts like crossing traffic).
3. **`hasMovedSinceStart`** — latches true on first `tick.speed > 0.5f` (≈1.8 km/h). "Paused from movement" is only meaningful after actual movement; prevents pause tones while the runner is still tying shoes or pocketing the phone.

All three reset in `startWorkout()`. Also: `autoPauseCountThisSession` counter — first auto-pause of a session plays tone + banner but skips the "Run autopaused" TTS announcement (surprise management). Subsequent pauses announce normally.

## Startup Guidance Branching

Initial `WorkoutState.guidanceText` set after the countdown block in `startWorkout()`:

- `SimulationController.isActive` → "SIM STARTING"
- `bleCoordinator.isConnected.value` (true at this point for users who connected on Setup) → "Get set"
- Otherwise → "Searching for HR signal"

Unconditionally setting "Searching for HR signal" causes a one-frame flash for users who connected pre-run — the first `processTick` then overwrites with real guidance. Do not regress the branch.

## WorkoutTick nullables

`WorkoutTick.speed` is `Float?` (nullable). Comparisons need `?: 0f` coercion — `tick.speed > 0.5f` is a compile error. `tick.hr` is non-nullable `Int`; `tick.connected` is `Boolean`.
