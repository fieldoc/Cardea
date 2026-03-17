# Cardea System Audit — 2026-03-16

Full codebase audit for zombie features: values computed but never consumed,
features wired in schema but never called, and broken call chains.

---

## Severity Legend

| Rating | Meaning |
|--------|---------|
| 🔴 RED | Feature appears functional but is fundamentally broken — user thinks it works, it doesn't |
| 🟡 YELLOW | Dead wire — computed/stored but never consumed. Wastes CPU or confuses future maintainers |
| 🟢 GREEN | Minor — unused field or parameter, no user-facing impact |
| ⚡ BUG | Correctness issue — produces wrong results in production |

---

## Critical Findings (RED)

### 🔴 1. Bootcamp session completion is phantom
**Files:** `BootcampViewModel.onWorkoutCompleted()`, `BootcampSessionCompleter`

`onWorkoutCompleted()` exists as a `suspend fun` on `BootcampViewModel` and correctly
calls `BootcampSessionCompleter.complete()` to mark sessions done, advance the enrollment,
and seed next-week sessions. **But nothing ever calls `onWorkoutCompleted()`.** No NavGraph
route, no PostRun screen, no service triggers it.

The user can start bootcamp workouts (via `onBootcampWorkoutStarting()`), but completing
them never marks the session as completed. The entire completion → week-advance →
next-week-generation flow is dead.

**Impact:** Bootcamp sessions stay SCHEDULED forever. Progress never advances. The user
sees a schedule that never updates after workouts.

**Fix:** Wire `onWorkoutCompleted()` — either from PostRunSummaryScreen's onDone callback,
or from a `WorkoutState.completedWorkoutId` observer that detects when a bootcamp-linked
workout finishes.

---

### 🔴 2. Bootcamp notifications are phantom
**Files:** `BootcampNotificationManager`, `BootcampReminderWorker`

Both classes are fully implemented with Hilt annotations. `BootcampNotificationManager`
has `scheduleWeekReminders()`, `cancelAll()`, `createNotificationChannel()`, and
`scheduleResumeReminder()`. `BootcampReminderWorker` handles notification display
with permission checks.

**Neither is ever injected or called.** No ViewModel, Service, or Activity requests
`BootcampNotificationManager` as a dependency. `WorkManager.enqueueUniqueWork()` is
never called for the worker.

**Impact:** Users never receive bootcamp session reminders. The reminder toggle in
settings (if any) does nothing.

**Fix:** Inject `BootcampNotificationManager` into `BootcampViewModel` or
`BootcampSessionCompleter` and call `scheduleWeekReminders()` when sessions are
generated/rescheduled.

---

### 🔴 3. Three of five achievement types are phantom
**Files:** `AchievementEvaluator`, `BootcampViewModel`

Only 2 of 5 achievement types are ever awarded:
- ✅ `TIER_GRADUATION` — awarded on tier-up (BootcampViewModel:611)
- ✅ `BOOTCAMP_GRADUATION` — awarded on graduation (BootcampViewModel:736)
- ❌ `DISTANCE_MILESTONE` — `evaluateDistance()` exists, never called
- ❌ `STREAK_MILESTONE` — `evaluateStreak()` exists, never called
- ❌ `WEEKLY_GOAL_STREAK` — `evaluateWeeklyGoalStreak()` exists, never called

The "new achievement" notification flow is also phantom: `getUnshownAchievements()` and
`markShown()` DAO methods exist but are never called. No toast, dialog, or badge
notifies the user of newly earned achievements.

**Impact:** Users can only earn 2 achievement types. Distance and streak milestones
are never recognized. Users never get notified when they earn anything.

**Fix:** Call `evaluateDistance()` and `evaluateStreak()` from
`WorkoutForegroundService.stopWorkout()` after saving the workout. Call
`evaluateWeeklyGoalStreak()` from `BootcampSessionCompleter.complete()` (once that
is itself wired). Wire the `shown` notification flow from `PostRunSummaryViewModel`
or a global achievement observer.

---

## Significant Dead Wires (YELLOW)

### 🟡 4. `hrr1Bpm` — illness detection is inert
**Files:** `WorkoutMetricsEntity`, `FitnessSignalEvaluator`

