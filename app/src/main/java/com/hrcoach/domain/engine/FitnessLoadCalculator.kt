package com.hrcoach.domain.engine

import kotlin.math.exp

object FitnessLoadCalculator {

    data class LoadResult(
        val ctl: Float,
        val atl: Float,
        val tsb: Float
    )

    private const val CTL_TAU = 42f
    private const val ATL_TAU = 7f

    fun updateLoads(
        currentCtl: Float,
        currentAtl: Float,
        trimpScore: Float,
        daysSinceLast: Int
    ): LoadResult {
        val days = daysSinceLast.coerceAtLeast(0).toFloat().coerceAtLeast(0.5f)
        val ctlDecay = exp(-days / CTL_TAU).toFloat()
        val atlDecay = exp(-days / ATL_TAU).toFloat()

        val newCtl = currentCtl * ctlDecay + trimpScore * (1f - ctlDecay)
        val newAtl = currentAtl * atlDecay + trimpScore * (1f - atlDecay)

        return LoadResult(
            ctl = newCtl,
            atl = newAtl,
            tsb = newCtl - newAtl
        )
    }
}
