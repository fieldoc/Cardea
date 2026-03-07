package com.hrcoach.ui.bootcamp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.PlannedSession
import com.hrcoach.domain.bootcamp.SessionType
import com.hrcoach.domain.engine.TierPromptDirection
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GlassHighlight
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.ZoneGreen

@Composable
fun BootcampScreen(
    onStartWorkout: (configJson: String) -> Unit,
    onBack: () -> Unit,
    viewModel: BootcampViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(CardeaBgSecondary, CardeaBgPrimary),
                        center = Offset.Zero,
                        radius = 1800f
                    )
                )
                .padding(padding)
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
                        onGraduateGoal = viewModel::graduateCurrentGoal
                    )
                }
            }

            // Welcome-back dialog shown on top of any state
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
                icon = Icons.Default.TrendingUp,
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
            tint = GradientPink,
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
        BootcampGoal.RACE_5K_10K to "Train for your first race",
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
    onMinutesChanged: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Text(
        text = "Time per session",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = CardeaTextPrimary
    )
    Text(
        text = "How long can you run each session?",
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
    }

    if (warning != null) {
        Text(
            text = warning,
            style = MaterialTheme.typography.bodySmall,
            color = GradientRed
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
    onGraduateGoal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Phase header with overflow menu
        PhaseHeader(
            uiState = uiState,
            onPause = onPause,
            onResume = onResume,
            onEndProgram = onEndProgram
        )

        // Paused state - shows resume button instead of a dead status message
        if (uiState.isPaused) {
            PausedCard(onResume = onResume)
        }
        if (uiState.missedSession) {
            StatusCard(
                title = "Missed session detected",
                detail = "You have at least one earlier session this week that is still incomplete."
            )
        }
        if (uiState.scheduledRestDay) {
            StatusCard(
                title = "Scheduled rest day",
                detail = "No run is scheduled for today. Recovery supports adaptation."
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

        // Week sessions list
        WeekSessionList(sessions = uiState.currentWeekSessions)
        if (uiState.upcomingWeeks.isNotEmpty()) {
            ComingUpCard(weeks = uiState.upcomingWeeks)
        }

        if (uiState.showGraduationCta) {
            GraduationCard(onGraduateGoal = onGraduateGoal)
        }

        // Next session CTA - hidden when paused (no point starting a run)
        val nextSession = uiState.nextSession
        if (nextSession != null && !uiState.isPaused) {
            NextSessionCard(
                session = nextSession,
                dayLabel = uiState.nextSessionDayLabel,
                swapMessage = uiState.swapRestMessage,
                onSwapToRest = onSwapTodayForRest,
                onStartWorkout = { configJson ->
                    onBootcampWorkoutStarting()
                    onStartWorkout(configJson)
                }
            )
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
    Row(
        modifier = modifier,
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
                    color = if (enabled) Color.White else CardeaTextTertiary
                )
            }
        }
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
        Spacer(modifier = Modifier.height(8.dp))
        weeks.forEachIndexed { weekIndex, week ->
            if (weekIndex > 0) {
                HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 6.dp))
            }
            Text(
                text = "Week ${week.weekNumber}${if (week.isRecoveryWeek) " · Recovery" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = if (week.isRecoveryWeek) ZoneGreen else CardeaTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            week.sessions.forEach { session ->
                Text(
                    text = "${session.typeName} · ${session.minutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTextTertiary
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

@Composable
private fun PhaseHeader(
    uiState: BootcampUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndProgram: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val goal = uiState.goal
                if (goal != null) {
                    Text(
                        text = goalDisplayName(goal),
                        style = MaterialTheme.typography.labelMedium,
                        color = GradientPink
                    )
                }
                val phase = uiState.currentPhase
                Text(
                    text = if (phase != null) {
                        "Week ${uiState.absoluteWeek} of ${uiState.totalWeeks} — ${phase.displayName}"
                    } else {
                        "Week ${uiState.absoluteWeek} of ${uiState.totalWeeks}"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = CardeaTextPrimary
                )
                val recoveryLabel = when (uiState.weeksUntilNextRecovery) {
                    0 -> "This is your recovery week."
                    1 -> "Recovery week coming up next week."
                    2 -> "Recovery week in 2 weeks."
                    null -> null
                    else -> "Recovery week in ${uiState.weeksUntilNextRecovery} weeks."
                }
                recoveryLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (uiState.activePreferredDays.isNotEmpty()) {
                    PreferredDaysStrip(
                        preferredDays = uiState.activePreferredDays,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isRecoveryWeek) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlassHighlight)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Recovery",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ZoneGreen
                        )
                    }
                }

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
                        if (uiState.isPaused) {
                            DropdownMenuItem(
                                text = { Text("Resume program") },
                                onClick = {
                                    menuExpanded = false
                                    onResume()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Pause program") },
                                onClick = {
                                    menuExpanded = false
                                    onPause()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "End program...",
                                    color = GradientRed
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onEndProgram()
                            }
                        )
                    }
                }
            }
        }

        // Progress bar
        val progress = if (uiState.totalWeeks > 0) uiState.absoluteWeek.toFloat() / uiState.totalWeeks else 0f
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(GlassHighlight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CardeaGradient)
            )
        }
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

