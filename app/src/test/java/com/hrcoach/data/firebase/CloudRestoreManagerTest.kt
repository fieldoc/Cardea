package com.hrcoach.data.firebase

import androidx.room.RoomDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.hrcoach.data.db.AchievementDao
import com.hrcoach.data.db.AppDatabase
import com.hrcoach.data.db.BootcampDao
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.TrackPointDao
import com.hrcoach.data.db.WorkoutDao
import com.hrcoach.data.db.WorkoutMetricsDao
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.data.repository.AutoPauseSettingsRepository
import com.hrcoach.data.repository.OnboardingRepository
import com.hrcoach.data.repository.ThemePreferencesRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.domain.model.AdaptiveProfile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Unit tests for CloudRestoreManager business logic.
 *
 * Firebase RTDB I/O (hasCloudBackup) is mocked via a spy; Room SQL correctness
 * (C1: getWorkoutCount filtering simulated rows) requires an instrumented Room test
 * and is verified by code inspection of WorkoutDao.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CloudRestoreManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authManager: FirebaseAuthManager
    private lateinit var onboardingRepo: OnboardingRepository
    private lateinit var workoutDao: WorkoutDao
    private lateinit var userProfileRepo: UserProfileRepository
    private lateinit var adaptiveProfileRepo: AdaptiveProfileRepository
    private lateinit var audioSettingsRepo: AudioSettingsRepository
    private lateinit var themePrefsRepo: ThemePreferencesRepository
    private lateinit var autoPauseRepo: AutoPauseSettingsRepository
    private lateinit var roomDb: AppDatabase
    private lateinit var trackPointDao: TrackPointDao
    private lateinit var workoutMetricsDao: WorkoutMetricsDao
    private lateinit var bootcampDao: BootcampDao
    private lateinit var achievementDao: AchievementDao
    private lateinit var firebaseDb: FirebaseDatabase

    private lateinit var manager: CloudRestoreManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authManager       = mockk(relaxed = true)
        onboardingRepo    = mockk(relaxed = true)
        workoutDao        = mockk(relaxed = true)
        userProfileRepo   = mockk(relaxed = true)
        adaptiveProfileRepo = mockk(relaxed = true) {
            coEvery { getProfile() } returns AdaptiveProfile()
        }
        audioSettingsRepo = mockk(relaxed = true)
        themePrefsRepo    = mockk(relaxed = true)
        autoPauseRepo     = mockk(relaxed = true)
        roomDb            = mockk(relaxed = true)
        trackPointDao     = mockk(relaxed = true)
        workoutMetricsDao = mockk(relaxed = true)
        bootcampDao       = mockk(relaxed = true)
        achievementDao    = mockk(relaxed = true)
        firebaseDb        = mockk(relaxed = true)

        manager = CloudRestoreManager(
            db = firebaseDb,
            authManager = authManager,
            userProfileRepo = userProfileRepo,
            audioSettingsRepo = audioSettingsRepo,
            themePrefsRepo = themePrefsRepo,
            autoPauseRepo = autoPauseRepo,
            adaptiveProfileRepo = adaptiveProfileRepo,
            onboardingRepo = onboardingRepo,
            roomDb = roomDb,
            workoutDao = workoutDao,
            trackPointDao = trackPointDao,
            workoutMetricsDao = workoutMetricsDao,
            bootcampDao = bootcampDao,
            achievementDao = achievementDao,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── needsRestore() gate logic ────────────────────────────────────────

    @Test
    fun `needsRestore returns false when Google is not linked`() = runTest {
        every { authManager.isGoogleLinked() } returns false

        assertFalse(manager.needsRestore())
    }

    @Test
    fun `needsRestore returns false when onboarding is already complete`() = runTest {
        every { authManager.isGoogleLinked() } returns true
        every { onboardingRepo.isOnboardingCompleted() } returns true

        assertFalse(manager.needsRestore())
    }

    @Test
    fun `needsRestore returns false when real workouts exist locally`() = runTest {
        every { authManager.isGoogleLinked() } returns true
        every { onboardingRepo.isOnboardingCompleted() } returns false
        // After C1 fix: getWorkoutCount() counts only non-simulated rows.
        // Here we simulate a device that already has real workout data.
        coEvery { workoutDao.getWorkoutCount() } returns 3

        assertFalse(manager.needsRestore())
    }

    @Test
    fun `needsRestore proceeds to backup check when no real workouts exist`() = runTest {
        every { authManager.isGoogleLinked() } returns true
        every { onboardingRepo.isOnboardingCompleted() } returns false
        // After C1 fix: 0 means no real workouts (simulated don't count).
        coEvery { workoutDao.getWorkoutCount() } returns 0
        // No cloud backup → needsRestore still false
        every { authManager.getCurrentUid() } returns null

        assertFalse(manager.needsRestore())
    }
}
