# Bootcamp Scheduling

Load when touching `BootcampRepository`, `BootcampViewModel`, `BootcampScreen`, `SessionSelector`, `PhaseEngine`, `BootcampSessionCompleter`, or session generation.

## Phases

- **`TrainingPhase` enum:** BASE, BUILD, PEAK, TAPER, EVERGREEN. Race goals: BASE→BUILD→PEAK→TAPER. CARDIO_HEALTH: BASE→EVERGREEN.
- **EVERGREEN** — perpetual 4-week micro-cycle (A Tempo, B Strides, C Intervals/Hills for tier 2+, D Recovery). Never graduates.

## Session selection

- **`SessionSelector.weekSessions()`** accepts `weekInPhase`, `absoluteWeek`, `isRecoveryWeek` (EVERGREEN rotation + tier-dependent recovery).
- **Interval variety:** PEAK and EVERGREEN alternate by `absoluteWeek % 2` (race: norwegian_4x4/hill_repeats; evergreen: hill_repeats/hiit_30_30).
- **Recovery composition:** tier 0-1 all-easy; tier 2+ downgrade (interval→tempo, tempo→strides). EVERGREEN handles its own recovery on week D.
- **Tempo presets** (`aerobic_tempo`, `lactate_threshold`) use DISTANCE_PROFILE: 10min WU / 20min main / 5min CD.

## Recovery cadence

- **`PhaseEngine.isRecoveryWeek()`** accepts optional `TuningDirection`. Cadence: EASE_BACK=2w, HOLD/null=3w, PUSH_HARDER=4w. `weeksUntilNextRecovery()` simulates phase transitions (walks `advancePhase()` boundaries); EVERGREEN → distance to week D of the 4-week cycle. All call sites in `BootcampViewModel` pass `fitnessSignals.tuningDirection`.
- **`WorkoutConfig.isRecoveryWeek`** — set in `BootcampViewModel.buildWorkoutConfig` via `PhaseEngine.isRecoveryWeek(tuningDirection)`, stamped on both preset and free-run `WorkoutConfig.copy(...)` paths. Default `false` for non-bootcamp launches. Consumed by `WorkoutForegroundService.processTick` to gate below-zone alerts (see `audio-pipeline.md`) and by `BootcampScreen` (`RecoveryWeekDisclosure` chip + briefing audio prefix).

## Date handling

- **`getNextSession()` is date-unaware** — returns earliest SCHEDULED/DEFERRED by weekNumber+dayOfWeek. HomeViewModel uses `getScheduledAndDeferredSessions()` + computed-date filtering. Do not regress to `getNextSession()` for display.
- **Session date computation:** `session.dayOfWeek` is ISO (1=Mon, 7=Sun), NOT positional offset. Compute via `enrollStartDate.with(DayOfWeek.MONDAY).plusWeeks(weekNumber-1).plusDays(dayOfWeek-1)`. The old `enrollStartDate + ((weekNumber-1)*7 + (dayOfWeek-1))` was wrong when enrollment didn't start Monday. Fixed 2026-04-12.

## Session completion

- **Three `bootcampSessionCompleter.complete()` call sites:** PostRunSummaryViewModel (reads lastTuningDirection from AdaptiveProfileRepository), WFS sim path (reads from saved profile), BootcampViewModel.onWorkoutCompleted (reads from UI state). All three pass tuningDirection.
- **`PostRunSummaryViewModel`** injects `AdaptiveProfileRepository` and forwards `lastTuningDirection` to `bootcampSessionCompleter.complete()` (previously defaulted to HOLD).
- **Sim workout cleanup** — for sim, call `WorkoutState.setPendingBootcampSessionId(null)` immediately after `bootcampSessionCompleter.complete()`, before `WorkoutState.reset()`. `reset()` preserves `pendingBootcampSessionId`.
- **Bootcamp session identity contract:** `prepareStartWorkout()` resolves DB session ID and sets `WorkoutState.pendingBootcampSessionId` immediately. `onBootcampWorkoutStarting()` is fallback only. Do NOT use "first uncompleted" heuristic — wrong when sessions started out of order.

## UI

- **Manual-run CTA** — inline "Manual run →" in `RestDay` and `RunDone` hero states only. No global catch-all (caused duplication on rest days).
