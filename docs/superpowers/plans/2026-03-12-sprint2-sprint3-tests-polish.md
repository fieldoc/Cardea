# Sprint 2+3: Test Coverage & Polish Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add ViewModel test coverage for the 4 most critical ViewModels, add MetricsCalculator edge-case tests, run a typography token pass on 3 UI files, and wire live sensor status to the Home screen.

**Architecture:** Sprint 2 adds mockk to test dependencies, then writes ViewModel tests that mock repositories and verify state emissions via Turbine. Sprint 3 mechanically replaces ad-hoc `Color.White.copy(alpha=...)` with tokenized constants, then wires persisted BLE device info into HomeViewModel/HomeScreen.

**Tech Stack:** Kotlin, JUnit 4, mockk, Turbine, kotlinx-coroutines-test, Jetpack Compose, DataStore

---

## File Map

### Sprint 2 — Test Coverage

| Action | File | Purpose |
|--------|------|---------|
| Modify | `app/build.gradle.kts` | Add mockk + turbine test dependencies |
| Create | `app/src/test/java/com/hrcoach/ui/home/HomeViewModelTest.kt` | HomeViewModel state emission tests |
| Create | `app/src/test/java/com/hrcoach/ui/bootcamp/BootcampViewModelTest.kt` | Bootcamp enrollment + onboarding tests |
| Create | `app/src/test/java/com/hrcoach/ui/postrun/PostRunSummaryViewModelTest.kt` | Post-run completion + achievement tests |
| Create | `app/src/test/java/com/hrcoach/domain/engine/MetricsCalculatorEdgeCaseTest.kt` | Resting HR proxy + efficiency edge cases |

### Sprint 3 — Polish

| Action | File | Purpose |
|--------|------|---------|
| Modify | `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt` | Replace Color.White.copy → tokens (16 occurrences) |
| Modify | `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt` | Replace Color.White.copy → tokens (3 occurrences) |
| Modify | `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt` | Replace Color.White.copy → tokens (4 occurrences) |
| Modify | `app/src/main/java/com/hrcoach/ui/theme/Color.kt` | Add missing token constants |
| Modify | `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt` | Add sensor status fields to HomeUiState |
| Modify | `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt` | Show sensor status indicator |
| Modify | `app/src/main/java/com/hrcoach/service/BleHrManager.kt` | Persist last device info to SharedPreferences |

---

## Task Dependency Order

```
Chunk 1 (mockk + HomeVM test)     — independent
Chunk 2 (BootcampVM test)         — depends on Chunk 1 (mockk)
Chunk 3 (PostRunVM test)          — depends on Chunk 1 (mockk)
Chunk 4 (MetricsCalculator tests) — independent
Chunk 5 (typography tokens)       — independent
Chunk 6 (live sensor status)      — independent
```

Parallel execution: Chunks 1 + 4 + 5 can run in parallel. Chunks 2 + 3 after Chunk 1. Chunk 6 independent.

---

## Chunk 1: Add Test Dependencies + HomeViewModel Tests

### Task 1: Add mockk and Turbine to test dependencies

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add mockk and Turbine dependencies**

In the `dependencies` block of `app/build.gradle.kts`, after the existing `testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")`, add:

```kotlin
testImplementation("io.mockk:mockk:1.13.13")
testImplementation("app.cash.turbine:turbine:1.2.0")
```

- [ ] **Step 2: Sync and compile**

```bash
./gradlew.bat :app:compileDebugUnitTestKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add mockk and Turbine test dependencies"
```

---

### Task 2: HomeViewModel tests

**Files:**
- Create: `app/src/test/java/com/hrcoach/ui/home/HomeViewModelTest.kt`

**Context:** `HomeViewModel` injects `WorkoutRepository` and `BootcampRepository`. Its `uiState` is a reactive `StateFlow` built from `combine(workoutRepo.getAllWorkouts(), bootcampRepo.getActiveEnrollment(), WorkoutState.snapshot).flatMapLatest { ... }.stateIn(...)`. Key things to test: greeting, streak computation, next session detection, bootcamp enrollment detection, and `isSessionRunning` flag.

