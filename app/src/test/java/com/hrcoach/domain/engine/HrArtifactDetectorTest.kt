package com.hrcoach.domain.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HrArtifactDetectorTest {

    @Test
    fun `detects cadence lock pattern`() {
        val samples = buildList {
            repeat(5) { add(135) }
            add(165)
            repeat(10) { add(163 + (it % 2)) }
        }

        assertTrue(
            HrArtifactDetector.isCadenceLockSuspected(
                hrSamples = samples,
                jumpThreshold = 25,
                flatWindowSec = 8,
                flatToleranceBpm = 3
            )
        )
    }

    @Test
    fun `does not flag gradual HR increase`() {
        val samples = (130..160).toList() + List(10) { 160 }

        assertFalse(
            HrArtifactDetector.isCadenceLockSuspected(
                hrSamples = samples,
                jumpThreshold = 25,
                flatWindowSec = 8,
                flatToleranceBpm = 3
            )
        )
    }

    @Test
    fun `does not flag brief spike that recovers`() {
        val samples = List(10) { 135 } + listOf(165) + List(10) { 137 }

        assertFalse(
            HrArtifactDetector.isCadenceLockSuspected(
                hrSamples = samples,
                jumpThreshold = 25,
                flatWindowSec = 8,
                flatToleranceBpm = 3
            )
        )
    }
}
