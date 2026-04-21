package com.hrcoach.domain.education

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneEducationProviderTest {

    @Test
    fun `BADGE returns single fixed string per zone (no rotation)`() {
        val day1 = 19834L
        val day2 = 19899L
        for (zone in ZoneId.values()) {
            val a = ZoneEducationProvider.getContent(zone, ContentDensity.BADGE, dayEpoch = day1)
            val b = ZoneEducationProvider.getContent(zone, ContentDensity.BADGE, dayEpoch = day2)
            assertEquals("BADGE for $zone must be stable across days", a, b)
            assertTrue("BADGE for $zone should be short label", a.length in 4..30)
        }
    }

    @Test
    fun `ONE_LINER and FULL return same content for same day and zone`() {
        val day = 19834L
        for (zone in ZoneId.values()) {
            for (density in listOf(ContentDensity.ONE_LINER, ContentDensity.FULL)) {
                val a = ZoneEducationProvider.getContent(zone, density, dayEpoch = day)
                val b = ZoneEducationProvider.getContent(zone, density, dayEpoch = day)
                assertEquals(a, b)
            }
        }
    }

    @Test
    fun `forSessionType routes EASY to ZONE_2 pool`() {
        val day = 19834L
        val viaType = ZoneEducationProvider.forSessionType("EASY", ContentDensity.ONE_LINER, dayEpoch = day)
        val viaZone = ZoneEducationProvider.getContent(ZoneId.ZONE_2, ContentDensity.ONE_LINER, dayEpoch = day)
        assertNotNull(viaType)
        assertEquals(viaZone, viaType)
    }

    @Test
    fun `forSessionType returns null for unknown raw type`() {
        assertNull(ZoneEducationProvider.forSessionType("NOPE", ContentDensity.BADGE, dayEpoch = 0L))
    }

    @Test
    fun `BPM range appended to ONE_LINER when maxHr supplied`() {
        val withMax = ZoneEducationProvider.getContent(
            ZoneId.ZONE_2, ContentDensity.ONE_LINER,
            maxHr = 190, restHr = 50, dayEpoch = 19834L
        )
        val withoutMax = ZoneEducationProvider.getContent(
            ZoneId.ZONE_2, ContentDensity.ONE_LINER,
            maxHr = null, restHr = null, dayEpoch = 19834L
        )
        assertTrue("BPM suffix expected", withMax.contains("BPM"))
        assertFalse("no BPM when maxHr null", withoutMax.contains("BPM"))
    }
}
