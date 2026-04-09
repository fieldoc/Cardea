package com.hrcoach.ui.bootcamp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.FitnessLevel
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.domain.bootcamp.PlannedSession
import com.hrcoach.domain.bootcamp.FinishingTimeTierMapper
import com.hrcoach.domain.bootcamp.SessionType
import com.hrcoach.domain.education.ContentDensity
import com.hrcoach.domain.education.ZoneEducationProvider
import com.hrcoach.domain.engine.TierPromptDirection
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaCtaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.ZoneAmber
import com.hrcoach.ui.theme.ZoneRed
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.ZoneGreen

import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.offset
import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun BootcampScreen(
    onStartWorkout: (configJson: String, deviceAddress: String?) -> Unit,
    onBack: () -> Unit,
    onGoToSettings: () -> Unit,
    onGoToManualSetup: (() -> Unit)? = null,
    viewModel: BootcampViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPhaseDetail by remember { mutableStateOf(false) }
    var showDaysSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show swap-rest confirmation
    LaunchedEffect(uiState.swapRestMessage) {
        uiState.swapRestMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSwapRestMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaTheme.colors.bgPrimary)
    ) {
        when {
            uiState.loadError != null -> {
                LoadErrorContent(
                    errorMessage = uiState.loadError!!,
                    onRetry = viewModel::retryLoad
                )
            }

            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GradientPink)
                }
            }

            uiState.showCarousel -> {
                BootcampOnboardingCarousel(
                    onStartSetup = { viewModel.dismissCarousel() },
                    onSkip = { viewModel.dismissCarousel() }
                )
            }

            uiState.showOnboarding -> {
                OnboardingWizard(
                    uiState = uiState,
                    step = uiState.onboardingStep,
                    onStepChange = viewModel::setOnboardingStep,
                    onGoalSelected = { viewModel.setOnboardingGoal(it) },
                    onMinutesChanged = { viewModel.setOnboardingMinutes(it) },
                    onRunsPerWeekChanged = { viewModel.setOnboardingRunsPerWeek(it) },
                    onFinishingTimeChanged = { viewModel.setOnboardingFinishingTime(it) },
                    onCycleDayPreference = { viewModel.cycleOnboardingDayPreference(it) },
                    onToggleBlackoutDay = { viewModel.toggleOnboardingBlackoutDay(it) },
                    onComplete = { viewModel.completeOnboarding() },
                    onBack = onBack
                )
            }

            !uiState.hasActiveEnrollment -> {
                NoEnrollmentContent(
                    onStartBootcamp = { viewModel.startOnboarding() }
                )
            }

            else -> {
                ActiveBootcampDashboard(
                    uiState = uiState,
                    onRequestSession = { session ->
                        viewModel.prepareStartWorkout(session) { configJson ->
                            viewModel.showHrConnectDialog(configJson)
                        }
                    },
                    onPause = { viewModel.pauseBootcamp() },
                    onResume = { viewModel.resumeBootcamp() },
                    onEndProgram = { viewModel.showDeleteConfirmDialog() },
                    onDismissTierPrompt = viewModel::dismissTierPrompt,
                    onAcceptTierChange = viewModel::acceptTierChange,
                    onConfirmIllness = viewModel::confirmIllness,
                    onDismissIllness = viewModel::dismissIllness,
                    onSwapTodayForRest = viewModel::swapTodayForRest,
                    onGraduateGoal = viewModel::graduateCurrentGoal,
                    onReschedule = { sessionId -> viewModel.requestReschedule(sessionId) },
                    onProgressClick = { showPhaseDetail = true },
                    onSessionClick = viewModel::onSessionClick,
                    onGoalClick = viewModel::showGoalDetail,
                    onSavePreferredDays = viewModel::savePreferredDays,
                    onSettingsClick = onGoToSettings,
                    onPreferredDaysClick = { showDaysSheet = true },
                    onGoToManualSetup = onGoToManualSetup
                )

                if (uiState.showMaxHrGate) {
                    MaxHrGateSheet(
                        input = uiState.maxHrGateInput,
                        error = uiState.maxHrGateError,
                        onInputChanged = viewModel::setMaxHrGateInput,
                        onConfirm = {
                            val configJson = viewModel.confirmMaxHrGate()
                            if (configJson != null) {
                                viewModel.showHrConnectDialog(configJson)
                            }
                        },
                        onDismiss = viewModel::dismissMaxHrGate
                    )
                }

                if (uiState.showHrConnectDialog) {
                    HrConnectDialog(
                        uiState = uiState,
                        onScan = viewModel::startBleScan,
                        onConnect = viewModel::connectToDevice,
                        onDisconnect = viewModel::disconnectDevice,
                        onStartWithMonitor = {
                            val configJson = uiState.pendingConfigJson ?: return@HrConnectDialog
                            viewModel.onBootcampWorkoutStarting()
                            val deviceAddress = viewModel.handoffConnectedDeviceAddress()
                            onStartWorkout(configJson, deviceAddress)
                        },
                        onStartWithout = {
                            val configJson = uiState.pendingConfigJson ?: return@HrConnectDialog
                            viewModel.onBootcampWorkoutStarting()
                            viewModel.dismissHrConnectDialog()
                            onStartWorkout(configJson, null)
                        },
                        onDismiss = viewModel::dismissHrConnectDialog
                    )
                }
            }
        }

        if (showDaysSheet) {
            PreferredDaysBottomSheet(
                currentDays = uiState.activePreferredDays,
                onSave = { days ->
                    viewModel.savePreferredDays(days)
                    showDaysSheet = false
                },
                onDismiss = { showDaysSheet = false }
            )
        }

        if (showPhaseDetail) {
            PhaseDetailSheet(
                uiState = uiState,
                onDismiss = { showPhaseDetail = false }
            )
        }

        if (uiState.showSessionDetail && uiState.sessionDetailItem != null) {
            SessionDetailSheet(
                session = uiState.sessionDetailItem!!,
                maxHr = uiState.maxHr,
                onDismiss = viewModel::dismissSessionDetail,
                onReschedule = { id -> viewModel.rescheduleFromDetail(id) },
                onSkip = { id -> viewModel.skipSession(id) },
                onStartRun = { session ->
                    viewModel.dismissSessionDetail()
                    viewModel.prepareStartWorkout(session) { configJson ->
                        viewModel.showHrConnectDialog(configJson)
                    }
                },
                onRestToday = viewModel::swapTodayForRestFromDetail
            )
        }

        if (uiState.showGoalDetail && uiState.goal != null) {
            GoalDetailSheet(
                goal = uiState.goal!!,
                progressPercentage = uiState.goalProgressPercentage,
                onDismiss = viewModel::dismissGoalDetail
            )
        }

        if (uiState.welcomeBackMessage != null) {
            WelcomeBackDialog(
                message = uiState.welcomeBackMessage!!,
                onDismiss = { viewModel.dismissWelcomeBack() }
            )
        }

        if (uiState.showDeleteConfirmDialog) {
            DeleteConfirmDialog(
                onConfirm = { viewModel.deleteBootcamp() },
                onDismiss = { viewModel.dismissDeleteConfirmDialog() }
            )
        }

        if (uiState.rescheduleSheetSessionId != null) {
            RescheduleBottomSheet(
                autoTargetLabel = uiState.rescheduleAutoTargetLabel,
                availableDays = uiState.rescheduleAvailableDays,
                availableLabels = uiState.rescheduleAvailableLabels,
                onConfirm   = { day -> viewModel.confirmReschedule(day) },
                onDefer     = { viewModel.deferReschedule() },
                onDismiss   = { viewModel.dismissRescheduleSheet() }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        )
    }
}

