package com.hrcoach.ui.bootcamp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.firebase.CloudBackupManager
import com.hrcoach.data.db.BootcampEnrollmentEntity
import com.hrcoach.data.db.BootcampSessionEntity
import com.hrcoach.data.repository.AdaptiveProfileRepository
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.data.repository.BootcampRepository
import com.hrcoach.data.repository.UserProfileRepository
import com.hrcoach.data.repository.WorkoutMetricsRepository
import com.hrcoach.domain.achievement.AchievementEvaluator
import com.hrcoach.domain.bootcamp.BootcampSessionCompleter
import com.hrcoach.domain.bootcamp.CalendarDriftRecoverer
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.service.BootcampNotificationManager
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.domain.bootcamp.firstPreferredDayAfterMs
import com.hrcoach.domain.bootcamp.FitnessEvaluator
import com.hrcoach.domain.bootcamp.GapAdvisor
import com.hrcoach.domain.bootcamp.GapStrategy
import com.hrcoach.domain.engine.FitnessLoadCalculator
import com.hrcoach.domain.bootcamp.PhaseEngine
import com.hrcoach.domain.bootcamp.PlannedSession
import com.hrcoach.domain.bootcamp.CoachingCopyGenerator
import com.hrcoach.domain.bootcamp.RescheduleRequest
import com.hrcoach.domain.bootcamp.RescheduleResult
import com.hrcoach.domain.bootcamp.SessionDayAssigner
import com.hrcoach.domain.bootcamp.SessionRescheduler
import com.hrcoach.domain.bootcamp.SuggestionReason
import com.hrcoach.domain.bootcamp.SessionType
import com.hrcoach.domain.education.ZoneEducationProvider
import com.hrcoach.domain.bootcamp.DurationScaler
import com.hrcoach.domain.bootcamp.FinishingTimeTierMapper
import com.hrcoach.domain.bootcamp.TierCtlRanges
import com.hrcoach.domain.model.WorkoutAdaptiveMetrics
import com.hrcoach.domain.engine.FitnessSignalEvaluator
import com.hrcoach.domain.engine.StridesController
import com.hrcoach.domain.engine.TierPromptDirection
import com.hrcoach.domain.engine.TuningDirection
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase
import com.hrcoach.service.BleConnectionCoordinator
import com.hrcoach.service.WorkoutState
import com.hrcoach.service.simulation.SimulationController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class BootcampViewModel @Inject constructor(
    private val bootcampRepository: BootcampRepository,
    private val adaptiveProfileRepository: AdaptiveProfileRepository,
    private val userProfileRepository: UserProfileRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository,
    private val bootcampSessionCompleter: BootcampSessionCompleter,
    private val achievementEvaluator: AchievementEvaluator,
    private val notificationManager: BootcampNotificationManager,
    private val bleCoordinator: BleConnectionCoordinator,
    private val cloudBackupManager: CloudBackupManager,
    private val audioSettingsRepository: AudioSettingsRepository,
    private val calendarDriftRecoverer: CalendarDriftRecoverer
) : ViewModel() {

    private val _uiState = MutableStateFlow(BootcampUiState())
    val uiState: StateFlow<BootcampUiState> = _uiState.asStateFlow()

    private var welcomeBackDismissed = false
    private var rewindBreadcrumbDismissed = false
    private var illnessPromptSnoozedUntilMs = 0L
    /**
     * Latest gap-adjustment disclosure built by [applyGapAdjustmentIfNeeded].
     * Null when the most recent load found no rewind / tier change worth disclosing.
     * Persists across [refreshFromEnrollment] until the user dismisses both surfaces
     * or completes a session in the new engine-week.
     */
    @Volatile private var _pendingDisclosure: WelcomeBackDisclosure? = null
    @Volatile private var currentEnrollment: BootcampEnrollmentEntity? = null
    @Volatile private var currentTuningDirection: TuningDirection = TuningDirection.HOLD
    private var loadJob: Job? = null
    private var bleCollectJob: Job? = null
    private var scanTimeoutJob: Job? = null

    init {
        loadBootcampState()
    }

    private fun loadBootcampState() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        loadJob = viewModelScope.launch {
            try {
                // Apply gap adjustment once at startup (prevents write-inside-collect re-emission)
                val initial = bootcampRepository.getActiveEnrollmentOnce()
                if (initial != null) {
                    applyGapAdjustmentIfNeeded(initial)
                }

                // Then observe the Flow for ongoing UI updates.
                // Track last-seen enrollment to avoid redundant refreshes from our own writes.
                // Use a boolean guard so the first emission (which may be null) is never skipped.
                var lastSeen: BootcampEnrollmentEntity? = null
                var initialized = false
                bootcampRepository.getActiveEnrollment().collect { enrollment ->
                    if (initialized && enrollment == lastSeen) return@collect
                    initialized = true
                    lastSeen = enrollment
                    if (enrollment == null) {
                        _uiState.value = BootcampUiState(
                            isLoading = false,
                            loadError = null,
                            hasActiveEnrollment = false,
                            isPaused = false
                        )
                    } else {
                        refreshFromEnrollment(enrollment)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = t.message ?: "Failed to load bootcamp data."
                    )
                }
            }
        }
    }

    private suspend fun applyGapAdjustmentIfNeeded(enrollment: BootcampEnrollmentEntity) {
        val lastSession = bootcampRepository.getLastCompletedSession(enrollment.id)
        val daysSinceLastRun = computeDaysSinceLastRun(enrollment, lastSession)
        val gapStrategy = GapAdvisor.assess(daysSinceLastRun)
        val gapAction = GapAdvisor.action(gapStrategy, enrollment.currentPhaseIndex, enrollment.currentWeekInPhase)

        var updatedTierIndex = enrollment.tierIndex

        // CTL-aware tier adjustment for meaningful breaks and worse:
        // After a significant break, CTL decays and the runner's current tier may
        // no longer match their fitness. Auto-demote to prevent overly-hard sessions.
        val profile = adaptiveProfileRepository.getProfile()
        if (gapStrategy >= GapStrategy.MEANINGFUL_BREAK
            && enrollment.tierIndex > TierCtlRanges.minTierIndex
            && profile.ctl > 0f) {  // only demote if runner has actual fitness data (C2: guard against new-user false demotion)
            val projectedLoad = FitnessLoadCalculator.updateLoads(
                currentCtl = profile.ctl,
                currentAtl = profile.atl,
                trimpScore = 0f,
                daysSinceLast = daysSinceLastRun
            )
            val goal = BootcampGoal.valueOf(enrollment.goalType)
            val suggestedTier = TierCtlRanges.suggestedTierForCtl(goal, projectedLoad.ctl)
            if (suggestedTier < enrollment.tierIndex) {
                updatedTierIndex = suggestedTier
                Log.i("BootcampVM", "Gap recovery: CTL decayed to ${projectedLoad.ctl}, " +
                    "tier adjusted ${enrollment.tierIndex} -> $suggestedTier")
            }
        }

        val phaseChanged = gapAction.phaseIndex != enrollment.currentPhaseIndex ||
            gapAction.weekInPhase != enrollment.currentWeekInPhase
        val tierChanged = updatedTierIndex != enrollment.tierIndex

        if (phaseChanged || tierChanged) {
            // C3: Delete stale sessions whether phase or tier changed.
            // When only tier changes, gapAction.phaseIndex/weekInPhase equal the current position,
            // so targetWeek == current week and deleteSessionsAfterWeek(id, currentWeek - 1)
            // removes current-week sessions, forcing regeneration at the new tier.
            val targetWeek = PhaseEngine(
                goal = BootcampGoal.valueOf(enrollment.goalType),
                phaseIndex = gapAction.phaseIndex,
                weekInPhase = gapAction.weekInPhase,
                runsPerWeek = enrollment.runsPerWeek,
                targetMinutes = enrollment.targetMinutesPerRun
            ).absoluteWeek
            val sessionsCleared = bootcampRepository.deleteSessionsAfterWeek(enrollment.id, targetWeek - 1)
            val gapUpdatedEnrollment = enrollment.copy(
                currentPhaseIndex = gapAction.phaseIndex,
                currentWeekInPhase = gapAction.weekInPhase,
                tierIndex = updatedTierIndex,
                lastTierChangeWeek = if (tierChanged) null else enrollment.lastTierChangeWeek  // I3: reset on demotion
            )
            bootcampRepository.updateEnrollment(gapUpdatedEnrollment)
            runCatching { cloudBackupManager.syncBootcampEnrollment(gapUpdatedEnrollment) }
                .onFailure { Log.w("BootcampVM", "Cloud backup failed for gap adjustment", it) }

            // Build the disclosure shown by WelcomeBackDialog + the ambient
            // breadcrumb chip on the week strip. Combines schedule + intensity
            // sections so the override-style "tier message hides rewind" bug
            // can't recur.
            _pendingDisclosure = buildDisclosure(
                oldPhaseIndex = enrollment.currentPhaseIndex,
                oldWeekInPhase = enrollment.currentWeekInPhase,
                newPhaseIndex = gapAction.phaseIndex,
                newWeekInPhase = gapAction.weekInPhase,
                goalType = enrollment.goalType,
                sessionsCleared = sessionsCleared,
                tierEased = tierChanged,
                requiresCalibration = gapAction.requiresCalibration
            )
            // Ambient breadcrumb auto-rearms whenever a fresh disclosure lands.
            rewindBreadcrumbDismissed = false
        }
    }

    private fun buildDisclosure(
        oldPhaseIndex: Int,
        oldWeekInPhase: Int,
        newPhaseIndex: Int,
        newWeekInPhase: Int,
        goalType: String,
        sessionsCleared: Int,
        tierEased: Boolean,
        requiresCalibration: Boolean
    ): WelcomeBackDisclosure {
        val goal = BootcampGoal.valueOf(goalType)
        val newPhaseName = phaseDisplayName(
            PhaseEngine(goal = goal, phaseIndex = newPhaseIndex, weekInPhase = newWeekInPhase,
                runsPerWeek = 1, targetMinutes = 30).currentPhase
        )
        val schedule = when {
            // Phase index decreased — full reset (LONG_ABSENCE / FULL_RESET land here).
            newPhaseIndex < oldPhaseIndex ->
                WelcomeBackDisclosure.ScheduleChange.FullReset(
                    phaseName = newPhaseName,
                    sessionsCleared = sessionsCleared
                )
            // Same phase, week reset to 0 — EXTENDED_BREAK / start-of-phase landing.
            newWeekInPhase == 0 && oldWeekInPhase != 0 ->
                WelcomeBackDisclosure.ScheduleChange.PhaseStartReset(
                    phaseName = newPhaseName,
                    sessionsCleared = sessionsCleared
                )
            // Same phase, week stepped back by one or more — MEANINGFUL_BREAK.
            newWeekInPhase < oldWeekInPhase ->
                WelcomeBackDisclosure.ScheduleChange.WeekRollback(
                    fromWeek = oldWeekInPhase + 1,   // 1-based for display
                    toWeek = newWeekInPhase + 1,
                    phaseName = newPhaseName,
                    sessionsCleared = sessionsCleared
                )
            // No actual rewind — tier-only adjustment at start of phase. Treat
            // as PhaseStartReset for a coherent "you're at the start" framing.
            else ->
                WelcomeBackDisclosure.ScheduleChange.PhaseStartReset(
                    phaseName = newPhaseName,
                    sessionsCleared = sessionsCleared
                )
        }
        // DiscoveryRun (calibration) trumps TierEased — calibration is a
        // genuinely different next-run kind, while tier easing is implied by
        // the discovery run's open-ended nature.
        val intensity = when {
            requiresCalibration -> WelcomeBackDisclosure.IntensityChange.DiscoveryRun
            tierEased -> WelcomeBackDisclosure.IntensityChange.TierEased
            else -> null
        }
        return WelcomeBackDisclosure(schedule = schedule, intensity = intensity)
    }

    private fun phaseDisplayName(phase: TrainingPhase): String =
        phase.name.lowercase().replaceFirstChar { it.titlecase(Locale.ROOT) }

    private suspend fun refreshFromEnrollment(initialEnrollment: BootcampEnrollmentEntity) {
        val goal = BootcampGoal.valueOf(initialEnrollment.goalType)

        // ── Calendar drift recovery (runs before any state reads) ────────────
        // If the engine fell behind the calendar (residual SCHEDULED in a past
        // engine-week), self-heal: skip residuals, advance the engine, seed the
        // next week. No-ops when a workout is in flight or no completions exist
        // in the engine-week (GapAdvisor's territory).
        val initialEngine = PhaseEngine(
            goal = goal,
            phaseIndex = initialEnrollment.currentPhaseIndex,
            weekInPhase = initialEnrollment.currentWeekInPhase,
            runsPerWeek = initialEnrollment.runsPerWeek,
            targetMinutes = initialEnrollment.targetMinutesPerRun
        )
        val workoutSnapshot = WorkoutState.snapshot.value
        val systemZone = ZoneId.systemDefault()
        val recoveryOutcome = calendarDriftRecoverer.recover(
            enrollment = initialEnrollment,
            engine = initialEngine,
            today = LocalDate.now(systemZone),
            zone = systemZone,
            isWorkoutActive = workoutSnapshot.isRunning,
            pendingSessionId = workoutSnapshot.pendingBootcampSessionId
        )
        val enrollment = (recoveryOutcome as? CalendarDriftRecoverer.Outcome.Recovered)
            ?.finalEnrollment ?: initialEnrollment
        val engine = (recoveryOutcome as? CalendarDriftRecoverer.Outcome.Recovered)
            ?.finalEngine ?: initialEngine

        val profile = adaptiveProfileRepository.getProfile()
        val recentMetrics = workoutMetricsRepository.getRecentMetrics(limitDays = RECENT_METRICS_DAYS)
        val enrollmentForPrompt = clearTierPromptSnoozeIfCtlSafe(goal, enrollment, profile.ctl)

        val fitnessSignals = FitnessSignalEvaluator.evaluate(profile, recentMetrics)
        val fitnessLevel = FitnessEvaluator.assess(profile, recentMetrics)
        currentEnrollment = enrollment
        currentTuningDirection = fitnessSignals.tuningDirection
        val illnessFlag = fitnessSignals.illnessFlag && System.currentTimeMillis() >= illnessPromptSnoozedUntilMs
        val tierPrompt = resolveTierPromptDirection(
            goal = goal,
            profileCtl = profile.ctl,
            profileAtl = profile.atl,
            totalSessions = profile.totalSessions,
            enrollment = enrollmentForPrompt,
            recentMetrics = recentMetrics,
            currentPhase = engine.currentPhase
        )
        val tierPromptDirection = if (illnessFlag) TierPromptDirection.NONE else tierPrompt.direction
        val tierPromptEvidence = if (illnessFlag) null else tierPrompt.evidence

        val lastSession = bootcampRepository.getLastCompletedSession(enrollment.id)
        val daysSinceLastRun = computeDaysSinceLastRun(enrollment, lastSession)
        val gapStrategy = GapAdvisor.assess(daysSinceLastRun)
        val gapAction = GapAdvisor.action(gapStrategy, enrollment.currentPhaseIndex, enrollment.currentWeekInPhase)

        val displayEngine = engine

        val today = LocalDate.now().dayOfWeek.value
        val preferredDays = enrollment.preferredDays
        val activePreferredDays = preferredDays.filter { it.level != com.hrcoach.domain.bootcamp.DaySelectionLevel.NONE }
        val scheduledSessions = ensureCurrentWeekSessions(
            enrollment = enrollment,
            engine = displayEngine,
            preferredDays = preferredDays,
            tuningDirection = fitnessSignals.tuningDirection
        )

        // Next incomplete session (may be in a future week via repository lookahead)
        val nextScheduledSession = scheduledSessions.firstOrNull {
            it.status == BootcampSessionEntity.STATUS_SCHEDULED
        }

        // Collect all sessions needing attention: missed (past + still SCHEDULED) and DEFERRED
        val missedOrDeferredSessions = scheduledSessions.filter {
            (it.dayOfWeek < today && it.status == BootcampSessionEntity.STATUS_SCHEDULED) ||
                it.status == BootcampSessionEntity.STATUS_DEFERRED
        }
        val missedSessionCount = missedOrDeferredSessions.size
        val missedSessionIds = missedOrDeferredSessions.map { it.id }

        // Build 7-day strip items: one WeekDayItem per day M–S.
        // Header is calendar-driven; CalendarDriftRecoverer keeps the engine in
        // sync with the calendar so the sessions plotted here are for the right
        // week. (A formula-based clamp was considered but doesn't generalize —
        // weekNumber → calendar week isn't a clean function for users with
        // pre-enrollment sessions or who pull sessions forward via Reschedule.)
        val weekStart = LocalDate.now().with(DayOfWeek.MONDAY)
        val weekDays = (1..7).map { dow ->
            val session = scheduledSessions.find { it.dayOfWeek == dow }
            WeekDayItem(
                dayOfWeek = dow,
                dayLabel = DayOfWeek.of(dow)
                    .getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                isToday = dow == today,
                session = session?.let {
                    SessionUiItem(
                        dayLabel = dayLabelFor(it.dayOfWeek),
                        typeName = sessionTypeDisplayName(it.sessionType, it.presetId),
                        rawTypeName = it.sessionType,
                        minutes = it.targetMinutes,
                        isCompleted = it.status == BootcampSessionEntity.STATUS_COMPLETED ||
                            it.status == BootcampSessionEntity.STATUS_SKIPPED,
                        isToday = it.dayOfWeek == today,
                        isPast = it.dayOfWeek < today,
                        isDeferred = it.status == BootcampSessionEntity.STATUS_DEFERRED,
                        sessionId = it.id,
                        presetId = it.presetId
                    )
                }
            )
        }

        // Compute today's context state
        val dayKind = computeDayKind(scheduledSessions, today)
        val nextDayLabel = nextScheduledSession?.let { dayLabelFor(it.dayOfWeek) }
        val nextRelLabel = nextScheduledSession?.let {
            computeRelativeLabel(it.dayOfWeek, today)
        }
        // Find the next future session (after today) for pull-forward CTA
        val nextFutureSessionId = scheduledSessions.firstOrNull {
            it.dayOfWeek > today && it.status == BootcampSessionEntity.STATUS_SCHEDULED
        }?.id
        val todayState: TodayState = when (dayKind) {
            DayKind.RUN_UPCOMING -> TodayState.RunUpcoming(
                session = scheduledSessions.first { it.dayOfWeek == today }.toPlannedSession()
            )
            DayKind.RUN_DONE -> TodayState.RunDone(
                nextSession = nextScheduledSession?.toPlannedSession(),
                nextSessionDayLabel = nextDayLabel,
                nextSessionRelativeLabel = nextRelLabel,
                nextFutureSessionId = nextFutureSessionId
            )
            DayKind.REST -> TodayState.RestDay(
                nextSession = nextScheduledSession?.toPlannedSession(),
                nextSessionDayLabel = nextDayLabel,
                nextSessionRelativeLabel = nextRelLabel,
                nextFutureSessionId = nextFutureSessionId
            )
        }

        val upcomingWeeks = displayEngine.lookaheadWeeks(
            count = 2,
            tierIndex = enrollment.tierIndex,
            tuningDirection = fitnessSignals.tuningDirection,
            lastTierChangeWeek = enrollment.lastTierChangeWeek
        ).map { lookahead ->
            UpcomingWeekItem(
                weekNumber = lookahead.weekNumber,
                isRecoveryWeek = lookahead.isRecovery,
                sessions = lookahead.sessions.map { session ->
                    SessionUiItem(
                        dayLabel = "",
                        typeName = sessionTypeDisplayName(session.type.name, session.presetId),
                        rawTypeName = session.type.name,
                        minutes = session.minutes,
                        isCompleted = false,
                        isToday = false,
                        presetId = session.presetId
                    )
                }
            )
        }

        // ── Upcoming runs list (next ~4 future sessions, rest days excluded) ──
        // Combines this week's remaining scheduled sessions (which already have a day
        // from the DB) with next week's planned sessions (assigned to days via
        // SessionDayAssigner using the user's preferred days).
        val upcomingRuns: List<UpcomingRunItem> = run {
            val limit = 4
            val out = mutableListOf<UpcomingRunItem>()

            // 1) This week's remaining scheduled sessions
            scheduledSessions
                .filter { it.dayOfWeek > today && it.status == BootcampSessionEntity.STATUS_SCHEDULED }
                .sortedBy { it.dayOfWeek }
                .forEach { s ->
                    if (out.size >= limit) return@forEach
                    val date = weekStart.plusDays((s.dayOfWeek - 1).toLong())
                    out.add(
                        UpcomingRunItem(
                            sessionId = s.id,
                            dayLabel = DayOfWeek.of(s.dayOfWeek)
                                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                .uppercase(Locale.getDefault()),
                            dayOfMonth = date.dayOfMonth,
                            title = sessionTypeDisplayName(s.sessionType, s.presetId),
                            subtitle = buildUpcomingSubtitle(s.targetMinutes, s.sessionType),
                            zoneTag = ZoneEducationProvider.zoneTag(s.sessionType),
                            rawSessionType = s.sessionType
                        )
                    )
                }

            // 2) Next week's planned sessions (assigned days via the same heuristic the
            //    real scheduler uses — preferred days + hard-effort spacing)
            if (out.size < limit && upcomingWeeks.isNotEmpty()) {
                val nextLookahead = displayEngine.lookaheadWeeks(
                    count = 1,
                    tierIndex = enrollment.tierIndex,
                    tuningDirection = fitnessSignals.tuningDirection,
                    lastTierChangeWeek = enrollment.lastTierChangeWeek
                ).firstOrNull()
                if (nextLookahead != null) {
                    val availableDays = activePreferredDays.map { it.day }
                    val longBias = preferredDays.firstOrNull { it.level == DaySelectionLevel.LONG_RUN_BIAS }?.day
                    val assignments = SessionDayAssigner.assign(
                        sessions = nextLookahead.sessions,
                        availableDays = availableDays,
                        longRunBiasDay = longBias
                    ).sortedBy { it.second }
                    val nextWeekStart = weekStart.plusDays(7)
                    assignments.forEach { (planned, dow) ->
                        if (out.size >= limit) return@forEach
                        val date = nextWeekStart.plusDays((dow - 1).toLong())
                        out.add(
                            UpcomingRunItem(
                                sessionId = null,
                                dayLabel = DayOfWeek.of(dow)
                                    .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                                    .uppercase(Locale.getDefault()),
                                dayOfMonth = date.dayOfMonth,
                                title = sessionTypeDisplayName(planned.type.name, planned.presetId),
                                subtitle = buildUpcomingSubtitle(planned.minutes, planned.type.name),
                                zoneTag = ZoneEducationProvider.zoneTag(planned.type.name),
                                rawSessionType = planned.type.name
                            )
                        )
                    }
                }
            }
            out
        }

        val progressPercentage = if (displayEngine.totalWeeks > 0) {
            (displayEngine.absoluteWeek.toFloat() / displayEngine.totalWeeks * 100).toInt().coerceIn(0, 100)
        } else 0

        // ── Strides primer gating ────────────────────────────────────────────
        // Show the primer only once, on the first time the user lands on a
        // bootcamp screen where the next scheduled session is a strides
        // session. Resolves the "next" session as: today's session if there
        // is one upcoming today, otherwise the next future scheduled session.
        val upcomingSession: BootcampSessionEntity? = when (todayState) {
            is TodayState.RunUpcoming ->
                scheduledSessions.firstOrNull { it.dayOfWeek == today }
            else -> nextScheduledSession
        }
        val isStridesUpcoming = upcomingSession?.isStridesSession() == true
        val stridesPrimerSeen = audioSettingsRepository.getAudioSettings().stridesPrimerSeen
        val showStridesPrimer = isStridesUpcoming && !stridesPrimerSeen
        val stridesPrimerTotalReps = upcomingSession
            ?.let { StridesController.repsForDuration(it.targetMinutes) }
            ?: 5

        _uiState.value = BootcampUiState(
            isLoading = false,
            loadError = null,
            hasActiveEnrollment = true,
            isPaused = enrollment.status == BootcampEnrollmentEntity.STATUS_PAUSED,
            goal = goal,
            currentPhase = displayEngine.currentPhase,
            absoluteWeek = displayEngine.absoluteWeek,
            totalWeeks = displayEngine.totalWeeks,
            weekInPhase = displayEngine.weekInPhase,
            isRecoveryWeek = displayEngine.isRecoveryWeek(fitnessSignals.tuningDirection),
            weeksUntilNextRecovery = displayEngine.weeksUntilNextRecovery(fitnessSignals.tuningDirection),
            showGraduationCta = displayEngine.absoluteWeek >= displayEngine.totalWeeks,
            currentWeekDays = weekDays,
            currentWeekDateRange = computeWeekDateRange(weekStart),
            todayState = todayState,
            activePreferredDays = activePreferredDays,
            upcomingWeeks = upcomingWeeks,
            upcomingRuns = upcomingRuns,
            welcomeBackDisclosure = if (welcomeBackDismissed) null else _pendingDisclosure,
            showRewindBreadcrumb = computeShowRewindBreadcrumb(scheduledSessions, today),
            needsCalibration = gapAction.requiresCalibration,
            fitnessLevel = fitnessLevel,
            tuningDirection = fitnessSignals.tuningDirection,
            illnessFlag = illnessFlag,
            tierIndex = enrollment.tierIndex,
            ctl = profile.ctl,
            tierPromptDirection = tierPromptDirection,
            tierPromptEvidence = tierPromptEvidence,
            missedSessionCount = missedSessionCount,
            missedSessionIds = missedSessionIds,
            swapRestMessage = null,
            goalProgressPercentage = progressPercentage,
            maxHr = userProfileRepository.getMaxHr(),
            showStridesPrimer = showStridesPrimer,
            stridesPrimerTotalReps = stridesPrimerTotalReps
        )
    }

    /**
     * Show the breadcrumb chip while a disclosure is pending AND no session in
     * the current engine-week has been completed yet. Auto-dismiss when the
     * runner completes their first session in the new week — at that point the
     * causal story has been replaced by visible progress.
     */
    private fun computeShowRewindBreadcrumb(
        scheduledSessions: List<BootcampSessionEntity>,
        today: Int
    ): Boolean {
        if (rewindBreadcrumbDismissed) return false
        if (_pendingDisclosure == null) return false
        val anyCompletedThisWeek = scheduledSessions.any {
            it.status == BootcampSessionEntity.STATUS_COMPLETED
        }
        return !anyCompletedThisWeek
    }

    private fun computeDaysSinceLastRun(
        enrollment: BootcampEnrollmentEntity,
        lastSession: BootcampSessionEntity?
    ): Int {
        val now = System.currentTimeMillis()
        if (lastSession?.completedWorkoutId == null) {
            // No completed session — compute days since enrollment start
            return ((now - enrollment.startDate) / TimeUnit.DAYS.toMillis(1)).toInt().coerceAtLeast(0)
        }
        val lastDate = lastSession.completedAtMs ?: run {
            // Fallback: compute date from enrollment week Monday + ISO dayOfWeek
            val enrollStart = Instant.ofEpochMilli(enrollment.startDate)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            val enrollWeekMonday = enrollStart.with(DayOfWeek.MONDAY)
            val sessionDate = enrollWeekMonday
                .plusWeeks((lastSession.weekNumber - 1).toLong())
                .plusDays((lastSession.dayOfWeek - 1).toLong())
            sessionDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        return ((now - lastDate) / TimeUnit.DAYS.toMillis(1)).toInt().coerceAtLeast(0)
    }

    // --- Carousel & Onboarding ---

    fun startCarousel() {
        _uiState.update { it.copy(showCarousel = true) }
    }

    fun dismissCarousel() {
        _uiState.update { it.copy(showCarousel = false, showOnboarding = true, onboardingStep = 0) }
    }

    fun startOnboarding() {
        _uiState.update { it.copy(showCarousel = true) }
    }

    fun setOnboardingStep(step: Int) {
        _uiState.update { it.copy(onboardingStep = step) }
    }

    private fun computeOnboardingDurationState(
        minutes: Int,
        runsPerWeek: Int,
        goal: BootcampGoal?
    ): Triple<Int, Int, String?> {
        val durations = DurationScaler.compute(runsPerWeek, minutes)
        val easyRuns = if (runsPerWeek >= 3) runsPerWeek - 1 else runsPerWeek
        val weeklyTotal = durations.easyMinutes * easyRuns + durations.longMinutes
        val longRunWarning = if (goal != null && durations.longMinutes < goal.minLongRunMinutes) {
            "Your long run (~${durations.longMinutes} min) is shorter than recommended for " +
                "${goal.name.replace('_', ' ')} training (${goal.minLongRunMinutes} min). " +
                "Consider increasing your run length or adding a day."
        } else null
        return Triple(durations.longMinutes, weeklyTotal, longRunWarning)
    }

    fun setOnboardingGoal(goal: BootcampGoal) {
        val state = _uiState.value
        val finishingTime = FinishingTimeTierMapper.bracketsFor(goal)?.defaultMinutes
        val derivedTier = if (finishingTime != null) FinishingTimeTierMapper.tierFromFinishingTime(goal, finishingTime) else 0
        val recommendedMin = FinishingTimeTierMapper.recommendedRunMinutes(goal, derivedTier)
        val minutes = if (FinishingTimeTierMapper.isRaceGoal(goal)) recommendedMin else state.onboardingAvailableMinutes
        val validation = FinishingTimeTierMapper.validateTimeCommitment(goal, derivedTier, minutes)
        val (longRun, weekly, longWarning) = computeOnboardingDurationState(
            minutes, state.onboardingRunsPerWeek, goal
        )
        _uiState.update {
            it.copy(
                onboardingGoal = goal,
                onboardingTargetFinishingTime = finishingTime,
                onboardingAvailableMinutes = minutes,
                onboardingTimeWarning = validation.warningMessage,
                onboardingTimeCanProceed = validation.canProceed,
                onboardingLongRunMinutes = longRun,
                onboardingWeeklyTotal = weekly,
                onboardingLongRunWarning = longWarning,
                onboardingPreferredDays = if (it.onboardingPreferredDays.isEmpty())
                    defaultPreferredDays(it.onboardingRunsPerWeek) else it.onboardingPreferredDays
            )
        }
    }

    fun setOnboardingMinutes(minutes: Int) {
        val goal = _uiState.value.onboardingGoal
        val finishingTime = _uiState.value.onboardingTargetFinishingTime
        val tierIndex = if (goal != null && finishingTime != null)
            FinishingTimeTierMapper.tierFromFinishingTime(goal, finishingTime) else 0
        val validation = if (goal != null)
            FinishingTimeTierMapper.validateTimeCommitment(goal, tierIndex, minutes) else null
        val warning = validation?.warningMessage
            ?: if (goal != null && minutes < goal.warnBelowMinutes) {
                "${goal.name.replace('_', ' ')} training typically needs at least ${goal.suggestedMinMinutes} min per session."
            } else null
        val (longRun, weekly, longWarning) = computeOnboardingDurationState(
            minutes, _uiState.value.onboardingRunsPerWeek, goal
        )
        _uiState.update {
            it.copy(
                onboardingAvailableMinutes = minutes,
                onboardingTimeWarning = warning,
                onboardingTimeCanProceed = validation?.canProceed ?: true,
                onboardingLongRunMinutes = longRun,
                onboardingWeeklyTotal = weekly,
                onboardingLongRunWarning = longWarning
            )
        }
    }

    fun setOnboardingFinishingTime(minutes: Int) {
        val goal = _uiState.value.onboardingGoal ?: return
        val tierIndex = FinishingTimeTierMapper.tierFromFinishingTime(goal, minutes)
        val recommendedMin = FinishingTimeTierMapper.recommendedRunMinutes(goal, tierIndex)
        val validation = FinishingTimeTierMapper.validateTimeCommitment(goal, tierIndex, recommendedMin)
        val (longRun, weekly, longWarning) = computeOnboardingDurationState(
            recommendedMin, _uiState.value.onboardingRunsPerWeek, goal
        )
        _uiState.update {
            it.copy(
                onboardingTargetFinishingTime = minutes,
                onboardingAvailableMinutes = recommendedMin,
                onboardingTimeWarning = validation.warningMessage,
                onboardingTimeCanProceed = validation.canProceed,
                onboardingLongRunMinutes = longRun,
                onboardingWeeklyTotal = weekly,
                onboardingLongRunWarning = longWarning
            )
        }
    }

    fun setOnboardingRunsPerWeek(runs: Int) {
        val state = _uiState.value
        val (longRun, weekly, longWarning) = computeOnboardingDurationState(
            state.onboardingAvailableMinutes, runs, state.onboardingGoal
        )
        _uiState.update {
            it.copy(
                onboardingRunsPerWeek = runs,
                onboardingPreferredDays = defaultPreferredDays(runs),
                onboardingLongRunMinutes = longRun,
                onboardingWeeklyTotal = weekly,
                onboardingLongRunWarning = longWarning
            )
        }
    }

    fun cycleOnboardingDayPreference(day: Int) {
        _uiState.update { state ->
            val current = state.onboardingPreferredDays.toMutableList()
            val existingIndex = current.indexOfFirst { it.day == day }

            if (existingIndex != -1) {
                val nextLevel = current[existingIndex].level.next()
                if (nextLevel == DaySelectionLevel.NONE) {
                    current.removeAt(existingIndex)
                } else {
                    current[existingIndex] = current[existingIndex].copy(level = nextLevel)
                }
            } else {
                current.add(DayPreference(day, DaySelectionLevel.AVAILABLE))
            }

            state.copy(onboardingPreferredDays = current.sortedBy { it.day })
        }
    }

    fun toggleOnboardingBlackoutDay(day: Int) {
        _uiState.update { state ->
            val current = state.onboardingPreferredDays.toMutableList()
            val existing = current.indexOfFirst { it.day == day }
            if (existing != -1 && current[existing].level == DaySelectionLevel.BLACKOUT) {
                current.removeAt(existing)
            } else {
                if (existing != -1) current.removeAt(existing)
                current.add(DayPreference(day, DaySelectionLevel.BLACKOUT))
            }
            state.copy(onboardingPreferredDays = current.sortedBy { it.day })
        }
    }

    fun onBootcampWorkoutStarting() {
        // Fallback: if prepareStartWorkout didn't already set the pending ID
        // (e.g. code path bypassing the normal flow), pick the next scheduled session.
        if (WorkoutState.snapshot.value.pendingBootcampSessionId != null) return
        val nextScheduled = _uiState.value.currentWeekDays
            .mapNotNull { it.session }
            .firstOrNull { it.sessionId != null && !it.isCompleted }
        WorkoutState.setPendingBootcampSessionId(nextScheduled?.sessionId)
    }

    fun completeOnboarding() {
        val state = _uiState.value
        val goal = state.onboardingGoal ?: return
        val preferredDays = state.onboardingPreferredDays.ifEmpty {
            defaultPreferredDays(state.onboardingRunsPerWeek)
        }
        viewModelScope.launch {
            val startDate = firstPreferredDayAfterMs(preferredDays.map { it.day })
            bootcampRepository.createEnrollment(
                goal = goal,
                targetMinutesPerRun = state.onboardingAvailableMinutes,
                runsPerWeek = state.onboardingRunsPerWeek,
                preferredDays = preferredDays,
                startDate = startDate,
                targetFinishingTimeMinutes = state.onboardingTargetFinishingTime
            )
            // Cloud backup: sync the newly created enrollment
            runCatching {
                val enrollment = bootcampRepository.getActiveEnrollmentOnce()
                if (enrollment != null) cloudBackupManager.syncBootcampEnrollment(enrollment)
            }.onFailure { Log.w("BootcampVM", "Cloud backup failed for enrollment", it) }
            // Session seeding is handled by refreshFromEnrollment() which fires
            // automatically when the Flow re-emits after createEnrollment writes.
            _uiState.update { it.copy(showOnboarding = false) }
        }
    }

    private fun defaultPreferredDays(runsPerWeek: Int): List<DayPreference> {
        val days = when (runsPerWeek) {
            2 -> listOf(2, 5)              // Tue, Fri
            3 -> listOf(1, 3, 6)           // Mon, Wed, Sat
            4 -> listOf(1, 3, 5, 7)        // Mon, Wed, Fri, Sun
            else -> listOf(1, 2, 4, 6, 7)  // Mon, Tue, Thu, Sat, Sun (5+)
        }
        return days.mapIndexed { i, day ->
            DayPreference(day, if (i == days.lastIndex) DaySelectionLevel.LONG_RUN_BIAS else DaySelectionLevel.AVAILABLE)
        }
    }

    fun pauseBootcamp() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            bootcampRepository.pauseEnrollment(enrollment.id)
        }
    }

    fun resumeBootcamp() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            bootcampRepository.resumeEnrollment(enrollment.id)
        }
    }

    fun showDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = true) }
    }

    fun onSessionClick(session: SessionUiItem) {
        _uiState.update { it.copy(showSessionDetail = true, sessionDetailItem = session) }
    }

    fun dismissSessionDetail() {
        _uiState.update { it.copy(showSessionDetail = false, sessionDetailItem = null) }
    }

    fun skipSession(sessionId: Long) {
        viewModelScope.launch {
            bootcampRepository.dropSession(sessionId)
            dismissSessionDetail()
            loadBootcampState()
        }
    }

    fun rescheduleFromDetail(sessionId: Long) {
        dismissSessionDetail()
        requestReschedule(sessionId)
    }

    fun startRunFromDetail(configJson: String) {
        dismissSessionDetail()
        showHrConnectDialog(configJson)
    }

    fun swapTodayForRestFromDetail() {
        dismissSessionDetail()
        swapTodayForRest()
    }

    fun clearSwapRestMessage() {
        _uiState.update { it.copy(swapRestMessage = null) }
    }

    fun showGoalDetail() {
        _uiState.update { it.copy(showGoalDetail = true) }
    }

    fun dismissGoalDetail() {
        _uiState.update { it.copy(showGoalDetail = false) }
    }

    fun showTierDetail() {
        _uiState.update { it.copy(showTierDetail = true) }
    }

    fun dismissTierDetail() {
        _uiState.update { it.copy(showTierDetail = false) }
    }

    fun dismissDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
    }

    fun deleteBootcamp() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            notificationManager.cancelAll(enrollment.id)
            WorkoutState.setPendingBootcampSessionId(null)
            bootcampRepository.deleteEnrollment(enrollment.id)
            _uiState.update { it.copy(showDeleteConfirmDialog = false) }
        }
    }

    fun dismissWelcomeBack() {
        welcomeBackDismissed = true
        _uiState.update { it.copy(welcomeBackDisclosure = null) }
    }

    fun dismissRewindBreadcrumb() {
        rewindBreadcrumbDismissed = true
        _uiState.update { it.copy(showRewindBreadcrumb = false) }
    }

    fun dismissTierPrompt() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            val nextDismissCount = enrollment.tierPromptDismissCount + 1
            val snoozeWeeks = TierCtlRanges.snoozeWeeksForDismissCount(nextDismissCount)
            val snoozedUntilMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(snoozeWeeks.toLong() * 7L)
            val updated = enrollment.copy(
                tierPromptDismissCount = nextDismissCount,
                tierPromptSnoozedUntilMs = snoozedUntilMs
            )
            bootcampRepository.updateEnrollment(updated)
            runCatching { cloudBackupManager.syncBootcampEnrollment(updated) }
            _uiState.update {
                it.copy(
                    tierPromptDirection = TierPromptDirection.NONE,
                    tierPromptEvidence = null
                )
            }
        }
    }

    fun acceptTierChange(direction: TierPromptDirection) {
        if (direction == TierPromptDirection.NONE) return
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            val delta = when (direction) {
                TierPromptDirection.UP -> 1
                TierPromptDirection.DOWN -> -1
                TierPromptDirection.NONE -> 0
            }
            val updatedTierIndex = (enrollment.tierIndex + delta)
                .coerceIn(TierCtlRanges.minTierIndex, TierCtlRanges.maxTierIndex)

            // Adjust finishing time to stay consistent with the new tier
            val goal = BootcampGoal.valueOf(enrollment.goalType)
            val updatedFinishingTime = if (enrollment.targetFinishingTimeMinutes != null &&
                FinishingTimeTierMapper.isRaceGoal(goal)
            ) {
                val brackets = FinishingTimeTierMapper.bracketsFor(goal)
                if (brackets != null) {
                    val midpoint = when (updatedTierIndex) {
                        0 -> (brackets.easyAboveMinutes + brackets.uiMax) / 2
                        2 -> (brackets.uiMin + brackets.hardBelowMinutes) / 2
                        else -> (brackets.easyAboveMinutes + brackets.hardBelowMinutes) / 2
                    }
                    midpoint
                } else enrollment.targetFinishingTimeMinutes
            } else enrollment.targetFinishingTimeMinutes

            // Track when the tier changed so SessionSelector can use a transition preset
            val engine = PhaseEngine(
                goal = goal,
                phaseIndex = enrollment.currentPhaseIndex,
                weekInPhase = enrollment.currentWeekInPhase,
                runsPerWeek = enrollment.runsPerWeek,
                targetMinutes = enrollment.targetMinutesPerRun
            )
            val updatedEnrollment = enrollment.copy(
                tierIndex = updatedTierIndex,
                targetFinishingTimeMinutes = updatedFinishingTime,
                tierPromptDismissCount = 0,
                tierPromptSnoozedUntilMs = 0L,
                lastTierChangeWeek = if (direction == TierPromptDirection.UP) engine.absoluteWeek else enrollment.lastTierChangeWeek
            )
            bootcampRepository.updateEnrollment(updatedEnrollment)
            runCatching { cloudBackupManager.syncBootcampEnrollment(updatedEnrollment) }
                .onFailure { Log.w("BootcampVM", "Cloud backup failed for tier change", it) }
            if (direction == TierPromptDirection.UP) {
                achievementEvaluator.evaluateTierGraduation(
                    newTierIndex = updatedTierIndex,
                    goal = enrollment.goalType
                )
            }
            _uiState.update {
                it.copy(
                    tierPromptDirection = TierPromptDirection.NONE,
                    tierPromptEvidence = null
                )
            }
        }
    }

    fun retryLoad() {
        loadBootcampState()
    }

    fun confirmIllness() {
        illnessPromptSnoozedUntilMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(ILLNESS_CONFIRM_SNOOZE_DAYS)
        _uiState.update {
            it.copy(
                illnessFlag = false,
                tuningDirection = TuningDirection.EASE_BACK
            )
        }
    }

    fun dismissIllness() {
        illnessPromptSnoozedUntilMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(ILLNESS_DISMISS_SNOOZE_DAYS)
        _uiState.update { it.copy(illnessFlag = false) }
    }

    fun swapTodayForRest() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            val session = bootcampRepository.getNextScheduledSession(enrollment.id) ?: return@launch
            if (session.dayOfWeek != LocalDate.now().dayOfWeek.value) return@launch
            val sessionId = session.id
            bootcampRepository.swapSessionToRestDay(sessionId)
            refreshFromEnrollment(enrollment)
            _uiState.update {
                it.copy(swapRestMessage = "Rest day saved. Today's run was swapped out.")
            }
        }
    }

    fun requestReschedule(sessionId: Long) {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            val sessions = bootcampRepository.getSessionsForWeek(enrollment.id, _uiState.value.absoluteWeek)
            val session = sessions.find { it.id == sessionId } ?: return@launch
            val today = LocalDate.now().dayOfWeek.value
            val occupied = sessions.map { it.dayOfWeek }.toSet()
            val req = RescheduleRequest(
                session = session,
                enrollment = enrollment,
                todayDayOfWeek = today,
                occupiedDaysThisWeek = occupied,
                allSessionsThisWeek = sessions
            )
            val suggestions = SessionRescheduler.suggestions(req)
            val firstFreeDay = suggestions.firstOrNull { it.reason == SuggestionReason.FREE }?.dayOfWeek

            val ui = suggestions.map { s ->
                RescheduleDayUi(
                    day = s.dayOfWeek,
                    label = dayLabelFor(s.dayOfWeek),
                    reason = s.reason.toUi(),
                    isRecommended = s.dayOfWeek == firstFreeDay
                )
            }

            _uiState.update {
                it.copy(
                    rescheduleSheetSessionId = sessionId,
                    rescheduleSessionTypeLabel = sessionTypeDisplayName(session.sessionType, session.presetId),
                    rescheduleAutoTargetDay = firstFreeDay,
                    rescheduleAutoTargetLabel = firstFreeDay?.let(::dayLabelFor),
                    rescheduleSuggestions = ui,
                    rescheduleConfirmDay = null,
                    rescheduleConfirmDayLabel = null,
                    rescheduleConfirmReason = null
                )
            }
        }
    }

    /** Tap on a chip in the reschedule strip. FREE → confirm immediately; OCCUPIED → no-op
     *  defensive guard (chip is disabled in UI); BLACKOUT/RECOVERY → open advisory dialog. */
    fun onRescheduleChipTapped(day: Int) {
        val state = _uiState.value
        val suggestion = state.rescheduleSuggestions.firstOrNull { it.day == day } ?: return
        when (suggestion.reason) {
            RescheduleReasonUi.FREE -> performReschedule(day)
            RescheduleReasonUi.OCCUPIED -> Unit
            RescheduleReasonUi.BLACKOUT, RescheduleReasonUi.RECOVERY -> {
                _uiState.update {
                    it.copy(
                        rescheduleConfirmDay = day,
                        rescheduleConfirmDayLabel = suggestion.label,
                        rescheduleConfirmReason = suggestion.reason
                    )
                }
            }
        }
    }

    /** Confirm-button on the advisory dialog. */
    fun confirmAdvisoryReschedule() {
        val day = _uiState.value.rescheduleConfirmDay ?: return
        performReschedule(day)
    }

    /** Cancel-button on the advisory dialog — closes dialog only, leaves chip strip open. */
    fun dismissAdvisoryConfirm() {
        _uiState.update {
            it.copy(
                rescheduleConfirmDay = null,
                rescheduleConfirmDayLabel = null,
                rescheduleConfirmReason = null
            )
        }
    }

    /** Tap "Sounds good" on the recommendation callout. */
    fun confirmRecommendedReschedule() {
        val day = _uiState.value.rescheduleAutoTargetDay ?: return
        performReschedule(day)
    }

    private fun performReschedule(day: Int) {
        val sessionId = _uiState.value.rescheduleSheetSessionId ?: return
        viewModelScope.launch {
            bootcampRepository.rescheduleSession(sessionId, day)
            clearRescheduleSheet()
            loadBootcampState()
        }
    }

    fun deferReschedule() {
        val sessionId = _uiState.value.rescheduleSheetSessionId ?: return
        viewModelScope.launch {
            bootcampRepository.deferSession(sessionId)
            clearRescheduleSheet()
            loadBootcampState()
        }
    }

    fun dismissRescheduleSheet() = clearRescheduleSheet()

    private fun clearRescheduleSheet() {
        _uiState.update {
            it.copy(
                rescheduleSheetSessionId = null,
                rescheduleSessionTypeLabel = null,
                rescheduleAutoTargetDay = null,
                rescheduleAutoTargetLabel = null,
                rescheduleSuggestions = emptyList(),
                rescheduleConfirmDay = null,
                rescheduleConfirmDayLabel = null,
                rescheduleConfirmReason = null
            )
        }
    }

    private fun SuggestionReason.toUi(): RescheduleReasonUi = when (this) {
        SuggestionReason.FREE             -> RescheduleReasonUi.FREE
        SuggestionReason.OCCUPIED         -> RescheduleReasonUi.OCCUPIED
        SuggestionReason.RECOVERY_SPACING -> RescheduleReasonUi.RECOVERY
        SuggestionReason.BLACKOUT         -> RescheduleReasonUi.BLACKOUT
    }

    fun graduateCurrentGoal() {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            bootcampRepository.graduateEnrollment(enrollment.id)
            runCatching { cloudBackupManager.syncBootcampEnrollment(enrollment.copy(status = BootcampEnrollmentEntity.STATUS_GRADUATED)) }
                .onFailure { Log.w("BootcampVM", "Cloud backup failed for graduation", it) }
            achievementEvaluator.evaluateBootcampGraduation(
                enrollmentId = enrollment.id,
                goal = enrollment.goalType,
                tierIndex = enrollment.tierIndex
            )
        }
    }

    fun savePreferredDays(days: List<com.hrcoach.domain.bootcamp.DayPreference>) {
        viewModelScope.launch {
            val enrollment = bootcampRepository.getActiveEnrollmentOnce() ?: return@launch
            bootcampRepository.updatePreferredDays(enrollment.id, days, _uiState.value.absoluteWeek)
        }
    }

    suspend fun onWorkoutCompleted(workoutId: Long): Boolean {
        val pendingSessionId = WorkoutState.snapshot.value.pendingBootcampSessionId ?: return false
        val result = bootcampSessionCompleter.complete(
            workoutId = workoutId,
            pendingSessionId = pendingSessionId,
            tuningDirection = _uiState.value.tuningDirection
        )
        if (result.completed) {
            WorkoutState.setPendingBootcampSessionId(null)
        }
        return result.completed
    }

    private suspend fun ensureCurrentWeekSessions(
        enrollment: BootcampEnrollmentEntity,
        engine: PhaseEngine,
        preferredDays: List<com.hrcoach.domain.bootcamp.DayPreference>,
        tuningDirection: TuningDirection = TuningDirection.HOLD
    ): List<BootcampSessionEntity> {
        val weekNumber = engine.absoluteWeek
        val existing = bootcampRepository.getSessionsForWeek(enrollment.id, weekNumber)
        if (existing.isNotEmpty()) return existing

        val currentPresetIndices = loadPresetIndicesForWeek(enrollment.id, weekNumber)
        val plannedSessions = engine.planCurrentWeek(
            tierIndex = enrollment.tierIndex,
            tuningDirection = tuningDirection,
            currentPresetIndices = currentPresetIndices,
            lastTierChangeWeek = enrollment.lastTierChangeWeek
        )
        if (plannedSessions.isEmpty()) return emptyList()

        val filteredPrefs = preferredDays
            .filter { it.level != com.hrcoach.domain.bootcamp.DaySelectionLevel.NONE && it.level != com.hrcoach.domain.bootcamp.DaySelectionLevel.BLACKOUT }
        val allAvailableDays = filteredPrefs.map { it.day }

        // Don't create sessions for days already past — prevents false "missed" alerts
        // when enrolling mid-week or when sessions are first generated after the week started.
        val today = LocalDate.now().dayOfWeek.value
        val availableDays = allAvailableDays.filter { it >= today }
        if (availableDays.isEmpty()) return emptyList()

        // Reorder planned sessions so LONG-type lands on the LONG_RUN_BIAS day if one exists
        // Use filtered prefs matching only future/today days for bias index lookup
        val futureFilteredPrefs = filteredPrefs.filter { it.day >= today }
        val orderedSessions = run {
            val biasIndex = futureFilteredPrefs.indexOfFirst { it.level == com.hrcoach.domain.bootcamp.DaySelectionLevel.LONG_RUN_BIAS }
            val longIndex = plannedSessions.indexOfFirst { it.type == com.hrcoach.domain.bootcamp.SessionType.LONG }
            if (biasIndex >= 0 && longIndex >= 0 && biasIndex != longIndex && biasIndex < plannedSessions.size) {
                plannedSessions.toMutableList().apply {
                    val tmp = this[biasIndex]
                    this[biasIndex] = this[longIndex]
                    this[longIndex] = tmp
                }
            } else {
                plannedSessions
            }
        }

        // Only create as many sessions as we have remaining days for
        val sessionsToCreate = orderedSessions.take(availableDays.size)

        val entities = sessionsToCreate.mapIndexed { index, session ->
            BootcampRepository.buildSessionEntity(
                enrollmentId = enrollment.id,
                weekNumber = weekNumber,
                dayOfWeek = availableDays.getOrElse(index) { index + 1 }.coerceIn(1, 7),
                sessionType = session.type.name,
                targetMinutes = session.minutes,
                presetId = session.presetId
            )
        }
        bootcampRepository.insertSessions(entities)
        val saved = bootcampRepository.getSessionsForWeek(enrollment.id, weekNumber)

        // Cloud backup: sync newly seeded sessions
        for (session in saved) {
            runCatching { cloudBackupManager.syncBootcampSession(session) }
                .onFailure { Log.w("BootcampVM", "Cloud backup failed for session", it) }
        }

        // Schedule day-before notification reminders for the new sessions
        notificationManager.createNotificationChannel()
        notificationManager.scheduleWeekReminders(
            enrollmentId = enrollment.id,
            weekNumber = weekNumber,
            sessions = saved,
            startDateMs = enrollment.startDate
        )

        return saved
    }

    private suspend fun clearTierPromptSnoozeIfCtlSafe(
        goal: BootcampGoal,
        enrollment: BootcampEnrollmentEntity,
        profileCtl: Float
    ): BootcampEnrollmentEntity {
        if (!TierCtlRanges.isCtlInRange(goal, enrollment.tierIndex, profileCtl)) return enrollment
        if (enrollment.tierPromptSnoozedUntilMs <= 0L && enrollment.tierPromptDismissCount == 0) return enrollment

        val cleared = enrollment.copy(
            tierPromptDismissCount = 0,
            tierPromptSnoozedUntilMs = 0L
        )
        bootcampRepository.updateEnrollment(cleared)
        return cleared
    }

    private fun resolveTierPromptDirection(
        goal: BootcampGoal,
        profileCtl: Float,
        profileAtl: Float,
        totalSessions: Int,
        enrollment: BootcampEnrollmentEntity,
        recentMetrics: List<WorkoutAdaptiveMetrics>,
        currentPhase: TrainingPhase
    ): TierPromptDecision {
        if (totalSessions < TierCtlRanges.MIN_SESSIONS_FOR_PROMPT) return TierPromptDecision.None
        if (System.currentTimeMillis() < enrollment.tierPromptSnoozedUntilMs) return TierPromptDecision.None

        val range = TierCtlRanges.rangeFor(goal, enrollment.tierIndex)
        val lowerBound = range.first.toFloat()
        val upperBound = range.last.toFloat()
        val tsb = profileCtl - profileAtl

        val snapshots = buildRecentWeeklyLoadSnapshots(recentMetrics)
        if (snapshots.size < TierCtlRanges.MIN_CONSECUTIVE_WEEKS_FOR_PROMPT) return TierPromptDecision.None

        val aboveWeeks = consecutiveWeeksFromNow(snapshots) { it.ctlProxy > upperBound }
        val belowWeeks = consecutiveWeeksFromNow(snapshots) { it.ctlProxy < lowerBound }

        if (
            profileCtl > upperBound &&
            aboveWeeks >= TierCtlRanges.MIN_CONSECUTIVE_WEEKS_FOR_PROMPT &&
            tsb > 0f &&
            enrollment.tierIndex < TierCtlRanges.maxTierIndex
        ) {
            val ctlTrend = ctlTrendAcrossWeeks(snapshots, aboveWeeks)
            return TierPromptDecision(
                direction = TierPromptDirection.UP,
                evidence = CoachingCopyGenerator.tierPromptCopy(
                    direction = TierPromptDirection.UP,
                    aboveOrBelowWeeks = aboveWeeks,
                    ctlTrend = ctlTrend,
                    tsb = tsb,
                    hasFinishingTime = enrollment.targetFinishingTimeMinutes != null
                )
            )
        }

        if (
            profileCtl < lowerBound &&
            belowWeeks >= TierCtlRanges.MIN_CONSECUTIVE_WEEKS_FOR_PROMPT &&
            enrollment.tierIndex > TierCtlRanges.minTierIndex
        ) {
            val latestWeek = snapshots.first()
            val priorWeek = snapshots.getOrNull(1)
            val atlDeclining = priorWeek != null && latestWeek.totalLoad < priorWeek.totalLoad
            val belowPlannedFrequency = latestWeek.sessionCount < enrollment.runsPerWeek
            val intentionalDeload = currentPhase == TrainingPhase.TAPER || atlDeclining || belowPlannedFrequency
            if (intentionalDeload) return TierPromptDecision.None

            val ctlTrend = ctlTrendAcrossWeeks(snapshots, belowWeeks)
            return TierPromptDecision(
                direction = TierPromptDirection.DOWN,
                evidence = CoachingCopyGenerator.tierPromptCopy(
                    direction = TierPromptDirection.DOWN,
                    aboveOrBelowWeeks = belowWeeks,
                    ctlTrend = ctlTrend,
                    tsb = tsb,
                    hasFinishingTime = enrollment.targetFinishingTimeMinutes != null
                )
            )
        }

        return TierPromptDecision.None
    }

    private fun buildRecentWeeklyLoadSnapshots(
        recentMetrics: List<WorkoutAdaptiveMetrics>,
        lookbackWeeks: Int = 6
    ): List<WeeklyLoadSnapshot> {
        val now = System.currentTimeMillis()
        return (0 until lookbackWeeks).map { weekOffset ->
            val endMs = now - TimeUnit.DAYS.toMillis((weekOffset * 7L))
            val startMs = now - TimeUnit.DAYS.toMillis(((weekOffset + 1L) * 7L))
            val weekMetrics = recentMetrics.filter { metric ->
                metric.recordedAtMs >= startMs &&
                    metric.recordedAtMs < endMs &&
                    metric.trimpReliable &&
                    !metric.environmentAffected
            }
            val totalLoad = weekMetrics.sumOf { (it.trimpScore ?: 0f).toDouble() }.toFloat()
            WeeklyLoadSnapshot(
                totalLoad = totalLoad,
                ctlProxy = totalLoad / 7f,
                sessionCount = weekMetrics.size
            )
        }
    }

    private fun consecutiveWeeksFromNow(
        snapshots: List<WeeklyLoadSnapshot>,
        predicate: (WeeklyLoadSnapshot) -> Boolean
    ): Int {
        var count = 0
        for (snapshot in snapshots) {
            if (predicate(snapshot)) {
                count++
            } else {
                break
            }
        }
        return count
    }

    private fun ctlTrendAcrossWeeks(snapshots: List<WeeklyLoadSnapshot>, weeks: Int): Float {
        if (weeks <= 1 || snapshots.isEmpty()) return 0f
        val cappedIndex = (weeks - 1).coerceAtMost(snapshots.lastIndex)
        val latest = snapshots.first().ctlProxy
        val prior = snapshots[cappedIndex].ctlProxy
        return latest - prior
    }

    private data class WeeklyLoadSnapshot(
        val totalLoad: Float,
        val ctlProxy: Float,
        val sessionCount: Int
    )

    private data class TierPromptDecision(
        val direction: TierPromptDirection,
        val evidence: String?
    ) {
        companion object {
            val None = TierPromptDecision(TierPromptDirection.NONE, null)
        }
    }

    private suspend fun loadPresetIndicesForWeek(
        enrollmentId: Long,
        weekNumber: Int
    ): Map<String, Int> {
        if (weekNumber <= 1) return emptyMap()
        val previousWeek = bootcampRepository.getSessionsForWeek(enrollmentId, weekNumber - 1)
        if (previousWeek.isEmpty()) return emptyMap()
        return previousWeek
            .mapNotNull { sessionTypePresetKey(it.sessionType) }
            .distinct()
            .associateWith { 1 }
    }

    private fun sessionTypePresetKey(rawType: String): String? {
        val type = runCatching { SessionType.valueOf(rawType) }
            .onFailure { Log.w("BootcampVM", "Unknown session type: $rawType") }
            .getOrNull() ?: return null
        return when (type) {
            SessionType.EASY -> "easy"
            SessionType.TEMPO -> "tempo"
            SessionType.INTERVAL -> "interval"
            SessionType.STRIDES -> "strides"
            SessionType.LONG -> "long"
            SessionType.RACE_SIM,
            SessionType.DISCOVERY,
            SessionType.CHECK_IN -> null
        }
    }

    private fun dayLabelFor(dayOfWeek: Int): String =
        DayOfWeek.of(dayOfWeek.coerceIn(1, 7)).getDisplayName(TextStyle.SHORT, Locale.getDefault())

    /** "54 min · Base" / "35 min · Threshold" — minutes + short zone for the Coming-Up list. */
    private fun buildUpcomingSubtitle(minutes: Int, rawSessionType: String): String {
        val zone = ZoneEducationProvider.shortBadge(rawSessionType)
        return if (zone != null) "$minutes min · $zone" else "$minutes min"
    }

    private fun sessionTypeDisplayName(rawType: String, presetId: String? = null): String {
        val presetLabel = SessionType.displayLabelForPreset(presetId)
        if (presetLabel != null) return presetLabel

        return runCatching {
            SessionType.valueOf(rawType)
        }.onFailure { Log.w("BootcampVM", "Unknown session type for label: $rawType") }
            .getOrNull()?.let { sessionType ->
            when (sessionType) {
                SessionType.EASY -> "Easy"
                SessionType.LONG -> "Long"
                SessionType.TEMPO -> "Tempo (Z3)"
                SessionType.INTERVAL -> "Intervals (Z5)"
                SessionType.STRIDES -> "Strides"
                SessionType.RACE_SIM -> "Race Sim"
                SessionType.DISCOVERY -> "Discovery"
                SessionType.CHECK_IN -> "Check In"
            }
        } ?: rawType.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun BootcampSessionEntity.toPlannedSession(): PlannedSession = PlannedSession(
        type = runCatching { SessionType.valueOf(sessionType) }
            .onFailure { Log.w("BootcampVM", "Unknown session type in entity: $sessionType") }
            .getOrDefault(SessionType.EASY),
        minutes = targetMinutes,
        presetId = presetId,
        weekNumber = weekNumber
    )

    // ─── MaxHR gate (blocks workout start when maxHR is not set) ────────

    /**
     * Call instead of buildConfigJson when the user taps "Start Run".
     * If maxHR is set, builds the config and proceeds to BLE dialog.
     * If not, shows the maxHR gate sheet first.
     */
    fun prepareStartWorkout(session: PlannedSession, onConfigReady: (String) -> Unit) {
        // Resolve the DB session ID for the session the user tapped and store it
        // in WorkoutState now, so onBootcampWorkoutStarting() doesn't have to guess.
        val matchedSessionId = _uiState.value.currentWeekDays
            .mapNotNull { it.session }
            .firstOrNull { uiItem ->
                uiItem.sessionId != null &&
                !uiItem.isCompleted &&
                uiItem.presetId == session.presetId &&
                uiItem.minutes == session.minutes
            }?.sessionId
        WorkoutState.setPendingBootcampSessionId(matchedSessionId)

        val currentMaxHr = _uiState.value.maxHr
        if (currentMaxHr != null) {
            onConfigReady(buildWorkoutConfig(session, currentMaxHr))
        } else {
            _uiState.update {
                it.copy(
                    showMaxHrGate = true,
                    maxHrGateInput = "",
                    maxHrGateError = null,
                    pendingGateSession = session
                )
            }
        }
    }

    fun setMaxHrGateInput(value: String) {
        _uiState.update { it.copy(maxHrGateInput = value, maxHrGateError = null) }
    }

    /**
     * Saves maxHR and returns the config JSON for the pending session,
     * or null if validation fails.
     */
    fun confirmMaxHrGate(): String? {
        val state = _uiState.value
        val parsed = state.maxHrGateInput.trim().toIntOrNull()
        if (parsed == null || parsed !in 100..220) {
            _uiState.update { it.copy(maxHrGateError = "Enter a value between 100 and 220") }
            return null
        }
        userProfileRepository.setMaxHr(parsed)
        adaptiveProfileRepository.saveProfile(
            adaptiveProfileRepository.getProfile().copy(hrMax = parsed)
        )
        val session = state.pendingGateSession ?: return null
        _uiState.update {
            it.copy(
                maxHr = parsed,
                showMaxHrGate = false,
                pendingGateSession = null,
                maxHrGateInput = "",
                maxHrGateError = null
            )
        }
        return buildWorkoutConfig(session, parsed)
    }

    fun dismissMaxHrGate() {
        _uiState.update {
            it.copy(
                showMaxHrGate = false,
                pendingGateSession = null,
                maxHrGateInput = "",
                maxHrGateError = null
            )
        }
    }

    private fun buildWorkoutConfig(session: PlannedSession, maxHr: Int): String {
        val restHr = adaptiveProfileRepository.getProfile().hrRest?.toInt()
            ?: com.hrcoach.domain.model.defaultRestHr(userProfileRepository.getAge())
        val recovery = currentEnrollment?.let {
            PhaseEngine(
                goal = BootcampGoal.valueOf(it.goalType),
                phaseIndex = it.currentPhaseIndex,
                weekInPhase = it.currentWeekInPhase,
                runsPerWeek = it.runsPerWeek,
                targetMinutes = it.targetMinutesPerRun
            ).isRecoveryWeek(currentTuningDirection)
        } ?: false
        val presetId = session.presetId
        if (presetId != null) {
            val preset = com.hrcoach.domain.preset.PresetLibrary.ALL.firstOrNull { it.id == presetId }
            if (preset != null) {
                val config = preset.buildConfig(maxHr, restHr)
                // Carry session metadata so the active workout screen can show goal info
                val enriched = config.copy(
                    plannedDurationMinutes = config.plannedDurationMinutes ?: session.minutes,
                    sessionLabel = config.sessionLabel
                        ?: com.hrcoach.domain.bootcamp.SessionType.displayLabelForPreset(presetId)
                        ?: session.type.name.lowercase().replaceFirstChar { it.uppercase() },
                    bootcampWeekNumber = session.weekNumber,
                    isRecoveryWeek = recovery
                )
                return com.hrcoach.util.JsonCodec.gson.toJson(enriched)
            }
        }
        // Timed sessions without a matching preset — free run with goal metadata
        val label = session.type.name.lowercase().replaceFirstChar { it.uppercase() }
        return com.hrcoach.util.JsonCodec.gson.toJson(
            com.hrcoach.domain.model.WorkoutConfig(
                mode = com.hrcoach.domain.model.WorkoutMode.FREE_RUN,
                plannedDurationMinutes = session.minutes,
                sessionLabel = label,
                bootcampWeekNumber = session.weekNumber,
                isRecoveryWeek = recovery
            )
        )
    }

    // ─── BLE connection (pre-start dialog) ─────────────────────────────

    fun showHrConnectDialog(configJson: String) {
        val lastDevice = bleCoordinator.getLastKnownDevice()
        _uiState.update {
            it.copy(
                showHrConnectDialog = true,
                pendingConfigJson = configJson,
                bleLastKnownDeviceName = lastDevice?.name,
                bleLastKnownDeviceAddress = lastDevice?.address
            )
        }
        startBleCollectors()
        // Auto-reconnect to last known device if available
        if (!bleCoordinator.isConnected.value && lastDevice != null) {
            reconnectLastDevice(lastDevice.address)
        }
    }

    @SuppressLint("MissingPermission")
    private fun reconnectLastDevice(address: String) {
        val connected = runCatching {
            bleCoordinator.connectToAddress(address)
        }.onFailure { Log.w("BootcampVM", "BLE reconnect failed for $address", it) }
            .getOrDefault(false)
        if (!connected) {
            // Fallback to scanning if reconnect fails
            startBleScan()
        }
    }

    fun dismissHrConnectDialog() {
        bleCollectJob?.cancel()
        scanTimeoutJob?.cancel()
        _uiState.update {
            it.copy(
                showHrConnectDialog = false,
                pendingConfigJson = null,
                bleIsScanning = false,
                bleDiscoveredDevices = emptyList(),
                bleConnectionError = null
            )
        }
    }

    // ── Audio primer gating ──────────────────────────────────────────────────
    //
    // Before the first workout (fresh install), show a 3-slide primer that
    // explains chime / voice / vibration layering and predictive warnings.
    // See AudioPrimerDialog. Simulation mode skips the primer entirely (mirrors
    // the PermissionGate.hasAllRuntimePermissions bypass in NavGraph).

    /**
     * Call from the bootcamp "start workout" entry points.
     * If the primer hasn't been shown (and we're not in simulation), raises
     * [BootcampUiState.showAudioPrimer] and does NOT invoke [onProceed].
     * Otherwise invokes [onProceed] immediately.
     */
    fun maybeShowPrimerOrProceed(onProceed: () -> Unit) {
        val audio = audioSettingsRepository.getAudioSettings()
        if (!audio.audioPrimerShown && !SimulationController.isActive) {
            _uiState.update { it.copy(showAudioPrimer = true) }
        } else {
            onProceed()
        }
    }

    /**
     * Called from the primer's "Got it — start my run" button. Persists the
     * primer-shown flag, hides the dialog, then invokes [onProceed] to start
     * the workout.
     */
    fun dismissPrimerThenProceed(onProceed: () -> Unit) {
        audioSettingsRepository.setAudioPrimerShown(true)
        _uiState.update { it.copy(showAudioPrimer = false) }
        // Skip the next workout's TTS briefing — primer already explained the audio system.
        // See SetupViewModel.dismissPrimerThenProceed for the same pattern.
        com.hrcoach.service.audio.CoachingAudioManager.skipNextBriefing = true
        onProceed()
    }

    /**
     * Called when the user taps "See all sounds ->" from the primer. Persists
     * the primer-shown flag, hides the dialog, but does NOT start the workout —
     * the caller navigates to the sound library screen instead.
     */
    fun dismissPrimerNoProceed() {
        audioSettingsRepository.setAudioPrimerShown(true)
        _uiState.update { it.copy(showAudioPrimer = false) }
    }

    // ── Strides primer gating ────────────────────────────────────────────────
    //
    // First-time educational modal shown when the user's next bootcamp session
    // is a strides session. Mirrors the [dismissPrimerThenProceed] /
    // [dismissPrimerNoProceed] pattern above but persists into a separate
    // SharedPreferences-backed field [AudioSettings.stridesPrimerSeen] so the
    // two primers are independently dismissible.

    /**
     * Called from the strides primer's "Got it" button. Persists the
     * primer-seen flag and hides the dialog. Does not start a workout — the
     * primer is purely educational.
     */
    fun dismissStridesPrimer() {
        audioSettingsRepository.setStridesPrimerSeen(true)
        _uiState.update { it.copy(showStridesPrimer = false) }
    }

    private fun startBleCollectors() {
        bleCollectJob?.cancel()
        bleCollectJob = viewModelScope.launch {
            launch {
                bleCoordinator.discoveredDevices.collect { devices ->
                    _uiState.update {
                        it.copy(
                            bleDiscoveredDevices = devices,
                            bleIsScanning = it.bleIsScanning && !it.bleIsConnected
                        )
                    }
                }
            }
            launch {
                bleCoordinator.isConnected.collect { connected ->
                    _uiState.update {
                        it.copy(
                            bleIsConnected = connected,
                            bleIsScanning = if (connected) false else it.bleIsScanning,
                            bleConnectionError = if (connected) null else it.bleConnectionError,
                            bleConnectedDeviceName = if (connected && it.bleConnectedDeviceName.isBlank()) {
                                it.bleLastKnownDeviceName ?: "HR Monitor"
                            } else {
                                it.bleConnectedDeviceName
                            }
                        )
                    }
                }
            }
            launch {
                bleCoordinator.heartRate.collect { hr ->
                    _uiState.update { it.copy(bleLiveHr = hr) }
                }
            }
        }
    }

    fun startBleScan() {
        runCatching {
            bleCoordinator.startScan()
        }.onSuccess {
            scanTimeoutJob?.cancel()
            scanTimeoutJob = viewModelScope.launch {
                delay(16_000)
                if (!_uiState.value.bleIsConnected) {
                    _uiState.update { it.copy(bleIsScanning = false) }
                }
            }
            _uiState.update {
                it.copy(
                    bleIsScanning = true,
                    bleDiscoveredDevices = emptyList(),
                    bleConnectionError = null
                )
            }
        }.onFailure { throwable ->
            Log.e("BootcampVM", "BLE scan failed", throwable)
            _uiState.update {
                it.copy(
                    bleIsScanning = false,
                    bleConnectionError = when (throwable) {
                        is SecurityException -> "Bluetooth permission required. Check Settings."
                        else -> "Unable to scan. Check Bluetooth and permissions."
                    }
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        runCatching {
            bleCoordinator.connectToDevice(device)
        }.onSuccess {
            scanTimeoutJob?.cancel()
            _uiState.update {
                it.copy(
                    bleIsScanning = false,
                    bleConnectedDeviceName = device.name ?: device.address,
                    bleConnectedDeviceAddress = device.address,
                    bleConnectionError = null
                )
            }
        }.onFailure { t ->
            Log.e("BootcampVM", "BLE connect failed", t)
            _uiState.update {
                it.copy(bleConnectionError = "Unable to connect to selected device.")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        bleCoordinator.disconnect()
        _uiState.update {
            it.copy(
                bleIsConnected = false,
                bleConnectedDeviceName = "",
                bleConnectedDeviceAddress = "",
                bleLiveHr = 0
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun handoffConnectedDeviceAddress(): String? {
        val address = _uiState.value.bleConnectedDeviceAddress.takeIf { it.isNotBlank() }
        val activeAddress = bleCoordinator.handoffConnectedDeviceAddress(address)
        bleCollectJob?.cancel()
        scanTimeoutJob?.cancel()
        _uiState.update {
            it.copy(
                showHrConnectDialog = false,
                bleIsScanning = false
            )
        }
        return activeAddress
    }

    companion object {
        private const val RECENT_METRICS_DAYS = 42
        const val ILLNESS_CONFIRM_SNOOZE_DAYS = 10L
        const val ILLNESS_DISMISS_SNOOZE_DAYS = 1L
    }

}

/**
 * Strides session detection. Mirrors the gating used by the audio pipeline
 * (`WorkoutConfig.guidanceTag == "strides"` OR a strides-flavored preset id).
 * The DB row carries `presetId` only — `guidanceTag` lives on `WorkoutConfig`
 * which is constructed at workout start — so we match on `presetId` here.
 */
private fun BootcampSessionEntity.isStridesSession(): Boolean {
    val pid = presetId
    return pid == "strides_20s" || pid == "zone2_with_strides"
}

