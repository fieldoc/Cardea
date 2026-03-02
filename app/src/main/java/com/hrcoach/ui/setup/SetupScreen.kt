package com.hrcoach.ui.setup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.R
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.model.WorkoutMode
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_setup_title)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isWideLayout) 24.dp else 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    selected = state.mode == WorkoutMode.STEADY_STATE,
                    onClick = { viewModel.setMode(WorkoutMode.STEADY_STATE) }
                ) {
                    Text("Steady State")
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    selected = state.mode == WorkoutMode.DISTANCE_PROFILE,
                    onClick = { viewModel.setMode(WorkoutMode.DISTANCE_PROFILE) }
                ) {
                    Text("Distance Profile")
                }
            }

            HrMonitorCard(
                state = state,
                onStartScan = viewModel::startScan,
                onConnectDevice = viewModel::connectToDevice,
                onDisconnect = viewModel::disconnectDevice
            )

            TargetCard(
                state = state,
                onSteadyStateHrChange = viewModel::setSteadyStateHr,
                onAddSegment = viewModel::addSegment,
                onUpdateSegmentDistance = viewModel::updateSegmentDistance,
                onUpdateSegmentTarget = viewModel::updateSegmentTarget,
                onRemoveSegment = viewModel::removeSegment
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Advanced Settings",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = viewModel::toggleAdvancedSettings) {
                            Icon(
                                imageVector = if (state.showAdvancedSettings) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                contentDescription = if (state.showAdvancedSettings) {
                                    "Collapse advanced settings"
                                } else {
                                    "Expand advanced settings"
                                }
                            )
                        }
                    }

                    if (state.showAdvancedSettings) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = state.bufferBpm,
                            onValueChange = viewModel::setBufferBpm,
                            singleLine = true,
                            label = { Text("Buffer (+/- bpm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = state.validation.bufferBpm != null,
                            supportingText = {
                                state.validation.bufferBpm?.let { Text(it) }
                            }
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = state.alertDelaySec,
                            onValueChange = viewModel::setAlertDelaySec,
                            singleLine = true,
                            label = { Text("Grace Period (sec)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = state.validation.alertDelaySec != null,
                            supportingText = {
                                state.validation.alertDelaySec?.let { Text(it) }
                            }
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = state.alertCooldownSec,
                            onValueChange = viewModel::setAlertCooldownSec,
                            singleLine = true,
                            label = { Text("Repeat Interval (sec)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = state.validation.alertCooldownSec != null,
                            supportingText = {
                                state.validation.alertCooldownSec?.let { Text(it) }
                            }
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
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Slider(
                            value = state.earconVolume.toFloat(),
                            onValueChange = { viewModel.setEarconVolume((it / 5f).roundToInt() * 5) },
                            valueRange = 0f..100f,
                            steps = 19
                        )

                        Text(
                            text = "Voice Coaching",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                selected = state.voiceVerbosity == VoiceVerbosity.OFF,
                                onClick = { viewModel.setVoiceVerbosity(VoiceVerbosity.OFF) }
                            ) {
                                Text("Off")
                            }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                selected = state.voiceVerbosity == VoiceVerbosity.MINIMAL,
                                onClick = { viewModel.setVoiceVerbosity(VoiceVerbosity.MINIMAL) }
                            ) {
                                Text("Minimal")
                            }
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                selected = state.voiceVerbosity == VoiceVerbosity.FULL,
                                onClick = { viewModel.setVoiceVerbosity(VoiceVerbosity.FULL) }
                            ) {
                                Text("Full")
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
                                onCheckedChange = viewModel::setEnableVibration
                            )
                        }

                        Text(
                            text = "Preview Sounds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.previewEarcon(CoachingEvent.SPEED_UP) }
                            ) {
                                Text("Speed Up")
                            }
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.previewEarcon(CoachingEvent.SLOW_DOWN) }
                            ) {
                                Text("Slow Down")
                            }
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.previewEarcon(CoachingEvent.RETURN_TO_ZONE) }
                            ) {
                                Text("In Zone")
                            }
                        }
                    } else {
                        Text(
                            text = "Buffer, timing, and audio options are hidden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "App Settings",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = viewModel::toggleAppSettings) {
                            Icon(
                                imageVector = if (state.showAppSettings) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                contentDescription = if (state.showAppSettings) {
                                    "Collapse app settings"
                                } else {
                                    "Expand app settings"
                                }
                            )
                        }
                    }

                    if (state.showAppSettings) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = state.mapsApiKey,
                            onValueChange = viewModel::setMapsApiKey,
                            singleLine = true,
                            label = { Text("Google Maps API key") }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (state.mapsApiKeySaved) {
                                    "Saved. Restart app if the map is still blank."
                                } else {
                                    "Used only for route map rendering in history."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            FilledTonalButton(onClick = viewModel::saveMapsApiKey) {
                                Text("Save Key")
                            }
                        }
                    } else {
                        Text(
                            text = "Maps key is optional and configured once.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            state.validation.startBlockedReason?.let { reason ->
                if (!state.validation.canStartWorkout) {
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Button(
                onClick = {
                    val configJson = viewModel.buildConfigJsonOrNull() ?: return@Button
                    viewModel.saveAudioSettings()
                    val deviceAddress = viewModel.handoffConnectedDeviceAddress()
                    onStartWorkout(configJson, deviceAddress)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = state.validation.canStartWorkout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    text = stringResource(R.string.button_start_workout).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TargetCard(
    state: SetupUiState,
    onSteadyStateHrChange: (String) -> Unit,
    onAddSegment: () -> Unit,
    onUpdateSegmentDistance: (Int, String) -> Unit,
    onUpdateSegmentTarget: (Int, String) -> Unit,
    onRemoveSegment: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                        supportingText = {
                            val error = state.validation.steadyStateHr
                            if (error != null) {
                                Text(error)
                            }
                        }
                    )
                }

                WorkoutMode.DISTANCE_PROFILE -> {
                    state.segments.forEachIndexed { index, segment ->
                        val segmentError = state.validation.segments.getOrNull(index) ?: SegmentInputError()
                        Text(
                            text = "Segment ${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = segment.distanceKm,
                                onValueChange = { onUpdateSegmentDistance(index, it) },
                                singleLine = true,
                                label = { Text("Distance (km)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = segmentError.distanceKm != null,
                                supportingText = {
                                    segmentError.distanceKm?.let { Text(it) }
                                }
                            )
                            OutlinedTextField(
                                modifier = Modifier.weight(1f),
                                value = segment.targetHr,
                                onValueChange = { onUpdateSegmentTarget(index, it) },
                                singleLine = true,
                                label = { Text("HR (bpm)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = segmentError.targetHr != null,
                                supportingText = {
                                    segmentError.targetHr?.let { Text(it) }
                                }
                            )
                            if (state.segments.size > 1) {
                                IconButton(
                                    onClick = { onRemoveSegment(index) },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove segment",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        if (index < state.segments.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                    TextButton(
                        onClick = onAddSegment,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Segment")
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun HrMonitorCard(
    state: SetupUiState,
    onStartScan: () -> Unit,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "HR Monitor",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (state.isHrConnected) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            text = if (state.liveHr > 0) {
                                "Live HR: ${state.liveHr} bpm"
                            } else {
                                "Waiting for HR signal..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = onStartScan,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isScanning
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (state.isScanning) "Scanning..." else "Scan for Devices")
                }
                Text(
                    text = "COOSPO H808S usually appears as \"H808...\" or \"COOSPO...\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "You can still start without a live connection; scanning continues during the run.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                state.connectionError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (state.isScanning && state.discoveredDevices.isEmpty()) {
                    Text(
                        text = "Looking for nearby HR devices...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (state.discoveredDevices.isNotEmpty()) {
                    HorizontalDivider()
                    state.discoveredDevices.forEach { device ->
                        DeviceRow(
                            device = device,
                            onClick = { onConnectDevice(device) }
                        )
                    }
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
                color = MaterialTheme.colorScheme.outline
            )
        }
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