- [ ] **Step 1: Write HomeViewModelTest**

Create `app/src/test/java/com/hrcoach/ui/home/HomeViewModelTest.kt`:

```kotlin
package com.hrcoach.ui.home

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.service.WorkoutSnapshot
import com.hrcoach.service.WorkoutState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val workoutRepository: WorkoutRepository = mockk(relaxed = true)
    private val bootcampRepository: BootcampRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        WorkoutState.set(WorkoutSnapshot())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        WorkoutState.set(WorkoutSnapshot())
    }

    private fun buildVm(): HomeViewModel {
        every { workoutRepository.getAllWorkouts() } returns flowOf(emptyList())
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(null)
        coEvery { bootcampRepository.getNextSession(any()) } returns null
        coEvery { bootcampRepository.getSessionsForEnrollmentOnce(any()) } returns emptyList()
        return HomeViewModel(workoutRepository, bootcampRepository)
    }

    @Test
    fun `uiState defaults to no active bootcamp`() = runTest {
        val vm = buildVm()
        val state = vm.uiState.value
        assertFalse(state.hasActiveBootcamp)
        assertNull(state.nextSession)
    }

    @Test
    fun `uiState reflects active bootcamp enrollment`() = runTest {
        val enrollment = BootcampEnrollmentEntity(
            id = 1L,
            goalType = "RACE_5K",
            targetMinutesPerRun = 30,
            runsPerWeek = 3,
            preferredDays = listOf(
                DayPreference(2, DaySelectionLevel.AVAILABLE),
                DayPreference(4, DaySelectionLevel.AVAILABLE),
                DayPreference(6, DaySelectionLevel.AVAILABLE)
            ),
            startDate = System.currentTimeMillis(),
            currentPhaseIndex = 0,
            currentWeekInPhase = 0,
            status = "ACTIVE",
            tierIndex = 1,
            tierPromptDismissCount = 0,
            tierPromptSnoozedUntilMs = 0L
        )
        every { workoutRepository.getAllWorkouts() } returns flowOf(emptyList())
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(enrollment)
        coEvery { bootcampRepository.getNextSession(1L) } returns null
        coEvery { bootcampRepository.getSessionsForEnrollmentOnce(1L) } returns emptyList()

        val vm = HomeViewModel(workoutRepository, bootcampRepository)
        val state = vm.uiState.value

        assertTrue(state.hasActiveBootcamp)
    }

    @Test
    fun `isSessionRunning reflects WorkoutState`() = runTest {
        WorkoutState.set(WorkoutSnapshot(isRunning = true))
        val vm = buildVm()
        val state = vm.uiState.value
        assertTrue(state.isSessionRunning)
    }

    @Test
    fun `workoutsThisWeek counts recent workouts`() = runTest {
        val now = System.currentTimeMillis()
        val workouts = listOf(
            WorkoutEntity(id = 1L, startTime = now - 3600_000, endTime = now, totalDistanceMeters = 5000f),
            WorkoutEntity(id = 2L, startTime = now - 7200_000, endTime = now - 3600_000, totalDistanceMeters = 3000f)
        )
        every { workoutRepository.getAllWorkouts() } returns flowOf(workouts)
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(null)

        val vm = HomeViewModel(workoutRepository, bootcampRepository)
        val state = vm.uiState.value

        assertEquals(2, state.workoutsThisWeek)
    }
}
```

- [ ] **Step 2: Run tests**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.ui.home.HomeViewModelTest"
```
Expected: PASS (all 4 tests)

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/hrcoach/ui/home/HomeViewModelTest.kt
git commit -m "test(home): add HomeViewModel unit tests for bootcamp state and session detection"
```

---

## Chunk 2: BootcampViewModel Key-Path Tests