The DB column exists (added in a migration). `FitnessSignalEvaluator` reads `hrr1Bpm`
to detect illness signals (`IllnessSignalTier.SOFT/FULL`) and trigger `EASE_BACK`
tuning direction. **But `hrr1Bpm` is never computed or written.** No code measures
heart rate recovery. The field is always `null`.

**Impact:** The entire HRR1-based illness detection system is inert. `FitnessSignalEvaluator`
can never fire its HRR1 code branches.

**Fix:** Either implement post-workout HRR measurement (the `PostRunSummaryViewModel`
already has `isHrrActive` countdown logic suggesting this was planned), or remove
the dead branches from `FitnessSignalEvaluator` and the DB column.

---

### 🟡 5. `paceAtRefHrMinPerKm` — always null
**Files:** `MetricsCalculator`, `WorkoutForegroundService`, `ProgressViewModel`

`MetricsCalculator.calculatePaceAtHr()` can compute this, but it requires a `targetHr`
parameter. Neither `WorkoutForegroundService.deriveFullMetrics()` nor
`AdaptivePaceController.finishSession()` passes a `targetHr`. The field is always `null`
in the DB.

`ProgressViewModel` reads it to build a "pace at fixed HR" trend series — but the
series is always empty.

**Fix:** Pass the workout's target HR (or a reference HR from the adaptive profile) to
`MetricsCalculator.deriveFullMetrics()`. Or remove the dead Progress chart series.

---

### 🟡 6. `FitnessEvaluator` output is dead
**Files:** `FitnessEvaluator`, `BootcampViewModel`, `BootcampUiState`

`FitnessEvaluator.assess()` is called every refresh. The result `fitnessLevel`
(`UNKNOWN/BEGINNER/INTERMEDIATE/ADVANCED`) is stored in `BootcampUiState.fitnessLevel`.
**The field is never read by `BootcampScreen.kt`** — zero references.

**Impact:** Fitness level is computed but invisible to the user.

**Fix:** Display it in the bootcamp UI (e.g., in the hero card or settings), or remove
the computation.

---

### 🟡 7. `tuningDirection` is dropped by PhaseEngine
**Files:** `PhaseEngine.planCurrentWeek()`, `SessionSelector.weekSessions()`

`PhaseEngine.planCurrentWeek()` accepts a `tuningDirection` parameter (from
`AdaptiveProfile.lastTuningDirection`). It is **never forwarded** to
`SessionSelector.weekSessions()`, which is where session difficulty is actually
determined. The parameter is silently dropped.

**Impact:** The adaptive system evaluates fitness signals and computes a tuning
direction (HARDER/EASIER/MAINTAIN), stores it to the profile, but it never
influences actual session content.

**Fix:** Forward `tuningDirection` to `SessionSelector.weekSessions()` and use it
to adjust duration, intensity, or session composition.

---

## Bugs (not dead wires, but incorrect behavior)

### ⚡ 8. `hrMax` dual-write desync
**Files:** `AccountViewModel`, `SetupViewModel`, `BootcampSettingsViewModel`,
`AdaptiveProfileRepository`, `UserProfileRepository`

`hrMax` is stored in TWO places:
- `UserProfileRepository` (SharedPrefs, key `max_hr`)
- `AdaptiveProfile.hrMax` (JSON via `AdaptiveProfileRepository`)

Only `BootcampSettingsViewModel.saveSettings()` writes to **both** stores.
`AccountViewModel.saveMaxHr()` and `SetupViewModel.confirmMaxHr()` write
**only to `UserProfileRepository`**. The workout engine reads
`AdaptiveProfile.hrMax` for TRIMP scoring and zone calculations.

**Impact:** If the user changes maxHr from the Account or Setup screen, the
workout engine silently ignores it. Only changes through Bootcamp Settings
take effect for the engine.

**Fix:** `AccountViewModel.saveMaxHr()` and `SetupViewModel.confirmMaxHr()`
must also update `AdaptiveProfileRepository`.

---

## Minor Findings (GREEN)

### 🟢 9. `WorkoutSnapshot.adaptiveLagSec` — dead field
Written every tick by the service. Never read by any UI composable or ViewModel.
The lag value *is* used internally by `AdaptivePaceController` (projection horizon)
and shown in post-run summary via `WorkoutMetricsEntity.responseLagSec` — but the
live snapshot field is never rendered.

