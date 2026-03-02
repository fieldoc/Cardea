package com.hrcoach.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity
import java.util.Locale

class VoiceCoach(context: Context) {

    @Volatile
    private var isReady = false

    private var tts: TextToSpeech? = null

    var verbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL

    init {
        val appContext = context.applicationContext
        tts = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                isReady = false
                return@TextToSpeech
            }

            val engine = tts ?: return@TextToSpeech
            val languageResult = engine.setLanguage(Locale.US)
            if (
                languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                isReady = false
                return@TextToSpeech
            }

            engine.setSpeechRate(1.1f)
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            isReady = true
        }
    }

    fun speak(event: CoachingEvent, guidanceText: String?) {
        if (!isReady || verbosity == VoiceVerbosity.OFF) return
        val utterance = when (verbosity) {
            VoiceVerbosity.OFF -> null
            VoiceVerbosity.MINIMAL -> minimalUtteranceFor(event)
            VoiceVerbosity.FULL -> fullUtteranceFor(event, guidanceText)
        } ?: return

        tts?.speak(
            utterance,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "coach_${event.name}_${System.currentTimeMillis()}"
        )
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    private fun minimalUtteranceFor(event: CoachingEvent): String? {
        return when (event) {
            CoachingEvent.SPEED_UP -> "Pick it up"
            CoachingEvent.SLOW_DOWN -> "Easy, easy"
            CoachingEvent.SEGMENT_CHANGE -> "Next segment"
            CoachingEvent.SIGNAL_LOST -> "Signal lost"
            else -> null
        }
    }

    private fun fullUtteranceFor(event: CoachingEvent, guidanceText: String?): String {
        return when (event) {
            CoachingEvent.SPEED_UP -> guidanceText?.takeIf { it.isNotBlank() } ?: "Pick it up"
            CoachingEvent.SLOW_DOWN -> guidanceText?.takeIf { it.isNotBlank() } ?: "Ease off slightly"
            CoachingEvent.RETURN_TO_ZONE -> "Back in zone"
            CoachingEvent.PREDICTIVE_WARNING -> guidanceText?.takeIf { it.isNotBlank() } ?: "Watch your pace"
            CoachingEvent.SEGMENT_CHANGE -> "Next segment"
            CoachingEvent.SIGNAL_LOST -> "Signal lost"
            CoachingEvent.SIGNAL_REGAINED -> "Signal back"
        }
    }
}
