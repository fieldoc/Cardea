package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.preset.PresetCategory

enum class SessionType(val presetCategory: PresetCategory?) {
    EASY(PresetCategory.BASE_AEROBIC),
    LONG(PresetCategory.BASE_AEROBIC),
    TEMPO(PresetCategory.THRESHOLD),
    INTERVAL(PresetCategory.INTERVAL),
    RACE_SIM(PresetCategory.RACE_PREP),
    DISCOVERY(null),
    CHECK_IN(null)
}

data class PlannedSession(
    val type: SessionType,
    val minutes: Int,
    val presetId: String? = null
)
