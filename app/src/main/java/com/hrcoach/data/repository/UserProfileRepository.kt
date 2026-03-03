package com.hrcoach.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "hr_coach_user_profile"
        private const val PREF_MAX_HR = "max_hr"
        private const val UNSET = -1
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getMaxHr(): Int? {
        val stored = prefs.getInt(PREF_MAX_HR, UNSET)
        return if (stored == UNSET) null else stored
    }

    @Synchronized
    fun setMaxHr(maxHr: Int) {
        require(maxHr in 100..220) { "maxHr must be in 100..220" }
        prefs.edit().putInt(PREF_MAX_HR, maxHr).apply()
    }
}
