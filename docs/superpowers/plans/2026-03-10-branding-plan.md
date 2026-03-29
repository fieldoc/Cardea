# Branding Implementation Plan

> **Note (2026-03-29):** Gradient usage guidance in this plan (gradient on all nav icons + all recording indicators) has been refined. Cardea now uses a 3-tier visual hierarchy — gradient is reserved for Tier 1 elements. Active nav icons still use gradient; other indicators follow the tier system. See `docs/plans/2026-03-02-cardea-ui-ux-design.md` Section 2.3.

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current CardeaLogo (geometric heart + ECG + orbit ring) with a premium "running route" ribbon heart, update the splash screen copy and animation, and install a real Cardea launcher icon.

**Architecture:** Three independent file areas — the Compose Canvas logo component, the splash screen layout, and the Android launcher icon resources. The logo component is the core asset; splash and launcher both derive from it. All changes are purely presentational — no ViewModels, DAOs, or business logic are touched.

**Spec:** `docs/superpowers/specs/2026-03-10-branding-design.md`

**Tech Stack:** Kotlin, Jetpack Compose Canvas, `PathMeasure`, `PathEffect.dashPathEffect`, `BlurMaskFilter` (glow), Android Vector Drawables (adaptive icon)

---

## Chunk 1: CardeaLogo Component

### Task 1: Route-heart path geometry

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/components/CardeaLogo.kt`

The new logo is two `Path` objects:
- **Outer heart** — smooth rounded bezier heart, stroke only, no fill
- **Hairpin** — smaller path that enters from the apex, loops inside the left lobe like a road hairpin turn, exits back toward center
- Both use the same gradient brush and stroke width (~8% of size)
- At sizes < 40dp: only the outer heart is drawn

- [ ] **Step 1: Replace the file contents with the new skeleton**

Replace `CardeaLogo.kt` with:

```kotlin
package com.hrcoach.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed

private val DrawOnEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

@Composable
fun CardeaLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    animate: Boolean = true
) {
    // Hoist constant gradient stops — identical every frame
    val gradientColorStops = remember {
        arrayOf(
            0.00f to GradientRed,
            0.35f to GradientPink,
            0.65f to GradientBlue,
            1.00f to GradientCyan
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "logoAnim")

    // Draw-on phase: 0→1 over 1800ms with ease-in-out, then restarts
    val drawProgress by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = DrawOnEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "drawProgress"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    // Glow pulse — 2500ms cycle, spec range 0.18→0.36
    val glowAlpha by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.18f,
            targetValue = 0.36f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowPulse"
        )
    } else {
        remember { mutableStateOf(0.18f) }
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        val gradient = Brush.linearGradient(
            colorStops = gradientColorStops,
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        val strokeW = w * 0.082f
        val showHairpin = size >= 40.dp

        val outerHeart = buildOuterHeartPath(w, h)
        val hairpin = if (showHairpin) buildHairpinPath(w, h) else null

        // Measure path lengths for draw-on animation
        val outerLength = PathMeasure().apply { setPath(outerHeart, false) }.length
        val hairpinLength = hairpin?.let {
            PathMeasure().apply { setPath(it, false) }.length
        } ?: 0f
        val totalLength = outerLength + hairpinLength

        // ── Glow layer — BlurMaskFilter soft bloom, drawn underneath ──
        val blurRadius = w * 0.15f
        drawIntoCanvas { canvas ->
            val glowPaint = Paint().apply {
                asFrameworkPaint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = strokeW * 2.2f
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                    alpha = (glowAlpha * 255).toInt()
                    shader = android.graphics.LinearGradient(
                        0f, 0f, w, h,
                        intArrayOf(
                            GradientRed.toArgb(), GradientPink.toArgb(),
                            GradientBlue.toArgb(), GradientCyan.toArgb()
                        ),
                        floatArrayOf(0f, 0.35f, 0.65f, 1f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
            }
            canvas.drawPath(outerHeart, glowPaint)
            hairpin?.let { canvas.drawPath(it, glowPaint) }
        }

        // ── Stroke layer — dash-phase draw-on animation ─────────
        // Leading dot: always show a tiny visible segment at the tip so frame-0 is not blank
        val leadingDot = strokeW * 2f
        val drawnSoFar = (drawProgress * totalLength).coerceAtLeast(leadingDot)

        val outerDrawn = drawnSoFar.coerceAtMost(outerLength)
        val outerEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(outerDrawn, outerLength),
            phase = 0f
        )

        val hairpinDrawn = (drawnSoFar - outerLength).coerceAtLeast(0f)
            .coerceAtMost(hairpinLength.coerceAtLeast(0.01f))
        val hairpinEffect = hairpin?.let {
            PathEffect.dashPathEffect(
                intervals = floatArrayOf(hairpinDrawn.coerceAtLeast(0.01f), hairpinLength),
                phase = 0f
            )
        }

        val baseStroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

        drawPath(
            outerHeart,
            brush = gradient,
            style = if (animate)
                Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = outerEffect)
            else baseStroke
        )
        hairpin?.let {
            drawPath(
                it,
                brush = gradient,
                style = if (animate && hairpinEffect != null)
                    Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = hairpinEffect)
                else baseStroke
            )
        }
    }
}
```

- [ ] **Step 2: Add `buildOuterHeartPath` and `buildHairpinPath` private functions below the composable**

```kotlin
/**
 * Smooth rounded heart — two symmetric bezier lobes meeting at the bottom point.
 * All coordinates are expressed as fractions of [w] × [h].
 */
