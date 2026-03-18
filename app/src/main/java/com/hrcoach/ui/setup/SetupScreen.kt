package com.hrcoach.ui.setup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrcoach.R
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.preset.PresetCategory
import com.hrcoach.domain.preset.PresetLibrary
import com.hrcoach.domain.preset.WorkoutPreset
import com.hrcoach.ui.components.CardeaSlider
import com.hrcoach.ui.components.CardeaSwitch
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.components.cardeaSegmentedButtonColors
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.ZoneRed
import com.hrcoach.service.simulation.SimulationController
import kotlin.math.roundToInt

// ── WorkoutMode display helpers ───────────────────────────────────────────────

private fun WorkoutMode.displayLabel() = when (this) {
    WorkoutMode.STEADY_STATE      -> "Steady State"
    WorkoutMode.DISTANCE_PROFILE  -> "Paced Segments"
    WorkoutMode.FREE_RUN          -> "Free Run"
}

private fun WorkoutMode.displayDescription() = when (this) {
    WorkoutMode.STEADY_STATE      -> "Hold one heart rate zone for the full run."
    WorkoutMode.DISTANCE_PROFILE  -> "Different zone targets across distance intervals."
    WorkoutMode.FREE_RUN          -> "No targets. Heart rate recorded for analysis."
}

private fun buildAlertsSummary(state: SetupUiState): String {
    val parts = mutableListOf<String>()
    if (state.earconVolume > 0) parts.add("Audio")
    if (state.enableVibration) parts.add("Vibration")
    when (state.voiceVerbosity) {
        VoiceVerbosity.MINIMAL -> parts.add("Voice minimal")
        VoiceVerbosity.FULL    -> parts.add("Voice full")
        else                   -> {}
    }
    val infoCues = mutableListOf<String>()
    if (state.enableHalfwayReminder) infoCues.add("Halfway")
    if (state.enableKmSplits) infoCues.add("Splits")
    if (state.enableWorkoutComplete) infoCues.add("Complete")
    if (state.enableInZoneConfirm) infoCues.add("Zone confirm")
    if (infoCues.isNotEmpty()) parts.add("Info: ${infoCues.joinToString(", ")}")
    return if (parts.isEmpty()) "All alerts off" else parts.joinToString(" · ")
}

