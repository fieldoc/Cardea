package com.hrcoach.ui.bootcamp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.domain.bootcamp.FinishingTimeTierMapper
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaCtaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.ZoneAmber
import com.hrcoach.ui.theme.ZoneGreen

// ─── Main composable ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BootcampSetupFlow(
    state: BootcampUiState,
    onGoalSelected: (BootcampGoal) -> Unit,
    onFinishingTimeChanged: (Int) -> Unit,
    onTierSelected: (Int) -> Unit,
    onRunsPerWeekChanged: (Int) -> Unit,
    onDayToggled: (Int) -> Unit,
    onDayLongPressed: (Int) -> Unit,
    onAvailableMinutesChanged: (Int) -> Unit,
    onHrMaxChanged: (String) -> Unit,
    onEnroll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. Your Goal ────────────────────────────────────────────────
        SectionGoal(
            selectedGoal = state.onboardingGoal,
            onGoalSelected = onGoalSelected
        )

        // ── 2. Current Ability ──────────────────────────────────────────
        if (state.onboardingGoal != null) {
            SectionAbility(
                goal = state.onboardingGoal,
                finishingTimeMinutes = state.onboardingTargetFinishingTime,
                onFinishingTimeChanged = onFinishingTimeChanged,
                onTierSelected = onTierSelected
            )
        }

        // ── 3. How Many Days? ───────────────────────────────────────────
        if (state.onboardingGoal != null) {
            SectionRunsPerWeek(
                runsPerWeek = state.onboardingRunsPerWeek,
                onRunsPerWeekChanged = onRunsPerWeekChanged
            )
        }

        // ── 4. Which Days Work? ─────────────────────────────────────────
        if (state.onboardingGoal != null) {
            SectionDayPicker(
                preferredDays = state.onboardingPreferredDays,
                runsPerWeek = state.onboardingRunsPerWeek,
                onDayToggled = onDayToggled,
                onDayLongPressed = onDayLongPressed
            )
        }

        // ── 5. How Much Time Per Run? ───────────────────────────────────
        if (state.onboardingGoal != null) {
            SectionTimeAvailability(
                goal = state.onboardingGoal,
                availableMinutes = state.onboardingAvailableMinutes,
                timeWarning = state.onboardingTimeWarning,
                longRunMinutes = state.onboardingLongRunMinutes,
                weeklyTotal = state.onboardingWeeklyTotal,
                onAvailableMinutesChanged = onAvailableMinutesChanged
            )
        }

        // ── 6. Max Heart Rate (optional) ────────────────────────────────
        if (state.onboardingGoal != null) {
            SectionMaxHr(
                maxHr = state.maxHr,
                onHrMaxChanged = onHrMaxChanged
            )
        }

        // ── 7. Program Preview ──────────────────────────────────────────
        if (state.onboardingGoal != null && state.onboardingPreviewSessions.isNotEmpty()) {
            SectionPreview(state = state)
        }

        // ── 8. Start CTA ───────────────────────────────────────────────
        if (state.onboardingGoal != null) {
            val selectedCount = state.onboardingPreferredDays.count {
                it.level == DaySelectionLevel.AVAILABLE || it.level == DaySelectionLevel.LONG_RUN_BIAS
            }
            val daysValid = selectedCount == state.onboardingRunsPerWeek
            CardeaButton(
                text = "Start Program",
                onClick = onEnroll,
                enabled = daysValid && state.onboardingTimeCanProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─── Section 1: Goal ────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionGoal(
    selectedGoal: BootcampGoal?,
    onGoalSelected: (BootcampGoal) -> Unit
) {
    SectionHeader("Your Goal")
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        val goals = listOf(
            BootcampGoal.CARDIO_HEALTH to "Cardio Health",
            BootcampGoal.RACE_5K to "5K",
            BootcampGoal.RACE_10K to "10K",
            BootcampGoal.HALF_MARATHON to "Half Marathon",
            BootcampGoal.MARATHON to "Marathon"
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            goals.forEach { (goal, label) ->
                val isSelected = goal == selectedGoal
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (isSelected) Modifier.background(CardeaCtaGradient)
                            else Modifier.border(
                                1.dp,
                                CardeaTheme.colors.glassBorder,
                                RoundedCornerShape(20.dp)
                            )
                        )
                        .clickable { onGoalSelected(goal) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) CardeaTheme.colors.onGradient
                        else CardeaTheme.colors.textPrimary
                    )
                }
            }
        }
    }
}