@Composable
private fun WeekSessionList(sessions: List<SessionUiItem>) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "This Week",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CardeaTextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (sessions.isEmpty()) {
            Text(
                text = "No sessions scheduled yet.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary
            )
        } else {
            sessions.forEachIndexed { index, session ->
                if (index > 0) {
                    HorizontalDivider(
                        color = GlassBorder,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                SessionRow(session = session)
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionUiItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Completed check or day label box
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (session.isCompleted) GradientBlue.copy(alpha = 0.2f)
                        else GlassHighlight
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (session.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = ZoneGreen,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = session.dayLabel.take(2),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (session.isToday) GradientPink else CardeaTextSecondary
                    )
                }
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = session.typeName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (session.isToday) CardeaTextPrimary else CardeaTextSecondary
                    )
                    if (session.isToday) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(GradientPink.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.labelSmall,
                                color = GradientPink
                            )
                        }
                    }
                }
                Text(
                    text = "${session.minutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTextTertiary
                )
            }
        }

        if (session.isCompleted) {
            Text(
                text = "Done",
                style = MaterialTheme.typography.labelSmall,
                color = ZoneGreen
            )
        }
    }
}

@Composable
private fun NextSessionCard(
    session: PlannedSession,
    dayLabel: String?,
    swapMessage: String?,
    onSwapToRest: () -> Unit,
    onStartWorkout: (configJson: String) -> Unit
) {
    val sessionLabel = SessionType.displayLabelForPreset(session.presetId)
        ?: session.type.name.lowercase().replaceFirstChar { it.uppercase() }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Next Session${if (dayLabel != null) " — $dayLabel" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = CardeaTextSecondary
        )
        Text(
            text = sessionLabel,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = CardeaTextPrimary
        )
        Text(
            text = "${session.minutes} min",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        CardeaButton(
            text = "Start Run",
            onClick = {
                val configJson = buildConfigJson(session)
                onStartWorkout(configJson)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )
        TextButton(
            onClick = onSwapToRest,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = "Rest today instead",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary
            )
        }
        swapMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextTertiary
            )
        }
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
        containerColor = CardeaBgSecondary,
        shape = RoundedCornerShape(18.dp)
    )
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
        containerColor = CardeaBgSecondary,
        shape = RoundedCornerShape(18.dp)
    )
}

private fun goalDisplayName(goal: BootcampGoal): String = when (goal) {
    BootcampGoal.CARDIO_HEALTH -> "Cardio Health"
    BootcampGoal.RACE_5K_10K -> "5K / 10K"
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