// ── SetupScreen ───────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    isWideLayout: Boolean,
    onStartWorkout: (configJson: String, deviceAddress: String?) -> Unit,
    onGoToBootcamp: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pulseOn by remember { mutableStateOf(false) }
    var expandedSection by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.liveHr, state.isHrConnected) {
        if (state.liveHr > 0 && state.isHrConnected) {
            pulseOn = true
            kotlinx.coroutines.delay(140L)
            pulseOn = false
        }
    }
    val hrPulseScale by animateFloatAsState(
        targetValue = if (pulseOn) 1.04f else 1f,
        label = "setup-hr-pulse"
    )
    val canStart = state.validation.canStartWorkout

    // Reusable start action — return@run is the Kotlin idiom for early return in a lambda
    val doStartWorkout: () -> Unit = {
        run {
            val configJson = viewModel.buildConfigJsonOrNull() ?: return@run
            viewModel.saveAudioSettings()
            val deviceAddress = viewModel.handoffConnectedDeviceAddress()
            onStartWorkout(configJson, deviceAddress)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaTheme.colors.bgPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (isWideLayout) 24.dp else 16.dp,
                    vertical = 20.dp
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Title ─────────────────────────────────────────────────────────
            Text(
                text = "Training",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = CardeaTheme.colors.textPrimary,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            // ── Simulation badge ─────────────────────────────────────────────
            if (SimulationController.isActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33FF5A5F))
                        .border(1.dp, Color(0xFFFF5A5F), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFFF5A5F), CircleShape)
                    )
                    Text(
                        text = "SIM MODE",
                        color = Color(0xFFFF5A5F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = SimulationController.state.value.scenario?.name ?: "",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            // ── Bootcamp hero ──────────────────────────────────────────────────
            BootcampEntryCard(onClick = onGoToBootcamp)

            // ── Manual Run section ─────────────────────────────────────────────
            // Section label — Manual Run is a full peer section, not an afterthought
            Text(
                text = "Manual run",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.sp,
                    fontSize = 10.sp
                ),
                color = CardeaTheme.colors.textTertiary,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
            )

            // Quick-launch — last settings + one-tap start
            QuickLaunchCard(
                state = state,
                canStart = canStart,
                onStart = doStartWorkout
            )

            // ── Change setup accordion ─────────────────────────────────────────
            Text(
                text = "Change setup",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.sp,
                    fontSize = 10.sp
                ),
                color = CardeaTheme.colors.textTertiary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            // All config rows in a single grouped card
            val hrSummary = if (state.isHrConnected)
                "${state.connectedDeviceName.ifBlank { "HR Monitor" }} · " +
                    if (state.liveHr > 0) "${state.liveHr} bpm" else "connected"
            else "Not connected"
            val hrSummaryColor = if (state.isHrConnected) CardeaTheme.colors.zoneGreen else CardeaTheme.colors.textSecondary

            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                ConfigSectionHeader(
                    title = "Mode",
                    summary = state.mode.displayLabel(),
                    isExpanded = expandedSection == "mode",
                    onToggle = { expandedSection = if (expandedSection == "mode") null else "mode" }
                )
                HorizontalDivider(color = CardeaTheme.colors.glassBorder)
                ConfigSectionHeader(
                    title = "HR Monitor",
                    summary = hrSummary,
                    summaryColor = hrSummaryColor,
                    isExpanded = expandedSection == "hr",
                    onToggle = { expandedSection = if (expandedSection == "hr") null else "hr" }
                )
                if (state.mode != WorkoutMode.FREE_RUN) {
                    val targetSummary = when {
                        state.selectedPresetId != null && state.selectedPresetId != "custom" ->
                            state.selectedPresetId ?: "Zone target"
                        state.steadyStateHr.isNotBlank() -> "${state.steadyStateHr} bpm target"
                        else -> "Not set"
                    }
                    HorizontalDivider(color = CardeaTheme.colors.glassBorder)
                    ConfigSectionHeader(
                        title = "HR Target",
                        summary = targetSummary,
                        isExpanded = expandedSection == "target",
                        onToggle = { expandedSection = if (expandedSection == "target") null else "target" }
                    )
                }
                HorizontalDivider(color = CardeaTheme.colors.glassBorder)
                ConfigSectionHeader(
                    title = "Alerts",
                    summary = buildAlertsSummary(state),
                    isExpanded = expandedSection == "alerts",
                    onToggle = { expandedSection = if (expandedSection == "alerts") null else "alerts" }
                )
            }

            // Expanded content — appears below the accordion group
            AnimatedVisibility(visible = expandedSection == "mode") {
                ModeOptionsCard(
                    selectedMode = state.mode,
                    onModeSelected = { mode ->
                        viewModel.setMode(mode)
                        expandedSection = null
                    }
                )
            }
            AnimatedVisibility(visible = expandedSection == "hr") {
                HrMonitorCard(
                    state = state,
                    hrPulseScale = hrPulseScale,
                    onStartScan = viewModel::startScan,
                    onConnectDevice = viewModel::connectToDevice,
                    onDisconnect = viewModel::disconnectDevice
                )
            }
            AnimatedVisibility(visible = expandedSection == "target") {
                TargetCard(
                    state = state,
                    onSteadyStateHrChange = viewModel::setSteadyStateHr,
                    onSelectPreset = { viewModel.selectPreset(it.id) },
                    onSelectCustom = { viewModel.selectPreset("custom") },
                    onAddSegment = viewModel::addSegment,
                    onUpdateSegmentDistance = viewModel::updateSegmentDistance,
                    onUpdateSegmentTarget = viewModel::updateSegmentTarget,
                    onRemoveSegment = viewModel::removeSegment
                )
            }
            AnimatedVisibility(visible = expandedSection == "alerts") {
                AlertBehaviorCard(
                    state = state,
                    onToggle = viewModel::toggleAdvancedSettings,
                    onBufferChange = viewModel::setBufferBpm,
                    onAlertDelayChange = viewModel::setAlertDelaySec,
                    onCooldownChange = viewModel::setAlertCooldownSec,
                    onVolumeChange = { viewModel.setEarconVolume((it / 5f).roundToInt() * 5) },
                    onVoiceVerbosityChange = viewModel::setVoiceVerbosity,
                    onVibrationChange = viewModel::setEnableVibration,
                    onPreview = viewModel::previewEarcon,
                    onHalfwayChange = viewModel::setEnableHalfwayReminder,
                    onKmSplitsChange = viewModel::setEnableKmSplits,
                    onWorkoutCompleteChange = viewModel::setEnableWorkoutComplete,
                    onInZoneConfirmChange = viewModel::setEnableInZoneConfirm
                )
            }

            state.validation.startBlockedReason?.takeIf { !canStart }?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // HRmax onboarding dialog
            if (state.showHrMaxDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissHrMaxDialog() },
                    title = { Text("Max Heart Rate") },
                    text = {
                        Column {
                            Text("Required to personalise preset heart rate targets.")
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.maxHrInput,
                                onValueChange = { viewModel.setMaxHrInput(it) },
                                label = { Text("Max HR (bpm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                placeholder = { Text("185") }
                            )
                            Text(
                                text = "220 − age is a good estimate. A field test is more accurate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTheme.colors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardeaGradient)
                                .clickable { viewModel.confirmMaxHr() }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) { Text("Confirm", color = CardeaTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissHrMaxDialog() }) { Text("Cancel") }
                    }
                )
            }
    }
}