// ─── No Enrollment ─────────────────────────────────────────────────────────

@Composable
private fun NoEnrollmentContent(onStartBootcamp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Bootcamp",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = CardeaTheme.colors.textPrimary
        )
        Text(
            text = "A program that adapts to your goal, your schedule, and your life.",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            FeatureBullet(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = "Periodized phases",
                detail = "Base, Build, Peak, and Taper — structured like real coach plans."
            )
            Spacer(modifier = Modifier.height(12.dp))
            FeatureBullet(
                icon = Icons.Default.FavoriteBorder,
                title = "Heart-rate guided sessions",
                detail = "Every run has a purpose — zone targets and real-time coaching."
            )
            Spacer(modifier = Modifier.height(12.dp))
            FeatureBullet(
                icon = Icons.Default.EventRepeat,
                title = "Life-aware scheduling",
                detail = "Missed a week? The plan adjusts, not you."
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        CardeaButton(
            text = "Start my program",
            onClick = onStartBootcamp,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        )
    }
}

@Composable
private fun LoadErrorContent(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Unable to load Bootcamp",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            CardeaButton(
                text = "Retry",
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            )
        }
    }
}

@Composable
private fun FeatureBullet(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CardeaTheme.colors.textPrimary,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTheme.colors.textPrimary
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun WeekStripCard(
    days: List<WeekDayItem>,
    dateRange: String,
    onSessionClick: (SessionUiItem) -> Unit = {}
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Date range label only (no title — day letters make context obvious)
        Text(
            text = dateRange,
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTheme.colors.textTertiary,
            modifier = Modifier.align(Alignment.End)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 7-day pill row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEach { day ->
                WeekDayPill(
                    day = day,
                    onClick = day.session?.let { session -> { onSessionClick(session) } }
                )
            }
        }
    }
}

@Composable
private fun WeekDayPill(day: WeekDayItem, onClick: (() -> Unit)? = null) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pillScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        label = "pillPress"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = (if (onClick != null) Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        ) else Modifier).scale(pillScale)
    ) {
        // Day letter
        Text(
            text = day.dayLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = CardeaTheme.colors.textTertiary
        )

        // Pill dot
        val session = day.session
        val dotBackground: Color
        val dotBorder: Color
        val dotContent: @Composable () -> Unit

        when {
            session == null && day.isToday -> {
                // today is a rest day — subtle bordered empty dot
                dotBackground = Color.Transparent
                dotBorder = CardeaTheme.colors.glassBorder
                dotContent = {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = CardeaTheme.colors.textTertiary.copy(alpha = 0.4f)
                    )
                }
            }
            session == null -> {
                dotBackground = CardeaTheme.colors.glassHighlight.copy(alpha = 0.4f)
                dotBorder = Color.Transparent
                dotContent = {}
            }
            session.isCompleted && day.isToday -> {
                dotBackground = ZoneGreen.copy(alpha = 0.18f)
                dotBorder = ZoneGreen.copy(alpha = 0.5f)
                dotContent = {
                    Icon(
                        Icons.Default.Check, contentDescription = null,
                        tint = ZoneGreen, modifier = Modifier.size(12.dp)
                    )
                }
            }
            session.isCompleted -> {
                dotBackground = ZoneGreen.copy(alpha = 0.12f)
                dotBorder = ZoneGreen.copy(alpha = 0.22f)
                dotContent = {
                    Icon(
                        Icons.Default.Check, contentDescription = null,
                        tint = ZoneGreen, modifier = Modifier.size(12.dp)
                    )
                }
            }
            session.isDeferred -> {
                // Deferred: amber tint to signal "needs attention"
                dotBackground = ZoneAmber.copy(alpha = 0.12f)
                dotBorder = ZoneAmber.copy(alpha = 0.35f)
                dotContent = {
                    Text(
                        text = "?",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ZoneAmber
                    )
                }
            }
            session.isPast && !session.isCompleted -> {
                // Missed: past day, still scheduled — subtle red
                dotBackground = ZoneRed.copy(alpha = 0.10f)
                dotBorder = ZoneRed.copy(alpha = 0.30f)
                dotContent = {
                    Text(
                        text = "!",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ZoneRed.copy(alpha = 0.7f)
                    )
                }
            }
            day.isToday -> {
                dotBackground = GradientPink.copy(alpha = 0.14f)
                dotBorder = GradientPink.copy(alpha = 0.5f)
                dotContent = {
                    Text(
                        text = session.rawTypeName.take(2),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = GradientPink
                    )
                }
            }
            else -> {
                dotBackground = CardeaTheme.colors.glassHighlight
                dotBorder = CardeaTheme.colors.glassBorder
                dotContent = {
                    Text(
                        text = session.rawTypeName.take(2),
                        style = MaterialTheme.typography.labelSmall,
                        color = CardeaTheme.colors.textTertiary
                    )
                }
            }
        }

        // Today's pill gets an organic breathing pulse
        val isToday = day.isToday
        val isCompleted = session?.isCompleted == true
        val todayPulse = if (isToday && !isCompleted) {
            val pulseTransition = rememberInfiniteTransition(label = "todayPulse")
            val pulseScale by pulseTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.09f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = FastOutSlowInEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "todayScale"
            )
            pulseScale
        } else 1f

        Box(contentAlignment = Alignment.Center) {
            // Tight glow halo behind completed pills
            if (isCompleted) {
                Box(
                    modifier = Modifier
                        .size(33.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ZoneGreen.copy(alpha = 0.16f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .scale(todayPulse)
                    .clip(RoundedCornerShape(7.dp))
                    .background(dotBackground)
                    .then(if (dotBorder != Color.Transparent) Modifier.border(1.dp, dotBorder, RoundedCornerShape(7.dp)) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                dotContent()
            }
        }
    }
}

// ─── Onboarding Wizard ─────────────────────────────────────────────────────

@Composable
private fun OnboardingWizard(
    uiState: BootcampUiState,
    step: Int,
    onStepChange: (Int) -> Unit,
    onGoalSelected: (BootcampGoal) -> Unit,
    onMinutesChanged: (Int) -> Unit,
    onRunsPerWeekChanged: (Int) -> Unit,
    onFinishingTimeChanged: (Int) -> Unit,
    onCycleDayPreference: (Int) -> Unit,
    onToggleBlackoutDay: (Int) -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val isRaceGoal = uiState.onboardingGoal != null &&
        FinishingTimeTierMapper.isRaceGoal(uiState.onboardingGoal)
    val totalSteps = if (isRaceGoal) 5 else 4

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Step indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(totalSteps) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .then(
                            if (index <= step) Modifier.background(CardeaGradient)
                            else Modifier.background(CardeaTheme.colors.glassBorder)
                        )
                )
            }
        }

        // Steps: for race goals, insert finishing-time step at position 1
        val timeStep = if (isRaceGoal) 2 else 1
        val freqStep = if (isRaceGoal) 3 else 2
        val daysStep = freqStep + 1

        when (step) {
            0 -> OnboardingStep1Goal(
                selectedGoal = uiState.onboardingGoal,
                onGoalSelected = { onGoalSelected(it) },
                onNext = { if (uiState.onboardingGoal != null) onStepChange(1) },
                onBack = onBack
            )
            1 -> if (isRaceGoal) {
                OnboardingStepFinishingTime(
                    goal = uiState.onboardingGoal!!,
                    finishingTimeMinutes = uiState.onboardingTargetFinishingTime ?: 30,
                    onFinishingTimeChanged = onFinishingTimeChanged,
                    onNext = { onStepChange(2) },
                    onBack = { onStepChange(0) }
                )
            } else {
                OnboardingStep2Time(
                    minutes = uiState.onboardingAvailableMinutes,
                    warning = uiState.onboardingTimeWarning,
                    canProceed = uiState.onboardingTimeCanProceed,
                    longRunMinutes = uiState.onboardingLongRunMinutes,
                    weeklyTotal = uiState.onboardingWeeklyTotal,
                    longRunWarning = uiState.onboardingLongRunWarning,
                    onMinutesChanged = onMinutesChanged,
                    onNext = { onStepChange(2) },
                    onBack = { onStepChange(0) }
                )
            }
            timeStep -> if (isRaceGoal && step == 2) {
                OnboardingStep2Time(
                    minutes = uiState.onboardingAvailableMinutes,
                    warning = uiState.onboardingTimeWarning,
                    canProceed = uiState.onboardingTimeCanProceed,
                    longRunMinutes = uiState.onboardingLongRunMinutes,
                    weeklyTotal = uiState.onboardingWeeklyTotal,
                    longRunWarning = uiState.onboardingLongRunWarning,
                    onMinutesChanged = onMinutesChanged,
                    onNext = { onStepChange(freqStep) },
                    onBack = { onStepChange(1) }
                )
            }
            freqStep -> OnboardingStep3Frequency(
                runsPerWeek = uiState.onboardingRunsPerWeek,
                onRunsPerWeekChanged = onRunsPerWeekChanged,
                onNext = { onStepChange(daysStep) },
                onBack = { onStepChange(timeStep) }
            )
            daysStep -> OnboardingStep4Days(
                preferredDays = uiState.onboardingPreferredDays,
                runsPerWeek = uiState.onboardingRunsPerWeek,
                onCycleDayPreference = onCycleDayPreference,
                onToggleBlackoutDay = onToggleBlackoutDay,
                onComplete = onComplete,
                onBack = { onStepChange(freqStep) }
            )
        }
    }
}

