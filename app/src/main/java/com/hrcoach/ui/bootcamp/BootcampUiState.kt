package com.hrcoach.ui.bootcamp

import android.bluetooth.BluetoothDevice
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
        val nextSessionRelativeLabel: String?,  // e.g. "in 2 days"
        val nextFutureSessionId: Long? = null   // for "pull forward" CTA
    ) : TodayState()

    /** Today has no session scheduled (rest day or all sessions done). */
    data class RestDay(
        val nextSession: PlannedSession?,
        val nextSessionDayLabel: String?,
        val nextSessionRelativeLabel: String?,
        val nextFutureSessionId: Long? = null   // for "pull forward" CTA
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
    val showCarousel: Boolean = false,
    val showOnboarding: Boolean = false,
    val onboardingStep: Int = 0,
    val onboardingGoal: BootcampGoal? = null,
    val onboardingAvailableMinutes: Int = 30,
    val onboardingRunsPerWeek: Int = 3,
    val onboardingTargetFinishingTime: Int? = null,
    val onboardingTimeWarning: String? = null,
    val onboardingTimeCanProceed: Boolean = true,
    val onboardingLongRunMinutes: Int = 0,
    val onboardingWeeklyTotal: Int = 0,
    val onboardingPreferredDays: List<DayPreference> = emptyList(),
    val onboardingLongRunWarning: String? = null,
    val onboardingPreviewSessions: List<PlannedSession> = emptyList(),
    // Gap return
    val welcomeBackMessage: String? = null,
    val needsCalibration: Boolean = false,
    // Fitness
    val fitnessLevel: FitnessLevel = FitnessLevel.UNKNOWN,
    val tuningDirection: TuningDirection = TuningDirection.HOLD,
    val illnessFlag: Boolean = false,
    val tierIndex: Int = 0,
    val ctl: Float = 0f,
    val showTierDetail: Boolean = false,
    val tierPromptDirection: TierPromptDirection = TierPromptDirection.NONE,
    val tierPromptEvidence: String? = null,
    val missedSessionCount: Int = 0,
    val missedSessionIds: List<Long> = emptyList(),
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
    val maxHr: Int? = null,
    // MaxHR gate (blocks workout start until maxHR is set)
    val showMaxHrGate: Boolean = false,
    val maxHrGateInput: String = "",
    val maxHrGateError: String? = null,
    val pendingGateSession: PlannedSession? = null,
    // BLE connection (pre-start dialog)
    val showHrConnectDialog: Boolean = false,
    val bleIsScanning: Boolean = false,
    val bleDiscoveredDevices: List<BluetoothDevice> = emptyList(),
    val bleIsConnected: Boolean = false,
    val bleConnectedDeviceName: String = "",
    val bleConnectedDeviceAddress: String = "",
    val bleLiveHr: Int = 0,
    val bleConnectionError: String? = null,
    val pendingConfigJson: String? = null,
    val bleLastKnownDeviceName: String? = null,
    val bleLastKnownDeviceAddress: String? = null,
    /** True when the one-time audio primer dialog should be rendered. */
    val showAudioPrimer: Boolean = false
)

data class SessionUiItem(
    val dayLabel: String,
    val typeName: String,
    val rawTypeName: String = "",
    val minutes: Int,
    val isCompleted: Boolean,
    val isToday: Boolean,
    val isPast: Boolean = false,
    val isDeferred: Boolean = false,
    val sessionId: Long? = null,
    val presetId: String? = null
)

data class UpcomingWeekItem(
    val weekNumber: Int,
    val isRecoveryWeek: Boolean,
    val sessions: List<SessionUiItem>
)
