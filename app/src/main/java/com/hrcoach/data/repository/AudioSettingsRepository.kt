package com.hrcoach.data.repository

import android.content.Context
import com.google.gson.reflect.TypeToken
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.util.JsonCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) {
    companion object {
        private const val PREFS_PREFIX = "hr_coach_audio_settings_"
        private const val LEGACY_PREFS_NAME = "hr_coach_audio_settings"
        private const val PREF_AUDIO_SETTINGS_JSON = "audio_settings_json"
    }

    private val audioSettingsType = object : TypeToken<AudioSettings>() {}.type

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
    fun getAudioSettings(): AudioSettings {
        val raw = prefs().getString(PREF_AUDIO_SETTINGS_JSON, null) ?: return AudioSettings()
        return runCatching {
            JsonCodec.gson.fromJson<AudioSettings>(raw, audioSettingsType)
        }.getOrElse { AudioSettings() }
    }

    @Synchronized
    fun saveAudioSettings(settings: AudioSettings) {
        prefs().edit().putString(PREF_AUDIO_SETTINGS_JSON, JsonCodec.gson.toJson(settings)).apply()
    }
}
