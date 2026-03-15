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
import com.hrcoach.ui.components.CardeaSlider
import com.hrcoach.ui.components.CardeaSwitch
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.components.cardeaSegmentedButtonColors
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaCtaGradient
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onThemeModeChanged: (ThemeMode) -> Unit = {},
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showProfileSheet by remember { mutableStateOf(false) }

    if (showProfileSheet) {
        ProfileEditBottomSheet(
            displayName = state.displayName,
            avatarSymbol = state.avatarSymbol,
            onNameChange = viewModel::setDisplayName,
            onAvatarChange = viewModel::setAvatarSymbol,
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
            .background(CardeaBgPrimary)
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
                color = CardeaTextPrimary
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
                avatarSymbol = state.avatarSymbol,
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
                            .background(Color(0x0FFFFFFF), RoundedCornerShape(8.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = CardeaTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTextPrimary
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
                            color = if (state.mapsApiKeySaved) ZoneGreen else CardeaTextTertiary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        GradientSaveButton(onClick = viewModel::saveMapsApiKey)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = GlassBorder
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
                                else -> CardeaTextTertiary
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
                            color = CardeaTextSecondary
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = GlassBorder)

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

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = GlassBorder)

                // Vibration toggle
                SettingToggleRow(
                    icon = Icons.Default.Notifications,
                    title = "Vibration Alerts",
                    checked = state.enableVibration,
                    onCheckedChange = { viewModel.setVibration(it); viewModel.saveAudioSettings() }
                )
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

            Spacer(modifier = Modifier.height(32.dp))

            // ── About ─────────────────────────────────────────────────────────
            Text(
                text = "Cardea · Heart rate zone coach for runners",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                color = CardeaTextTertiary,
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
    avatarSymbol: String,
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
            // Gradient ring with unicode symbol
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CardeaGradient),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(CardeaBgPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = avatarSymbol,
                        fontSize = 24.sp,
                        color = Color(0xFFFF6B8A)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = CardeaTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$runCount runs recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTextSecondary
                )
            }
        }
    }
}

// ── Avatar symbols ────────────────────────────────────────────────────────────

private val AVATAR_SYMBOLS = listOf(
    "\u2665", // ♥
    "\u2605", // ★
    "\u26A1", // ⚡
    "\u25C6", // ◆
    "\u25B2", // ▲
    "\u25CF", // ●
    "\u2726", // ✦
    "\u2666", // ♦
    "\u2191", // ↑
    "\u221E", // ∞
)

// ── Profile edit bottom sheet ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditBottomSheet(
    displayName: String,
    avatarSymbol: String,
    onNameChange: (String) -> Unit,
    onAvatarChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardeaBgSecondary,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .size(32.dp, 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CardeaTextTertiary)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.titleMedium,
                color = CardeaTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Name field
            Text("Name", style = MaterialTheme.typography.labelMedium, color = CardeaTextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 20) onNameChange(it) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF6B8A),
                    unfocusedBorderColor = CardeaTextTertiary,
                    cursorColor = Color(0xFFFF6B8A),
                    focusedTextColor = CardeaTextPrimary,
                    unfocusedTextColor = CardeaTextPrimary,
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Avatar picker
            Text("Avatar", style = MaterialTheme.typography.labelMedium, color = CardeaTextSecondary)
            Spacer(modifier = Modifier.height(10.dp))

            // 5x2 grid
            for (row in AVATAR_SYMBOLS.chunked(5)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { symbol ->
                        val selected = symbol == avatarSymbol
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) CardeaGradient
                                    else Brush.linearGradient(
                                        listOf(
                                            CardeaTextTertiary.copy(alpha = 0.3f),
                                            CardeaTextTertiary.copy(alpha = 0.1f)
                                        )
                                    )
                                )
                                .clickable { onAvatarChange(symbol) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(CardeaBgPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = symbol,
                                    fontSize = 20.sp,
                                    color = if (selected) Color(0xFFFF6B8A)
                                            else CardeaTextTertiary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

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
        color = CardeaTextTertiary,
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
                    .background(Color(0x0FFFFFFF), RoundedCornerShape(8.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CardeaTextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTextPrimary
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
                .background(Color(0x0FFFFFFF), RoundedCornerShape(8.dp))
                .border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CardeaTextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTextPrimary
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTextTertiary
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
            color = Color.White
        )
    }
}
