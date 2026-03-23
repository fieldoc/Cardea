package com.hrcoach.domain.sharing

import com.hrcoach.data.firebase.RunCompletionPayload

enum class DayState {
    COMPLETED, DEFERRED, BONUS, TODAY, REST, FUTURE
}

object WeekStripState {
    fun compute(completions: List<RunCompletionPayload>, todayWeekDay: Int): List<DayState> {
        val states = MutableList(7) { index ->
            val dayNumber = index + 1
            when {
                dayNumber == todayWeekDay -> DayState.TODAY
                dayNumber < todayWeekDay -> DayState.REST
                else -> DayState.FUTURE
            }
        }
        for (c in completions) {
            val origDay = c.originalScheduledWeekDay
            if (origDay != null && origDay in 1..7) {
                states[origDay - 1] = DayState.DEFERRED
            }
        }
        for (c in completions) {
            val dayIdx = c.weekDay - 1
            if (dayIdx !in 0..6) continue
            states[dayIdx] = if (c.wasScheduled) DayState.COMPLETED else DayState.BONUS
        }
        return states
    }
}
