package com.hrcoach.service.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsBriefingPlayerAdHocTest {

    @Test
    fun `kmAnnouncementText returns correct string for km 51`() {
        assertEquals("Kilometer 51", TtsBriefingPlayer.kmAnnouncementText(51))
    }

    @Test
    fun `kmAnnouncementText returns correct string for km 100`() {
        assertEquals("Kilometer 100", TtsBriefingPlayer.kmAnnouncementText(100))
    }

    @Test
    fun `kmAnnouncementText returns correct string for km 1`() {
        assertEquals("Kilometer 1", TtsBriefingPlayer.kmAnnouncementText(1))
    }
}
