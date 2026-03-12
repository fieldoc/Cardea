package com.hrcoach.domain.bootcamp

import org.junit.Assert.*
import org.junit.Test

class DurationScalerTest {

    @Test fun beginner_3x30_long_is_25pct_of_weekly() {
        // weekly = 90, 25% = 22.5 -> 22, but coerced to min easyMinutes (30)
        val d = DurationScaler.compute(runsPerWeek = 3, easyMinutes = 30)
        assertEquals(30, d.longMinutes) // can't be less than easy
    }

    @Test fun intermediate_4x45_long_scales_properly() {
        // weekly = 180, 25% = 45
        val d = DurationScaler.compute(runsPerWeek = 4, easyMinutes = 45)
        assertEquals(45, d.longMinutes)
    }

    @Test fun experienced_5x60_long_is_75min() {
        // weekly = 300, 25% = 75
        val d = DurationScaler.compute(runsPerWeek = 5, easyMinutes = 60)
        assertEquals(75, d.longMinutes)
    }

    @Test fun high_volume_long_capped_at_150() {
        // weekly = 7*90 = 630, 25% = 157 -> capped at 150
        val d = DurationScaler.compute(runsPerWeek = 7, easyMinutes = 90)
        assertEquals(150, d.longMinutes)
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
