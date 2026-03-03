package com.hrcoach.ui.setup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBarDefaults
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextTertiary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.R
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.preset.PresetCategory
import com.hrcoach.domain.preset.PresetLibrary
import com.hrcoach.domain.preset.WorkoutPreset
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.HrCoachThemeTokens
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    isWideLayout: Boolean,
    onStartWorkout: (configJson: String, deviceAddress: String?) -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var pulseOn by remember { mutableStateOf(false) }

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

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_setup_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(CardeaBgSecondary, CardeaBgPrimary),
                        center = androidx.compose.ui.geometry.Offset.Zero,
                        radius = 1800f
                    )
                )
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isWideLayout) 24.dp else 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ModeSelector(
                selectedMode = state.mode,
                onModeSelected = viewModel::setMode
            )

            HrMonitorCard(
                state = state,
                hrPulseScale = hrPulseScale,
                onStartScan = viewModel::startScan,
                onConnectDevice = viewModel::connectToDevice,
                onDisconnect = viewModel::disconnectDevice
            )

            if (state.mode != WorkoutMode.FREE_RUN) {
                TargetCard(
                    state = state,
                    viewModel = viewModel,
                    onSteadyStateHrChange = viewModel::setSteadyStateHr,
                    onAddSegment = viewModel::addSegment,
                    onUpdateSegmentDistance = viewModel::updateSegmentDistance,
                    onUpdateSegmentTarget = viewModel::updateSegmentTarget,
                    onRemoveSegment = viewModel::removeSegment
                )
            }

            AlertBehaviorCard(
                state = state,
                onToggle = viewModel::toggleAdvancedSettings,
                onBufferChange = viewModel::setBufferBpm,
                onAlertDelayChange = viewModel::setAlertDelaySec,
                onCooldownChange = viewModel::setAlertCooldownSec,
                onVolumeChange = { viewModel.setEarconVolume((it / 5f).roundToInt() * 5) },
                onVoiceVerbosityChange = viewModel::setVoiceVerbosity,
                onVibrationChange = viewModel::setEnableVibration,
                onPreview = viewModel::previewEarcon
            )

            state.validation.startBlockedReason?.takeIf { !state.validation.canStartWorkout }?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = HrCoachThemeTokens.subtleText
                )
            }

            val canStart = state.validation.canStartWorkout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (canStart) CardeaGradient
                        else androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(CardeaTextTertiary, CardeaTextTertiary)
                        )
                    )
                    .clickable(enabled = canStart) {
                        val configJson = viewModel.buildConfigJsonOrNull() ?: return@clickable
                        viewModel.saveAudioSettings()
                        val deviceAddress = viewModel.handoffConnectedDeviceAddress()
                        onStartWorkout(configJson, deviceAddress)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.button_start_workout),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // HRmax onboarding dialog
        if (state.showHrMaxDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissHrMaxDialog() },
                title = { Text("Your Max Heart Rate") },
                text = {
                    Column {
                        Text("Presets use % of your max HR to personalise targets.")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.maxHrInput,
                            onValueChange = { viewModel.setMaxHrInput(it) },
                            label = { Text("Max HR (bpm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            placeholder = { Text("e.g. 185") }
                        )
                        Text(
                            text = "Tip: 220 - age is a rough guide. A field test gives better results.",
                            style = MaterialTheme.typography.bodySmall,
                            color = HrCoachThemeTokens.subtleText,
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
                    ) { Text("Confirm", color = CardeaTextPrimary, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissHrMaxDialog() }) { Text("Cancel") }
                }
            )
        }
        } // close Box
    }

}

@Composable
private fun ModeSelector(
    selectedMode: WorkoutMode,
    onModeSelected: (WorkoutMode) -> Unit
) {
    val items = listOf(
        Triple(WorkoutMode.STEADY_STATE, Icons.Default.FavoriteBorder, "Steady"),
        Triple(WorkoutMode.DISTANCE_PROFILE, Icons.Default.Route, "Guided"),
        Triple(WorkoutMode.FREE_RUN, Icons.AutoMirrored.Filled.DirectionsRun, "Free Run")
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { (mode, icon, label) ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                label = { Text(label) },
                leadingIcon = { Icon(icon, contentDescription = null) }
            )
        }
    }
}

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
    onPreview: (CoachingEvent) -> Unit
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
                color = MaterialTheme.colorScheme.onSurface
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
                label = { Text("Buffer (+/- bpm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.validation.bufferBpm != null,
                supportingText = { state.validation.bufferBpm?.let { Text(it) } }
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.alertDelaySec,
                onValueChange = onAlertDelayChange,
                singleLine = true,
                label = { Text("Grace Period (sec)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.validation.alertDelaySec != null,
                supportingText = { state.validation.alertDelaySec?.let { Text(it) } }
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.alertCooldownSec,
                onValueChange = onCooldownChange,
                singleLine = true,
                label = { Text("Repeat Interval (sec)") },
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
                Text(
                    text = "Alert Sound Volume",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${state.earconVolume}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HrCoachThemeTokens.subtleText
                )
            }
            Slider(
                value = state.earconVolume.toFloat(),
                onValueChange = onVolumeChange,
                valueRange = 0f..100f,
                steps = 19
            )

            Text(
                text = "Voice Coaching",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    VoiceVerbosity.OFF to "Off",
                    VoiceVerbosity.MINIMAL to "Minimal",
                    VoiceVerbosity.FULL to "Full"
                ).forEachIndexed { index, (verbosity, label) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        selected = state.voiceVerbosity == verbosity,
                        onClick = { onVoiceVerbosityChange(verbosity) }
                    ) {
                        Text(label)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vibration Alerts",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = state.enableVibration,
                    onCheckedChange = onVibrationChange
                )
            }

            Text(
                text = "Preview Sounds",
                style = MaterialTheme.typography.labelSmall,
                color = HrCoachThemeTokens.subtleText
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PreviewSoundButton(
                    icon = Icons.Default.ArrowUpward,
                    label = "Speed Up",
                    onClick = { onPreview(CoachingEvent.SPEED_UP) }
                )
                PreviewSoundButton(
                    icon = Icons.Default.ArrowDownward,
                    label = "Slow Down",
                    onClick = { onPreview(CoachingEvent.SLOW_DOWN) }
                )
                PreviewSoundButton(
                    icon = Icons.Default.Check,
                    label = "In Zone",
                    onClick = { onPreview(CoachingEvent.RETURN_TO_ZONE) }
                )
            }
        } else {
            Text(
                text = "Audio alerts & timing options",
                style = MaterialTheme.typography.bodySmall,
                color = HrCoachThemeTokens.subtleText
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
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .border(1.dp, HrCoachThemeTokens.glassBorder, CircleShape)
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = HrCoachThemeTokens.subtleText
        )
    }
}

