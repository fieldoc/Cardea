package com.hrcoach.ui.home

import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.domain.achievement.StreakCalculator
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
): Int = StreakCalculator.computeSessionStreak(sessions, enrollmentStartMs, today, zone)
