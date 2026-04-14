package com.hrcoach.domain.bootcamp

import android.util.Log
import kotlin.math.abs

object SessionDayAssigner {

    val HARD_TYPES = setOf(SessionType.TEMPO, SessionType.INTERVAL, SessionType.LONG, SessionType.RACE_SIM)

    private const val MIN_HARD_DAY_GAP = 2

    /**
     * Assigns planned sessions to available days with hard-effort spacing.
     * Returns pairs of (session, dayOfWeek).
     *
     * Rules:
     * 1. LONG/RACE_SIM goes to longRunBiasDay if provided
     * 2. Hard sessions (TEMPO, INTERVAL, LONG, RACE_SIM) maximally spaced
     *    with a HARD constraint: at least 2-day gap between hard sessions.
     *    If no valid day satisfies the gap, the hard session is demoted to EASY.
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
        val demotedSessions = mutableListOf<PlannedSession>()

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

        // 2. Place hard sessions with maximum spacing, enforcing minimum 2-day gap
        for (hard in hardSessions) {
            val placedHardDays = result.filter { it.first.type in HARD_TYPES }.map { it.second }
            val validDays = sortedDays
                .filter { it !in usedDays }
                .filter { day -> placedHardDays.all { abs(it - day) >= MIN_HARD_DAY_GAP } }

            val bestDay = validDays.maxByOrNull { day ->
                if (placedHardDays.isEmpty()) Int.MAX_VALUE
                else placedHardDays.minOf { abs(it - day) }
            }

            if (bestDay != null) {
                result.add(hard to bestDay)
                usedDays.add(bestDay)
            } else {
                // No valid day satisfies the 2-day gap — demote to EASY
                Log.i("SessionDayAssigner", "Demoted ${hard.type}(${hard.minutes}min) to EASY — no day with ${MIN_HARD_DAY_GAP}-day gap from ${result.filter { it.first.type in HARD_TYPES }.map { it.second }}")
                demotedSessions.add(
                    PlannedSession(SessionType.EASY, hard.minutes, "zone2_base")
                )
            }
        }

        // 3. Easy sessions (original + demoted) fill remaining days — one session per day
        val allEasy = easySessions + demotedSessions
        val remainingDays = sortedDays.filter { it !in usedDays }.toMutableList()
        for (easy in allEasy) {
            val day = remainingDays.removeFirstOrNull() ?: break  // no remaining days — drop excess
            result.add(easy to day)
        }

        return result.sortedBy { it.second }
    }
}
