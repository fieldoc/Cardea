package com.hrcoach.ui.postrun

import android.content.Context
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.service.audio.EarconPlayer
import com.hrcoach.service.audio.VoicePlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Post-workout HRR (Heart Rate Recovery) cooldown bookends.
 * Plays an earcon + TTS at the start of the 120s HRR window ("Walk slowly") and at the end
 * ("Recovery measurement complete") so the runner can look away from the phone.
 *
 * Independent from WorkoutForegroundService's CoachingAudioManager because that manager
 * is destroyed in stopWorkout(). We own our own EarconPlayer + VoicePlayer for the window.
 * The helper is @Singleton — it lives for the process lifetime so the TTS engine stays
 * warm across multiple runs.
 *
 * Verbosity:
 *  - OFF: silent (no earcon, no voice).
 *  - MINIMAL: voice only (no earcon — mirrors CoachingAudioManager's rule that MINIMAL
 *    suppresses informational earcons).
 *  - FULL: earcon + voice.
 *
 * Asset reuse: earcon_in_zone_confirm for the start cue, earcon_workout_complete for the
 * end cue. No new audio assets required.
 *
 * KNOWN LIMITATION: The very first invocation after process start may silently drop the
 * TTS (VoicePlayer needs a few hundred ms to initialize its TextToSpeech engine; it no-ops
 * when !ttsReady). The earcon still plays. On subsequent runs within the same process,
 * TTS is warm and both parts play. Acceptable trade-off for keeping the helper simple.
 */
@Singleton
class HrrAudio @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioSettingsRepository: AudioSettingsRepository
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var earconPlayer: EarconPlayer? = null
    private var voicePlayer: VoicePlayer? = null

    private fun ensureInitialised(settings: AudioSettings) {
        if (earconPlayer == null) earconPlayer = EarconPlayer(context).also {
            it.setVolume(settings.earconVolume)
        }
        if (voicePlayer == null) voicePlayer = VoicePlayer(context).also {
            it.setVolume(settings.voiceVolume)
            it.verbosity = settings.voiceVerbosity
        }
    }

    fun playHrrStart() = scope.launch {
        val settings = audioSettingsRepository.getAudioSettings()
        if (settings.voiceVerbosity == VoiceVerbosity.OFF) return@launch
        ensureInitialised(settings)
        if (settings.voiceVerbosity == VoiceVerbosity.FULL) {
            earconPlayer?.play(CoachingEvent.IN_ZONE_CONFIRM)
        }
        voicePlayer?.speakAnnouncement("Walk slowly. Measuring your recovery.")
    }

    fun playHrrComplete() = scope.launch {
        val settings = audioSettingsRepository.getAudioSettings()
        if (settings.voiceVerbosity == VoiceVerbosity.OFF) return@launch
        ensureInitialised(settings)
        if (settings.voiceVerbosity == VoiceVerbosity.FULL) {
            earconPlayer?.play(CoachingEvent.WORKOUT_COMPLETE)
        }
        voicePlayer?.speakAnnouncement("Recovery measurement complete.")
    }

    /**
     * Release audio resources. Not currently called — the helper is @Singleton and lives
     * for the process lifetime. Provided for future use if the class is ever demoted out
     * of singleton scope.
     */
    fun release() {
        earconPlayer?.destroy()
        earconPlayer = null
        voicePlayer?.destroy()
        voicePlayer = null
    }
}
