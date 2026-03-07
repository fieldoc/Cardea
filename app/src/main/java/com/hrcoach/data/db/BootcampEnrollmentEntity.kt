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
    val preferredDays: String,
    val startDate: Long,
    val currentPhaseIndex: Int = 0,
    val currentWeekInPhase: Int = 0,
    val status: String = STATUS_ACTIVE,
    val pausedAtMs: Long = 0L,
    val illnessPromptSnoozedUntilMs: Long = 0L,
    val tierIndex: Int = 0,
    val tierPromptSnoozedUntilMs: Long = 0L,
    val tierPromptDismissCount: Int = 0
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_PAUSED = "PAUSED"

        /**
         * Serializes a list of DayPreference objects to a string.
         * Format: "1:AVAILABLE,3:LONG_RUN_BIAS,6:AVAILABLE"
         */
        fun serializeDayPreferences(days: List<DayPreference>): String =
            days.joinToString(",") { "${it.day}:${it.level.name}" }

        /**
         * Parses a preferredDays string back to DayPreference objects.
         * Handles legacy format "[1,3,6]" (plain ints -> all AVAILABLE)
         * and new format "1:AVAILABLE,3:LONG_RUN_BIAS".
         */
        fun parseDayPreferences(encoded: String): List<DayPreference> {
            if (encoded.isBlank()) return emptyList()
            // Legacy format: "[1,3,6]"
            if (encoded.startsWith("[")) {
                return encoded
                    .removeSurrounding("[", "]")
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .map { DayPreference(it, DaySelectionLevel.AVAILABLE) }
            }
            // New format: "1:AVAILABLE,3:LONG_RUN_BIAS"
            return encoded.split(",").mapNotNull { token ->
                val parts = token.split(":")
                if (parts.size != 2) return@mapNotNull null
                val day = parts[0].toIntOrNull() ?: return@mapNotNull null
                val level = runCatching { DaySelectionLevel.valueOf(parts[1]) }.getOrNull()
                    ?: DaySelectionLevel.AVAILABLE
                DayPreference(day, level)
            }
        }
    }
}
