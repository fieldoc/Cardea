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
    fun `triggerAtSec leaves room for stridesBlock plus 30s cooldown inside session`() {
        // 20 min / 4 reps -> 1200 - (4*80) - 30 = 850s (14:10)
        assertEquals(850L, StridesController.triggerAtSec(20))
        // 22 min / 6 reps -> 1320 - 480 - 30 = 810s (13:30)
        assertEquals(810L, StridesController.triggerAtSec(22))
        // 24 min / 8 reps -> 1440 - 640 - 30 = 770s (12:50)
        assertEquals(770L, StridesController.triggerAtSec(24))
        // 26 min / 10 reps -> 1560 - 800 - 30 = 730s (12:10)
        assertEquals(730L, StridesController.triggerAtSec(26))
        // 30 min fallback / 5 reps -> 1800 - 400 - 30 = 1370s
        assertEquals(1370L, StridesController.triggerAtSec(30))
    }

    @Test
    fun `triggerAtSec coerces to zero for degenerate sessions`() {
        // Degenerate: 1 min session can't fit 5 reps (5*80 = 400s).
        // Without coercion this would be -370s; with it, 0.
        assertEquals(0L, StridesController.triggerAtSec(1))
        assertEquals(0L, StridesController.triggerAtSec(0))
    }

    @Test
    fun `idle stays idle when elapsedSec is below trigger`() {
        val c = StridesController(durationMin = 24)  // trigger = 770
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(0, ZoneStatus.IN_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(600, ZoneStatus.IN_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(769, ZoneStatus.IN_ZONE))
        assertFalse(c.isActive)
    }

    @Test
    fun `idle stays idle at trigger when not in zone`() {
        val c = StridesController(durationMin = 24)  // trigger = 770
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(770, ZoneStatus.ABOVE_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(770, ZoneStatus.BELOW_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(770, ZoneStatus.NO_DATA))
        assertFalse(c.isActive)
    }

    @Test
    fun `first in-zone tick at trigger emits Announce and RepStart`() {
        val c = StridesController(durationMin = 24)  // trigger = 770, totalReps = 8
        val events = c.evaluateTick(770, ZoneStatus.IN_ZONE)
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
        val c = StridesController(durationMin = 24)  // trigger = 770
        c.evaluateTick(770, ZoneStatus.IN_ZONE)  // start rep 1
        val events = c.evaluateTick(790, ZoneStatus.IN_ZONE)
        assertEquals(listOf(StridesEvent.RepEnd), events)
    }

    @Test
    fun `Work to Rest before +20s emits empty list`() {
        val c = StridesController(durationMin = 24)  // trigger = 770
        c.evaluateTick(770, ZoneStatus.IN_ZONE)
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(780, ZoneStatus.IN_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(789, ZoneStatus.IN_ZONE))
    }

    @Test
    fun `Rest to next Work at +80s emits RepStart for next rep`() {
        val c = StridesController(durationMin = 24)  // trigger = 770, totalReps = 8
        c.evaluateTick(770, ZoneStatus.IN_ZONE)  // start rep 1
        c.evaluateTick(790, ZoneStatus.IN_ZONE)  // RepEnd, enter Rest
        val events = c.evaluateTick(850, ZoneStatus.IN_ZONE)  // +80s in cycle
        assertEquals(listOf(StridesEvent.RepStart(2, 8)), events)
        assertTrue(c.isActive)
    }

    @Test
    fun `final rep transition emits SetComplete not RepStart`() {
        val c = StridesController(durationMin = 20)  // trigger = 850, totalReps = 4
        var t = 850L
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
        // SetComplete fires at t = 850 + 4*80 = 1170s = 19:30 — leaves the
        // 30s cooldown buffer before the 20-min session ends. This was the
        // whole point of the trigger refactor.
        assertEquals(1170L, t)
    }

    @Test
    fun `after Done additional ticks emit empty list`() {
        val c = StridesController(durationMin = 20)  // trigger = 850, totalReps = 4
        var t = 850L
        repeat(4) {
            c.evaluateTick(t, ZoneStatus.IN_ZONE)        // RepStart
            c.evaluateTick(t + 20, ZoneStatus.IN_ZONE)   // RepEnd
            t += 80
        }
        val complete = c.evaluateTick(t, ZoneStatus.IN_ZONE)
        assertEquals(listOf(StridesEvent.SetComplete), complete)
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(t + 1, ZoneStatus.IN_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(t + 100, ZoneStatus.ABOVE_ZONE))
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(t + 1000, ZoneStatus.NO_DATA))
        assertFalse(c.isActive)
    }

    @Test
    fun `cycle is not zone-gated once started - OUT_OF_ZONE mid-set still progresses`() {
        val c = StridesController(durationMin = 24)  // trigger = 770
        c.evaluateTick(770, ZoneStatus.IN_ZONE)  // start rep 1
        // mid-work, OUT_OF_ZONE
        assertEquals(emptyList<StridesEvent>(), c.evaluateTick(775, ZoneStatus.ABOVE_ZONE))
        // at +20s, RepEnd still fires regardless of zone
        val events = c.evaluateTick(790, ZoneStatus.ABOVE_ZONE)
        assertEquals(listOf(StridesEvent.RepEnd), events)
    }
}