@Composable
private fun OnboardingStep1Goal(
    selectedGoal: BootcampGoal?,
    onGoalSelected: (BootcampGoal) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Text(
        text = "What's your goal?",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = CardeaTheme.colors.textPrimary
    )
    Text(
        text = "Choose the program that matches your ambition.",
        style = MaterialTheme.typography.bodyMedium,
        color = CardeaTheme.colors.textSecondary
    )

    val goals = listOf(
        BootcampGoal.CARDIO_HEALTH to "Build your aerobic base",
        BootcampGoal.RACE_5K to "Speed and VO2max power",
        BootcampGoal.RACE_10K to "Stamina and threshold grit",
        BootcampGoal.HALF_MARATHON to "Conquer the half",
        BootcampGoal.MARATHON to "Go the distance"
    )

    goals.forEach { (goal, description) ->
        val isSelected = selectedGoal == goal
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) Modifier.border(
                        width = 1.5.dp,
                        brush = CardeaGradient,
                        shape = RoundedCornerShape(18.dp)
                    ) else Modifier
                )
                .clickable { onGoalSelected(goal) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goalDisplayName(goal),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTheme.colors.textPrimary
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textSecondary
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = GradientPink,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
            Text("Back", color = CardeaTheme.colors.textSecondary)
        }
        CardeaButton(
            text = "Next",
            onClick = onNext,
            modifier = Modifier
                .weight(2f)
                .height(48.dp)
        )
    }
}

@Composable
private fun OnboardingStepFinishingTime(
    goal: BootcampGoal,
    finishingTimeMinutes: Int,
    onFinishingTimeChanged: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val brackets = FinishingTimeTierMapper.bracketsFor(goal) ?: return
    val tierIndex = FinishingTimeTierMapper.tierFromFinishingTime(goal, finishingTimeMinutes)
    val tierLabel = when (tierIndex) {
        0 -> "Easy"
        1 -> "Moderate"
        2 -> "Hard"
        else -> "Moderate"
    }

    Text(
        text = "Target finishing time",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = CardeaTheme.colors.textPrimary
    )
    Text(
        text = "What time are you aiming for?",
        style = MaterialTheme.typography.bodyMedium,
        color = CardeaTheme.colors.textSecondary
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = FinishingTimeTierMapper.formatTime(finishingTimeMinutes),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = CardeaTheme.colors.textPrimary
        )
        Slider(
            value = finishingTimeMinutes.toFloat(),
            onValueChange = { onFinishingTimeChanged(it.toInt()) },
            valueRange = brackets.uiMin.toFloat()..brackets.uiMax.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = GradientPink,
                activeTrackColor = GradientPink,
                inactiveTrackColor = CardeaTheme.colors.glassHighlight
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                FinishingTimeTierMapper.formatTime(brackets.uiMin),
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textTertiary
            )
            Text(
                FinishingTimeTierMapper.formatTime(brackets.uiMax),
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textTertiary
            )
        }
        HorizontalDivider(color = CardeaTheme.colors.divider, modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = "Intensity: $tierLabel",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTheme.colors.textSecondary
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
            Text("Back", color = CardeaTheme.colors.textSecondary)
        }
        CardeaButton(
            text = "Next",
            onClick = onNext,
            modifier = Modifier
                .weight(2f)
                .height(48.dp)
        )
    }
}

@Composable
private fun OnboardingStep2Time(
    minutes: Int,
    warning: String?,
    canProceed: Boolean = true,
    longRunMinutes: Int,
    weeklyTotal: Int,
    longRunWarning: String?,
    onMinutesChanged: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Text(
        text = "Easy run length",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = CardeaTheme.colors.textPrimary
    )
    Text(
        text = "Set the length of a typical easy run.",
        style = MaterialTheme.typography.bodyMedium,
        color = CardeaTheme.colors.textSecondary
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$minutes min",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = CardeaTheme.colors.textPrimary
        )
        Slider(
            value = minutes.toFloat(),
            onValueChange = { onMinutesChanged(it.toInt()) },
            valueRange = 15f..90f,
            steps = 14,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = GradientPink,
                activeTrackColor = GradientPink,
                inactiveTrackColor = CardeaTheme.colors.glassHighlight
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("15 min", style = MaterialTheme.typography.labelSmall, color = CardeaTheme.colors.textTertiary)
            Text("90 min", style = MaterialTheme.typography.labelSmall, color = CardeaTheme.colors.textTertiary)
        }
        HorizontalDivider(color = CardeaTheme.colors.divider, modifier = Modifier.padding(vertical = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Long run: ~$longRunMinutes min",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
            Text(
                text = "Weekly: ~$weeklyTotal min",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textTertiary
            )
        }
    }

    if (warning != null) {
        Text(
            text = warning,
            style = MaterialTheme.typography.bodySmall,
            color = if (canProceed) CardeaTheme.colors.zoneAmber else GradientRed
        )
    }
    if (longRunWarning != null) {
        Text(
            text = longRunWarning,
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTheme.colors.zoneAmber
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
            Text("Back", color = CardeaTheme.colors.textSecondary)
        }
        CardeaButton(
            text = if (canProceed) "Next" else "Increase time",
            onClick = { if (canProceed) onNext() },
            modifier = Modifier
                .weight(2f)
                .height(48.dp)
        )
    }
}

