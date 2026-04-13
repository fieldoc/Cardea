package com.hrcoach.ui.bootcamp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.hrcoach.ui.components.SectionHeader
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.domain.bootcamp.FinishingTimeTierMapper
import com.hrcoach.domain.bootcamp.TierInfo
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaCtaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.ZoneRed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayLetter = listOf("M", "T", "W", "T", "F", "S", "S")
private val settingsDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BootcampSettingsScreen(
    onBack: () -> Unit,
    viewModel: BootcampSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDestructiveConfirm by remember { mutableStateOf(false) }

    // ── Destructive-change confirmation dialog ────────────────────────────────
    if (showDestructiveConfirm) {
        AlertDialog(
            onDismissRequest = { showDestructiveConfirm = false },
            containerColor = CardeaTheme.colors.bgPrimary,
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = "Recalculate Program?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = CardeaTheme.colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "These changes affect your program structure:",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textSecondary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (state.hasGoalChanges) ChangeChip("Goal → ${goalLabel(state.editGoal)}")
                        if (state.hasTierChanges) ChangeChip("Difficulty → ${tierLabel(state.editTierIndex)}")
                        if (state.hasRunsPerWeekChanges) ChangeChip("${state.editRunsPerWeek} runs / week")
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Future sessions will be recalculated to match. Completed sessions are unaffected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textSecondary
                    )
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardeaCtaGradient)
                        .clickable {
                            showDestructiveConfirm = false
                            viewModel.saveSettings(onDone = onBack)
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = "Save anyway",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTheme.colors.onGradient
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDestructiveConfirm = false }) {
                    Text("Cancel", color = CardeaTheme.colors.textSecondary)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaTheme.colors.bgPrimary)
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = CardeaTheme.colors.textPrimary
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── Title row ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CardeaTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = CardeaTheme.colors.textPrimary
                    )
                }

                // ── TRAINING ──────────────────────────────────────────────────
                SectionHeader("Training")
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Goal",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTheme.colors.textSecondary
                    )
                    GoalSelector(
                        selectedGoal = state.editGoal,
                        onGoalSelected = viewModel::setGoal
                    )

                    HorizontalDivider(color = CardeaTheme.colors.glassBorder, modifier = Modifier.padding(vertical = 14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Time per run",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = CardeaTheme.colors.textSecondary
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${state.editTargetMinutesPerRun}",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = CardeaTheme.colors.textPrimary
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "min",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTheme.colors.textSecondary,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }
                    Slider(
                        value = state.editTargetMinutesPerRun.toFloat(),
                        onValueChange = { viewModel.setTargetMinutesPerRun(it.toInt()) },
                        valueRange = 15f..90f,
                        steps = 14,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = CardeaTheme.colors.onGradient,
                            activeTrackColor = Color.White,
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
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Sessions will be prescribed within this time limit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textTertiary
                    )

                    HorizontalDivider(color = CardeaTheme.colors.glassBorder, modifier = Modifier.padding(vertical = 14.dp))

                    Text(
                        text = "Runs / Week",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTheme.colors.textSecondary
                    )
                    val runOptions = listOf(2, 3, 4, 5, 6)
                    val runLabels = listOf("2", "3", "4", "5", "6")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        runOptions.forEachIndexed { index, runs ->
                            val isSelected = state.editRunsPerWeek == runs
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .then(
                                        if (isSelected) Modifier.background(CardeaCtaGradient)
                                        else Modifier.background(CardeaTheme.colors.glassHighlight)
                                    )
                                    .clickable { viewModel.setRunsPerWeek(runs) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = runLabels[index],
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) CardeaTheme.colors.textPrimary else CardeaTheme.colors.textSecondary
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = CardeaTheme.colors.glassBorder, modifier = Modifier.padding(vertical = 14.dp))

                    Text(
                        text = "Difficulty",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTheme.colors.textSecondary
                    )
                    if (FinishingTimeTierMapper.isRaceGoal(state.editGoal)) {
                        FinishingTimeInput(
                            goal = state.editGoal,
                            finishingTimeMinutes = state.editTargetFinishingTimeMinutes
                                ?: FinishingTimeTierMapper.bracketsFor(state.editGoal)?.defaultMinutes ?: 30,
                            derivedTierIndex = state.editTierIndex,
                            warning = state.editTimeWarning,
                            onFinishingTimeChanged = viewModel::setTargetFinishingTime
                        )
                    } else {
                        TierSelector(
                            tierIndex = state.editTierIndex,
                            onTierSelected = viewModel::setTierIndex
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── SCHEDULE ──────────────────────────────────────────────────
                SectionHeader("Schedule")
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Preferred Days",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTheme.colors.textSecondary
                    )
                    val selectedCount = state.editPreferredDays.count {
                        it.level == DaySelectionLevel.AVAILABLE || it.level == DaySelectionLevel.LONG_RUN_BIAS
                    }
                    Text(
                        text = if (selectedCount == state.editRunsPerWeek)
                            "Select exactly ${state.editRunsPerWeek} days · $selectedCount selected"
                        else
                            "Select exactly ${state.editRunsPerWeek} days · $selectedCount selected ✕",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedCount == state.editRunsPerWeek) CardeaTheme.colors.textSecondary else ZoneRed,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    DayChipRow(
                        selectedDays = state.editPreferredDays,
                        onToggle = viewModel::cycleDayPreference,
                        onLongPress = viewModel::toggleBlackoutDay
                    )

                    HorizontalDivider(color = CardeaTheme.colors.glassBorder, modifier = Modifier.padding(vertical = 14.dp))

                    Text(
                        text = "Start Date",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTheme.colors.textSecondary
                    )
                    StartDateEditor(
                        dateMs = state.editStartDateMs,
                        onPrevDay = { viewModel.shiftStartDate(-1) },
                        onNextDay = { viewModel.shiftStartDate(1) }
                    )
                    Text(
                        text = "If your schedule changed, move the start date so gap logic matches reality.",
                        style = MaterialTheme.typography.labelSmall,
                        color = CardeaTheme.colors.textTertiary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ── PROFILE ───────────────────────────────────────────────────
                SectionHeader("Profile")
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Max Heart Rate",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTheme.colors.textSecondary
                    )
                    OutlinedTextField(
                        value = state.editHrMaxInput,
                        onValueChange = viewModel::setHrMaxInput,
                        singleLine = true,
                        label = { Text("100–220 bpm") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.hrMaxValidationError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                    val hrError = state.hrMaxValidationError
                    if (hrError != null) {
                        Text(
                            text = hrError,
                            style = MaterialTheme.typography.bodySmall,
                            color = ZoneRed,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(
                            text = "Adaptive coaching quality improves when HRmax is set.",
                            style = MaterialTheme.typography.labelSmall,
                            color = CardeaTheme.colors.textTertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Errors + Save CTA ─────────────────────────────────────────
                val visibleError = state.preferredDaysValidationError
                    ?: state.hrMaxValidationError
                    ?: state.saveError
                visibleError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZoneRed,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                val hasProgramChanges = state.hasGoalChanges || state.hasTierChanges || state.hasFinishingTimeChanges || state.hasRunsPerWeekChanges
                val saveEnabled = state.canSave && !state.isSaving

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (saveEnabled) CardeaCtaGradient
                            else Brush.linearGradient(listOf(CardeaTheme.colors.glassHighlight, CardeaTheme.colors.glassHighlight))
                        )
                        .clickable(enabled = saveEnabled) {
                            if (hasProgramChanges) showDestructiveConfirm = true
                            else viewModel.saveSettings(onDone = onBack)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = CardeaTheme.colors.onGradient,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Save Changes",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (saveEnabled) CardeaTheme.colors.onGradient else CardeaTheme.colors.textTertiary
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── Shared helpers ─────────────────────────────────────────────────────────────

// SectionLabel replaced by shared SectionHeader from ui/components

@Composable
private fun ChangeChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x15FF4D5A))
            .border(1.dp, Color(0x35FF4D5A), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = GradientRed
        )
    }
}

private fun goalLabel(goal: BootcampGoal): String = when (goal) {
    BootcampGoal.CARDIO_HEALTH -> "Cardio Health"
    BootcampGoal.RACE_5K -> "5K"
    BootcampGoal.RACE_10K -> "10K"
    BootcampGoal.HALF_MARATHON -> "Half Marathon"
    BootcampGoal.MARATHON -> "Marathon"
}

private fun tierLabel(index: Int): String = TierInfo.displayName(index)

// ── GoalSelector ──────────────────────────────────────────────────────────────

@Composable
private fun GoalSelector(
    selectedGoal: BootcampGoal,
    onGoalSelected: (BootcampGoal) -> Unit
) {
    val goals = listOf(
        BootcampGoal.CARDIO_HEALTH,
        BootcampGoal.RACE_5K,
        BootcampGoal.RACE_10K,
        BootcampGoal.HALF_MARATHON,
        BootcampGoal.MARATHON
    )
    Column(
        modifier = Modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        goals.forEach { goal ->
            val isSelected = selectedGoal == goal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (isSelected) Modifier.background(CardeaCtaGradient) else Modifier.background(CardeaTheme.colors.glassHighlight))
                    .clickable { onGoalSelected(goal) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = goalLabel(goal),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) CardeaTheme.colors.textPrimary else CardeaTheme.colors.textSecondary
                )
            }
        }
    }
}

