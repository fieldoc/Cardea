package com.hrcoach.service.audio

import android.content.Context
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.model.WorkoutConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CoachingAudioManager(
    context: Context,
    settings: AudioSettings
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val earconPlayer = EarconPlayer(context)
    private val voiceCoach = VoiceCoach(context)
    private val vibrationManager = VibrationManager(context)
    private val ttsBriefingPlayer = TtsBriefingPlayer(context)
    private val startupSequencer = StartupSequencer()
    private val escalationTracker = EscalationTracker()
    private var currentSettings: AudioSettings = settings

    init {
        applySettings(settings)
    }

    fun applySettings(settings: AudioSettings) {
        currentSettings = settings
        earconPlayer.setVolume(settings.earconVolume)
        voiceCoach.verbosity = settings.voiceVerbosity
        ttsBriefingPlayer.verbosity = settings.voiceVerbosity
        vibrationManager.enabled = settings.enableVibration
    }

    /**
     * Plays the full startup sequence: 3-2-1-GO countdown beeps followed by
     * a TTS voice briefing of the workout config. Suspends for the duration
     * of the countdown (~4s); the TTS briefing plays asynchronously after.
     */
    suspend fun playStartSequence(config: WorkoutConfig) {
        startupSequencer.playCountdown()
        ttsBriefingPlayer.speakBriefing(config)
    }

    fun fireEvent(event: CoachingEvent, guidanceText: String? = null) {
        // Filter informational cues by individual toggles
        when (event) {
            CoachingEvent.HALFWAY -> if (currentSettings.enableHalfwayReminder == false) return
            CoachingEvent.KM_SPLIT -> if (currentSettings.enableKmSplits == false) return
            CoachingEvent.WORKOUT_COMPLETE -> if (currentSettings.enableWorkoutComplete == false) return
            CoachingEvent.IN_ZONE_CONFIRM -> if (currentSettings.enableInZoneConfirm == false) return
            else -> { /* coaching alerts always pass through */ }
        }

        when (event) {
            CoachingEvent.SPEED_UP,
            CoachingEvent.SLOW_DOWN -> {
                val escalationLevel = escalationTracker.onZoneAlert()
                if (shouldPlayEarcon(currentSettings.voiceVerbosity)) earconPlayer.play(event)
                if (
                    escalationLevel == EscalationLevel.EARCON_VOICE ||
                    escalationLevel == EscalationLevel.EARCON_VOICE_VIBRATION
                ) {
                    scope.launch {
                        delay(300L)
                        voiceCoach.speak(event, guidanceText)
                    }
                }
                if (escalationLevel == EscalationLevel.EARCON_VOICE_VIBRATION) {
                    vibrationManager.pulseAlert()
                }
            }

            CoachingEvent.RETURN_TO_ZONE -> {
                escalationTracker.reset()
                if (shouldPlayEarcon(currentSettings.voiceVerbosity)) earconPlayer.play(event)
                voiceCoach.speak(event, guidanceText)
            }

            else -> {
                if (shouldPlayEarcon(currentSettings.voiceVerbosity)) earconPlayer.play(event)
                voiceCoach.speak(event, guidanceText)
            }
        }
    }

    fun resetEscalation() {
        escalationTracker.reset()
    }

    fun destroy() {
        scope.cancel()
        earconPlayer.destroy()
        voiceCoach.destroy()
        ttsBriefingPlayer.destroy()
        vibrationManager.destroy()
    }

    companion object {
        /** Returns true when earcons should fire. OFF suppresses earcons; other levels allow them. */
        fun shouldPlayEarcon(verbosity: VoiceVerbosity): Boolean =
            verbosity != VoiceVerbosity.OFF
    }
}
