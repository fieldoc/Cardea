# Workout UI Redesign — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Redesign `ActiveWorkoutScreen` with a hero HR pulsing ring, connect-in-ring disconnected state, Cardea-consistent buttons, and fix `SetupScreen` wording/styling.

**Architecture:** Six self-contained tasks: (1) data model, (2) new `HrRing` composable, (3) full `ActiveWorkoutScreen` restructure, (4) service BLE rescan action + NavGraph wiring, (5) `SetupScreen` polish. Each task ends with a build + commit.

**Tech Stack:** Kotlin, Jetpack Compose, Canvas DrawScope, Hilt, Room. Cardea design tokens from `ui/theme/Color.kt`. Build: `./gradlew.bat assembleDebug`.

---

## Task 1: Add `avgHr` to WorkoutSnapshot and service

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutState.kt`
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

### Step 1: Add the field to WorkoutSnapshot

In `WorkoutState.kt`, add `avgHr: Int = 0` to the data class. Place it after `adaptiveLagSec`:

```kotlin
data class WorkoutSnapshot(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentHr: Int = 0,
    val targetHr: Int = 0,
    val zoneStatus: ZoneStatus = ZoneStatus.NO_DATA,
    val distanceMeters: Float = 0f,
    val hrConnected: Boolean = false,
    val paceMinPerKm: Float = 0f,
    val predictedHr: Int = 0,
    val guidanceText: String = "GET HR SIGNAL",
    val adaptiveLagSec: Float = 0f,
    val projectionReady: Boolean = false,
    val completedWorkoutId: Long? = null,
    val isFreeRun: Boolean = false,
    val avgHr: Int = 0          // NEW — running average HR for the session
)
```

### Step 2: Add tracking fields to WorkoutForegroundService

In `WorkoutForegroundService.kt`, add two private fields alongside the other instance variables (near `latestTick` or `observationJob`):

```kotlin
private var hrSampleSum: Long = 0L
private var hrSampleCount: Int = 0
```

### Step 3: Accumulate HR in processTick

In `processTick`, after computing `zoneStatus` and before the `WorkoutState.update` block, add:

```kotlin
if (tick.connected && tick.hr > 0 && !isPaused) {
    hrSampleSum += tick.hr
    hrSampleCount++
}
val sessionAvgHr = if (hrSampleCount > 0) (hrSampleSum / hrSampleCount).toInt() else 0
```

Then add `avgHr = sessionAvgHr` to the `WorkoutState.update { current -> current.copy(...) }` call.

The paused branch (`if (isPaused)` block) should preserve the existing `avgHr` by NOT resetting it — just don't include `avgHr` in the paused copy, so it keeps whatever value was last set. Add `avgHr = WorkoutState.snapshot.value.avgHr` to the paused copy block.

### Step 4: Reset counters in stopWorkout

Find the `stopWorkout()` private function. Add resets right at the start:

```kotlin
private fun stopWorkout() {
    hrSampleSum = 0L
    hrSampleCount = 0
    // ... existing stop logic
}
```

### Step 5: Build

```bash
./gradlew.bat assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL. No changes to UI yet.

### Step 6: Commit

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutState.kt \
        app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(service): add avgHr running average to WorkoutSnapshot"
```

---

## Task 2: Create HrRing composable

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/workout/HrRing.kt`

### Step 1: Create the file

