package com.hrcoach.ui.history

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
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
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.ZoneAmber
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed
import com.hrcoach.util.JsonCodec
import com.hrcoach.util.asModeLabel
import com.hrcoach.util.durationMinutes
import com.hrcoach.util.formatDuration
import com.hrcoach.util.formatPaceMinPerKm
import com.hrcoach.util.formatWorkoutDate
import com.hrcoach.util.metersToKm

private enum class HistoryDetailContentState { LOADING, EMPTY, CONTENT }

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
    LaunchedEffect(workoutId) { viewModel.loadWorkoutDetail(workoutId) }

    val workout by viewModel.selectedWorkout.collectAsStateWithLifecycle()
    val trackPoints by viewModel.trackPoints.collectAsStateWithLifecycle()
    val metrics by viewModel.selectedMetrics.collectAsStateWithLifecycle()
    val isLoading by viewModel.isDetailLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.detailError.collectAsStateWithLifecycle()
    val isMapsEnabled by viewModel.isMapsEnabled.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaTheme.colors.bgPrimary)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Inline header ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = CardeaTheme.colors.textSecondary,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onBack)
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = workout?.startTime?.let(::formatWorkoutDate) ?: "Workout",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = CardeaTheme.colors.textPrimary
                )
            }

            // ── Content ──────────────────────────────────────────────────
            val contentState = when {
                isLoading -> HistoryDetailContentState.LOADING
                workout == null -> HistoryDetailContentState.EMPTY
                else -> HistoryDetailContentState.CONTENT
            }

            Crossfade(targetState = contentState, label = "history-detail-content") { state ->
                when (state) {
                    HistoryDetailContentState.LOADING -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GradientRed)
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
                                color = CardeaTheme.colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    HistoryDetailContentState.CONTENT -> {
                        val selectedWorkout = workout ?: return@Crossfade
                        val targetConfig = remember(selectedWorkout.targetConfig) {
                            parseWorkoutConfig(selectedWorkout.targetConfig)
                        }
                        val targetSummary = remember(targetConfig) {
                            parseTargetSummary(targetConfig)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                Spacer(modifier = Modifier.height(8.dp))
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
                    Text(stringResource(R.string.button_delete_run), color = ZoneRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
            containerColor = CardeaTheme.colors.bgSecondary,
            titleContentColor = Color.White,
            textContentColor = CardeaTheme.colors.textSecondary
        )
    }
}

// ── Map card ─────────────────────────────────────────────────────────────────

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
            .border(border = BorderStroke(1.dp, CardeaTheme.colors.glassBorder), shape = RoundedCornerShape(18.dp))
            .background(CardeaTheme.colors.glassHighlight, RoundedCornerShape(18.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
                .background(CardeaTheme.colors.bgPrimary, RoundedCornerShape(17.dp))
        ) {
            when {
                trackPoints.size >= 2 && isMapsEnabled -> {
                    HrHeatmapRouteMap(trackPoints = trackPoints, workoutConfig = workoutConfig)
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = CardeaTheme.colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    border = BorderStroke(1.dp, CardeaTheme.colors.glassBorder)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.MapHeaderOverlay(hasWorkoutTarget: Boolean) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .align(Alignment.TopStart)
            .background(Color(0xCC121212), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (hasWorkoutTarget) "Target heatmap" else "Heart-rate route",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = CardeaTheme.colors.textPrimary
        )
    }
}

@Composable
private fun BoxScope.MapLegendOverlay(hasWorkoutTarget: Boolean) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
            .background(Color(0xCC121212), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasWorkoutTarget) {
            LegendChip("On target", ZoneGreen)
            LegendChip("Caution", ZoneAmber)
            LegendChip("Redline", ZoneRed)
        } else {
            LegendChip("Easy", ZoneGreen)
            LegendChip("Moderate", ZoneAmber)
            LegendChip("High", ZoneRed)
        }
    }
}

@Composable
private fun LegendChip(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(999.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTheme.colors.textPrimary
        )
    }
}

// ── Map route ────────────────────────────────────────────────────────────────

@Composable
private fun HrHeatmapRouteMap(
    trackPoints: List<TrackPointEntity>,
    workoutConfig: WorkoutConfig?
) {
    val context = LocalContext.current
    val mapStyle = remember {
        runCatching {
            MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
        }.getOrNull()
    }
    val cameraPositionState = rememberCameraPositionState()
    var isMapLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(isMapLoaded, trackPoints) {
        if (!isMapLoaded || trackPoints.isEmpty()) return@LaunchedEffect
        runCatching {
            val boundsBuilder = LatLngBounds.builder()
            trackPoints.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
            cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 64))
        }.onFailure {
            val first = trackPoints.first()
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 15f)
            )
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        onMapLoaded = { isMapLoaded = true },
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
        properties = MapProperties(
            mapStyleOptions = mapStyle,
            mapType = MapType.NORMAL
        )
    ) {
        for (i in 0 until trackPoints.lastIndex) {
            val p1 = trackPoints[i]
            val p2 = trackPoints[i + 1]
            Polyline(
                points = listOf(LatLng(p1.latitude, p1.longitude), LatLng(p2.latitude, p2.longitude)),
                color = hrToColor(
                    hr = p2.heartRate,
                    distanceMeters = p2.distanceMeters,
                    config = workoutConfig,
                    inZoneColor = ZoneGreen,
                    warningColor = ZoneAmber,
                    highColor = ZoneRed
                ),
                width = 8f
            )
        }
        trackPoints.firstOrNull()?.let { start ->
            Marker(state = MarkerState(LatLng(start.latitude, start.longitude)), title = "Start")
        }
        trackPoints.lastOrNull()?.let { end ->
            Marker(state = MarkerState(LatLng(end.latitude, end.longitude)), title = "Finish")
        }
    }
}

