package com.hrcoach.domain.achievement

import com.hrcoach.data.db.BootcampSessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object StreakCalculator {

    /**
     * Count consecutive completed sessions walking backward.
     * DEFERRED sessions are skipped (don't break streak).
     * SKIPPED or past SCHEDULED sessions break the streak.
     * Future SCHEDULED sessions are ignored.
     */
    fun computeSessionStreak(
        sessions: List<BootcampSessionEntity>,
        enrollmentStartMs: Long,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val startDate = Instant.ofEpochMilli(enrollmentStartMs).atZone(zone).toLocalDate()

        val sorted = sessions.sortedWith(
            compareByDescending<BootcampSessionEntity> { it.weekNumber }
                .thenByDescending { it.dayOfWeek }
        )

        var streak = 0
        for (session in sorted) {
            when (session.status) {
                BootcampSessionEntity.STATUS_COMPLETED -> streak++
                BootcampSessionEntity.STATUS_SKIPPED -> return streak
                BootcampSessionEntity.STATUS_SCHEDULED -> {
                    val sessionDate = startDate.plusDays(
                        ((session.weekNumber - 1L) * 7L) + (session.dayOfWeek - 1L)
                    )
                    if (sessionDate.isBefore(today)) return streak // effectively missed
                    // future session — ignore and continue
                }
                BootcampSessionEntity.STATUS_DEFERRED -> {
                    // Deferred sessions are rescheduled, not abandoned — they do not break the streak.
                }
            }
        }
        return streak
    }

    /**
     * Count consecutive completed ISO weeks where completed session count >= runsPerWeek.
     * Walks backward from the most recently completed week (excludes current incomplete week).
     * DEFERRED sessions do not count as completions.
     */
    fun computeWeeklyGoalStreak(
        sessions: List<BootcampSessionEntity>,
        runsPerWeek: Int,
        enrollmentStartMs: Long,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        if (sessions.isEmpty() || runsPerWeek <= 0) return 0

        val completedByWeek = sessions
            .filter { it.status == BootcampSessionEntity.STATUS_COMPLETED }
            .groupBy { it.weekNumber }
            .mapValues { (_, v) -> v.size }

        val maxWeek = sessions.maxOf { it.weekNumber }
        val firstWeek = sessions.minOf { it.weekNumber }

        // Determine current week to exclude it
        val hasScheduledInMaxWeek = sessions.any {
            it.weekNumber == maxWeek && it.status == BootcampSessionEntity.STATUS_SCHEDULED
        }
        val lastCompleteWeek = if (hasScheduledInMaxWeek) maxWeek - 1 else maxWeek

        var streak = 0
        for (week in lastCompleteWeek downTo firstWeek) {
            val completed = completedByWeek[week] ?: 0
            if (completed >= runsPerWeek) {
                streak++
            } else {
                break
            }
        }
        return streak
    }
}
