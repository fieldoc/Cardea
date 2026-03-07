package com.hrcoach.domain.preset

import com.hrcoach.domain.engine.TuningDirection

data class PresetConfig(
    val presetId: String,
    val durationMinutes: Int,
    val description: String
)

data class SessionPresetArray(
    val sessionTypeName: String,
    val presets: List<PresetConfig>
) {
    fun tune(currentIndex: Int, direction: TuningDirection): Int = when (direction) {
        TuningDirection.PUSH_HARDER -> (currentIndex + 1).coerceAtMost(presets.size - 1)
        TuningDirection.EASE_BACK -> (currentIndex - 1).coerceAtLeast(0)
        TuningDirection.HOLD -> currentIndex
    }

    fun presetAt(index: Int): PresetConfig = presets[index.coerceIn(presets.indices)]

    companion object {
        fun easyRunTier1() = SessionPresetArray(
            sessionTypeName = "easy",
            presets = listOf(
                PresetConfig("zone2_base", 20, "20-min easy walk/run"),
                PresetConfig("zone2_base", 30, "30-min easy run"),
                PresetConfig("zone2_base", 38, "38-min easy run")
            )
        )

        fun easyRunTier2() = SessionPresetArray(
            sessionTypeName = "easy",
            presets = listOf(
                PresetConfig("zone2_base", 28, "28-min easy"),
                PresetConfig("zone2_base", 35, "35-min easy"),
                PresetConfig("zone2_base", 42, "42-min easy"),
                PresetConfig("zone2_base", 50, "50-min easy (T2 cap)")
            )
        )

        fun tempoTier2() = SessionPresetArray(
            sessionTypeName = "tempo",
            presets = listOf(
                PresetConfig("aerobic_tempo", 20, "2x8 min Z3 with rest"),
                PresetConfig("aerobic_tempo", 25, "20-min continuous Z3"),
                PresetConfig("aerobic_tempo", 30, "25-min continuous Z3"),
                PresetConfig("aerobic_tempo", 35, "35-min continuous Z3"),
                PresetConfig("lactate_threshold", 35, "30-min continuous Z4 (T2 cap)")
            )
        )

        fun longRunTier2() = SessionPresetArray(
            sessionTypeName = "long",
            presets = listOf(
                PresetConfig("zone2_base", 45, "45-min long run"),
                PresetConfig("zone2_base", 60, "60-min long run"),
                PresetConfig("zone2_base", 75, "75-min long run"),
                PresetConfig("zone2_base", 90, "90-min long run (T2 cap)")
            )
        )

        fun easyRunTier3() = SessionPresetArray(
            sessionTypeName = "easy",
            presets = listOf(
                PresetConfig("zone2_base", 35, "35-min easy"),
                PresetConfig("zone2_base", 45, "45-min easy"),
                PresetConfig("zone2_base", 55, "55-min easy"),
                PresetConfig("zone2_base", 65, "65-min easy (T3 cap)")
            )
        )

        fun vo2maxTier3() = SessionPresetArray(
            sessionTypeName = "interval",
            presets = listOf(
                PresetConfig("norwegian_4x4", 30, "3x4 min Z5, long recovery"),
                PresetConfig("norwegian_4x4", 35, "4x4 min Z5 (baseline)"),
                PresetConfig("norwegian_4x4", 40, "5x4 min Z5"),
                PresetConfig("norwegian_4x4", 45, "6x4 min Z5 (T3 cap)")
            )
        )

        fun thresholdTier3() = SessionPresetArray(
            sessionTypeName = "tempo",
            presets = listOf(
                PresetConfig("lactate_threshold", 30, "2x10 min Z4 cruise"),
                PresetConfig("lactate_threshold", 35, "3x10 min Z4 cruise"),
                PresetConfig("lactate_threshold", 40, "40-min continuous Z4"),
                PresetConfig("lactate_threshold", 50, "50-min continuous Z4 (T3 cap)")
            )
        )

        fun stridesTier2() = SessionPresetArray(
            sessionTypeName = "strides",
            presets = listOf(
                PresetConfig("strides_20s", 20, "4x20s strides after easy run"),
                PresetConfig("strides_20s", 22, "6x20s strides after easy run"),
                PresetConfig("strides_20s", 24, "8x20s strides after easy run")
            )
        )

        fun stridesTier3() = SessionPresetArray(
            sessionTypeName = "strides",
            presets = listOf(
                PresetConfig("strides_20s", 22, "6x20s strides after easy run"),
                PresetConfig("strides_20s", 24, "8x20s strides after easy run"),
                PresetConfig("strides_20s", 26, "10x20s strides after easy run")
            )
        )
    }
}
