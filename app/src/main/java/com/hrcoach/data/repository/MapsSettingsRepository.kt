package com.hrcoach.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapsSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "hr_coach_maps_settings"
        private const val PREF_MAPS_API_KEY = "maps_api_key"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getMapsApiKey(): String {
        return prefs.getString(PREF_MAPS_API_KEY, "")?.trim().orEmpty()
    }

    @Synchronized
    fun setMapsApiKey(key: String) {
        prefs.edit().putString(PREF_MAPS_API_KEY, key.trim()).apply()
    }
}
