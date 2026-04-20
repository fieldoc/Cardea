package com.hrcoach.service.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.hrcoach.domain.model.CoachingEvent

enum class VibrationPattern { SPEED_UP, SLOW_DOWN, GENERIC_ALERT, NONE }

class VibrationManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var enabled: Boolean = true

    /**
     * Dispatches to a direction-coded pattern. At escalation tier 3, CoachingAudioManager
     * calls this instead of [pulseAlert] so the vibration *itself* encodes whether the
     * runner should speed up or slow down — learnable tactile signal.
     */
    fun pulseForEvent(event: CoachingEvent) {
        when (patternFor(event)) {
            VibrationPattern.SPEED_UP -> pulseSpeedUp()
            VibrationPattern.SLOW_DOWN -> pulseSlowDown()
            VibrationPattern.GENERIC_ALERT -> pulseAlert()
            VibrationPattern.NONE -> { /* no-op */ }
        }
    }

    /** "Hurry" — three short taps. */
    fun pulseSpeedUp() {
        vibrate(longArrayOf(0, 100, 80, 100, 80, 100), intArrayOf(0, 255, 0, 255, 0, 255))
    }

    /** "Settle" — two slow heavy pulses. */
    fun pulseSlowDown() {
        vibrate(longArrayOf(0, 280, 150, 280), intArrayOf(0, 200, 0, 200))
    }

    /** Original generic alert pattern. Preserved for SIGNAL_LOST and any caller without an event. */
    fun pulseAlert() {
        vibrate(longArrayOf(0, 150, 100, 150), intArrayOf(0, 200, 0, 200))
    }

    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        if (!enabled) return
        val deviceVibrator = vibrator ?: return
        if (!deviceVibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        deviceVibrator.vibrate(effect)
    }

    fun destroy() {
        vibrator?.cancel()
    }

    companion object {
        fun patternFor(event: CoachingEvent): VibrationPattern = when (event) {
            CoachingEvent.SPEED_UP -> VibrationPattern.SPEED_UP
            CoachingEvent.SLOW_DOWN -> VibrationPattern.SLOW_DOWN
            CoachingEvent.SIGNAL_LOST -> VibrationPattern.GENERIC_ALERT
            else -> VibrationPattern.NONE
        }
    }
}
