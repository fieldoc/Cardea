# Cardea UI/UX Redesign — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.
> Use LSP tools for semantic code search/navigation (findReferences, goToDefinition, hover, documentSymbol). Fall back to Grep for non-Kotlin files.

**Goal:** Rebrand HR Coach → Cardea with full UI/UX redesign per spec, add Home tab (dashboard) and Account tab (settings), apply Cardea design tokens + glass material system across all screens.

**Architecture:** MVVM + Foreground Service unchanged. UI layer only. New screens: HomeScreen + AccountScreen in new packages `ui/home/` and `ui/account/`. NavGraph expands from 3 tabs to 5 (Home, Workout, History, Progress, Account). Design tokens centralized in Color.kt → referenced everywhere via CardeaTheme.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Hilt DI, Room (read-only from new screens), existing repositories.

**Design doc:** `docs/plans/2026-03-02-cardea-ui-ux-design.md`
**Visual spec:** `ui_ux_spec.md`
**Logo renders:** `Render Images/Cardea Logo.png` and `Render Images/Branding Images.png`

---

## Dependency Order

```
Task 1 (Tokens) → must be FIRST
    ↓
Tasks 2, 3, 4, 5, 7, 8, 9 — PARALLEL (Phase 2)
    ↓
Task 6 (NavGraph) → after all screens exist
    ↓
Task 10 (Docs + strings) → last
```

---

## Task 1: Design Tokens + CardeaTheme

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/theme/Type.kt`
- Modify: `app/src/main/res/values/colors.xml`

**Step 1: Replace Color.kt entirely**

```kotlin
package com.hrcoach.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Backgrounds ────────────────────────────────────────────
val CardeaBgPrimary   = Color(0xFF0B0F17)
val CardeaBgSecondary = Color(0xFF0F1623)

// ── Glass surface ──────────────────────────────────────────
val GlassBorder    = Color(0x0FFFFFFF)   // rgba(255,255,255,0.06)
val GlassHighlight = Color(0x14FFFFFF)   // rgba(255,255,255,0.08)
val GlassFill      = Color(0x0AFFFFFF)   // rgba(255,255,255,0.04) — for card bg

// ── Cardea gradient stops ──────────────────────────────────
val GradientRed    = Color(0xFFFF5A5F)
val GradientPink   = Color(0xFFFF2DA6)
val GradientBlue   = Color(0xFF5B5BFF)
val GradientCyan   = Color(0xFF00D1FF)

/** Cardea core gradient — 135deg, exact stops per spec. */
val CardeaGradient = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to GradientRed,
        0.35f to GradientPink,
        0.65f to GradientBlue,
        1.00f to GradientCyan
    )
)

// ── Text ────────────────────────────────────────────────────
val CardeaTextPrimary   = Color(0xFFFFFFFF)
val CardeaTextSecondary = Color(0xFF9AA4B2)
val CardeaTextTertiary  = Color(0xFF5A6573)

// ── Zone colors (preserved) ────────────────────────────────
val ZoneGreen = Color(0xFF34D399)
val ZoneAmber = Color(0xFFF59E0B)
val ZoneRed   = Color(0xFFEF4444)

// ── Legacy aliases (keep for files not yet updated) ─────────
val Background    = CardeaBgPrimary
val Surface       = Color(0xFF0F1623)
val SurfaceVariant = Color(0xFF131921)
val Primary       = GradientBlue
val PrimaryVariant = GradientPink
val OnPrimary     = CardeaTextPrimary
val OnBackground  = CardeaTextPrimary
val OnSurface     = CardeaTextPrimary
val SubtleText    = CardeaTextSecondary
val DividerColor  = GlassBorder
val DisabledGray  = CardeaTextTertiary
val HeatmapGreen  = ZoneGreen
val HeatmapYellow = ZoneAmber
val HeatmapRed    = ZoneRed
val ProgressGreen = ZoneGreen
val ProgressAmber = ZoneAmber
val ThresholdLine  = CardeaTextTertiary
```

**Step 2: Update Theme.kt**

Replace the file content:

```kotlin
package com.hrcoach.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val CardeaDarkColorScheme = darkColorScheme(
    primary         = GradientBlue,
    secondary       = ZoneAmber,
    tertiary        = ZoneGreen,
    error           = ZoneRed,
    onPrimary       = CardeaTextPrimary,
    background      = CardeaBgPrimary,
    onBackground    = CardeaTextPrimary,
    surface         = Surface,
    onSurface       = CardeaTextPrimary,
    surfaceVariant  = SurfaceVariant,
    onSurfaceVariant = CardeaTextSecondary,
    outline         = GlassBorder,
    outlineVariant  = GlassHighlight
)

val LocalGlassBorder  = staticCompositionLocalOf { GlassBorder }
val LocalSubtleText   = staticCompositionLocalOf { CardeaTextSecondary }
val LocalCardeaGradient = staticCompositionLocalOf<Brush> { CardeaGradient }

object CardeaThemeTokens {
    val glassBorder: Color
        @Composable @ReadOnlyComposable
        get() = LocalGlassBorder.current

    val subtleText: Color
        @Composable @ReadOnlyComposable
        get() = LocalSubtleText.current

    val gradient: Brush
        @Composable @ReadOnlyComposable
        get() = LocalCardeaGradient.current
}

// Legacy alias so existing files that import HrCoachThemeTokens still compile
typealias HrCoachThemeTokens = CardeaThemeTokens

@Composable
fun CardeaTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalGlassBorder provides GlassBorder,
        LocalSubtleText provides CardeaTextSecondary,
        LocalCardeaGradient provides CardeaGradient
    ) {
        MaterialTheme(
            colorScheme = CardeaDarkColorScheme,
            typography = HrCoachTypography,
            content = content
        )
    }
}

// Legacy alias — callers using HrCoachTheme(darkTheme=…, dynamicColor=…) need update in MainActivity
@Composable
fun HrCoachTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = CardeaTheme(content)
```

**Step 3: Update colors.xml** — update background to match:

```xml
<resources>
    <color name="splash_background">#0B0F17</color>
