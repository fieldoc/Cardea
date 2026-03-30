package com.hrcoach.domain.bootcamp

import com.hrcoach.domain.preset.PresetCategory

enum class SessionType(val presetCategory: PresetCategory?) {
    EASY(PresetCategory.BASE_AEROBIC),
    LONG(PresetCategory.BASE_AEROBIC),
    TEMPO(PresetCategory.THRESHOLD),
    INTERVAL(PresetCategory.INTERVAL),
    STRIDES(PresetCategory.BASE_AEROBIC),
    RACE_SIM(PresetCategory.RACE_PREP),
    DISCOVERY(null),
    CHECK_IN(null);

    companion object {
        fun displayLabelForPreset(presetId: String?): String? = when (presetId) {
            "lactate_threshold" -> "Threshold (Z4)"
            "aerobic_tempo" -> "Tempo (Z3)"
            "norwegian_4x4" -> "Intervals (Z5)"
            "strides_20s" -> "Strides"
            else -> null
        }
    }
}

data class PlannedSession(
    val type: SessionType,
    val minutes: Int,
    val presetId: String? = null,
    val weekNumber: Int? = null
)
