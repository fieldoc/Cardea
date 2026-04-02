package com.hrcoach.domain.preset

import org.junit.Assert.assertEquals
import org.junit.Test

class KarvonenTest {

    @Test
    fun `zone2 base at 68 pct with rest 60 max 191`() {
        // restHr + (maxHr - restHr) * pct = 60 + 131 * 0.68 = 60 + 89.08 = 149
        val target = karvonen(maxHr = 191, restHr = 60, pct = 0.68f)
        assertEquals(149, target)
    }

    @Test
    fun `lactate threshold at 90 pct with rest 60 max 191`() {
        // 60 + 131 * 0.90 = 60 + 117.9 = 178
        val target = karvonen(maxHr = 191, restHr = 60, pct = 0.90f)
        assertEquals(178, target)
    }

    @Test
    fun `rest equals zero degenerates to simple pct of max`() {
        val target = karvonen(maxHr = 200, restHr = 0, pct = 0.68f)
        assertEquals(136, target)
    }

    @Test
    fun `rest equals max returns max regardless of pct`() {
        val target = karvonen(maxHr = 180, restHr = 180, pct = 0.68f)
        assertEquals(180, target)
    }

    @Test
    fun `high rest low max still produces valid result`() {
        // Edge: rest=75, max=160, pct=0.68 -> 75 + 85*0.68 = 75 + 57.8 = 133
        val target = karvonen(maxHr = 160, restHr = 75, pct = 0.68f)
        assertEquals(133, target)
    }
}
