package com.hrcoach.domain.engine

import kotlin.math.exp

object FitnessLoadCalculator {

    data class LoadResult(
        val ctl: Float,
        val atl: Float,
        val tsb: Float
    )

    private const val CTL_TAU = 42.0
    private const val ATL_TAU = 7.0

    fun updateLoads(
        currentCtl: Float,
        currentAtl: Float,
        trimpScore: Float,
        daysSinceLast: Int
    ): LoadResult {
        val days = daysSinceLast.coerceAtLeast(0).toDouble().coerceAtLeast(0.5)
        val ctlDecay = exp(-days / CTL_TAU)
        val atlDecay = exp(-days / ATL_TAU)

        val newCtl = (currentCtl * ctlDecay + trimpScore * (1.0 - ctlDecay)).toFloat()
        val newAtl = (currentAtl * atlDecay + trimpScore * (1.0 - atlDecay)).toFloat()

        return LoadResult(
            ctl = newCtl,
            atl = newAtl,
            tsb = newCtl - newAtl
        )
    }
}
