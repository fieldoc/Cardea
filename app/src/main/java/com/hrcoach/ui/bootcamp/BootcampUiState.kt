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

/**
 * Reason a candidate day looks the way it does in the reschedule chip strip.
 * Mirrors `domain.bootcamp.SuggestionReason` but lives in the UI layer to keep the
 * domain model free of UI vocabulary. Precedence (single-valued, most-restrictive
 * wins): OCCUPIED > BLACKOUT > RECOVERY > FREE.
 */
enum class RescheduleReasonUi { FREE, OCCUPIED, RECOVERY, BLACKOUT }

/** One chip in the reschedule sheet's day strip. */
data class RescheduleDayUi(
    /** ISO day-of-week, 1=Mon … 7=Sun */
    val day: Int,
    /** Short locale-aware label (e.g. "Wed"). */
    val label: String,
    val reason: RescheduleReasonUi,
    /** True for the first preferred FREE day — gets the "Recommended" callout. */
    val isRecommended: Boolean,
    /** True if this day is in the user's preferred-days list at AVAILABLE/LONG_RUN_BIAS.
     *  Drives stronger emphasis on FREE chips that match the user's plan. */
    val isPreferred: Boolean
)

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
    val upcomingRuns: List<UpcomingRunItem> = emptyList(),
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
    val welcomeBackDisclosure: WelcomeBackDisclosure? = null,
    val showRewindBreadcrumb: Boolean = false,
    val needsCalibration: Boolean = false,
    // Fitness
    val fitnessLevel: FitnessLevel = FitnessLevel.UNKNOWN,
    val tuningDirection: TuningDirection = TuningDirection.HOLD,
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
    val rescheduleSessionTypeLabel: String? = null,
    val rescheduleAutoTargetDay: Int? = null,
    val rescheduleAutoTargetLabel: String? = null,
    val rescheduleSuggestions: List<RescheduleDayUi> = emptyList(),
    // Advisory-confirm dialog (rotation-safe; resets on process death along with the sheet)
    val rescheduleConfirmDay: Int? = null,
    val rescheduleConfirmDayLabel: String? = null,
    val rescheduleConfirmDayLongLabel: String? = null,
    val rescheduleConfirmReason: RescheduleReasonUi? = null,
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
    val showAudioPrimer: Boolean = false,
    /**
     * True when the one-time strides bootcamp primer dialog should be rendered. Gated
     * on the next bootcamp session being a strides session AND the user not having
     * dismissed the primer before. See [BootcampViewModel.dismissStridesPrimer].
     */
    val showStridesPrimer: Boolean = false,
    /**
     * Reps in the upcoming strides session. Derived from session duration
     * (20→4, 22→6, 24→8, 26→10, else 5). Surfaced for the primer copy.
     */
    val stridesPrimerTotalReps: Int = 5
)

/**
 * Structured payload for the post-gap "Welcome Back" dialog.
 *
 * Replaces the legacy single-string `welcomeBackMessage` so the UI can render BOTH
 * the schedule rewind (always present when the dialog is shown) and the optional
 * intensity easing as labeled sections, instead of one silently overriding the other.
 *
 * Built by `BootcampViewModel.applyGapAdjustmentIfNeeded()`; consumed by
 * `WelcomeBackDialog`. See `docs/cardea/2026-05-05-welcome-back-disclosure.md`.
 */
data class WelcomeBackDisclosure(
    /** SCHEDULE section body — required. Pre-formatted with concrete numbers. */
    val schedule: ScheduleChange,
    /** INTENSITY section body — null when no tier change / discovery run. */
    val intensity: IntensityChange? = null
) {
    /** Pattern: "Rolled back from week 3 to week 2 of Base. Cleared 4 upcoming sessions." */
    sealed class ScheduleChange {
        /** weekInPhase rewound by 1 within the same phase. */
        data class WeekRollback(
            val fromWeek: Int,
            val toWeek: Int,
            val phaseName: String,
            val sessionsCleared: Int
        ) : ScheduleChange()

        /** weekInPhase reset to 0 (start of phase) — same phase. */
        data class PhaseStartReset(
            val phaseName: String,
            val sessionsCleared: Int
        ) : ScheduleChange()

        /** Full reset to start of Base (LONG_ABSENCE / FULL_RESET). */
        data class FullReset(
            val phaseName: String,
            val sessionsCleared: Int
        ) : ScheduleChange()
    }

    sealed class IntensityChange {
        /** Tier was demoted because CTL decayed below the runner's prior tier band. */
        object TierEased : IntensityChange()

        /** Next run is a calibration / Discovery Run rather than a planned session. */
        object DiscoveryRun : IntensityChange()
    }
}

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

/**
 * One row in the "Coming up" list — a future scheduled session, rest days excluded.
 * Drives the per-session list view in the bootcamp dashboard (mirrors the design's
 * SUN 17 / Long run / Z2 chip pattern).
 */
data class UpcomingRunItem(
    val sessionId: Long?,
    val dayLabel: String,        // "SUN", "TUE" — uppercase 3-letter
    val dayOfMonth: Int,         // 17
    val title: String,           // "Long" / "Tempo (Z3)" / "Strides"
    val subtitle: String,        // "54 min · Base" — minutes + short zone
    val zoneTag: String?,        // "Z2" / "Z3" / "Z4" / "Recovery" / "Race"
    val rawSessionType: String   // for caller-side coloring decisions
)
