package com.hrcoach.ui.bootcamp

import com.hrcoach.data.db.BootcampSessionEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Discriminant for what kind of day today is from a training perspective. */
internal enum class DayKind { RUN_UPCOMING, RUN_DONE, REST }

/**
 * Determines whether today is a run day (upcoming/done) or a rest day.
 *
 * A session is considered "done" if its status is anything other than STATUS_SCHEDULED
 * (i.e., COMPLETED, SKIPPED, or DEFERRED all count as done for orientation purposes).
 */
internal fun computeDayKind(
    scheduledSessions: List<BootcampSessionEntity>,
    todayDow: Int   // 1=Mon … 7=Sun
): DayKind {
    val todaySession = scheduledSessions.find { it.dayOfWeek == todayDow }
        ?: return DayKind.REST
    return if (todaySession.status != BootcampSessionEntity.STATUS_SCHEDULED)
        DayKind.RUN_DONE
    else
        DayKind.RUN_UPCOMING
}

/**
 * Returns a human-readable relative label for how far away [targetDow] is from [todayDow].
 * Handles week wrap (e.g., Friday→Monday = 3 days).
 */
internal fun computeRelativeLabel(targetDow: Int, todayDow: Int): String {
    val days = (targetDow - todayDow + 7) % 7
    return when (days) {
        0 -> "today"
        1 -> "tomorrow"
        else -> "in $days days"
    }
}

/**
 * Formats the week's date range as "Mar 10–16" or "Mar 30–Apr 5" for cross-month weeks.
 * [weekStart] must be a Monday.
 */
internal fun computeWeekDateRange(weekStart: LocalDate): String {
    val weekEnd = weekStart.plusDays(6)
    val monthFmt = DateTimeFormatter.ofPattern("MMM", java.util.Locale.US)
    return if (weekStart.month == weekEnd.month) {
        "${monthFmt.format(weekStart)} ${weekStart.dayOfMonth}–${weekEnd.dayOfMonth}"
    } else {
        "${monthFmt.format(weekStart)} ${weekStart.dayOfMonth}–${monthFmt.format(weekEnd)} ${weekEnd.dayOfMonth}"
    }
}
