package com.hrcoach.service.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EarconSynthesizer {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentJob: Job? = null
    private var volumeScalar: Float = 0.8f

    fun setVolume(percent: Int) {
        volumeScalar = (percent.coerceIn(0, 100) / 100f)
    }

    fun playSpeedUp() = play(EarconWaveforms.risingArpeggio())

    fun playSlowDown() = play(EarconWaveforms.fallingArpeggio())

    fun playReturnToZone() = play(EarconWaveforms.warmChime())

    fun playPredictiveWarning() = play(EarconWaveforms.ascendingFifth())

    fun playSegmentChange() = play(EarconWaveforms.doubleTap())

    fun playSignalLost() = play(EarconWaveforms.sosClicks())

    fun playSignalRegained() = play(EarconWaveforms.brightPing())

    fun destroy() {
        currentJob?.cancel()
        scope.cancel()
    }

    private fun play(pcm: ShortArray) {
        currentJob?.cancel()
        currentJob = scope.launch {
            val track = buildAudioTrack(pcm.size)
            try {
                track.setVolume(volumeScalar)
                track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                track.play()
                delay(durationMsForSamples(pcm.size) + 40L)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Earcons are best-effort only.
            } finally {
                runCatching { track.stop() }
                track.release()
            }
        }
    }

    private fun buildAudioTrack(sampleCount: Int): AudioTrack {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(EarconWaveforms.SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        return AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(sampleCount * Short.SIZE_BYTES)
            .build()
    }

    private fun durationMsForSamples(sampleCount: Int): Long {
        return (sampleCount * 1000L / EarconWaveforms.SAMPLE_RATE_HZ).coerceAtLeast(1L)
    }
}
