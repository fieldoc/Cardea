package com.hrcoach.ui.history

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.hrcoach.data.db.TrackPointEntity
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.util.JsonCodec
import com.hrcoach.util.formatDuration
import com.hrcoach.util.formatWorkoutDate

private enum class HistoryDetailContentState {
    LOADING,
    EMPTY,
    CONTENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    workoutId: Long,
    onBack: () -> Unit,
    onOpenMapsSetup: () -> Unit,
    onViewProgress: () -> Unit,
    onViewPostRunSummary: () -> Unit,
    onDeleteWorkout: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    LaunchedEffect(workoutId) {
        viewModel.loadWorkoutDetail(workoutId)
    }

    val workout by viewModel.selectedWorkout.collectAsState()
    val trackPoints by viewModel.trackPoints.collectAsState()
    val metrics by viewModel.selectedMetrics.collectAsState()
    val isLoading by viewModel.isDetailLoading.collectAsState()
    val errorMessage by viewModel.detailError.collectAsState()
    val isMapsEnabled by viewModel.isMapsEnabled.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = workout?.startTime?.let(::formatWorkoutDate) ?: "Workout Detail"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        val contentState = when {
            isLoading -> HistoryDetailContentState.LOADING
            workout == null -> HistoryDetailContentState.EMPTY
            else -> HistoryDetailContentState.CONTENT
        }

        Crossfade(targetState = contentState, label = "history-detail-content") { state ->
            when (state) {
                HistoryDetailContentState.LOADING -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                HistoryDetailContentState.EMPTY -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = errorMessage ?: "Workout not found.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                HistoryDetailContentState.CONTENT -> {
                    val targetConfig = remember(workout?.targetConfig) {
                        workout?.targetConfig?.let(::parseWorkoutConfig)
                    }
                    val targetSummary = remember(targetConfig) {
                        parseTargetSummary(targetConfig)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (trackPoints.size >= 2 && isMapsEnabled) {
                                HrHeatmapRouteMap(
                                    trackPoints = trackPoints,
                                    workoutConfig = targetConfig
                                )
                            } else if (trackPoints.size >= 2) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Map unavailable. Add your Google Maps API key in Setup > App Settings.",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        OutlinedButton(onClick = onOpenMapsSetup) {
                                            Text(stringResource(R.string.button_open_maps_setup))
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No route data available.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                        StatsCard(
                            workout = workout!!,
                            trackPoints = trackPoints,
                            avgHr = metrics?.avgHr,
                            targetSummary = targetSummary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onViewPostRunSummary,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.button_post_run_insights))
                            }
                            TextButton(
                                onClick = onViewProgress,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.button_view_progress))
                            }
                        }
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(stringResource(R.string.button_delete_run))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_run_title)) },
            text = { Text(stringResource(R.string.dialog_delete_run_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteWorkout(workoutId)
                        onDeleteWorkout()
                    }
                ) {
                    Text(stringResource(R.string.button_delete_run))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun HrHeatmapRouteMap(
    trackPoints: List<TrackPointEntity>,
    workoutConfig: WorkoutConfig?
) {
    val cameraPositionState = rememberCameraPositionState()
    var isMapLoaded by remember { mutableStateOf(false) }
    val inZoneColor = MaterialTheme.colorScheme.tertiary
    val warningColor = MaterialTheme.colorScheme.secondary
    val highColor = MaterialTheme.colorScheme.error

    LaunchedEffect(isMapLoaded, trackPoints) {
        if (!isMapLoaded || trackPoints.isEmpty()) return@LaunchedEffect
        runCatching {
            val boundsBuilder = LatLngBounds.builder()
            trackPoints.forEach { point ->
                boundsBuilder.include(LatLng(point.latitude, point.longitude))
            }
            val bounds = boundsBuilder.build()
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 64))
        }.onFailure {
            val first = trackPoints.first()
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(first.latitude, first.longitude),
                    15f
                )
            )
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        onMapLoaded = { isMapLoaded = true },
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false
        )
    ) {
        for (i in 0 until trackPoints.lastIndex) {
            val p1 = trackPoints[i]
            val p2 = trackPoints[i + 1]
            Polyline(
                points = listOf(
                    LatLng(p1.latitude, p1.longitude),
                    LatLng(p2.latitude, p2.longitude)
                ),
                color = hrToColor(
                    hr = p2.heartRate,
                    distanceMeters = p2.distanceMeters,
                    config = workoutConfig,
                    inZoneColor = inZoneColor,
                    warningColor = warningColor,
                    highColor = highColor
                ),
                width = 8f
            )
        }

        trackPoints.firstOrNull()?.let { start ->
            Marker(
                state = MarkerState(position = LatLng(start.latitude, start.longitude)),
                title = "Start"
            )
        }
        trackPoints.lastOrNull()?.let { end ->
            Marker(
                state = MarkerState(position = LatLng(end.latitude, end.longitude)),
                title = "Finish"
            )
        }
    }
}

