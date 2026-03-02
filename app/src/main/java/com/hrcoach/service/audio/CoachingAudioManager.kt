package com.hrcoach.service.audio

import android.content.Context
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.CoachingEvent
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

    private val earconSynthesizer = EarconSynthesizer()
    private val voiceCoach = VoiceCoach(context)
    private val vibrationManager = VibrationManager(context)
    private val escalationTracker = EscalationTracker()

    init {
        applySettings(settings)
    }

    fun applySettings(settings: AudioSettings) {
        earconSynthesizer.setVolume(settings.earconVolume)
        voiceCoach.verbosity = settings.voiceVerbosity
        vibrationManager.enabled = settings.enableVibration
    }

    fun fireEvent(event: CoachingEvent, guidanceText: String? = null) {
        when (event) {
            CoachingEvent.SPEED_UP,
            CoachingEvent.SLOW_DOWN -> {
                val escalationLevel = escalationTracker.onZoneAlert()
                playEarcon(event)
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
                playEarcon(event)
                voiceCoach.speak(event, guidanceText)
            }

            else -> {
                playEarcon(event)
                voiceCoach.speak(event, guidanceText)
            }
        }
    }

    fun resetEscalation() {
        escalationTracker.reset()
    }

    fun destroy() {
        scope.cancel()
        earconSynthesizer.destroy()
        voiceCoach.destroy()
        vibrationManager.destroy()
    }

    private fun playEarcon(event: CoachingEvent) {
        when (event) {
            CoachingEvent.SPEED_UP -> earconSynthesizer.playSpeedUp()
            CoachingEvent.SLOW_DOWN -> earconSynthesizer.playSlowDown()
            CoachingEvent.RETURN_TO_ZONE -> earconSynthesizer.playReturnToZone()
            CoachingEvent.PREDICTIVE_WARNING -> earconSynthesizer.playPredictiveWarning()
            CoachingEvent.SEGMENT_CHANGE -> earconSynthesizer.playSegmentChange()
            CoachingEvent.SIGNAL_LOST -> earconSynthesizer.playSignalLost()
            CoachingEvent.SIGNAL_REGAINED -> earconSynthesizer.playSignalRegained()
        }
    }
}
