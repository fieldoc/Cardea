package com.hrcoach.domain.preset

import com.hrcoach.domain.model.WorkoutConfig

data class WorkoutPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val category: PresetCategory,
    val durationLabel: String,
    val intensityLabel: String,
    val buildConfig: (maxHr: Int, restHr: Int) -> WorkoutConfig
)

enum class PresetCategory {
    BASE_AEROBIC,
    THRESHOLD,
    INTERVAL,
    RACE_PREP
}
