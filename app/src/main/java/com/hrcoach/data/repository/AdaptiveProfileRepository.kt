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
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "hr_coach_adaptive_profile"
        private const val PREF_PROFILE_JSON = "adaptive_profile_json"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val profileType = object : TypeToken<AdaptiveProfile>() {}.type

    @Synchronized
    fun getProfile(): AdaptiveProfile {
        val raw = prefs.getString(PREF_PROFILE_JSON, null) ?: return AdaptiveProfile()
        return runCatching {
            JsonCodec.gson.fromJson<AdaptiveProfile>(raw, profileType)
        }.getOrElse { AdaptiveProfile() }
    }

    @Synchronized
    fun saveProfile(profile: AdaptiveProfile) {
        prefs.edit().putString(PREF_PROFILE_JSON, JsonCodec.gson.toJson(profile)).apply()
    }

    /**
     * Resets the long-term HR trim to zero. Use when environmental factors (heat block,
     * illness, overtraining) have biased the trim and the runner has recovered.
     * Does not affect pace buckets or session count.
     */
    @Synchronized
    fun resetLongTermTrim() {
        saveProfile(getProfile().copy(longTermHrTrimBpm = 0f))
    }
}