### Task 3: BootcampViewModel tests — onboarding flow + enrollment loading

**Files:**
- Create: `app/src/test/java/com/hrcoach/ui/bootcamp/BootcampViewModelTest.kt`

**Context:** `BootcampViewModel` is the most complex ViewModel (20+ methods, 40+ UiState fields). We focus on the 3 highest-risk paths: enrollment loading, onboarding computation, and session completion.

Constructor: `BootcampViewModel(bootcampRepository, adaptiveProfileRepository, userProfileRepository, workoutMetricsRepository, bootcampSessionCompleter, achievementEvaluator)`

- [ ] **Step 1: Write BootcampViewModelTest**

Create `app/src/test/java/com/hrcoach/ui/bootcamp/BootcampViewModelTest.kt`:

```kotlin
package com.hrcoach.ui.bootcamp

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.domain.achievement.AchievementEvaluator
import com.hrcoach.domain.bootcamp.BootcampSessionCompleter
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.service.WorkoutSnapshot
import com.hrcoach.service.WorkoutState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BootcampViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val bootcampRepository: BootcampRepository = mockk(relaxed = true)
    private val adaptiveProfileRepository: AdaptiveProfileRepository = mockk(relaxed = true)
    private val userProfileRepository: UserProfileRepository = mockk(relaxed = true)
    private val workoutMetricsRepository: WorkoutMetricsRepository = mockk(relaxed = true)
    private val bootcampSessionCompleter: BootcampSessionCompleter = mockk(relaxed = true)
    private val achievementEvaluator: AchievementEvaluator = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        WorkoutState.set(WorkoutSnapshot())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        WorkoutState.set(WorkoutSnapshot())
    }

    private fun makeEnrollment(
        id: Long = 1L,
        goal: BootcampGoal = BootcampGoal.RACE_5K,
        runsPerWeek: Int = 3,
        minutes: Int = 30
    ) = BootcampEnrollmentEntity(
        id = id,
        goalType = goal.name,
        targetMinutesPerRun = minutes,
        runsPerWeek = runsPerWeek,
        preferredDays = listOf(
            DayPreference(2, DaySelectionLevel.AVAILABLE),
            DayPreference(4, DaySelectionLevel.AVAILABLE),
            DayPreference(6, DaySelectionLevel.AVAILABLE)
        ),
        startDate = System.currentTimeMillis(),
        currentPhaseIndex = 0,
        currentWeekInPhase = 0,
        status = BootcampEnrollmentEntity.STATUS_ACTIVE,
        tierIndex = 1,
        tierPromptDismissCount = 0,
        tierPromptSnoozedUntilMs = 0L
    )

    private fun buildVm(): BootcampViewModel {
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(null)
        coEvery { bootcampRepository.getActiveEnrollmentOnce() } returns null
        coEvery { workoutMetricsRepository.getRecentMetrics(any()) } returns emptyList()
        return BootcampViewModel(
            bootcampRepository,
            adaptiveProfileRepository,
            userProfileRepository,
            workoutMetricsRepository,
            bootcampSessionCompleter,
            achievementEvaluator
        )
    }

    @Test
    fun `no enrollment shows onboarding`() = runTest {
        val vm = buildVm()
        vm.loadBootcampState()

        val state = vm.uiState.value
        assertFalse(state.hasActiveEnrollment)
        assertTrue(state.showOnboarding)
    }

    @Test
    fun `active enrollment shows dashboard`() = runTest {
        val enrollment = makeEnrollment()
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(enrollment)
        coEvery { bootcampRepository.getActiveEnrollmentOnce() } returns enrollment
        coEvery { bootcampRepository.getSessionsForWeek(1L, any()) } returns emptyList()
        coEvery { bootcampRepository.getSessionsForEnrollmentOnce(1L) } returns emptyList()
        coEvery { bootcampRepository.getLastCompletedSession(1L) } returns null

        val vm = BootcampViewModel(
            bootcampRepository,
            adaptiveProfileRepository,
            userProfileRepository,
            workoutMetricsRepository,
            bootcampSessionCompleter,
            achievementEvaluator
        )
        vm.loadBootcampState()

        val state = vm.uiState.value
        assertTrue(state.hasActiveEnrollment)
        assertFalse(state.showOnboarding)
        assertEquals(BootcampGoal.RACE_5K, state.goal)
    }

    @Test
    fun `onboarding step advances correctly`() = runTest {
        val vm = buildVm()
        vm.startOnboarding()

        assertEquals(0, vm.uiState.value.onboardingStep)

        vm.setOnboardingGoal(BootcampGoal.RACE_5K)
        assertEquals(BootcampGoal.RACE_5K, vm.uiState.value.onboardingGoal)

        vm.setOnboardingStep(1)
        assertEquals(1, vm.uiState.value.onboardingStep)
    }

    @Test
    fun `onboarding minutes update long run preview`() = runTest {
        val vm = buildVm()
        vm.startOnboarding()
        vm.setOnboardingGoal(BootcampGoal.RACE_5K)
        vm.setOnboardingRunsPerWeek(3)
        vm.setOnboardingMinutes(30)

        val state = vm.uiState.value
        // DurationScaler at 3x30: long = 45 min (1.5x multiplier)
        assertEquals(45, state.onboardingLongRunMinutes)
        // Weekly total: 3 * 30 = 90
        assertEquals(90, state.onboardingWeeklyTotal)
    }
}
```