// ─── Section 2: Current Ability ─────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionAbility(
    goal: BootcampGoal,
    finishingTimeMinutes: Int?,
    onFinishingTimeChanged: (Int) -> Unit,
    onTierSelected: (Int) -> Unit
) {
    val isRace = FinishingTimeTierMapper.isRaceGoal(goal)
    SectionHeader("Current Ability")

    if (isRace) {
        // Race goals: finishing time slider
        val brackets = FinishingTimeTierMapper.bracketsFor(goal) ?: return
        val currentMinutes = finishingTimeMinutes ?: brackets.defaultMinutes
        val tierIndex = FinishingTimeTierMapper.tierFromFinishingTime(goal, currentMinutes)
        val tierLabel = when (tierIndex) {
            0 -> "Easy"
            1 -> "Moderate"
            2 -> "Hard"
            else -> "Moderate"
        }
        val goalLabel = when (goal) {
            BootcampGoal.RACE_5K -> "5K"
            BootcampGoal.RACE_10K -> "10K"
            BootcampGoal.HALF_MARATHON -> "Half Marathon"
            BootcampGoal.MARATHON -> "Marathon"
            else -> ""
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "What could you run a $goalLabel in today?",
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = FinishingTimeTierMapper.formatTime(currentMinutes),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = CardeaTheme.colors.textPrimary
            )
            Slider(
                value = currentMinutes.toFloat(),
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
            HorizontalDivider(
                color = CardeaTheme.colors.divider,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Intensity: $tierLabel",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
        }
    } else {
        // Cardio Health: 3 tier pills
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            val tiers = listOf(
                Triple(0, "Easy", "Just getting started or returning from a break"),
                Triple(1, "Moderate", "Can jog 20-30 minutes comfortably"),
                Triple(2, "Hard", "Running regularly, looking to improve")
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tiers.forEach { (tier, label, description) ->
                    // Derive current tier from finishing time if available
                    val currentTier = finishingTimeMinutes?.let {
                        FinishingTimeTierMapper.tierFromFinishingTime(goal, it)
                    }
                    val isSelected = currentTier == tier
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (isSelected) Modifier.background(CardeaCtaGradient)
                                else Modifier.border(
                                    1.dp,
                                    CardeaTheme.colors.glassBorder,
                                    RoundedCornerShape(14.dp)
                                )
                            )
                            .clickable { onTierSelected(tier) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSelected) CardeaTheme.colors.onGradient
                            else CardeaTheme.colors.textPrimary
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) CardeaTheme.colors.onGradient.copy(alpha = 0.8f)
                            else CardeaTheme.colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}

// ─── Section 3: How Many Days ───────────────────────────────────────────────

@Composable
private fun SectionRunsPerWeek(
    runsPerWeek: Int,
    onRunsPerWeekChanged: (Int) -> Unit
) {
    SectionHeader("How Many Days?")
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (2..6).forEach { count ->
                val isSelected = count == runsPerWeek
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .then(
                            if (isSelected) Modifier.background(CardeaCtaGradient)
                            else Modifier.border(
                                1.dp,
                                CardeaTheme.colors.glassBorder,
                                RoundedCornerShape(14.dp)
                            )
                        )
                        .clickable { onRunsPerWeekChanged(count) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) CardeaTheme.colors.onGradient
                        else CardeaTheme.colors.textPrimary
                    )
                }
            }
        }
    }
}

