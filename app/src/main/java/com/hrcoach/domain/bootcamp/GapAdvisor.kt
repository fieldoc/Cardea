package com.hrcoach.domain.bootcamp

enum class GapStrategy {
    ON_TRACK,
    MINOR_SLIP,
    SHORT_BREAK,
    MEANINGFUL_BREAK,
    EXTENDED_BREAK,
    LONG_ABSENCE,
    FULL_RESET
}

data class GapAction(
    val phaseIndex: Int,
    val weekInPhase: Int,
    val insertReturnSession: Boolean,
    val requiresCalibration: Boolean,
    val welcomeMessage: String? = null
)

object GapAdvisor {

    fun assess(daysSinceLastRun: Int): GapStrategy = when {
        daysSinceLastRun <= 3 -> GapStrategy.ON_TRACK
        daysSinceLastRun <= 7 -> GapStrategy.MINOR_SLIP
        daysSinceLastRun <= 14 -> GapStrategy.SHORT_BREAK
        daysSinceLastRun <= 28 -> GapStrategy.MEANINGFUL_BREAK
        daysSinceLastRun <= 60 -> GapStrategy.EXTENDED_BREAK
        daysSinceLastRun <= 120 -> GapStrategy.LONG_ABSENCE
        else -> GapStrategy.FULL_RESET
    }

    fun action(
        strategy: GapStrategy,
        currentPhaseIndex: Int,
        currentWeekInPhase: Int
    ): GapAction = when (strategy) {
        GapStrategy.ON_TRACK -> GapAction(
            phaseIndex = currentPhaseIndex,
            weekInPhase = currentWeekInPhase,
            insertReturnSession = false,
            requiresCalibration = false
        )
        GapStrategy.MINOR_SLIP -> GapAction(
            phaseIndex = currentPhaseIndex,
            weekInPhase = currentWeekInPhase,
            insertReturnSession = false,
            requiresCalibration = false
        )
        GapStrategy.SHORT_BREAK -> GapAction(
            phaseIndex = currentPhaseIndex,
            weekInPhase = currentWeekInPhase,
            insertReturnSession = true,
            requiresCalibration = false,
            welcomeMessage = "Welcome back — here's an easy one to get moving again."
        )
        GapStrategy.MEANINGFUL_BREAK -> GapAction(
            phaseIndex = currentPhaseIndex,
            weekInPhase = (currentWeekInPhase - 1).coerceAtLeast(0),
            insertReturnSession = true,
            requiresCalibration = false,
            welcomeMessage = "Welcome back — let's ease in."
        )
        GapStrategy.EXTENDED_BREAK -> GapAction(
            phaseIndex = currentPhaseIndex,
            weekInPhase = 0,
            insertReturnSession = true,
            requiresCalibration = false,
            welcomeMessage = "Welcome back after a break — we've rewound to the start of this phase."
        )
        GapStrategy.LONG_ABSENCE -> GapAction(
            phaseIndex = 0,
            weekInPhase = 0,
            insertReturnSession = true,
            requiresCalibration = true,
            welcomeMessage = "Welcome back — your first run will help us gauge where you are."
        )
        GapStrategy.FULL_RESET -> GapAction(
            phaseIndex = 0,
            weekInPhase = 0,
            insertReturnSession = true,
            requiresCalibration = true,
            welcomeMessage = "Welcome back — let's start fresh with a Discovery Run."
        )
    }
}
