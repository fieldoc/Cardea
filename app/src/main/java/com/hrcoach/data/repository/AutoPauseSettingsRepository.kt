package com.hrcoach.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoPauseSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "hr_coach_auto_pause_settings"
        private const val PREF_AUTO_PAUSE_ENABLED = "auto_pause_enabled"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoPauseEnabled(): Boolean =
        prefs.getBoolean(PREF_AUTO_PAUSE_ENABLED, true)

    fun setAutoPauseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_PAUSE_ENABLED, enabled).apply()
    }
}
