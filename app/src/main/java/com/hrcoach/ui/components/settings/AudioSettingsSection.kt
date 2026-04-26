package com.hrcoach.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.hrcoach.domain.model.AudioSettings
import com.hrcoach.domain.model.DistanceUnit
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.ui.components.CardeaSlider
import com.hrcoach.ui.components.CardeaSwitch
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.components.cardeaSegmentedButtonColors
import com.hrcoach.ui.theme.CardeaTheme

/**
 * Events emitted by [AudioSettingsSection]. Events mirror the surface of
 * [com.hrcoach.ui.account.AccountViewModel] — volume sliders distinguish live
 * drag updates (`committed = false`) from release (`committed = true`) so that
 * consumers can replicate the DataStore-safe pattern (update in-memory state
 * flow on drag; persist on release).
 *
 * Each event maps 1:1 to a field in [AudioSettings]; there is no event for a
 * field that does not exist on the model.
 */
sealed class AudioSettingsEvent {
    /**
     * Earcon/alert volume slider moved.
     *
     * @param value 0f..100f (pre-quantization — consumer should coerce).
     * @param committed true when the user released the thumb; false for drag.
     */
    data class EarconVolumeChanged(val value: Float, val committed: Boolean) : AudioSettingsEvent()

    /**
     * Voice volume slider moved.
     *
     * @param value 0f..100f (pre-quantization — consumer should coerce).
     * @param committed true when the user released the thumb; false for drag.
     */
    data class VoiceVolumeChanged(val value: Float, val committed: Boolean) : AudioSettingsEvent()

    /** Voice verbosity segmented button changed (OFF / MINIMAL / FULL). */
    data class VoiceVerbosityChanged(val verbosity: VoiceVerbosity) : AudioSettingsEvent()

    /** Vibration alerts toggle flipped. */
    data class VibrationEnabledToggled(val enabled: Boolean) : AudioSettingsEvent()

    /** Halfway-point reminder toggle flipped. */
    data class HalfwayReminderToggled(val enabled: Boolean) : AudioSettingsEvent()

    /** Km/mile split announcement toggle flipped. */
    data class KmSplitsToggled(val enabled: Boolean) : AudioSettingsEvent()

    /** Workout-complete cue toggle flipped. */
    data class WorkoutCompleteToggled(val enabled: Boolean) : AudioSettingsEvent()

    /** In-zone confirmation cue toggle flipped. */
    data class InZoneConfirmToggled(val enabled: Boolean) : AudioSettingsEvent()

    /** Strides per-rep timer chimes (Go / Ease / Set Complete) toggle flipped. */
    data class StridesTimerEarconsToggled(val enabled: Boolean) : AudioSettingsEvent()
}

/**
 * Stateless audio & coaching settings card.
 *
 * Renders the audio controls (alert volume, voice volume, voice coaching
 * verbosity, vibration, and informational cue toggles) as a single [GlassCard]
 * and reports user interactions via [onEvent]. Identical to the inline block
 * that previously lived in `AccountScreen.kt` — extracted so the same UI can
 * be reused by the active-run settings bottom sheet.
 *
 * Slider persistence contract: `EarconVolumeChanged`/`VoiceVolumeChanged` fire
 * with `committed = false` on every drag tick and `committed = true` exactly
 * once on release. Consumers should update their in-memory state flow on drag
 * and persist on release — writing DataStore on every drag tick freezes the UI.
 *
 * @param state source-of-truth for all displayed values.
 * @param onEvent callback receiving each user-initiated change.
 * @param distanceUnit controls whether the split toggle is labelled "Kilometer
 *   splits" or "Mile splits". Defaults to KM.
 * @param modifier outer modifier applied to the [GlassCard].
 */
