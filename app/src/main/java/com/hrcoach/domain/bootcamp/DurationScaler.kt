package com.hrcoach.domain.bootcamp

/**
 * Computes per-session-type durations from weekly training context.
 *
 * Long run uses a graduated multiplier: fewer runs/week -> bigger spike,
 * since there is less volume spread across the week. Capped at 150 min
 * (Daniels' absolute ceiling for L runs).
 *
 * Quality sessions (tempo, interval) use Daniels' Running Formula ratios:
 * - Tempo: 10% of weekly total, clamped [15, 40]
 * - Interval: 8% of weekly total, clamped [12, 35]
 *
 * Easy runs absorb the remainder to keep weekly total constant.
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
        val hasLong = runsPerWeek >= 3

        // Graduated multiplier: fewer runs -> bigger long-run spike.
        // At 5+ runs, 1.25x aligns with Daniels' 25%-of-weekly rule naturally.
        val longMinutes = if (hasLong) {
            val multiplier = when {
                runsPerWeek <= 3 -> 1.5f
                runsPerWeek == 4 -> 1.35f
                else -> 1.25f
            }
            (easyMinutes * multiplier).toInt().coerceAtMost(150)
        } else {
            easyMinutes
        }

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
