package com.hrcoach.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.ToneGenerator
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
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
    private val voicePlayer = VoicePlayer(context)
    private val vibrationManager = VibrationManager(context)
    private val startupSequencer = StartupSequencer(context)
    private val escalationTracker = EscalationTracker()
    private var currentSettings: AudioSettings = settings
    private var currentWorkoutMode: WorkoutMode = WorkoutMode.STEADY_STATE

    init {
        applySettings(settings)
    }

    fun applySettings(settings: AudioSettings) {
        currentSettings = settings
        earconPlayer.setVolume(settings.earconVolume)
        voicePlayer.setVolume(settings.voiceVolume)
        voicePlayer.verbosity = settings.voiceVerbosity
        vibrationManager.enabled = settings.enableVibration
    }

    fun setWorkoutMode(mode: WorkoutMode) {
        currentWorkoutMode = mode
    }

    /**
     * Plays the full startup sequence: TTS voice briefing of the workout config,
     * followed by a 3-2-1-GO countdown WAV. Suspends for the full duration.
     */
    suspend fun playStartSequence(config: WorkoutConfig) {
        currentWorkoutMode = config.mode
        voicePlayer.speakBriefing(config)
        delay(500L)
        startupSequencer.playCountdown(volumePercent = currentSettings.earconVolume)
    }

    fun fireEvent(event: CoachingEvent, guidanceText: String? = null, paceMinPerKm: Float? = null) {
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
                        voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode)
                    }
                }
                if (escalationLevel == EscalationLevel.EARCON_VOICE_VIBRATION) {
                    vibrationManager.pulseAlert()
                }
            }

            CoachingEvent.RETURN_TO_ZONE -> {
                escalationTracker.reset()
                if (shouldPlayEarcon(currentSettings.voiceVerbosity)) earconPlayer.play(event)
                voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode)
            }

            else -> {
                if (shouldPlayEarcon(currentSettings.voiceVerbosity)) earconPlayer.play(event)
                voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode, paceMinPerKm)
            }
        }
    }

    fun resetEscalation() {
        escalationTracker.reset()
    }

    /**
     * Plays a short transition tone to confirm pause/resume to the runner.
     * Pause = two descending tones; Resume = two ascending tones.
     * Always plays regardless of voice verbosity — tones use earconVolume which is independent.
     */
    fun playPauseFeedback(paused: Boolean) {
        val volume = currentSettings.earconVolume.coerceIn(0, 100)
        scope.launch {
            val toneGenerator = try {
                ToneGenerator(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, volume)
            } catch (_: RuntimeException) {
                return@launch
            }
            try {
                if (paused) {
                    // Descending: high-pitch then low-pitch
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
                    delay(250L)
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    delay(200L)
                } else {
                    // Ascending: low-pitch then high-pitch
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    delay(250L)
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
                    delay(200L)
                }
            } finally {
                toneGenerator.release()
            }
        }
    }

    fun destroy() {
        scope.cancel()
        earconPlayer.destroy()
        voicePlayer.destroy()
        vibrationManager.destroy()
    }

    companion object {
        /** Returns true when earcons should fire. OFF suppresses earcons; other levels allow them. */
        fun shouldPlayEarcon(verbosity: VoiceVerbosity): Boolean =
            verbosity != VoiceVerbosity.OFF
    }
}
