package com.hrcoach.data.repository

import android.content.Context
import com.google.gson.reflect.TypeToken
import com.hrcoach.domain.model.AdaptiveProfile
import com.hrcoach.util.JsonCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdaptiveProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val PREFS_PREFIX = "hr_coach_adaptive_profile_"
        private const val LEGACY_PREFS_NAME = "hr_coach_adaptive_profile"
        private const val PREF_PROFILE_JSON = "adaptive_profile_json"
    }

    private val profileType = object : TypeToken<AdaptiveProfile>() {}.type

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

    @Synchronized
    fun getProfile(): AdaptiveProfile {
        val raw = prefs().getString(PREF_PROFILE_JSON, null) ?: return AdaptiveProfile()
        return runCatching {
            JsonCodec.gson.fromJson<AdaptiveProfile>(raw, profileType)
        }.getOrElse { AdaptiveProfile() }
    }

    @Synchronized
    fun saveProfile(profile: AdaptiveProfile) {
        prefs().edit().putString(PREF_PROFILE_JSON, JsonCodec.gson.toJson(profile)).apply()
    }
}