@Composable
private fun OnboardingStep3Frequency(
    runsPerWeek: Int,
    onRunsPerWeekChanged: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Text(
        text = "Runs per week",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = CardeaTheme.colors.textPrimary
    )
    Text(
        text = "How often will you run?",
        style = MaterialTheme.typography.bodyMedium,
        color = CardeaTheme.colors.textSecondary
    )

    val options = listOf(2, 3, 4, 5)
    val labels = listOf("2", "3", "4", "5+")

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { index, runs ->
                val isSelected = runsPerWeek == runs
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (isSelected) Modifier.background(CardeaGradient)
                            else Modifier.background(CardeaTheme.colors.glassHighlight)
                        )
                        .clickable { onRunsPerWeekChanged(runs) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labels[index],
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) CardeaTheme.colors.textPrimary else CardeaTheme.colors.textSecondary
                    )
                }
            }
        }
        Text(
            text = "runs / week",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTheme.colors.textTertiary
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
            Text("Back", color = CardeaTheme.colors.textSecondary)
        }
        CardeaButton(
            text = "Next",
            onClick = onNext,
            modifier = Modifier
                .weight(2f)
                .height(48.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnboardingStep4Days(
    preferredDays: List<DayPreference>,
    runsPerWeek: Int,
    onCycleDayPreference: (Int) -> Unit,
    onToggleBlackoutDay: (Int) -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val selectedCount = preferredDays.count {
        it.level == DaySelectionLevel.AVAILABLE || it.level == DaySelectionLevel.LONG_RUN_BIAS
    }
    val isValid = selectedCount == runsPerWeek

    Text(
        text = "Pick your days",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = CardeaTheme.colors.textPrimary
    )
    Text(
        text = "Tap to toggle, hold to block",
        style = MaterialTheme.typography.bodyMedium,
        color = CardeaTheme.colors.textSecondary
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (isValid)
                "Select exactly $runsPerWeek days \u00b7 $selectedCount selected"
            else
                "Select exactly $runsPerWeek days \u00b7 $selectedCount selected \u2715",
            style = MaterialTheme.typography.bodySmall,
            color = if (isValid) CardeaTheme.colors.textSecondary else ZoneRed,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DayLegendChip("open", DaySelectionLevel.NONE)
            DayLegendChip("run", DaySelectionLevel.AVAILABLE)
            DayLegendChip("long", DaySelectionLevel.LONG_RUN_BIAS)
            DayLegendChip("blocked", DaySelectionLevel.BLACKOUT)
        }

        DayChipRow(
            days = preferredDays,
            onCycleDay = onCycleDayPreference,
            onToggleBlackout = onToggleBlackoutDay
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
            Text("Back", color = CardeaTheme.colors.textSecondary)
        }
        CardeaButton(
            text = "Start Program",
            onClick = onComplete,
            enabled = isValid,
            modifier = Modifier
                .weight(2f)
                .height(48.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhaseDetailSheet(
    uiState: BootcampUiState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaTheme.colors.bgPrimary,
        dragHandle = { Box(Modifier.padding(vertical = 12.dp).width(32.dp).height(4.dp).clip(CircleShape).background(CardeaTheme.colors.glassBorder)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Training Plan Details",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = CardeaTheme.colors.textPrimary
            )

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhaseDetailRow("Phase", uiState.currentPhase?.name
                        ?.replace('_', ' ')
                        ?.lowercase()
                        ?.replaceFirstChar { it.uppercase() } ?: "\u2014")
                    HorizontalDivider(color = CardeaTheme.colors.glassBorder.copy(alpha = 0.5f))
                    PhaseDetailRow("Total Program", "Week ${uiState.absoluteWeek} of ${uiState.totalWeeks}")
                    HorizontalDivider(color = CardeaTheme.colors.glassBorder.copy(alpha = 0.5f))
                    PhaseDetailRow("Phase Progress", "Week ${uiState.weekInPhase}")
                    HorizontalDivider(color = CardeaTheme.colors.glassBorder.copy(alpha = 0.5f))
                    
                    val sessionsCount = uiState.currentWeekDays.count { it.session != null }
                    if (uiState.isRecoveryWeek) {
                        PhaseDetailRow("Intensity", "Recovery week")
                    } else {
                        PhaseDetailRow("Runs this week", "$sessionsCount scheduled")
                    }
                    
                    uiState.weeksUntilNextRecovery
                        ?.takeIf { it > 0 }
                        ?.let { 
                            HorizontalDivider(color = CardeaTheme.colors.glassBorder.copy(alpha = 0.5f))
                            PhaseDetailRow("Next recovery", "in $it week${if (it == 1) "" else "s"}") 
                        }
                }
            }

            Text(
                text = "Your plan adapts based on your fatigue (ATL) and fitness (CTL) signals from every run.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary,
                lineHeight = 18.sp
            )

            CardeaButton(
                text = "Got it",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                cornerRadius = 14.dp
            )
        }
    }
}

@Composable
private fun PhaseDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CardeaTheme.colors.textSecondary)
        Text(
            text = value, 
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), 
            color = CardeaTheme.colors.textPrimary
        )
    }
}

// ─── Active Dashboard ──────────────────────────────────────────────────────

@Composable
private fun ActiveBootcampDashboard(
    uiState: BootcampUiState,
    onRequestSession: (PlannedSession) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndProgram: () -> Unit,
    onDismissTierPrompt: () -> Unit,
    onAcceptTierChange: (TierPromptDirection) -> Unit,
    onConfirmIllness: () -> Unit,
    onDismissIllness: () -> Unit,
    onSwapTodayForRest: () -> Unit,
    onGraduateGoal: () -> Unit,
    onReschedule: (Long) -> Unit,
    onProgressClick: () -> Unit,
    onSessionClick: (SessionUiItem) -> Unit,
    onGoalClick: () -> Unit,
    onSavePreferredDays: (List<DayPreference>) -> Unit,
    onSettingsClick: () -> Unit,
    onPreferredDaysClick: () -> Unit,
    onGoToManualSetup: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        var missedDismissedIds by remember { mutableStateOf(emptySet<Long>()) }

        val todaySessionId = uiState.currentWeekDays.find { it.isToday }?.session?.sessionId

        // ── Today hero (full-bleed, no horizontal padding) ──────────────────
        TodayHeroSection(
            uiState = uiState,
            onRequestSession = onRequestSession,
            onReschedule = todaySessionId?.let { id -> { onReschedule(id) } },
            onSwapTodayForRest = onSwapTodayForRest,
            onPullForward = { sessionId -> onReschedule(sessionId) },
            onGoToManualSetup = onGoToManualSetup,
            onGoalClick = onGoalClick,
            onProgressClick = onProgressClick,
            onPreferredDaysClick = onPreferredDaysClick,
            onSettingsClick = onSettingsClick,
            onPause = onPause,
            onResume = onResume,
            onEndProgram = onEndProgram
        )

        // ── Cards with horizontal padding ────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WeekStripCard(
                days = uiState.currentWeekDays,
                dateRange = uiState.currentWeekDateRange,
                onSessionClick = onSessionClick
            )

            if (uiState.isPaused) {
                PausedCard(onResume = onResume)
            }

            // Show missed/deferred card for sessions needing attention
            val unresolvedIds = uiState.missedSessionIds.filterNot { it in missedDismissedIds }
            if (unresolvedIds.isNotEmpty()) {
                MissedSessionCard(
                    count = unresolvedIds.size,
                    onDismiss = { missedDismissedIds = missedDismissedIds + unresolvedIds.first() },
                    onReschedule = { onReschedule(unresolvedIds.first()) }
                )
            }

            if (uiState.tierPromptDirection != TierPromptDirection.NONE) {
                TierPromptCard(
                    direction = uiState.tierPromptDirection,
                    evidence = uiState.tierPromptEvidence,
                    onAccept = onAcceptTierChange,
                    onDismiss = onDismissTierPrompt
                )
            } else if (uiState.illnessFlag) {
                IllnessPromptCard(
                    onConfirm = onConfirmIllness,
                    onDismiss = onDismissIllness
                )
            }

            if (uiState.upcomingWeeks.isNotEmpty()) {
                ComingUpCard(weeks = uiState.upcomingWeeks)
            }

            if (uiState.showGraduationCta) {
                GraduationCard(onGraduateGoal = onGraduateGoal)
            }

            if (onGoToManualSetup != null) {
                TextButton(
                    onClick = onGoToManualSetup,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "Set up a manual run \u2192",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    detail: String
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CardeaTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTheme.colors.textSecondary
        )
    }
}