// ── BootcampEntryCard ─────────────────────────────────────────────────────────
// Visually distinct from the manual run section — pink-tinted gradient
// background + gradient border signals a different category of action.

@Composable
private fun BootcampEntryCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0x1AFF4D6D),
                        1f to Color(0x144D9FFF)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Color(0x33FF85A1),
                        1f to Color(0x224D9FFF)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {
        // Subtle ambient glow in top-left corner
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x26FF4D6D), Color.Transparent)
                    )
                )
                .align(Alignment.TopStart)
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x26FF85A1))
                        .border(1.dp, Color(0x33FF85A1), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                        contentDescription = null,
                        tint = CardeaTheme.colors.accentPink,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "STRUCTURED TRAINING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = CardeaTheme.colors.accentPink.copy(alpha = 0.67f)
                    )
                    Text(
                        text = "Bootcamp",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTheme.colors.textPrimary
                    )
                }
            }

            Text(
                text = "Adaptive weekly plan. Cardea schedules, paces, and adjusts your sessions automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary.copy(alpha = 0.75f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(CardeaGradient)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Open Bootcamp →",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = CardeaTheme.colors.onGradient
                )
            }
        }
    }
}

// ── QuickLaunchCard ───────────────────────────────────────────────────────────

@Composable
private fun QuickLaunchCard(
    state: SetupUiState,
    canStart: Boolean,
    onStart: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Last setup",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = CardeaTheme.colors.textTertiary
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SetupChip(label = state.mode.displayLabel(), color = Color(0xFF4D9FFF))
            if (state.isHrConnected) {
                SetupChip(
                    label = "● ${if (state.liveHr > 0) "${state.liveHr} bpm" else "Connected"}",
                    color = CardeaTheme.colors.zoneGreen
                )
            } else {
                SetupChip(label = "No Monitor", color = CardeaTheme.colors.textSecondary)
            }
            SetupChip(label = "Audio On", color = CardeaTheme.colors.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    if (canStart) CardeaGradient
                    else Brush.linearGradient(listOf(CardeaTheme.colors.textTertiary, CardeaTheme.colors.textTertiary))
                )
                .clickable(enabled = canStart, onClick = onStart),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Start Run",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = CardeaTheme.colors.onGradient
            )
        }
    }
}

@Composable
private fun SetupChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}

// ── ConfigSectionHeader ───────────────────────────────────────────────────────

@Composable
private fun ConfigSectionHeader(
    title: String,
    summary: String,
    summaryColor: Color = CardeaTheme.colors.textSecondary,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTheme.colors.textPrimary
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = summaryColor
            )
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = CardeaTheme.colors.textTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ── ModeOptionsCard ───────────────────────────────────────────────────────────

