package com.hrcoach.ui.bootcamp

import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.domain.achievement.AchievementEvaluator
import com.hrcoach.domain.bootcamp.BootcampSessionCompleter
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.domain.model.AdaptiveProfile
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
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val notificationManager: com.hrcoach.service.BootcampNotificationManager = mockk(relaxed = true)

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

    private fun createViewModel(): BootcampViewModel {
        return BootcampViewModel(
            bootcampRepository = bootcampRepository,
            adaptiveProfileRepository = adaptiveProfileRepository,
            userProfileRepository = userProfileRepository,
            workoutMetricsRepository = workoutMetricsRepository,
            bootcampSessionCompleter = bootcampSessionCompleter,
            achievementEvaluator = achievementEvaluator,
            notificationManager = notificationManager
        )
    }

    // ── Test 1: no enrollment shows no active enrollment ────────────────

    @Test
    fun `no enrollment sets hasActiveEnrollment false`() {
        coEvery { bootcampRepository.getActiveEnrollmentOnce() } returns null
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(null)

        val vm = createViewModel()
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.hasActiveEnrollment)
    }

    // ── Test 2: active enrollment shows dashboard ───────────────────────

    @Test
    fun `active enrollment sets hasActiveEnrollment true with correct goal`() {
        val enrollment = BootcampEnrollmentEntity(
            id = 1L,
            goalType = "RACE_5K",
            targetMinutesPerRun = 30,
            runsPerWeek = 3,
            preferredDays = listOf(
                DayPreference(2, DaySelectionLevel.AVAILABLE),
                DayPreference(4, DaySelectionLevel.AVAILABLE),
                DayPreference(6, DaySelectionLevel.LONG_RUN_BIAS)
            ),
            startDate = System.currentTimeMillis() - 86_400_000L,
            currentPhaseIndex = 0,
            currentWeekInPhase = 0,
            status = BootcampEnrollmentEntity.STATUS_ACTIVE,
            tierIndex = 1,
            tierPromptDismissCount = 0,
            tierPromptSnoozedUntilMs = 0L
        )

        coEvery { bootcampRepository.getActiveEnrollmentOnce() } returns enrollment
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(enrollment)
        coEvery { bootcampRepository.getSessionsForWeek(any(), any()) } returns emptyList()
        coEvery { bootcampRepository.getSessionsForEnrollmentOnce(any()) } returns emptyList()
        coEvery { bootcampRepository.getLastCompletedSession(any()) } returns null
        coEvery { bootcampRepository.insertSessions(any()) } returns Unit
        every { adaptiveProfileRepository.getProfile() } returns AdaptiveProfile()
        coEvery { workoutMetricsRepository.getRecentMetrics(any()) } returns emptyList()

        val vm = createViewModel()
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.hasActiveEnrollment)
        assertFalse(state.showOnboarding)
        assertEquals(BootcampGoal.RACE_5K, state.goal)
    }

    // ── Test 3: onboarding step advances correctly ──────────────────────

    @Test
    fun `onboarding step advances correctly`() {
        coEvery { bootcampRepository.getActiveEnrollmentOnce() } returns null
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(null)

        val vm = createViewModel()

        vm.startOnboarding()
        assertTrue(vm.uiState.value.showOnboarding)
        assertEquals(0, vm.uiState.value.onboardingStep)

        vm.setOnboardingGoal(BootcampGoal.RACE_5K)
        assertEquals(BootcampGoal.RACE_5K, vm.uiState.value.onboardingGoal)

        vm.setOnboardingStep(1)
        assertEquals(1, vm.uiState.value.onboardingStep)
    }

    // ── Test 4: onboarding minutes update long run preview ──────────────

    @Test
    fun `onboarding minutes update long run preview via DurationScaler`() {
        coEvery { bootcampRepository.getActiveEnrollmentOnce() } returns null
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(null)

        val vm = createViewModel()

        vm.startOnboarding()
        vm.setOnboardingGoal(BootcampGoal.RACE_5K)
        vm.setOnboardingRunsPerWeek(3)
        vm.setOnboardingMinutes(30)

        val state = vm.uiState.value
        // DurationScaler.compute(3, 30):
        //   longMinutes = (30 * 1.5).toInt() = 45
        //   adjustedEasy = (90 - 45) / 2 = 22
        // computeOnboardingDurationState: easyRuns = 3-1 = 2, weeklyTotal = 22*2 + 45 = 89
        assertEquals(45, state.onboardingLongRunMinutes)
        assertEquals(89, state.onboardingWeeklyTotal)
    }
}
