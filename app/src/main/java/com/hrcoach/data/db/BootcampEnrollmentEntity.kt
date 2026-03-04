package com.hrcoach.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val status: String = "ACTIVE"
)