- [ ] **Step 2: Run tests**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.ui.bootcamp.BootcampViewModelTest"
```
Expected: PASS (all 4 tests)

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/hrcoach/ui/bootcamp/BootcampViewModelTest.kt
git commit -m "test(bootcamp): add BootcampViewModel tests for enrollment loading and onboarding flow"
```

---

## Chunk 3: PostRunSummaryViewModel Tests

### Task 4: PostRunSummaryViewModel tests — bootcamp completion + achievement flow

**Files:**
- Create: `app/src/test/java/com/hrcoach/ui/postrun/PostRunSummaryViewModelTest.kt`

**Context:** `PostRunSummaryViewModel` takes `SavedStateHandle`, `WorkoutRepository`, `WorkoutMetricsRepository`, `BootcampSessionCompleter`, `AchievementEvaluator`, `AchievementDao`, and `BootcampRepository`. Key test paths: loads workout data, calls bootcampSessionCompleter when isFreshWorkout, evaluates achievements, handles missing workout gracefully.

- [ ] **Step 1: Write PostRunSummaryViewModelTest**

Create `app/src/test/java/com/hrcoach/ui/postrun/PostRunSummaryViewModelTest.kt`:

```kotlin
package com.hrcoach.ui.postrun

import androidx.lifecycle.SavedStateHandle
import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.achievement.AchievementEvaluator
import com.hrcoach.domain.bootcamp.BootcampSessionCompleter
import com.hrcoach.service.WorkoutSnapshot
import com.hrcoach.service.WorkoutState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostRunSummaryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val workoutRepository: WorkoutRepository = mockk(relaxed = true)
    private val workoutMetricsRepository: WorkoutMetricsRepository = mockk(relaxed = true)
    private val bootcampSessionCompleter: BootcampSessionCompleter = mockk(relaxed = true)
    private val achievementEvaluator: AchievementEvaluator = mockk(relaxed = true)
    private val achievementDao: AchievementDao = mockk(relaxed = true)
    private val bootcampRepository: BootcampRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        WorkoutState.set(WorkoutSnapshot())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        WorkoutState.set(WorkoutSnapshot())
    }

    private fun savedState(workoutId: Long = 1L, fresh: Boolean = true): SavedStateHandle {
        return SavedStateHandle(mapOf("workoutId" to workoutId, "fresh" to fresh))
    }

    private fun makeWorkout(id: Long = 1L) = WorkoutEntity(
        id = id,
        startTime = System.currentTimeMillis() - 1800_000,
        endTime = System.currentTimeMillis(),
        totalDistanceMeters = 5000f
    )

    private fun buildVm(
        workoutId: Long = 1L,
        fresh: Boolean = true
    ): PostRunSummaryViewModel {
        coEvery { workoutRepository.getWorkoutById(workoutId) } returns makeWorkout(workoutId)
        coEvery { workoutRepository.getTrackPoints(workoutId) } returns emptyList()
        coEvery { workoutRepository.sumAllDistanceKm() } returns 10.0
        coEvery { workoutMetricsRepository.getMetricsForWorkout(workoutId) } returns null
        coEvery { workoutMetricsRepository.getRecentMetrics(any()) } returns emptyList()
        coEvery { bootcampRepository.getActiveEnrollmentOnce() } returns null
        coEvery { achievementDao.getUnshownAchievements() } returns emptyList()
        coEvery {
            bootcampSessionCompleter.complete(any(), any())
        } returns BootcampSessionCompleter.CompletionResult(completed = false)

        return PostRunSummaryViewModel(
            savedState(workoutId, fresh),
            workoutRepository,
            workoutMetricsRepository,
            bootcampSessionCompleter,
            achievementEvaluator,
            achievementDao,
            bootcampRepository
        )
    }

    @Test
    fun `loads workout data successfully`() = runTest {
        val vm = buildVm()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertTrue(state.distanceText.contains("5.00"))
    }

    @Test
    fun `missing workout shows error`() = runTest {
        coEvery { workoutRepository.getWorkoutById(1L) } returns null

        val vm = PostRunSummaryViewModel(
            savedState(1L, true),
            workoutRepository,
            workoutMetricsRepository,
            bootcampSessionCompleter,
            achievementEvaluator,
            achievementDao,
            bootcampRepository
        )
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `fresh workout calls bootcamp completer`() = runTest {
        WorkoutState.set(WorkoutSnapshot(pendingBootcampSessionId = 42L))
        coEvery {
            bootcampSessionCompleter.complete(1L, 42L)
        } returns BootcampSessionCompleter.CompletionResult(
            completed = true,
            weekComplete = false,
            progressLabel = "1 of 3 sessions this week"
        )

        val vm = buildVm(fresh = true)
        advanceUntilIdle()

        coVerify { bootcampSessionCompleter.complete(1L, 42L) }
        val state = vm.uiState.value
        assertTrue(state.isBootcampRun)
        assertEquals("1 of 3 sessions this week", state.bootcampProgressLabel)
    }

    @Test
    fun `non-fresh workout skips bootcamp completion`() = runTest {
        val vm = buildVm(fresh = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { bootcampSessionCompleter.complete(any(), any()) }
        assertFalse(vm.uiState.value.isBootcampRun)
    }

    @Test
    fun `isHrrActive set only for fresh workouts`() = runTest {
        val vmFresh = buildVm(fresh = true)
        advanceUntilIdle()
        assertTrue(vmFresh.uiState.value.isHrrActive)

        val vmStale = buildVm(workoutId = 2L, fresh = false)
        coEvery { workoutRepository.getWorkoutById(2L) } returns makeWorkout(2L)
        coEvery { workoutRepository.getTrackPoints(2L) } returns emptyList()
        coEvery { workoutMetricsRepository.getMetricsForWorkout(2L) } returns null
        advanceUntilIdle()
        assertFalse(vmStale.uiState.value.isHrrActive)
    }
}
```

