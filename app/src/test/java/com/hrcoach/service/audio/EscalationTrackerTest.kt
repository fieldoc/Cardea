package com.hrcoach.service.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class EscalationTrackerTest {

    @Test
    fun `first second third alerts escalate and then cap`() {
        val tracker = EscalationTracker()

        assertEquals(EscalationLevel.EARCON_ONLY, tracker.onZoneAlert())
        assertEquals(EscalationLevel.EARCON_VOICE, tracker.onZoneAlert())
        assertEquals(EscalationLevel.EARCON_VOICE_VIBRATION, tracker.onZoneAlert())
        assertEquals(EscalationLevel.EARCON_VOICE_VIBRATION, tracker.onZoneAlert())
    }

    @Test
    fun `reset returns escalation to first level`() {
        val tracker = EscalationTracker()
        tracker.onZoneAlert()
        tracker.onZoneAlert()
        tracker.onZoneAlert()

        tracker.reset()

        assertEquals(EscalationLevel.EARCON_ONLY, tracker.onZoneAlert())
    }
}
