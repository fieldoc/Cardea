package com.hrcoach.data.repository

import android.content.Context
import com.google.gson.JsonParser
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

        // Boolean fields on AudioSettings whose Kotlin data-class default is `true`.
        // Gson allocates the data class via Unsafe and bypasses the synthetic
        // constructor, so any of these MISSING from a previously-persisted JSON blob
        // would otherwise come back as the JVM Boolean default (false) — silently
        // disabling features for upgrading users. We backfill missing keys here
        // before deserializing. Keep this list in sync when adding new
        // `Boolean = true` fields. Nullable Boolean? fields don't need migration
        // (JVM default for object refs is null, matching the Kotlin default).
        private val DEFAULT_TRUE_BOOLEAN_FIELDS = listOf(
            "enableVibration",
            "minimalTierOneVoice",
            "stridesTimerEarcons",
        )
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val audioSettingsType = object : TypeToken<AudioSettings>() {}.type

    @Synchronized
    fun getAudioSettings(): AudioSettings {
        val raw = prefs.getString(PREF_AUDIO_SETTINGS_JSON, null) ?: return AudioSettings()
        return runCatching {
            val obj = JsonParser.parseString(raw).asJsonObject
            var migrated = false
            for (field in DEFAULT_TRUE_BOOLEAN_FIELDS) {
                if (!obj.has(field)) {
                    obj.addProperty(field, true)
                    migrated = true
                }
            }
            val settings: AudioSettings = JsonCodec.gson.fromJson(obj, audioSettingsType)
            if (migrated) {
                // Persist the migrated form so the next read is clean and the
                // migration is idempotent.
                prefs.edit().putString(
                    PREF_AUDIO_SETTINGS_JSON,
                    JsonCodec.gson.toJson(settings)
                ).apply()
            }
            settings
        }.getOrElse { AudioSettings() }
    }

    @Synchronized
    fun saveAudioSettings(settings: AudioSettings) {
        prefs.edit().putString(PREF_AUDIO_SETTINGS_JSON, JsonCodec.gson.toJson(settings)).apply()
    }

    @Synchronized
    fun setAudioPrimerShown(shown: Boolean) {
        val current = getAudioSettings()
        saveAudioSettings(current.copy(audioPrimerShown = shown))
    }

    @Synchronized
    fun setStridesPrimerSeen(seen: Boolean) {
        val current = getAudioSettings()
        saveAudioSettings(current.copy(stridesPrimerSeen = seen))
    }
}
