# PostRun Inline Route Map Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the nav-hop overlap between PostRun and HistoryDetail by surfacing a compact, tappable route-map card directly on PostRun. Closes the loop so the runner sees "how did I do" and "where did I go" in one screen, and HistoryDetail becomes the true deep-dive instead of a parallel summary.

**Architecture:** Extract the currently-private `HrHeatmapRouteMap` composable from `HistoryDetailScreen.kt:436` into a shared `ui/components/RouteMap.kt`. Add `trackPoints` + `workoutConfig` + `isMapsEnabled` state to `PostRunSummaryUiState` and load them in the ViewModel. Render a fixed-height (220 dp) `RouteMap` card in the "Your Run" section of PostRun. The card is tappable → navigates to HistoryDetail's full-screen map.

**Tech Stack:** Kotlin, Jetpack Compose, `com.google.maps.android.compose` (already a dependency), existing `TrackPointEntity`, `MapsSettingsRepository`, `JsonCodec`.

---

## File Structure

- Create: `app/src/main/java/com/hrcoach/ui/components/RouteMap.kt` — shared map + empty-state composables.
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt` — delegate to shared `RouteMap` instead of private composable.
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt` — load track points + maps-enabled flag.
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt` — render the card, wire `onOpenFullMap` to NavGraph.
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt` — pass a `onOpenFullMap` callback into the PostRun screen.
- Create: `app/src/test/java/com/hrcoach/ui/postrun/PostRunSummaryViewModelMapTest.kt` — verifies track-point load path.

---

## Task 1: Extract `RouteMap` into a shared component

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/components/RouteMap.kt`

- [ ] **Step 1.1: Create the shared composable by copy-adapting from HistoryDetailScreen**

```kotlin
package com.hrcoach.ui.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.hrcoach.ui.history.hrToColor  // made internal in Task 2
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
                )
            }
        }
    }
}
```

- [ ] **Step 1.2: Verify `hrToColor` visibility**

Open `app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt` and find the `hrToColor` function. If it's `private`, change to `internal`:

```kotlin
internal fun hrToColor(
    hr: Int,
    distanceMeters: Float,
    config: WorkoutConfig?,
    ...
```

If `hrToColor` is defined inside a class or has no modifier, move it to top-level `internal`. Confirm the file and function signature via grep before editing.

- [ ] **Step 1.3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 1.4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/RouteMap.kt \
        app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt
git commit -m "refactor(ui): extract shared RouteMap composable"
```

---

## Task 2: Replace `DetailMapCard` to use the new `RouteMap`

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt`

- [ ] **Step 2.1: Replace the body of `DetailMapCard`**

At `HistoryDetailScreen.kt:294`, replace the entire `DetailMapCard` composable with a thin wrapper:

```kotlin
@Composable
private fun DetailMapCard(
    trackPoints: List<TrackPointEntity>,
    workoutConfig: WorkoutConfig?,
    isMapsEnabled: Boolean,
    onOpenMapsSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        com.hrcoach.ui.components.RouteMap(
            trackPoints = trackPoints,
            workoutConfig = workoutConfig,
            isMapsEnabled = isMapsEnabled,
            onOpenMapsSetup = onOpenMapsSetup,
            modifier = Modifier.fillMaxSize()
        )
        // Keep the header + legend overlays that HistoryDetail uniquely uses.
        MapHeaderOverlay(hasWorkoutTarget = workoutConfig != null)
        MapLegendOverlay(hasWorkoutTarget = workoutConfig != null)
    }
}
```

- [ ] **Step 2.2: Delete the now-unused private `HrHeatmapRouteMap` and `EmptyMapState` in `HistoryDetailScreen.kt`**

Remove:
- `private fun HrHeatmapRouteMap(...)` (line ~436)
- `private fun EmptyMapState(...)` (line ~339)

Overlays (`MapHeaderOverlay`, `MapLegendOverlay`) stay — they're HistoryDetail-specific.

- [ ] **Step 2.3: Verify compilation and run the app screen once**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Install and briefly check HistoryDetail renders the map exactly as before:

```bash
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open any past workout → HistoryDetail → map visible and correctly bounded.