@Composable
private fun MissedSessionCard(
    count: Int,
    onDismiss: () -> Unit,
    onReschedule: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(end = 32.dp)) {
                Text(
                    text = if (count == 1) "Session to reschedule" else "$count sessions to reschedule",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = CardeaTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (count == 1)
                        "You have a session that needs your attention."
                    else
                        "You have $count sessions that are missed or deferred.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
                CardeaButton(
                    text = "Reschedule",
                    onClick = onReschedule,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = CardeaTheme.colors.textTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PausedCard(onResume: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Program paused",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CardeaTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your schedule is on hold. Resume whenever you're ready.",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(12.dp))
        CardeaButton(
            text = "Resume Program",
            onClick = onResume,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )
    }
}

@Composable
private fun PreferredDaysStrip(
    preferredDays: List<DayPreference>,
    modifier: Modifier = Modifier
) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val selected = preferredDays.map { it.day }.toSet()
    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, label ->
                val day = index + 1
                val enabled = selected.contains(day)
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .then(
                            if (enabled) Modifier.background(CardeaGradient)
                            else Modifier.background(CardeaTheme.colors.glassHighlight)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled) CardeaTheme.colors.textPrimary else CardeaTheme.colors.textTertiary
                    )
                }
            }
        }
        Text(
            text = "Tap to edit",
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ComingUpCard(weeks: List<UpcomingWeekItem>) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Show only the first upcoming week for density; rest accessible via progress view
        val displayWeeks = weeks.take(2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Coming Up",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTheme.colors.textPrimary
            )
            if (weeks.size > 2) {
                Text(
                    text = "${weeks.size} weeks ahead",
                    style = MaterialTheme.typography.labelSmall,
                    color = CardeaTheme.colors.textTertiary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        displayWeeks.forEachIndexed { weekIndex, week ->
            if (weekIndex > 0) {
                HorizontalDivider(color = CardeaTheme.colors.glassBorder, modifier = Modifier.padding(vertical = 6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Week ${week.weekNumber}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTheme.colors.textPrimary
                    )
                    Text(
                        text = if (week.isRecoveryWeek) "Recovery" else "Build",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (week.isRecoveryWeek) ZoneGreen else CardeaTheme.colors.textSecondary
                    )
                }

                val types = week.sessions
                    .map { it.typeName.split(" ")[0] }
                    .distinct()
                    .joinToString(", ")

                Text(
                    text = types,
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textTertiary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f).padding(start = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun GraduationCard(onGraduateGoal: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "You finished!",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CardeaTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "You've completed this goal. Graduate to unlock your next training block.",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        CardeaButton(
            text = "Graduate Goal",
            onClick = onGraduateGoal,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailSheet(
    session: SessionUiItem,
    maxHr: Int?,
    onDismiss: () -> Unit,
    onReschedule: ((Long) -> Unit)? = null,
    onSkip: ((Long) -> Unit)? = null,
    onStartRun: ((PlannedSession) -> Unit)? = null,
    onRestToday: (() -> Unit)? = null
) {
    val sessionId = session.sessionId
    // Determine which actions to show based on session state
    val canAct = sessionId != null && !session.isCompleted
    val showStartRun = canAct && session.isToday && onStartRun != null
    val showReschedule = canAct && onReschedule != null
    val showSkip = canAct && onSkip != null
    val showRestToday = canAct && session.isToday && onRestToday != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaTheme.colors.bgPrimary,
        dragHandle = { Box(Modifier.padding(vertical = 12.dp).width(32.dp).height(4.dp).clip(CircleShape).background(CardeaTheme.colors.glassBorder)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.typeName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTheme.colors.textPrimary
                    )
                    Text(
                        text = "${session.minutes} minute run",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CardeaTheme.colors.textSecondary
                    )
                    ZoneEducationProvider.forSessionType(
                        session.rawTypeName, ContentDensity.BADGE
                    )?.let { badge ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = CardeaTheme.colors.textTertiary
                        )
                    }
                }
                when {
                    session.isCompleted -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ZoneGreen.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Completed",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ZoneGreen
                            )
                        }
                    }
                    session.isDeferred -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ZoneAmber.copy(alpha = 0.1f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Deferred",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ZoneAmber
                            )
                        }
                    }
                    session.isPast -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ZoneRed.copy(alpha = 0.08f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Missed",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ZoneRed.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            GlassCard {
                Text(
                    text = SessionDescription.forType(session.rawTypeName, session.presetId, maxHr),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CardeaTheme.colors.textPrimary,
                    lineHeight = 22.sp
                )
            }

            Text(
                text = "Training targets will be calculated based on your current recovery state and historical zones.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textTertiary
            )

            // ── Contextual action buttons ────────────────────────────
            if (showStartRun) {
                androidx.compose.material3.Button(
                    onClick = {
                        onStartRun!!.invoke(
                            PlannedSession(
                                type = runCatching { SessionType.valueOf(session.rawTypeName) }
                                    .getOrDefault(SessionType.EASY),
                                minutes = session.minutes,
                                presetId = session.presetId
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardeaCtaGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Start run",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = CardeaTheme.colors.onGradient
                        )
                    }
                }
            }

            if (showReschedule || showSkip || showRestToday) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showReschedule) {
                        CardeaButton(
                            text = "Reschedule",
                            onClick = { onReschedule!!(sessionId!!) },
                            modifier = Modifier.weight(1f).height(44.dp)
                        )
                    }
                    if (showRestToday) {
                        TextButton(onClick = onRestToday!!) {
                            Text(
                                text = "Rest today",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTheme.colors.textTertiary
                            )
                        }
                    }
                    if (showSkip && !showRestToday) {
                        TextButton(onClick = { onSkip!!(sessionId!!) }) {
                            Text(
                                text = "Skip",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTheme.colors.textTertiary
                            )
                        }
                    }
                }
            }

            if (!showStartRun && !showReschedule && !showSkip) {
                CardeaButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }
        }
    }
}

private object SessionDescription {
    fun forType(rawType: String, presetId: String?, maxHr: Int? = null): String {
        val educationFull = ZoneEducationProvider.forSessionType(
            rawType, ContentDensity.FULL, maxHr
        )
        if (educationFull != null) return educationFull

        val type = runCatching { SessionType.valueOf(rawType) }.getOrNull()
        return when (type) {
            SessionType.STRIDES -> "Short, fast accelerations to improve your running form and neuromuscular coordination without accumulating significant fatigue."
            else -> "A specialized training session tailored to your current phase and fitness level."
        }
    }
}

// ─── Session-type ambient color mapping ─────────────────────────────────────

/** Returns ambient tint color for the hero background based on today's session type. */
private fun sessionAmbientColor(todayState: TodayState): Color = when (todayState) {
    is TodayState.RunUpcoming -> when (todayState.session.type) {
        SessionType.EASY, SessionType.STRIDES -> Color(0xFF0EA5E9) // Cool sky-teal
        SessionType.LONG -> Color(0xFF6366F1)                     // Deep indigo
        SessionType.TEMPO -> ZoneAmber                             // Warm amber
        SessionType.INTERVAL -> GradientPink                       // Hot pink
        SessionType.RACE_SIM -> GradientRed                        // Aggressive red
        SessionType.DISCOVERY, SessionType.CHECK_IN -> GradientBlue
    }
    is TodayState.RunDone -> ZoneGreen                             // Accomplished green
    is TodayState.RestDay -> Color.Transparent                     // No tint
}

