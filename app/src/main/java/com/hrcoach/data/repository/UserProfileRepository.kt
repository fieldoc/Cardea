package com.hrcoach.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val PREFS_PREFIX = "hr_coach_user_profile_"
        private const val LEGACY_PREFS_NAME = "hr_coach_user_profile"
        private const val PREF_MAX_HR = "max_hr"
        private const val PREF_DISPLAY_NAME = "display_name"
        private const val PREF_AVATAR_EMBLEM_ID = "avatar_emblem_id"
        private const val PREF_BIO = "bio"
        private const val PREF_IS_CLAIMED = "is_claimed"
        private const val PREF_CLAIMED_AT_MS = "claimed_at_ms"
        private const val PREF_LAST_NAME_CHANGE_MS = "last_name_change_ms"
        private const val UNSET = -1
        private const val DEFAULT_NAME = "Runner"
        private const val DEFAULT_EMBLEM = "pulse"
        private const val NAME_CHANGE_COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000
    }

    private val UNICODE_TO_EMBLEM = mapOf(
        "\u2665" to "heart", "\u2605" to "nova", "\u26A1" to "bolt",
        "\u25C6" to "diamond", "\u25B2" to "ascent", "\u25CF" to "ripple",
        "\u2726" to "compass", "\u2666" to "prism", "\u2191" to "flame",
        "\u221E" to "infinity"
    )

    private fun prefs(): android.content.SharedPreferences {
        val uid = authRepository.effectiveUserId.ifEmpty { "anonymous" }
        val userPrefs = context.getSharedPreferences("$PREFS_PREFIX$uid", Context.MODE_PRIVATE)
        // One-time migration: copy legacy prefs to per-UID file
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

    @Synchronized
    fun getMaxHr(): Int? {
        val stored = prefs().getInt(PREF_MAX_HR, UNSET)
        return if (stored == UNSET) null else stored
    }

    @Synchronized
    fun setMaxHr(maxHr: Int) {
        require(maxHr in 100..220) { "maxHr must be in 100..220" }
        prefs().edit().putInt(PREF_MAX_HR, maxHr).apply()
    }

    @Synchronized
    fun getDisplayName(): String {
        return prefs().getString(PREF_DISPLAY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME
    }

    @Synchronized
    fun setDisplayName(name: String) {
        val p = prefs()
        p.edit().putString(PREF_DISPLAY_NAME, sanitizeDisplayName(name)).apply()
        p.edit().putLong(PREF_LAST_NAME_CHANGE_MS, System.currentTimeMillis()).apply()
    }

    @Synchronized
    fun getAvatarEmblemId(): String {
        val p = prefs()
        val stored = p.getString(PREF_AVATAR_EMBLEM_ID, null)
        if (stored != null) {
            // Unicode-to-emblem migration
            val mapped = UNICODE_TO_EMBLEM[stored]
            if (mapped != null) {
                p.edit().putString(PREF_AVATAR_EMBLEM_ID, mapped).apply()
                return mapped
            }
            return stored
        }
        // Check legacy key for migration
        val legacySymbol = p.getString("avatar_symbol", null)
        if (legacySymbol != null) {
            val mapped = UNICODE_TO_EMBLEM[legacySymbol] ?: DEFAULT_EMBLEM
            p.edit().putString(PREF_AVATAR_EMBLEM_ID, mapped).apply()
            return mapped
        }
        return DEFAULT_EMBLEM
    }

    @Synchronized
    fun setAvatarEmblemId(emblemId: String) {
        prefs().edit().putString(PREF_AVATAR_EMBLEM_ID, emblemId).apply()
    }

    @Synchronized
    fun getBio(): String {
        return prefs().getString(PREF_BIO, "") ?: ""
    }

    @Synchronized
    fun setBio(bio: String) {
        prefs().edit().putString(PREF_BIO, bio).apply()
    }

    @Synchronized
    fun isProfileClaimed(): Boolean {
        return prefs().getBoolean(PREF_IS_CLAIMED, false)
    }

    @Synchronized
    fun claimProfile() {
        val p = prefs()
        p.edit().apply {
            putBoolean(PREF_IS_CLAIMED, true)
            putLong(PREF_CLAIMED_AT_MS, System.currentTimeMillis())
            apply()
        }
    }

    @Synchronized
    fun getProfileClaimedAtMs(): Long {
        return prefs().getLong(PREF_CLAIMED_AT_MS, 0L)
    }

    @Synchronized
    fun canChangeName(): Boolean {
        val lastChange = prefs().getLong(PREF_LAST_NAME_CHANGE_MS, 0L)
        if (lastChange == 0L) return true
        return System.currentTimeMillis() - lastChange >= NAME_CHANGE_COOLDOWN_MS
    }

    @Synchronized
    fun daysUntilNameChange(): Int {
        val lastChange = prefs().getLong(PREF_LAST_NAME_CHANGE_MS, 0L)
        if (lastChange == 0L) return 0
        val elapsed = System.currentTimeMillis() - lastChange
        val remaining = NAME_CHANGE_COOLDOWN_MS - elapsed
        if (remaining <= 0L) return 0
        return ((remaining + 86_399_999L) / 86_400_000L).toInt()
    }
}

internal fun sanitizeDisplayName(name: String): String {
    val trimmed = name.trim().take(20)
    return trimmed.ifBlank { "Runner" }
}
