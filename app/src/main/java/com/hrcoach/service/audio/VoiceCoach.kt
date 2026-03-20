package com.hrcoach.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.hrcoach.R
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity

class VoiceCoach(private val context: Context) {

    var verbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL

    private var mediaPlayer: MediaPlayer? = null

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun speak(event: CoachingEvent, guidanceText: String?) {
        if (verbosity == VoiceVerbosity.OFF) return

        val resId = when (verbosity) {
            VoiceVerbosity.OFF -> return
            VoiceVerbosity.MINIMAL -> minimalResFor(event)
            VoiceVerbosity.FULL -> fullResFor(event, guidanceText)
        } ?: return

        // Stop any in-progress playback (QUEUE_FLUSH equivalent)
        releasePlayer()

        mediaPlayer = MediaPlayer.create(context, resId, audioAttributes, 0)?.also { mp ->
            mp.setOnCompletionListener {
                it.release()
                mediaPlayer = null
            }
            mp.start()
        }
    }

    fun destroy() {
        releasePlayer()
    }

    private fun releasePlayer() {
        val mp = mediaPlayer
        mediaPlayer = null
        if (mp != null) {
            try {
                if (mp.isPlaying) mp.stop()
            } catch (_: IllegalStateException) {
                // MediaPlayer already released or in error state
            }
            try {
                mp.release()
            } catch (_: IllegalStateException) {
                // Already released
            }
        }
    }

    private fun minimalResFor(event: CoachingEvent): Int? {
        return when (event) {
            CoachingEvent.SPEED_UP -> R.raw.voice_speed_up
            CoachingEvent.SLOW_DOWN -> R.raw.voice_slow_down
            CoachingEvent.SEGMENT_CHANGE -> R.raw.voice_segment_change
            CoachingEvent.SIGNAL_LOST -> R.raw.voice_signal_lost
            else -> null
        }
    }

    private fun fullResFor(event: CoachingEvent, guidanceText: String?): Int? {
        return when (event) {
            CoachingEvent.SPEED_UP -> R.raw.voice_speed_up
            CoachingEvent.SLOW_DOWN -> R.raw.voice_slow_down
            CoachingEvent.RETURN_TO_ZONE -> R.raw.voice_return_to_zone
            CoachingEvent.PREDICTIVE_WARNING -> R.raw.voice_predictive_warning
            CoachingEvent.SEGMENT_CHANGE -> R.raw.voice_segment_change
            CoachingEvent.SIGNAL_LOST -> R.raw.voice_signal_lost
            CoachingEvent.SIGNAL_REGAINED -> R.raw.voice_signal_regained
            CoachingEvent.HALFWAY -> R.raw.voice_halfway
            CoachingEvent.WORKOUT_COMPLETE -> R.raw.voice_workout_complete
            CoachingEvent.IN_ZONE_CONFIRM -> R.raw.voice_in_zone_confirm
            CoachingEvent.KM_SPLIT -> kmSplitRes(guidanceText)
        }
    }

    private fun kmSplitRes(guidanceText: String?): Int? {
        val km = guidanceText?.toIntOrNull() ?: return null
        return KM_RESOURCES[km]
    }

    companion object {
        val KM_RESOURCES: Map<Int, Int> = mapOf(
            1 to R.raw.voice_km_1, 2 to R.raw.voice_km_2, 3 to R.raw.voice_km_3,
            4 to R.raw.voice_km_4, 5 to R.raw.voice_km_5, 6 to R.raw.voice_km_6,
            7 to R.raw.voice_km_7, 8 to R.raw.voice_km_8, 9 to R.raw.voice_km_9,
            10 to R.raw.voice_km_10, 11 to R.raw.voice_km_11, 12 to R.raw.voice_km_12,
            13 to R.raw.voice_km_13, 14 to R.raw.voice_km_14, 15 to R.raw.voice_km_15,
            16 to R.raw.voice_km_16, 17 to R.raw.voice_km_17, 18 to R.raw.voice_km_18,
            19 to R.raw.voice_km_19, 20 to R.raw.voice_km_20, 21 to R.raw.voice_km_21,
            22 to R.raw.voice_km_22, 23 to R.raw.voice_km_23, 24 to R.raw.voice_km_24,
            25 to R.raw.voice_km_25, 26 to R.raw.voice_km_26, 27 to R.raw.voice_km_27,
            28 to R.raw.voice_km_28, 29 to R.raw.voice_km_29, 30 to R.raw.voice_km_30,
            31 to R.raw.voice_km_31, 32 to R.raw.voice_km_32, 33 to R.raw.voice_km_33,
            34 to R.raw.voice_km_34, 35 to R.raw.voice_km_35, 36 to R.raw.voice_km_36,
            37 to R.raw.voice_km_37, 38 to R.raw.voice_km_38, 39 to R.raw.voice_km_39,
            40 to R.raw.voice_km_40, 41 to R.raw.voice_km_41, 42 to R.raw.voice_km_42,
            43 to R.raw.voice_km_43, 44 to R.raw.voice_km_44, 45 to R.raw.voice_km_45,
            46 to R.raw.voice_km_46, 47 to R.raw.voice_km_47, 48 to R.raw.voice_km_48,
            49 to R.raw.voice_km_49, 50 to R.raw.voice_km_50,
        )
    }
}
