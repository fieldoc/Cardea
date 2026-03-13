package com.hrcoach.ui.bootcamp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.domain.bootcamp.PlannedSession
import com.hrcoach.domain.bootcamp.SessionType
import com.hrcoach.domain.engine.TierPromptDirection
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaCtaGradient
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GlassHighlight
import com.hrcoach.ui.theme.GlassSurface
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.ZoneGreen

import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun BootcampScreen(
    onStartWorkout: (configJson: String) -> Unit,
    onBack: () -> Unit,
    onGoToSettings: () -> Unit,
    onGoToManualSetup: (() -> Unit)? = null,
    viewModel: BootcampViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPhaseDetail by remember { mutableStateOf(false) }
    var showDaysSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaBgPrimary)
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

            uiState.showOnboarding -> {
                OnboardingWizard(
                    uiState = uiState,
                    step = uiState.onboardingStep,
                    onStepChange = viewModel::setOnboardingStep,
                    onGoalSelected = { viewModel.setOnboardingGoal(it) },
                    onMinutesChanged = { viewModel.setOnboardingMinutes(it) },
                    onRunsPerWeekChanged = { viewModel.setOnboardingRunsPerWeek(it) },
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
                    onStartWorkout = onStartWorkout,
                    onPause = { viewModel.pauseBootcamp() },
                    onResume = { viewModel.resumeBootcamp() },
                    onEndProgram = { viewModel.showDeleteConfirmDialog() },
                    onBootcampWorkoutStarting = viewModel::onBootcampWorkoutStarting,
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
                onDismiss = viewModel::dismissSessionDetail
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
            color = CardeaTextPrimary
        )
        Text(
            text = "A program that adapts to your goal, your schedule, and your life.",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTextSecondary,
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
                color = CardeaTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary
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
            tint = CardeaTextPrimary,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTextPrimary
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary
            )
        }
    }
}

