package com.hrcoach.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class PartnerActivity(
    val userId: String,
    val displayName: String,
    val emblemId: String,
    val currentStreak: Int,
    val weeklyRunCount: Int,
    val lastRunDate: String?,
    val lastRunDurationMin: Int?,
    val lastRunPhase: String?,
) {
    fun ranToday(): Boolean {
        val date = lastRunDate ?: return false
        return LocalDate.parse(date) == LocalDate.now()
    }

    fun isRecentlyActive(): Boolean {
        val date = lastRunDate ?: return false
        val daysSince = ChronoUnit.DAYS.between(LocalDate.parse(date), LocalDate.now())
        return daysSince <= 2
    }
}