// ── Stats card ───────────────────────────────────────────────────────────────

@Composable
private fun StatsCard(
    workout: WorkoutEntity,
    trackPoints: List<TrackPointEntity>,
    avgHr: Float?,
    targetSummary: String?,
    modifier: Modifier = Modifier
) {
    val duration = formatDuration(workout.startTime, workout.endTime)
    val fallbackAvg = trackPoints.map { it.heartRate }.filter { it > 0 }
        .takeIf { it.isNotEmpty() }?.average()?.toInt()
    val avgHrValue = avgHr?.toInt() ?: fallbackAvg ?: 0
    val distanceKm = metersToKm(workout.totalDistanceMeters)
    val durationMin = workout.durationMinutes
    val pace = if (distanceKm > 0f && durationMin > 0f) {
        formatPaceMinPerKm(durationMin / distanceKm)
    } else "--"

    GlassCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)) {
        Text(
            text = "Session overview",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = CardeaTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailStatCell("Distance", "%.2f km".format(distanceKm), modifier = Modifier.weight(1f))
            DetailStatCell("Duration", duration, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailStatCell("Avg HR", if (avgHrValue > 0) "$avgHrValue bpm" else "--", modifier = Modifier.weight(1f))
            DetailStatCell("Avg pace", pace, modifier = Modifier.weight(1f))
        }

        if (targetSummary != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardeaTheme.colors.glassHighlight, RoundedCornerShape(10.dp))
                    .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Workout plan",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CardeaTheme.colors.textTertiary
                    )
                    Text(
                        text = targetSummary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = CardeaTheme.colors.textPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailStatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(CardeaTheme.colors.glassHighlight, RoundedCornerShape(12.dp))
            .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = CardeaTheme.colors.textTertiary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = CardeaTheme.colors.textPrimary
            )
        }
    }
}

// ── More actions card ─────────────────────────────────────────────────────────

@Composable
private fun MoreActionsCard(
    onViewPostRunSummary: () -> Unit,
    onViewProgress: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)) {
        Text(
            text = "More actions",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = CardeaTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(14.dp))
        CardeaButton(
            text = stringResource(R.string.button_post_run_insights),
            onClick = onViewPostRunSummary,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onViewProgress,
                modifier = Modifier.weight(1f).height(44.dp),
                border = BorderStroke(1.dp, CardeaTheme.colors.glassBorder)
            ) {
                Text(
                    text = stringResource(R.string.button_view_progress),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Text(
                    text = stringResource(R.string.button_delete_run),
                    style = MaterialTheme.typography.labelLarge,
                    color = ZoneRed
                )
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun parseWorkoutConfig(targetConfigJson: String): WorkoutConfig? =
    runCatching { JsonCodec.gson.fromJson(targetConfigJson, WorkoutConfig::class.java) }.getOrNull()

private fun parseTargetSummary(config: WorkoutConfig?): String? {
    if (config == null) return null
    return when (config.mode) {
        WorkoutMode.STEADY_STATE ->
            config.steadyStateTargetHr?.let { "Target: $it bpm steady state" }
        WorkoutMode.DISTANCE_PROFILE -> {
            val segments = config.segments
            if (segments.isEmpty()) null
            else "Distance profile · ${segments.size} segments (${segments.first().targetHr}–${segments.last().targetHr} bpm)"
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
    if (config == null) return fallbackHrColor(hr, inZoneColor, warningColor, highColor)
    val target = config.targetHrAtDistance(distanceMeters)
        ?: return fallbackHrColor(hr, inZoneColor, warningColor, highColor)
    val buffer = config.bufferBpm.coerceAtLeast(1)
    val absDelta = kotlin.math.abs(hr - target).toFloat()
    return when {
        absDelta <= buffer -> inZoneColor
        absDelta <= buffer * 2f -> lerp(inZoneColor, warningColor, (absDelta - buffer) / buffer.toFloat())
        absDelta <= buffer * 4f -> lerp(warningColor, highColor, (absDelta - buffer * 2f) / (buffer * 2f))
        else -> highColor
    }
}

private fun fallbackHrColor(hr: Int, inZoneColor: Color, warningColor: Color, highColor: Color): Color =
    when {
        hr <= 100 -> inZoneColor
        hr in 101..149 -> lerp(inZoneColor, warningColor, (hr - 100) / 50f)
        hr in 150..199 -> lerp(warningColor, highColor, (hr - 150) / 50f)
        else -> highColor
    }
