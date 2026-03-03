package com.hrcoach.ui.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.R
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.service.WorkoutSnapshot
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.components.StatItem
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.HrCoachThemeTokens
import com.hrcoach.util.formatDistanceKm
import com.hrcoach.util.formatPaceMinPerKm
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onPauseResume: () -> Unit,
    onStopConfirmed: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState.snapshot
    var stopConfirmationVisible by remember { mutableStateOf(false) }
    var pulseOn by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentHr, state.hrConnected) {
        if (state.currentHr > 0 && state.hrConnected) {
            pulseOn = true
            delay(130L)
            pulseOn = false
        }
    }

    val pulseScale by animateFloatAsState(
        targetValue = if (pulseOn) 1.04f else 1f,
        label = "hr-pulse-scale"
    )

    val zoneColor = zoneColorFor(state)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HR Coach") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    Text(
                        text = formatElapsedHms(uiState.elapsedSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        color = HrCoachThemeTokens.subtleText
                    )
                }
            )
        }
    ) { paddingValues ->
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
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showDistanceProfileProgress(state, uiState)) {
                    LinearProgressIndicator(
                        progress = {
                            (state.distanceMeters / (uiState.workoutConfig?.segments?.lastOrNull()?.distanceMeters
                                ?: state.distanceMeters.coerceAtLeast(1f))).coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                if (!state.hrConnected) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text(
                            text = "HR monitor disconnected. Guidance and projections will resume when the signal returns.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                ZoneStatusPill(state = state, zoneColor = zoneColor)

                Spacer(modifier = Modifier.height(8.dp))

                BoxWithConstraints(contentAlignment = Alignment.Center) {
                    val hrFontSize = if (maxWidth < 360.dp) 72.sp else 92.sp
                    AnimatedContent(
                        targetState = if (state.currentHr > 0) state.currentHr.toString() else "---",
                        label = "hr-number"
                    ) { hrText ->
                        Text(
                            text = hrText,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = hrFontSize),
                            color = zoneColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.scale(pulseScale)
                        )
                    }
                }
                Text(
                    text = "bpm",
                    style = MaterialTheme.typography.labelSmall,
                    color = HrCoachThemeTokens.subtleText
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InlineMetric(
                        label = "Target",
                        value = when {
                            state.isFreeRun -> "Free run"
                            state.targetHr > 0 -> "${state.targetHr} bpm"
                            else -> "--"
                        }
                    )
                    InlineMetric(
                        label = "Projected",
                        value = when {
                            !state.hrConnected || state.currentHr <= 0 -> "--"
                            state.projectionReady && state.predictedHr > 0 -> "${state.predictedHr} bpm"
                            else -> "Learning"
                        }
                    )
                }

                GuidanceCard(
                    guidance = if (state.isPaused) "Workout Paused" else state.guidanceText,
                    zoneColor = zoneColor,
                    isActive = state.guidanceText.isNotBlank() && !state.isPaused
                )

                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem(
                            label = "Distance",
                            value = formatDistanceKm(state.distanceMeters),
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        )
                        StatItem(
                            label = "Pace",
                            value = formatPaceMinPerKm(state.paceMinPerKm),
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        )
                        StatItem(
                            label = "Lag",
                            value = if (state.adaptiveLagSec > 0f) "${state.adaptiveLagSec.toInt()}s" else "--",
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPauseResume,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text(
                        text = if (state.isPaused) stringResource(R.string.button_resume) else stringResource(R.string.button_pause),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Button(
                    onClick = { stopConfirmationVisible = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = stringResource(R.string.button_stop),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    if (stopConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { stopConfirmationVisible = false },
            title = { Text(stringResource(R.string.dialog_stop_workout_title)) },
            text = { Text(stringResource(R.string.dialog_stop_workout_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        stopConfirmationVisible = false
                        onStopConfirmed()
                    }
                ) { Text(stringResource(R.string.button_stop)) }
            },
            dismissButton = {
                TextButton(onClick = { stopConfirmationVisible = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun GuidanceCard(
    guidance: String,
    zoneColor: Color,
    isActive: Boolean
) {
    val transition = rememberInfiniteTransition(label = "guidance-pulse")
    val borderAlpha = if (isActive) {
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(850),
                repeatMode = RepeatMode.Reverse
            ),
            label = "guidance-border-alpha"
        ).value
    } else {
        0.3f
    }

    GlassCard(
        borderColor = zoneColor.copy(alpha = borderAlpha)
    ) {
        Text(
            text = "Guidance",
            style = MaterialTheme.typography.labelSmall,
            color = HrCoachThemeTokens.subtleText
        )
        Text(
            text = guidance.ifBlank { "Stay ready" },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ZoneStatusPill(state: WorkoutSnapshot, zoneColor: Color) {
    val label = when {
        !state.hrConnected || state.zoneStatus == ZoneStatus.NO_DATA -> "NO SIGNAL"
        state.zoneStatus == ZoneStatus.IN_ZONE -> "IN ZONE"
        state.zoneStatus == ZoneStatus.ABOVE_ZONE -> "ABOVE ZONE"
        state.zoneStatus == ZoneStatus.BELOW_ZONE -> "BELOW ZONE"
        else -> "NO SIGNAL"
    }
    Row(
        modifier = Modifier
            .border(1.dp, zoneColor, RoundedCornerShape(999.dp))
            .background(zoneColor.copy(alpha = 0.2f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(CardeaGradient, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = zoneColor
        )
    }
}

@Composable
private fun InlineMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = HrCoachThemeTokens.subtleText
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun zoneColorFor(state: WorkoutSnapshot): Color {
    return if (state.isFreeRun) {
        MaterialTheme.colorScheme.primary
    } else {
        when (state.zoneStatus) {
            ZoneStatus.IN_ZONE -> MaterialTheme.colorScheme.tertiary
            ZoneStatus.BELOW_ZONE -> MaterialTheme.colorScheme.secondary
            ZoneStatus.ABOVE_ZONE -> MaterialTheme.colorScheme.error
            ZoneStatus.NO_DATA -> HrCoachThemeTokens.subtleText
        }
    }
}

private fun showDistanceProfileProgress(
    state: WorkoutSnapshot,
    uiState: ActiveWorkoutUiState
): Boolean {
    val config = uiState.workoutConfig ?: return false
    return !state.isFreeRun &&
        config.mode == WorkoutMode.DISTANCE_PROFILE &&
        config.segments.isNotEmpty() &&
        (config.segments.lastOrNull()?.distanceMeters ?: 0f) > 0f
}

private fun formatElapsedHms(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}