</resources>
```

**Step 4: Find and update MainActivity** to call `CardeaTheme {}` instead of `HrCoachTheme(dynamicColor = false)`.
Use LSP `findReferences` on `HrCoachTheme` to locate callers, then update them.

**Step 5: Verify it compiles**

```bash
cd C:/Users/glm_6/AndroidStudioProjects/HRapp
./gradlew.bat assembleDebug 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL (no Unresolved reference errors for color tokens).

**Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/theme/ app/src/main/res/values/colors.xml
git commit -m "feat(theme): add Cardea design tokens and CardeaTheme"
```

---

## Task 2: Cardea Logo Component + Splash Screen

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/components/CardeaLogo.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/splash/SplashScreen.kt`

**Context:** The logo (see `Render Images/Cardea Logo.png`) is a stylized heart with an ECG pulse line and an orbital ring/halo. Gradient: red → pink → indigo → cyan. It's drawn with Canvas in Compose so the gradient can be applied dynamically.

**Step 1: Create CardeaLogo.kt**

```kotlin
package com.hrcoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed

@Composable
fun CardeaLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val gradient = Brush.linearGradient(
            colorStops = arrayOf(
                0.00f to GradientRed,
                0.35f to GradientPink,
                0.65f to GradientBlue,
                1.00f to GradientCyan
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        // 1. Draw orbit ring (rotated ellipse, gradient stroke, glow)
        drawOrbitRing(gradient, w, h)

        // 2. Draw heart shape (gradient fill, subtle)
        drawHeart(gradient, w, h)

        // 3. Draw ECG pulse line across heart
        drawEcgLine(gradient, w, h)
    }
}

private fun DrawScope.drawOrbitRing(gradient: Brush, w: Float, h: Float) {
    val ringStroke = w * 0.06f
    withTransform({
        rotate(-25f, pivot = Offset(w / 2f, h / 2f))
    }) {
        // Glow layer
        drawOval(
            brush = gradient,
            topLeft = Offset(w * 0.02f, h * 0.30f),
            size = Size(w * 0.96f, h * 0.40f),
            style = Stroke(width = ringStroke * 2f, cap = StrokeCap.Round),
            alpha = 0.18f
        )
        // Core ring
        drawOval(
            brush = gradient,
            topLeft = Offset(w * 0.02f, h * 0.30f),
            size = Size(w * 0.96f, h * 0.40f),
            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawHeart(gradient: Brush, w: Float, h: Float) {
    val path = Path().apply {
        val cx = w * 0.50f
        val cy = h * 0.52f
        val hw = w * 0.30f
        val hh = h * 0.26f
        moveTo(cx, cy + hh * 0.6f)
        cubicTo(cx - hw * 0.1f, cy + hh * 0.3f, cx - hw, cy - hh * 0.1f, cx - hw * 0.5f, cy - hh)
        cubicTo(cx - hw * 0.1f, cy - hh * 1.3f, cx, cy - hh * 0.5f, cx, cy - hh * 0.2f)
        cubicTo(cx, cy - hh * 0.5f, cx + hw * 0.1f, cy - hh * 1.3f, cx + hw * 0.5f, cy - hh)
        cubicTo(cx + hw, cy - hh * 0.1f, cx + hw * 0.1f, cy + hh * 0.3f, cx, cy + hh * 0.6f)
        close()
    }
    drawPath(path = path, brush = gradient, alpha = 0.55f)
}

private fun DrawScope.drawEcgLine(gradient: Brush, w: Float, h: Float) {
    val cy = h * 0.52f
    val path = Path().apply {
        moveTo(w * 0.22f, cy)
        lineTo(w * 0.35f, cy)
        lineTo(w * 0.40f, cy - h * 0.08f)
        lineTo(w * 0.45f, cy + h * 0.12f)
        lineTo(w * 0.50f, cy - h * 0.16f)
        lineTo(w * 0.55f, cy + h * 0.06f)
        lineTo(w * 0.60f, cy)
        lineTo(w * 0.78f, cy)
    }
    // Glow
    drawPath(
        path = path,
        brush = gradient,
        style = Stroke(width = w * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        alpha = 0.20f
    )
    // Sharp line
    drawPath(
        path = path,
        brush = gradient,
        style = Stroke(width = w * 0.025f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}
```

**Step 2: Update SplashScreen.kt**

Replace the entire file:

```kotlin
package com.hrcoach.ui.splash

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.components.CardeaLogo
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "splash-pulse")
    val pulseScale = transition.animateFloat(
        initialValue = 1.00f,
        targetValue  = 1.06f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splash-scale"
    )

    LaunchedEffect(Unit) {
        delay(1_800L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(CardeaBgSecondary, CardeaBgPrimary),
                    center = Offset.Zero,
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CardeaLogo(
                size = 96.dp,
                modifier = Modifier.scale(pulseScale.value)
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Cardea",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Listen to your body.",
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTextSecondary
            )
        }
    }
}
```

**Step 3: Build to verify**

```bash
./gradlew.bat assembleDebug 2>&1 | tail -20
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/CardeaLogo.kt \
        app/src/main/java/com/hrcoach/ui/splash/SplashScreen.kt
git commit -m "feat(branding): add CardeaLogo component and redesign splash screen"
```

---

## Task 3: GlassCard + Nav Bar Component Updates

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/components/GlassCard.kt`

**Context:** GlassCard is the shared container for all metric cards. Needs: 18dp radius, proper Cardea glass fill (semi-transparent gradient), updated border color.

**Step 1: Update GlassCard.kt**

Replace the file:

```kotlin
package com.hrcoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.HrCoachThemeTokens

private val GlassShape = RoundedCornerShape(18.dp)

