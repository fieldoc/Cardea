package com.hrcoach.domain.engine

object HrArtifactDetector {

    fun isArtifactSuspected(hrSamples: List<Int>): Boolean {
        return isCadenceLockSuspected(hrSamples = hrSamples)
    }

    fun isCadenceLockSuspected(
        hrSamples: List<Int>,
        jumpThreshold: Int = 25,
        flatWindowSec: Int = 8,
        flatToleranceBpm: Int = 3
    ): Boolean {
        if (hrSamples.size < flatWindowSec + 1) return false
        if (jumpThreshold <= 0 || flatWindowSec <= 0 || flatToleranceBpm < 0) return false

        for (index in 1..(hrSamples.size - flatWindowSec)) {
            val jump = hrSamples[index] - hrSamples[index - 1]
            if (jump < jumpThreshold) continue

            val window = hrSamples.subList(index, index + flatWindowSec)
            val min = window.minOrNull() ?: continue
            val max = window.maxOrNull() ?: continue
            if (max - min <= flatToleranceBpm) return true
        }

        return false
    }
}
