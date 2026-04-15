package com.hrcoach.service.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.DistanceUnit
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    var distanceUnit: DistanceUnit = DistanceUnit.KM

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
                    // NonCancellable: a voice alert that has already started must complete even if
                    // destroy() cancels the scope mid-flight, so the runner isn't left with an
                    // earcon beep but no spoken cue.
                    scope.launch {
                        delay(300L)
                        withContext(NonCancellable) {
                            voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode, distanceUnit = distanceUnit)
                        }
                    }
                }
                if (escalationLevel == EscalationLevel.EARCON_VOICE_VIBRATION) {
                    vibrationManager.pulseAlert()
                }
            }

            CoachingEvent.RETURN_TO_ZONE -> {
                // Escalation reset is already handled by AlertPolicy.onResetEscalation callback
                // (which fires when IN_ZONE is first detected, before this event is emitted).
                if (shouldPlayEarcon(currentSettings.voiceVerbosity)) earconPlayer.play(event)
                voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode, distanceUnit = distanceUnit)
            }

            else -> {
                if (shouldPlayEarcon(currentSettings.voiceVerbosity)) earconPlayer.play(event)
                voicePlayer.speakEvent(event, guidanceText, currentWorkoutMode, paceMinPerKm, distanceUnit)
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
            // ToneGenerator takes an AudioManager.STREAM_* constant, not an AudioAttributes.USAGE_*
            // constant. STREAM_MUSIC routes through the media volume the runner controls during a run.
            val toneGenerator = try {
                ToneGenerator(AudioManager.STREAM_MUSIC, volume)
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

    /**
     * Speaks an auto-pause/resume announcement via TTS. Only for auto-pause events —
     * not called for manual pause. Respects voice verbosity (OFF = silent).
     */
    fun speakAnnouncement(text: String) {
        voicePlayer.speakAnnouncement(text)
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
