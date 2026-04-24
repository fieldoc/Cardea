package com.hrcoach.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hrcoach.ui.components.settings.AudioSettingsSection
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.ZoneRed

/**
 * Mid-run settings bottom sheet. Surfaces audio settings + (for bootcamp sessions) an
 * "End session early" action that finalizes progress rather than discarding.
 *
 * Invariants:
 * - Audio settings persist on each event via [ActiveRunSettingsViewModel.onEvent], which
 *   forwards to [WorkoutForegroundService.ACTION_RELOAD_AUDIO_SETTINGS] so the running
 *   service reloads live (never call `CoachingAudioManager.applySettings` from outside the
 *   service).
 * - "End session early" only renders when [pendingBootcampSessionId] is non-null.
 * - Caller is responsible for suppressing this sheet during the countdown window (see
 *   `ActiveWorkoutScreen`).
 * - Title uses Cardea's defined `headlineSmall` (22sp SemiBold — see `Type.kt`).
 * - "End session early" is an outlined destructive action (ZoneRed border + text) to mirror
 *   the "End Run" button styling; the CTA gradient is reserved for the primary forward
 *   action, and a session-ending control is destructive, not primary.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveRunSettingsSheet(
    pendingBootcampSessionId: Long?,
    onDismiss: () -> Unit,
    viewModel: ActiveRunSettingsViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val audioSettings by viewModel.audioSettings.collectAsStateWithLifecycle()
    val distanceUnit by viewModel.distanceUnit.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardeaBgPrimary,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Grab handle — Cardea-tinted, centered.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(CardeaTheme.colors.glassBorder)
                        .width(40.dp)
                        .height(4.dp)
                )
            }

            Text(
                text = "Run settings",
                style = MaterialTheme.typography.headlineSmall,
                color = CardeaTheme.colors.textPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            AudioSettingsSection(
                state = audioSettings,
                onEvent = viewModel::onEvent,
                distanceUnit = distanceUnit,
                modifier = Modifier.fillMaxWidth()
            )

            if (pendingBootcampSessionId != null) {
                Spacer(Modifier.height(4.dp))

                // Uppercase label section header — matches Cardea SectionHeader pattern
                // (see HomeScreen / Account screen precedent).
                Text(
                    text = "BOOTCAMP SESSION",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    color = CardeaTheme.colors.textSecondary
                )

                // Destructive-styled outlined button; explicit text color override to
                // prevent M3 text-color leak (docs: `CLAUDE.md` M3 button text color leak).
                OutlinedButton(
                    onClick = {
                        viewModel.finishBootcampEarly()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = ZoneRed
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ZoneRed.copy(alpha = 0.12f),
                        contentColor = ZoneRed
                    )
                ) {
                    Text(
                        text = "End session early",
                        style = MaterialTheme.typography.titleSmall,
                        color = ZoneRed
                    )
                }

                Text(
                    text = "Finalizes the session with progress so far and credits it in your plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