- [ ] **Step 2.4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt
git commit -m "refactor(history): delegate DetailMapCard to shared RouteMap"
```

---

## Task 3: Extend ViewModel state with map data (TDD)

**Files:**
- Create: `app/src/test/java/com/hrcoach/ui/postrun/PostRunSummaryViewModelMapTest.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt`

- [ ] **Step 3.1: Write the failing test**

Note — full VM test scaffold is extensive; if the project lacks an existing `PostRunSummaryViewModelTest`, skip this task's tests and rely on the compile-time type check + device verification. Search first:

```bash
find app/src/test -name "PostRunSummaryViewModelTest*"
```

If such tests exist, follow their fake-repository pattern. If not, write just a minimal shape test:

```kotlin
package com.hrcoach.ui.postrun

import com.hrcoach.data.db.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostRunSummaryUiStateShapeTest {
    @Test
    fun `default ui state has empty track points and maps disabled`() {
        val state = PostRunSummaryUiState()
        assertTrue(state.trackPoints.isEmpty())
        assertEquals(false, state.isMapsEnabled)
    }
}
```

- [ ] **Step 3.2: Run test — verify FAIL**

Run: `./gradlew testDebugUnitTest --tests "com.hrcoach.ui.postrun.PostRunSummaryUiStateShapeTest"`
Expected: FAIL — `trackPoints` and `isMapsEnabled` unresolved.

- [ ] **Step 3.3: Add fields to `PostRunSummaryUiState`**

In `PostRunSummaryViewModel.kt`, around line 44, update:

```kotlin
data class PostRunSummaryUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val titleText: String = "Post-run Summary",
    val distanceText: String = "--",
    val durationText: String = "--",
    val avgHrText: String = "--",
    val similarRunCount: Int = 0,
    val comparisons: List<PostRunComparison> = emptyList(),
    val bootcampProgressLabel: String? = null,
    val bootcampWeekComplete: Boolean = false,
    val isBootcampRun: Boolean = false,
    val workoutEndTimeMs: Long = 0L,
    val newAchievements: List<AchievementEntity> = emptyList(),
    val hrMaxDelta: Pair<Int, Int>? = null,
    val cueCounts: Map<com.hrcoach.domain.model.CoachingEvent, Int> = emptyMap(),
    val showSoundsRecap: Boolean = false,
    // ── New fields for inline route map ──
    val trackPoints: List<com.hrcoach.data.db.TrackPointEntity> = emptyList(),
    val workoutConfig: com.hrcoach.domain.model.WorkoutConfig? = null,
    val isMapsEnabled: Boolean = false,
)
```

- [ ] **Step 3.4: Inject `MapsSettingsRepository` into the VM**

Update the constructor:

```kotlin
@HiltViewModel
class PostRunSummaryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val workoutMetricsRepository: WorkoutMetricsRepository,
    private val bootcampSessionCompleter: BootcampSessionCompleter,
    private val achievementEvaluator: AchievementEvaluator,
    private val achievementDao: AchievementDao,
    private val adaptiveProfileRepository: AdaptiveProfileRepository,
    private val userProfileRepository: UserProfileRepository,
    private val mapsSettingsRepository: com.hrcoach.data.repository.MapsSettingsRepository
) : ViewModel() {
```

- [ ] **Step 3.5: Load track points + config + maps flag in the summary block**

In `load()`, inside the summary `runCatching` block at `PostRunSummaryViewModel.kt:105`, extend the _uiState.value assignment. Find:

```kotlin
val avgHr = currentMetrics?.avgHr ?: workoutRepository.getTrackPoints(workout.id)
    .map { it.heartRate }
    .takeIf { it.isNotEmpty() }
    ?.average()
    ?.toFloat()
```

Replace with:

```kotlin
// Load track points ONCE — shared for avgHr fallback and for map rendering.
val allTrackPoints = workoutRepository.getTrackPoints(workout.id)
val avgHr = currentMetrics?.avgHr ?: allTrackPoints
    .map { it.heartRate }
    .takeIf { it.isNotEmpty() }
    ?.average()
    ?.toFloat()
val isMapsEnabled = runCatching { mapsSettingsRepository.isEnabled() }.getOrDefault(false)
val parsedConfig = runCatching {
    com.hrcoach.util.JsonCodec.gson.fromJson(
        workout.targetConfig,
        com.hrcoach.domain.model.WorkoutConfig::class.java
    )
}.getOrNull()
```

Then in the `_uiState.value = PostRunSummaryUiState(...)` assignment, add three fields:

```kotlin
_uiState.value = PostRunSummaryUiState(
    isLoading = false,
    errorMessage = null,
    titleText = formatWorkoutDate(workout.startTime),
    distanceText = run { /* unchanged */ ... },
    durationText = formatDurationSeconds(activeDurationSecondsOf(workout)),
    avgHrText = avgHr?.let { "${it.toInt()} bpm" } ?: "--",
    similarRunCount = similar.size,
    comparisons = comparisons,
    workoutEndTimeMs = workout.endTime,
    cueCounts = cueCounts,
    showSoundsRecap = showSoundsRecap,
    trackPoints = allTrackPoints,
    workoutConfig = parsedConfig,
    isMapsEnabled = isMapsEnabled,
)
```

- [ ] **Step 3.6: Verify `MapsSettingsRepository.isEnabled()` exists**

Grep:
```bash
grep -n "fun isEnabled" app/src/main/java/com/hrcoach/data/repository/MapsSettingsRepository.kt
```

If the actual method name is different (e.g. `getApiKey()?.isNotBlank() == true`), substitute accordingly.

- [ ] **Step 3.7: Run test — verify PASS + run compile**

Run:
```bash
./gradlew testDebugUnitTest --tests "com.hrcoach.ui.postrun.PostRunSummaryUiStateShapeTest"
./gradlew :app:compileDebugKotlin
```
Expected: Test PASS, compile SUCCESSFUL.

- [ ] **Step 3.8: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryViewModel.kt \
        app/src/test/java/com/hrcoach/ui/postrun/PostRunSummaryUiStateShapeTest.kt
git commit -m "feat(postrun): load track points + maps flag into ui state"
```

---

## Task 4: Render the compact map card on PostRun

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`

- [ ] **Step 4.1: Add `onOpenFullMap` and `onOpenMapsSetup` callbacks to the screen**

In `PostRunSummaryScreen.kt`, update the signature:

```kotlin
fun PostRunSummaryScreen(
    workoutId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onNavigateToSoundLibrary: () -> Unit = {},
    onOpenFullMap: () -> Unit,
    onOpenMapsSetup: () -> Unit,
    viewModel: PostRunSummaryViewModel = hiltViewModel()
)
```

- [ ] **Step 4.2: Render the card inside the "Your Run" section**

This plan assumes Plans 2 and 4 are applied (pinned Done + section grouping). Inside the `Your Run` section, after the 2-up stat row, add:

```kotlin
// ── Route map preview — tappable, opens HistoryDetail for deep-dive ──
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(220.dp)
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onOpenFullMap)
) {
    com.hrcoach.ui.components.RouteMap(
        trackPoints = uiState.trackPoints,
        workoutConfig = uiState.workoutConfig,
        isMapsEnabled = uiState.isMapsEnabled,
        onOpenMapsSetup = onOpenMapsSetup,
        modifier = Modifier.fillMaxSize()
    )
}
```

Add imports at the top:
```kotlin
import androidx.compose.foundation.clickable
```

- [ ] **Step 4.3: Wire the new callbacks in NavGraph**

In `NavGraph.kt` at the `PostRunSummaryScreen(...)` composable (around line 646), add:

```kotlin
PostRunSummaryScreen(
    workoutId = workoutId,
    onDone = { /* unchanged */ },
    onBack = { /* unchanged */ },
    onNavigateToSoundLibrary = {
        navController.navigate(Routes.SOUND_LIBRARY) { launchSingleTop = true }
    },
    onOpenFullMap = {
        navController.navigate(Routes.historyDetail(workoutId)) {
            popUpTo(Routes.HISTORY) { inclusive = false }
            launchSingleTop = true
        }
    },
    onOpenMapsSetup = {
        navController.navigate(Routes.ACCOUNT) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
        }
    },
    viewModel = postRunViewModel
)
```

- [ ] **Step 4.4: Remove `MoreActionsCard.onViewPostRunSummary` button from HistoryDetail**

PostRun and HistoryDetail no longer loop. Delete the `OutlinedButton(onClick = onViewPostRunSummary, ...)` at `HistoryDetailScreen.kt:608-620` and its enclosing Row (if that leaves only one button, collapse to direct child of the card).

Simplified replacement for the `MoreActionsCard` body:

```kotlin
Text(
    text = "More actions",
    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
    color = CardeaTheme.colors.textPrimary
)
Spacer(modifier = Modifier.height(14.dp))
OutlinedButton(
    onClick = onViewProgress,
    modifier = Modifier.fillMaxWidth().height(44.dp),
    border = BorderStroke(1.dp, CardeaTheme.colors.glassBorder)
) {
    Text(
        text = stringResource(R.string.button_view_progress),
        style = MaterialTheme.typography.labelLarge,
        color = CardeaTheme.colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
Spacer(modifier = Modifier.height(10.dp))
CardeaButton(
    text = stringResource(R.string.button_done),
    onClick = onDone,
    modifier = Modifier.fillMaxWidth().height(48.dp)
)
```

Also delete the `onViewPostRunSummary: () -> Unit` parameter from `MoreActionsCard`, `HistoryDetailScreen`, and the `onViewPostRunSummary = { ... }` wiring in NavGraph at line 622-626.

- [ ] **Step 4.5: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt \
        app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt \
        app/src/main/java/com/hrcoach/ui/history/HistoryDetailScreen.kt
git commit -m "feat(postrun): inline compact route map and remove HistoryDetail loop-back"
```

---

## Task 5: Device verification

- [ ] **Step 5.1: Build and install**

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 5.2: Complete a real outdoor run OR a sim run with synthetic GPS**

Sim runs don't generate GPS track points; if `SimulationController` injects location, verify. Otherwise test with a real short run (or on a device with mock location enabled).

- [ ] **Step 5.3: Observe PostRun**

Expected:
1. 220-dp route map card inside the "Your Run" section, below the 2 stat cards.
2. HR-colored polyline, start/finish markers, camera auto-fitted to bounds.
3. Tap the card → navigates to HistoryDetail map full-screen.
4. HistoryDetail's "More actions" card no longer has a "Post-run insights" button — Done + View Progress only.

- [ ] **Step 5.4: Verify empty states**

1. No API key: Account → Maps → clear key. Post-run: card shows "Route ready, map setup missing" + action label.
2. No GPS (indoor): short run indoors. Card shows "No route data".

- [ ] **Step 5.5: Run full test suite**

Run: `./gradlew testDebugUnitTest`
Expected: ALL PASS.

- [ ] **Step 5.6: PR screenshot**

```bash
adb shell screencap //sdcard/postrun_with_map.png
adb pull //sdcard/postrun_with_map.png /tmp/postrun_with_map.png
```

---

## Self-review checklist

- [x] `RouteMap` is the single source of truth for route visualisation — HistoryDetail delegates, PostRun uses directly.
- [x] `hrToColor` made `internal` so shared component can import it without relocating the function (cheaper churn).
- [x] Tap on PostRun map card → HistoryDetail for the deep-dive: one click, not a two-step "see route button → other screen".
- [x] `MoreActionsCard.onViewPostRunSummary` removed — closes the mutual-navigation loop.
- [x] Map card sits above comparisons so the user sees context (numbers) + setting (route) together.
- [x] Empty states handled via the `RouteMap` component itself — no special-casing at the caller.
- [x] `workoutConfig` is parsed from `workout.targetConfig` JSON (the established path — see `HistoryDetailScreen.kt:parseWorkoutConfig`).
- [x] GPS-less runs show a graceful empty state, not an error.
