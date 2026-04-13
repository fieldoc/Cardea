package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.engine.TierPromptDirection
import com.hrcoach.domain.model.BootcampGoal

object TierCtlRanges {
    const val MIN_SESSIONS_FOR_PROMPT = 9
    const val MIN_CONSECUTIVE_WEEKS_FOR_PROMPT = 3

    private val cardioRanges = listOf(0..30, 30..55, 55..200)
    private val raceRanges = listOf(0..35, 35..65, 65..200)
    private val halfRanges = listOf(0..45, 45..75, 75..200)
    private val marathonRanges = listOf(0..55, 55..90, 90..200)

    val minTierIndex: Int = 0
    val maxTierIndex: Int = 2

    fun rangeFor(goal: BootcampGoal, tierIndex: Int): IntRange {
        val ranges = when (goal) {
            BootcampGoal.CARDIO_HEALTH -> cardioRanges
            BootcampGoal.RACE_5K -> raceRanges
            BootcampGoal.RACE_10K -> raceRanges
            BootcampGoal.HALF_MARATHON -> halfRanges
            BootcampGoal.MARATHON -> marathonRanges
        }
        return ranges[tierIndex.coerceIn(minTierIndex, maxTierIndex)]
    }

    fun directionFor(goal: BootcampGoal, tierIndex: Int, ctl: Float): TierPromptDirection {
        val safeTier = tierIndex.coerceIn(minTierIndex, maxTierIndex)
        val current = rangeFor(goal, safeTier)
        return when {
            ctl > current.last && safeTier < maxTierIndex -> TierPromptDirection.UP
            ctl < current.first && safeTier > minTierIndex -> TierPromptDirection.DOWN
            else -> TierPromptDirection.NONE
        }
    }

    fun isCtlInRange(goal: BootcampGoal, tierIndex: Int, ctl: Float): Boolean {
        val range = rangeFor(goal, tierIndex)
        return ctl >= range.first && ctl <= range.last
    }

    /**
     * Returns the highest tier whose lower bound is <= [ctl].
     * At exact boundaries (e.g., CTL=35 for RACE_5K where T0=0..35, T1=35..65),
     * returns the HIGHER tier — runners at the boundary keep their tier.
     */
    fun suggestedTierForCtl(goal: BootcampGoal, ctl: Float): Int {
        val ranges = when (goal) {
            BootcampGoal.CARDIO_HEALTH -> cardioRanges
            BootcampGoal.RACE_5K -> raceRanges
            BootcampGoal.RACE_10K -> raceRanges
            BootcampGoal.HALF_MARATHON -> halfRanges
            BootcampGoal.MARATHON -> marathonRanges
        }
        // Walk from highest tier down; return the first whose lower bound <= ctl
        for (tier in ranges.indices.reversed()) {
            if (ctl >= ranges[tier].first) return tier
        }
        return minTierIndex
    }

    fun snoozeWeeksForDismissCount(dismissCount: Int): Int {
        return when (dismissCount) {
            1 -> 2
            2 -> 3
            else -> 4
        }
    }
}
