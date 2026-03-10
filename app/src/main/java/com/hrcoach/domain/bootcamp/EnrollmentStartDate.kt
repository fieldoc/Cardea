package com.hrcoach.domain.bootcamp

import java.time.LocalDate
import java.time.ZoneId

/**
 * Returns the first day in [preferredDayNumbers] (1=Mon … 7=Sun) that is
 * strictly after [today]. Wraps to the following week if needed.
 */
fun firstPreferredDayAfter(preferredDayNumbers: List<Int>, today: LocalDate): LocalDate {
    val sorted = preferredDayNumbers.sorted()
    val todayDow = today.dayOfWeek.value          // 1=Mon, 7=Sun
    val thisWeek = sorted.firstOrNull { it > todayDow }
    if (thisWeek != null) {
        return today.plusDays((thisWeek - todayDow).toLong())
    }
    // Wrap: first preferred day next week
    val nextWeekFirst = sorted.first()
    return today.plusDays((7 - todayDow + nextWeekFirst).toLong())
}

/** Returns the epoch-millis of midnight (local) for the first upcoming preferred day. */
fun firstPreferredDayAfterMs(preferredDayNumbers: List<Int>): Long {
    val today = LocalDate.now(ZoneId.systemDefault())
    return firstPreferredDayAfter(preferredDayNumbers, today)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
