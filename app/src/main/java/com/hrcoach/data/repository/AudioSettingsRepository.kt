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
    @ApplicationContext context: Context
) {
    companion object {
        private const val PREFS_NAME = "hr_coach_audio_settings"
        private const val PREF_AUDIO_SETTINGS_JSON = "audio_settings_json"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val audioSettingsType = object : TypeToken<AudioSettings>() {}.type

    @Synchronized
    fun getAudioSettings(): AudioSettings {
        val raw = prefs.getString(PREF_AUDIO_SETTINGS_JSON, null) ?: return AudioSettings()
        return runCatching {
            JsonCodec.gson.fromJson<AudioSettings>(raw, audioSettingsType)
        }.getOrElse { AudioSettings() }
    }

    @Synchronized
    fun saveAudioSettings(settings: AudioSettings) {
        prefs.edit().putString(PREF_AUDIO_SETTINGS_JSON, JsonCodec.gson.toJson(settings)).apply()
    }

    fun setAudioPrimerShown(shown: Boolean) {
        val current = getAudioSettings()
        saveAudioSettings(current.copy(audioPrimerShown = shown))
    }
}
