package com.hrcoach.ui.bootcamp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.hrcoach.domain.bootcamp.PlannedSession
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.domain.model.TrainingPhase
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
import com.hrcoach.ui.theme.GradientCyan
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
    ) {
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GradientPink)
                }
            }

            uiState.showOnboarding -> {
                OnboardingWizard(
                    uiState = uiState,
                    onGoalSelected = { viewModel.setOnboardingGoal(it) },
                    onMinutesChanged = { viewModel.setOnboardingMinutes(it) },
                    onRunsPerWeekChanged = { viewModel.setOnboardingRunsPerWeek(it) },
                    onComplete = { viewModel.completeOnboarding(listOf(1, 3, 6)) },
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
                    onPause = { viewModel.pauseBootcamp() }
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
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Bootcamp",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = CardeaTextPrimary
        )
        Text(
            text = "A structured training program that adapts to your fitness level and goals.",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTextSecondary
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Adaptive periodization — Base, Build, Peak, Taper phases",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary
            )
            Text(
                text = "Heart-rate guided sessions with real-time coaching",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary
            )
            Text(
                text = "Gap detection and smart re-entry when life happens",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        CardeaButton(
            text = "Start Bootcamp",
            onClick = onStartBootcamp,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        )
    }
}

// ─── Onboarding Wizard ─────────────────────────────────────────────────────

@Composable
private fun OnboardingWizard(
    uiState: BootcampUiState,
    onGoalSelected: (BootcampGoal) -> Unit,
    onMinutesChanged: (Int) -> Unit,
    onRunsPerWeekChanged: (Int) -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }

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
                        .background(
                            if (index <= step) GradientPink else GlassBorder
                        )
                )
            }
        }

        when (step) {
            0 -> OnboardingStep1Goal(
                selectedGoal = uiState.onboardingGoal,
                onGoalSelected = {
                    onGoalSelected(it)
                },
                onNext = { if (uiState.onboardingGoal != null) step = 1 },
                onBack = onBack
            )
            1 -> OnboardingStep2Time(
                minutes = uiState.onboardingMinutes,
                warning = uiState.onboardingTimeWarning,
                onMinutesChanged = onMinutesChanged,
                onNext = { step = 2 },
                onBack = { step = 0 }
            )
            2 -> OnboardingStep3Frequency(
                runsPerWeek = uiState.onboardingRunsPerWeek,
                onRunsPerWeekChanged = onRunsPerWeekChanged,
                onComplete = onComplete,
                onBack = { step = 1 }
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
        val borderBrush = if (isSelected) CardeaGradient else Brush.linearGradient(listOf(GlassBorder, GlassBorder))
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
    onPause: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Phase header
        PhaseHeader(uiState = uiState)

        // Week sessions list
        WeekSessionList(sessions = uiState.currentWeekSessions)

        // Next session CTA
        val nextSession = uiState.nextSession
        if (nextSession != null) {
            NextSessionCard(
                session = nextSession,
                dayLabel = uiState.nextSessionDayLabel,
                onStartWorkout = onStartWorkout
            )
        }

        // Pause option
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(
            onClick = onPause,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Pause Bootcamp", color = CardeaTextTertiary)
        }
    }
}

@Composable
private fun PhaseHeader(uiState: BootcampUiState) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
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
                        "Week ${uiState.absoluteWeek} of ${uiState.totalWeeks} — ${phaseDisplayName(phase)}"
                    } else {
                        "Week ${uiState.absoluteWeek} of ${uiState.totalWeeks}"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = CardeaTextPrimary
                )
            }
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
    onStartWorkout: (configJson: String) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Next Session${if (dayLabel != null) " — $dayLabel" else ""}",
            style = MaterialTheme.typography.labelMedium,
            color = CardeaTextSecondary
        )
        Text(
            text = session.type.name.lowercase().replaceFirstChar { it.uppercase() },
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

private fun goalDisplayName(goal: BootcampGoal): String = when (goal) {
    BootcampGoal.CARDIO_HEALTH -> "Cardio Health"
    BootcampGoal.RACE_5K_10K -> "5K / 10K"
    BootcampGoal.HALF_MARATHON -> "Half Marathon"
    BootcampGoal.MARATHON -> "Marathon"
}

private fun phaseDisplayName(phase: TrainingPhase): String = when (phase) {
    TrainingPhase.BASE -> "Base"
    TrainingPhase.BUILD -> "Build"
    TrainingPhase.PEAK -> "Peak"
    TrainingPhase.TAPER -> "Taper"
}

private fun buildConfigJson(session: PlannedSession): String {
    val presetId = session.presetId
    return if (presetId != null) {
        """{"mode":"DISTANCE_PROFILE","presetId":"$presetId"}"""
    } else {
        """{"mode":"FREE_RUN"}"""
    }
}
