package com.hrcoach.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoPauseSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val PREFS_PREFIX = "hr_coach_auto_pause_settings_"
        private const val LEGACY_PREFS_NAME = "hr_coach_auto_pause_settings"
        private const val PREF_AUTO_PAUSE_ENABLED = "auto_pause_enabled"
    }

    private fun prefs(): android.content.SharedPreferences {
        val uid = authRepository.effectiveUserId.ifEmpty { "anonymous" }
        val userPrefs = context.getSharedPreferences("$PREFS_PREFIX$uid", Context.MODE_PRIVATE)
        if (userPrefs.all.isEmpty()) {
            val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            if (legacy.all.isNotEmpty()) {
                userPrefs.edit().apply {
                    legacy.all.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Boolean -> putBoolean(key, value)
                            is Float -> putFloat(key, value)
                        }
                    }
                    apply()
                }
            }
        }
        return userPrefs
    }

    fun isAutoPauseEnabled(): Boolean =
        prefs().getBoolean(PREF_AUTO_PAUSE_ENABLED, true)

    fun setAutoPauseEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(PREF_AUTO_PAUSE_ENABLED, enabled).apply()
    }
}
