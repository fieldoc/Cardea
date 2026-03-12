package com.hrcoach.domain.bootcamp

import org.junit.Assert.*
import org.junit.Test

class DurationScalerTest {

    @Test fun beginner_3x30_long_is_1_5x_easy() {
        // 3 runs/week -> 1.5x multiplier: 30 * 1.5 = 45
        val d = DurationScaler.compute(runsPerWeek = 3, easyMinutes = 30)
        assertEquals(45, d.longMinutes)
    }

    @Test fun intermediate_4x45_long_is_1_35x_easy() {
        // 4 runs/week -> 1.35x multiplier: 45 * 1.35 = 60
        val d = DurationScaler.compute(runsPerWeek = 4, easyMinutes = 45)
        assertEquals(60, d.longMinutes)
    }

    @Test fun experienced_5x60_long_is_1_25x_easy() {
        // 5 runs/week -> 1.25x multiplier: 60 * 1.25 = 75
        val d = DurationScaler.compute(runsPerWeek = 5, easyMinutes = 60)
        assertEquals(75, d.longMinutes)
    }

    @Test fun high_volume_long_capped_at_150() {
        // 5 runs/week at 90 min -> 1.25x = 112, under cap
        val d5 = DurationScaler.compute(runsPerWeek = 5, easyMinutes = 90)
        assertEquals(112, d5.longMinutes)
        // 3 runs/week at 90 min -> 1.5x = 135, under cap
        val d3 = DurationScaler.compute(runsPerWeek = 3, easyMinutes = 90)
        assertEquals(135, d3.longMinutes)
    }

    @Test fun two_runs_per_week_no_distinct_long() {
        val d = DurationScaler.compute(runsPerWeek = 2, easyMinutes = 30)
        assertEquals(30, d.longMinutes) // same as easy
    }

    @Test fun easy_minutes_redistributed_when_long_exceeds_easy() {
        // 5x60: weekly=300, long=75, remaining=225, 4 easy runs = 56 each
        val d = DurationScaler.compute(runsPerWeek = 5, easyMinutes = 60)
        assertEquals(56, d.easyMinutes)
    }

    @Test fun easy_redistributed_3x30() {
        // 3x30: weekly=90, long=45, remaining=45, 2 easy runs = 22 each
        val d = DurationScaler.compute(runsPerWeek = 3, easyMinutes = 30)
        assertEquals(22, d.easyMinutes)
        // Weekly total preserved: 22*2 + 45 = 89 (rounding)
    }

    @Test fun tempo_is_10pct_clamped() {
        // 5x60: weekly=300, 10%=30
        val d = DurationScaler.compute(runsPerWeek = 5, easyMinutes = 60)
        assertEquals(30, d.tempoMinutes)
    }

    @Test fun tempo_floor_at_15() {
        // 3x30: weekly=90, 10%=9 -> clamped to 15
        val d = DurationScaler.compute(runsPerWeek = 3, easyMinutes = 30)
        assertEquals(15, d.tempoMinutes)
    }

    @Test fun interval_is_8pct_clamped() {
        // 5x60: weekly=300, 8%=24
        val d = DurationScaler.compute(runsPerWeek = 5, easyMinutes = 60)
        assertEquals(24, d.intervalMinutes)
    }

    @Test fun interval_floor_at_12() {
        // 3x30: weekly=90, 8%=7 -> clamped to 12
        val d = DurationScaler.compute(runsPerWeek = 3, easyMinutes = 30)
        assertEquals(12, d.intervalMinutes)
    }
}
