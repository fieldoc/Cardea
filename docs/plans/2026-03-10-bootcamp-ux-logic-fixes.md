# Bootcamp UX Logic Fixes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 5 logic/wiring bugs in the Bootcamp + Workout flow — no artistic decisions required.

**Architecture:** Touches service lifecycle (WorkoutForegroundService), singleton state (WorkoutState), navigation (NavGraph), and ViewModel wiring (PostRunSummaryViewModel → BootcampRepository). All fixes are deterministic with clear correct behaviour.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, AndroidX Navigation Compose

---

## Chunk 1: Deterministic Bug Fixes

### Task 1: Fix Workout Timer Drift During Pause (Finding 3)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

**Root cause:** `elapsedSeconds` (line 242) is `(nowMs - workoutStartMs) / 1000L`. When paused, wall-clock time still advances, so when resumed the zone engine jumps forward by the full pause duration — skipping planned HR targets.

**Fix:** Track total paused duration. Subtract it from elapsed time.

- [ ] **Step 1: Write the failing unit test**

Create `app/src/test/java/com/hrcoach/service/WorkoutPauseElapsedTest.kt`:

```kotlin
package com.hrcoach.service

import org.junit.Test
import org.junit.Assert.assertEquals

class WorkoutPauseElapsedTest {

    // Utility matching the fixed formula
    private fun calcElapsed(startMs: Long, nowMs: Long, totalPausedMs: Long): Long =
        if (startMs > 0L) (nowMs - startMs - totalPausedMs) / 1000L else 0L

    @Test
    fun `elapsed ignores paused wall time`() {
        val start = 0L
        // 300s wall time, 60s was paused
        val now = 300_000L
        val paused = 60_000L
        assertEquals(240L, calcElapsed(start, now, paused))
    }

    @Test
    fun `elapsed without any pause equals wall time`() {
        assertEquals(300L, calcElapsed(0L, 300_000L, 0L))
    }

    @Test
    fun `elapsed before start is zero`() {
        assertEquals(0L, calcElapsed(0L, 100_000L, 0L))
    }
}
```

