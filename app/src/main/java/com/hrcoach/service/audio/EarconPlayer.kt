package com.hrcoach.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.hrcoach.R
import com.hrcoach.domain.model.CoachingEvent
import java.util.concurrent.ConcurrentHashMap

class EarconPlayer(context: Context) {

    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<CoachingEvent, Int>()
    // Samples whose async load completed. SoundPool.play() on a not-yet-loaded sample returns
    // streamId=0 silently — on cold service start a SIGNAL_LOST earcon can easily race ahead of
    // its load and drop with no log. This set is populated by setOnLoadCompleteListener and
    // gated in play().
    private val loadedSampleIds = ConcurrentHashMap.newKeySet<Int>()

    private var volumeScalar: Float = 0.8f

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSampleIds.add(sampleId)
            } else {
                Log.w(TAG, "Earcon sample $sampleId failed to load (status=$status)")
            }
        }

        val mapping = mapOf(
            CoachingEvent.SPEED_UP to R.raw.earcon_speed_up,
            CoachingEvent.SLOW_DOWN to R.raw.earcon_slow_down,
            CoachingEvent.RETURN_TO_ZONE to R.raw.earcon_return_to_zone,
            CoachingEvent.PREDICTIVE_WARNING to R.raw.earcon_predictive_warning,
            CoachingEvent.SEGMENT_CHANGE to R.raw.earcon_segment_change,
            CoachingEvent.SIGNAL_LOST to R.raw.earcon_signal_lost,
            CoachingEvent.SIGNAL_REGAINED to R.raw.earcon_signal_regained,
            CoachingEvent.HALFWAY to R.raw.earcon_halfway,
            CoachingEvent.KM_SPLIT to R.raw.earcon_km_split,
            CoachingEvent.WORKOUT_COMPLETE to R.raw.earcon_workout_complete,
            CoachingEvent.IN_ZONE_CONFIRM to R.raw.earcon_in_zone_confirm,
        )
        mapping.forEach { (event, resId) ->
            soundIds[event] = soundPool.load(context, resId, 1)
        }
    }

    fun setVolume(percent: Int) {
        volumeScalar = (percent.coerceIn(0, 100) / 100f)
    }

    fun play(event: CoachingEvent) {
        val soundId = soundIds[event] ?: return
        if (soundId !in loadedSampleIds) {
            Log.w(TAG, "Dropping earcon $event — sample $soundId not yet loaded")
            return
        }
        soundPool.play(soundId, volumeScalar, volumeScalar, 1, 0, 1f)
    }

    fun destroy() {
        soundPool.release()
        loadedSampleIds.clear()
    }

    companion object {
        private const val TAG = "EarconPlayer"
    }
}
