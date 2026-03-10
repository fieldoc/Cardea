package com.hrcoach.ui.bootcamp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.domain.bootcamp.DayPreference
import com.hrcoach.domain.bootcamp.DaySelectionLevel
import com.hrcoach.domain.model.BootcampGoal
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GlassHighlight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions

private val DayLetter = listOf("M", "T", "W", "T", "F", "S", "S")
private val settingsDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BootcampSettingsScreen(
    onBack: () -> Unit,
    viewModel: BootcampSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Program Settings",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
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
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Training Schedule",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Goal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        GoalSelector(
                            selectedGoal = state.editGoal,
                            onGoalSelected = viewModel::setGoal
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Target Minutes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Text(
                            text = "${state.editTargetMinutesPerRun} min",
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = CardeaTextPrimary
                        )
                        Slider(
                            value = state.editTargetMinutesPerRun.toFloat(),
                            onValueChange = { viewModel.setTargetMinutesPerRun(it.toInt()) },
                            valueRange = 15f..90f,
                            steps = 14,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
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

                        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 16.dp))

                        Text(
                            text = "Runs Per Week",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        val options = listOf(2, 3, 4, 5)
                        val labels = listOf("2", "3", "4", "5+")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            options.forEachIndexed { index, runs ->
                                val isSelected = state.editRunsPerWeek == runs
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .then(
                                            if (isSelected) Modifier.background(CardeaGradient)
                                            else Modifier.background(GlassHighlight)
                                        )
                                        .clickable { viewModel.setRunsPerWeek(runs) },
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
                            color = CardeaTextTertiary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                        )

                        Text(
                            text = "Difficulty Tier",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        TierSelector(
                            tierIndex = state.editTierIndex,
                            onTierSelected = viewModel::setTierIndex
                        )

                        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 16.dp))

                        Text(
                            text = "Program Start Date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        StartDateEditor(
                            dateMs = state.editStartDateMs,
                            onPrevDay = { viewModel.shiftStartDate(-1) },
                            onNextDay = { viewModel.shiftStartDate(1) }
                        )
                        Text(
                            text = "If your schedule changed, move the start date so gap logic matches reality.",
                            style = MaterialTheme.typography.labelSmall,
                            color = CardeaTextSecondary,
                            modifier = Modifier.padding(top = 6.dp)
                        )

                        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 16.dp))

                        Text(
                            text = "Max Heart Rate (HRmax)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        OutlinedTextField(
                            value = state.editHrMaxInput,
                            onValueChange = viewModel::setHrMaxInput,
                            singleLine = true,
                            label = { Text("100-220 bpm") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = state.hrMaxValidationError != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val hrError = state.hrMaxValidationError
                        if (hrError != null) {
                            Text(
                                text = hrError,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF5A5F)
                            )
                        } else {
                            Text(
                                text = "Adaptive coaching quality improves when HRmax is set.",
                                style = MaterialTheme.typography.labelSmall,
                                color = CardeaTextSecondary
                            )
                        }

                        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 16.dp))

                        Text(
                            text = "Preferred Days",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        val selectedCount = state.editPreferredDays.size
                        val dayCopy = "Select exactly ${state.editRunsPerWeek} days \u00b7 $selectedCount selected"
                        Text(
                            text = if (selectedCount == state.editRunsPerWeek) dayCopy else "$dayCopy \u2715",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selectedCount == state.editRunsPerWeek) {
                                CardeaTextSecondary
                            } else {
                                Color(0xFFFF5A5F)
                            }
                        )
                        DayChipRow(
                            selectedDays = state.editPreferredDays,
                            onToggle = viewModel::cycleDayPreference,
                            onLongPress = viewModel::toggleBlackoutDay
                        )

                        val visibleError = state.preferredDaysValidationError
                            ?: state.hrMaxValidationError
                            ?: state.saveError
                        visibleError?.let { err ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = err,
                                color = Color(0xFFFF5A5F),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { viewModel.saveSettings(onDone = onBack) },
                                enabled = state.canSave && !state.isSaving
                            ) {
                                Text(
                                    text = if (state.isSaving) "Saving..." else "Save",
                                    color = if (state.canSave && !state.isSaving) Color.White else CardeaTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun GoalSelector(
    selectedGoal: BootcampGoal,
    onGoalSelected: (BootcampGoal) -> Unit
) {
    val goals = listOf(
        BootcampGoal.CARDIO_HEALTH,
        BootcampGoal.RACE_5K_10K,
        BootcampGoal.HALF_MARATHON,
        BootcampGoal.MARATHON
    )
    goals.forEach { goal ->
        val isSelected = selectedGoal == goal
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .then(if (isSelected) Modifier.background(CardeaGradient) else Modifier.background(GlassHighlight))
                .clickable { onGoalSelected(goal) }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = goalLabel(goal),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isSelected) CardeaTextPrimary else CardeaTextSecondary
            )
        }
    }
}

