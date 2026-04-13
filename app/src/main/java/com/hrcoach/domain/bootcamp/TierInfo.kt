package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.model.BootcampGoal

/**
 * Centralised tier metadata — names, descriptions, content summaries.
 *
 * Tier indices: 0 = Foundation, 1 = Development, 2 = Performance.
 * Every UI surface that displays tier information should read from here
 * so copy stays consistent across setup, dashboard, settings, and prompts.
 */
object TierInfo {

    /** Short display name shown in pills, badges, and headers. */
    fun displayName(tierIndex: Int): String = when (tierIndex) {
        0 -> "Foundation"
        1 -> "Development"
        2 -> "Performance"
        else -> "Development"
    }

    /** One-line description for setup / settings context. */
    fun tagline(tierIndex: Int): String = when (tierIndex) {
        0 -> "Build your aerobic base with easy-effort running"
        1 -> "Add tempo and strides to sharpen your fitness"
        2 -> "Full periodisation with intervals, tempo, and race prep"
        else -> tagline(1)
    }

    /** Who this tier is for — shown during setup placement. */
    fun audience(tierIndex: Int): String = when (tierIndex) {
        0 -> "New to running, returning from a break, or building a base"
        1 -> "Can run 20\u201330 min comfortably and ready for structured work"
        2 -> "Running consistently and seeking race-specific intensity"
        else -> audience(1)
    }

    /** Bullet-point summary of what a typical week includes at this tier. */
    fun weekContent(tierIndex: Int): List<String> = when (tierIndex) {
        0 -> listOf(
            "All easy-effort runs",
            "Optional long run at Zone 2",
            "No quality sessions — pure aerobic development"
        )
        1 -> listOf(
            "Easy runs for aerobic base",
            "Tempo run (threshold effort)",
            "Strides for neuromuscular speed",
            "Long run at Zone 2"
        )
        2 -> listOf(
            "Easy runs for recovery between efforts",
            "Intervals (VO\u2082max / hill repeats)",
            "Tempo run (lactate threshold)",
            "Long run with race-simulation segments"
        )
        else -> weekContent(1)
    }

    /**
     * Compact label for the tier change prompt —
     * e.g. "Foundation → Development" or "Performance → Development".
     */
    fun transitionLabel(fromTier: Int, toTier: Int): String =
        "${displayName(fromTier)} → ${displayName(toTier)}"

    /**
     * What changes when moving to [toTier] from [fromTier].
     * Used in the tier prompt card to explain impact.
     */
    fun transitionSummary(fromTier: Int, toTier: Int): String {
        if (toTier > fromTier) {
            // Promotion
            return when (toTier) {
                1 -> "Your weeks will add tempo and strides sessions alongside your easy runs."
                2 -> "Your weeks will include intervals and race-specific efforts for peak fitness."
                else -> "Your training intensity will increase."
            }
        } else {
            // Demotion
            return when (toTier) {
                0 -> "Your weeks will shift to all easy-effort running to rebuild your base."
                1 -> "Intervals will be replaced with tempo work at a more sustainable intensity."
                else -> "Your training intensity will decrease."
            }
        }
    }

    /** CTL range context — human-readable description of where the runner sits. */
    fun ctlPositionLabel(
        goal: BootcampGoal,
        tierIndex: Int,
        ctl: Float
    ): String? {
        val range = TierCtlRanges.rangeFor(goal, tierIndex)
        val span = (range.last - range.first).toFloat()
        if (span <= 0f) return null
        val pct = ((ctl - range.first) / span).coerceIn(0f, 1f)
        return when {
            pct < 0.15f -> "Near the lower end of ${displayName(tierIndex)}"
            pct > 0.85f -> "Approaching ${displayName((tierIndex + 1).coerceAtMost(2))} territory"
            else -> null // Comfortably in range — no label needed
        }
    }

    /** Fractional position within the current tier's CTL range (0..1). */
    fun ctlProgress(goal: BootcampGoal, tierIndex: Int, ctl: Float): Float {
        val range = TierCtlRanges.rangeFor(goal, tierIndex)
        val span = (range.last - range.first).toFloat()
        return if (span > 0f) ((ctl - range.first) / span).coerceIn(0f, 1f) else 0.5f
    }
}