// ─── Section 4: Which Days Work ─────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionDayPicker(
    preferredDays: List<DayPreference>,
    runsPerWeek: Int,
    onDayToggled: (Int) -> Unit,
    onDayLongPressed: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val selectedCount = preferredDays.count {
        it.level == DaySelectionLevel.AVAILABLE || it.level == DaySelectionLevel.LONG_RUN_BIAS
    }
    val isValid = selectedCount == runsPerWeek

    SectionHeader("Which Days Work?")
    Text(
        text = "Choose which days work for running, and pick a day for your long run.",
        style = MaterialTheme.typography.bodySmall,
        color = CardeaTheme.colors.textSecondary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$selectedCount selected of $runsPerWeek needed",
            style = MaterialTheme.typography.bodySmall,
            color = if (isValid) ZoneGreen else ZoneAmber
        )

        // Legend — 2x2 grid
        DayPickerLegendGrid()

        // Day chips
        val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            (1..7).forEach { day ->
                val level = preferredDays.firstOrNull { it.day == day }?.level
                    ?: DaySelectionLevel.NONE
                val isSelected = level == DaySelectionLevel.AVAILABLE ||
                        level == DaySelectionLevel.LONG_RUN_BIAS
                val isBlackout = level == DaySelectionLevel.BLACKOUT
                val isLongRun = level == DaySelectionLevel.LONG_RUN_BIAS

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .then(
                            when {
                                isBlackout -> Modifier.background(CardeaTheme.colors.blackoutBg)
                                isSelected -> Modifier.background(CardeaCtaGradient)
                                else -> Modifier.border(
                                    1.dp,
                                    CardeaTheme.colors.glassBorder,
                                    CircleShape
                                )
                            }
                        )
                        .combinedClickable(
                            onClick = { onDayToggled(day) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDayLongPressed(day)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayLetters[day - 1],
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isBlackout) CardeaTheme.colors.blackoutText
                        else CardeaTheme.colors.textPrimary
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

        // Interaction hints card
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CardeaTheme.colors.glassHighlight.copy(alpha = 0.5f))
                .border(1.dp, CardeaTheme.colors.glassBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hint 1: Tap
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardeaTheme.colors.glassHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(CardeaTheme.colors.textSecondary)
                    )
                }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = CardeaTheme.colors.textPrimary)) { append("Tap") }
                        append(" a day to cycle: available \u2192 run \u2192 long run \u2192 available")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = CardeaTheme.colors.textSecondary,
                    lineHeight = 16.sp
                )
            }
            // Hint 2: Hold
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardeaTheme.colors.glassHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(CardeaTheme.colors.textSecondary)
                    )
                }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = CardeaTheme.colors.textPrimary)) { append("Hold") }
                        append(" a day to block it \u2014 Cardea will never schedule a run on a blocked day (e.g. school pickup)")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = CardeaTheme.colors.textSecondary,
                    lineHeight = 16.sp
                )
            }
            // Hint 3: Star
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardeaTheme.colors.glassHighlight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = CardeaTheme.colors.textSecondary, modifier = Modifier.size(12.dp))
                }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = CardeaTheme.colors.textPrimary)) { append("Long run day") }
                        append(" is when you have the most time. Tap a run day again to promote it")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = CardeaTheme.colors.textSecondary,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun DayPickerLegendGrid() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: Available + Run day
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DayPickerLegendItem(
                circleContent = { /* empty circle — just the border */ },
                circleBg = null,
                label = "Available",
                description = "No run scheduled",
                modifier = Modifier.weight(1f)
            )
            DayPickerLegendItem(
                circleContent = {},
                circleBg = CardeaCtaGradient,
                label = "Run day",
                description = "Regular session",
                modifier = Modifier.weight(1f)
            )
        }
        // Row 2: Long run + Blocked
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DayPickerLegendItem(
                circleContent = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = CardeaTheme.colors.onGradient,
                        modifier = Modifier.size(10.dp)
                    )
                },
                circleBg = CardeaCtaGradient,
                label = "Long run",
                description = "Your biggest effort of the week",
                modifier = Modifier.weight(1f)
            )
            DayPickerLegendItem(
                circleContent = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = CardeaTheme.colors.blackoutText,
                        modifier = Modifier.size(10.dp)
                    )
                },
                circleBg = null,
                isBlackout = true,
                label = "Blocked",
                description = "Never schedule here",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DayPickerLegendItem(
    circleContent: @Composable () -> Unit,
    circleBg: Brush?,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    isBlackout: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardeaTheme.colors.glassHighlight.copy(alpha = 0.5f))
            .padding(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 24dp circle matching real day circle styling
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .then(
                        when {
                            isBlackout -> Modifier.background(CardeaTheme.colors.blackoutBg)
                            circleBg != null -> Modifier.background(circleBg)
                            else -> Modifier.border(1.dp, CardeaTheme.colors.glassBorder, CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                circleContent()
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = CardeaTheme.colors.textPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = CardeaTheme.colors.textSecondary
                )
            }
        }
    }
}

// ─── Section 5: Time Availability ───────────────────────────────────────────