@Composable
private fun WeekStripCard(
    days: List<WeekDayItem>,
    dateRange: String
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        // Header: "This Week" + date range
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "This Week",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTextPrimary
            )
            Text(
                text = dateRange,
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTextTertiary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 7-day pill row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            days.forEach { day ->
                WeekDayPill(day = day)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Tick + glow timeline indicator
        val todayIndex = days.indexOfFirst { it.isToday }.takeIf { it >= 0 } ?: 0
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val pillHalfWidth = 15.dp.toPx()
            val trackStart = pillHalfWidth
            val trackEnd = size.width - pillHalfWidth
            val trackY = size.height / 2f
            val tickX = trackStart + (trackEnd - trackStart) * todayIndex / 6f

            // Dim track
            drawLine(
                color = GlassBorder,
                start = Offset(trackStart, trackY),
                end = Offset(trackEnd, trackY),
                strokeWidth = 1.dp.toPx()
            )

            // Radial glow bloom at today
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GlassSurface,
                        Color.Transparent
                    ),
                    center = Offset(tickX, trackY),
                    radius = 9.dp.toPx()
                ),
                radius = 9.dp.toPx(),
                center = Offset(tickX, trackY)
            )

            // Tick line
            drawLine(
                color = CardeaTextSecondary,
                start = Offset(tickX, trackY - 4.5.dp.toPx()),
                end = Offset(tickX, trackY + 4.5.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun WeekDayPill(day: WeekDayItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Day letter
        Text(
            text = day.dayLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = CardeaTextTertiary
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
                dotBorder = GlassBorder
                dotContent = {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = CardeaTextTertiary.copy(alpha = 0.4f)
                    )
                }
            }
            session == null -> {
                dotBackground = GlassHighlight.copy(alpha = 0.4f)
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
                dotBackground = GlassHighlight
                dotBorder = GlassBorder
                dotContent = {
                    Text(
                        text = session.rawTypeName.take(2),
                        style = MaterialTheme.typography.labelSmall,
                        color = CardeaTextTertiary
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(dotBackground)
                .then(if (dotBorder != Color.Transparent) Modifier.border(1.dp, dotBorder, RoundedCornerShape(7.dp)) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            dotContent()
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
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
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
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .then(
                            if (index <= step) Modifier.background(CardeaGradient)
                            else Modifier.background(GlassBorder)
                        )
                )
            }
        }

        when (step) {
            0 -> OnboardingStep1Goal(
                selectedGoal = uiState.onboardingGoal,
                onGoalSelected = { onGoalSelected(it) },
                onNext = { if (uiState.onboardingGoal != null) onStepChange(1) },
                onBack = onBack
            )
            1 -> OnboardingStep2Time(
                minutes = uiState.onboardingMinutes,
                warning = uiState.onboardingTimeWarning,
                longRunMinutes = uiState.onboardingLongRunMinutes,
                weeklyTotal = uiState.onboardingWeeklyTotal,
                longRunWarning = uiState.onboardingLongRunWarning,
                onMinutesChanged = onMinutesChanged,
                onNext = { onStepChange(2) },
                onBack = { onStepChange(0) }
            )
            2 -> OnboardingStep3Frequency(
                runsPerWeek = uiState.onboardingRunsPerWeek,
                onRunsPerWeekChanged = onRunsPerWeekChanged,
                onComplete = onComplete,
                onBack = { onStepChange(1) }
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
        color = CardeaTextPrimary
    )
    Text(
        text = "Choose the program that matches your ambition.",
        style = MaterialTheme.typography.bodyMedium,
        color = CardeaTextSecondary
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
                        color = CardeaTextPrimary
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTextSecondary
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
            Text("Back", color = CardeaTextSecondary)
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
        color = CardeaTextPrimary
    )
    Text(
        text = "Set the length of a typical easy run.",
        style = MaterialTheme.typography.bodyMedium,
        color = CardeaTextSecondary
    )

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$minutes min",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = CardeaTextPrimary
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
                inactiveTrackColor = GlassHighlight
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("15 min", style = MaterialTheme.typography.labelSmall, color = CardeaTextTertiary)
            Text("90 min", style = MaterialTheme.typography.labelSmall, color = CardeaTextTertiary)
        }
        HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Long run: ~$longRunMinutes min",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary
            )
            Text(
                text = "Weekly: ~$weeklyTotal min",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextTertiary
            )
        }
    }

    if (warning != null) {
        Text(
            text = warning,
            style = MaterialTheme.typography.bodySmall,
            color = GradientRed
        )
    }
    if (longRunWarning != null) {
        Text(
            text = longRunWarning,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFB74D)
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
            Text("Back", color = CardeaTextSecondary)
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
private fun OnboardingStep3Frequency(
    runsPerWeek: Int,
    onRunsPerWeekChanged: (Int) -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    Text(
        text = "Runs per week",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = CardeaTextPrimary
    )
    Text(
        text = "How often will you run?",
        style = MaterialTheme.typography.bodyMedium,
        color = CardeaTextSecondary
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
                            else Modifier.background(GlassHighlight)
                        )
                        .clickable { onRunsPerWeekChanged(runs) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labels[index],
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) CardeaTextPrimary else CardeaTextSecondary
                    )
                }
            }
        }
        Text(
            text = "runs / week",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTextTertiary
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
            Text("Back", color = CardeaTextSecondary)
        }
        CardeaButton(
            text = "Start Program",
            onClick = onComplete,
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
        containerColor = CardeaBgPrimary,
        dragHandle = { Box(Modifier.padding(vertical = 12.dp).width(32.dp).height(4.dp).clip(CircleShape).background(GlassBorder)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Training Plan Details",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = CardeaTextPrimary
            )

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhaseDetailRow("Phase", uiState.currentPhase?.name
                        ?.replace('_', ' ')
                        ?.lowercase()
                        ?.replaceFirstChar { it.uppercase() } ?: "\u2014")
                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))
                    PhaseDetailRow("Total Program", "Week ${uiState.absoluteWeek} of ${uiState.totalWeeks}")
                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))
                    PhaseDetailRow("Phase Progress", "Week ${uiState.weekInPhase}")
                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))
                    
                    val sessionsCount = uiState.currentWeekDays.count { it.session != null }
                    if (uiState.isRecoveryWeek) {
                        PhaseDetailRow("Intensity", "Recovery week")
                    } else {
                        PhaseDetailRow("Runs this week", "$sessionsCount scheduled")
                    }
                    
                    uiState.weeksUntilNextRecovery
                        ?.takeIf { it > 0 }
                        ?.let { 
                            HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))
                            PhaseDetailRow("Next recovery", "in $it week${if (it == 1) "" else "s"}") 
                        }
                }
            }

            Text(
                text = "Your plan adapts based on your fatigue (ATL) and fitness (CTL) signals from every run.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary,
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
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CardeaTextSecondary)
        Text(
            text = value, 
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), 
            color = CardeaTextPrimary
        )
    }
}

