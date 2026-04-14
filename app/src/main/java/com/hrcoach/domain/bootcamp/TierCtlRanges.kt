package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.engine.TierPromptDirection
import com.hrcoach.domain.model.BootcampGoal

/**
 * CTL-based tier ranges for bootcamp auto-promotion prompts.
 *
 * Tier indices map to the [TierInfo] named tiers: 0 = Foundation, 1 = Development,
 * 2 = Performance. The design spec in `docs/plans/2026-03-04-adaptive-bootcamp-design.md`
 * uses 1-indexed labels (T1/T2/T3) — same concept, different naming convention.
 *
 * ## Divergences from the design spec
 *
 * The spec lists these CTL ranges:
 *
 * | Goal          | T1 (Foundation) | T2 (Development) | T3 (Performance) |
 * |---------------|-----------------|------------------|------------------|
 * | Cardio Health | 10–30           | 30–55            | 55–90            |
 * | 5K/10K        | 15–35           | 35–65            | 65–110           |
 * | Half          | 20–45           | 45–75            | 75–120           |
 * | Marathon      | 25–55           | 55–90            | 90–140           |
 *
 * The code extends the spec at both ends:
 *
 *  - **Foundation floor = 0** (not 10/15/20/25). A brand-new runner with CTL < 10
 *    still needs a tier. The spec is silent on below-floor behavior; this code
 *    puts sub-floor runners in Foundation so they have a valid program to run.
 *    No auto-promotion prompt fires until they cross into Development.
 *
 *  - **Performance ceiling = 200** (effectively infinite). The spec's upper bound
 *    (90/110/120/140) exists only to describe "expected range"; the code treats
 *    Performance as the terminal tier so no prompt fires above it. A runner at
 *    CTL 150 in Cardio goal is simply "above the usual Performance range" — this
 *    isn't a bug, there's no higher tier to promote them to.
 *
 * Both divergences have been reviewed and accepted. See the Science Constants
 * Register (`docs/plans/2026-04-14-science-constants-register.md`) for the
 * decision trail.
 */
object TierCtlRanges {
    const val MIN_SESSIONS_FOR_PROMPT = 9
    const val MIN_CONSECUTIVE_WEEKS_FOR_PROMPT = 3

    // See class KDoc for the relationship to the design spec's T1–T3 ranges.
    // Foundation floor is 0 (not spec-10) to handle new users with low CTL.
    // Performance ceiling is 200 (not spec-90) because it's the terminal tier.
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
