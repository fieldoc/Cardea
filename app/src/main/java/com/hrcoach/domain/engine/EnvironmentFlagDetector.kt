package com.hrcoach.domain.engine

object EnvironmentFlagDetector {

    private const val DECOUPLING_THRESHOLD = 10f
    private const val PACE_DELTA_THRESHOLD = 0.25f

    fun isEnvironmentAffected(
        aerobicDecoupling: Float?,
        sessionAvgGapPace: Float?,
        baselineGapPaceAtEquivalentHr: Float?
    ): Boolean {
        if (
            aerobicDecoupling == null ||
            sessionAvgGapPace == null ||
            baselineGapPaceAtEquivalentHr == null
        ) {
            return false
        }

        val decouplingHigh = aerobicDecoupling > DECOUPLING_THRESHOLD
        val paceSlower = (sessionAvgGapPace - baselineGapPaceAtEquivalentHr) > PACE_DELTA_THRESHOLD
        return decouplingHigh && paceSlower
    }
}