@Composable
fun AudioSettingsSection(
    state: AudioSettings,
    onEvent: (AudioSettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    distanceUnit: DistanceUnit = DistanceUnit.KM,
) {
    GlassCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        // Volume
        SettingSection(icon = Icons.Default.VolumeUp, title = "Alert Volume") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardeaSlider(
                    value = state.earconVolume.toFloat(),
                    onValueChange = { v -> onEvent(AudioSettingsEvent.EarconVolumeChanged(v, committed = false)) },
                    valueRange = 0f..100f,
                    steps = 0,
                    onValueChangeFinished = {
                        onEvent(AudioSettingsEvent.EarconVolumeChanged(state.earconVolume.toFloat(), committed = true))
                    },
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

        // Voice volume
        SettingSection(icon = Icons.Default.Mic, title = "Voice Volume") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardeaSlider(
                    value = state.voiceVolume.toFloat(),
                    onValueChange = { v -> onEvent(AudioSettingsEvent.VoiceVolumeChanged(v, committed = false)) },
                    valueRange = 0f..100f,
                    steps = 0,
                    onValueChangeFinished = {
                        onEvent(AudioSettingsEvent.VoiceVolumeChanged(state.voiceVolume.toFloat(), committed = true))
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${state.voiceVolume}%",
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
                            onClick = { onEvent(AudioSettingsEvent.VoiceVerbosityChanged(v)) },
                            colors = cardeaSegmentedButtonColors()
                        ) { Text(label) }
                    }
            }
        }

        // Show only the active voice mode description (not all 3)
        ActiveVoiceModeHint(currentVerbosity = state.voiceVerbosity)

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = CardeaTheme.colors.glassBorder)

        // Vibration toggle
        SettingToggleRow(
            icon = Icons.Default.Notifications,
            title = "Vibration Alerts",
            checked = state.enableVibration,
            onCheckedChange = { onEvent(AudioSettingsEvent.VibrationEnabledToggled(it)) }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = CardeaTheme.colors.glassBorder)

        // Fine-tune Cues
        SettingSection(icon = Icons.Default.Mic, title = "Fine-tune Cues") {
            // Informational cues only fire in FULL verbosity — in OFF and MINIMAL the
            // toggles are inactive regardless of stored state. We render them as
            // strikethrough with a muted switch so the user still sees what THEIR
            // setting is (not a false "off") while making clear the control is gated.
            val cuesEnabled = state.voiceVerbosity == VoiceVerbosity.FULL
            val gateHint = when (state.voiceVerbosity) {
                VoiceVerbosity.OFF ->
                    "Inactive while Voice Coaching is Off."
                VoiceVerbosity.MINIMAL ->
                    "Inactive in Minimal — switch to Full to use these."
                VoiceVerbosity.FULL ->
                    "Fine-tune which informational cues you hear."
            }
            Text(
                text = gateHint,
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTheme.colors.textTertiary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            // Nullable booleans in AudioSettings default to "on" when unset (mirrors VM init).
            val halfwayOn = state.enableHalfwayReminder ?: true
            val kmSplitsOn = state.enableKmSplits ?: true
            val workoutCompleteOn = state.enableWorkoutComplete ?: true
            val inZoneConfirmOn = state.enableInZoneConfirm ?: true
            Column {
                InfoCueToggle(
                    label = "Halfway reminder",
                    checked = halfwayOn,
                    enabled = cuesEnabled
                ) { onEvent(AudioSettingsEvent.HalfwayReminderToggled(it)) }
                InfoCueToggle(
                    label = if (distanceUnit == DistanceUnit.MI) "Mile splits" else "Kilometer splits",
                    checked = kmSplitsOn,
                    enabled = cuesEnabled
                ) { onEvent(AudioSettingsEvent.KmSplitsToggled(it)) }
                InfoCueToggle(
                    label = "Workout complete",
                    checked = workoutCompleteOn,
                    enabled = cuesEnabled
                ) { onEvent(AudioSettingsEvent.WorkoutCompleteToggled(it)) }
                InfoCueToggle(
                    label = "In-zone confirmation",
                    checked = inZoneConfirmOn,
                    enabled = cuesEnabled
                ) { onEvent(AudioSettingsEvent.InZoneConfirmToggled(it)) }
                // Strides timer chimes are gated on shouldPlayEarcon(verbosity) like other
                // info cues, so the same `cuesEnabled` gate applies.
                InfoCueToggle(
                    label = "Strides timer chimes",
                    checked = state.stridesTimerEarcons,
                    enabled = cuesEnabled
                ) { onEvent(AudioSettingsEvent.StridesTimerEarconsToggled(it)) }
            }
        }
    }
}

// ── Internal helpers (copies of AccountScreen's private helpers) ─────────────
// These are duplicated rather than made public because AccountScreen still
// uses its own copies for non-audio rows (Auto-pause, Theme, Maps API key,
// etc.). If more screens start needing them, promote to a shared file.

@Composable
private fun SettingIconBox(
    icon: ImageVector,
    tint: Color = CardeaTheme.colors.textSecondary
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(tint.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .border(1.dp, tint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SettingSection(
    icon: ImageVector,
    title: String,
    iconTint: Color = CardeaTheme.colors.textSecondary,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            SettingIconBox(icon = icon, tint = iconTint)
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

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = CardeaTheme.colors.textSecondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingIconBox(icon = icon, tint = iconTint)
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

@Composable
private fun ActiveVoiceModeHint(currentVerbosity: VoiceVerbosity) {
    val hint = when (currentVerbosity) {
        VoiceVerbosity.OFF -> "Silent running. Pause/resume tones still play for safety."
        VoiceVerbosity.MINIMAL -> "Zone change alerts and workout start/end cues only."
        VoiceVerbosity.FULL -> "All coaching: zone alerts, splits, pacing, and informational cues."
    }
    Text(
        text = hint,
        style = MaterialTheme.typography.bodySmall,
        color = CardeaTheme.colors.textTertiary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun InfoCueToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    // When disabled by verbosity gate: strikethrough label + dimmed switch so user sees
    // their stored preference (not a false "off") but understands the control is inactive.
    val labelColor =
        if (enabled) CardeaTheme.colors.textSecondary
        else CardeaTheme.colors.textTertiary
    val labelDecoration =
        if (enabled) TextDecoration.None
        else TextDecoration.LineThrough
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = labelDecoration),
            color = labelColor
        )
        // Dimming only the switch (not its row) preserves the contrast of the strikethrough
        // text against the glass surface — if we alpha'd the whole row, the label would
        // wash out below AA contrast on #050505.
        Box(modifier = Modifier.alpha(if (enabled) 1f else 0.45f)) {
            CardeaSwitch(checked = checked, onCheckedChange = { if (enabled) onCheckedChange(it) })
        }
    }
}
