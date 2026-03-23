package com.hrcoach.domain.sharing

import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.db.WorkoutEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object PartnerStreakCalculator {

    fun computeBootcampStreak(
        sessions: List<BootcampSessionEntity>,
        enrollmentStartMs: Long = 0L,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val startDate = if (enrollmentStartMs > 0)
            Instant.ofEpochMilli(enrollmentStartMs).atZone(zone).toLocalDate()
        else today.minusDays(365)

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
                    if (sessionDate.isBefore(today)) return streak
                }
                BootcampSessionEntity.STATUS_DEFERRED -> { /* skip, don't break */ }
            }
        }
        return streak
    }

    fun computeFreeRunnerStreak(
        workouts: List<WorkoutEntity>,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        if (workouts.isEmpty()) return 0
        val runDays = workouts.map { w ->
            Instant.ofEpochMilli(w.startTime).atZone(zone).toLocalDate()
        }.toSet()
        var streak = 0
        var day = today
        while (runDays.contains(day)) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }
}