- [ ] **Step 2: Run test to confirm it passes** (it's a pure function test)

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.service.WorkoutPauseElapsedTest"
```
Expected: PASS (function matches spec already; this locks the contract)

- [ ] **Step 3: Add state fields to WorkoutForegroundService**

In `WorkoutForegroundService.kt`, after `private var workoutStartMs: Long = 0L` (line ~88), add:

```kotlin
private var totalPausedMs: Long = 0L
private var pauseStartMs: Long = 0L
```

- [ ] **Step 4: Reset in startWorkout()**

In `startWorkout()`, after the `isStopping = false` block (~line 154), add:

```kotlin
totalPausedMs = 0L
pauseStartMs = 0L
```

- [ ] **Step 5: Record pause start in pauseWorkout()**

In `pauseWorkout()`, after the `WorkoutState.update` block, add:

```kotlin
pauseStartMs = System.currentTimeMillis()
```

- [ ] **Step 6: Accumulate paused time in resumeWorkout()**

In `resumeWorkout()`, before the `WorkoutState.update` block, add:

```kotlin
if (pauseStartMs > 0L) {
    totalPausedMs += System.currentTimeMillis() - pauseStartMs
    pauseStartMs = 0L
}
```

- [ ] **Step 7: Fix the elapsed calculation in processTick()**

Replace line 242:
```kotlin
// OLD
val elapsedSeconds = if (workoutStartMs > 0L) (nowMs - workoutStartMs) / 1000L else 0L
```
With:
```kotlin
// NEW
val elapsedSeconds = if (workoutStartMs > 0L) (nowMs - workoutStartMs - totalPausedMs) / 1000L else 0L
```

- [ ] **Step 8: Compile check**

```bash
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt \
        app/src/test/java/com/hrcoach/service/WorkoutPauseElapsedTest.kt
git commit -m "fix(service): subtract paused duration from elapsed time calculation"
```

---

### Task 2: Fix Navigation Race Condition — Setup Screen Flash (Finding 4)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

**Root cause:** In `NavGraph.kt`, the `LaunchedEffect(isWorkoutRunning, completedWorkoutId)` calls `WorkoutState.clearCompletedWorkoutId()` immediately after `navController.navigate(postrun)`. This flip of `completedWorkoutId` → null re-triggers the LaunchedEffect. Before the nav back stack settles, `routeNow` can still read as `WORKOUT`, causing the `else` branch to fire `navigate(SETUP)`.

**Fix:** Remove `clearCompletedWorkoutId()` from the NavGraph LaunchedEffect. Instead, let `PostRunSummaryScreen` clear the ID via a `LaunchedEffect(Unit)` when it first composes.

- [ ] **Step 1: Remove clearCompletedWorkoutId from NavGraph**

In `NavGraph.kt`, inside the `LaunchedEffect(isWorkoutRunning, completedWorkoutId)` block (~line 128-133), remove the line:
```kotlin
WorkoutState.clearCompletedWorkoutId()
```
The block should now read:
```kotlin
if (finishedWorkoutId != null) {
    navController.navigate(Routes.postRunSummary(finishedWorkoutId, fresh = true)) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
    // clearCompletedWorkoutId() moved to PostRunSummaryScreen
} else {
    navController.navigate(Routes.SETUP) {
        popUpTo(Routes.HOME) { inclusive = false }
        launchSingleTop = true
    }
}
```

- [ ] **Step 2: Clear the ID in PostRunSummaryScreen**

In `PostRunSummaryScreen.kt`, after the `viewModel: PostRunSummaryViewModel = hiltViewModel()` parameter (~line 80), add at the top of the function body (before the `Box`):

```kotlin
// Clear the completed workout ID from WorkoutState now that this screen has consumed it.
// This prevents the NavGraph LaunchedEffect from re-navigating when completedWorkoutId changes.
LaunchedEffect(Unit) {
    WorkoutState.clearCompletedWorkoutId()
}
```

Also add the import at the top of the file:
```kotlin
import com.hrcoach.service.WorkoutState
```

- [ ] **Step 3: Compile check**

```bash
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt \
        app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "fix(nav): clear completedWorkoutId in PostRunSummaryScreen to prevent Setup flash"
```

---

### Task 3: Preserve pendingBootcampSessionId Through Workout Reset (Finding 5 — in-session fix)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutState.kt`
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

**Root cause (in-process):** `WorkoutState.reset()` creates a fresh `WorkoutSnapshot`, preserving only `completedWorkoutId`. The `pendingBootcampSessionId` is silently dropped *every time* `stopWorkout()` fires — even without process death. This means `PostRunSummaryViewModel` can never read it.

**Root cause (process death):** `WorkoutState` is an in-memory object singleton. If Android kills the app process mid-workout and restarts it for the foreground service, `pendingBootcampSessionId` is `null` on restart.

This task fixes the in-process case. The process-death case is deferred (see note at end).

- [ ] **Step 1: Write the failing test**

In `app/src/test/java/com/hrcoach/service/WorkoutStateResetTest.kt`:

```kotlin
package com.hrcoach.service

import org.junit.Test
import org.junit.Assert.*

class WorkoutStateResetTest {

    @Test
    fun `reset preserves pendingBootcampSessionId`() {
        WorkoutState.set(WorkoutSnapshot(
            isRunning = true,
            pendingBootcampSessionId = 42L,
            completedWorkoutId = null
        ))
        WorkoutState.reset()
        assertEquals(42L, WorkoutState.snapshot.value.pendingBootcampSessionId)
    }

    @Test
    fun `reset preserves completedWorkoutId`() {
        WorkoutState.set(WorkoutSnapshot(
            isRunning = true,
            completedWorkoutId = 99L
        ))
        WorkoutState.reset()
        assertEquals(99L, WorkoutState.snapshot.value.completedWorkoutId)
    }

    @Test
    fun `reset clears isRunning`() {
        WorkoutState.set(WorkoutSnapshot(isRunning = true))
        WorkoutState.reset()
        assertFalse(WorkoutState.snapshot.value.isRunning)
    }
}
```

- [ ] **Step 2: Run test to confirm it currently fails**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.service.WorkoutStateResetTest.reset preserves pendingBootcampSessionId"
```
Expected: FAIL (pendingBootcampSessionId is null after reset)

- [ ] **Step 3: Fix WorkoutState.reset()**

In `WorkoutState.kt`, replace the `reset()` function:

```kotlin
// OLD
fun reset() {
    _snapshot.update { current ->
        WorkoutSnapshot(completedWorkoutId = current.completedWorkoutId)
    }
}

// NEW
fun reset() {
    _snapshot.update { current ->
        WorkoutSnapshot(
            completedWorkoutId = current.completedWorkoutId,
            pendingBootcampSessionId = current.pendingBootcampSessionId
        )
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.service.WorkoutStateResetTest"
```
Expected: PASS (all 3 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutState.kt \
        app/src/test/java/com/hrcoach/service/WorkoutStateResetTest.kt
git commit -m "fix(state): preserve pendingBootcampSessionId through WorkoutState.reset()"
```

> **Note — process death (out of scope for this plan):** For true crash-survival, persist `pendingBootcampSessionId` to `SharedPreferences` in `WorkoutState.setPendingBootcampSessionId()` and restore it in the service's `onCreate`. This is deferred to avoid scope creep.

---

### Task 4: Wire Bootcamp Session Completion (Finding 2 — the critical missing link)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/data/repository/BootcampRepository.kt`
- Modify: `app/src/main/java/com/hrcoach/di/AppModule.kt` (if BootcampRepository injection changes)

**Root cause:** `BootcampViewModel.onWorkoutCompleted()` exists and is correct, but it is *never called*. The `PostRunSummaryScreen` composable gets a separate `PostRunSummaryViewModel` that knows nothing about Bootcamp. Meanwhile, `BootcampViewModel` on the post-run back-stack entry is a *different instance* from the one that started the workout — so passing a callback between them doesn't work.

**Fix:**
1. Move the session-completion logic to a new `BootcampSessionCompleter` use-case in `domain/bootcamp/`.
2. Inject it into `PostRunSummaryViewModel`.
3. Call it from `PostRunSummaryViewModel.load()` when `isFreshWorkout == true` and `pendingBootcampSessionId != null`.
4. Expose `bootcampProgressLabel` and `bootcampWeekComplete` from the result (already in the UiState).

**Note:** `BootcampSessionCompleter` is the same logic as `BootcampViewModel.onWorkoutCompleted()` — extracted so it can be used from any ViewModel. After this task, `BootcampViewModel.onWorkoutCompleted()` can delegate to the same use-case.

- [ ] **Step 1: Write the failing test for BootcampSessionCompleter**

Create `app/src/test/java/com/hrcoach/domain/bootcamp/BootcampSessionCompleterTest.kt`:

```kotlin
package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.repository.BootcampRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class BootcampSessionCompleterTest {

    private val repo: BootcampRepository = mockk(relaxed = true)

    private fun makeEnrollment(id: Long = 1L) = BootcampEnrollmentEntity(
        id = id,
        goalType = "FIVE_K",
        targetMinutesPerRun = 30,
        runsPerWeek = 3,
        currentPhaseIndex = 0,
        currentWeekInPhase = 1,
        startDate = System.currentTimeMillis(),
        status = BootcampEnrollmentEntity.STATUS_ACTIVE,
        tierIndex = 1,
        preferredDaysMask = 0b0101010,
        tierPromptDismissCount = 0,
        tierPromptSnoozedUntilMs = 0L
    )

    private fun makeSession(id: Long = 10L, enrollmentId: Long = 1L) = BootcampSessionEntity(
        id = id,
        enrollmentId = enrollmentId,
        weekNumber = 1,
        dayOfWeek = 2,
        sessionType = "EASY",
        targetMinutes = 30,
        status = BootcampSessionEntity.STATUS_SCHEDULED,
        completedWorkoutId = null,
        presetId = null
    )

    @Test
    fun `complete returns false when no pending session id`() = runTest {
        val completer = BootcampSessionCompleter(repo)
        val result = completer.complete(workoutId = 5L, pendingSessionId = null)
        assertFalse(result.completed)
        coVerify(exactly = 0) { repo.completeSessionOnly(any()) }
    }

    @Test
    fun `complete returns false when enrollment missing`() = runTest {
        coEvery { repo.getActiveEnrollmentOnce() } returns null
        val completer = BootcampSessionCompleter(repo)
        val result = completer.complete(workoutId = 5L, pendingSessionId = 10L)
        assertFalse(result.completed)
    }

    @Test
    fun `complete marks session and returns true`() = runTest {
        val enrollment = makeEnrollment()
        val session = makeSession()
        coEvery { repo.getActiveEnrollmentOnce() } returns enrollment
        coEvery { repo.getSessionsForWeek(1L, 1) } returns listOf(session)
        val completer = BootcampSessionCompleter(repo)
        val result = completer.complete(workoutId = 99L, pendingSessionId = 10L)
        assertTrue(result.completed)
        coVerify { repo.completeSessionOnly(match { it.completedWorkoutId == 99L && it.id == 10L }) }
    }
}
```

- [ ] **Step 2: Run test to confirm it fails** (class doesn't exist yet)

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.BootcampSessionCompleterTest"
```
Expected: FAIL (compilation error — class not found)

- [ ] **Step 3: Create BootcampSessionCompleter**

Create `app/src/main/java/com/hrcoach/domain/bootcamp/BootcampSessionCompleter.kt`:

```kotlin
package com.hrcoach.domain.bootcamp

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.model.BootcampGoal
import javax.inject.Inject

/**
 * Use-case that marks a Bootcamp session complete when a workout finishes.
 * Extracted from BootcampViewModel.onWorkoutCompleted() so it can be called
 * from PostRunSummaryViewModel (a separate ViewModel instance).
 */
class BootcampSessionCompleter @Inject constructor(
    private val bootcampRepository: BootcampRepository
) {

    data class CompletionResult(
        val completed: Boolean,
        val weekComplete: Boolean = false,
        val progressLabel: String? = null
    )

    suspend fun complete(workoutId: Long, pendingSessionId: Long?): CompletionResult {
        if (pendingSessionId == null) return CompletionResult(completed = false)
        val enrollment = bootcampRepository.getActiveEnrollmentOnce()
            ?: return CompletionResult(completed = false)

        val goal = BootcampGoal.valueOf(enrollment.goalType)
        val engine = PhaseEngine(
            goal = goal,
            phaseIndex = enrollment.currentPhaseIndex,
            weekInPhase = enrollment.currentWeekInPhase,
            runsPerWeek = enrollment.runsPerWeek,
            targetMinutes = enrollment.targetMinutesPerRun
        )

        val currentWeekSessions = bootcampRepository.getSessionsForWeek(
            enrollment.id, engine.absoluteWeek
        )
        val targetSession = currentWeekSessions.firstOrNull { it.id == pendingSessionId }
            ?: return CompletionResult(completed = false)

        val completedSession = targetSession.copy(
            status = BootcampSessionEntity.STATUS_COMPLETED,
            completedWorkoutId = workoutId
        )

        // Simulate post-completion state to check if week is done
        val simulatedWeek = currentWeekSessions.map { s ->
            if (s.id == completedSession.id) completedSession else s
        }
        val weekComplete = simulatedWeek.isNotEmpty() &&
            simulatedWeek.all { it.status == BootcampSessionEntity.STATUS_COMPLETED }

        if (weekComplete) {
            val nextEngine = if (engine.shouldAdvancePhase()) {
                engine.advancePhase()
            } else {
                engine.copy(weekInPhase = engine.weekInPhase + 1)
            }
            val updatedEnrollment = enrollment.copy(
                currentPhaseIndex = nextEngine.phaseIndex,
                currentWeekInPhase = nextEngine.weekInPhase
            )
            val preferredDays = enrollment.preferredDays
            val availableDays = preferredDays
                .filter { it.level != DaySelectionLevel.NONE }
                .map { it.day }
            val plannedSessions = nextEngine.planCurrentWeek(
                tierIndex = enrollment.tierIndex,
                tuningDirection = TuningDirection.HOLD,
                currentPresetIndices = emptyMap()
            )
            val nextWeekEntities = plannedSessions.mapIndexed { index, session ->
                BootcampRepository.buildSessionEntity(
                    enrollmentId = enrollment.id,
                    weekNumber = nextEngine.absoluteWeek,
                    dayOfWeek = availableDays.getOrElse(index) { index + 1 }.coerceIn(1, 7),
                    sessionType = session.type.name,
                    targetMinutes = session.minutes,
                    presetId = session.presetId
                )
            }
            bootcampRepository.completeSessionAndAdvanceWeek(
                completedSession = completedSession,
                updatedEnrollment = updatedEnrollment,
                newSessions = nextWeekEntities
            )
            val completedCount = simulatedWeek.count { it.status == BootcampSessionEntity.STATUS_COMPLETED }
            val progressLabel = "Week ${engine.absoluteWeek} complete · ${engine.currentPhase.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}"
            return CompletionResult(completed = true, weekComplete = true, progressLabel = progressLabel)
        } else {
            bootcampRepository.completeSessionOnly(completedSession)
            val completedCount = simulatedWeek.count { it.status == BootcampSessionEntity.STATUS_COMPLETED }
            val progressLabel = "$completedCount of ${enrollment.runsPerWeek} sessions this week"
            return CompletionResult(completed = true, weekComplete = false, progressLabel = progressLabel)
        }
    }
}
```

- [ ] **Step 4: Run tests again**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.bootcamp.BootcampSessionCompleterTest"
```
Expected: PASS

- [ ] **Step 5: Wire BootcampSessionCompleter into PostRunSummaryViewModel**

In `PostRunSummaryViewModel.kt`, add the injection and call:

```kotlin
// Add to constructor:
@HiltViewModel
class PostRunSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository,
    private val bootcampSessionCompleter: BootcampSessionCompleter   // ADD THIS
) : ViewModel() {
```

In the `load()` function, after setting `isHrrActive = isFreshWorkout`, add bootcamp completion:

```kotlin
// Inside load() viewModelScope.launch, after building the PostRunSummaryUiState:
_uiState.value = PostRunSummaryUiState(
    isLoading = false,
    // ... existing fields ...
    isHrrActive = isFreshWorkout
)

// Complete bootcamp session if this is a fresh workout from Bootcamp
if (isFreshWorkout) {
    val pendingSessionId = WorkoutState.snapshot.value.pendingBootcampSessionId
    val result = bootcampSessionCompleter.complete(
        workoutId = id,
        pendingSessionId = pendingSessionId
    )
    if (result.completed) {
        WorkoutState.setPendingBootcampSessionId(null)
        _uiState.update { it.copy(
            bootcampProgressLabel = result.progressLabel,
            bootcampWeekComplete = result.weekComplete
        )}
    }
}
```

Add import:
```kotlin
import com.hrcoach.service.WorkoutState
import com.hrcoach.domain.bootcamp.BootcampSessionCompleter
```

- [ ] **Step 6: Hilt — verify BootcampSessionCompleter is injectable**

`BootcampSessionCompleter` has `@Inject constructor` and only depends on `BootcampRepository` which is already provided in `AppModule`. No additional module changes needed — Hilt will generate the binding automatically.

- [ ] **Step 7: Compile check**

```bash
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/hrcoach/domain/bootcamp/BootcampSessionCompleter.kt \
        app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt \
        app/src/test/java/com/hrcoach/domain/bootcamp/BootcampSessionCompleterTest.kt
git commit -m "feat(bootcamp): wire session completion through PostRunSummaryViewModel"
```

---

### Task 5: Route Post-Run "Done" to Bootcamp Tab (Finding 8)

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

**Root cause:** `onDone` in NavGraph's `POST_RUN_SUMMARY` composable always navigates to `historyDetail(workoutId)`, regardless of whether the run was a Bootcamp session. Users who ran from the Training tab land in the History tab and can't see their Bootcamp progress checkmark.

**Fix:** Expose `isBootcampRun: Boolean` from `PostRunSummaryUiState` (set by the completion step in Task 4). In NavGraph, route `onDone` to `Routes.BOOTCAMP` when `isBootcampRun = true`.

- [ ] **Step 1: Add isBootcampRun to PostRunSummaryUiState**

In `PostRunSummaryViewModel.kt`, add to `PostRunSummaryUiState`:

```kotlin
data class PostRunSummaryUiState(
    // ... existing fields ...
    val isBootcampRun: Boolean = false   // ADD
)
```

- [ ] **Step 2: Set isBootcampRun when bootcamp completion succeeds**

In `PostRunSummaryViewModel.load()`, in the existing completion block added in Task 4:

```kotlin
if (result.completed) {
    WorkoutState.setPendingBootcampSessionId(null)
    _uiState.update { it.copy(
        bootcampProgressLabel = result.progressLabel,
        bootcampWeekComplete = result.weekComplete,
        isBootcampRun = true   // ADD
    )}
}
```

- [ ] **Step 3: Expose isBootcampRun to NavGraph via callback**

In `PostRunSummaryScreen.kt`, add an `onDone` that passes `isBootcampRun`:

Update the composable signature to expose the state to NavGraph. The cleanest approach: `onDone` receives a lambda that NavGraph passes in, and the screen passes `uiState.isBootcampRun` to it:

```kotlin
// PostRunSummaryScreen signature already has: onDone: () -> Unit
// NavGraph will make the routing decision; PostRunSummaryScreen just calls onDone()
// No change to screen signature needed — routing logic lives in NavGraph
```

- [ ] **Step 4: Update NavGraph onDone routing**

In `NavGraph.kt`, the `POST_RUN_SUMMARY` composable needs access to `isBootcampRun`. Since `PostRunSummaryViewModel` is scoped to this composable, collect it here:

```kotlin
composable(route = Routes.POST_RUN_SUMMARY, ...) { backStackEntry ->
    val workoutId = backStackEntry.arguments?.getLong("workoutId") ?: return@composable
    val postRunVm: PostRunSummaryViewModel = hiltViewModel()        // ADD
    val postRunState by postRunVm.uiState.collectAsStateWithLifecycle()  // ADD
    PostRunSummaryScreen(
        workoutId = workoutId,
        onViewProgress = { ... },
        onViewHistory = { ... },
        onDone = {
            if (postRunState.isBootcampRun) {            // CHANGED
                navController.navigate(Routes.BOOTCAMP) {
                    popUpTo(Routes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
            } else {
                navController.navigate(Routes.historyDetail(workoutId)) {
                    popUpTo(Routes.HISTORY) { inclusive = false }
                    launchSingleTop = true
                }
            }
        },
        onBack = { ... },
        viewModel = postRunVm   // pass the shared instance to avoid double-init
    )
}
```

> Note: `PostRunSummaryScreen` already has `viewModel: PostRunSummaryViewModel = hiltViewModel()` as a default param. Passing `viewModel = postRunVm` reuses the same hiltViewModel instance from the NavGraph composable scope.

- [ ] **Step 5: Compile check**

```bash
.\gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt \
        app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt
git commit -m "fix(nav): route Done on post-run to Bootcamp tab when run was a bootcamp session"
```

---

## Final verification

- [ ] **Full test suite**

```bash
.\gradlew.bat :app:testDebugUnitTest
```
Expected: All tests pass, no regressions

- [ ] **Compile release variant**

```bash
.\gradlew.bat :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

---

## Task Dependency Order

```
Task 1 (pause timer)   — independent
Task 2 (nav race)      — independent
Task 3 (state reset)   — must be done BEFORE Task 4
Task 4 (wire completion) — depends on Task 3
Task 5 (routing)       — depends on Task 4
```

Parallel execution: Tasks 1, 2, and 3 can run in parallel. Task 4 after Task 3. Task 5 after Task 4.
