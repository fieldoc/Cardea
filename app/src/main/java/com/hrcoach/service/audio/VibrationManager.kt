package com.hrcoach.service.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class VibrationManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var enabled: Boolean = true

    fun pulseAlert() {
        if (!enabled) return
        val deviceVibrator = vibrator ?: return
        if (!deviceVibrator.hasVibrator()) return

        val effect = VibrationEffect.createWaveform(
            longArrayOf(0, 150, 100, 150),
            intArrayOf(0, 200, 0, 200),
            -1
        )
        deviceVibrator.vibrate(effect)
    }

    fun destroy() {
        vibrator?.cancel()
    }
}
