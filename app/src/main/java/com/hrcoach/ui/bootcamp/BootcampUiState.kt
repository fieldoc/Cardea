package com.hrcoach.ui.bootcamp

import com.hrcoach.domain.bootcamp.FitnessLevel
import com.hrcoach.domain.bootcamp.PlannedSession
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase

data class BootcampUiState(
    val isLoading: Boolean = true,
    val hasActiveEnrollment: Boolean = false,
    // Enrollment details
    val goal: BootcampGoal? = null,
    val currentPhase: TrainingPhase? = null,
    val absoluteWeek: Int = 0,
    val totalWeeks: Int = 0,
    val weekInPhase: Int = 0,
    val isRecoveryWeek: Boolean = false,
    // Next session
    val nextSession: PlannedSession? = null,
    val nextSessionDayLabel: String? = null,
    // Week view
    val currentWeekSessions: List<SessionUiItem> = emptyList(),
    // Onboarding
    val showOnboarding: Boolean = false,
    val onboardingGoal: BootcampGoal? = null,
    val onboardingMinutes: Int = 30,
    val onboardingRunsPerWeek: Int = 3,
    val onboardingTimeWarning: String? = null,
    // Gap return
    val welcomeBackMessage: String? = null,
    val needsCalibration: Boolean = false,
    // Fitness
    val fitnessLevel: FitnessLevel = FitnessLevel.UNKNOWN
)

data class SessionUiItem(
    val dayLabel: String,
    val typeName: String,
    val minutes: Int,
    val isCompleted: Boolean,
    val isToday: Boolean,
    val sessionId: Long? = null
)
