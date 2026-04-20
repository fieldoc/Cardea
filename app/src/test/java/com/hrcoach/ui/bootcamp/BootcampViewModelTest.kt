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
import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.domain.model.BootcampGoal
import android.bluetooth.BluetoothDevice
import com.hrcoach.service.BleConnectionCoordinator
import com.hrcoach.service.WorkoutSnapshot
import com.hrcoach.service.WorkoutState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.util.concurrent.TimeUnit

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
    private val bleCoordinator: BleConnectionCoordinator = mockk(relaxed = true)
    private val cloudBackupManager: com.hrcoach.data.firebase.CloudBackupManager = mockk(relaxed = true)
    private val audioSettingsRepository: com.hrcoach.data.repository.AudioSettingsRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        every { bleCoordinator.heartRate } returns MutableStateFlow(0)
        every { bleCoordinator.isConnected } returns MutableStateFlow(false)
        every { bleCoordinator.discoveredDevices } returns MutableStateFlow(emptyList<BluetoothDevice>())
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
            notificationManager = notificationManager,
            bleCoordinator = bleCoordinator,
            cloudBackupManager = cloudBackupManager,
            audioSettingsRepository = audioSettingsRepository
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
        assertTrue(vm.uiState.value.showCarousel)

        vm.dismissCarousel()
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

    // ── Test 5: CTL-decay causes tier demotion after 21-day gap ─────────

    @Test
    fun `T1 runner with decayed CTL is demoted to T0 after 21-day gap`() {
        // Arrange: T1 RACE_5K runner with CTL=38 (just inside T1's 35..65 range).
        // Last completed session was 21 days ago — crosses the MEANINGFUL_BREAK (>14 days) threshold.
        // After 21 days of inactivity: decay = exp(-21/42) ≈ 0.607,
        // projectedCtl = 38 * 0.607 ≈ 23 — below T1 floor of 35 → demoted to T0.
        val twentyOneDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(21)

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
            startDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60),
            currentPhaseIndex = 1,
            currentWeekInPhase = 2,
            status = BootcampEnrollmentEntity.STATUS_ACTIVE,
            tierIndex = 1,  // T1 — should be demoted
            tierPromptDismissCount = 0,
            tierPromptSnoozedUntilMs = 0L
        )

        val lastCompletedSession = BootcampSessionEntity(
            id = 10L,
            enrollmentId = 1L,
            weekNumber = 5,
            dayOfWeek = 3,
            sessionType = "EASY",
            targetMinutes = 30,
            status = BootcampSessionEntity.STATUS_COMPLETED,
            completedWorkoutId = 99L,
            completedAtMs = twentyOneDaysAgo
        )

        // Profile with CTL=38 (just inside T1 range of 35..65 for RACE_5K)
        val profileWithCtl = AdaptiveProfile(ctl = 38f, atl = 20f)

        val capturedEnrollment = slot<BootcampEnrollmentEntity>()

        coEvery { bootcampRepository.getActiveEnrollmentOnce() } returns enrollment
        every { bootcampRepository.getActiveEnrollment() } returns flowOf(enrollment)
        coEvery { bootcampRepository.getLastCompletedSession(any()) } returns lastCompletedSession
        coEvery { bootcampRepository.getSessionsForWeek(any(), any()) } returns emptyList()
        coEvery { bootcampRepository.getSessionsForEnrollmentOnce(any()) } returns emptyList()
        coEvery { bootcampRepository.insertSessions(any()) } returns Unit
        coEvery { bootcampRepository.deleteSessionsAfterWeek(any(), any()) } returns Unit
        coEvery { bootcampRepository.updateEnrollment(capture(capturedEnrollment)) } returns Unit
        every { adaptiveProfileRepository.getProfile() } returns profileWithCtl
        coEvery { workoutMetricsRepository.getRecentMetrics(any()) } returns emptyList()

        // Act
        createViewModel()

        // Assert: updateEnrollment was called with tierIndex = 0 (T0 demotion)
        coVerify { bootcampRepository.updateEnrollment(any()) }
        assertEquals(
            "Enrollment should be updated with demoted tierIndex=0 after CTL decay",
            0,
            capturedEnrollment.captured.tierIndex
        )
    }
}