private val GlassFillBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0x0FFFFFFF),  // rgba(255,255,255,0.06)
        Color(0x05FFFFFF)   // rgba(255,255,255,0.02)
    )
)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.wrapContentHeight(),
        shape = GlassShape,
        border = BorderStroke(1.dp, GlassBorder),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.background(GlassFillBrush)) {
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CardeaTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = if (horizontalAlignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start
        )
        if (unit != null) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = CardeaTextSecondary
            )
        }
    }
}
```

**Step 2: Build**

```bash
./gradlew.bat assembleDebug 2>&1 | tail -15
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/GlassCard.kt
git commit -m "feat(ui): update GlassCard to Cardea glass material system"
```

---

## Task 4: HomeScreen + HomeViewModel

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/home/HomeViewModel.kt`
- Create: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt`

**Context:**
- Home is the new dashboard tab. Shows: greeting, Efficiency Ring, last run card, Start Run CTA, quick links to Progress/History.
- HomeViewModel queries `WorkoutRepository` for recent workouts.
- Use LSP to find WorkoutRepository and WorkoutEntity to understand available data before writing the ViewModel.
- The Efficiency Ring shows: workouts this week as a fraction of a weekly target (default 4). Value is clamped 0–100.

**Step 1: Inspect existing repository**

Use LSP `hover` on `WorkoutRepository` to see available methods.
Use LSP `hover` on `WorkoutEntity` to see entity fields.

Key fields to expect on WorkoutEntity:
- `id: Long`
- `startEpochMs: Long` (or similar timestamp)
- `durationMs: Long`
- `distanceKm: Double`
- `avgHr: Int`

**Step 2: Create HomeViewModel.kt**

```kotlin
package com.hrcoach.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.db.WorkoutEntity
import com.hrcoach.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class HomeUiState(
    val greeting: String = "Hello",
    val lastWorkout: WorkoutEntity? = null,
    val workoutsThisWeek: Int = 0,
    val weeklyTarget: Int = 4,
    val efficiencyPercent: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = workoutRepository.allWorkoutsFlow()
        .map { workouts ->
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val weekStart = now.atZone(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toInstant().toEpochMilli()

            val thisWeek = workouts.count { it.startEpochMs >= weekStart }
            val target = 4
            val pct = ((thisWeek.toFloat() / target) * 100).toInt().coerceIn(0, 100)
            val hour = now.atZone(zone).hour
            val greeting = when {
                hour < 12 -> "Good morning"
                hour < 18 -> "Good afternoon"
                else      -> "Good evening"
            }
            HomeUiState(
                greeting            = greeting,
                lastWorkout         = workouts.firstOrNull(),
                workoutsThisWeek    = thisWeek,
                weeklyTarget        = target,
                efficiencyPercent   = pct
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
```

> **Note:** If `WorkoutRepository` does not expose `allWorkoutsFlow()`, use LSP `documentSymbol` to find the actual method name. If the repository returns a List rather than Flow, wrap with `flowOf()`. Adapt as needed but do not change the repository or DAO.

**Step 3: Create HomeScreen.kt**

```kotlin
package com.hrcoach.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.ui.components.CardeaLogo
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GlassHighlight
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.util.formatDistanceKm
import com.hrcoach.util.formatPaceMinPerKm
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(
    onStartRun: () -> Unit,
    onGoToProgress: () -> Unit,
    onGoToHistory: () -> Unit,
    onGoToAccount: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(containerColor = Color.Transparent) { padding ->
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
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Header ────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Cardea",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = GradientPink
                        )
                        Text(
                            text = state.greeting,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                            color = Color.White
                        )
                    }
                    // Avatar → Account
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(GlassHighlight)
                            .clickable(onClick = onGoToAccount),
                        contentAlignment = Alignment.Center
                    ) {
                        CardeaLogo(size = 24.dp)
                    }
                }

                // ── Efficiency Ring Card ──────────────────────────
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Weekly Activity",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "${state.workoutsThisWeek} of ${state.weeklyTarget} runs",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTextSecondary
                            )
                        }
                        EfficiencyRing(percent = state.efficiencyPercent)
                    }
                }

                // ── Last Run Card ─────────────────────────────────
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Last Run",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    val workout = state.lastWorkout
                    if (workout == null) {
                        Text(
                            text = "No runs yet — start your first run below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CardeaTextSecondary
                        )
                    } else {
                        val dateStr = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                            .format(Date(workout.startEpochMs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LastRunStat(label = "Date", value = dateStr)
                            LastRunStat(label = "Distance", value = formatDistanceKm(workout.distanceKm))
                            LastRunStat(label = "Avg HR", value = "${workout.avgHr} bpm")
                        }
                    }
                }

                // ── Start Run CTA ─────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardeaGradient)
                        .clickable(onClick = onStartRun),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Start a Run",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                // ── Quick Links ───────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickLinkChip(
                        label = "Progress",
                        modifier = Modifier.weight(1f),
                        onClick = onGoToProgress
                    )
                    QuickLinkChip(
                        label = "History",
                        modifier = Modifier.weight(1f),
                        onClick = onGoToHistory
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EfficiencyRing(percent: Int) {
    val gradient = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to GradientRed,
            0.35f to GradientPink,
            0.65f to GradientBlue,
            1.00f to GradientCyan
        )
    )
    Box(
        modifier = Modifier
            .size(90.dp)
            .drawWithCache {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2f
                val sweepAngle = (percent / 100f) * 360f
                onDrawWithContent {
                    // Background ring
                    drawArc(
                        color = Color(0x14FFFFFF),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        topLeft = Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    )
                    // Gradient foreground
                    drawArc(
                        brush = gradient,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        topLeft = Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp
            ),
            color = Color.White
        )
    }
}

@Composable
private fun LastRunStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CardeaTextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Color.White)
    }
}

@Composable
private fun QuickLinkChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x14FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = CardeaTextSecondary
        )
    }
}
```

**Step 4: Check field names match WorkoutEntity**

Use LSP `hover` on `WorkoutEntity` — confirm `startEpochMs`, `distanceKm`, `avgHr` exist. If field names differ, update the ViewModel and screen accordingly.

**Step 5: Check WorkoutRepository flow method name**

Use LSP `documentSymbol` on `WorkoutRepository` — find the method that returns all workouts as a Flow. Update `HomeViewModel.kt` line `workoutRepository.allWorkoutsFlow()` to use the correct method name.

**Step 6: Build**

```bash
./gradlew.bat assembleDebug 2>&1 | tail -20
```

**Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/home/
git commit -m "feat(home): add HomeScreen dashboard with Efficiency Ring and last run card"
```