- [ ] **Step 2: Run tests**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.ui.postrun.PostRunSummaryViewModelTest"
```
Expected: PASS (all 5 tests)

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/hrcoach/ui/postrun/PostRunSummaryViewModelTest.kt
git commit -m "test(postrun): add PostRunSummaryViewModel tests for completion flow and achievement gating"
```

---

## Chunk 4: MetricsCalculator Edge-Case Tests

### Task 5: MetricsCalculator edge cases — resting HR proxy + efficiency edge cases

**Files:**
- Create: `app/src/test/java/com/hrcoach/domain/engine/MetricsCalculatorEdgeCaseTest.kt`

**Context:** `MetricsCalculator` is an `object` with static methods. `computeRestingHrProxy` takes first 60s of track points and estimates resting HR. `calculateEfficiencyMetrics` computes aerobic decoupling. These are pure functions — no mocking needed.

The existing `MetricsCalculatorTest` covers `deriveFromPaceSamples`. This test file covers edge cases not yet tested.

- [ ] **Step 1: Write MetricsCalculatorEdgeCaseTest**

Create `app/src/test/java/com/hrcoach/domain/engine/MetricsCalculatorEdgeCaseTest.kt`:

```kotlin
package com.hrcoach.domain.engine

import com.hrcoach.data.db.TrackPointEntity
import org.junit.Assert.*
import org.junit.Test

class MetricsCalculatorEdgeCaseTest {

    // --- computeRestingHrProxy ---

    @Test
    fun `restingHrProxy returns null for empty track points`() {
        assertNull(MetricsCalculator.computeRestingHrProxy(emptyList()))
    }

    @Test
    fun `restingHrProxy returns null when first HR is above 100`() {
        val points = (0..5).map { i ->
            TrackPointEntity(
                workoutId = 1L,
                timestampMs = i * 10_000L,
                heartRate = 110,
                latitude = null,
                longitude = null,
                distanceMeters = 0f
            )
        }
        assertNull(MetricsCalculator.computeRestingHrProxy(points))
    }

    @Test
    fun `restingHrProxy returns null when HR range exceeds 15 bpm`() {
        val points = listOf(60, 62, 80, 62, 60).mapIndexed { i, hr ->
            TrackPointEntity(
                workoutId = 1L,
                timestampMs = i * 10_000L,
                heartRate = hr,
                latitude = null,
                longitude = null,
                distanceMeters = 0f
            )
        }
        assertNull(MetricsCalculator.computeRestingHrProxy(points))
    }

    @Test
    fun `restingHrProxy returns valid proxy for stable low HR`() {
        val points = (0..5).map { i ->
            TrackPointEntity(
                workoutId = 1L,
                timestampMs = i * 10_000L,
                heartRate = 65 + (i % 3), // 65, 66, 67, 65, 66, 67
                latitude = null,
                longitude = null,
                distanceMeters = 0f
            )
        }
        val proxy = MetricsCalculator.computeRestingHrProxy(points)
        assertNotNull(proxy)
        // min HR is 65, proxy = (65 - 10) = 55, clamped >= 30
        assertEquals(55f, proxy!!, 1f)
    }

    @Test
    fun `restingHrProxy clamps to 30 minimum`() {
        val points = (0..5).map { i ->
            TrackPointEntity(
                workoutId = 1L,
                timestampMs = i * 10_000L,
                heartRate = 35,
                latitude = null,
                longitude = null,
                distanceMeters = 0f
            )
        }
        val proxy = MetricsCalculator.computeRestingHrProxy(points)
        // min HR 35, proxy = (35 - 10) = 25, clamped to 30
        assertEquals(30f, proxy!!, 0.1f)
    }

    // --- deriveFullMetrics edge cases ---

    @Test
    fun `deriveFullMetrics returns null for single track point`() {
        val points = listOf(
            TrackPointEntity(
                workoutId = 1L,
                timestampMs = 1000L,
                heartRate = 140,
                latitude = 51.0,
                longitude = 0.0,
                distanceMeters = 0f
            )
        )
        assertNull(MetricsCalculator.deriveFullMetrics(1L, System.currentTimeMillis(), points))
    }

    @Test
    fun `deriveFullMetrics returns null for empty track points`() {
        assertNull(MetricsCalculator.deriveFullMetrics(1L, System.currentTimeMillis(), emptyList()))
    }
}
```