// ─── Today Hero Section (replaces PhaseHeader + TodayContextCard) ───────────

@Composable
private fun TodayHeroSection(
    uiState: BootcampUiState,
    onRequestSession: (PlannedSession) -> Unit,
    onReschedule: (() -> Unit)?,
    onSwapTodayForRest: () -> Unit,
    onPullForward: ((Long) -> Unit)? = null,
    onGoToManualSetup: (() -> Unit)? = null,
    onGoalClick: () -> Unit,
    onProgressClick: () -> Unit,
    onPreferredDaysClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndProgram: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // Ambient color based on today's session type
    val ambientColor = sessionAmbientColor(uiState.todayState)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ambientColor.copy(alpha = 0.12f), Color.Transparent),
                    endY = 600f
                )
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

            // ── Compact header: title + goal + progress + menu ──────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: "Training" + goal name inline
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Training",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = CardeaTheme.colors.textPrimary
                    )
                    val goal = uiState.goal
                    if (goal != null) {
                        Text(
                            text = goalDisplayName(goal),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = GradientPink,
                            modifier = Modifier.clickable(onClick = onGoalClick)
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Program options",
                            tint = CardeaTheme.colors.textSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Program settings") },
                            onClick = { menuExpanded = false; onSettingsClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Preferred days") },
                            onClick = { menuExpanded = false; onPreferredDaysClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("View progress") },
                            onClick = { menuExpanded = false; onProgressClick() }
                        )
                        if (uiState.isPaused) {
                            DropdownMenuItem(
                                text = { Text("Resume program") },
                                onClick = { menuExpanded = false; onResume() }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Pause program") },
                                onClick = { menuExpanded = false; onPause() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("End program...", color = GradientRed) },
                            onClick = { menuExpanded = false; onEndProgram() }
                        )
                    }
                }
            }

            // ── Progress pills: week · phase · recovery · fitness ──────────
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(onClick = onProgressClick),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val phaseText = if (uiState.currentPhase != null)
                    "Wk ${uiState.absoluteWeek}/${uiState.totalWeeks} · ${uiState.currentPhase.displayName}"
                else
                    "Wk ${uiState.absoluteWeek}/${uiState.totalWeeks}"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardeaTheme.colors.glassHighlight)
                        .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = phaseText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = CardeaTheme.colors.textSecondary
                    )
                }
                if (uiState.isRecoveryWeek) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(ZoneGreen.copy(alpha = 0.12f))
                            .border(1.dp, ZoneGreen.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Recovery",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ZoneGreen
                        )
                    }
                }
                if (uiState.fitnessLevel != FitnessLevel.UNKNOWN) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardeaTheme.colors.glassHighlight)
                            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = uiState.fitnessLevel.name.lowercase()
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = CardeaTheme.colors.textSecondary
                        )
                    }
                }
            }

            // ── Program progress bar ────────────────────────────────────────
            Spacer(modifier = Modifier.height(10.dp))
            if (uiState.totalWeeks > 0) {
                val progress = uiState.absoluteWeek.toFloat() / uiState.totalWeeks.toFloat()
                val barGradient = Brush.linearGradient(
                    colors = listOf(GradientPink, GradientBlue)
                )
                val trackColor = CardeaTheme.colors.glassBorder
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .clickable(onClick = onProgressClick)
                ) {
                    // Track background
                    drawRoundRect(
                        color = trackColor,
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                    )
                    // Filled progress
                    drawRoundRect(
                        brush = barGradient,
                        size = size.copy(width = size.width * progress),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // ── State-dependent hero content ───────────────────────────────
            when {
                uiState.isPaused -> {
                    Text(
                        text = "Program paused",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = CardeaTheme.colors.textPrimary
                    )
                    Text(
                        text = "Your schedule is on hold. Resume whenever you're ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                else -> when (val today = uiState.todayState) {
                    is TodayState.RunUpcoming -> {
                        val sessionLabel = SessionType.displayLabelForPreset(today.session.presetId)
                            ?: today.session.type.name
                                .lowercase()
                                .replaceFirstChar { it.uppercase() }
                        // Session name + duration on one row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = sessionLabel,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = CardeaTheme.colors.textPrimary
                            )
                            Text(
                                text = "${today.session.minutes} min",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = CardeaTheme.colors.textSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        // Zone badge + one-liner description inline
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val badge = ZoneEducationProvider.forSessionType(
                                today.session.type.name, ContentDensity.BADGE
                            )
                            if (badge != null) MetaChip(badge)
                            ZoneEducationProvider.forSessionType(
                                today.session.type.name, ContentDensity.ONE_LINER
                            )?.let { oneLiner ->
                                var expanded by remember { mutableStateOf(false) }
                                Text(
                                    text = oneLiner,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = CardeaTheme.colors.textTertiary,
                                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { expanded = !expanded }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        // Breathing scale CTA — subtle "ready to tap" pulse
                        val ctaBreathe = rememberInfiniteTransition(label = "ctaBreathe")
                        val ctaScale by ctaBreathe.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.015f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(3000, easing = FastOutSlowInEasing),
                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                            ),
                            label = "ctaScale"
                        )
                        androidx.compose.material3.Button(
                            onClick = {
                                onRequestSession(today.session)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .scale(ctaScale),
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CardeaCtaGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Start run",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = CardeaTheme.colors.onGradient
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (onReschedule != null) {
                                TextButton(
                                    onClick = onReschedule,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = "Reschedule",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CardeaTheme.colors.textSecondary
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(1.dp))
                            }
                            TextButton(
                                onClick = onSwapTodayForRest,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "Rest today",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CardeaTheme.colors.textSecondary
                                )
                            }
                        }
                    }

                    is TodayState.RunDone -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "\u2713",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = ZoneGreen
                            )
                            Text(
                                text = "Today's run is done",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = CardeaTheme.colors.textPrimary
                            )
                        }
                        val nextLabel = today.nextSession?.let { s ->
                            SessionType.displayLabelForPreset(s.presetId)
                                ?: s.type.name.lowercase().replaceFirstChar { it.uppercase() }
                        }
                        Text(
                            text = if (nextLabel != null)
                                "Next: $nextLabel · ${today.nextSessionDayLabel ?: ""}${today.nextSessionRelativeLabel?.let { " · $it" } ?: ""}"
                            else
                                "Week complete — great work!",
                            style = MaterialTheme.typography.bodySmall,
                            color = CardeaTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        // ── Pull forward next run ────────────────────────
                        if (today.nextFutureSessionId != null && onPullForward != null) {
                            TextButton(
                                onClick = { onPullForward(today.nextFutureSessionId) },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = "Pull forward next run",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CardeaTheme.colors.textSecondary
                                )
                            }
                        }
                    }

                    is TodayState.RestDay -> {
                        Text(
                            text = "Rest day",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = CardeaTheme.colors.textPrimary
                        )
                        val nextLabel = today.nextSession?.let { s ->
                            SessionType.displayLabelForPreset(s.presetId)
                                ?: s.type.name.lowercase().replaceFirstChar { it.uppercase() }
                        }
                        Text(
                            text = if (nextLabel != null)
                                "Next: $nextLabel · ${today.nextSessionDayLabel ?: ""}${today.nextSessionRelativeLabel?.let { " · $it" } ?: ""}"
                            else
                                "Week complete — great work!",
                            style = MaterialTheme.typography.bodySmall,
                            color = CardeaTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        // ── Action row: pull forward + manual run ────────
                        if (today.nextFutureSessionId != null || onGoToManualSetup != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (today.nextFutureSessionId != null && onPullForward != null) {
                                    TextButton(
                                        onClick = { onPullForward(today.nextFutureSessionId) },
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = "Pull forward next run",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CardeaTheme.colors.textSecondary
                                        )
                                    }
                                }
                                if (onGoToManualSetup != null) {
                                    TextButton(
                                        onClick = onGoToManualSetup,
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = "Manual run \u2192",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = CardeaTheme.colors.textTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = CardeaTheme.colors.textSecondary
        )
    }
}


@Composable
private fun TierPromptCard(
    direction: TierPromptDirection,
    evidence: String?,
    onAccept: (TierPromptDirection) -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (direction == TierPromptDirection.UP) "Progression available" else "Step-back recommended"
    val body = if (direction == TierPromptDirection.UP) {
        "Your recent load has remained high enough to support a tier increase."
    } else {
        "Your recent load has stayed below your current tier's target range."
    }
    val actionLabel = if (direction == TierPromptDirection.UP) "Increase Tier" else "Lower Tier"

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CardeaTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTheme.colors.textSecondary
        )
        if (!evidence.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = evidence,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textTertiary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Not now", color = CardeaTheme.colors.textSecondary)
            }
            CardeaButton(
                text = actionLabel,
                onClick = { onAccept(direction) },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )
        }
    }
}

