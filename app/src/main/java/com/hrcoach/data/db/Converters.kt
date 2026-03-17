package com.hrcoach.data.db

import androidx.room.TypeConverter
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel

class Converters {

    /**
     * Serializes a list of DayPreference objects to a comma-separated string.
     * Format: "1:AVAILABLE,3:LONG_RUN_BIAS,6:AVAILABLE"
     * Empty list → ""
     */
    @TypeConverter
    fun fromDayPreferences(days: List<DayPreference>): String =
        days.joinToString(",") { "${it.day}:${it.level.name}" }

    /**
     * Parses a preferredDays string back to DayPreference objects.
     * Handles legacy format "[1,3,6]" (plain ints → all AVAILABLE)
     * and current format "1:AVAILABLE,3:LONG_RUN_BIAS".
     */
    @TypeConverter
    fun toDayPreferences(value: String): List<DayPreference> {
        if (value.isBlank()) return emptyList()
        // Legacy format: "[1,3,6]"
        if (value.startsWith("[")) {
            return value
                .removeSurrounding("[", "]")
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .map { DayPreference(it, DaySelectionLevel.AVAILABLE) }
        }
        // Current format: "1:AVAILABLE,3:LONG_RUN_BIAS"
        return value.split(",").mapNotNull { token ->
            val parts = token.split(":")
            if (parts.size != 2) return@mapNotNull null
            val day = parts[0].toIntOrNull() ?: return@mapNotNull null
            val level = runCatching { DaySelectionLevel.valueOf(parts[1]) }.getOrNull()
                ?: DaySelectionLevel.AVAILABLE
            DayPreference(day, level)
        }
    }
}
