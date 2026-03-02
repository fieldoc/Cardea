package com.hrcoach.ui.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.R
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.util.formatDistanceKm
import com.hrcoach.util.formatPaceMinPerKm
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    onPauseResume: () -> Unit,
    onStopConfirmed: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    var stopConfirmationVisible by remember { mutableStateOf(false) }
    var pulseOn by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentHr) {
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

    val zoneColor = when (state.zoneStatus) {
        ZoneStatus.IN_ZONE -> MaterialTheme.colorScheme.tertiary
        ZoneStatus.BELOW_ZONE -> MaterialTheme.colorScheme.secondary
        ZoneStatus.ABOVE_ZONE -> MaterialTheme.colorScheme.error
        ZoneStatus.NO_DATA -> MaterialTheme.colorScheme.outline
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isPaused) {
                            stringResource(R.string.screen_workout_paused_title)
                        } else {
                            stringResource(R.string.screen_workout_title)
                        }
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!state.hrConnected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "HR monitor disconnected",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            BoxWithConstraints {
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (state.targetHr > 0) "Target: ${state.targetHr} bpm" else "Target unavailable",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (state.isPaused) "Workout Paused" else state.guidanceText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = if (state.isPaused) MaterialTheme.colorScheme.secondary else zoneColor
            )
            Text(
                text = when {
                    !state.hrConnected || state.currentHr <= 0 -> "Projected HR: --"
                    state.projectionReady && state.predictedHr > 0 -> "Projected HR: ${state.predictedHr} bpm"
                    else -> "Projected HR: Learning your patterns..."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_distance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    AnimatedContent(
                        targetState = formatDistanceKm(state.distanceMeters),
                        label = "distance-number"
                    ) { distanceText ->
                        Text(
                            text = distanceText,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = if (state.adaptiveLagSec > 0f) {
                            "Pace: ${formatPaceMinPerKm(state.paceMinPerKm)}  |  HR settles in ~${state.adaptiveLagSec.toInt()}s"
                        } else {
                            "Pace: ${formatPaceMinPerKm(state.paceMinPerKm)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onPauseResume,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text(
                        text = if (state.isPaused) {
                            stringResource(R.string.button_resume)
                        } else {
                            stringResource(R.string.button_pause)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
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
            Spacer(modifier = Modifier.height(20.dp))
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
                ) {
                    Text(stringResource(R.string.button_stop))
                }
            },
            dismissButton = {
                TextButton(onClick = { stopConfirmationVisible = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}