private fun buildOuterHeartPath(w: Float, h: Float): Path = Path().apply {
    // Start at bottom tip
    moveTo(w * 0.50f, h * 0.90f)
    // Left lobe: bottom tip → left side → apex
    cubicTo(
        w * 0.50f, h * 0.90f,
        w * 0.08f, h * 0.62f,
        w * 0.08f, h * 0.38f
    )
    // Left lobe top arc
    cubicTo(
        w * 0.08f, h * 0.14f,
        w * 0.28f, h * 0.06f,
        w * 0.50f, h * 0.30f
    )
    // Right lobe top arc
    cubicTo(
        w * 0.72f, h * 0.06f,
        w * 0.92f, h * 0.14f,
        w * 0.92f, h * 0.38f
    )
    // Right side → bottom tip
    cubicTo(
        w * 0.92f, h * 0.62f,
        w * 0.50f, h * 0.90f,
        w * 0.50f, h * 0.90f
    )
    close()
}

/**
 * Hairpin loop inside the left lobe — the "road crossover" that gives the
 * logo its running-route identity. Starts near the apex, loops around the
 * inside of the left lobe, exits back toward center.
 */
private fun buildHairpinPath(w: Float, h: Float): Path = Path().apply {
    moveTo(w * 0.50f, h * 0.30f)
    cubicTo(
        w * 0.42f, h * 0.18f,
        w * 0.22f, h * 0.14f,
        w * 0.20f, h * 0.30f
    )
    cubicTo(
        w * 0.18f, h * 0.44f,
        w * 0.32f, h * 0.50f,
        w * 0.44f, h * 0.42f
    )
}
```

- [ ] **Step 3: Compile — check for errors**

```
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL. If any import errors, verify the gradient color names match `Color.kt` (`GradientRed`, `GradientPink`, `GradientBlue`, `GradientCyan`).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/components/CardeaLogo.kt
git commit -m "feat(logo): route-heart shape — outer heart + hairpin paths, draw-on animation"
```

---

### Task 2: Verify logo renders correctly at key sizes

The logo renders at three sizes in the app. Visual spot-check each one.

**Files:**
- Read-only: `app/src/main/java/com/hrcoach/ui/splash/SplashScreen.kt` (110dp, animate=true)
- Read-only: `app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt` (28dp, animate=false)
- Read-only: `app/src/main/java/com/hrcoach/ui/account/AccountScreen.kt` (check for CardeaLogo usage)

- [ ] **Step 1: Confirm sizes used across the app**

```bash
grep -rn "CardeaLogo" app/src/main/java/com/hrcoach/
```

Expected output: usages in `SplashScreen.kt`, `HomeScreen.kt`, and possibly `AccountScreen.kt`.

- [ ] **Step 2: Confirm the 28dp usage passes `animate=false`**

Read `HomeScreen.kt` around the `CardeaLogo` call. Confirm `animate = false` is set. If it uses the default (`animate = true`), change it to `animate = false` — the avatar in the header should not animate.

- [ ] **Step 3: Commit any animate=false fixes**

```bash
git add app/src/main/java/com/hrcoach/ui/home/HomeScreen.kt
git commit -m "fix(home): logo avatar uses animate=false — header should be static"
```

(Skip this commit if no change was needed.)

---

## Chunk 2: Splash Screen

### Task 3: Update splash layout and tagline

**Files:**
- Modify: `app/src/main/java/com/hrcoach/ui/splash/SplashScreen.kt`

Changes:
1. Tagline: `"PERFORMANCE ANALYTICS"` → `"HEART-LED PERFORMANCE"`
2. Remove the secondary ambient glow `Box` (the `Brush.radialGradient` background box inside the outer `Box`) — the logo's own glow is sufficient
3. Stagger the wordmark/tagline fade-up: two separate `animateFloatAsState` values with a 200ms offset
4. Duration: bump `onFinished` delay to `2400L` ms to allow draw-on animation to complete before navigating away

- [ ] **Step 1: Read the current file**

Read `app/src/main/java/com/hrcoach/ui/splash/SplashScreen.kt` in full to locate exact line numbers for each change.

- [ ] **Step 2: Replace the tagline string**

Find: `"PERFORMANCE ANALYTICS"`
Replace with: `"HEART-LED PERFORMANCE"`

- [ ] **Step 3: Remove the ambient glow Box**

There are two `Box` composables in `SplashScreen`:
- **Outer Box** — `modifier = Modifier.fillMaxSize().background(CardeaBgPrimary)` — this is the screen background. **Keep this.**
- **Inner Box** (child of the outer Box) — `modifier = Modifier.fillMaxSize().background(Brush.radialGradient(...CardeaBgSecondary.copy(alpha = 0.4f)...Color.Transparent...))` — this is the ambient glow overlay. **Remove this inner Box entirely.**

The `Column` with the logo and text is also a direct child of the outer Box — leave it alone.

- [ ] **Step 4: Add staggered text reveal**

Replace the single `contentAlpha` animation with two separate alphas:

```kotlin
val wordmarkAlpha by animateFloatAsState(
    targetValue = 1f,
    animationSpec = tween(700, delayMillis = 300, easing = LinearOutSlowInEasing),
    label = "wordmark"
)
val taglineAlpha by animateFloatAsState(
    targetValue = 1f,
    animationSpec = tween(700, delayMillis = 500, easing = LinearOutSlowInEasing),
    label = "tagline"
)
```

Apply `wordmarkAlpha` to the "CARDEA" `Text` via `.graphicsLayer { alpha = wordmarkAlpha }` and `taglineAlpha` to the tagline `Text`.

- [ ] **Step 5: Update onFinished delay**

Find: `delay(2200L)`
Replace with: `delay(2400L)`

- [ ] **Step 6: Compile**

```
.\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hrcoach/ui/splash/SplashScreen.kt
git commit -m "feat(splash): Heart-Led Performance tagline, staggered reveal, remove ambient glow"
```

---

## Chunk 3: Launcher Icon

### Task 4: Create launcher icon vector drawables

**Files:**
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Modify: `app/src/main/AndroidManifest.xml`

The launcher uses Android Adaptive Icons (API 26+). The foreground vector contains the route-heart logo; the background is the Cardea dark color. The system clips it to a circle.

- [ ] **Step 1: Create the res directories**

```bash
mkdir -p app/src/main/res/drawable
mkdir -p app/src/main/res/mipmap-anydpi-v26
```

- [ ] **Step 2: Create `ic_launcher_background.xml`**

Create `app/src/main/res/drawable/ic_launcher_background.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#0D0D14" />
</shape>
```

- [ ] **Step 3: Create `ic_launcher_foreground.xml`**

The Android vector canvas for adaptive icon foreground is 108×108dp, with a 72dp safe zone centered. The logo heart is sized to fit within the safe zone (~66dp).

Create `app/src/main/res/drawable/ic_launcher_foreground.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">

    <!-- Outer heart path — centered in safe zone (18dp inset on each side) -->
    <path
        android:pathData="M54,90 C54,90 14,66 14,42 C14,26 26,16 38,22 C44,26 50,34 54,42 C58,34 64,26 70,22 C82,16 94,26 94,42 C94,66 54,90 54,90 Z"
        android:fillColor="@android:color/transparent"
        android:strokeWidth="7"
        android:strokeLineCap="round"
        android:strokeLineJoin="round">
        <aapt:attr name="android:strokeColor">
            <gradient
                android:type="linear"
                android:startX="14"
                android:startY="16"
                android:endX="94"
                android:endY="90">
                <item android:offset="0.00" android:color="#FFFF4D5A"/>
                <item android:offset="0.35" android:color="#FFFF2DA6"/>
                <item android:offset="0.65" android:color="#FF4D61FF"/>
                <item android:offset="1.00" android:color="#FF00E5FF"/>
            </gradient>
        </aapt:attr>
    </path>

    <!-- Hairpin path — left lobe inner road edge -->
    <path
        android:pathData="M54,42 C48,28 30,20 26,34 C22,46 34,56 44,50 C50,46 54,42 54,42"
        android:fillColor="@android:color/transparent"
        android:strokeWidth="7"
        android:strokeLineCap="round"
        android:strokeLineJoin="round">
        <aapt:attr name="android:strokeColor">
            <gradient
                android:type="linear"
                android:startX="14"
                android:startY="16"
                android:endX="94"
                android:endY="90">
                <item android:offset="0.00" android:color="#FFFF4D5A"/>
                <item android:offset="0.35" android:color="#FFFF2DA6"/>
                <item android:offset="0.65" android:color="#FF4D61FF"/>
                <item android:offset="1.00" android:color="#FF00E5FF"/>
            </gradient>
        </aapt:attr>
    </path>

    <!-- Subtle glow ring — very low alpha bloom behind the heart -->
    <path
        android:pathData="M54,90 C54,90 14,66 14,42 C14,26 26,16 38,22 C44,26 50,34 54,42 C58,34 64,26 70,22 C82,16 94,26 94,42 C94,66 54,90 54,90 Z"
        android:fillColor="@android:color/transparent"
        android:strokeWidth="14"
        android:strokeAlpha="0.18"
        android:strokeLineCap="round"
        android:strokeLineJoin="round">
        <aapt:attr name="android:strokeColor">
            <gradient
                android:type="linear"
                android:startX="14"
                android:startY="16"
                android:endX="94"
                android:endY="90">
                <item android:offset="0.00" android:color="#FFFF4D5A"/>
                <item android:offset="0.35" android:color="#FFFF2DA6"/>
                <item android:offset="0.65" android:color="#FF4D61FF"/>
                <item android:offset="1.00" android:color="#FF00E5FF"/>
            </gradient>
        </aapt:attr>
    </path>

