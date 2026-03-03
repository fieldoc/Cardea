package com.hrcoach.ui.workout

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GlassHighlight
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.HrCoachThemeTokens
import com.hrcoach.ui.theme.ZoneAmber
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed
import com.hrcoach.util.formatDistanceKm
import com.hrcoach.util.formatPaceMinPerKm
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun ActiveWorkoutScreen(
    onPauseResume: () -> Unit,
    onStopConfirmed: () -> Unit,
    onConnectHr: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val state = uiState.snapshot
    var stopConfirmationVisible by remember { mutableStateOf(false) }
    var pulseOn by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentHr, state.hrConnected) {
        if (state.currentHr > 0 && state.hrConnected) {
            try {
                pulseOn = true
                delay(130L)
            } finally {
                pulseOn = false
            }
        }
    }

    val pulseScale by animateFloatAsState(
        targetValue = if (pulseOn) 1.04f else 1f,
        label = "hr-pulse-scale"
    )

    val zoneColor = zoneColorFor(state)

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
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cardea",
                    style = MaterialTheme.typography.titleMedium.copy(
                        brush = CardeaGradient
                    )
                )
                Text(
                    text = formatElapsedHms(uiState.elapsedSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = HrCoachThemeTokens.subtleText
                )
            }

            if (showDistanceProfileProgress(state, uiState)) {
                GradientProgressBar(
                    progress = (state.distanceMeters / (
                        uiState.workoutConfig?.segments?.lastOrNull()?.distanceMeters
                            ?: state.distanceMeters.coerceAtLeast(1f)
                        )).coerceIn(0f, 1f)
                )
            }

            ZoneStatusPill(state = state, zoneColor = zoneColor)

            Spacer(modifier = Modifier.height(4.dp))

            HrRing(
                hr = state.currentHr,
                isConnected = state.hrConnected,
                zoneColor = zoneColor,
                pulseScale = pulseScale,
                onConnectHr = onConnectHr
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InlineMetric(
                    label = "Target",
                    value = when {
                        state.isFreeRun -> "—"
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
                        label = "Avg HR",
                        value = if (state.avgHr > 0) "${state.avgHr}" else "--",
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
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WorkoutButton(
                text = if (state.isPaused) stringResource(R.string.button_resume) else stringResource(R.string.button_pause),
                onClick = onPauseResume,
                modifier = Modifier.weight(1f),
                borderColor = GlassBorder,
                backgroundColor = Color.Transparent
            )
            WorkoutButton(
                text = stringResource(R.string.button_stop),
                onClick = { stopConfirmationVisible = true },
                modifier = Modifier.weight(1f),
                borderColor = ZoneRed,
                backgroundColor = ZoneRed.copy(alpha = 0.15f)
            )
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
private fun WorkoutButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = GlassBorder,
    backgroundColor: Color = Color.Transparent
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = CardeaTextPrimary
        )
    }
}

@Composable
private fun GradientProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
    ) {
        val cornerRadius = CornerRadius(2.dp.toPx())
        drawRoundRect(color = GlassHighlight, cornerRadius = cornerRadius)
        if (progress > 0f) {
            drawRoundRect(
                brush = CardeaGradient,
                size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                cornerRadius = cornerRadius
            )
        }
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

    GlassCard(borderColor = zoneColor.copy(alpha = borderAlpha)) {
        Text(
            text = "Guidance",
            style = MaterialTheme.typography.labelSmall,
            color = HrCoachThemeTokens.subtleText
        )
        Text(
            text = guidance.ifBlank { "Stay ready" },
            style = MaterialTheme.typography.titleLarge,
            color = CardeaTextPrimary
        )
    }
}

@Composable
private fun ZoneStatusPill(state: WorkoutSnapshot, zoneColor: Color) {
    val label = when {
        state.isFreeRun && state.hrConnected -> "FREE RUN"
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
            color = zoneColor,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
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
            color = CardeaTextPrimary
        )
    }
}

@Composable
private fun zoneColorFor(state: WorkoutSnapshot): Color {
    return if (state.isFreeRun) {
        GradientBlue
    } else {
        when (state.zoneStatus) {
            ZoneStatus.IN_ZONE -> ZoneGreen
            ZoneStatus.BELOW_ZONE -> ZoneAmber
            ZoneStatus.ABOVE_ZONE -> ZoneRed
            ZoneStatus.NO_DATA -> CardeaTextTertiary
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
    return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
}