---

## Task 5: AccountScreen + AccountViewModel

**Files:**
- Create: `app/src/main/java/com/hrcoach/ui/account/AccountViewModel.kt`
- Create: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt`

**Context:**
- Account is the settings hub. Surfaces: Maps API key, voice verbosity, volume, vibration, app info.
- These settings currently live in a dialog in SetupScreen. After this task, we remove the settings dialog from SetupScreen (Task 7).
- Use LSP to find `AudioSettingsRepository` and `MapsSettingsRepository` before writing ViewModel.
- Use LSP `documentSymbol` on `SetupViewModel` to find how settings are currently read/saved.

**Step 1: Inspect existing repos and SetupUiState**

Use LSP `documentSymbol` on `AudioSettingsRepository` — find available read/write methods.
Use LSP `documentSymbol` on `MapsSettingsRepository` — find available read/write methods.
Use LSP `hover` on `SetupUiState` — see what fields exist.

Expected fields in AudioSettingsRepository: earconVolume (Int 0-100), voiceVerbosity (VoiceVerbosity enum), enableVibration (Boolean).
Expected fields in MapsSettingsRepository: mapsApiKey (String).

**Step 2: Create AccountViewModel.kt**

```kotlin
package com.hrcoach.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrcoach.data.repository.AudioSettingsRepository
import com.hrcoach.data.repository.MapsSettingsRepository
import com.hrcoach.data.repository.WorkoutRepository
import com.hrcoach.domain.model.VoiceVerbosity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val totalWorkouts: Int = 0,
    val mapsApiKey: String = "",
    val mapsApiKeySaved: Boolean = false,
    val earconVolume: Int = 80,
    val voiceVerbosity: VoiceVerbosity = VoiceVerbosity.MINIMAL,
    val enableVibration: Boolean = true,
    val appVersion: String = "1.0"
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val audioRepo: AudioSettingsRepository,
    private val mapsRepo: MapsSettingsRepository,
    private val workoutRepo: WorkoutRepository
) : ViewModel() {

    // Local mutable state for fields not in flows
    private val _mapsKey = MutableStateFlow("")
    private val _mapsKeySaved = MutableStateFlow(false)
    private val _volume = MutableStateFlow(80)
    private val _verbosity = MutableStateFlow(VoiceVerbosity.MINIMAL)
    private val _vibration = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            _mapsKey.value = mapsRepo.getApiKey()
            val prefs = audioRepo.load()
            _volume.value = prefs.earconVolume
            _verbosity.value = prefs.voiceVerbosity
            _vibration.value = prefs.enableVibration
        }
    }

    val uiState: StateFlow<AccountUiState> = combine(
        workoutRepo.allWorkoutsFlow().map { it.size },
        _mapsKey,
        _mapsKeySaved,
        _volume,
        _verbosity
    ) { count, key, saved, vol, verb ->
        AccountUiState(
            totalWorkouts    = count,
            mapsApiKey       = key,
            mapsApiKeySaved  = saved,
            earconVolume     = vol,
            voiceVerbosity   = verb,
            enableVibration  = _vibration.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState())

    fun setMapsApiKey(key: String) { _mapsKey.value = key; _mapsKeySaved.value = false }
    fun saveMapsApiKey() {
        viewModelScope.launch { mapsRepo.saveApiKey(_mapsKey.value); _mapsKeySaved.value = true }
    }

    fun setVolume(v: Float) { _volume.value = ((v / 5f).toInt() * 5).coerceIn(0, 100) }
    fun setVerbosity(v: VoiceVerbosity) { _verbosity.value = v }
    fun setVibration(v: Boolean) { _vibration.value = v }
    fun saveAudioSettings() {
        viewModelScope.launch { audioRepo.save(_volume.value, _verbosity.value, _vibration.value) }
    }
}
```

> **Note:** Adapt method names to match actual repository API found via LSP. If `audioRepo.load()` returns individual fields or a data class, adjust accordingly.

**Step 3: Create AccountScreen.kt**

```kotlin
package com.hrcoach.ui.account

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hrcoach.BuildConfig
import com.hrcoach.domain.model.VoiceVerbosity
import com.hrcoach.ui.components.CardeaLogo
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GradientPink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account", color = Color.White, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
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
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CardeaGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            CardeaLogo(size = 36.dp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Runner",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = Color.White
                            )
                            Text(
                                text = "${state.totalWorkouts} runs recorded",
                                style = MaterialTheme.typography.bodySmall,
                                color = CardeaTextSecondary
                            )
                        }
                    }
                }

                // Maps settings
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Maps", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.mapsApiKey,
                        onValueChange = viewModel::setMapsApiKey,
                        singleLine = true,
                        label = { Text("Google Maps API key") }
                    )
                    Text(
                        text = if (state.mapsApiKeySaved) "Saved. Restart if map still appears blank."
                               else "Used only for route rendering in History.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTextSecondary
                    )
                    TextButton(onClick = viewModel::saveMapsApiKey) { Text("Save") }
                }

                // Audio settings
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Audio & Alerts", style = MaterialTheme.typography.titleMedium, color = Color.White)

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Alert Sound Volume", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        Text("${state.earconVolume}%", color = CardeaTextSecondary)
                    }
                    Slider(
                        value = state.earconVolume.toFloat(),
                        onValueChange = viewModel::setVolume,
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChangeFinished = viewModel::saveAudioSettings
                    )

                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(4.dp))

                    Text("Voice Coaching", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(VoiceVerbosity.OFF to "Off", VoiceVerbosity.MINIMAL to "Minimal", VoiceVerbosity.FULL to "Full")
                            .forEachIndexed { i, (v, label) ->
                                SegmentedButton(
                                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                                    selected = state.voiceVerbosity == v,
                                    onClick = { viewModel.setVerbosity(v); viewModel.saveAudioSettings() }
                                ) { Text(label) }
                            }
                    }

                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Vibration Alerts", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        Switch(
                            checked = state.enableVibration,
                            onCheckedChange = { viewModel.setVibration(it); viewModel.saveAudioSettings() },
                            colors = SwitchDefaults.colors(checkedTrackColor = GradientPink)
                        )
                    }
                }

                // About
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("About", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text("Cardea", style = MaterialTheme.typography.bodyMedium, color = CardeaTextSecondary)
                    Text(
                        text = "Heart rate zone coach for runners.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
```

**Step 4: Verify repository method signatures with LSP**

Use `find_symbol("AudioSettingsRepository", depth=1, include_body=true)` and adapt the ViewModel's `audioRepo.load()` and `audioRepo.save(...)` calls to the actual API.

**Step 5: Build**

```bash
./gradlew.bat assembleDebug 2>&1 | tail -20
```

**Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/account/
git commit -m "feat(account): add AccountScreen with profile card and settings"
```

---

## Task 6: NavGraph — 5-Tab Navigation

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Context:** Add Home and Account routes. Bottom bar gains 2 new tabs. Gradient active icon via `drawWithCache` + `BlendMode.SrcIn`.

**Step 1: Update strings.xml**

```xml
<resources>
    <string name="app_name">Cardea</string>
    <string name="nav_home">Home</string>
    <string name="nav_workout">Workout</string>
    <string name="nav_progress">Progress</string>
    <string name="nav_history">History</string>
    <string name="nav_account">Account</string>
    <string name="screen_home_title">Home</string>
    <string name="screen_setup_title">Start a Run</string>
    <string name="screen_progress_title">Progress</string>
    <string name="screen_history_title">History</string>
    <string name="screen_workout_title">Workout</string>
    <string name="screen_workout_paused_title">Workout Paused</string>
    <string name="button_start_workout">Start Workout</string>
    <string name="button_view_progress">View Progress</string>
    <string name="button_view_run">View This Run</string>
    <string name="button_post_run_insights">Post-run Insights</string>
    <string name="button_open_maps_setup">Open Maps Setup</string>
    <string name="button_delete_run">Delete Run</string>
    <string name="button_done">Done</string>
    <string name="button_pause">PAUSE</string>
    <string name="button_resume">RESUME</string>
    <string name="button_stop">STOP</string>
    <string name="dialog_stop_workout_title">Stop workout?</string>
    <string name="dialog_stop_workout_message">Your run will end and post-run summary will be generated.</string>
    <string name="dialog_delete_run_title">Delete this run?</string>
    <string name="dialog_delete_run_message">This removes the workout, route points, and saved metrics.</string>
    <string name="dialog_cancel">Cancel</string>
    <string name="label_distance">Distance</string>
    <string name="label_duration">Duration</string>
    <string name="label_avg_hr">Avg HR</string>
</resources>
```

**Step 2: Rewrite NavGraph.kt**

Key changes:
- Add `HOME = "home"` and `ACCOUNT = "account"` to Routes.
- Change start destination to `Routes.SPLASH` (unchanged) → navigates to `Routes.HOME` (was SETUP).
- Bottom bar shows 5 tabs: Home, Workout, History, Progress, Account.
- `showBottomBar` includes `home` and `account` routes.
- Gradient icon tint using `drawWithCache + BlendMode.SrcIn` + `graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)`.

```kotlin
package com.hrcoach.ui.navigation

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.spring
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hrcoach.R
import com.hrcoach.service.WorkoutForegroundService
import com.hrcoach.service.WorkoutState
import com.hrcoach.ui.account.AccountScreen
import com.hrcoach.ui.history.HistoryDetailScreen
import com.hrcoach.ui.history.HistoryListScreen
import com.hrcoach.ui.home.HomeScreen
import com.hrcoach.ui.postrun.PostRunSummaryScreen
import com.hrcoach.ui.progress.ProgressScreen
import com.hrcoach.ui.setup.SetupScreen
import com.hrcoach.ui.splash.SplashScreen
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.workout.ActiveWorkoutScreen
import com.hrcoach.util.PermissionGate

private const val NavDurationMs = 250

object Routes {
    const val SPLASH  = "splash"
    const val HOME    = "home"
    const val SETUP   = "setup"
    const val WORKOUT = "workout"
    const val PROGRESS = "progress"
    const val HISTORY  = "history"
    const val ACCOUNT  = "account"
    const val HISTORY_DETAIL    = "history/{workoutId}"
    const val POST_RUN_SUMMARY  = "postrun/{workoutId}"

    fun historyDetail(workoutId: Long) = "history/$workoutId"
    fun postRunSummary(workoutId: Long) = "postrun/$workoutId"
}

private data class NavItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavItem(Routes.HOME,     R.string.nav_home,     Icons.Filled.Home,           Icons.Outlined.Home),
    NavItem(Routes.SETUP,    R.string.nav_workout,  Icons.Filled.DirectionsRun,  Icons.Filled.DirectionsRun),
    NavItem(Routes.HISTORY,  R.string.nav_history,  Icons.AutoMirrored.Filled.List,    Icons.AutoMirrored.Outlined.List),
    NavItem(Routes.PROGRESS, R.string.nav_progress, Icons.AutoMirrored.Filled.ShowChart, Icons.AutoMirrored.Outlined.ShowChart),
    NavItem(Routes.ACCOUNT,  R.string.nav_account,  Icons.Filled.AccountCircle,  Icons.Outlined.AccountCircle)
)

@Composable
fun HrCoachNavGraph(windowSizeClass: WindowSizeClass) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val workoutSnapshot by WorkoutState.snapshot.collectAsState()
    val isWorkoutRunning = workoutSnapshot.isRunning
    val completedWorkoutId = workoutSnapshot.completedWorkoutId
    val isWideLayout = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    LaunchedEffect(isWorkoutRunning, completedWorkoutId) {
        val routeNow = navController.currentBackStackEntry?.destination?.route
        if (isWorkoutRunning && routeNow != Routes.WORKOUT) {
            navController.navigate(Routes.WORKOUT) {
                popUpTo(Routes.HOME) { inclusive = false }
                launchSingleTop = true
            }
            return@LaunchedEffect
        }
        if (!isWorkoutRunning && routeNow == Routes.WORKOUT) {
            val finishedId = completedWorkoutId
            if (finishedId != null) {
                navController.navigate(Routes.postRunSummary(finishedId)) {
                    popUpTo(Routes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
                WorkoutState.clearCompletedWorkoutId()
            } else {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.HOME) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    val bottomBarRoutes = setOf(Routes.HOME, Routes.SETUP, Routes.HISTORY, Routes.PROGRESS, Routes.ACCOUNT)
    val showBottomBar = !isWorkoutRunning && currentRoute in bottomBarRoutes

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it / 2 }, animationSpec = spring()) + fadeIn(tween(NavDurationMs)),
                exit  = slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = spring()) + fadeOut(tween(NavDurationMs))
            ) {
                CardeaNavBar(currentRoute = currentRoute) { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController   = navController,
            startDestination = Routes.SPLASH,
            modifier        = Modifier.padding(paddingValues)
        ) {
            composable(Routes.SPLASH,
                enterTransition = { fadeIn(tween(NavDurationMs)) },
                exitTransition  = { scaleOut(targetScale = 1.03f, animationSpec = tween(NavDurationMs)) + fadeOut(tween(NavDurationMs)) }
            ) {
                SplashScreen(onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                        launchSingleTop = true
                    }
                })
            }

            composable(Routes.HOME, enterTransition = { defaultEnter(-1) }, exitTransition = { defaultExit(-1) }) {
                HomeScreen(
                    onStartRun       = { navController.navigate(Routes.SETUP) { launchSingleTop = true } },
                    onGoToProgress   = { navController.navigate(Routes.PROGRESS) { popUpTo(Routes.HOME) { inclusive = false }; launchSingleTop = true } },
                    onGoToHistory    = { navController.navigate(Routes.HISTORY)  { popUpTo(Routes.HOME) { inclusive = false }; launchSingleTop = true } },
                    onGoToAccount    = { navController.navigate(Routes.ACCOUNT)  { popUpTo(Routes.HOME) { inclusive = false }; launchSingleTop = true } }
                )
            }

            composable(Routes.SETUP, enterTransition = { defaultEnter(-1) }, exitTransition = { defaultExit(-1) }) {
                SetupScreen(
                    isWideLayout = isWideLayout,
                    onStartWorkout = { configJson, deviceAddress ->
                        if (!PermissionGate.hasAllRuntimePermissions(context)) {
                            Toast.makeText(context, "Grant required permissions before starting workout.", Toast.LENGTH_LONG).show()
                        } else {
                            val intent = Intent(context, WorkoutForegroundService::class.java).apply {
                                action = WorkoutForegroundService.ACTION_START
                                putExtra(WorkoutForegroundService.EXTRA_CONFIG_JSON, configJson)
                                putExtra(WorkoutForegroundService.EXTRA_DEVICE_ADDRESS, deviceAddress)
                            }
                            runCatching { context.startForegroundService(intent) }.onFailure {
                                Toast.makeText(context, "Unable to start workout.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            composable(Routes.WORKOUT, enterTransition = { defaultEnter(1) }, exitTransition = { defaultExit(1) }) {
                ActiveWorkoutScreen(
                    onPauseResume = {
                        val action = if (workoutSnapshot.isPaused) WorkoutForegroundService.ACTION_RESUME else WorkoutForegroundService.ACTION_PAUSE
                        context.startService(Intent(context, WorkoutForegroundService::class.java).apply { this.action = action })
                    },
                    onStopConfirmed = {
                        context.startService(Intent(context, WorkoutForegroundService::class.java).apply { action = WorkoutForegroundService.ACTION_STOP })
                    }
                )
            }

            composable(Routes.PROGRESS, enterTransition = { defaultEnter(1) }, exitTransition = { defaultExit(1) }) {
                ProgressScreen(onStartWorkout = {
                    navController.navigate(Routes.SETUP) { popUpTo(Routes.HOME) { inclusive = false }; launchSingleTop = true }
                })
            }

            composable(Routes.HISTORY, enterTransition = { defaultEnter(1) }, exitTransition = { defaultExit(1) }) {
                HistoryListScreen(
                    onWorkoutClick = { navController.navigate(Routes.historyDetail(it)) },
                    onStartWorkout = { navController.navigate(Routes.SETUP) { popUpTo(Routes.HOME) { inclusive = false }; launchSingleTop = true } }
                )
            }

            composable(Routes.ACCOUNT, enterTransition = { defaultEnter(1) }, exitTransition = { defaultExit(1) }) {
                AccountScreen()
            }

            composable(
                route = Routes.HISTORY_DETAIL,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
                enterTransition = { defaultEnter(1) }, exitTransition = { defaultExit(1) }
            ) { back ->
                val workoutId = back.arguments?.getLong("workoutId") ?: return@composable
                HistoryDetailScreen(
                    workoutId = workoutId,
                    onBack = { navController.popBackStack() },
                    onOpenMapsSetup = { navController.navigate(Routes.ACCOUNT) { launchSingleTop = true } },
                    onViewProgress = { navController.navigate(Routes.PROGRESS) { launchSingleTop = true } },
                    onViewPostRunSummary = { navController.navigate(Routes.postRunSummary(workoutId)) { launchSingleTop = true } },
                    onDeleteWorkout = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.POST_RUN_SUMMARY,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType }),
                enterTransition = { defaultEnter(1) }, exitTransition = { defaultExit(1) }
            ) { back ->
                val workoutId = back.arguments?.getLong("workoutId") ?: return@composable
                PostRunSummaryScreen(
                    workoutId = workoutId,
                    onViewProgress = { navController.navigate(Routes.PROGRESS) { launchSingleTop = true } },
                    onViewHistory  = { navController.navigate(Routes.historyDetail(workoutId)) { popUpTo(Routes.HISTORY) { inclusive = false }; launchSingleTop = true } },
                    onDone = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true }; launchSingleTop = true } },
                    onBack = { if (!navController.popBackStack()) navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } }
                )
            }
        }
    }
}

@Composable
private fun CardeaNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(GradientRed, GradientPink, GradientBlue, GradientCyan)
    )
    NavigationBar(
        containerColor = Color(0x14FFFFFF),  // glass tint
        tonalElevation = 0.dp,
        modifier = Modifier.height(72.dp)
    ) {
        navItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick  = { onNavigate(item.route) },
                icon = {
                    val icon = if (selected) item.selectedIcon else item.unselectedIcon
                    if (selected) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithCache {
                                    onDrawWithContent {
                                        drawContent()
                                        drawRect(brush = gradientBrush, blendMode = BlendMode.SrcIn)
                                    }
                                },
                            tint = Color.White  // will be overridden by blend mode
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = CardeaTextTertiary
                        )
                    }
                },
                label = {
                    Text(
                        text = stringResource(item.labelRes),
                        fontSize = 11.sp,
                        color = if (selected) Color.White else CardeaTextTertiary
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = Color.Transparent,
                    unselectedIconColor = CardeaTextTertiary,
                    indicatorColor      = Color.Transparent
                )
            )
        }
    }
}

private fun defaultEnter(dir: Int): EnterTransition =
    slideInHorizontally(initialOffsetX = { it * dir / 3 }, animationSpec = tween(NavDurationMs)) + fadeIn(tween(NavDurationMs))

private fun defaultExit(dir: Int): ExitTransition =
    slideOutHorizontally(targetOffsetX = { it * dir / 4 }, animationSpec = tween(NavDurationMs)) + fadeOut(tween(NavDurationMs))
```

**Step 3: Check `Outlined.Home` and `Outlined.AccountCircle` availability**

These icons may not exist in the standard Icons set. If `Icons.Outlined.Home` doesn't resolve, use `Icons.Default.Home` for both states. Verify by trying to build.

**Step 4: Build**

```bash
./gradlew.bat assembleDebug 2>&1 | tail -30
```

Fix any import/reference errors. The most likely issue: outlined icon variants may not exist — fall back to Filled variants with different alpha.

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/navigation/NavGraph.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(nav): expand to 5-tab Cardea navigation (Home, Workout, History, Progress, Account)"
```

---

## Task 7: SetupScreen Visual Upgrade

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt`

**Context:**
- Remove the Maps API key dialog (moved to Account).
- Remove the Settings IconButton from TopAppBar (no longer needed).
- Apply Cardea gradient to the Start Workout button.
- Update TopAppBar to transparent/glass style.
- Keep ALL existing content and logic.

Read `SetupScreen.kt` to review the current state, then make targeted edits.

**Step 1: Remove settings dialog**

Use Edit to:
1. Remove `var showMapsDialog by remember { mutableStateOf(false) }` line.
2. Remove the `IconButton(onClick = { showMapsDialog = true })` block in `actions = { }`.
3. Remove the `if (showMapsDialog) { AlertDialog(...) }` block at the bottom.
4. Remove `mapsApiKey` and `mapsApiKeySaved` references from the screen (they're now in AccountScreen).
5. Remove `state.mapsApiKey`, `viewModel::setMapsApiKey`, `viewModel.saveMapsApiKey()` calls.

**Step 2: Replace Start Workout button with gradient version**

Find the Button composable near the end of the screen. Replace:
```kotlin
Button(
    onClick = { ... },
    modifier = Modifier.fillMaxWidth().height(56.dp),
    enabled = state.validation.canStartWorkout,
    colors = ButtonDefaults.buttonColors(...)
) {
    Text(text = stringResource(R.string.button_start_workout), ...)
}
```

With:
```kotlin
val isEnabled = state.validation.canStartWorkout
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(56.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(
            if (isEnabled) CardeaGradient else Brush.linearGradient(listOf(CardeaTextTertiary, CardeaTextTertiary))
        )
        .clickable(enabled = isEnabled) {
            val configJson = viewModel.buildConfigJsonOrNull() ?: return@clickable
            viewModel.saveAudioSettings()
            val deviceAddress = viewModel.handoffConnectedDeviceAddress()
            onStartWorkout(configJson, deviceAddress)
        },
    contentAlignment = Alignment.Center
) {
    Text(
        text = stringResource(R.string.button_start_workout),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = Color.White
    )
}
```

Add required imports: `CardeaGradient`, `CardeaTextTertiary`, `RoundedCornerShape`, `Brush`, `clip`, `clickable`.

**Step 3: Update TopAppBar to transparent**

Replace `TopAppBar` with:
```kotlin
TopAppBar(
    title = { Text(stringResource(R.string.screen_setup_title)) },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
)
```

Add import: `TopAppBarDefaults`.

**Step 4: Build**

```bash
./gradlew.bat assembleDebug 2>&1 | tail -20
```

**Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/setup/SetupScreen.kt
git commit -m "feat(setup): apply Cardea gradient CTA, remove settings dialog (moved to Account)"
```

---

## Task 8: Charts — Cardea Gradient + Glow

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/charts/BarChart.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/charts/ScatterPlot.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/charts/ProgressChartCard.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/charts/CalendarHeatmap.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/charts/PieChart.kt`

**Context:** All graphs need: Cardea gradient stroke, glow layer (duplicate line 6dp blur 0.15 alpha), round caps, grid lines rgba(255,255,255,0.04). Read each file before editing.

**For each file — General approach:**

1. Read the file.
2. Identify where `drawLine`, `drawPath`, `drawArc`, or `drawRect` calls are used for the primary data lines/bars.
3. Replace solid-color strokes with `CardeaGradient` brush.
4. Add a glow pass before the main draw call.

**Glow layer pattern for line graphs:**

```kotlin
// 1. Glow pass (behind main line)
drawPath(
    path = linePath,
    brush = Brush.linearGradient(colors = listOf(GradientRed, GradientPink, GradientBlue, GradientCyan)),
    style = Stroke(width = strokeWidthPx * 4f, cap = StrokeCap.Round),
    alpha = 0.15f
)
// 2. Main line
drawPath(
    path = linePath,
    brush = Brush.linearGradient(colors = listOf(GradientRed, GradientPink, GradientBlue, GradientCyan)),
    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
)
```

**For bar charts:** replace bar `drawRect` with gradient fill:
```kotlin
drawRect(
    brush = Brush.verticalGradient(
        colors = listOf(GradientCyan, GradientBlue, GradientPink),
        startY = barTop,
        endY = barBottom
    ),
    ...
)
```

**Grid lines:** replace grid `drawLine` colors with `Color(0x0AFFFFFF)`.

**Step: Read, update, build each file one by one.**

Read each chart file, then use Edit to make targeted changes. Don't rewrite the entire file — only change the color/brush references.

**Build after all chart changes:**

```bash
./gradlew.bat assembleDebug 2>&1 | tail -20
```

**Commit:**

```bash
git add app/src/main/java/com/hrcoach/ui/charts/
git commit -m "feat(charts): apply Cardea gradient and glow layer to all chart components"
```

---

## Task 9: ActiveWorkoutScreen Visual Polish

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt`

**Context:** Spec says: use gradient ONLY for active recording indicators. Don't recolor everything. Add Cardea background. Polish glass cards.

Read `ActiveWorkoutScreen.kt` to review current state.

**Step 1: Add Cardea background**

In the root `Scaffold` or outer `Box`, add:
```kotlin
.background(
    Brush.radialGradient(
        colors = listOf(CardeaBgSecondary, CardeaBgPrimary),
        center = Offset.Zero,
        radius = 1800f
    )
)
```

**Step 2: Apply gradient to the active recording indicator**

Find where the "recording" or "live" indicator is shown (likely a pulsing dot or colored circle). Replace its color with gradient:
```kotlin
Box(
    modifier = Modifier
        .size(10.dp)
        .clip(CircleShape)
        .background(CardeaGradient)
)
```

**Step 3: Make HR number prominent with gradient tint (if currently plain)**

The HR display number doesn't need the gradient — keep it white. Only the recording indicator uses gradient per spec.

**Step 4: Update TopAppBar to transparent**

```kotlin
TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
```

**Step 5: Build**

```bash
./gradlew.bat assembleDebug 2>&1 | tail -15
```

**Step 6: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/workout/ActiveWorkoutScreen.kt
git commit -m "feat(workout): add Cardea background and gradient recording indicator"
```

---

## Task 10: Documentation + History/Progress Screen Headers

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/plans/2026-02-25-hr-coaching-app-design.md`
- Modify: `app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt`
- Modify: `app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt`

**Step 1: Update CLAUDE.md**

Find `HR Coach` references (use grep) and update:
- App name: "Cardea (formerly HR Coach)"
- Theme section: add note about `CardeaTheme` and token locations
- Navigation section: update routes list to include `home` and `account`
- UI section: add note about CardeaGradient and glass material system

**Step 2: Add supersede note to old design doc**

Add to the top of `docs/plans/2026-02-25-hr-coaching-app-design.md`:
```
> **Note:** UI/UX layer superseded by Cardea rebrand — see `2026-03-02-cardea-ui-ux-design.md`. Domain and service architecture in this document remains valid.
```

**Step 3: Add transparent TopAppBar to remaining screens**

In `HistoryListScreen.kt`, `ProgressScreen.kt`, and `PostRunSummaryScreen.kt`: update TopAppBar colors to transparent (same pattern as Task 7 Step 3). Use LSP `findReferences` or grep to locate each TopAppBar.

**Step 4: Add background gradient to remaining screens**

In `HistoryListScreen`, `ProgressScreen`, `PostRunSummaryScreen`: wrap content in a Box with the Cardea radial gradient background (same as Tasks 4, 5, 9).

**Step 5: Final full build**

```bash
./gradlew.bat assembleDebug 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL

**Step 6: Run unit tests**

```bash
./gradlew.bat test 2>&1 | tail -20
```

Expected: All existing tests pass (no logic was changed).

**Step 7: Commit all docs + screen polish**

```bash
git add CLAUDE.md \
        docs/plans/2026-02-25-hr-coaching-app-design.md \
        app/src/main/java/com/hrcoach/ui/history/HistoryListScreen.kt \
        app/src/main/java/com/hrcoach/ui/progress/ProgressScreen.kt \
        app/src/main/java/com/hrcoach/ui/postrun/PostRunSummaryScreen.kt
git commit -m "docs: update CLAUDE.md and design docs for Cardea rebrand; polish remaining screen headers"
```

---

## Task 11: Final Review Build

**Step 1: Clean build**

```bash
./gradlew.bat clean assembleDebug 2>&1 | tail -40
```

**Step 2: Unit tests**

```bash
./gradlew.bat test 2>&1 | tail -20
```

**Step 3: Lint**

```bash
./gradlew.bat lint 2>&1 | grep -E "(Error|Warning)" | head -20
```

Address any errors. Warnings about unused resources or icons are acceptable.

**Step 4: Final commit (if any fixes needed)**

```bash
git add -A
git commit -m "fix: address post-build lint and compilation issues"
```

---

## Task Dependency Summary for Subagent Team

```
Agent A: Task 1 (Tokens) — must complete BEFORE all others start
    ↓
Agent B: Task 2 (Logo + Splash) + Task 3 (GlassCard)        [parallel with C, D, E]
Agent C: Task 4 (HomeScreen + ViewModel)                      [parallel with B, D, E]
Agent D: Task 5 (AccountScreen + ViewModel)                   [parallel with B, C, E]
Agent E: Task 8 (Charts) + Task 9 (WorkoutScreen)            [parallel with B, C, D]
    ↓
Agent F: Task 6 (NavGraph + Strings) — after B, C, D done
    ↓
Agent G: Task 7 (SetupScreen) + Task 10 (Docs) + Task 11 (Final build)
```
