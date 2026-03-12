package com.hrcoach.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AchievementType {
    TIER_GRADUATION,
    DISTANCE_MILESTONE,
    STREAK_MILESTONE,
    BOOTCAMP_GRADUATION,
    WEEKLY_GOAL_STREAK
}

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val milestone: String,
    val goal: String? = null,
    val tier: Int? = null,
    val prestigeLevel: Int,
    val earnedAtMs: Long,
    val triggerWorkoutId: Long? = null,
    @ColumnInfo(defaultValue = "0") val shown: Boolean = false
)