@Composable
private fun IllnessPromptCard(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Check in with your body",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CardeaTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Recent signals look atypical. If you're getting sick, keep today's effort easy.",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Dismiss", color = CardeaTheme.colors.textSecondary)
            }
            CardeaButton(
                text = "I'm unwell",
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            )
        }
    }
}

// ─── Reschedule Bottom Sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RescheduleBottomSheet(
    autoTargetLabel: String?,
    availableDays: List<Int>,
    availableLabels: List<String>,
    onConfirm: (Int?) -> Unit,
    onDefer: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaTheme.colors.bgPrimary,
        dragHandle = null,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Reschedule Run",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = CardeaTheme.colors.textPrimary
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            if (autoTargetLabel != null) {
                Text(
                    text = "Recommended for $autoTargetLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CardeaTheme.colors.textSecondary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                CardeaButton(
                    text = "Sounds good",
                    onClick = { onConfirm(null) }, // Use auto target
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )
            } else {
                Text(
                    text = "No other preferred slots this week.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CardeaTheme.colors.textSecondary
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                CardeaButton(
                    text = "Drop for this week",
                    onClick = { onConfirm(null) }, // Drops the session
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )
            }
            
            if (availableDays.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Or pick another day",
                    style = MaterialTheme.typography.labelSmall,
                    color = CardeaTheme.colors.textTertiary
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableDays.forEachIndexed { index, day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardeaTheme.colors.glassHighlight)
                                .clickable { onConfirm(day) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = availableLabels[index],
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = CardeaTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = onDefer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "I'm not sure yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textTertiary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferredDaysBottomSheet(
    currentDays: List<DayPreference>,
    onSave: (List<DayPreference>) -> Unit,
    onDismiss: () -> Unit
) {
    var stagedDays by remember(currentDays) { mutableStateOf(currentDays) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CardeaTheme.colors.bgPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Preferred training days",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap to toggle · Long-press to block a day out",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                DayLegendChip("run", DaySelectionLevel.AVAILABLE)
                DayLegendChip("open", DaySelectionLevel.NONE)
                DayLegendChip("blocked", DaySelectionLevel.BLACKOUT)
            }
            DayChipRow(
                days = stagedDays,
                onCycleDay = { day -> stagedDays = cycleDayInList(stagedDays, day) },
                onToggleBlackout = { day -> stagedDays = toggleBlackoutInList(stagedDays, day) }
            )
            Spacer(modifier = Modifier.height(24.dp))
            CardeaButton(
                text = "Done",
                onClick = { onSave(stagedDays) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayChipRow(
    days: List<DayPreference>,
    onCycleDay: (Int) -> Unit,
    onToggleBlackout: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (1..7).forEach { day ->
            val level = days.firstOrNull { it.day == day }?.level ?: DaySelectionLevel.NONE
            val isSelected = level == DaySelectionLevel.AVAILABLE || level == DaySelectionLevel.LONG_RUN_BIAS
            val isBlackout = level == DaySelectionLevel.BLACKOUT
            val isLongRun = level == DaySelectionLevel.LONG_RUN_BIAS
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        when {
                            isBlackout -> Modifier.background(CardeaTheme.colors.blackoutBg)
                            isSelected -> Modifier.background(CardeaGradient)
                            else       -> Modifier.border(1.dp, CardeaTheme.colors.glassBorder, CircleShape)
                        }
                    )
                    .combinedClickable(
                        onClick = { onCycleDay(day) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleBlackout(day)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayLetters[day - 1],
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isBlackout) CardeaTheme.colors.blackoutText else CardeaTheme.colors.textPrimary
                )
                if (isLongRun) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = CardeaTheme.colors.onGradient,
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-3).dp, y = 3.dp)
                    )
                }
                if (isBlackout) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = CardeaTheme.colors.blackoutText,
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = (-3).dp, y = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayLegendChip(label: String, level: DaySelectionLevel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .then(
                    when (level) {
                        DaySelectionLevel.AVAILABLE,
                        DaySelectionLevel.LONG_RUN_BIAS -> Modifier.background(CardeaGradient)
                        DaySelectionLevel.BLACKOUT      -> Modifier
                            .background(CardeaTheme.colors.blackoutBg)
                            .border(1.dp, CardeaTheme.colors.blackoutBorder, CircleShape)
                        DaySelectionLevel.NONE          -> Modifier.background(CardeaTheme.colors.glassHighlight)
                    }
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTheme.colors.textTertiary
        )
    }
}

private fun cycleDayInList(days: List<DayPreference>, day: Int): List<DayPreference> {
    val current = days.toMutableList()
    val index = current.indexOfFirst { it.day == day }
    if (index != -1) {
        val nextLevel = current[index].level.next()
        if (nextLevel == DaySelectionLevel.NONE) {
            current.removeAt(index)
        } else {
            current[index] = current[index].copy(level = nextLevel)
        }
    } else {
        current.add(DayPreference(day, DaySelectionLevel.AVAILABLE))
    }
    return current.sortedBy { it.day }
}

private fun toggleBlackoutInList(days: List<DayPreference>, day: Int): List<DayPreference> {
    val current = days.toMutableList()
    val index = current.indexOfFirst { it.day == day }
    return if (index != -1 && current[index].level == DaySelectionLevel.BLACKOUT) {
        current.removeAt(index)
        current.sortedBy { it.day }
    } else {
        if (index != -1) current.removeAt(index)
        current.add(DayPreference(day, DaySelectionLevel.BLACKOUT))
        current.sortedBy { it.day }
    }
}

// ─── Welcome Back Dialog ────────────────────────────────────────────────────

@Composable
private fun WelcomeBackDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = CardeaTheme.colors.textPrimary
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTheme.colors.textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = GradientPink)
            }
        },
        containerColor = CardeaTheme.colors.bgPrimary,
        shape = RoundedCornerShape(18.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDetailSheet(
    goal: BootcampGoal,
    progressPercentage: Int,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaTheme.colors.bgPrimary,
        dragHandle = { Box(Modifier.padding(vertical = 12.dp).width(32.dp).height(4.dp).clip(CircleShape).background(CardeaTheme.colors.glassBorder)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Program Milestone",
                style = MaterialTheme.typography.labelLarge,
                color = GradientPink
            )
            
            Text(
                text = goalDisplayName(goal),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = CardeaTheme.colors.textPrimary
            )

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Overall Progress",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CardeaTheme.colors.textSecondary
                        )
                        Text(
                            text = "$progressPercentage%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = CardeaTheme.colors.textPrimary
                        )
                    }
                    
                    // Progress bar - core gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(CardeaTheme.colors.glassBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressPercentage / 100f)
                                .fillMaxHeight()
                                .background(CardeaGradient)
                        )
                    }
                }
            }

            GlassCard {
                Text(
                    text = GoalDescription.forGoal(goal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CardeaTheme.colors.textPrimary,
                    lineHeight = 22.sp
                )
            }

            Text(
                text = "You're training at a tier that balances your historical volume with your current recovery capacity.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textTertiary,
                lineHeight = 18.sp
            )

            CardeaButton(
                text = "Back to Training",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        }
    }
}

