package com.hrcoach.data.repository

import android.content.Context
import com.hrcoach.data.db.WorkoutDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val workoutDao: WorkoutDao,
) {
    companion object {
        private const val PREFS_NAME = "onboarding_prefs"
        private const val KEY_COMPLETED = "onboarding_completed"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_COMPLETED, false)
    }

    @Synchronized
    fun setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    /**
     * For existing users upgrading to a version with onboarding:
     * if they already have workout data, auto-complete onboarding.
     */
    suspend fun autoCompleteForExistingUser(): Boolean {
        if (isOnboardingCompleted()) return true
        val count = workoutDao.getWorkoutCount()
        if (count > 0) {
            setOnboardingCompleted()
            return true
        }
        return false
    }
}