@Composable
private fun TierSelector(
    tierIndex: Int,
    onTierSelected: (Int) -> Unit
) {
    val labels = listOf("Easy", "Moderate", "Hard")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = tierIndex == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (isSelected) Modifier.background(CardeaGradient) else Modifier.background(GlassHighlight))
                    .clickable { onTierSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) CardeaTextPrimary else CardeaTextSecondary
                )
            }
        }
    }
}

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
            Text("Previous", color = CardeaTextSecondary)
        }
        Text(
            text = settingsDateFormatter.format(localDate),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
        )
        TextButton(onClick = onNextDay) {
            Text("Next", color = CardeaTextSecondary)
        }
    }
}

private fun goalLabel(goal: BootcampGoal): String = when (goal) {
    BootcampGoal.CARDIO_HEALTH -> "Cardio Health"
    BootcampGoal.RACE_5K_10K -> "5K / 10K"
    BootcampGoal.HALF_MARATHON -> "Half Marathon"
    BootcampGoal.MARATHON -> "Marathon"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayChipRow(
    selectedDays: List<DayPreference>,
    onToggle: (Int) -> Unit,
    onLongPress: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Legend strip — shows all four states as tiny labeled chips
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
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
                    .background(
                        when {
                            isBlackout -> Brush.linearGradient(listOf(Color(0xFF1C1F26), Color(0xFF1C1F26)))
                            isSelected -> CardeaGradient
                            else       -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            isBlackout -> Color(0xFF3D2020)
                            isSelected -> Color.Transparent
                            else       -> GlassBorder
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
                    color = if (isBlackout) Color(0xFF8B3A3A) else Color.White
                )
                if (isLongRun) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Long run bias",
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
                        contentDescription = "Blocked",
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
    val isBlackout = level == DaySelectionLevel.BLACKOUT
    val isSelected = level == DaySelectionLevel.AVAILABLE || level == DaySelectionLevel.LONG_RUN_BIAS
    val isLongRun  = level == DaySelectionLevel.LONG_RUN_BIAS

    Box(
        modifier = Modifier
            .height(22.dp)
            .clip(CircleShape)
            .background(
                when {
                    isBlackout -> Brush.linearGradient(listOf(Color(0xFF1C1F26), Color(0xFF1C1F26)))
                    isSelected -> CardeaGradient
                    else       -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .border(1.dp, if (isSelected) Color.Transparent else GlassBorder, CircleShape)
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
                color = if (isBlackout) Color(0xFF8B3A3A) else Color.White
            )
            if (isLongRun)  Icon(Icons.Default.Star,  null, tint = Color.White,        modifier = Modifier.size(7.dp))
            if (isBlackout) Icon(Icons.Default.Close, null, tint = Color(0xFF8B3A3A),  modifier = Modifier.size(7.dp))
        }
    }
}