- [ ] **Step 2: Run tests**

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.hrcoach.domain.engine.MetricsCalculatorEdgeCaseTest"
```
Expected: PASS (all 6 tests)

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/hrcoach/domain/engine/MetricsCalculatorEdgeCaseTest.kt
git commit -m "test(engine): add MetricsCalculator edge-case tests for resting HR proxy and empty inputs"
```

---

## Chunk 5: Typography Token Pass

### Task 6: Replace ad-hoc Color.White.copy(alpha) with tokenized constants

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt` (16 occurrences)
- Modify: `app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt` (3 occurrences)
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt` (4 occurrences)

**Context:** The codebase has tokenized text colors (`CardeaTextPrimary`, `CardeaTextSecondary`, `CardeaTextTertiary`) and glass tokens (`GlassBorder`, `GlassHighlight`) in `Color.kt`. But 23 instances of `Color.White.copy(alpha=...)` still exist across 3 UI files, creating brittle theming.

**Replacement mapping:**

| Ad-hoc Pattern | Token Replacement | Rationale |
|---------------|-------------------|-----------|
| `Color.White.copy(alpha = 0.03f..0.05f)` | `GlassHighlight` (0x0AFFFFFF) | Subtle glass surfaces |
| `Color.White.copy(alpha = 0.06f..0.12f)` | `GlassBorder` (0x1AFFFFFF) | Glass borders, dividers |
| `Color.White.copy(alpha = 0.15f..0.25f)` | New: `GlassSurface` | Interactive surface overlay |
| `Color.White.copy(alpha = 0.5f..0.7f)` | `CardeaTextSecondary` | Secondary text/icons |
| `Color.White.copy(alpha = 0.85f..0.95f)` | `CardeaTextPrimary` | Primary text |
| `Color.White` (full) | `CardeaTextPrimary` | Primary text |