@Composable
private fun TargetCard(
    state: SetupUiState,
    viewModel: SetupViewModel,
    onSteadyStateHrChange: (String) -> Unit,
    onAddSegment: () -> Unit,
    onUpdateSegmentDistance: (Int, String) -> Unit,
    onUpdateSegmentTarget: (Int, String) -> Unit,
    onRemoveSegment: (Int) -> Unit
) {
    GlassCard {
        Text(
            text = "Target Zone Plan",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
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
                    onSelectPreset = { viewModel.selectPreset(it.id) }
                )
                if (state.selectedPresetId == "custom") {
                    SegmentEditor(state = state, viewModel = viewModel)
                }
                TextButton(
                    onClick = { viewModel.selectPreset("custom") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Custom segment editor...") }
            }

            WorkoutMode.FREE_RUN -> Unit
        }
    }
}

@Composable
private fun SegmentEditor(state: SetupUiState, viewModel: SetupViewModel) {
    val segmentColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary
    )
    state.segments.forEachIndexed { index, segment ->
        val segmentError = state.validation.segments.getOrNull(index) ?: SegmentInputError()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = HrCoachThemeTokens.glassBorder,
                    shape = RoundedCornerShape(16.dp)
                )
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
                    color = HrCoachThemeTokens.subtleText
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = segment.distanceKm,
                    onValueChange = { viewModel.updateSegmentDistance(index, it) },
                    singleLine = true,
                    label = { Text("Distance (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = segmentError.distanceKm != null,
                    supportingText = { segmentError.distanceKm?.let { Text(it) } }
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = segment.targetHr,
                    onValueChange = { viewModel.updateSegmentTarget(index, it) },
                    singleLine = true,
                    label = { Text("HR (bpm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = segmentError.targetHr != null,
                    supportingText = { segmentError.targetHr?.let { Text(it) } }
                )
            }
            if (state.segments.size > 1) {
                IconButton(onClick = { viewModel.removeSegment(index) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove segment",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    TextButton(onClick = { viewModel.addSegment() }) {
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
                color = HrCoachThemeTokens.subtleText,
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
        Modifier.border(
            width = 2.dp,
            brush = CardeaGradient,
            shape = RoundedCornerShape(18.dp)
        )
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
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTextPrimary
                    )
                    Text(
                        preset.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = HrCoachThemeTokens.subtleText
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(preset.durationLabel, style = MaterialTheme.typography.labelSmall,
                        color = HrCoachThemeTokens.subtleText)
                    Text(preset.intensityLabel, style = MaterialTheme.typography.labelSmall,
                        color = CardeaTextPrimary)
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
        Text(
            text = "HR Monitor",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (state.isHrConnected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Connected: ${state.connectedDeviceName.ifBlank { "HR Monitor" }}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = if (state.liveHr > 0) state.liveHr.toString() else "--",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.scale(hrPulseScale)
                    )
                    Text(
                        text = if (state.liveHr > 0) "Live HR bpm" else "Waiting for HR signal...",
                        style = MaterialTheme.typography.labelSmall,
                        color = HrCoachThemeTokens.subtleText
                    )
                }
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect")
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (!state.isScanning)
                            Modifier.background(CardeaGradient)
                        else
                            Modifier.background(CardeaTextTertiary)
                    )
                    .clickable(enabled = !state.isScanning, onClick = onStartScan),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = CardeaTextPrimary
                    )
                    Text(
                        text = if (state.isScanning) "Scanning..." else "Scan for Devices",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTextPrimary
                    )
                }
            }
            Text(
                text = "No signal? You can still start - scanning continues during the run.",
                style = MaterialTheme.typography.bodySmall,
                color = HrCoachThemeTokens.subtleText
            )

            state.connectionError?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
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
private fun DeviceRow(
    device: BluetoothDevice,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name ?: "Unknown Device",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = HrCoachThemeTokens.subtleText
            )
        }
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f)
        )
    }
}
