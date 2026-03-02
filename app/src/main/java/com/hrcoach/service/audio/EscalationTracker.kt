package com.hrcoach.service.audio

enum class EscalationLevel {
    EARCON_ONLY,
    EARCON_VOICE,
    EARCON_VOICE_VIBRATION
}

class EscalationTracker {
    private var count = 0

    fun onZoneAlert(): EscalationLevel {
        count += 1
        return when {
            count <= 1 -> EscalationLevel.EARCON_ONLY
            count == 2 -> EscalationLevel.EARCON_VOICE
            else -> EscalationLevel.EARCON_VOICE_VIBRATION
        }
    }

    fun reset() {
        count = 0
    }
}