- [ ] **Step 1: Add missing token to Color.kt**

In `app/src/main/java/com/hrcoach/ui/theme/Color.kt`, after `GlassHighlight`:

```kotlin
val GlassSurface = Color(0x33FFFFFF)   // 0.20 alpha — interactive glass overlay, hover/press states
```

- [ ] **Step 2: Replace in HomeScreen.kt**

Open `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt` and perform the following replacements. For each `Color.White.copy(alpha = ...)`, replace with the appropriate token based on the alpha value:

- `Color.White.copy(alpha = 0.07f)` → `GlassBorder`
- `Color.White.copy(alpha = 0.04f)` → `GlassHighlight`
- `Color.White.copy(alpha = 0.5f)` → `CardeaTextSecondary`
- `Color.White.copy(alpha = 0.9f)` → `CardeaTextPrimary`
- `Color.White.copy(alpha = 0.7f)` → `CardeaTextSecondary`
- `Color.White.copy(alpha = 0.1f)` → `GlassBorder`
- `Color.White.copy(alpha = 0.2f)` → `GlassSurface`

Add import if not present:
```kotlin
import com.hrcoach.ui.theme.GlassSurface
```

- [ ] **Step 3: Replace in BootcampScreen.kt**

Same pattern for the 3 occurrences. Match alpha values to the token mapping above.

- [ ] **Step 4: Replace in SetupScreen.kt**

Same pattern for the 4 occurrences.

- [ ] **Step 5: Compile check**

```bash
./gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run tests**

```bash
./gradlew.bat :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL (no logic changes)

- [ ] **Step 7: Commit**

```bash
git add \
  app/src/main/java/com/hrcoach/ui/theme/Color.kt \
  app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt \
  app/src/main/java/com/hrcoach/ui/bootcamp/BootcampScreen.kt \
  app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt
git commit -m "refactor(ui): replace ad-hoc Color.White.copy(alpha) with tokenized constants"
```

---

## Chunk 6: Live Sensor Status on Home Screen

### Task 7: Persist last BLE device info and display on Home

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/BleHrManager.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

