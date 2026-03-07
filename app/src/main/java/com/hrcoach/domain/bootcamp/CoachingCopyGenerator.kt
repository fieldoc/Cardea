package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.engine.TierPromptDirection

object CoachingCopyGenerator {

    fun tierPromptCopy(
        direction: TierPromptDirection,
        aboveOrBelowWeeks: Int,
        ctlTrend: Float,
        tsb: Float
    ): String = when (direction) {
        TierPromptDirection.UP   -> upCopy(aboveOrBelowWeeks, ctlTrend, tsb)
        TierPromptDirection.DOWN -> downCopy(aboveOrBelowWeeks, tsb)
        TierPromptDirection.NONE -> ""
    }

    private fun upCopy(weeks: Int, ctlTrend: Float, tsb: Float): String {
        val weekPhrase = if (weeks == 1) "the past week" else "the past $weeks weeks"
        val trendPhrase = if (ctlTrend > 0.5f) "and it's still growing" else "and it's holding steady"
        val recoveryPhrase = if (tsb > 10f) "Your body is well-recovered — " else "You have good recovery headroom — "
        return "${recoveryPhrase}your heart rate has been staying remarkably calm during efforts over $weekPhrase, $trendPhrase. You're ready for a bigger challenge."
    }

    private fun downCopy(weeks: Int, tsb: Float): String {
        val weekPhrase = if (weeks == 1) "this past week" else "the past $weeks weeks"
        return if (tsb < -20f) {
            "Your body is working hard. Effort has been building for $weekPhrase and recovery is lagging — the right call now is to dial back before digging a deeper hole."
        } else {
            "Your recent sessions have been tougher than usual for $weekPhrase. A lighter phase now sets you up for a stronger next block."
        }
    }
}
