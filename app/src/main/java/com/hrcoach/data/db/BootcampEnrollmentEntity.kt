package com.hrcoach.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel

@Entity(tableName = "bootcamp_enrollments")
data class BootcampEnrollmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalType: String,
    val targetMinutesPerRun: Int,
    val runsPerWeek: Int,
    val preferredDays: List<DayPreference>,
    val startDate: Long,
    val currentPhaseIndex: Int = 0,
    val currentWeekInPhase: Int = 0,
    val status: String = STATUS_ACTIVE,
    val tierIndex: Int = 0,
    val tierPromptSnoozedUntilMs: Long = 0,
    val tierPromptDismissCount: Int = 0,
    val illnessPromptSnoozedUntilMs: Long = 0,
    val pausedAtMs: Long = 0
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_GRADUATED = "GRADUATED"

        /** Legacy helper — parses a serialized day-preferences string. */
        fun parseDayPreferences(raw: String): List<DayPreference> {
            if (raw.isBlank()) return emptyList()
            if (raw.startsWith("[")) {
                return raw.removeSurrounding("[", "]")
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .map { DayPreference(it, DaySelectionLevel.AVAILABLE) }
            }
            return raw.split(",").mapNotNull { token ->
                val parts = token.split(":")
                if (parts.size != 2) return@mapNotNull null
                val day = parts[0].toIntOrNull() ?: return@mapNotNull null
                val level = runCatching { DaySelectionLevel.valueOf(parts[1]) }.getOrNull()
                    ?: DaySelectionLevel.AVAILABLE
                DayPreference(day, level)
            }
        }

        /** Legacy helper — serializes day preferences to a string. */
        fun serializeDayPreferences(days: List<DayPreference>): String =
            days.joinToString(",") { "${it.day}:${it.level.name}" }
    }
}
