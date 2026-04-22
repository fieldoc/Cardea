# WorkoutForegroundService Lifecycle & Races

Load when touching `WorkoutForegroundService`, `WorkoutState`, `WorkoutNotificationHelper`, `AutoPauseDetector`, or anything called from `processTick`/`onHrTick`.

## Notification stop gate

`WorkoutNotificationHelper.stop()` MUST be called before every `stopForeground(STOP_FOREGROUND_REMOVE)`. Without it a late `processTick()` re-posts the notification. `@Volatile stopped` flag short-circuits `update()`. Three call sites in WFS: normal stop, short-run discard, error handler.

## WorkoutState / Same-Tick Race

- Never read `WorkoutState.snapshot.value` in the same function that called `WorkoutState.update{}` — `MutableStateFlow.update` may not have propagated. Use a local var. (Fixed in `onHrTick()` autopause 2026-04-10.)
- **`MutableStateFlow.update` import** — `import kotlinx.coroutines.flow.update` is NOT pulled in by `kotlinx.coroutines.flow.*` in some Kotlin versions; import explicitly.
- **`pauseWorkout()` ordering** — capture `pauseStartMs = clock.now()` BEFORE `WorkoutState.update { isPaused = true }`. If captured after, IO-thread `processTick()` can see `isPaused=true` while `pauseStartMs=0` → resume skips accumulation → stale timestamp on next pause.

## Dual Pause Overlap

Manual pause and auto-pause can be simultaneous. Three guards keep `elapsedSeconds` correct; removing any one → double-subtraction:

1. **`pauseWorkout()`:** if `isAutoPaused`, latch `totalAutoPausedMs += nowMs - autoPauseStartMs` and zero `autoPauseStartMs`.
2. **`resumeWorkout()`:** if `isAutoPaused` still true on manual resume, restart `autoPauseStartMs = nowMs`.
3. **`AutoPauseEvent.RESUMED` handler:** guard `if (autoPauseStartMs > 0L)` before accumulating — `pauseWorkout()` may have zeroed it.

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
