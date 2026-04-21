package com.hrcoach.ui.workout

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.R
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.service.WorkoutSnapshot
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GlassHighlight
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.CardeaCtaGradient
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.ZoneAmber
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed
import com.hrcoach.service.simulation.SimulationController
import com.hrcoach.ui.debug.SimulationOverlay
import com.hrcoach.util.formatDistance
import com.hrcoach.util.formatDurationSeconds
import com.hrcoach.util.formatPace
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

@Composable
fun ActiveWorkoutScreen(
    onPauseResume: () -> Unit,
    onStopConfirmed: () -> Unit,
    onConnectHr: () -> Unit,
    onToggleAutoPause: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    val hrRingAlpha by animateFloatAsState(
        targetValue = if (state.isAutoPaused) 0.4f else 1f,
        label = "hr-ring-alpha"
    )

    val zoneColor = zoneColorFor(state)

    // Zone ambient — screen breathes with the current zone
    val ambientColor by animateColorAsState(
        targetValue = zoneColor.copy(alpha = 0.09f),
        animationSpec = tween(durationMillis = 1200),
        label = "zone-ambient-bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to ambientColor,
                            0.5f to CardeaBgPrimary,
                            1f to CardeaBgPrimary
                        )
                    )
                )
            }
    ) {
        if (SimulationController.isActive) {
            SimulationOverlay(modifier = Modifier.align(Alignment.TopCenter).zIndex(10f))
        }

        // Countdown overlay: shows 3/2/1/GO during startup so users know the run registered.
        // Rendered first (below cue banner in source order but gated so banner is suppressed
        // while countdown is visible — avoids two transient overlays competing for attention).
        val countdownSeconds = state.countdownSecondsRemaining
        val countdownActive = countdownSeconds != null

        // Transient cue banner — visible whenever a coaching event fires.
        // Ignores voice verbosity: this is a transparency feature.
        // Suppressed during countdown overlay so BLE/GPS "searching" cues don't stack on top
        // of the "3-2-1-GO" digits during the TTS+countdown window.
        if (!countdownActive) {
            CueBannerOverlay(
                banner = state.lastCueBanner,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .zIndex(9f)
            )
        }

        if (countdownSeconds != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.92f),
                                CardeaBgPrimary.copy(alpha = 0.82f),
                            )
                        )
                    )
                    .zIndex(8f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (countdownSeconds > 0) countdownSeconds.toString() else "GO",
                    fontSize = 112.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (countdownSeconds == 0) ZoneGreen else Color.White
                )
            }
        }

        // Hide workout chrome entirely during countdown — a 0.92-alpha radial overlay still
        // lets faint outlines bleed through, and a zeroed stat card ("0 bpm", "0.00 km") is
        // more jarring than empty space.
        if (!countdownActive) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mission Card: goal context, zone status, timer, target HR
            MissionCard(
                uiState = uiState,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Progress bar with halfway turn marker
            run {
                val totalDur = uiState.totalDurationSeconds
                val totalDist = uiState.totalDistanceMeters
                val showProgress = totalDur != null || totalDist != null
                if (showProgress) {
                    val progress = when {
                        totalDur != null ->
                            (uiState.elapsedSeconds.toFloat() / totalDur).coerceIn(0f, 1f)
                        totalDist != null ->
                            (state.distanceMeters / totalDist).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    val turnLabel = when {
                        totalDur != null ->
                            "Turn ${formatDurationSeconds(totalDur / 2)}"
                        totalDist != null ->
                            "Turn ${formatDistance(totalDist / 2f, uiState.distanceUnit)}"
                        else -> null
                    }
                    ProgressBarWithTurnMarker(
                        progress = progress,
                        showTurnMarker = true,
                        turnLabel = turnLabel
                    )
                }
            }

            HrRing(
                hr = state.currentHr,
                isConnected = state.hrConnected,
                zoneColor = zoneColor,
                pulseScale = pulseScale,
                onConnectHr = onConnectHr,
                modifier = Modifier.graphicsLayer { alpha = hrRingAlpha }
            )

            // Projected HR pill — subtle, below ring
            val projectedText = when {
                !state.hrConnected || state.currentHr <= 0 -> null
                state.projectionReady && state.predictedHr > 0 -> "Projected ${state.predictedHr} bpm"
                else -> null
            }
            if (projectedText != null) {
                Box(
                    modifier = Modifier
                        .background(GlassBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = projectedText,
                        style = MaterialTheme.typography.labelSmall,
                        color = CardeaTextTertiary
                    )
                }
            }

            // Distance + Pace — promoted to hero cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroStatCard(
                    label = "Distance",
                    value = formatDistance(state.distanceMeters, uiState.distanceUnit),
                    unit = if (uiState.distanceUnit == com.hrcoach.domain.model.DistanceUnit.MI) "mi" else "km",
                    zoneColor = zoneColor,
                    modifier = Modifier.weight(1f)
                )
                HeroStatCard(
                    label = "Pace",
                    value = formatPace(state.paceMinPerKm, uiState.distanceUnit),
                    unit = if (uiState.distanceUnit == com.hrcoach.domain.model.DistanceUnit.MI) "/mi" else "/km",
                    zoneColor = zoneColor,
                    modifier = Modifier.weight(1f)
                )
            }

            GuidanceCard(
                guidance = when {
                    state.isPaused -> "Workout Paused"
                    else -> state.guidanceText
                },
                zoneColor = zoneColor,
                isActive = state.guidanceText.isNotBlank() && !state.isPaused && !state.isAutoPaused
            )

            uiState.segmentLabel?.let { label ->
                val countdown = uiState.segmentCountdownSeconds ?: 0L
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = CardeaTextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = CardeaTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "%d:%02d remaining".format(countdown / 60, countdown % 60),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CardeaTextPrimary
                            )
                        }
                        uiState.nextSegmentLabel?.let { next ->
                            Text(
                                text = "next › $next",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTextSecondary
                            )
                        }
                    }
                }
            }

            // Tertiary stats strip: Avg HR · Auto-pause toggle
            TertiaryStatsRow(
                state = state,
                onToggleAutoPause = onToggleAutoPause
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isPaused) {
                // Paused: gradient Resume (high urgency) + low-opacity outlined End run
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardeaCtaGradient)
                        .clickable(onClick = onPauseResume),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.button_resume),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, ZoneRed.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .clickable { stopConfirmationVisible = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.button_stop),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = ZoneRed.copy(alpha = 0.80f)
                    )
                }
            } else {
                // Running: equal ghost Pause + outlined Stop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WorkoutButton(
                        text = stringResource(R.string.button_pause),
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
        }
        }  // end if (!countdownActive)
    }

    if (stopConfirmationVisible) {
        StopConfirmationSheet(
            onConfirm = {
                stopConfirmationVisible = false
                onStopConfirmed()
            },
            onDismiss = { stopConfirmationVisible = false }
        )
    }
}

