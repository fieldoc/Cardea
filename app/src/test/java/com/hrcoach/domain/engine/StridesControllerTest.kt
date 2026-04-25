package com.hrcoach.domain.engine

import com.hrcoach.domain.model.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StridesControllerTest {

    @Test
    fun `repsForDuration returns expected counts`() {
        assertEquals(4, StridesController.repsForDuration(20))
        assertEquals(6, StridesController.repsForDuration(22))
        assertEquals(8, StridesController.repsForDuration(24))
        assertEquals(10, StridesController.repsForDuration(26))
        assertEquals(5, StridesController.repsForDuration(30))
    }

    @Test
    fun `idle stays idle when elapsedSec is below 1200`() {
        val c = StridesController(durationMin = 24)
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(0, ZoneStatus.IN_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(600, ZoneStatus.IN_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(1199, ZoneStatus.IN_ZONE))
        assertFalse(c.isActive)
    }

    @Test
    fun `idle stays idle at 1200 when not in zone`() {
        val c = StridesController(durationMin = 24)
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(1200, ZoneStatus.ABOVE_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(1200, ZoneStatus.BELOW_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(1200, ZoneStatus.NO_DATA))
        assertFalse(c.isActive)
    }

    @Test
    fun `first in-zone tick at 1200 emits Announce and RepStart`() {
        val c = StridesController(durationMin = 24)
        val events = c.evaluateTick(1200, ZoneStatus.IN_ZONE)
        assertEquals(
            listOf(
                StridesEvent.Announce(8),
                StridesEvent.RepStart(1, 8)
            ),
            events
        )
        assertTrue(c.isActive)
    }

    @Test
    fun `Work to Rest at +20s emits RepEnd`() {
        val c = StridesController(durationMin = 24)
        c.evaluateTick(1200, ZoneStatus.IN_ZONE)  // start rep 1
        val events = c.evaluateTick(1220, ZoneStatus.IN_ZONE)
        assertEquals(listOf(StridesEvent.RepEnd), events)
    }

    @Test
    fun `Work to Rest before +20s emits empty list`() {
        val c = StridesController(durationMin = 24)
        c.evaluateTick(1200, ZoneStatus.IN_ZONE)
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(1210, ZoneStatus.IN_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(1219, ZoneStatus.IN_ZONE))
    }

    @Test
    fun `Rest to next Work at +80s emits RepStart for next rep`() {
        val c = StridesController(durationMin = 24)
        c.evaluateTick(1200, ZoneStatus.IN_ZONE)  // start rep 1
        c.evaluateTick(1220, ZoneStatus.IN_ZONE)  // RepEnd, enter Rest
        val events = c.evaluateTick(1280, ZoneStatus.IN_ZONE)  // +80s in cycle
        assertEquals(listOf(StridesEvent.RepStart(2, 8)), events)
        assertTrue(c.isActive)
    }

    @Test
    fun `final rep transition emits SetComplete not RepStart`() {
        val c = StridesController(durationMin = 20)  // 4 reps
        var t = 1200L
        // rep 1
        c.evaluateTick(t, ZoneStatus.IN_ZONE)
        c.evaluateTick(t + 20, ZoneStatus.IN_ZONE)
        // rep 2
        t += 80
        c.evaluateTick(t, ZoneStatus.IN_ZONE)
        c.evaluateTick(t + 20, ZoneStatus.IN_ZONE)
        // rep 3
        t += 80
        c.evaluateTick(t, ZoneStatus.IN_ZONE)
        c.evaluateTick(t + 20, ZoneStatus.IN_ZONE)
        // rep 4
        t += 80
        c.evaluateTick(t, ZoneStatus.IN_ZONE)
        c.evaluateTick(t + 20, ZoneStatus.IN_ZONE)  // RepEnd
        // +80s after rep 4 cycle start -> SetComplete
        t += 80
        val events = c.evaluateTick(t, ZoneStatus.IN_ZONE)
        assertEquals(listOf(StridesEvent.SetComplete), events)
        assertFalse(c.isActive)
    }

    @Test
    fun `after Done additional ticks emit empty list`() {
        val c = StridesController(durationMin = 20)  // 4 reps
        var t = 1200L
        repeat(4) { rep ->
            c.evaluateTick(t, ZoneStatus.IN_ZONE)        // RepStart
            c.evaluateTick(t + 20, ZoneStatus.IN_ZONE)   // RepEnd
            t += 80
        }
        // t now equals cycleStart of rep4 + 80 -> SetComplete
        val complete = c.evaluateTick(t, ZoneStatus.IN_ZONE)
        assertEquals(listOf(StridesEvent.SetComplete), complete)
        // further ticks no-op
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(t + 1, ZoneStatus.IN_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(t + 100, ZoneStatus.ABOVE_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(t + 1000, ZoneStatus.NO_DATA))
        assertFalse(c.isActive)
    }

    @Test
    fun `cycle is not zone-gated once started - OUT_OF_ZONE mid-set still progresses`() {
        val c = StridesController(durationMin = 24)
        c.evaluateTick(1200, ZoneStatus.IN_ZONE)  // start rep 1
        // mid-work, OUT_OF_ZONE
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(1205, ZoneStatus.ABOVE_ZONE))
        // at +20s, RepEnd still fires regardless of zone
        val events = c.evaluateTick(1220, ZoneStatus.ABOVE_ZONE)
        assertEquals(listOf(StridesEvent.RepEnd), events)
    }
}