// ── TierSelector ──────────────────────────────────────────────────────────────

@Composable
private fun FinishingTimeInput(
    goal: BootcampGoal,
    finishingTimeMinutes: Int,
    derivedTierIndex: Int,
    warning: String?,
    onFinishingTimeChanged: (Int) -> Unit
) {
    val brackets = FinishingTimeTierMapper.bracketsFor(goal) ?: return
    val tierLabel = TierInfo.displayName(derivedTierIndex)

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Target: ${FinishingTimeTierMapper.formatTime(finishingTimeMinutes)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = CardeaTheme.colors.textPrimary
            )
            Text(
                text = tierLabel,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
        }
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
        if (warning != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = warning,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.zoneAmber
            )
        }
    }
}

@Composable
private fun TierSelector(
    tierIndex: Int,
    onTierSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        (0..2).forEach { index ->
            val isSelected = tierIndex == index
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (isSelected) Modifier.background(CardeaCtaGradient)
                        else Modifier.background(CardeaTheme.colors.glassHighlight)
                    )
                    .clickable { onTierSelected(index) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = TierInfo.displayName(index),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) CardeaTheme.colors.textPrimary else CardeaTheme.colors.textSecondary
                )
                Text(
                    text = TierInfo.tagline(index),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) CardeaTheme.colors.textPrimary.copy(alpha = 0.7f)
                        else CardeaTheme.colors.textTertiary
                )
            }
        }
    }
}

