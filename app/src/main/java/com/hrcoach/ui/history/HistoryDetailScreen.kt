package com.hrcoach.ui.history

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.hrcoach.R
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

private val DetailBackdrop = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF061019),
        Color(0xFF0D1623),
        Color(0xFF162739)
    )
)

private val DetailGlass = Color(0xCC0D1824)

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
        containerColor = Color(0xFF061019),
        topBar = {
            TopAppBar(
                title = {
                    Text(workout?.startTime?.let(::formatWorkoutDate) ?: "Workout detail")
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
                    containerColor = Color(0xFF061019),
                    titleContentColor = Color(0xFFF4F7FB),
                    navigationIconContentColor = Color(0xFFF4F7FB)
                )
            )
        }
    ) { paddingValues ->
        val contentState = when {
            isLoading -> HistoryDetailContentState.LOADING
            workout == null -> HistoryDetailContentState.EMPTY
            else -> HistoryDetailContentState.CONTENT
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DetailBackdrop)
                .padding(paddingValues)
        ) {
            Crossfade(targetState = contentState, label = "history-detail-content") { state ->
                when (state) {
                    HistoryDetailContentState.LOADING -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    HistoryDetailContentState.EMPTY -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage ?: "Workout not found.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFB6C2D1),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    HistoryDetailContentState.CONTENT -> {
                        val selectedWorkout = workout ?: return@Crossfade
                        val targetConfig = remember(selectedWorkout.targetConfig) {
                            selectedWorkout.targetConfig.let(::parseWorkoutConfig)
                        }
                        val targetSummary = remember(targetConfig) {
                            parseTargetSummary(targetConfig)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            DetailMapCard(
                                trackPoints = trackPoints,
                                workoutConfig = targetConfig,
                                isMapsEnabled = isMapsEnabled,
                                onOpenMapsSetup = onOpenMapsSetup,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatsCard(
                                    workout = selectedWorkout,
                                    trackPoints = trackPoints,
                                    avgHr = metrics?.avgHr,
                                    targetSummary = targetSummary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                MoreActionsCard(
                                    onViewPostRunSummary = onViewPostRunSummary,
                                    onViewProgress = onViewProgress,
                                    onDelete = { showDeleteDialog = true }
                                )
                            }
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
private fun DetailMapCard(
    trackPoints: List<TrackPointEntity>,
    workoutConfig: WorkoutConfig?,
    isMapsEnabled: Boolean,
    onOpenMapsSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(32.dp)
            )
            .background(DetailGlass, RoundedCornerShape(32.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
                .background(Color(0xFF08111A), RoundedCornerShape(31.dp))
        ) {
            when {
                trackPoints.size >= 2 && isMapsEnabled -> {
                    HrHeatmapRouteMap(
                        trackPoints = trackPoints,
                        workoutConfig = workoutConfig
                    )
                    MapHeaderOverlay(hasWorkoutTarget = workoutConfig != null)
                    MapLegendOverlay(hasWorkoutTarget = workoutConfig != null)
                }

                trackPoints.size >= 2 -> {
                    EmptyMapState(
                        title = "Route ready, map setup missing",
                        body = "Add your Google Maps API key in Setup > App Settings to unlock the route replay overlay.",
                        actionLabel = stringResource(R.string.button_open_maps_setup),
                        onAction = onOpenMapsSetup
                    )
                }

                else -> {
                    EmptyMapState(
                        title = "No route data captured",
                        body = "This workout does not include enough track points to draw a route or heart-rate heatmap."
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMapState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFF4F7FB),
                textAlign = TextAlign.Center
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB6C2D1),
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.MapHeaderOverlay(hasWorkoutTarget: Boolean) {
    Surface(
        modifier = Modifier
            .padding(16.dp)
            .align(Alignment.TopStart),
        shape = RoundedCornerShape(20.dp),
        color = DetailGlass,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = if (hasWorkoutTarget) "Target heatmap" else "Heart-rate route",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFF4F7FB),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (hasWorkoutTarget) {
                    "Green is on target. Warm colors show drift above the plan."
                } else {
                    "Colors move from lower effort to higher effort across the route."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB6C2D1)
            )
        }
    }
}

@Composable
private fun BoxScope.MapLegendOverlay(hasWorkoutTarget: Boolean) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(16.dp),
        shape = RoundedCornerShape(22.dp),
        color = DetailGlass,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (hasWorkoutTarget) {
                LegendChip("On target", MaterialTheme.colorScheme.tertiary)
                LegendChip("Caution", MaterialTheme.colorScheme.secondary)
                LegendChip("Redline", MaterialTheme.colorScheme.error)
            } else {
                LegendChip("Easy", MaterialTheme.colorScheme.tertiary)
                LegendChip("Moderate", MaterialTheme.colorScheme.secondary)
                LegendChip("High", MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun LegendChip(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            modifier = Modifier.size(10.dp),
            shape = RoundedCornerShape(999.dp),
            color = color
        ) {}
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFF4F7FB)
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
    val statItems = listOf(
        "Distance" to String.format("%.2f km", workout.totalDistanceMeters / 1000f),
        "Duration" to duration,
        "Avg HR" to if (avgHrValue > 0) "$avgHrValue bpm" else "--",
        "Track points" to trackPoints.size.toString()
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = DetailGlass)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Session overview",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFF4F7FB),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "The stats grid summarizes what this run captured, and the route heatmap shows where effort drifted across the course.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB6C2D1)
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailStatCell(
                        label = statItems[0].first,
                        value = statItems[0].second,
                        modifier = Modifier.weight(1f)
                    )
                    DetailStatCell(
                        label = statItems[1].first,
                        value = statItems[1].second,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailStatCell(
                        label = statItems[2].first,
                        value = statItems[2].second,
                        modifier = Modifier.weight(1f)
                    )
                    DetailStatCell(
                        label = statItems[3].first,
                        value = statItems[3].second,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.04f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Workout plan",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF8FA4B7)
                    )
                    Text(
                        text = targetSummary ?: workout.mode.asModeLabel(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFF4F7FB),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Glass overlays keep the target context and map legend visible without burying the route.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9DB0C2)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8EA4B8)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFF4F7FB),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MoreActionsCard(
    onViewPostRunSummary: () -> Unit,
    onViewProgress: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(30.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        colors = CardDefaults.cardColors(containerColor = DetailGlass)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "More actions",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFF4F7FB),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Open the post-run breakdown, jump into long-term progress, or remove the workout from history.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB6C2D1)
            )
            Button(
                onClick = onViewPostRunSummary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.button_post_run_insights))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onViewProgress,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.button_view_progress))
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.button_delete_run))
                }
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

        WorkoutMode.FREE_RUN -> "Free run"
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

private fun String.asModeLabel(): String =
    lowercase()
        .split('_')
        .joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
