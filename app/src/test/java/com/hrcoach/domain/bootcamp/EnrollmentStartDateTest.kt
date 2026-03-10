package com.hrcoach.domain.bootcamp

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EnrollmentStartDateTest {

    @Test fun picks_next_preferred_day_in_same_week() {
        // preferred Mon(1), Wed(3), Sat(6). Today is Mon(1). Next preferred = Wed(3).
        val result = firstPreferredDayAfter(listOf(1, 3, 6), LocalDate.of(2026, 3, 9))
        assertEquals(LocalDate.of(2026, 3, 11), result) // Wednesday
    }

    @Test fun wraps_to_next_week_when_no_days_remain() {
        // preferred Mon(1) only. Today is Wednesday(3). Wraps to next Monday.
        val result = firstPreferredDayAfter(listOf(1), LocalDate.of(2026, 3, 11))
        assertEquals(LocalDate.of(2026, 3, 16), result)
    }

    @Test fun never_returns_today() {
        // preferred Mon(1), Wed(3). Today is Monday(1). Returns Wed(3), not today.
        val result = firstPreferredDayAfter(listOf(1, 3), LocalDate.of(2026, 3, 9))
        assertEquals(LocalDate.of(2026, 3, 11), result)
    }
}