@Composable
private fun ModeOptionsCard(
    selectedMode: WorkoutMode,
    onModeSelected: (WorkoutMode) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WorkoutMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) Color(0xFF4D9FFF).copy(alpha = 0.10f)
                            else CardeaTheme.colors.glassHighlight
                        )
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFF4D9FFF).copy(alpha = 0.30f)
                            else CardeaTheme.colors.glassBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onModeSelected(mode) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (isSelected) Color(0xFF4D9FFF)
                                else CardeaTheme.colors.glassSurface,
                                CircleShape
                            )
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = mode.displayLabel(),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                            ),
                            color = if (isSelected) CardeaTheme.colors.textPrimary else CardeaTheme.colors.textSecondary
                        )
                        Text(
                            text = mode.displayDescription(),
                            style = MaterialTheme.typography.bodySmall,
                            color = CardeaTheme.colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}

// ── AlertBehaviorCard ─────────────────────────────────────────────────────────

@Composable
private fun AlertBehaviorCard(
    state: SetupUiState,
    onToggle: () -> Unit,
    onBufferChange: (String) -> Unit,
    onAlertDelayChange: (String) -> Unit,
    onCooldownChange: (String) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVoiceVerbosityChange: (VoiceVerbosity) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onPreview: (CoachingEvent) -> Unit,
    onHalfwayChange: (Boolean) -> Unit,
    onKmSplitsChange: (Boolean) -> Unit,
    onWorkoutCompleteChange: (Boolean) -> Unit,
    onInZoneConfirmChange: (Boolean) -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Alert Behavior",
                style = MaterialTheme.typography.titleLarge,
                color = CardeaTheme.colors.textPrimary
            )
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (state.showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
        }

        if (state.showAdvancedSettings) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.bufferBpm,
                onValueChange = onBufferChange,
                singleLine = true,
                label = { Text("HR Buffer (bpm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.validation.bufferBpm != null,
                supportingText = { state.validation.bufferBpm?.let { Text(it) } }
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.alertDelaySec,
                onValueChange = onAlertDelayChange,
                singleLine = true,
                label = { Text("Onset Delay (s)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.validation.alertDelaySec != null,
                supportingText = { state.validation.alertDelaySec?.let { Text(it) } }
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.alertCooldownSec,
                onValueChange = onCooldownChange,
                singleLine = true,
                label = { Text("Alert Interval (s)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.validation.alertCooldownSec != null,
                supportingText = { state.validation.alertCooldownSec?.let { Text(it) } }
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Alert Volume", style = MaterialTheme.typography.bodyLarge, color = CardeaTheme.colors.textPrimary)
                Text("${state.earconVolume}%", style = MaterialTheme.typography.bodyMedium, color = CardeaTheme.colors.textSecondary)
            }
            CardeaSlider(
                value = state.earconVolume.toFloat(),
                onValueChange = onVolumeChange,
                valueRange = 0f..100f,
                steps = 19
            )

            Text("Voice Coaching", style = MaterialTheme.typography.bodyLarge, color = CardeaTheme.colors.textPrimary)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    VoiceVerbosity.OFF     to "Off",
                    VoiceVerbosity.MINIMAL to "Minimal",
                    VoiceVerbosity.FULL    to "Full"
                ).forEachIndexed { index, (verbosity, label) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        selected = state.voiceVerbosity == verbosity,
                        onClick = { onVoiceVerbosityChange(verbosity) },
                        colors = cardeaSegmentedButtonColors()
                    ) { Text(label) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vibration Alerts", style = MaterialTheme.typography.bodyLarge, color = CardeaTheme.colors.textPrimary)
                CardeaSwitch(checked = state.enableVibration, onCheckedChange = onVibrationChange)
            }

            Text("Preview Sounds", style = MaterialTheme.typography.labelSmall, color = CardeaTheme.colors.textSecondary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                PreviewSoundButton(icon = Icons.Default.ArrowUpward, label = "Speed Up",  onClick = { onPreview(CoachingEvent.SPEED_UP) })
                PreviewSoundButton(icon = Icons.Default.ArrowDownward, label = "Slow Down", onClick = { onPreview(CoachingEvent.SLOW_DOWN) })
                PreviewSoundButton(icon = Icons.Default.Check,         label = "In Zone",   onClick = { onPreview(CoachingEvent.RETURN_TO_ZONE) })
            }

            HorizontalDivider()

            Text("Informational Cues", style = MaterialTheme.typography.bodyLarge, color = CardeaTheme.colors.textPrimary)

            val cuesEnabled = state.voiceVerbosity != VoiceVerbosity.OFF
            val cueAlpha = if (cuesEnabled) 1f else 0.4f

            Row(
                modifier = Modifier.fillMaxWidth().alpha(cueAlpha),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Halfway reminder", style = MaterialTheme.typography.bodyMedium, color = CardeaTheme.colors.textSecondary)
                CardeaSwitch(checked = state.enableHalfwayReminder && cuesEnabled, onCheckedChange = { if (cuesEnabled) onHalfwayChange(it) })
            }
            Row(
                modifier = Modifier.fillMaxWidth().alpha(cueAlpha),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Kilometer splits", style = MaterialTheme.typography.bodyMedium, color = CardeaTheme.colors.textSecondary)
                CardeaSwitch(checked = state.enableKmSplits && cuesEnabled, onCheckedChange = { if (cuesEnabled) onKmSplitsChange(it) })
            }
            Row(
                modifier = Modifier.fillMaxWidth().alpha(cueAlpha),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Workout complete", style = MaterialTheme.typography.bodyMedium, color = CardeaTheme.colors.textSecondary)
                CardeaSwitch(checked = state.enableWorkoutComplete && cuesEnabled, onCheckedChange = { if (cuesEnabled) onWorkoutCompleteChange(it) })
            }
            Row(
                modifier = Modifier.fillMaxWidth().alpha(cueAlpha),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("In-zone confirmation", style = MaterialTheme.typography.bodyMedium, color = CardeaTheme.colors.textSecondary)
                CardeaSwitch(checked = state.enableInZoneConfirm && cuesEnabled, onCheckedChange = { if (cuesEnabled) onInZoneConfirmChange(it) })
            }
        } else {
            Text(
                text = "Audio alerts and timing",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
        }
    }
}

@Composable
private fun PreviewSoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .background(CardeaTheme.colors.surfaceVariant, CircleShape)
                .border(1.dp, CardeaTheme.colors.glassBorder, CircleShape)
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = CardeaTheme.colors.textSecondary)
    }
}