@Composable
private fun HeroStatCard(
    label: String,
    value: String,
    unit: String,
    zoneColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GlassHighlight)
            .border(1.dp, zoneColor.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTextTertiary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = CardeaTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTextTertiary
            )
        }
    }
}

@Composable
private fun TertiaryStatsRow(
    state: WorkoutSnapshot,
    onToggleAutoPause: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(GlassHighlight)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
    ) {
        // Avg HR
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Avg HR",
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTextTertiary
            )
            Text(
                text = if (state.avgHr > 0) "${state.avgHr}" else "--",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = CardeaTextSecondary
            )
        }
        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .align(Alignment.CenterVertically)
                .background(GlassBorder)
        )
        // Auto-pause toggle
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToggleAutoPause)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (state.autoPauseEnabled) ZoneGreen else CardeaTextTertiary,
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = "Auto-pause",
                    style = MaterialTheme.typography.labelSmall,
                    color = CardeaTextTertiary
                )
                Text(
                    text = if (state.autoPauseEnabled) "On" else "Off",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (state.autoPauseEnabled) ZoneGreen else CardeaTextSecondary
                )
            }
        }
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
private fun GuidanceCard(
    guidance: String,
    zoneColor: Color,
    isActive: Boolean
) {
    val transition = rememberInfiniteTransition(label = "guidance-pulse")
    val stripAlpha = if (isActive) {
        transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(850),
                repeatMode = RepeatMode.Reverse
            ),
            label = "guidance-strip-alpha"
        ).value
    } else {
        0.4f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(GlassHighlight)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
    ) {
        // Left color strip — pulses with zone color when active
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(zoneColor.copy(alpha = stripAlpha))
        )
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Guidance",
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTextSecondary
            )
            Text(
                text = guidance.ifBlank { "Stay ready" },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTextPrimary
            )
        }
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


