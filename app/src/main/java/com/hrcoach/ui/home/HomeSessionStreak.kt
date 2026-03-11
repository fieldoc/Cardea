package com.hrcoach.ui.home

import com.hrcoach.data.db.BootcampSessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Computes the no-misses streak: consecutive completed Bootcamp sessions,
 * walking backward, stopping at the first skipped or effectively-missed session.
 *
 * @param sessions All sessions for the enrollment, any order (function sorts them).
 * @param enrollmentStartMs Epoch ms of the Monday that begins week 1 of the program.
 * @param today  LocalDate to compare against (injectable for testing).
 * @param zone   Time zone used to convert epoch ms to local dates (injectable for testing).
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
                // The user will complete them later; treat as pending, not missed.
            }
        }
    }
    return streak
}