private object GoalDescription {
    fun forGoal(goal: BootcampGoal): String = when (goal) {
        BootcampGoal.CARDIO_HEALTH -> "Focuses on heart health and consistency. This program uses aerobic base training to lower your resting heart rate and improve daily energy."
        BootcampGoal.RACE_5K -> "Built for speed. VO2max intervals and fast finishes to sharpen your kick and get you across the 5K line with a new personal best."
        BootcampGoal.RACE_10K -> "Built for stamina. Threshold tempo work and sustained efforts to build the endurance engine that carries you through 10 kilometers."
        BootcampGoal.HALF_MARATHON -> "A balanced approach to distance. Bridges the gap between speed and endurance, preparing your body for 13.1 miles of sustained effort."
        BootcampGoal.MARATHON -> "The ultimate endurance challenge. This plan prioritizes weekly volume and long runs to build the physiological resilience needed for 26.2 miles."
    }
}

// ─── Helpers ────────────────────────────────────────────────────────────────

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "End this program?",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = CardeaTheme.colors.textPrimary
            )
        },
        text = {
            Text(
                text = "Your schedule and progress will be permanently deleted. Your completed run history stays in the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTheme.colors.textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("End Program", color = GradientRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep it", color = CardeaTheme.colors.textSecondary)
            }
        },
        containerColor = CardeaTheme.colors.bgPrimary,
        shape = RoundedCornerShape(18.dp)
    )
}

private fun goalDisplayName(goal: BootcampGoal): String = when (goal) {
    BootcampGoal.CARDIO_HEALTH -> "Cardio Health"
    BootcampGoal.RACE_5K -> "5K"
    BootcampGoal.RACE_10K -> "10K"
    BootcampGoal.HALF_MARATHON -> "Half Marathon"
    BootcampGoal.MARATHON -> "Marathon"
}

// ─── MaxHR Gate Sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaxHrGateSheet(
    input: String,
    error: String?,
    onInputChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaTheme.colors.bgPrimary,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Set Your Max Heart Rate",
                style = MaterialTheme.typography.titleMedium,
                color = CardeaTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Your training zones are personalised using max HR. Set it now to get accurate coaching during your run.",
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTheme.colors.textSecondary
            )

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChanged,
                        label = { Text("Max HR (bpm)") },
                        placeholder = { Text("e.g. 185") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = error != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = error
                            ?: "220 minus your age is a good starting estimate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) ZoneRed else CardeaTheme.colors.textTertiary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardeaGradient)
                    .clickable { onConfirm() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save & Start Run",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ─── HR Monitor Connection Sheet ────────────────────────────────────────────

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HrConnectDialog(
    uiState: BootcampUiState,
    onScan: () -> Unit,
    onConnect: (android.bluetooth.BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onStartWithMonitor: () -> Unit,
    onStartWithout: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaTheme.colors.bgPrimary,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(CardeaTheme.colors.glassBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Title ────────────────────────────────────────────
            Text(
                text = "HR Monitor",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = CardeaTheme.colors.textPrimary
            )

            if (uiState.bleIsConnected) {
                // ── Connected state ──────────────────────────────
                HrConnectedCard(uiState, onDisconnect)

                // ── Start CTA ────────────────────────────────────
                CardeaButton(
                    text = "Start Run",
                    onClick = onStartWithMonitor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )
            } else {
                // ── Reconnecting indicator ───────────────────────
                if (uiState.bleLastKnownDeviceName != null && !uiState.bleIsScanning &&
                    uiState.bleDiscoveredDevices.isEmpty()
                ) {
                    HrReconnectingCard(uiState.bleLastKnownDeviceName)
                }

                // ── Scanning indicator ───────────────────────────
                if (uiState.bleIsScanning) {
                    HrScanningIndicator()
                }

                // ── Discovered devices ───────────────────────────
                if (uiState.bleDiscoveredDevices.isNotEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "NEARBY DEVICES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CardeaTheme.colors.textTertiary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        uiState.bleDiscoveredDevices.forEachIndexed { index, device ->
                            if (index > 0) {
                                HorizontalDivider(color = CardeaTheme.colors.glassBorder)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onConnect(device) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = GradientPink,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name ?: "Unknown Device",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = CardeaTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = device.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CardeaTheme.colors.textTertiary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Connect",
                                    tint = CardeaTheme.colors.textTertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // ── Error ────────────────────────────────────────
                uiState.bleConnectionError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZoneRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── Scan button ──────────────────────────────────
                if (!uiState.bleIsScanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(14.dp))
                            .background(CardeaTheme.colors.glassHighlight)
                            .clickable(onClick = onScan),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = CardeaTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (uiState.bleDiscoveredDevices.isNotEmpty()) "Scan Again" else "Scan for Monitors",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = CardeaTheme.colors.textPrimary
                            )
                        }
                    }
                }

                // ── Skip CTA ─────────────────────────────────────
                TextButton(onClick = onStartWithout) {
                    Text(
                        text = "Start without monitor",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CardeaTheme.colors.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun HrConnectedCard(uiState: BootcampUiState, onDisconnect: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Pulsing green dot
            val pulseAnim = androidx.compose.animation.core.rememberInfiniteTransition(label = "hrPulse")
            val pulseScale by pulseAnim.animateFloat(
                initialValue = 1f,
                targetValue = 1.4f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(800),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "pulse"
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(CardeaTheme.colors.zoneGreen)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.bleConnectedDeviceName.ifBlank { "HR Monitor" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = CardeaTheme.colors.textPrimary
                )
                Text(
                    text = if (uiState.bleLiveHr > 0) "${uiState.bleLiveHr} bpm" else "Waiting for signal\u2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.bleLiveHr > 0) CardeaTheme.colors.zoneGreen
                    else CardeaTheme.colors.textSecondary
                )
            }
            TextButton(onClick = onDisconnect) {
                Text("Disconnect", color = CardeaTheme.colors.textTertiary)
            }
        }
    }
}

@Composable
private fun HrReconnectingCard(deviceName: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = GradientPink
            )
            Column {
                Text(
                    text = "Reconnecting\u2026",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = CardeaTheme.colors.textPrimary
                )
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun HrScanningIndicator() {
    val scanAnim = androidx.compose.animation.core.rememberInfiniteTransition(label = "scanPulse")
    val alpha by scanAnim.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "scanAlpha"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { this.alpha = alpha },
            strokeWidth = 2.dp,
            color = GradientPink
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Scanning for nearby monitors\u2026",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTheme.colors.textSecondary,
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
        )
    }
}