// ── StartDateEditor ───────────────────────────────────────────────────────────

@Composable
private fun StartDateEditor(
    dateMs: Long,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit
) {
    val localDate = Instant.ofEpochMilli(dateMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onPrevDay) {
            Text("← Prev", color = CardeaTheme.colors.textSecondary)
        }
        Text(
            text = settingsDateFormatter.format(localDate),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CardeaTheme.colors.textPrimary
        )
        TextButton(onClick = onNextDay) {
            Text("Next →", color = CardeaTheme.colors.textSecondary)
        }
    }
}

// ── DayChipRow ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayChipRow(
    selectedDays: List<DayPreference>,
    onToggle: (Int) -> Unit,
    onLongPress: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Legend strip
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayLegendChip("open",    DaySelectionLevel.NONE)
        DayLegendChip("run",     DaySelectionLevel.AVAILABLE)
        DayLegendChip("long",    DaySelectionLevel.LONG_RUN_BIAS)
        DayLegendChip("blocked", DaySelectionLevel.BLACKOUT)
    }

    // Day buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (1..7).forEach { day ->
            val preference = selectedDays.find { it.day == day }
            val level      = preference?.level ?: DaySelectionLevel.NONE
            val isBlackout = level == DaySelectionLevel.BLACKOUT
            val isSelected = level == DaySelectionLevel.AVAILABLE || level == DaySelectionLevel.LONG_RUN_BIAS
            val isLongRun  = level == DaySelectionLevel.LONG_RUN_BIAS

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        when {
                            isBlackout -> Modifier.background(CardeaTheme.colors.blackoutBg)
                            isSelected -> Modifier.background(CardeaCtaGradient)
                            else       -> Modifier
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            isBlackout -> CardeaTheme.colors.blackoutBorder
                            isSelected -> Color.Transparent
                            else       -> CardeaTheme.colors.glassBorder
                        },
                        shape = CircleShape
                    )
                    .combinedClickable(
                        onClick = { onToggle(day) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress(day)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = DayLetter[day - 1],
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isBlackout) CardeaTheme.colors.blackoutText else CardeaTheme.colors.textPrimary
                )
                if (isLongRun) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Long run bias",
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
                        contentDescription = "Blocked",
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
    val isBlackout = level == DaySelectionLevel.BLACKOUT
    val isSelected = level == DaySelectionLevel.AVAILABLE || level == DaySelectionLevel.LONG_RUN_BIAS
    val isLongRun  = level == DaySelectionLevel.LONG_RUN_BIAS

    Box(
        modifier = Modifier
            .height(22.dp)
            .clip(CircleShape)
            .then(
                when {
                    isBlackout -> Modifier.background(CardeaTheme.colors.blackoutBg)
                    isSelected -> Modifier.background(CardeaCtaGradient)
                    else       -> Modifier
                }
            )
            .border(1.dp, if (isSelected) Color.Transparent else CardeaTheme.colors.glassBorder, CircleShape)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = if (isBlackout) CardeaTheme.colors.blackoutText else CardeaTheme.colors.textPrimary
            )
            if (isLongRun)  Icon(Icons.Default.Star,  null, tint = CardeaTheme.colors.onGradient, modifier = Modifier.size(7.dp))
            if (isBlackout) Icon(Icons.Default.Close, null, tint = CardeaTheme.colors.blackoutText, modifier = Modifier.size(7.dp))
        }
    }
}
