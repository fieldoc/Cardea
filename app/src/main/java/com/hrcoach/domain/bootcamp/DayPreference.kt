package com.hrcoach.domain.bootcamp

enum class DaySelectionLevel {
    NONE,
    AVAILABLE,
    LONG_RUN_BIAS,
    BLACKOUT;

    fun next(): DaySelectionLevel = when (this) {
        NONE -> AVAILABLE
        AVAILABLE -> LONG_RUN_BIAS
        LONG_RUN_BIAS -> NONE
        BLACKOUT -> NONE   // safety: tapping a blackout day clears it
    }
}

data class DayPreference(
    val day: Int,
    val level: DaySelectionLevel
)
