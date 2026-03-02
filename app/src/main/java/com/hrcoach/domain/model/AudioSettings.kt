package com.hrcoach.domain.model

data class AudioSettings(
    val earconVolume: Int = 80,
    val voiceVerbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL,
    val enableVibration: Boolean = true
)

enum class VoiceVerbosity {
    OFF,
    MINIMAL,
    FULL
}