</vector>
```

- [ ] **Step 4: Create `mipmap-anydpi-v26/ic_launcher.xml`**

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

- [ ] **Step 5: Create `mipmap-anydpi-v26/ic_launcher_round.xml`**

Create `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

(Same as `ic_launcher.xml` — the circle shape is applied by the Android launcher, not by the XML.)

- [ ] **Step 6: Update AndroidManifest.xml**

First, read the manifest to find the current icon attribute:
```bash
grep -n "icon" app/src/main/AndroidManifest.xml
```

Then in `app/src/main/AndroidManifest.xml`, find the `android:icon` attribute on the `<application>` element (whatever value it currently holds) and replace it. The entire `<application>` opening tag should include:
```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

If `android:roundIcon` is already present, update it. If not, add it alongside `android:icon`.

- [ ] **Step 7: Compile**

```
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. If the build fails with a resource error about `aapt:attr` gradient in the vector, ensure `compileSdk` is 26+ (it's 35 per CLAUDE.md — this is fine).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/drawable/ic_launcher_background.xml
git add app/src/main/res/drawable/ic_launcher_foreground.xml
git add app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
git add app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
git add app/src/main/AndroidManifest.xml
git commit -m "feat(launcher): Cardea route-heart adaptive icon — gradient heart on dark background"
```

---

### Task 5: Legacy PNG launcher icons (pre-API-26 fallback)

Android devices below API 26 cannot use adaptive icons — they fall back to `mipmap-*/ic_launcher.png`. We generate simple flat PNGs from the foreground vector at standard density buckets.

**Files:**
- Create: `app/src/main/res/mipmap-mdpi/ic_launcher.png` (48×48px)
- Create: `app/src/main/res/mipmap-hdpi/ic_launcher.png` (72×72px)
- Create: `app/src/main/res/mipmap-xhdpi/ic_launcher.png` (96×96px)
- Create: `app/src/main/res/mipmap-xxhdpi/ic_launcher.png` (144×144px)
- Create: `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` (192×192px)

> **Note:** Android Studio has a built-in tool (Image Asset Studio) that generates all density buckets automatically from the adaptive icon XML. This is the recommended approach and saves manual work. However if running headlessly, the steps below produce equivalent results via a script.

- [ ] **Step 1: Create the mipmap density directories**

```bash
mkdir -p app/src/main/res/mipmap-mdpi
mkdir -p app/src/main/res/mipmap-hdpi
mkdir -p app/src/main/res/mipmap-xhdpi
mkdir -p app/src/main/res/mipmap-xxhdpi
mkdir -p app/src/main/res/mipmap-xxxhdpi
```

- [ ] **Step 2: Use Android Studio Image Asset Studio (preferred)**

In Android Studio: right-click `res/` → New → Image Asset → Icon Type: Launcher Icons (Adaptive and Legacy) → Source Asset: select `ic_launcher_foreground.xml` → set background to `#0D0D14` → Generate. This produces all PNG density buckets automatically.

**If Android Studio is not available (headless/CI):** Skip this task. Devices below API 26 will fall back to the system default icon, which is acceptable for a development build. Add this task back when preparing a release build.

- [ ] **Step 3: Compile and confirm no resource conflicts**

```
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. If there are density-bucket PNG errors, confirm files were placed in the correct `mipmap-*` directories.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/mipmap-mdpi/ app/src/main/res/mipmap-hdpi/
git add app/src/main/res/mipmap-xhdpi/ app/src/main/res/mipmap-xxhdpi/
git add app/src/main/res/mipmap-xxxhdpi/
git commit -m "feat(launcher): add legacy PNG icons for pre-API-26 devices"
```

---

## Final Verification

- [ ] Run full unit test suite — confirm no regressions

```
.\gradlew.bat :app:testDebugUnitTest
```

Expected: all existing tests pass. (No new unit tests needed — these are purely visual Canvas/XML changes with no testable logic.)

- [ ] Build a debug APK and install on device or emulator

```
.\gradlew.bat :app:assembleDebug
```

Visually verify:
1. App icon on launcher is the gradient heart on dark background, clipped to circle
2. Splash screen shows route drawing in, wordmark fades up, tagline "HEART-LED PERFORMANCE" fades up with slight delay
3. Home header avatar (28dp) shows clean heart outline, no hairpin, no animation
4. Account screen (if CardeaLogo is used there) shows logo correctly
