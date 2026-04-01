package com.hrcoach.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrcoach.domain.model.ThemeMode
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.domain.emblem.Emblem
import com.hrcoach.ui.components.CardeaSlider
import com.hrcoach.ui.components.CardeaSwitch
import com.hrcoach.ui.components.EmblemIconWithRing
import com.hrcoach.ui.components.EmblemPicker
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.components.cardeaSegmentedButtonColors
import com.hrcoach.ui.theme.CardeaCtaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.BuildConfig
import com.hrcoach.ui.theme.ZoneRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onNavigateToSimulation: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showProfileSheet by remember { mutableStateOf(false) }

    if (showProfileSheet) {
        ProfileEditBottomSheet(
            displayName = state.displayName,
            emblemId = state.emblemId,
            onNameChange = viewModel::setDisplayName,
            onEmblemChange = { viewModel.setEmblemId(it) },
            onSave = {
                viewModel.saveProfile()
                showProfileSheet = false
            },
            onDismiss = { showProfileSheet = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaTheme.colors.bgPrimary)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = CardeaTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Scrollable content ───────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Profile hero ─────────────────────────────────────────────────
            ProfileHeroCard(
                displayName = state.displayName,
                emblemId = state.emblemId,
                runCount = state.totalWorkouts,
                onClick = { showProfileSheet = true }
            )

            if (state.achievements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                AchievementGallery(achievements = state.achievements)
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }

            // ── Appearance ────────────────────────────────────────────────────
            SectionLabel("Appearance")
            Spacer(modifier = Modifier.height(6.dp))

            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(CardeaTheme.colors.glassHighlight, RoundedCornerShape(8.dp))
                            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = CardeaTheme.colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTheme.colors.textPrimary
                    )
                }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
                        .forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                                selected = currentThemeMode == mode,
                                onClick = { onThemeModeChanged(mode) },
                                colors = cardeaSegmentedButtonColors()
                            ) { Text(label) }
                        }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Configuration ────────────────────────────────────────────────
            SectionLabel("Configuration")
            Spacer(modifier = Modifier.height(6.dp))

            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                // Maps row
                SettingSection(icon = Icons.Default.Map, title = "Maps API Key") {
                    OutlinedTextField(
                        value = state.mapsApiKey,
                        onValueChange = viewModel::setMapsApiKey,
                        singleLine = true,
                        label = { Text("Google Maps API key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.mapsApiKeySaved) "Saved. Restart if map still appears blank."
                                   else "Used for route rendering in History.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.mapsApiKeySaved) ZoneGreen else CardeaTheme.colors.textTertiary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        GradientSaveButton(onClick = viewModel::saveMapsApiKey)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = CardeaTheme.colors.glassBorder
                )

                // Max HR row
                SettingSection(icon = Icons.Default.Favorite, title = "Max Heart Rate") {
                    OutlinedTextField(
                        value = state.maxHrInput,
                        onValueChange = viewModel::setMaxHrInput,
                        singleLine = true,
                        label = { Text("Max HR (bpm)") },
                        placeholder = { Text("e.g. 185") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.maxHrError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.maxHrError
                                ?: if (state.maxHrSaved) "Saved."
                                   else "Personalises all preset HR targets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                state.maxHrError != null -> ZoneRed
                                state.maxHrSaved -> ZoneGreen
                                else -> CardeaTheme.colors.textTertiary
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        GradientSaveButton(onClick = viewModel::saveMaxHr)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Audio & Alerts ───────────────────────────────────────────────
            SectionLabel("Audio & Alerts")
            Spacer(modifier = Modifier.height(6.dp))

            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                // Volume
                SettingSection(icon = Icons.Default.VolumeUp, title = "Alert Volume") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CardeaSlider(
                            value = state.earconVolume.toFloat(),
                            onValueChange = viewModel::setVolume,
                            valueRange = 0f..100f,
                            steps = 19,
                            onValueChangeFinished = viewModel::saveAudioSettings,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${state.earconVolume}%",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = CardeaTheme.colors.textSecondary
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = CardeaTheme.colors.glassBorder)

                // Voice Coaching
                SettingSection(icon = Icons.Default.Mic, title = "Voice Coaching") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(VoiceVerbosity.OFF to "Off", VoiceVerbosity.MINIMAL to "Minimal", VoiceVerbosity.FULL to "Full")
                            .forEachIndexed { i, (v, label) ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                                    selected = state.voiceVerbosity == v,
                                    onClick = { viewModel.setVerbosity(v); viewModel.saveAudioSettings() },
                                    colors = cardeaSegmentedButtonColors()
                                ) { Text(label) }
                            }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = CardeaTheme.colors.glassBorder)

                // Vibration toggle
                SettingToggleRow(
                    icon = Icons.Default.Notifications,
                    title = "Vibration Alerts",
                    checked = state.enableVibration,
                    onCheckedChange = { viewModel.setVibration(it); viewModel.saveAudioSettings() }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = CardeaTheme.colors.glassBorder)

                // Informational Cues
                SettingSection(icon = Icons.Default.Mic, title = "Informational Cues") {
                    val cuesEnabled = state.voiceVerbosity != VoiceVerbosity.OFF
                    val cueAlpha = if (cuesEnabled) 1f else 0.4f
                    Column(modifier = Modifier.alpha(cueAlpha)) {
                        InfoCueToggle("Halfway reminder", state.enableHalfwayReminder && cuesEnabled, cuesEnabled) { viewModel.setEnableHalfwayReminder(it) }
                        InfoCueToggle("Kilometer splits", state.enableKmSplits && cuesEnabled, cuesEnabled) { viewModel.setEnableKmSplits(it) }
                        InfoCueToggle("Workout complete", state.enableWorkoutComplete && cuesEnabled, cuesEnabled) { viewModel.setEnableWorkoutComplete(it) }
                        InfoCueToggle("In-zone confirmation", state.enableInZoneConfirm && cuesEnabled, cuesEnabled) { viewModel.setEnableInZoneConfirm(it) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Workout ───────────────────────────────────────────────────────
            SectionLabel("Workout")
            Spacer(modifier = Modifier.height(6.dp))

            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                SettingToggleRow(
                    icon = Icons.Default.Settings,
                    title = "Auto-pause when stopped",
                    subtitle = "Silences alerts and pauses the timer at red lights or breaks",
                    checked = state.autoPauseEnabled,
                    onCheckedChange = viewModel::setAutoPauseEnabled
                )
            }

            // ── Simulation (debug only) ────────────────────────────────────
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(20.dp))
                SectionLabel("Simulation")
                Spacer(modifier = Modifier.height(6.dp))

                GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToSimulation)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(CardeaTheme.colors.glassHighlight, RoundedCornerShape(8.dp))
                                .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = CardeaTheme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Simulation Environment",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = CardeaTheme.colors.textPrimary
                            )
                            Text(
                                text = "Test workouts with simulated HR & GPS",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTheme.colors.textTertiary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── About ─────────────────────────────────────────────────────────
            Text(
                text = "Cardea · Heart rate zone coach for runners",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                color = CardeaTheme.colors.textTertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ── Profile hero ──────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeroCard(
    displayName: String,
    emblemId: String,
    runCount: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        GradientRed.copy(alpha = 0.10f),
                        GradientPink.copy(alpha = 0.06f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .border(1.dp, GradientRed.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmblemIconWithRing(
                emblem = Emblem.fromId(emblemId),
                size = 56.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = CardeaTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$runCount runs recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textSecondary
                )
            }
        }
    }
}

// ── Profile edit bottom sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditBottomSheet(
    displayName: String,
    emblemId: String,
    onNameChange: (String) -> Unit,
    onEmblemChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaTheme.colors.bgSecondary,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .size(32.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CardeaTheme.colors.textTertiary)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.titleMedium,
                color = CardeaTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Name field — select-all on open so user can immediately type a replacement
            Text("Name", style = MaterialTheme.typography.labelMedium, color = CardeaTheme.colors.textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            var nameField by remember {
                mutableStateOf(TextFieldValue(displayName, selection = TextRange(0, displayName.length)))
            }
            OutlinedTextField(
                value = nameField,
                onValueChange = { tfv ->
                    if (tfv.text.length <= 20) {
                        nameField = tfv
                        onNameChange(tfv.text)
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CardeaTheme.colors.accentPink,
                    unfocusedBorderColor = CardeaTheme.colors.textTertiary,
                    cursorColor = CardeaTheme.colors.accentPink,
                    focusedTextColor = CardeaTheme.colors.textPrimary,
                    unfocusedTextColor = CardeaTheme.colors.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Emblem picker
            Text("Emblem", style = MaterialTheme.typography.labelMedium, color = CardeaTheme.colors.textSecondary)
            Spacer(modifier = Modifier.height(10.dp))
            EmblemPicker(
                selected = Emblem.fromId(emblemId),
                onSelect = { onEmblemChange(it.id) },
                modifier = Modifier.height(200.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))
            GradientSaveButton(onClick = onSave)
        }
    }
}

// ── Section label ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.08.sp,
            fontSize = 10.sp
        ),
        color = CardeaTheme.colors.textTertiary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

// ── Setting section (icon + title + content slot) ─────────────────────────────

@Composable
private fun SettingSection(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(CardeaTheme.colors.glassHighlight, RoundedCornerShape(8.dp))
                    .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CardeaTheme.colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTheme.colors.textPrimary
            )
        }
        content()
    }
}

// ── Setting toggle row ────────────────────────────────────────────────────────

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(CardeaTheme.colors.glassHighlight, RoundedCornerShape(8.dp))
                .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CardeaTheme.colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTheme.colors.textPrimary
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textTertiary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        CardeaSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── Gradient save button ──────────────────────────────────────────────────────

@Composable
private fun GradientSaveButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CardeaCtaGradient)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Save",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = CardeaTheme.colors.onGradient
        )
    }
}

@Composable
private fun InfoCueToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CardeaTheme.colors.textSecondary)
        CardeaSwitch(checked = checked, onCheckedChange = { if (enabled) onCheckedChange(it) })
    }
}