@Composable
private fun StatsCard(
    workout: WorkoutEntity,
    trackPoints: List<TrackPointEntity>,
    avgHr: Float?,
    targetSummary: String?,
    modifier: Modifier = Modifier
) {
    val duration = formatDuration(workout.startTime, workout.endTime)
    val fallbackAvg = trackPoints
        .map { it.heartRate }
        .filter { it > 0 }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toInt()
    val avgHrValue = avgHr?.toInt() ?: fallbackAvg ?: 0

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = String.format("%.2f km  |  %s", workout.totalDistanceMeters / 1000f, duration),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Avg HR: ${if (avgHrValue > 0) "$avgHrValue bpm" else "--"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Points: ${trackPoints.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (targetSummary != null) {
                Text(
                    text = targetSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

private fun parseWorkoutConfig(targetConfigJson: String): WorkoutConfig? {
    return runCatching {
        JsonCodec.gson.fromJson(targetConfigJson, WorkoutConfig::class.java)
    }.getOrNull()
}

private fun parseTargetSummary(config: WorkoutConfig?): String? {
    if (config == null) return null
    return when (config.mode) {
        WorkoutMode.STEADY_STATE ->
            config.steadyStateTargetHr?.let { "Planned target: $it bpm" }
        WorkoutMode.DISTANCE_PROFILE -> {
            val segments = config.segments
            if (segments.isEmpty()) {
                null
            } else {
                val first = segments.first()
                val last = segments.last()
                "Planned profile: ${segments.size} segments (${first.targetHr}-${last.targetHr} bpm)"
            }
        }
    }
}

private fun hrToColor(
    hr: Int,
    distanceMeters: Float,
    config: WorkoutConfig?,
    inZoneColor: Color,
    warningColor: Color,
    highColor: Color
): Color {
    if (config == null) {
        return fallbackHrColor(hr, inZoneColor, warningColor, highColor)
    }
    val target = config.targetHrAtDistance(distanceMeters) ?: return fallbackHrColor(
        hr = hr,
        inZoneColor = inZoneColor,
        warningColor = warningColor,
        highColor = highColor
    )
    val buffer = config.bufferBpm.coerceAtLeast(1)
    val delta = hr - target

    val absDelta = kotlin.math.abs(delta).toFloat()
    return when {
        absDelta <= buffer -> inZoneColor
        absDelta <= buffer * 2f -> lerp(
            inZoneColor,
            warningColor,
            (absDelta - buffer) / buffer.toFloat()
        )
        absDelta <= buffer * 4f -> lerp(
            warningColor,
            highColor,
            (absDelta - (buffer * 2f)) / (buffer * 2f)
        )
        else -> highColor
    }
}

private fun fallbackHrColor(
    hr: Int,
    inZoneColor: Color,
    warningColor: Color,
    highColor: Color
): Color {
    return when {
        hr <= 100 -> inZoneColor
        hr in 101..149 -> lerp(inZoneColor, warningColor, (hr - 100) / 50f)
        hr in 150..199 -> lerp(warningColor, highColor, (hr - 150) / 50f)
        else -> highColor
    }
}
