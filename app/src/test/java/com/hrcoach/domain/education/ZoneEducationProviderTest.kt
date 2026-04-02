package com.hrcoach.domain.education

import com.hrcoach.domain.bootcamp.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneEducationProviderTest {

    // ── Badge density ────────────────────────────────────────────────────

    @Test
    fun `badge returns short text for all zones`() {
        for (zone in ZoneId.entries) {
            val badge = ZoneEducationProvider.getContent(zone, ContentDensity.BADGE)
            assertTrue(
                "Badge for $zone should be <= 15 chars but was '${badge}' (${badge.length})",
                badge.length <= 15
            )
        }
    }

    @Test
    fun `badge does not include BPM even when maxHr is provided`() {
        val badge = ZoneEducationProvider.getContent(ZoneId.ZONE_2, ContentDensity.BADGE, maxHr = 190)
        assertTrue("Badge should not contain BPM", !badge.contains("BPM"))
    }

    // ── One-liner density ────────────────────────────────────────────────

    @Test
    fun `one-liner includes BPM range when maxHr is known`() {
        val text = ZoneEducationProvider.getContent(ZoneId.ZONE_2, ContentDensity.ONE_LINER, maxHr = 190)
        assertTrue("One-liner should contain BPM when maxHr provided", text.contains("BPM"))
    }

    @Test
    fun `one-liner omits BPM range when maxHr is null`() {
        val text = ZoneEducationProvider.getContent(ZoneId.ZONE_2, ContentDensity.ONE_LINER, maxHr = null)
        assertTrue("One-liner should not contain BPM when maxHr is null", !text.contains("BPM"))
    }

    @Test
    fun `one-liner returns distinct text per zone`() {
        val texts = ZoneId.entries.map {
            ZoneEducationProvider.getContent(it, ContentDensity.ONE_LINER)
        }.toSet()
        assertEquals("Each zone should have unique one-liner text", ZoneId.entries.size, texts.size)
    }

    // ── Full density ─────────────────────────────────────────────────────

    @Test
    fun `full text includes discovery nudge when maxHr is null`() {
        val text = ZoneEducationProvider.getContent(ZoneId.ZONE_2, ContentDensity.FULL, maxHr = null)
        assertTrue("Full text should nudge Discovery session", text.contains("Discovery"))
    }

    @Test
    fun `full text includes personal range when maxHr is known`() {
        val text = ZoneEducationProvider.getContent(ZoneId.ZONE_3, ContentDensity.FULL, maxHr = 185)
        assertTrue("Full text should include 'Your range'", text.contains("Your range"))
        assertTrue("Full text should include BPM", text.contains("BPM"))
    }

    @Test
    fun `full text is longer than one-liner for all zones`() {
        for (zone in ZoneId.entries) {
            val oneLiner = ZoneEducationProvider.getContent(zone, ContentDensity.ONE_LINER)
            val full = ZoneEducationProvider.getContent(zone, ContentDensity.FULL)
            assertTrue(
                "Full text for $zone should be longer than one-liner",
                full.length > oneLiner.length
            )
        }
    }

    // ── Session type mapping ─────────────────────────────────────────────

    @Test
    fun `zoneForSessionType maps all session types`() {
        assertEquals(ZoneId.ZONE_2, ZoneEducationProvider.zoneForSessionType(SessionType.EASY))
        assertEquals(ZoneId.ZONE_2, ZoneEducationProvider.zoneForSessionType(SessionType.LONG))
        assertEquals(ZoneId.ZONE_2, ZoneEducationProvider.zoneForSessionType(SessionType.STRIDES))
        assertEquals(ZoneId.ZONE_3, ZoneEducationProvider.zoneForSessionType(SessionType.TEMPO))
        assertEquals(ZoneId.ZONE_4_5, ZoneEducationProvider.zoneForSessionType(SessionType.INTERVAL))
        assertEquals(ZoneId.RACE_PACE, ZoneEducationProvider.zoneForSessionType(SessionType.RACE_SIM))
        assertEquals(ZoneId.RECOVERY, ZoneEducationProvider.zoneForSessionType(SessionType.DISCOVERY))
        assertEquals(ZoneId.RECOVERY, ZoneEducationProvider.zoneForSessionType(SessionType.CHECK_IN))
    }

    // ── forSessionType convenience ───────────────────────────────────────

    @Test
    fun `forSessionType returns content for valid raw type`() {
        val text = ZoneEducationProvider.forSessionType("TEMPO", ContentDensity.BADGE)
        assertNotNull(text)
        assertEquals("Threshold", text)
    }

    @Test
    fun `forSessionType returns null for invalid raw type`() {
        val text = ZoneEducationProvider.forSessionType("INVALID", ContentDensity.BADGE)
        assertNull(text)
    }

    // ── BPM range calculation ────────────────────────────────────────────

    @Test
    fun `BPM range uses buffer correctly with no restHr`() {
        // Zone 2: 60-70% of HRmax=200 (restHr=null -> 0) = 120-140, with buffer 5 = 115-145
        val text = ZoneEducationProvider.getContent(
            ZoneId.ZONE_2, ContentDensity.ONE_LINER, maxHr = 200, bufferBpm = 5
        )
        assertTrue("Should contain computed range", text.contains("115"))
        assertTrue("Should contain computed range", text.contains("145"))
    }

    @Test
    fun `BPM range uses Karvonen formula when restHr is provided`() {
        // Zone 2: 60-70% of reserve. maxHr=191, restHr=60, reserve=131
        // low: 60 + 131*0.60 = 139, high: 60 + 131*0.70 = 152
        // with buffer 5: "134-157 BPM"
        val text = ZoneEducationProvider.getContent(
            ZoneId.ZONE_2, ContentDensity.ONE_LINER, maxHr = 191, restHr = 60, bufferBpm = 5
        )
        assertTrue("Expected Karvonen range with 134, got: $text", text.contains("134"))
        assertTrue("Expected Karvonen range with 157, got: $text", text.contains("157"))
    }
}
