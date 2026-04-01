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
        private const val PREF_DISPLAY_NAME = "display_name"
        private const val PREF_AVATAR_SYMBOL = "avatar_symbol"
        private const val PREF_USER_ID = "user_id"
        private const val UNSET = -1
        private const val DEFAULT_NAME = "Runner"
        private const val DEFAULT_EMBLEM = "pulse"
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

    @Synchronized
    fun getDisplayName(): String {
        return prefs.getString(PREF_DISPLAY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME
    }

    @Synchronized
    fun setDisplayName(name: String) {
        prefs.edit().putString(PREF_DISPLAY_NAME, sanitizeDisplayName(name)).apply()
    }

    @Synchronized
    fun getEmblemId(): String {
        return prefs.getString(PREF_AVATAR_SYMBOL, DEFAULT_EMBLEM) ?: DEFAULT_EMBLEM
    }

    @Synchronized
    fun setEmblemId(id: String) {
        prefs.edit().putString(PREF_AVATAR_SYMBOL, id).apply()
    }

    @Synchronized
    fun getUserId(): String {
        val existing = prefs.getString(PREF_USER_ID, null)
        if (existing != null) return existing
        val newId = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(PREF_USER_ID, newId).apply()
        return newId
    }
}

internal fun sanitizeDisplayName(name: String): String {
    val trimmed = name.trim().take(20)
    return trimmed.ifBlank { "Runner" }
}
