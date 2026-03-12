package com.hrcoach.domain.bootcamp

/**
 * Computes per-session-type durations from weekly training context.
 *
 * Based on Jack Daniels' Running Formula rules:
 * - Long run: 25% of weekly total, clamped [easyMinutes, 150]
 * - Tempo: 10% of weekly total, clamped [15, 40]
 * - Interval: 8% of weekly total, clamped [12, 35]
 * - Easy runs absorb the remainder to keep weekly total constant.
 */
object DurationScaler {

    data class WeekDurations(
        val easyMinutes: Int,
        val longMinutes: Int,
        val tempoMinutes: Int,
        val intervalMinutes: Int
    )

    /**
     * @param runsPerWeek number of runs in the week (2-7)
     * @param easyMinutes the user's chosen "easy run" duration - the anchor
     */
    fun compute(runsPerWeek: Int, easyMinutes: Int): WeekDurations {
        val weeklyTotal = runsPerWeek * easyMinutes

        // Long run: 25% of weekly total, at least as long as easy, max 150
        val hasLong = runsPerWeek >= 3
        val rawLong = (weeklyTotal * 0.25f).toInt()
        val longMinutes = if (hasLong) rawLong.coerceIn(easyMinutes, 150) else easyMinutes

        // Easy runs absorb the difference to keep weekly total ~constant
        val adjustedEasy = if (hasLong && runsPerWeek > 1) {
            (weeklyTotal - longMinutes) / (runsPerWeek - 1)
        } else {
            easyMinutes
        }

        // Quality session caps (Daniels)
        val tempoMinutes = (weeklyTotal * 0.10f).toInt().coerceIn(15, 40)
        val intervalMinutes = (weeklyTotal * 0.08f).toInt().coerceIn(12, 35)

        return WeekDurations(
            easyMinutes = adjustedEasy,
            longMinutes = longMinutes,
            tempoMinutes = tempoMinutes,
            intervalMinutes = intervalMinutes
        )
    }
}
