package com.hrcoach.domain.model

data class AudioSettings(
    val earconVolume: Int = 80,
    val voiceVolume: Int = 80,
    val voiceVerbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL,
    val enableVibration: Boolean = true,
    val enableHalfwayReminder: Boolean? = null,
    val enableKmSplits: Boolean? = null,
    val enableWorkoutComplete: Boolean? = null,
    val enableInZoneConfirm: Boolean? = null
)

enum class VoiceVerbosity {
    OFF,
    MINIMAL,
    FULL
}
