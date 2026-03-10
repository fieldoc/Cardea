package com.hrcoach.ui.account

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.hrcoach.ui.components.CardeaSlider
import com.hrcoach.ui.components.CardeaSwitch
import com.hrcoach.ui.components.cardeaSegmentedButtonColors
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.ui.components.CardeaLogo
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.GlassBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account", color = Color.White, fontWeight = FontWeight.SemiBold) },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile card with gradient accent
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(CardeaGradient)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CardeaGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            CardeaLogo(size = 36.dp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Runner",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                            Text(
                                text = "${state.totalWorkouts} runs recorded",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTextSecondary
                            )
                        }
                    }
                }

                // Maps API Key
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Maps", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.mapsApiKey,
                        onValueChange = viewModel::setMapsApiKey,
                        singleLine = true,
                        label = { Text("Google Maps API key") }
                    )
                    Text(
                        text = if (state.mapsApiKeySaved) "Saved. Restart if map still appears blank."
                        else "Used only for route rendering in History.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTextSecondary
                    )
                    TextButton(onClick = viewModel::saveMapsApiKey) { Text("Save") }
                }

                // Profile — Max HR
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Profile", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Max Heart Rate", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = state.maxHrInput,
                            onValueChange = viewModel::setMaxHrInput,
                            singleLine = true,
                            label = { Text("Max HR (bpm)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("e.g. 185") }
                        )
                        TextButton(onClick = viewModel::saveMaxHr) { Text("Save") }
                    }
                    Text(
                        text = if (state.maxHrSaved) "Saved." else "Used to personalise all preset HR targets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTextSecondary
                    )
                }

                // Audio & Alerts
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Audio & Alerts", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Alert Sound Volume", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        Text("${state.earconVolume}%", color = CardeaTextSecondary)
                    }
                    CardeaSlider(
                        value = state.earconVolume.toFloat(),
                        onValueChange = viewModel::setVolume,
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChangeFinished = viewModel::saveAudioSettings
                    )

                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(4.dp))

                    Text("Voice Coaching", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            VoiceVerbosity.OFF to "Off",
                            VoiceVerbosity.MINIMAL to "Minimal",
                            VoiceVerbosity.FULL to "Full"
                        ).forEachIndexed { i, (v, label) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                                selected = state.voiceVerbosity == v,
                                onClick = { viewModel.setVerbosity(v); viewModel.saveAudioSettings() },
                                colors = cardeaSegmentedButtonColors()
                            ) { Text(label) }
                        }
                    }

                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Vibration Alerts", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        CardeaSwitch(
                            checked = state.enableVibration,
                            onCheckedChange = { viewModel.setVibration(it); viewModel.saveAudioSettings() }
                        )
                    }
                }

                // About
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("About", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Cardea", style = MaterialTheme.typography.bodyMedium, color = CardeaTextSecondary)
                    Text(
                        text = "Heart rate zone coach for runners.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