**Fix:** Remove field and the two write sites (service lines 240, 381).

### 🟢 10. `BleHrManager.connectionFailed` — dead flow
`StateFlow<Boolean>` set to `true` when all 5 reconnect attempts are exhausted.
Never surfaced to any UI. User sees HR=0 and "No Signal" but never gets an
explicit "reconnection failed, try re-pairing" message.

**Fix:** Forward through `BleConnectionCoordinator` and show a UI prompt.

### 🟢 11. `TrackPointEntity.altitudeMeters` — never written
Column exists in the schema with a default of `null`. `TrackPointRecorder.saveIfNeeded()`
never populates it. No UI reads it.

**Fix:** Either populate from `Location.altitude` in the recorder, or drop the column.

### 🟢 12. `UserProfileRepository.userId` — dead field
Auto-generated UUID stored on first access. Never read by any consumer.

**Fix:** Remove, or save for future analytics/cloud sync.

### 🟢 13. `BootcampSessionEntity.presetIndex` — dead column
DB column exists (via migration). Never written during session creation, never read.

**Fix:** Remove the column or populate it in `buildSessionEntity()`.

### 🟢 14. `BootcampSessionEntity.completedAtMs` — inconsistently populated
Set only on *skipped* sessions (via `swapSessionToRestDay`/`dropSession`), never on
actually completed sessions. Never read by any code.

**Fix:** Set it in `BootcampSessionCompleter.complete()` (once that's wired) and
use it for streak/progress calculations.

### 🟢 15. `AchievementEntity.triggerWorkoutId` — never set for awarded types
The two achievement types that are actually awarded (`TIER_GRADUATION`,
`BOOTCAMP_GRADUATION`) both pass `null` for this field. Only the phantom
types would set it.

### 🟢 16. `AchievementDao.getAchievementsByType()` — unused query
DAO method exists, never called from production code.

### 🟢 17. `VoiceCoach.guidanceText` parameter — mostly dead
The `guidanceText` String parameter on `speak()` is accepted but discarded for
9 of 10 coaching events. Only `KM_SPLIT` at `FULL` verbosity uses it (to select
a numbered audio resource). The system uses pre-recorded audio, not TTS.

### 🟢 18. `PostRunSummaryViewModel` bootcamp nav args — dead wires
`bootcampProgressLabel` and `bootcampWeekComplete` are read from `SavedStateHandle`
but the navigation route `postrun/{workoutId}?fresh={fresh}` never passes these
arguments.

---

## Performance Concerns (not dead wires)

### `NavGraph` collects full WorkoutSnapshot
Reads only `isRunning`, `completedWorkoutId`, `isPaused` — but every HR tick
(~1/sec) triggers Scaffold recomposition. Should use `.map { }.distinctUntilChanged()`.

### `HomeViewModel` collects full WorkoutSnapshot
Reads only `isRunning` but every tick re-triggers the `combine + flatMapLatest`
pipeline, which includes suspend calls to `BootcampRepository` — redundant DB
queries ~1/sec during active workouts.

---

## System Health Summary

| System | Health | Critical Issues |
|--------|--------|-----------------|
| **Adaptive Trim** | 🟢 Healthy | `adaptiveLagSec` dead field (minor) |
| **Zone Engine** | 🟢 Healthy | — |
| **Audio Coaching** | 🟢 Healthy | `guidanceText` param mostly unused (minor) |
| **Metrics Pipeline** | 🟡 Mostly healthy | `hrr1Bpm` and `paceAtRefHrMinPerKm` dead |
| **BLE + GPS** | 🟢 Healthy | `connectionFailed`, `altitude` minor dead wires |
| **Auto-Pause** | 🟢 Healthy | Fully functional end-to-end |
| **Bootcamp Core** | 🔴 Broken | Session completion never fires; notifications phantom |
| **Bootcamp Scheduling** | 🟢 Healthy | PhaseEngine, DurationScaler, GapAdvisor all work |
| **Achievements** | 🔴 Mostly phantom | 3/5 types never awarded; no "new" notification |
| **Settings** | 🟡 Mostly healthy | hrMax desync bug |
| **WorkoutSnapshot** | 🟢 Healthy | 1 dead field out of 18 |