```kotlin
package com.hrcoach.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.HrCoachThemeTokens

@Composable
fun HrRing(
    hr: Int,
    isConnected: Boolean,
    zoneColor: Color,
    pulseScale: Float,
    onConnectHr: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(160.dp)
            .scale(if (isConnected) pulseScale else 1f)
            .then(
                if (!isConnected) Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onConnectHr)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val strokePx = 8.dp.toPx()
            val radius = size.minDimension / 2f - strokePx / 2f
            if (isConnected) {
                drawCircle(
                    color = zoneColor.copy(alpha = 0.12f),
                    radius = radius,
                    style = Stroke(width = strokePx)
                )
                drawArc(
                    brush = CardeaGradient,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            } else {
                drawCircle(
                    color = CardeaTextTertiary,
                    radius = radius,
                    style = Stroke(width = strokePx)
                )
            }
        }

        if (isConnected) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (hr > 0) hr.toString() else "---",
                    style = MaterialTheme.typography.displayLarge,
                    color = CardeaTextPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "bpm",
                    style = MaterialTheme.typography.labelSmall,
                    color = HrCoachThemeTokens.subtleText
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(CardeaGradient)
                )
                Text(
                    text = "Connect HR",
                    style = MaterialTheme.typography.titleSmall,
                    color = CardeaTextPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "monitor",
                    style = MaterialTheme.typography.labelSmall,
                    color = CardeaTextSecondary
                )
            }
        }
    }
}
```

### Step 2: Build

```bash
./gradlew.bat assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL (HrRing not yet used anywhere).

### Step 3: Commit

```bash
git add app/src/main/java/com/hrcoach/ui/workout/HrRing.kt
git commit -m "feat(workout): add HrRing canvas composable with connected/disconnected states"
```

---

## Task 3: Restructure ActiveWorkoutScreen

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt`

This is the large task. Replace the entire file content. The key structural changes:
- Remove `Scaffold` and `TopAppBar`; use `Box` with a manual top `Row`
- Replace `AnimatedContent` HR number with `HrRing`
- Remove `OutlinedCard` disconnected warning
- Fix `ZoneStatusPill`: add FREE RUN label, fix clipping
- Fix `zoneColorFor`: use explicit Cardea color constants
- Fix `InlineMetric` Target: show `"—"` for free run
- Replace `LinearProgressIndicator` with Canvas gradient bar
- Replace stats "Lag" with "Avg HR"
- Style Pause/Stop buttons as glass composables
- Add `onConnectHr` parameter

### Step 1: Write the new file

```kotlin
package com.hrcoach.ui.workout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.Canvas
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
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GlassHighlight
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
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
            // Top bar
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
                        brush = Brush.linearGradient(
                            colors = listOf(GradientRed, GradientPink, GradientBlue, GradientCyan)
                        )
                    )
                )
                Text(
                    text = formatElapsedHms(uiState.elapsedSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = HrCoachThemeTokens.subtleText
                )
            }

            // Distance profile progress bar
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

        // Control buttons overlay at bottom
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

// ── Private composables ───────────────────────────────────────────────────────

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
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
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
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}
```

### Step 2: Build

```bash
./gradlew.bat assembleDebug 2>&1 | tail -30
```
Expected: BUILD SUCCESSFUL.

If there are unresolved reference errors, check import paths — package names for `GlassHighlight`, `ZoneGreen`, `ZoneAmber`, `ZoneRed`, `GradientRed`, `GradientPink`, `GradientBlue`, `GradientCyan` are all in `com.hrcoach.ui.theme`.

### Step 3: Commit

```bash
git add app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt
git commit -m "feat(workout): hero HR ring layout, Cardea buttons, fix zone pill and stats"
```

---

## Task 4: Add ACTION_RESCAN_BLE to service + wire onConnectHr in NavGraph

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`

### Step 1: Add the action constant

In `WorkoutForegroundService.kt`, in the `companion object`, add:

```kotlin
const val ACTION_RESCAN_BLE = "com.hrcoach.ACTION_RESCAN_BLE"
```

### Step 2: Handle the new action in onStartCommand

In `onStartCommand`, add a new branch to the `when` block:

```kotlin
ACTION_RESCAN_BLE -> {
    bleCoordinator.startScan()
    return START_NOT_STICKY
}
```

### Step 3: Wire onConnectHr in NavGraph

In `NavGraph.kt`, find the `ActiveWorkoutScreen(...)` composable call. Add the new `onConnectHr` parameter:

```kotlin
ActiveWorkoutScreen(
    onPauseResume = { /* existing */ },
    onStopConfirmed = { /* existing */ },
    onConnectHr = {
        val intent = Intent(context, WorkoutForegroundService::class.java).apply {
            action = WorkoutForegroundService.ACTION_RESCAN_BLE
        }
        context.startService(intent)
    }
)
```

### Step 4: Build

```bash
./gradlew.bat assembleDebug 2>&1 | tail -20
```

### Step 5: Commit

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt \
        app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt
git commit -m "feat(service+nav): add ACTION_RESCAN_BLE and wire onConnectHr from workout screen"
```

