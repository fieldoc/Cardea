package com.hrcoach.service.audio

import com.hrcoach.domain.model.VoiceVerbosity
import org.junit.Assert.assertEquals
import org.junit.Test

class EarconVerbosityGatingTest {

    @Test
    fun `earcon is suppressed when verbosity is OFF`() {
        assertEquals(false, CoachingAudioManager.shouldPlayEarcon(VoiceVerbosity.OFF))
    }

    @Test
    fun `earcon plays when verbosity is MINIMAL`() {
        assertEquals(true, CoachingAudioManager.shouldPlayEarcon(VoiceVerbosity.MINIMAL))
    }

    @Test
    fun `earcon plays when verbosity is FULL`() {
        assertEquals(true, CoachingAudioManager.shouldPlayEarcon(VoiceVerbosity.FULL))
    }
}
