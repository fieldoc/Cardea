package com.hrcoach.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun formatDuration_formatsMinutesAndSecondsUnderOneHour() {
        assertEquals("45:30", formatDuration(startTime = 0L, endTime = 2_730_000L))
    }

    @Test
    fun formatDuration_formatsHoursMinutesAndSecondsAtOneHourOrMore() {
        assertEquals("1:30:00", formatDuration(startTime = 0L, endTime = 5_400_000L))
    }
}
