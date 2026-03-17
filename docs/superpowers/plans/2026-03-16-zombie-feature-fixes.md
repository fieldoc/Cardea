# Zombie Feature Fixes — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all zombie features, dead wires, and broken call chains identified in `docs/system-audit-2026-03-16.md`

**Architecture:** The fixes are organized into 10 tasks. Tasks 1-3 fix critical (RED) issues and **must run sequentially** (they share `PostRunSummaryViewModel` and `BootcampSessionCompleter`). Tasks 4-10 are independent of each other and of Tasks 1-3.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt DI, Room, WorkManager, SharedPreferences (Gson)

**Test command:** `./gradlew testDebugUnitTest`

---

## Task 1: Wire bootcamp session completion (RED #1 + GREEN #14 + GREEN #18)

The `PostRunSummaryViewModel` is the natural place to trigger completion — it already loads when a workout finishes, has access to `WorkoutState.pendingBootcampSessionId`, and its test file already mocks this flow.

Also fixes GREEN #14 (`completedAtMs` never set on completed sessions) and GREEN #18 (dead `bootcampProgressLabelArg` nav args).

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/BootcampSessionCompleter.kt`
- Modify: `app/src/test/java/com/hrcoach/ui/postrun/PostRunSummaryViewModelTest.kt`

- [ ] **Step 1: Fix `completedAtMs` in BootcampSessionCompleter.complete() (GREEN #14)**

In `BootcampSessionCompleter.complete()`, when building the `completedSession`, also set `completedAtMs`:

```kotlin
val completedSession = targetSession.copy(
    status = BootcampSessionEntity.STATUS_COMPLETED,
    completedWorkoutId = workoutId,
    completedAtMs = System.currentTimeMillis()  // ADD — was only set on skipped sessions
)
```

- [ ] **Step 2: Add `BootcampSessionCompleter` injection to PostRunSummaryViewModel**

```kotlin
class PostRunSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository,
    private val bootcampSessionCompleter: BootcampSessionCompleter  // ADD
) : ViewModel() {
```

- [ ] **Step 3: Remove dead `bootcampProgressLabelArg` / `bootcampWeekCompleteArg` nav args (GREEN #18)**

Remove the two `savedStateHandle.get<>()` properties for `bootcampProgressLabel` and `bootcampWeekComplete` — these nav args are never passed. The values will come from `BootcampSessionCompleter.complete()` instead.

- [ ] **Step 4: Add completion call in `load()` after metrics are loaded**

Inside `load()`, after the existing `_uiState.value = PostRunSummaryUiState(...)` assignment, add:

```kotlin
val fresh = savedStateHandle.get<Boolean>("fresh") ?: false
if (fresh) {
    val pendingId = WorkoutState.snapshot.value.pendingBootcampSessionId
    if (pendingId != null) {
        val result = bootcampSessionCompleter.complete(
            workoutId = id,
            pendingSessionId = pendingId
        )
        if (result.completed) {
            WorkoutState.setPendingBootcampSessionId(null)
            _uiState.value = _uiState.value.copy(
                isBootcampRun = true,
                bootcampProgressLabel = result.progressLabel,
                bootcampWeekComplete = result.weekComplete
            )
        }
    }
}
```

- [ ] **Step 5: Update test `buildVm()` to pass the new constructor param**

The test file mocks `bootcampSessionCompleter` (line 39) but `buildVm()` (line 83) doesn't pass it. Update:

```kotlin
private fun buildVm(fresh: Boolean = true) = PostRunSummaryViewModel(
    savedStateHandle = SavedStateHandle(mapOf("workoutId" to 1L, "fresh" to fresh)),
    workoutRepository = workoutRepository,
    workoutMetricsRepository = workoutMetricsRepository,
    bootcampSessionCompleter = bootcampSessionCompleter
)
```

- [ ] **Step 6: Run tests**

`./gradlew testDebugUnitTest --tests "com.hrcoach.ui.postrun.PostRunSummaryViewModelTest"`

- [ ] **Step 7: Commit**

```
feat: wire bootcamp session completion from PostRunSummaryViewModel

Previously onWorkoutCompleted() existed on BootcampViewModel but was
never called. Now PostRunSummaryViewModel triggers completion directly
via BootcampSessionCompleter when a fresh post-run summary loads with
a pending bootcamp session ID. Also sets completedAtMs on completed
sessions and removes dead bootcamp nav args.
```

---

## Task 2: Wire bootcamp notifications (RED #2)

`BootcampNotificationManager` is fully implemented. It just needs to be injected and called at the right lifecycle points. We inject into `BootcampViewModel` (UI layer) rather than `BootcampSessionCompleter` (domain layer) to avoid a cross-layer dependency on Android `Context`.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampViewModel.kt`

- [ ] **Step 1: Inject BootcampNotificationManager into BootcampViewModel**

Add to constructor:

```kotlin
private val notificationManager: BootcampNotificationManager
```

- [ ] **Step 2: Schedule reminders on enrollment creation**

In the enrollment creation flow (after `ensureCurrentWeekSessions` or initial session generation), call:

```kotlin
notificationManager.createNotificationChannel()
val sessions = bootcampRepository.getSessionsForWeek(enrollment.id, engine.absoluteWeek)
notificationManager.scheduleWeekReminders(
    enrollmentId = enrollment.id,
    weekNumber = engine.absoluteWeek,
    sessions = sessions,
    startDateMs = enrollment.startDateMs
)
```

- [ ] **Step 3: Schedule reminders after week completion triggers new sessions**

In the code path that detects week completion (after `bootcampSessionCompleter.complete()` returns `weekComplete = true` in Task 1's wiring from PostRunSummaryViewModel, or in `refreshFromEnrollment()`), schedule reminders for the new week. Since completion now happens in PostRunSummaryViewModel, do this in `refreshFromEnrollment()` which runs when the BootcampScreen next loads:

```kotlin
// In refreshFromEnrollment(), after getting currentWeekSessions:
notificationManager.createNotificationChannel()
notificationManager.scheduleWeekReminders(
    enrollmentId = enrollment.id,
    weekNumber = engine.absoluteWeek,
    sessions = currentWeekSessions,
    startDateMs = enrollment.startDateMs
)
```

- [ ] **Step 4: Cancel on bootcamp deletion**

In the delete bootcamp flow, call `notificationManager.cancelAll(enrollmentId)`.

- [ ] **Step 5: Run tests**

`./gradlew testDebugUnitTest --tests "com.hrcoach.ui.bootcamp.BootcampViewModelTest"`

- [ ] **Step 6: Commit**

```
feat: wire bootcamp notification reminders via WorkManager

BootcampNotificationManager was fully implemented but never injected.
Now schedules day-before reminders when sessions are created (on
enrollment and on each refresh). Cancels on bootcamp deletion.
```

---

## Task 3: Wire remaining achievement types + new-achievement notification (RED #3)

**Depends on:** Task 1 (PostRunSummaryViewModel already has `bootcampSessionCompleter`).

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryUiState` (inside same file or separate)
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/BootcampSessionCompleter.kt`
- Modify: `app/src/main/java/com/hrcoach/data/db/BootcampDao.kt` (add query)
- Modify: `app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt` (add method)
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`
- Modify: `app/src/test/java/com/hrcoach/ui/postrun/PostRunSummaryViewModelTest.kt`

- [ ] **Step 1: Inject AchievementEvaluator and AchievementDao into PostRunSummaryViewModel**

Both are needed: evaluator for awarding, DAO for the unshown query. The test already mocks both.

```kotlin
class PostRunSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository,
    private val bootcampSessionCompleter: BootcampSessionCompleter,  // from Task 1
    private val achievementEvaluator: AchievementEvaluator,          // ADD
    private val achievementDao: AchievementDao                       // ADD
) : ViewModel() {
```

- [ ] **Step 2: Call `evaluateDistance()` in `load()` after workout is loaded**

```kotlin
// In load(), after loading the workout, compute total distance and evaluate:
val allWorkouts = workoutRepository.getAllWorkoutsOnce()
val totalKm = allWorkouts.sumOf { it.totalDistanceMeters.toDouble() } / 1000.0
achievementEvaluator.evaluateDistance(totalKm, id)
```

- [ ] **Step 3: Add `computeWorkoutStreak()` helper and call `evaluateStreak()`**

Add an `internal` helper (not private — so it can be tested):

```kotlin
internal fun computeWorkoutStreak(workouts: List<WorkoutEntity>): Int {
    val sorted = workouts.sortedByDescending { it.startTime }
    if (sorted.isEmpty()) return 0
    var streak = 1
    for (i in 0 until sorted.lastIndex) {
        val gapDays = (sorted[i].startTime - sorted[i + 1].startTime) / 86_400_000L
        if (gapDays > 10) break
        streak++
    }
    return streak
}
```

Call it in `load()`:

```kotlin
val streak = computeWorkoutStreak(allWorkouts)
achievementEvaluator.evaluateStreak(streak, id)
```

- [ ] **Step 4: Add `countConsecutiveCompletedWeeks()` to BootcampDao and BootcampRepository**

This method does not yet exist. Add to `BootcampDao`:

```kotlin
@Query("""
    SELECT weekNumber FROM bootcamp_sessions
    WHERE enrollmentId = :enrollmentId
    GROUP BY weekNumber
    HAVING COUNT(*) = SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END)
    ORDER BY weekNumber DESC
""")
suspend fun getCompletedWeekNumbers(enrollmentId: Long): List<Int>
```

Add to `BootcampRepository`:

```kotlin
suspend fun countConsecutiveCompletedWeeks(enrollmentId: Long): Int {
    val weeks = bootcampDao.getCompletedWeekNumbers(enrollmentId)
    if (weeks.isEmpty()) return 0
    var count = 1
    for (i in 0 until weeks.lastIndex) {
        if (weeks[i] - weeks[i + 1] != 1) break
        count++
    }
    return count
}
```

- [ ] **Step 5: Inject AchievementEvaluator into BootcampSessionCompleter and call `evaluateWeeklyGoalStreak()`**

Update constructor (after Task 2, it already has `bootcampRepository`):

```kotlin
class BootcampSessionCompleter @Inject constructor(
    private val bootcampRepository: BootcampRepository,
    private val achievementEvaluator: AchievementEvaluator  // ADD
) {
```

In `complete()`, in the `weekComplete` branch after `completeSessionAndAdvanceWeek()`:

```kotlin
val completedWeeks = bootcampRepository.countConsecutiveCompletedWeeks(enrollment.id)
achievementEvaluator.evaluateWeeklyGoalStreak(completedWeeks, workoutId)
```

- [ ] **Step 6: Wire new-achievement display in PostRunSummaryUiState**

Add `newAchievements: List<AchievementEntity> = emptyList()` to `PostRunSummaryUiState`.

After all evaluations in `load()`:

```kotlin
val newAchievements = achievementDao.getUnshownAchievements()
if (newAchievements.isNotEmpty()) {
    achievementDao.markShown(newAchievements.map { it.id })
    _uiState.value = _uiState.value.copy(newAchievements = newAchievements)
}
```

- [ ] **Step 7: Add achievement display in PostRunSummaryScreen**

Show a celebratory card for each new achievement (reuse the existing `AchievementCard` composable from `ui/components/`).

- [ ] **Step 8: Update test `buildVm()` with all new constructor params**

```kotlin
private fun buildVm(fresh: Boolean = true) = PostRunSummaryViewModel(
    savedStateHandle = SavedStateHandle(mapOf("workoutId" to 1L, "fresh" to fresh)),
    workoutRepository = workoutRepository,
    workoutMetricsRepository = workoutMetricsRepository,
    bootcampSessionCompleter = bootcampSessionCompleter,
    achievementEvaluator = achievementEvaluator,
    achievementDao = achievementDao
)
```

- [ ] **Step 9: Run tests**

`./gradlew testDebugUnitTest --tests "com.hrcoach.ui.postrun.*"`

- [ ] **Step 10: Commit**

```
feat: wire distance, streak, weekly-goal achievements + new-achievement display

evaluateDistance and evaluateStreak are now called from
PostRunSummaryViewModel after each workout. evaluateWeeklyGoalStreak
is called from BootcampSessionCompleter on week completion.
New achievements are shown on the post-run summary screen.
Adds countConsecutiveCompletedWeeks to BootcampDao/Repository.
```

---

## Task 4: Fix hrMax dual-write desync (BUG #8)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupViewModel.kt`

- [ ] **Step 1: Inject AdaptiveProfileRepository into AccountViewModel**

Add `private val adaptiveProfileRepository: AdaptiveProfileRepository` to the constructor.

- [ ] **Step 2: Sync hrMax in `saveMaxHr()`**

After `userProfileRepo.setMaxHr(value)`, add:

```kotlin
val profile = adaptiveProfileRepository.getProfile()
adaptiveProfileRepository.saveProfile(profile.copy(hrMax = value))
```

- [ ] **Step 3: Inject AdaptiveProfileRepository into SetupViewModel**

Add to constructor if not already present.

- [ ] **Step 4: Sync hrMax in `confirmMaxHr()`**

After `userProfileRepository.setMaxHr(value)`, add the same adaptive profile sync.

- [ ] **Step 5: Commit**

```
fix: sync hrMax to AdaptiveProfile from Account and Setup screens

Previously only BootcampSettingsViewModel wrote hrMax to both
UserProfileRepository and AdaptiveProfileRepository. Account and
Setup screen edits were silently ignored by the workout engine.
```

---

## Task 5: Remove `hrr1Bpm` dead wire (YELLOW #4)

The HRR measurement feature was never implemented. Remove the dead branches rather than implementing a complex post-workout measurement system.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/engine/FitnessSignalEvaluator.kt`

- [ ] **Step 1: Remove HRR1-based branches from FitnessSignalEvaluator**

Remove the `hrr1Bpm`-reading code paths (illness detection based on HRR1 drop). These branches never fire because the field is always null. Keep the entity column (removing a Room column requires a migration and the field is harmless as null).

- [ ] **Step 2: Run tests**

`./gradlew testDebugUnitTest --tests "com.hrcoach.domain.engine.FitnessSignalEvaluatorTest"`

Update any tests that assert on HRR1-based behavior.

- [ ] **Step 3: Commit**

```
chore: remove inert hrr1Bpm branches from FitnessSignalEvaluator

hrr1Bpm is never computed — the post-workout HRR measurement was
planned but never implemented. The illness detection branches that
read this field were dead code. Column retained in schema to avoid
migration.
```

---

## Task 6: Fix `paceAtRefHrMinPerKm` — pass targetHr to MetricsCalculator (YELLOW #5)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

- [ ] **Step 1: Pass targetHr to `deriveFullMetrics()`**

At line ~542 in `stopWorkout()`, the call to `MetricsCalculator.deriveFullMetrics()` omits `targetHr`. Add it using the adaptive profile's hrMax or the workout's steady-state target:

```kotlin
val canonicalMetrics = MetricsCalculator.deriveFullMetrics(
    workoutId = workoutId,
    recordedAtMs = now,
    trackPoints = trackPoints,
    targetHr = workoutConfig.steadyStateTargetHr?.toFloat()
        ?: adaptiveProfileRepository.getProfile().hrMax?.toFloat()
)
```

- [ ] **Step 2: Run tests**

`./gradlew testDebugUnitTest`

- [ ] **Step 3: Commit**

```
fix: pass targetHr to MetricsCalculator so paceAtRefHrMinPerKm is populated

The Progress screen's "pace at fixed HR" trend was always empty
because no targetHr was passed. Now uses the workout's steady-state
target or the calibrated hrMax.
```

---

## Task 7: Wire `tuningDirection` through PhaseEngine to SessionSelector (YELLOW #7)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/PhaseEngine.kt`
- Modify: `app/src/main/java/com/hrcoach/domain/bootcamp/SessionSelector.kt`

- [ ] **Step 1: Forward tuningDirection in PhaseEngine.planCurrentWeek()**

```kotlin
fun planCurrentWeek(
    tierIndex: Int = 0,
    tuningDirection: TuningDirection = TuningDirection.HOLD,
    currentPresetIndices: Map<String, Int> = emptyMap()
): List<PlannedSession> {
    val effectiveMinutes = if (isRecoveryWeek) {
        (targetMinutes * 0.8f).toInt()
    } else {
        targetMinutes
    }
    return SessionSelector.weekSessions(
        phase = currentPhase,
        goal = goal,
        runsPerWeek = runsPerWeek,
        targetMinutes = effectiveMinutes,
        tierIndex = tierIndex,
        tuningDirection = tuningDirection  // ADD
    )
}
```

- [ ] **Step 2: Accept and use tuningDirection in SessionSelector.weekSessions()**

Add parameter and apply a duration scaling factor:

```kotlin
fun weekSessions(
    phase: TrainingPhase,
    goal: BootcampGoal,
    runsPerWeek: Int,
    targetMinutes: Int,
    tierIndex: Int = 1,
    tuningDirection: TuningDirection = TuningDirection.HOLD  // ADD
): List<PlannedSession> {
    val tuningFactor = when (tuningDirection) {
        TuningDirection.PUSH -> 1.05f    // 5% harder
        TuningDirection.EASE_BACK -> 0.90f  // 10% easier
        TuningDirection.HOLD -> 1.0f
    }
    val tunedMinutes = (targetMinutes * tuningFactor).toInt()
    // ... rest uses tunedMinutes instead of effectiveMinutes for durations
```

- [ ] **Step 3: Update tests**

`./gradlew testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.*"`

- [ ] **Step 4: Commit**

```
feat: forward tuningDirection from PhaseEngine to SessionSelector

The adaptive system's PUSH/EASE_BACK/HOLD signal was computed and
stored but silently dropped before reaching session generation.
Now applies a ±5-10% duration adjustment based on the signal.
```

---

## Task 8: Remove `FitnessEvaluator` dead wire (YELLOW #6)

Rather than removing the computation, display it — it's useful information.

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt`

- [ ] **Step 1: Display fitnessLevel in the bootcamp hero section**

Add a small chip or label showing the fitness level in the bootcamp screen's header area. `BootcampUiState.fitnessLevel` is already populated — just read it.

```kotlin
// In the hero section of BootcampScreen, after the phase/week display:
if (state.fitnessLevel != FitnessLevel.UNKNOWN) {
    Text(
        text = "Fitness: ${state.fitnessLevel.name.lowercase().replaceFirstChar { it.uppercase() }}",
        style = MaterialTheme.typography.labelSmall,
        color = CardeaThemeTokens.TextSecondary
    )
}
```

- [ ] **Step 2: Run tests**

`./gradlew testDebugUnitTest`

- [ ] **Step 3: Commit**

```
feat: display fitness level label on bootcamp screen

FitnessEvaluator.assess() was computed every refresh but never
displayed. Now shows as a label in the bootcamp hero section.
```

---

## Task 9: Remove `adaptiveLagSec` dead field (GREEN #9)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutState.kt`
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

- [ ] **Step 1: Remove `adaptiveLagSec` from WorkoutSnapshot**

Remove the field declaration. Remove the two write sites in `WorkoutForegroundService.kt` (the `.copy(adaptiveLagSec = ...)` at lines ~240 and ~381).

- [ ] **Step 2: Run tests to verify nothing breaks**

`./gradlew testDebugUnitTest`

- [ ] **Step 3: Commit**

```
chore: remove dead adaptiveLagSec field from WorkoutSnapshot

Written every tick but never read by any UI or ViewModel. The lag
value is used internally by AdaptivePaceController and surfaced
in post-run metrics — the live snapshot field was redundant.
```

---

## Task 10: Performance — add distinctUntilChanged to NavGraph and HomeViewModel (PERF)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt`

- [ ] **Step 1: NavGraph — extract only needed fields**

Replace the full snapshot collection with a derived state:

```kotlin
// Before:
val workoutSnapshot by WorkoutState.snapshot.collectAsStateWithLifecycle()
val isWorkoutRunning = workoutSnapshot.isRunning
val completedWorkoutId = workoutSnapshot.completedWorkoutId

// After:
data class NavWorkoutState(val isRunning: Boolean, val completedId: Long?, val isPaused: Boolean)
val navState by WorkoutState.snapshot
    .map { NavWorkoutState(it.isRunning, it.completedWorkoutId, it.isPaused) }
    .distinctUntilChanged()
    .collectAsStateWithLifecycle(NavWorkoutState(false, null, false))
val isWorkoutRunning = navState.isRunning
val completedWorkoutId = navState.completedId
```

- [ ] **Step 2: HomeViewModel — extract only `isRunning`**

```kotlin
// Before:
combine(
    workoutRepository.getAllWorkouts(),
    bootcampRepository.getActiveEnrollment(),
    WorkoutState.snapshot
) { workouts, enrollment, snapshot -> ... }

// After:
combine(
    workoutRepository.getAllWorkouts(),
    bootcampRepository.getActiveEnrollment(),
    WorkoutState.snapshot.map { it.isRunning }.distinctUntilChanged()
) { workouts, enrollment, isRunning -> ... }
```

Update the `flatMapLatest` lambda to use `isRunning` (Boolean) instead of `snapshot.isRunning`.

- [ ] **Step 3: Run tests**

`./gradlew testDebugUnitTest`

- [ ] **Step 4: Commit**

```
perf: reduce WorkoutSnapshot recomposition in NavGraph and HomeViewModel

NavGraph collected the full snapshot but only read 3 fields — every
HR tick triggered Scaffold recomposition. HomeViewModel only needed
isRunning but was re-triggering combine+flatMapLatest (with DB queries)
every tick. Both now use map+distinctUntilChanged.
```