---

## Task 5: SetupScreen polish

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`

### Step 1: Style the scan button as gradient CTA

Find `HrMonitorCard` (around line 535). In the `else` branch (not connected), replace:

```kotlin
Button(
    onClick = onStartScan,
    modifier = Modifier.fillMaxWidth(),
    enabled = !state.isScanning
) {
    Icon(imageVector = Icons.Default.Search, contentDescription = null)
    Spacer(modifier = Modifier.width(8.dp))
    Text(if (state.isScanning) "Scanning..." else "Scan for Devices")
}
```

With a gradient-styled `Box`:

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .clip(RoundedCornerShape(14.dp))
        .then(
            if (!state.isScanning)
                Modifier.background(CardeaGradient)
            else
                Modifier.background(CardeaTextTertiary)
        )
        .clickable(enabled = !state.isScanning, onClick = onStartScan),
    contentAlignment = Alignment.Center
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = CardeaTextPrimary
        )
        Text(
            text = if (state.isScanning) "Scanning..." else "Scan for Devices",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = CardeaTextPrimary
        )
    }
}
```

Make sure `CardeaGradient`, `CardeaTextTertiary`, `CardeaTextPrimary` are imported from `com.hrcoach.ui.theme`.

### Step 2: Consolidate hint text

Still in `HrMonitorCard`'s `else` branch, replace the two `Text` lines:

```kotlin
// REMOVE:
Text(
    text = "COOSPO H808S usually appears as \"H808...\" or \"COOSPO...\".",
    ...
)
Text(
    text = "You can still start without a live connection; scanning continues during the run.",
    ...
)

// REPLACE WITH:
Text(
    text = "No signal? You can still start — scanning continues during the run.",
    style = MaterialTheme.typography.bodySmall,
    color = HrCoachThemeTokens.subtleText
)
```

### Step 3: Fix collapsed audio hint

Find line ~397 and replace:

```kotlin
// REMOVE:
text = "Buffer, timing, voice, and vibration options are tucked here.",

// REPLACE WITH:
text = "Audio alerts & timing options",
```

### Step 4: Build

```bash
./gradlew.bat assembleDebug 2>&1 | tail -20
```

### Step 5: Commit

```bash
git add app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt
git commit -m "feat(setup): gradient scan button, consolidate hint text, fix audio label"
```

---

## Task 6: Final build + verify

### Step 1: Clean build

```bash
./gradlew.bat clean assembleDebug 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL, no warnings about unresolved references.

### Step 2: Check for stale imports

```bash
./gradlew.bat lint 2>&1 | grep -E "Error|Warning" | head -20
```

### Step 3: Commit if any lint fixes were needed

If no issues, no extra commit needed. If lint flagged unused imports from the old `ActiveWorkoutScreen` (e.g. `LinearProgressIndicator`, `OutlinedCard`, `Scaffold`, `TopAppBar`), they were already removed in Task 3.

---

## Notes for implementer

- `GlassHighlight = Color(0x14FFFFFF)` — use this as the progress bar track (slightly more visible than `GlassBorder`)
- The `Brush.linearGradient` on `Text` uses `TextStyle.brush` parameter — available since Compose UI 1.3+
- `statusBarsPadding()` handles edge-to-edge display; if the app doesn't use `enableEdgeToEdge()`, this modifier is a no-op so it's safe either way
- In `HrRing`, the `Canvas drawArc` with `brush = CardeaGradient` applies the gradient across the arc's bounding box — direction is horizontal (left to right) as defined by `CardeaGradient`'s default `linearGradient` offsets
- The `avgHr` in the paused state: the paused branch in `processTick` does an early return. Add `avgHr = WorkoutState.snapshot.value.avgHr` to preserve the last computed average during pause
