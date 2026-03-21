package com.hrcoach.domain.bootcamp

import kotlin.math.abs

object SessionDayAssigner {

    val HARD_TYPES = setOf(SessionType.TEMPO, SessionType.INTERVAL, SessionType.LONG, SessionType.RACE_SIM)

    /**
     * Assigns planned sessions to available days with hard-effort spacing.
     * Returns pairs of (session, dayOfWeek).
     *
     * Rules:
     * 1. LONG/RACE_SIM goes to longRunBiasDay if provided
     * 2. Hard sessions (TEMPO, INTERVAL, LONG, RACE_SIM) maximally spaced
     * 3. Easy sessions fill remaining days
     */
    fun assign(
        sessions: List<PlannedSession>,
        availableDays: List<Int>,
        longRunBiasDay: Int? = null
    ): List<Pair<PlannedSession, Int>> {
        if (sessions.isEmpty() || availableDays.isEmpty()) return emptyList()

        val sortedDays = availableDays.sorted()
        val result = mutableListOf<Pair<PlannedSession, Int>>()
        val usedDays = mutableSetOf<Int>()

        val longSession = sessions.firstOrNull { it.type == SessionType.LONG || it.type == SessionType.RACE_SIM }
        val hardSessions = sessions.filter { it.type in HARD_TYPES && it != longSession }
        val easySessions = sessions.filter { it.type !in HARD_TYPES }

        // 1. Place long run on bias day or last available
        if (longSession != null) {
            val longDay = if (longRunBiasDay != null && longRunBiasDay in sortedDays) longRunBiasDay
                          else sortedDays.last()
            result.add(longSession to longDay)
            usedDays.add(longDay)
        }

        // 2. Place hard sessions with maximum spacing from existing hard sessions
        for (hard in hardSessions) {
            val bestDay = sortedDays
                .filter { it !in usedDays }
                .maxByOrNull { day ->
                    val hardDays = result.filter { it.first.type in HARD_TYPES }.map { it.second }
                    if (hardDays.isEmpty()) Int.MAX_VALUE
                    else hardDays.minOf { abs(it - day) }
                }
            if (bestDay != null) {
                result.add(hard to bestDay)
                usedDays.add(bestDay)
            }
        }

        // 3. Easy sessions fill remaining days
        val remainingDays = sortedDays.filter { it !in usedDays }.toMutableList()
        for (easy in easySessions) {
            val day = remainingDays.removeFirstOrNull() ?: sortedDays.first()
            result.add(easy to day)
        }

        return result.sortedBy { it.second }
    }
}
