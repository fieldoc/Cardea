package com.hrcoach.domain.education

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FactSelectorTest {

    @Test
    fun `same seed and day return same index repeatedly`() {
        val a = FactSelector.selectIndex(10, "Z2_FULL", 19834L)
        val b = FactSelector.selectIndex(10, "Z2_FULL", 19834L)
        assertEquals(a, b)
    }

    @Test
    fun `30-day window with fixed seed hits at least 7 distinct indices`() {
        val seen = (0 until 30)
            .map { FactSelector.selectIndex(10, "Z2_FULL", 19000L + it) }
            .toSet()
        assertTrue("expected >=7 distinct, got ${seen.size}", seen.size >= 7)
    }

    @Test
    fun `different seeds on same day mostly return different indices`() {
        val day = 19834L
        val seeds = listOf("Z2_FULL", "Z3_FULL", "ZONE_4_5_FULL", "RECOVERY_FULL", "RACE_PACE_FULL")
        val indices = seeds.map { FactSelector.selectIndex(10, it, day) }
        val distinct = indices.toSet().size
        assertTrue("expected >=4 distinct of 5, got $distinct", distinct >= 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `poolSize zero throws`() {
        FactSelector.selectIndex(0, "anything", 1L)
    }

    @Test
    fun `extreme inputs do not throw and return valid index`() {
        val i = FactSelector.selectIndex(7, "", Long.MIN_VALUE)
        assertTrue(i in 0 until 7)
        val j = FactSelector.selectIndex(7, "x".repeat(1000), Long.MAX_VALUE)
        assertTrue(j in 0 until 7)
    }

    @Test
    fun `consecutive days with same seed do not always collide`() {
        val a = FactSelector.selectIndex(10, "Z2_FULL", 19834L)
        val b = FactSelector.selectIndex(10, "Z2_FULL", 19835L)
        assertNotEquals("consecutive-day collision is suspicious", a, b)
    }
}