// ─── Active Dashboard ──────────────────────────────────────────────────────

@Composable
private fun ActiveBootcampDashboard(
    uiState: BootcampUiState,
    onStartWorkout: (configJson: String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndProgram: () -> Unit,
    onBootcampWorkoutStarting: () -> Unit,
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
        var missedDismissed by remember { mutableStateOf(false) }

        val todaySessionId = uiState.currentWeekDays.find { it.isToday }?.session?.sessionId

        // ── Today hero (full-bleed, no horizontal padding) ──────────────────
        TodayHeroSection(
            uiState = uiState,
            onStartWorkout = onStartWorkout,
            onBootcampWorkoutStarting = onBootcampWorkoutStarting,
            onReschedule = todaySessionId?.let { id -> { onReschedule(id) } },
            onSwapTodayForRest = onSwapTodayForRest,
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
                .padding(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WeekStripCard(
                days = uiState.currentWeekDays,
                dateRange = uiState.currentWeekDateRange
            )

            if (uiState.isPaused) {
                PausedCard(onResume = onResume)
            }

            if (uiState.missedSession && !missedDismissed) {
                val todayDow = uiState.currentWeekDays.firstOrNull { it.isToday }?.dayOfWeek ?: 8
                val missedId = uiState.currentWeekDays
                    .firstOrNull { it.dayOfWeek < todayDow && it.session != null && !it.session.isCompleted }
                    ?.session?.sessionId
                MissedSessionCard(
                    onDismiss = { missedDismissed = true },
                    onReschedule = { if (missedId != null) onReschedule(missedId) }
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
                        color = CardeaTextTertiary
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
            color = CardeaTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTextSecondary
        )
    }
}

@Composable
private fun MissedSessionCard(
    onDismiss: () -> Unit,
    onReschedule: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(end = 32.dp)) {
                Text(
                    text = "Missed session detected",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = CardeaTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "You have at least one earlier session this week that is still incomplete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTextSecondary
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
                    tint = CardeaTextTertiary,
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
            color = CardeaTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your schedule is on hold. Resume whenever you're ready.",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTextSecondary
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
                            else Modifier.background(GlassHighlight)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled) CardeaTextPrimary else CardeaTextTertiary
                    )
                }
            }
        }
        Text(
            text = "Tap to edit",
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTextTertiary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ComingUpCard(weeks: List<UpcomingWeekItem>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Coming Up",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CardeaTextPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))
        weeks.forEachIndexed { weekIndex, week ->
            if (weekIndex > 0) {
                HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 10.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Week ${week.weekNumber}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTextPrimary
                    )
                    Text(
                        text = if (week.isRecoveryWeek) "Recovery week" else "Build week",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (week.isRecoveryWeek) ZoneGreen else CardeaTextSecondary
                    )
                }
                
                val types = week.sessions
                    .map { it.typeName.split(" ")[0] }
                    .distinct()
                    .joinToString(", ")
                
                Text(
                    text = types,
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTextTertiary,
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
            color = CardeaTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "You've completed this goal. Graduate to unlock your next training block.",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTextSecondary
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
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaBgPrimary,
        dragHandle = { Box(Modifier.padding(vertical = 12.dp).width(32.dp).height(4.dp).clip(CircleShape).background(GlassBorder)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
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
                        color = CardeaTextPrimary
                    )
                    Text(
                        text = "${session.minutes} minute run",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CardeaTextSecondary
                    )
                }
                if (session.isCompleted) {
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
            }

            GlassCard {
                Text(
                    text = SessionDescription.forType(session.rawTypeName, session.presetId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CardeaTextPrimary,
                    lineHeight = 22.sp
                )
            }

            Text(
                text = "Training targets will be calculated based on your current recovery state and historical zones.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextTertiary
            )

            CardeaButton(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        }
    }
}

private object SessionDescription {
    fun forType(rawType: String, presetId: String?): String {
        val type = runCatching { SessionType.valueOf(rawType) }.getOrNull()
        
        return when (type) {
            SessionType.EASY -> "A conversational, low-intensity run. These miles build your aerobic base and strengthen your heart without stressing your recovery."
            SessionType.LONG -> "The cornerstone of endurance training. Long runs teach your body to burn fat efficiently and build the mental stamina needed for your goal distance."
            SessionType.TEMPO -> "A 'comfortably hard' effort at Zone 3. This improves your lactate threshold, allowing you to run faster for longer periods."
            SessionType.INTERVAL -> "High-intensity bursts at Zone 5 followed by recovery. These sessions increase your VO2 max and top-end speed."
            SessionType.STRIDES -> "Short, fast accelerations to improve your running form and efficiency without building up significant fatigue."
            SessionType.RACE_SIM -> "A dress rehearsal for your goal. Practice your target pace and fueling strategy to build confidence for race day."
            SessionType.DISCOVERY -> "A test run to calibrate your zones. We'll use the data from this session to personalize your entire program."
            SessionType.CHECK_IN -> "A brief assessment of your current fitness. This helps the engine decide if you're ready to move up a tier."
            else -> "A specialized training session tailored to your current phase and fitness level."
        }
    }
}

// ─── Today Hero Section (replaces PhaseHeader + TodayContextCard) ───────────

@Composable
private fun TodayHeroSection(
    uiState: BootcampUiState,
    onStartWorkout: (configJson: String) -> Unit,
    onBootcampWorkoutStarting: () -> Unit,
    onReschedule: (() -> Unit)?,
    onSwapTodayForRest: () -> Unit,
    onGoalClick: () -> Unit,
    onProgressClick: () -> Unit,
    onPreferredDaysClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndProgram: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientRed.copy(alpha = 0.10f), Color.Transparent),
                    endY = 600f
                )
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {

            // ── Top row: "Training" title + ⋮ menu ─────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Training",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = CardeaTextPrimary
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Program options",
                            tint = CardeaTextSecondary
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

            // ── Goal name (gradient, clickable) ────────────────────────────
            val goal = uiState.goal
            if (goal != null) {
                Text(
                    text = goalDisplayName(goal),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = GradientPink,
                    modifier = Modifier
                        .clickable(onClick = onGoalClick)
                        .padding(top = 2.dp)
                )
            }

            // ── Progress pill: week X of Y · phase ─────────────────────────
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable(onClick = onProgressClick),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val phaseText = if (uiState.currentPhase != null)
                    "Week ${uiState.absoluteWeek} of ${uiState.totalWeeks} · ${uiState.currentPhase.displayName}"
                else
                    "Week ${uiState.absoluteWeek} of ${uiState.totalWeeks}"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x0EFFFFFF))
                        .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = phaseText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = CardeaTextSecondary
                    )
                }
                if (uiState.isRecoveryWeek) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(ZoneGreen.copy(alpha = 0.12f))
                            .border(1.dp, ZoneGreen.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Recovery",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ZoneGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ── State-dependent hero content ───────────────────────────────
            when {
                uiState.isPaused -> {
                    Text(
                        text = "Program paused",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = CardeaTextPrimary
                    )
                    Text(
                        text = "Your schedule is on hold. Resume whenever you're ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                else -> when (val today = uiState.todayState) {
                    is TodayState.RunUpcoming -> {
                        Text(
                            text = "TODAY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                fontSize = 10.sp
                            ),
                            color = CardeaTextTertiary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val sessionLabel = SessionType.displayLabelForPreset(today.session.presetId)
                            ?: today.session.type.name
                                .lowercase()
                                .replaceFirstChar { it.uppercase() }
                        Text(
                            text = sessionLabel,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = CardeaTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetaChip("${today.session.minutes} min")
                            val desc = SessionDescription.forType(
                                today.session.type.name,
                                today.session.presetId
                            )
                            if (desc.isNotBlank()) MetaChip(desc.take(24))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.Button(
                            onClick = {
                                onBootcampWorkoutStarting()
                                onStartWorkout(buildConfigJson(today.session))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
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
                                    color = Color.White
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (onReschedule != null) {
                                TextButton(
                                    onClick = onReschedule,
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = "Reschedule",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CardeaTextSecondary
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(1.dp))
                            }
                            TextButton(
                                onClick = onSwapTodayForRest,
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = "Rest today",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CardeaTextTertiary
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
                                text = "✓",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = ZoneGreen
                            )
                            Text(
                                text = "Today's run is done",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                ),
                                color = CardeaTextPrimary
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
                            color = CardeaTextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    is TodayState.RestDay -> {
                        Text(
                            text = "Rest day",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            ),
                            color = CardeaTextPrimary
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
                            color = CardeaTextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
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
            .background(Color(0x0FFFFFFF))
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = CardeaTextSecondary
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
            color = CardeaTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTextSecondary
        )
        if (!evidence.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = evidence,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextTertiary
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Not now", color = CardeaTextSecondary)
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
            color = CardeaTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Recent signals look atypical. If you're getting sick, keep today's effort easy.",
            style = MaterialTheme.typography.bodySmall,
            color = CardeaTextSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Dismiss", color = CardeaTextSecondary)
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
        containerColor = CardeaBgPrimary,
        dragHandle = null,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Reschedule Run",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = CardeaTextPrimary
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            if (autoTargetLabel != null) {
                Text(
                    text = "Recommended for $autoTargetLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CardeaTextSecondary
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
                    color = CardeaTextSecondary
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
                    color = CardeaTextTertiary
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
                                .background(GlassHighlight)
                                .clickable { onConfirm(day) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = availableLabels[index],
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = CardeaTextSecondary
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
                    color = CardeaTextTertiary
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
        containerColor = CardeaBgPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Preferred training days",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap to toggle · Long-press to block a day out",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary
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
                            isBlackout -> Modifier.background(Color(0xFF1C1F26))
                            isSelected -> Modifier.background(CardeaGradient)
                            else       -> Modifier.border(1.dp, GlassBorder, CircleShape)
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
                    color = if (isBlackout) Color(0xFF8B3A3A) else CardeaTextPrimary
                )
                if (isLongRun) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.White,
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
                        tint = Color(0xFF8B3A3A),
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
                            .background(Color(0xFF1C1F26))
                            .border(1.dp, Color(0xFF3D2020), CircleShape)
                        DaySelectionLevel.NONE          -> Modifier.background(GlassHighlight)
                    }
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTextTertiary
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
                color = CardeaTextPrimary
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = GradientPink)
            }
        },
        containerColor = CardeaBgPrimary,
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
        containerColor = CardeaBgPrimary,
        dragHandle = { Box(Modifier.padding(vertical = 12.dp).width(32.dp).height(4.dp).clip(CircleShape).background(GlassBorder)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
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
                color = CardeaTextPrimary
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
                            color = CardeaTextSecondary
                        )
                        Text(
                            text = "$progressPercentage%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = CardeaTextPrimary
                        )
                    }
                    
                    // Progress bar - core gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(GlassBorder)
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
                    color = CardeaTextPrimary,
                    lineHeight = 22.sp
                )
            }

            Text(
                text = "You're training at a tier that balances your historical volume with your current recovery capacity.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextTertiary,
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
                color = CardeaTextPrimary
            )
        },
        text = {
            Text(
                text = "Your schedule and progress will be permanently deleted. Your completed run history stays in the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("End Program", color = GradientRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep it", color = CardeaTextSecondary)
            }
        },
        containerColor = CardeaBgPrimary,
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

private fun buildConfigJson(session: PlannedSession): String {
    val presetId = session.presetId
    return if (presetId != null) {
        org.json.JSONObject().apply {
            put("mode", WorkoutMode.DISTANCE_PROFILE.name)
            put("presetId", presetId)
        }.toString()
    } else {
        org.json.JSONObject().apply {
            put("mode", WorkoutMode.FREE_RUN.name)
        }.toString()
    }
}
