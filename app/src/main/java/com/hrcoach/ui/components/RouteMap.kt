package com.hrcoach.ui.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.ui.history.hrToColor
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.ZoneAmber
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed

/**
 * Shared HR-heatmap route map. Used by HistoryDetailScreen (full-size) and
 * PostRunSummaryScreen (compact 220dp). Renders polyline segments coloured
 * by each point's HR zone, start/finish markers, and auto-fits bounds.
 *
 * Three display states:
 *   - map (trackPoints.size >= 2 && isMapsEnabled)
 *   - "maps setup missing" empty state (trackPoints exist but no API key)
 *   - "no route data" empty state (fewer than 2 track points)
 */
@Composable
fun RouteMap(
    trackPoints: List<TrackPointEntity>,
    workoutConfig: WorkoutConfig?,
    isMapsEnabled: Boolean,
    onOpenMapsSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(BorderStroke(1.dp, CardeaTheme.colors.glassBorder), RoundedCornerShape(18.dp))
            .background(CardeaTheme.colors.glassHighlight, RoundedCornerShape(18.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(CardeaTheme.colors.bgPrimary)
        ) {
            when {
                trackPoints.size >= 2 && isMapsEnabled ->
                    HrHeatmapMapInternal(trackPoints, workoutConfig)
                trackPoints.size >= 2 ->
                    RouteMapEmpty(
                        title = "Route ready, map setup missing",
                        body = "Add your Google Maps API key to unlock route replay.",
                        actionLabel = "Open setup",
                        onAction = onOpenMapsSetup
                    )
                else ->
                    RouteMapEmpty(
                        title = "No route data",
                        body = "Not enough track points to draw the route."
                    )
            }
        }
    }
}

@Composable
private fun HrHeatmapMapInternal(
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
        }.onFailure { e ->
            Log.w("RouteMap", "Bounds calc failed, centering on first point", e)
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
        properties = MapProperties(mapStyleOptions = mapStyle, mapType = MapType.NORMAL)
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

@Composable
private fun RouteMapEmpty(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = CardeaTheme.colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = CardeaTheme.colors.textPrimary,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, CardeaTheme.colors.glassBorder, RoundedCornerShape(8.dp))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}
