package com.hrcoach.util

import com.hrcoach.data.db.WorkoutEntity

val WorkoutEntity.recordedAtMs: Long
    get() = if (endTime > startTime) endTime else startTime

val WorkoutEntity.durationMinutes: Float
    get() = (endTime - startTime).coerceAtLeast(0L) / 60_000f
