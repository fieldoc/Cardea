package com.hrcoach.ui.bootcamp

import com.hrcoach.domain.bootcamp.FitnessLevel
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.PlannedSession
import com.hrcoach.domain.engine.TierPromptDirection
import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase

sealed class TodayState {
    /** Today has a session that has not yet been started. */
    data class RunUpcoming(
        val session: PlannedSession
    ) : TodayState()

    /** Today had a session that is now completed. */
    data class RunDone(
        val nextSession: PlannedSession?,
        val nextSessionDayLabel: String?,       // e.g. "Wed"
        val nextSessionRelativeLabel: String?   // e.g. "in 2 days"
    ) : TodayState()

    /** Today has no session scheduled (rest day or all sessions done). */
    data class RestDay(
        val nextSession: PlannedSession?,
        val nextSessionDayLabel: String?,
        val nextSessionRelativeLabel: String?
    ) : TodayState()
}

/** One slot in the 7-day week strip. `session` is null for rest days. */
data class WeekDayItem(
    val dayOfWeek: Int,       // 1=Mon … 7=Sun
    val dayLabel: String,     // single-letter narrow format: "M" "T" "W" …
    val isToday: Boolean,
    val session: SessionUiItem? // null = rest day
)

data class BootcampUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val hasActiveEnrollment: Boolean = false,
    val isPaused: Boolean = false,
    // Enrollment details
    val goal: BootcampGoal? = null,
    val currentPhase: TrainingPhase? = null,
    val absoluteWeek: Int = 0,
    val totalWeeks: Int = 0,
    val weekInPhase: Int = 0,
    val isRecoveryWeek: Boolean = false,
    val weeksUntilNextRecovery: Int? = null,
    val showGraduationCta: Boolean = false,
    // Week view
    val currentWeekDays: List<WeekDayItem> = emptyList(),
    val currentWeekDateRange: String = "",
    val todayState: TodayState = TodayState.RestDay(null, null, null),
    val activePreferredDays: List<DayPreference> = emptyList(),
    val upcomingWeeks: List<UpcomingWeekItem> = emptyList(),
    val swapRestMessage: String? = null,
    // Onboarding
    val showOnboarding: Boolean = false,
    val onboardingStep: Int = 0,
    val onboardingGoal: BootcampGoal? = null,
    val onboardingMinutes: Int = 30,
    val onboardingRunsPerWeek: Int = 3,
    val onboardingTimeWarning: String? = null,
    val onboardingLongRunMinutes: Int = 0,
    val onboardingWeeklyTotal: Int = 0,
    val onboardingLongRunWarning: String? = null,
    // Gap return
    val welcomeBackMessage: String? = null,
    val needsCalibration: Boolean = false,
    // Fitness
    val fitnessLevel: FitnessLevel = FitnessLevel.UNKNOWN,
    val tuningDirection: TuningDirection = TuningDirection.HOLD,
    val illnessFlag: Boolean = false,
    val tierPromptDirection: TierPromptDirection = TierPromptDirection.NONE,
    val tierPromptEvidence: String? = null,
    val missedSession: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    // Reschedule bottom sheet
    val rescheduleSheetSessionId: Long? = null,
    val rescheduleAutoTargetDay: Int? = null,
    val rescheduleAutoTargetLabel: String? = null,
    val rescheduleDropSessionId: Long? = null,
    val rescheduleAvailableDays: List<Int> = emptyList(),
    val rescheduleAvailableLabels: List<String> = emptyList(),
    // Session detail sheet
    val showSessionDetail: Boolean = false,
    val sessionDetailItem: SessionUiItem? = null,
    // Goal detail sheet
    val showGoalDetail: Boolean = false,
    val goalProgressPercentage: Int = 0,
    val maxHr: Int? = null
)

data class SessionUiItem(
    val dayLabel: String,
    val typeName: String,
    val rawTypeName: String = "",
    val minutes: Int,
    val isCompleted: Boolean,
    val isToday: Boolean,
    val sessionId: Long? = null,
    val presetId: String? = null
)

data class UpcomingWeekItem(
    val weekNumber: Int,
    val isRecoveryWeek: Boolean,
    val sessions: List<SessionUiItem>
)
