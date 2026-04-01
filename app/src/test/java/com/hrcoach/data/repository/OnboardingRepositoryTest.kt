package com.hrcoach.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.hrcoach.data.db.WorkoutDao
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OnboardingRepositoryTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var workoutDao: WorkoutDao
    private lateinit var repo: OnboardingRepository

    @Before
    fun setUp() {
        editor = mockk(relaxed = true)
        prefs = mockk(relaxed = true) {
            every { edit() } returns editor
            every { editor.putBoolean(any(), any()) } returns editor
        }
        workoutDao = mockk(relaxed = true)
        val context = mockk<Context> {
            every { getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE) } returns prefs
        }
        repo = OnboardingRepository(context, workoutDao)
    }

    @Test
    fun `isOnboardingCompleted returns false by default`() {
        every { prefs.getBoolean("onboarding_completed", false) } returns false
        assertFalse(repo.isOnboardingCompleted())
    }

    @Test
    fun `setOnboardingCompleted sets flag to true`() {
        repo.setOnboardingCompleted()
        verify { editor.putBoolean("onboarding_completed", true) }
    }

    @Test
    fun `autoCompleteForExistingUser returns true if already completed`() = runTest {
        every { prefs.getBoolean("onboarding_completed", false) } returns true
        assertTrue(repo.autoCompleteForExistingUser())
    }

    @Test
    fun `autoCompleteForExistingUser auto-completes when workouts exist`() = runTest {
        every { prefs.getBoolean("onboarding_completed", false) } returns false
        coEvery { workoutDao.getWorkoutCount() } returns 3
        assertTrue(repo.autoCompleteForExistingUser())
        verify { editor.putBoolean("onboarding_completed", true) }
    }

    @Test
    fun `autoCompleteForExistingUser returns false for new user with no workouts`() = runTest {
        every { prefs.getBoolean("onboarding_completed", false) } returns false
        coEvery { workoutDao.getWorkoutCount() } returns 0
        assertFalse(repo.autoCompleteForExistingUser())
    }
}