**Context:** `BleHrManager` tracks `lastDeviceAddress` and `isConnected` in memory, but this data is lost when the service stops. For the Home screen to show "Last used: COOSPO H808S" or a connection badge, we persist the last device name and address to SharedPreferences when a connection succeeds, and read it from HomeViewModel.

- [ ] **Step 1: Add device persistence to BleHrManager**

In `BleHrManager.kt`, add a `SharedPreferences` write when a device connects successfully. After the `gatt.discoverServices()` call in `onConnectionStateChange` (inside the `STATE_CONNECTED` branch):

```kotlin
// Persist device info for Home screen display
context.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE).edit()
    .putString("last_device_address", device.address)
    .putString("last_device_name", device.name ?: "HR Monitor")
    .putLong("last_connected_ms", System.currentTimeMillis())
    .apply()
```

- [ ] **Step 2: Add sensor fields to HomeUiState**

In `HomeViewModel.kt`, add to `HomeUiState`:

```kotlin
data class HomeUiState(
    // ... existing fields ...
    val sensorName: String? = null,
    val sensorLastSeenMs: Long? = null,
)
```

- [ ] **Step 3: Read sensor prefs in HomeViewModel**

In `HomeViewModel`'s `init` or factory flow, read from SharedPreferences. Since `HomeViewModel` gets injected via Hilt, add `@ApplicationContext context: Context` to the constructor:

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val bootcampRepository: BootcampRepository,
    @ApplicationContext private val context: Context
) : ViewModel()
```

Then in the `combine().flatMapLatest { }` block, read the prefs:

```kotlin
val blePrefs = context.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
val sensorName = blePrefs.getString("last_device_name", null)
val sensorLastSeen = blePrefs.getLong("last_connected_ms", 0L).takeIf { it > 0L }
```

And include them in the emitted `HomeUiState`:

```kotlin
HomeUiState(
    // ... existing fields ...
    sensorName = sensorName,
    sensorLastSeenMs = sensorLastSeen,
)
```

- [ ] **Step 4: Show sensor status in HomeScreen**

In `HomeScreen.kt`, replace the static grey Bluetooth icon with a sensor status indicator. Near the existing `Icon(Icons.Default.Bluetooth, ...)` (around line 127):

```kotlin
Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(GlassHighlight)
        .clickable { onGoToSetup() }
        .padding(horizontal = 8.dp, vertical = 4.dp)
) {
    val sensorColor = when {
        state.isSessionRunning -> Color(0xFF22C55E) // green — active now
        state.sensorName != null -> CardeaTextSecondary // grey — known device
        else -> CardeaTextTertiary // dim — never paired
    }
    Icon(
        imageVector = Icons.Default.Bluetooth,
        contentDescription = "Sensor setup",
        tint = sensorColor,
        modifier = Modifier.size(16.dp)
    )
    if (state.sensorName != null) {
        Spacer(Modifier.width(4.dp))
        Text(
            text = state.sensorName,
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTextSecondary,
            maxLines = 1
        )
    }
}
```

- [ ] **Step 5: Add `onGoToSetup` parameter to HomeScreen**

In `HomeScreen.kt` composable signature, add `onGoToSetup: () -> Unit`. Wire it in `NavGraph.kt`:

```kotlin
onGoToSetup = { navController.navigate(Routes.SETUP) },
```

- [ ] **Step 6: Compile check**

```bash
./gradlew.bat :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Run tests**

```bash
./gradlew.bat :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add \
  app/src/main/java/com/hrcoach/service/BleHrManager.kt \
  app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt \
  app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt \
  app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(home): show last-used BLE sensor name and status on Home screen"
```

---

## Final Verification

- [ ] **Full test suite**

```bash
./gradlew.bat :app:testDebugUnitTest
```
Expected: All tests pass, no regressions

- [ ] **Full debug build**

```bash
./gradlew.bat :app:assembleDebug
```
Expected: BUILD SUCCESSFUL
