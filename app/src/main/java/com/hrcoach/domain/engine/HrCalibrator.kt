package com.hrcoach.domain.engine

object HrCalibrator {

    fun detectNewHrMax(
        currentHrMax: Int,
        recentSamples: List<Int>,
        windowSec: Int = 8,
        cadenceLockSuspected: Boolean = false,
        plausibilityCeiling: Int = 220
    ): Int? {
        if (cadenceLockSuspected) return null
        val upperBound = plausibilityCeiling.coerceIn(1, 220)
        if (currentHrMax >= upperBound) return null

        var consecutiveCount = 0
        var peakObserved = 0

        for (sample in recentSamples) {
            if (sample in (currentHrMax + 1)..upperBound) {
                consecutiveCount++
                if (sample > peakObserved) peakObserved = sample
                if (consecutiveCount >= windowSec) return peakObserved
            } else {
                consecutiveCount = 0
                peakObserved = 0
            }
        }

        return null
    }

    fun updateHrRest(currentHrRest: Float, candidate: Float): Float {
        return if (currentHrRest - candidate >= 2f) candidate else currentHrRest
    }
}