@Composable
private fun SectionTimeAvailability(
    goal: BootcampGoal,
    availableMinutes: Int,
    timeWarning: String?,
    longRunMinutes: Int,
    weeklyTotal: Int,
    onAvailableMinutesChanged: (Int) -> Unit
) {
    SectionHeader("How Much Time Per Run?")
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "How much time can you usually set aside?",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$availableMinutes min",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = CardeaTheme.colors.textPrimary
        )
        Slider(
            value = availableMinutes.toFloat(),
            onValueChange = { onAvailableMinutesChanged(it.toInt()) },
            valueRange = 10f..90f,
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
                "10 min",
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textTertiary
            )
            Text(
                "90 min",
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textTertiary
            )
        }

        // Contextual guidance
        val recommendedMin = goal.suggestedMinMinutes
        if (timeWarning != null) {
            // Below prescribed: amber warning
            HorizontalDivider(
                color = CardeaTheme.colors.divider,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = timeWarning,
                style = MaterialTheme.typography.bodySmall,
                color = ZoneAmber
            )
        } else if (availableMinutes > recommendedMin) {
            // Above prescribed: green headroom note
            HorizontalDivider(
                color = CardeaTheme.colors.divider,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Your sessions will be ${recommendedMin}-${longRunMinutes} min based on your level. " +
                        "Extra time is headroom for progression.",
                style = MaterialTheme.typography.bodySmall,
                color = ZoneGreen
            )
        }

        if (weeklyTotal > 0) {
            Text(
                text = "Weekly total: ~$weeklyTotal min",
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ─── Section 6: Max Heart Rate ──────────────────────────────────────────────

@Composable
private fun SectionMaxHr(
    maxHr: Int?,
    onHrMaxChanged: (String) -> Unit
) {
    SectionHeader("Max Heart Rate (optional)")
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = maxHr?.toString() ?: "",
            onValueChange = onHrMaxChanged,
            label = { Text("Max HR (bpm)") },
            placeholder = { Text("e.g. 185") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GradientPink,
                unfocusedBorderColor = CardeaTheme.colors.glassBorder,
                focusedLabelColor = GradientPink,
                unfocusedLabelColor = CardeaTheme.colors.textTertiary,
                cursorColor = GradientPink,
                focusedTextColor = CardeaTheme.colors.textPrimary,
                unfocusedTextColor = CardeaTheme.colors.textPrimary
            )
        )
        Text(
            text = "We'll auto-detect this during runs if you skip it",
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTheme.colors.textTertiary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ─── Section 7: Program Preview ─────────────────────────────────────────────

@Composable
private fun SectionPreview(state: BootcampUiState) {
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    SectionHeader("Program Preview - Week 1")
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        if (state.onboardingPreviewSessions.isEmpty()) {
            Text(
                text = "Complete the selections above to see your plan.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textTertiary
            )
        } else {
            // Map sessions to selected days
            val selectedDays = state.onboardingPreferredDays
                .filter {
                    it.level == DaySelectionLevel.AVAILABLE ||
                            it.level == DaySelectionLevel.LONG_RUN_BIAS
                }
                .sortedBy { it.day }

            state.onboardingPreviewSessions.forEachIndexed { index, session ->
                if (index > 0) {
                    HorizontalDivider(
                        color = CardeaTheme.colors.divider.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                val dayLabel = selectedDays.getOrNull(index)?.let { pref ->
                    dayNames.getOrElse(pref.day - 1) { "Day ${index + 1}" }
                } ?: "Day ${index + 1}"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = CardeaTheme.colors.textPrimary
                        )
                        Text(
                            text = session.type.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = CardeaTheme.colors.textSecondary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${session.minutes} min",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = CardeaTheme.colors.textPrimary
                        )
                        val zoneHint = when (session.type) {
                            com.hrcoach.domain.bootcamp.SessionType.EASY,
                            com.hrcoach.domain.bootcamp.SessionType.LONG -> "Zone 2"
                            com.hrcoach.domain.bootcamp.SessionType.TEMPO -> "Zone 3-4"
                            com.hrcoach.domain.bootcamp.SessionType.INTERVAL -> "Zone 4-5"
                            com.hrcoach.domain.bootcamp.SessionType.STRIDES -> "Zone 2 + bursts"
                            com.hrcoach.domain.bootcamp.SessionType.RACE_SIM -> "Race pace"
                            com.hrcoach.domain.bootcamp.SessionType.DISCOVERY,
                            com.hrcoach.domain.bootcamp.SessionType.CHECK_IN -> "Any"
                        }
                        Text(
                            text = zoneHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = CardeaTheme.colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}

// ─── Section 8: CTA is inlined in the main column ──────────────────────────

// ─── Shared helpers ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = CardeaTheme.colors.textTertiary,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}
