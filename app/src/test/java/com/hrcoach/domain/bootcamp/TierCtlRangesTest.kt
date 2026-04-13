package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.BootcampGoal
import org.junit.Assert.*
import org.junit.Test

class TierCtlRangesTest {

    // ── suggestedTierForCtl ──────────────────────────

    @Test
    fun `suggestedTier returns T0 when CTL well below T1 floor`() {
        assertEquals(0, TierCtlRanges.suggestedTierForCtl(BootcampGoal.RACE_5K, 10f))
    }

    @Test
    fun `suggestedTier returns T1 when CTL in T1 range`() {
        assertEquals(1, TierCtlRanges.suggestedTierForCtl(BootcampGoal.RACE_5K, 50f))
    }

    @Test
    fun `suggestedTier returns T2 when CTL above T2 floor`() {
        assertEquals(2, TierCtlRanges.suggestedTierForCtl(BootcampGoal.RACE_5K, 80f))
    }

    @Test
    fun `suggestedTier at exact boundary returns higher tier - 5K CTL 35`() {
        // CTL=35 is T0 upper AND T1 lower — should return T1 (keep higher tier)
        assertEquals(1, TierCtlRanges.suggestedTierForCtl(BootcampGoal.RACE_5K, 35f))
    }

    @Test
    fun `suggestedTier at exact T2 boundary returns T2 - 5K CTL 65`() {
        assertEquals(2, TierCtlRanges.suggestedTierForCtl(BootcampGoal.RACE_5K, 65f))
    }

    @Test
    fun `suggestedTier returns T0 when CTL is zero`() {
        assertEquals(0, TierCtlRanges.suggestedTierForCtl(BootcampGoal.MARATHON, 0f))
    }

    @Test
    fun `suggestedTier for marathon respects higher thresholds`() {
        // Marathon T0=0..55, T1=55..90, T2=90+
        assertEquals(0, TierCtlRanges.suggestedTierForCtl(BootcampGoal.MARATHON, 40f))
        assertEquals(1, TierCtlRanges.suggestedTierForCtl(BootcampGoal.MARATHON, 70f))
        assertEquals(2, TierCtlRanges.suggestedTierForCtl(BootcampGoal.MARATHON, 95f))
    }

    @Test
    fun `suggestedTier for cardio health uses lower thresholds`() {
        // Cardio T0=0..30, T1=30..55, T2=55+
        assertEquals(0, TierCtlRanges.suggestedTierForCtl(BootcampGoal.CARDIO_HEALTH, 20f))
        assertEquals(1, TierCtlRanges.suggestedTierForCtl(BootcampGoal.CARDIO_HEALTH, 40f))
        assertEquals(2, TierCtlRanges.suggestedTierForCtl(BootcampGoal.CARDIO_HEALTH, 60f))
    }

    @Test
    fun `suggestedTier just below T1 boundary returns T0`() {
        // CTL=34.9 is below T1 lower bound (35) for RACE_5K
        assertEquals(0, TierCtlRanges.suggestedTierForCtl(BootcampGoal.RACE_5K, 34.9f))
    }
}
