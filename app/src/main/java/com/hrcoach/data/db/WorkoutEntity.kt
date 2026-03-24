package com.hrcoach.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workouts")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long = 0L,
    val totalDistanceMeters: Float = 0f,
    val mode: String,
    val targetConfig: String,
    @ColumnInfo(defaultValue = "") val userId: String = ""
)