// ── TargetCard ────────────────────────────────────────────────────────────────

@Composable
private fun TargetCard(
    state: SetupUiState,
    onSteadyStateHrChange: (String) -> Unit,
    onSelectPreset: (WorkoutPreset) -> Unit,
    onSelectCustom: () -> Unit,
    onAddSegment: () -> Unit,
    onUpdateSegmentDistance: (Int, String) -> Unit,
    onUpdateSegmentTarget: (Int, String) -> Unit,
    onRemoveSegment: (Int) -> Unit
) {
    GlassCard {
        Text(
            text = "Target Zone Plan",
            style = MaterialTheme.typography.titleLarge,
            color = CardeaTheme.colors.textPrimary
        )
        when (state.mode) {
            WorkoutMode.STEADY_STATE -> {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.steadyStateHr,
                    onValueChange = onSteadyStateHrChange,
                    singleLine = true,
                    label = { Text("Target HR (bpm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = state.validation.steadyStateHr != null,
                    supportingText = { state.validation.steadyStateHr?.let { Text(it) } }
                )
            }
            WorkoutMode.DISTANCE_PROFILE -> {
                PresetGrid(
                    presets = PresetLibrary.ALL,
                    selectedPresetId = state.selectedPresetId,
                    onSelectPreset = onSelectPreset
                )
                if (state.selectedPresetId == "custom") {
                    SegmentEditor(
                        state = state,
                        onAddSegment = onAddSegment,
                        onUpdateSegmentDistance = onUpdateSegmentDistance,
                        onUpdateSegmentTarget = onUpdateSegmentTarget,
                        onRemoveSegment = onRemoveSegment
                    )
                }
                TextButton(onClick = onSelectCustom, modifier = Modifier.fillMaxWidth()) { Text("Custom") }
            }
            WorkoutMode.FREE_RUN -> Unit
        }
    }
}

@Composable
private fun SegmentEditor(
    state: SetupUiState,
    onAddSegment: () -> Unit,
    onUpdateSegmentDistance: (Int, String) -> Unit,
    onUpdateSegmentTarget: (Int, String) -> Unit,
    onRemoveSegment: (Int) -> Unit
) {
    val segmentColors = listOf(
        GradientBlue,
        GradientCyan,
        GradientPink
    )
    state.segments.forEachIndexed { index, segment ->
        val segmentError = state.validation.segments.getOrNull(index) ?: SegmentInputError()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(88.dp)
                    .background(segmentColors[index % segmentColors.size], RoundedCornerShape(999.dp))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Segment ${index + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textSecondary
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = segment.distanceKm,
                    onValueChange = { onUpdateSegmentDistance(index, it) },
                    singleLine = true,
                    label = { Text("Distance (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = segmentError.distanceKm != null,
                    supportingText = { segmentError.distanceKm?.let { Text(it) } }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = segment.targetHr,
                    onValueChange = { onUpdateSegmentTarget(index, it) },
                    singleLine = true,
                    label = { Text("HR (bpm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = segmentError.targetHr != null,
                    supportingText = { segmentError.targetHr?.let { Text(it) } }
                )
            }
            if (state.segments.size > 1) {
                IconButton(onClick = { onRemoveSegment(index) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove segment", tint = ZoneRed)
                }
            }
        }
    }
    TextButton(onClick = onAddSegment) {
        Icon(imageVector = Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add Segment")
    }
}

@Composable
private fun PresetGrid(
    presets: List<WorkoutPreset>,
    selectedPresetId: String?,
    onSelectPreset: (WorkoutPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PresetCategory.entries.forEach { category ->
            val catPresets = presets.filter { it.category == category }
            if (catPresets.isEmpty()) return@forEach
            Text(
                text = category.displayName(),
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
            catPresets.forEach { preset ->
                PresetCard(
                    preset = preset,
                    isSelected = preset.id == selectedPresetId,
                    onClick = { onSelectPreset(preset) }
                )
            }
        }
    }
}

private fun PresetCategory.displayName() = when (this) {
    PresetCategory.BASE_AEROBIC -> "BASE AEROBIC"
    PresetCategory.THRESHOLD    -> "THRESHOLD"
    PresetCategory.INTERVAL     -> "INTERVALS"
    PresetCategory.RACE_PREP    -> "RACE PREP"
}

@Composable
private fun PresetCard(
    preset: WorkoutPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderModifier = if (isSelected) {
        Modifier.border(width = 2.dp, brush = CardeaGradient, shape = RoundedCornerShape(18.dp))
    } else Modifier

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clickable(onClick = onClick)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(preset.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = CardeaTheme.colors.textPrimary)
                    Text(preset.subtitle, style = MaterialTheme.typography.bodySmall, color = CardeaTheme.colors.textSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(preset.durationLabel, style = MaterialTheme.typography.labelSmall, color = CardeaTheme.colors.textSecondary)
                    Text(preset.intensityLabel, style = MaterialTheme.typography.labelSmall, color = CardeaTheme.colors.textPrimary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SegmentTimelineStrip(preset = preset)
        }
    }
}

@Composable
private fun SegmentTimelineStrip(preset: WorkoutPreset) {
    val config = remember(preset.id) { preset.buildConfig(180) }
    val segmentColors = remember(preset.id) { buildSegmentColors(config) }
    val totalDuration = remember(preset.id) {
        when {
            config.isTimeBased() -> config.segments.sumOf { it.durationSeconds?.toLong() ?: 0L }.toFloat()
            config.segments.isNotEmpty() -> config.segments.last().distanceMeters ?: 1f
            else -> 1f
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
    ) {
        if (totalDuration <= 0f) return@Canvas
        val gap = 2.dp.toPx()

        when {
            config.mode == WorkoutMode.STEADY_STATE -> {
                val color = segmentColors.firstOrNull() ?: Color(0xFF2B8C6E)
                drawRoundRect(color = color, cornerRadius = CornerRadius(3.dp.toPx()))
            }
            config.isTimeBased() -> {
                var x = 0f
                config.segments.forEachIndexed { i, seg ->
                    val dur = seg.durationSeconds?.toLong() ?: return@forEachIndexed
                    val w = (dur / totalDuration) * size.width - if (i < config.segments.size - 1) gap else 0f
                    val color = segmentColors.getOrElse(i) { Color(0xFF2B8C6E) }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, 0f),
                        size = Size(w.coerceAtLeast(2f), size.height),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                    x += w + gap
                }
            }
            else -> {
                var prevDist = 0f
                config.segments.forEachIndexed { i, seg ->
                    val endDist = seg.distanceMeters ?: return@forEachIndexed
                    val span = endDist - prevDist
                    val w = (span / totalDuration) * size.width - if (i < config.segments.size - 1) gap else 0f
                    val x = (prevDist / totalDuration) * size.width
                    val color = segmentColors.getOrElse(i) { Color(0xFF2B8C6E) }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, 0f),
                        size = Size(w.coerceAtLeast(2f), size.height),
                        cornerRadius = CornerRadius(3.dp.toPx())
                    )
                    prevDist = endDist
                }
            }
        }
    }
}

private fun buildSegmentColors(config: WorkoutConfig): List<Color> {
    val canonicalMaxHr = 180f
    return when {
        config.mode == WorkoutMode.STEADY_STATE ->
            listOf(hrPercentColor((config.steadyStateTargetHr ?: 120) / canonicalMaxHr))
        else -> config.segments.map { seg -> hrPercentColor(seg.targetHr / canonicalMaxHr) }
    }
}

private fun hrPercentColor(pct: Float) = when {
    pct >= 0.85f -> Color(0xFFFF5A5F)
    pct >= 0.75f -> Color(0xFFE8A838)
    else         -> Color(0xFF2B8C6E)
}

// ── HrMonitorCard ─────────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@Composable
private fun HrMonitorCard(
    state: SetupUiState,
    hrPulseScale: Float,
    onStartScan: () -> Unit,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit
) {
    GlassCard {
        Text(text = "HR Monitor", style = MaterialTheme.typography.titleLarge, color = CardeaTheme.colors.textPrimary)

        if (state.isHrConnected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = CardeaTheme.colors.zoneGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Connected: ${state.connectedDeviceName.ifBlank { "HR Monitor" }}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = CardeaTheme.colors.zoneGreen
                    )
                    Text(
                        text = if (state.liveHr > 0) state.liveHr.toString() else "--",
                        style = MaterialTheme.typography.headlineMedium,
                        color = CardeaTheme.colors.textPrimary,
                        modifier = Modifier.scale(hrPulseScale)
                    )
                    Text(
                        text = if (state.liveHr > 0) "Live" else "Waiting for signal…",
                        style = MaterialTheme.typography.labelSmall,
                        color = CardeaTheme.colors.textSecondary
                    )
                }
                TextButton(onClick = onDisconnect) { Text("Disconnect") }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (!state.isScanning) Modifier.background(CardeaGradient)
                        else Modifier.background(CardeaTheme.colors.textTertiary)
                    )
                    .clickable(enabled = !state.isScanning, onClick = onStartScan),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CardeaTheme.colors.textPrimary)
                    Text(
                        text = if (state.isScanning) "Scanning…" else "Scan for Monitors",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTheme.colors.textPrimary
                    )
                }
            }
            Text(
                text = "A monitor is optional — scanning continues during your run.",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )

            state.connectionError?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = ZoneRed)
            }

            if (state.discoveredDevices.isNotEmpty()) {
                HorizontalDivider()
                state.discoveredDevices.forEach { device ->
                    DeviceRow(device = device, onClick = { onConnectDevice(device) })
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceRow(device: BluetoothDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = device.name ?: "Unknown Device", style = MaterialTheme.typography.bodyLarge)
            Text(text = device.address, style = MaterialTheme.typography.bodySmall, color = CardeaTheme.colors.textSecondary)
        }
        Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null, tint = CardeaTheme.colors.textSecondary)
    }
}
