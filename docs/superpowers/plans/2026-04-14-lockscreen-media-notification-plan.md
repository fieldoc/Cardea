# Lockscreen Media Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current single-line workout notification with a Spotify-style MediaStyle notification showing HR, target HR, elapsed/total time, zone state, progress bar, and an inline pause/resume action — all visible on the lockscreen without unlocking.

**Architecture:** A new `BadgeBitmapRenderer` draws the Cardea gradient HR badge to a `Bitmap` via Android `Canvas` + `LinearGradient`. `WorkoutNotificationHelper` is rewritten to use `NotificationCompat.MediaStyle` with a `MediaSessionCompat` token, large icon = the rendered badge bitmap, pause/resume as a notification action PendingIntent targeting the existing `ACTION_PAUSE` / `ACTION_RESUME` service handlers. `WorkoutForegroundService` builds a `NotifPayload` each tick from its `WorkoutSnapshot` + cached `WorkoutConfig` total duration, and owns the `MediaSessionCompat` lifecycle.

**Tech Stack:** Kotlin, Android `NotificationCompat.MediaStyle`, `androidx.media:media:1.7.0`, `android.graphics.Canvas` + `LinearGradient` + `Paint` for bitmap rendering, JUnit 4 for unit tests.

**Design spec:** `docs/superpowers/specs/2026-04-14-lockscreen-media-notification-design.md`
**Visual reference:** `.superpowers/brainstorm/816-1776227880/content/approaches-v5.html`

---

## File Structure

### New files

| Path                                                                                   | Responsibility                                            |
|----------------------------------------------------------------------------------------|-----------------------------------------------------------|
| `app/src/main/java/com/hrcoach/service/workout/notification/NotifPayload.kt`           | Immutable data class passed from service → helper         |
| `app/src/main/java/com/hrcoach/service/workout/notification/NotifContentFormatter.kt`  | Pure functions: title / subtitle / delta label formatters |
| `app/src/main/java/com/hrcoach/service/workout/notification/BadgeBitmapRenderer.kt`    | Draws gradient HR badge to Bitmap via Canvas              |
| `app/src/main/java/com/hrcoach/service/workout/notification/BadgeBitmapCache.kt`       | LRU cache (max 16) wrapping the renderer                  |
| `app/src/test/java/com/hrcoach/service/workout/notification/NotifContentFormatterTest.kt` | Unit tests for formatter                               |
| `app/src/test/java/com/hrcoach/service/workout/notification/BadgeBitmapCacheTest.kt`   | Cache hit/miss tests using a fake renderer                |

### Modified files

| Path                                                                          | Change                                                                 |
|-------------------------------------------------------------------------------|------------------------------------------------------------------------|
| `app/build.gradle.kts`                                                        | Add `androidx.media:media:1.7.0` dependency                            |
| `app/src/main/java/com/hrcoach/service/workout/WorkoutNotificationHelper.kt`  | Major rewrite: MediaStyle, MediaSession, bitmap rendering, actions     |
| `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`           | Build `NotifPayload` each tick, own `MediaSessionCompat`, cache total  |

### Files NOT modified

- `WorkoutState.kt` — existing snapshot is sufficient
- `AlertPolicy`, `CoachingEventRouter`, `CoachingAudioManager` — unrelated
- Compose UI — notification is purely system-level

---

## Task 1: Add androidx.media Gradle dependency

**Files:**
- Modify: `app/build.gradle.kts` (dependencies block)

- [ ] **Step 1: Read the current dependencies block**

Run: use the `Read` tool on `app/build.gradle.kts`, locate the `dependencies { ... }` block.

- [ ] **Step 2: Add the androidx.media dependency**

Add this line inside the `dependencies { ... }` block, alongside the other `implementation(...)` lines:

```kotlin
    implementation("androidx.media:media:1.7.0")
```

(If the block already contains `androidx.media:media` at any version, leave it alone and note the version in the commit message.)

- [ ] **Step 3: Sync and build**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep "androidx.media:media"`
Expected: output contains `androidx.media:media:1.7.0`

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add androidx.media:media:1.7.0 for MediaStyle notification

Needed for MediaSessionCompat + NotificationCompat.MediaStyle in the
upcoming lockscreen workout notification."
```

---

## Task 2: Create NotifPayload data class

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/workout/notification/NotifPayload.kt`

- [ ] **Step 1: Create the file**

Path: `app/src/main/java/com/hrcoach/service/workout/notification/NotifPayload.kt`

```kotlin
package com.hrcoach.service.workout.notification

import com.hrcoach.domain.model.ZoneStatus

/**
 * All data needed to render one frame of the workout notification.
 *
 * Built once per processTick by WorkoutForegroundService and passed to
 * WorkoutNotificationHelper.update(payload). Immutable so the helper can
 * compare against the previous payload to avoid redundant renders.
 */
data class NotifPayload(
    /** e.g. "Aerobic Tempo · Target 145", or "Free Run", or "Get HR signal…" */
    val titleText: String,
    /** e.g. "18:30 / 45:00 · +13 BPM", or "18:30 / ∞ · ON TARGET" */
    val subtitleText: String,
    /** Current heart rate in bpm. 0 when no HR signal yet. */
    val currentHr: Int,
    /** Drives the badge gradient and rim accent. */
    val zoneStatus: ZoneStatus,
    /** Seconds since workout start (pauses subtracted). Used for MediaSession position + progress bar. */
    val elapsedSeconds: Long,
    /** Total planned duration in seconds. 0 for free run / unknown. */
    val totalSeconds: Long,
    /** True when workout is manually paused OR auto-paused. Drives action button + badge dimming. */
    val isPaused: Boolean,
    /** True when totalSeconds is unknown — progress bar is rendered indeterminate. */
    val isIndeterminate: Boolean,
)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (or at least no errors in the new file)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/workout/notification/NotifPayload.kt
git commit -m "feat(notification): add NotifPayload data class

Immutable payload built once per tick and passed from the foreground
service to the notification helper. Carries everything the MediaStyle
notification needs to render one frame."
```

---

## Task 3: Create NotifContentFormatter (TDD)

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/workout/notification/NotifContentFormatter.kt`
- Create: `app/src/test/java/com/hrcoach/service/workout/notification/NotifContentFormatterTest.kt`

Pure static formatting functions. Input: `WorkoutSnapshot` + `WorkoutConfig` + precomputed `totalSeconds`. Output: `NotifPayload`. No Android dependencies so tests run under plain JUnit.

- [ ] **Step 1: Write the failing test file**

Path: `app/src/test/java/com/hrcoach/service/workout/notification/NotifContentFormatterTest.kt`

```kotlin
package com.hrcoach.service.workout.notification

import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.WorkoutMode
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.service.WorkoutSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifContentFormatterTest {

    private fun snapshot(
        currentHr: Int = 152,
        targetHr: Int = 145,
        zoneStatus: ZoneStatus = ZoneStatus.ABOVE,
        elapsedSeconds: Long = 1110L, // 18:30
        isRunning: Boolean = true,
        isPaused: Boolean = false,
        isAutoPaused: Boolean = false,
        isFreeRun: Boolean = false,
        countdownSecondsRemaining: Int? = null,
    ) = WorkoutSnapshot(
        isRunning = isRunning,
        isPaused = isPaused,
        currentHr = currentHr,
        targetHr = targetHr,
        zoneStatus = zoneStatus,
        hrConnected = currentHr > 0,
        isFreeRun = isFreeRun,
        isAutoPaused = isAutoPaused,
        countdownSecondsRemaining = countdownSecondsRemaining,
        elapsedSeconds = elapsedSeconds,
    )

    private fun config(
        sessionLabel: String? = "Aerobic Tempo",
        mode: WorkoutMode = WorkoutMode.DISTANCE_PROFILE,
    ) = WorkoutConfig(mode = mode, sessionLabel = sessionLabel)

    // ----- Title -----

    @Test fun `title uses session label when present`() {
        val p = NotifContentFormatter.format(snapshot(), config(), totalSeconds = 2700L)
        assertEquals("Aerobic Tempo · Target 145", p.titleText)
    }

    @Test fun `title falls back to 'Workout' when sessionLabel is null`() {
        val p = NotifContentFormatter.format(
            snapshot(), config(sessionLabel = null), totalSeconds = 2700L
        )
        assertEquals("Workout · Target 145", p.titleText)
    }

    @Test fun `title is 'Free Run' for free runs`() {
        val p = NotifContentFormatter.format(
            snapshot(isFreeRun = true), config(sessionLabel = null), totalSeconds = 0L
        )
        assertEquals("Free Run", p.titleText)
    }

    @Test fun `title is 'Get HR signal…' when NO_DATA`() {
        val p = NotifContentFormatter.format(
            snapshot(zoneStatus = ZoneStatus.NO_DATA, currentHr = 0),
            config(),
            totalSeconds = 2700L
        )
        assertEquals("Get HR signal…", p.titleText)
    }

    @Test fun `title shows 'Paused' suffix when manually paused`() {
        val p = NotifContentFormatter.format(
            snapshot(isPaused = true), config(), totalSeconds = 2700L
        )
        assertEquals("Aerobic Tempo · Paused", p.titleText)
    }

    @Test fun `title shows 'Auto-paused' suffix when auto-paused`() {
        val p = NotifContentFormatter.format(
            snapshot(isAutoPaused = true), config(), totalSeconds = 2700L
        )
        assertEquals("Aerobic Tempo · Auto-paused", p.titleText)
    }

    @Test fun `title shows countdown during startup`() {
        val p = NotifContentFormatter.format(
            snapshot(countdownSecondsRemaining = 3), config(), totalSeconds = 2700L
        )
        assertEquals("Starting in 3…", p.titleText)
    }

    // ----- Subtitle -----

    @Test fun `subtitle shows elapsed slash total with positive delta when ABOVE`() {
        val p = NotifContentFormatter.format(
            snapshot(currentHr = 158, targetHr = 145, zoneStatus = ZoneStatus.ABOVE),
            config(), totalSeconds = 2700L
        )
        assertEquals("18:30 / 45:00 · +13 BPM", p.subtitleText)
    }

    @Test fun `subtitle shows negative delta when BELOW`() {
        val p = NotifContentFormatter.format(
            snapshot(currentHr = 138, targetHr = 145, zoneStatus = ZoneStatus.BELOW),
            config(), totalSeconds = 2700L
        )
        assertEquals("18:30 / 45:00 · -7 BPM", p.subtitleText)
    }

    @Test fun `subtitle shows 'ON TARGET' when IN_ZONE`() {
        val p = NotifContentFormatter.format(
            snapshot(currentHr = 145, targetHr = 145, zoneStatus = ZoneStatus.IN_ZONE),
            config(), totalSeconds = 2700L
        )
        assertEquals("18:30 / 45:00 · ON TARGET", p.subtitleText)
    }

    @Test fun `subtitle shows em-dash when NO_DATA`() {
        val p = NotifContentFormatter.format(
            snapshot(currentHr = 0, zoneStatus = ZoneStatus.NO_DATA),
            config(), totalSeconds = 2700L
        )
        assertEquals("18:30 / 45:00 · —", p.subtitleText)
    }

    @Test fun `subtitle uses infinity symbol when free run`() {
        val p = NotifContentFormatter.format(
            snapshot(isFreeRun = true, zoneStatus = ZoneStatus.NO_DATA, currentHr = 0),
            config(sessionLabel = null), totalSeconds = 0L
        )
        assertEquals("18:30 / ∞ · —", p.subtitleText)
    }

    @Test fun `subtitle handles zero elapsed`() {
        val p = NotifContentFormatter.format(
            snapshot(elapsedSeconds = 0L, currentHr = 0, zoneStatus = ZoneStatus.NO_DATA),
            config(), totalSeconds = 2700L
        )
        assertEquals("00:00 / 45:00 · —", p.subtitleText)
    }

    @Test fun `subtitle handles overtime (elapsed greater than total)`() {
        val p = NotifContentFormatter.format(
            snapshot(elapsedSeconds = 3000L, currentHr = 145, zoneStatus = ZoneStatus.IN_ZONE),
            config(), totalSeconds = 2700L
        )
        assertEquals("50:00 / 45:00 · ON TARGET", p.subtitleText)
    }

    // ----- Payload flags -----

    @Test fun `payload is indeterminate when totalSeconds is zero`() {
        val p = NotifContentFormatter.format(
            snapshot(isFreeRun = true), config(sessionLabel = null), totalSeconds = 0L
        )
        assertTrue(p.isIndeterminate)
    }

    @Test fun `payload is determinate when totalSeconds greater than zero`() {
        val p = NotifContentFormatter.format(snapshot(), config(), totalSeconds = 2700L)
        assertFalse(p.isIndeterminate)
    }

    @Test fun `payload isPaused is true when snapshot isPaused`() {
        val p = NotifContentFormatter.format(
            snapshot(isPaused = true), config(), totalSeconds = 2700L
        )
        assertTrue(p.isPaused)
    }

    @Test fun `payload isPaused is true when auto-paused`() {
        val p = NotifContentFormatter.format(
            snapshot(isAutoPaused = true), config(), totalSeconds = 2700L
        )
        assertTrue(p.isPaused)
    }

    @Test fun `payload carries elapsed and total seconds through unchanged`() {
        val p = NotifContentFormatter.format(
            snapshot(elapsedSeconds = 1110L), config(), totalSeconds = 2700L
        )
        assertEquals(1110L, p.elapsedSeconds)
        assertEquals(2700L, p.totalSeconds)
    }
}
```

- [ ] **Step 2: Run the test — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hrcoach.service.workout.notification.NotifContentFormatterTest"`
Expected: compilation failure — `NotifContentFormatter` does not exist yet.

- [ ] **Step 3: Create the formatter**

Path: `app/src/main/java/com/hrcoach/service/workout/notification/NotifContentFormatter.kt`

```kotlin
package com.hrcoach.service.workout.notification

import com.hrcoach.domain.model.WorkoutConfig
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.service.WorkoutSnapshot
import kotlin.math.abs

/**
 * Pure functions that convert a WorkoutSnapshot + WorkoutConfig into
 * a NotifPayload. No Android dependencies — fully unit-testable.
 */
object NotifContentFormatter {

    fun format(
        snapshot: WorkoutSnapshot,
        config: WorkoutConfig,
        totalSeconds: Long,
    ): NotifPayload {
        val paused = snapshot.isPaused || snapshot.isAutoPaused
        val indeterminate = totalSeconds <= 0L
        return NotifPayload(
            titleText = buildTitle(snapshot, config),
            subtitleText = buildSubtitle(snapshot, totalSeconds),
            currentHr = snapshot.currentHr,
            zoneStatus = snapshot.zoneStatus,
            elapsedSeconds = snapshot.elapsedSeconds,
            totalSeconds = totalSeconds,
            isPaused = paused,
            isIndeterminate = indeterminate,
        )
    }

    private fun buildTitle(snapshot: WorkoutSnapshot, config: WorkoutConfig): String {
        // Countdown phase wins over everything else
        val countdown = snapshot.countdownSecondsRemaining
        if (countdown != null && countdown > 0) return "Starting in $countdown…"

        if (snapshot.isFreeRun) return "Free Run"

        val label = config.sessionLabel?.takeIf { it.isNotBlank() } ?: "Workout"

        if (snapshot.isAutoPaused) return "$label · Auto-paused"
        if (snapshot.isPaused) return "$label · Paused"

        // NO_DATA (no HR signal yet) — prompt runner to check the strap
        if (snapshot.zoneStatus == ZoneStatus.NO_DATA || snapshot.currentHr <= 0) {
            return "Get HR signal…"
        }

        return "$label · Target ${snapshot.targetHr}"
    }

    private fun buildSubtitle(snapshot: WorkoutSnapshot, totalSeconds: Long): String {
        val elapsedLabel = formatMinSec(snapshot.elapsedSeconds)
        val totalLabel = if (totalSeconds <= 0L) "∞" else formatMinSec(totalSeconds)
        val deltaLabel = buildDeltaLabel(snapshot)
        return "$elapsedLabel / $totalLabel · $deltaLabel"
    }

    private fun buildDeltaLabel(snapshot: WorkoutSnapshot): String = when (snapshot.zoneStatus) {
        ZoneStatus.IN_ZONE -> "ON TARGET"
        ZoneStatus.ABOVE -> {
            val delta = snapshot.currentHr - snapshot.targetHr
            "+${abs(delta)} BPM"
        }
        ZoneStatus.BELOW -> {
            val delta = snapshot.targetHr - snapshot.currentHr
            "-${abs(delta)} BPM"
        }
        ZoneStatus.NO_DATA -> "—"
    }

    private fun formatMinSec(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0L)
        val mm = s / 60
        val ss = s % 60
        return "%02d:%02d".format(mm, ss)
    }
}
```

- [ ] **Step 4: Run tests again — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hrcoach.service.workout.notification.NotifContentFormatterTest"`
Expected: BUILD SUCCESSFUL, 17 tests passed.

If a test fails, fix the formatter (not the test) until green. The tests are the spec.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/workout/notification/NotifContentFormatter.kt \
        app/src/test/java/com/hrcoach/service/workout/notification/NotifContentFormatterTest.kt
git commit -m "feat(notification): add NotifContentFormatter with full TDD coverage

Pure formatter that builds NotifPayload from WorkoutSnapshot + WorkoutConfig
+ totalSeconds. Handles every title fallback (bootcamp label, free run,
paused, auto-paused, countdown, NO_DATA) and subtitle format (delta sign,
indeterminate ∞, overtime)."
```

---

## Task 4: Create BadgeBitmapRenderer

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/workout/notification/BadgeBitmapRenderer.kt`

This task is **rendering-only, no tests**. Canvas rendering is hard to unit-test meaningfully — we'll verify it via manual visual inspection on a real device in Task 8.

- [ ] **Step 1: Create the renderer file**

Path: `app/src/main/java/com/hrcoach/service/workout/notification/BadgeBitmapRenderer.kt`

```kotlin
package com.hrcoach.service.workout.notification

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.hrcoach.domain.model.ZoneStatus

/**
 * Draws the Cardea gradient HR badge to a square Bitmap.
 *
 * Every non-NO_DATA zone state keeps the full 4-stop Cardea palette
 * (red → pink → blue → cyan). Only stop positions shift — the badge
 * never becomes a solid colour alert object. Rim accent and badge
 * weighting together carry the zone signal through sweat.
 *
 * See docs/superpowers/specs/2026-04-14-lockscreen-media-notification-design.md
 * for the visual spec.
 */
class BadgeBitmapRenderer {

    /** Square bitmap dimension in pixels. 144px matches MediaStyle large-icon sweet spot. */
    private val size = 144

    // Cardea palette
    private val cardeaRed = Color.parseColor("#FF4D5A")
    private val cardeaPink = Color.parseColor("#FF2DA6")
    private val cardeaBlue = Color.parseColor("#4D61FF")
    private val cardeaCyan = Color.parseColor("#00E5FF")

    fun render(
        currentHr: Int,
        zoneStatus: ZoneStatus,
        paused: Boolean,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val cornerRadius = size * 0.14f // ~20px on a 144px bitmap — matches the 14/74 ratio in the mockup
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())

        // Clip to rounded rect so subsequent draws don't bleed past the corners
        val clipPath = Path().apply {
            addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clipPath)

        // 1. Base gradient
        drawGradient(canvas, rect, zoneStatus)

        // 2. Top sheen (subtle white radial highlight from top-centre)
        drawTopSheen(canvas, rect)

        // 3. Bottom shadow (subtle dark radial from bottom-centre)
        drawBottomShadow(canvas, rect)

        // 4. HR number + BPM label
        drawHrText(canvas, rect, currentHr, zoneStatus)

        // 5. Heart glyph top-right
        drawHeartGlyph(canvas, rect, paused)

        // 6. Rim accent (for ABOVE / BELOW only)
        if (!paused) drawRimAccent(canvas, rect, zoneStatus)

        canvas.restore()

        // 7. If paused, apply a global desaturation + dark overlay on top
        if (paused) applyPausedOverlay(canvas, rect, cornerRadius)

        return bmp
    }

    // ---------------------------------------------------------------------
    // Gradient
    // ---------------------------------------------------------------------

    private fun drawGradient(canvas: Canvas, rect: RectF, zone: ZoneStatus) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (zone == ZoneStatus.NO_DATA) {
            // Greyscale fallback
            val grey = LinearGradient(
                0f, 0f, rect.right, rect.bottom,
                intArrayOf(0xFF2A2A30.toInt(), 0xFF3A3A44.toInt(), 0xFF2A2A30.toInt()),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = grey
            canvas.drawRect(rect, paint)
            return
        }

        // 135° diagonal (top-left → bottom-right)
        val colors: IntArray
        val positions: FloatArray
        when (zone) {
            ZoneStatus.BELOW -> {
                // Cyan-dominant; red/pink only peek at the far top-left corner
                colors = intArrayOf(cardeaRed, cardeaPink, cardeaBlue, cardeaCyan, cardeaCyan)
                positions = floatArrayOf(0f, 0.12f, 0.38f, 0.82f, 1f)
            }
            ZoneStatus.IN_ZONE -> {
                colors = intArrayOf(cardeaRed, cardeaPink, cardeaBlue, cardeaCyan)
                positions = floatArrayOf(0f, 0.33f, 0.66f, 1f)
            }
            ZoneStatus.ABOVE -> {
                // Red/pink dominant; blue/cyan still peek at the far bottom-right corner
                colors = intArrayOf(cardeaRed, cardeaRed, cardeaPink, cardeaBlue, cardeaCyan)
                positions = floatArrayOf(0f, 0.18f, 0.50f, 0.85f, 1f)
            }
            else -> {
                // Should be unreachable (NO_DATA handled above)
                colors = intArrayOf(cardeaRed, cardeaPink, cardeaBlue, cardeaCyan)
                positions = floatArrayOf(0f, 0.33f, 0.66f, 1f)
            }
        }

        paint.shader = LinearGradient(
            0f, 0f, rect.right, rect.bottom,
            colors, positions,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
    }

    // ---------------------------------------------------------------------
    // Sheen + shadow
    // ---------------------------------------------------------------------

    private fun drawTopSheen(canvas: Canvas, rect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            rect.centerX(), -rect.height() * 0.1f,
            rect.width() * 0.55f,
            intArrayOf(0x66FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
    }

    private fun drawBottomShadow(canvas: Canvas, rect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            rect.centerX(), rect.bottom + rect.height() * 0.1f,
            rect.width() * 0.4f,
            intArrayOf(0x40000000, 0x00000000),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, paint)
    }

    // ---------------------------------------------------------------------
    // Text
    // ---------------------------------------------------------------------

    private fun drawHrText(canvas: Canvas, rect: RectF, currentHr: Int, zone: ZoneStatus) {
        val displayNum = if (currentHr <= 0 || zone == ZoneStatus.NO_DATA) "—" else currentHr.toString()

        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = rect.height() * 0.38f
            setShadowLayer(2f, 0f, 1f, 0x59000000)
        }
        val cy = rect.centerY() + (numberPaint.textSize / 3f) - rect.height() * 0.04f
        canvas.drawText(displayNum, rect.centerX(), cy, numberPaint)

        // "BPM" label below
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = 0xEBFFFFFF.toInt() // ~92% white
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = rect.height() * 0.095f
            letterSpacing = 0.22f
            setShadowLayer(2f, 0f, 1f, 0x59000000)
        }
        canvas.drawText("BPM", rect.centerX(), cy + rect.height() * 0.16f, labelPaint)
    }

    // ---------------------------------------------------------------------
    // Heart glyph (top-right, ~12% size)
    // ---------------------------------------------------------------------

    private fun drawHeartGlyph(canvas: Canvas, rect: RectF, paused: Boolean) {
        val size = rect.width() * 0.11f
        val cx = rect.right - size * 1.3f
        val cy = rect.top + size * 0.9f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 178 // ~70%
        }

        if (paused) {
            // Replace heart with pause glyph (two small vertical bars)
            val barW = size * 0.22f
            val barH = size * 0.75f
            val gap = size * 0.12f
            val left1 = cx - gap - barW
            val top = cy - barH / 2f
            canvas.drawRect(left1, top, left1 + barW, top + barH, paint)
            canvas.drawRect(cx + gap, top, cx + gap + barW, top + barH, paint)
            return
        }

        // Simple two-lobed heart path
        val path = Path().apply {
            val half = size / 2f
            val topOffset = size * 0.28f
            moveTo(cx, cy + half)
            cubicTo(
                cx - size, cy - topOffset,
                cx - half, cy - size,
                cx, cy - topOffset
            )
            cubicTo(
                cx + half, cy - size,
                cx + size, cy - topOffset,
                cx, cy + half
            )
            close()
        }
        canvas.drawPath(path, paint)
    }

    // ---------------------------------------------------------------------
    // Rim accent
    // ---------------------------------------------------------------------

    private fun drawRimAccent(canvas: Canvas, rect: RectF, zone: ZoneStatus) {
        if (zone != ZoneStatus.ABOVE && zone != ZoneStatus.BELOW) return

        val rimColor = if (zone == ZoneStatus.ABOVE) cardeaPink else cardeaCyan

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = rimColor
            strokeWidth = rect.height() * 0.022f // ~3px on 144
            strokeCap = Paint.Cap.ROUND
            setShadowLayer(rect.height() * 0.07f, 0f, 0f, rimColor)
        }

        val inset = rect.width() * 0.14f
        val y = if (zone == ZoneStatus.ABOVE) rect.top + paint.strokeWidth else rect.bottom - paint.strokeWidth
        canvas.drawLine(rect.left + inset, y, rect.right - inset, y, paint)
    }

    // ---------------------------------------------------------------------
    // Paused overlay — desaturate + darken
    // ---------------------------------------------------------------------

    private fun applyPausedOverlay(canvas: Canvas, rect: RectF, cornerRadius: Float) {
        // Desaturate whole bitmap via a colour matrix overlay — we draw a translucent
        // grey rounded rect on top to dim the gradient without needing to re-render it.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB31A1A1F.toInt() // ~70% #1A1A1F
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If the `cubicTo` / `Path` imports fail, add them explicitly.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/workout/notification/BadgeBitmapRenderer.kt
git commit -m "feat(notification): add BadgeBitmapRenderer

Renders the Cardea gradient HR badge to a 144x144 Bitmap via Canvas
+ LinearGradient. Every non-NO_DATA state keeps all 4 Cardea stops —
only positions shift. Rim accent on top/bottom edge for ABOVE/BELOW.
Paused state applies a dark translucent overlay and swaps the heart
glyph for a pause glyph.

Visual spec: docs/superpowers/specs/2026-04-14-lockscreen-media-notification-design.md"
```

---

## Task 5: Create BadgeBitmapCache with tests

**Files:**
- Create: `app/src/main/java/com/hrcoach/service/workout/notification/BadgeBitmapCache.kt`
- Create: `app/src/test/java/com/hrcoach/service/workout/notification/BadgeBitmapCacheTest.kt`

- [ ] **Step 1: Write the failing test**

Path: `app/src/test/java/com/hrcoach/service/workout/notification/BadgeBitmapCacheTest.kt`

```kotlin
package com.hrcoach.service.workout.notification

import com.hrcoach.domain.model.ZoneStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** Uses a fake "bitmap" (just an int) so the test runs on plain JUnit without Android. */
class BadgeBitmapCacheTest {

    private class FakeRenderer : (Int, ZoneStatus, Boolean) -> Int {
        var calls = 0
            private set
        override fun invoke(hr: Int, zone: ZoneStatus, paused: Boolean): Int {
            calls++
            return hr * 1000 + zone.ordinal * 10 + (if (paused) 1 else 0)
        }
    }

    private fun cache(renderer: FakeRenderer) =
        BadgeBitmapCache<Int>(maxEntries = 4) { hr, zone, paused ->
            renderer(hr, zone, paused)
        }

    @Test fun `first lookup renders and returns`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer)
        val bmp = cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(1, renderer.calls)
        assertEquals(152 * 1000 + ZoneStatus.IN_ZONE.ordinal * 10, bmp)
    }

    @Test fun `identical lookup returns cached bitmap without re-rendering`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer)
        val a = cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        val b = cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(1, renderer.calls)
        assertSame(a, b)
    }

    @Test fun `different hr re-renders`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer)
        val a = cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        val b = cache.get(153, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(2, renderer.calls)
        assertNotSame(a, b)
    }

    @Test fun `different zone re-renders`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer)
        cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        cache.get(152, ZoneStatus.ABOVE, paused = false)
        assertEquals(2, renderer.calls)
    }

    @Test fun `paused and unpaused are different cache entries`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer)
        cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        cache.get(152, ZoneStatus.IN_ZONE, paused = true)
        assertEquals(2, renderer.calls)
    }

    @Test fun `LRU evicts oldest entry when over capacity`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer) // max 4
        cache.get(150, ZoneStatus.IN_ZONE, paused = false)
        cache.get(151, ZoneStatus.IN_ZONE, paused = false)
        cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        cache.get(153, ZoneStatus.IN_ZONE, paused = false)
        // All four render calls
        assertEquals(4, renderer.calls)
        // This should evict 150
        cache.get(154, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(5, renderer.calls)
        // Re-fetching 150 must re-render
        cache.get(150, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(6, renderer.calls)
        // But 154 is still cached
        cache.get(154, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(6, renderer.calls)
    }

    @Test fun `clear empties the cache`() {
        val renderer = FakeRenderer()
        val cache = cache(renderer)
        cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        cache.clear()
        cache.get(152, ZoneStatus.IN_ZONE, paused = false)
        assertEquals(2, renderer.calls)
    }
}
```

- [ ] **Step 2: Run the test — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hrcoach.service.workout.notification.BadgeBitmapCacheTest"`
Expected: compilation failure — `BadgeBitmapCache` does not exist.

- [ ] **Step 3: Write the cache**

Path: `app/src/main/java/com/hrcoach/service/workout/notification/BadgeBitmapCache.kt`

```kotlin
package com.hrcoach.service.workout.notification

import com.hrcoach.domain.model.ZoneStatus

/**
 * LRU cache keyed by (currentHr, zoneStatus, paused) → T.
 *
 * Generic in T so the production type uses T = android.graphics.Bitmap
 * but tests can use T = Int (or any simple type) without needing Android.
 */
class BadgeBitmapCache<T>(
    private val maxEntries: Int = 16,
    private val factory: (Int, ZoneStatus, Boolean) -> T,
) {
    private data class Key(val hr: Int, val zone: ZoneStatus, val paused: Boolean)

    // LinkedHashMap with accessOrder = true gives us LRU semantics on read.
    private val entries = object : LinkedHashMap<Key, T>(
        /* initialCapacity = */ maxEntries + 1,
        /* loadFactor     = */ 0.75f,
        /* accessOrder    = */ true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, T>): Boolean =
            size > maxEntries
    }

    fun get(currentHr: Int, zoneStatus: ZoneStatus, paused: Boolean): T {
        val key = Key(currentHr, zoneStatus, paused)
        entries[key]?.let { return it }
        val created = factory(currentHr, zoneStatus, paused)
        entries[key] = created
        return created
    }

    fun clear() {
        entries.clear()
    }
}
```

- [ ] **Step 4: Run the tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "com.hrcoach.service.workout.notification.BadgeBitmapCacheTest"`
Expected: BUILD SUCCESSFUL, 7 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/workout/notification/BadgeBitmapCache.kt \
        app/src/test/java/com/hrcoach/service/workout/notification/BadgeBitmapCacheTest.kt
git commit -m "feat(notification): add generic LRU BadgeBitmapCache

Generic so the production type uses Bitmap and the tests use Int.
LRU eviction on 16-entry capacity. Clear() for workout-end cleanup."
```

---

## Task 6: Rewrite WorkoutNotificationHelper

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/workout/WorkoutNotificationHelper.kt` (entire file)

Major rewrite. The helper now owns the `BadgeBitmapRenderer` + `BadgeBitmapCache`, accepts a `MediaSessionCompat` token from the service, and exposes two `update` methods: the old `update(text)` for compatibility during startup and a new `update(payload)` for the steady-state ticks.

- [ ] **Step 1: Replace the file content**

Path: `app/src/main/java/com/hrcoach/service/workout/WorkoutNotificationHelper.kt`

```kotlin
package com.hrcoach.service.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.hrcoach.MainActivity
import com.hrcoach.R
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.service.WorkoutForegroundService
import com.hrcoach.service.workout.notification.BadgeBitmapCache
import com.hrcoach.service.workout.notification.BadgeBitmapRenderer
import com.hrcoach.service.workout.notification.NotifPayload

class WorkoutNotificationHelper(
    private val context: Context,
    private val channelId: String,
    private val notificationId: Int,
) {
    @Volatile
    private var stopped = false

    /** Supplied by the service in onCreate. Required for MediaStyle. */
    private var mediaSessionToken: MediaSessionCompat.Token? = null

    private val renderer = BadgeBitmapRenderer()
    private val bitmapCache = BadgeBitmapCache<Bitmap>(maxEntries = 16) { hr, zone, paused ->
        renderer.render(currentHr = hr, zoneStatus = zone, paused = paused)
    }

    fun attachMediaSession(token: MediaSessionCompat.Token) {
        this.mediaSessionToken = token
    }

    /** Startup call — plain text notification, before the first processTick runs. */
    fun startForeground(service: Service, text: String) {
        stopped = false
        createChannelIfNeeded()
        service.startForeground(notificationId, buildPlainNotification(text))
    }

    /** Pre-first-tick transitional updates (e.g. "Starting workout..."). */
    fun update(text: String) {
        if (stopped) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, buildPlainNotification(text))
    }

    /** Steady-state tick — rich MediaStyle notification. */
    fun update(payload: NotifPayload) {
        if (stopped) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, buildRichNotification(payload))
    }

    fun stop() {
        stopped = true
        bitmapCache.clear()
    }

    // ------------------------------------------------------------------
    // Notification builders
    // ------------------------------------------------------------------

    private fun buildPlainNotification(text: String): Notification {
        return baseBuilder()
            .setContentTitle("Cardea")
            .setContentText(text)
            .build()
    }

    private fun buildRichNotification(payload: NotifPayload): Notification {
        val badge = bitmapCache.get(
            currentHr = payload.currentHr,
            zoneStatus = payload.zoneStatus,
            paused = payload.isPaused,
        )

        val builder = baseBuilder()
            .setContentTitle(payload.titleText)
            .setContentText(payload.subtitleText)
            .setLargeIcon(badge)
            .addAction(buildPauseResumeAction(payload.isPaused))

        // Progress bar — indeterminate for free run / unknown total
        val maxProgress = if (payload.isIndeterminate) 0 else payload.totalSeconds.toInt().coerceAtLeast(0)
        val currentProgress = payload.elapsedSeconds.toInt().coerceIn(0, maxProgress.coerceAtLeast(1))
        builder.setProgress(
            maxProgress,
            currentProgress,
            payload.isIndeterminate,
        )

        // Attach MediaStyle if we have a session token
        val token = mediaSessionToken
        if (token != null) {
            val style = MediaStyle()
                .setMediaSession(token)
                .setShowActionsInCompactView(0) // only the pause/resume action
            builder.setStyle(style)
        }

        return builder.build()
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notif_cardea)
            .setColor(ContextCompat.getColor(context, R.color.cardea_notif_accent))
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // show on lockscreen in full
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    private fun buildPauseResumeAction(isPaused: Boolean): NotificationCompat.Action {
        val action: String
        val title: String
        val iconRes: Int
        val requestCode: Int
        if (isPaused) {
            action = WorkoutForegroundService.ACTION_RESUME
            title = "Resume"
            iconRes = android.R.drawable.ic_media_play
            requestCode = REQUEST_RESUME
        } else {
            action = WorkoutForegroundService.ACTION_PAUSE
            title = "Pause"
            iconRes = android.R.drawable.ic_media_pause
            requestCode = REQUEST_PAUSE
        }
        val intent = Intent(context, WorkoutForegroundService::class.java).apply {
            this.action = action
        }
        val pi = PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(iconRes, title, pi).build()
    }

    private fun createChannelIfNeeded() {
        val channel = NotificationChannel(
            channelId,
            "Cardea Workout",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Active workout tracking"
            setShowBadge(false)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val REQUEST_OPEN = 1001
        private const val REQUEST_PAUSE = 1002
        private const val REQUEST_RESUME = 1003
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: may fail with `unresolved reference: WorkoutForegroundService.ACTION_PAUSE` if visibility changes — `ACTION_PAUSE`/`ACTION_RESUME` live in the companion object of `WorkoutForegroundService.kt` and should already be accessible. If the compile fails for another reason, read the error and fix it.

If KSP surfaces "Internal compiler error" about `file-to-id.tab already registered`, rerun with `./gradlew assembleDebug --stacktrace` to see the real error.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/workout/WorkoutNotificationHelper.kt
git commit -m "feat(notification): rewrite WorkoutNotificationHelper with MediaStyle

Replaces the single-line text notification with NotificationCompat.MediaStyle:
- Large icon = BadgeBitmapRenderer output (cached)
- Pause/Resume action PendingIntent targeting the existing ACTION_PAUSE /
  ACTION_RESUME service actions
- Progress bar (indeterminate for free run)
- VISIBILITY_PUBLIC so the lockscreen shows all fields

Keeps the old update(text: String) signature for transitional 'Starting
workout...' messages before the first processTick fires. A new
update(payload: NotifPayload) is the steady-state path."
```

---

## Task 7: Wire WorkoutForegroundService to the new helper

**Files:**
- Modify: `app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt`

The service needs to:
1. Create + release a `MediaSessionCompat`
2. Compute + cache `totalSeconds` when the workout starts
3. Build a `NotifPayload` each tick via `NotifContentFormatter` and call `notificationHelper.update(payload)`
4. Still call the old `update(text: String)` for the three transitional messages ("Starting workout...", "Workout paused", "Workout resumed" — these are pre-first-tick / side paths)

- [ ] **Step 1: Add the MediaSession field and totalSeconds cache**

Near the top of the class (after the existing `private lateinit var notificationHelper:`, around line 119):

Add:

```kotlin
    private lateinit var mediaSession: android.support.v4.media.session.MediaSessionCompat
    private var workoutTotalSeconds: Long = 0L
```

- [ ] **Step 2: Initialise the MediaSession in onCreate**

Find `onCreate()` (around line 160). After `notificationHelper = WorkoutNotificationHelper(this, CHANNEL_ID, NOTIFICATION_ID)`, add:

```kotlin
        mediaSession = android.support.v4.media.session.MediaSessionCompat(this, "CardeaWorkout").apply {
            isActive = true
        }
        notificationHelper.attachMediaSession(mediaSession.sessionToken)
```

- [ ] **Step 3: Release the MediaSession in onDestroy**

If the service already has an `onDestroy()` override, add inside it:

```kotlin
        mediaSession.isActive = false
        mediaSession.release()
```

If `onDestroy()` does not exist, add it:

```kotlin
    override fun onDestroy() {
        super.onDestroy()
        mediaSession.isActive = false
        mediaSession.release()
    }
```

- [ ] **Step 4: Compute totalSeconds when the workout starts**

Find the point inside the service where a `WorkoutConfig` becomes available at workout start (inside the `ACTION_START` handler or `startWorkout(...)` — look for where `workoutConfig` is first assigned, near where `notificationHelper.startForeground(this, "Starting workout...")` is called around line 259).

Right after `workoutConfig` is stored on the service, add:

```kotlin
        workoutTotalSeconds = computeTotalSeconds(workoutConfig)
```

Then add the helper somewhere in the service (near the other private helpers):

```kotlin
    private fun computeTotalSeconds(config: com.hrcoach.domain.model.WorkoutConfig): Long {
        // 1. Explicit planned duration
        config.plannedDurationMinutes?.let { return it.toLong() * 60L }
        // 2. Sum of time-based segment durations
        val segSum = config.segments.sumOf { (it.durationSeconds ?: 0).toLong() }
        if (segSum > 0L) return segSum
        // 3. Unknown — treated as free run / indeterminate progress
        return 0L
    }
```

- [ ] **Step 5: Replace the final processTick notification update**

Find the end of `processTick(...)` around line 564-568 where the code currently reads:

```kotlin
        if (notificationText != lastNotificationText) {
            lastNotificationText = notificationText
            notificationHelper.update(notificationText)
        }
```

Replace that block with:

```kotlin
        val payload = com.hrcoach.service.workout.notification.NotifContentFormatter.format(
            snapshot = com.hrcoach.service.WorkoutState.snapshot.value,
            config = workoutConfig,
            totalSeconds = workoutTotalSeconds,
        )
        notificationHelper.update(payload)
        updateMediaSessionState(payload)
```

Then add the helper method near `computeTotalSeconds`:

```kotlin
    private fun updateMediaSessionState(payload: com.hrcoach.service.workout.notification.NotifPayload) {
        val state = android.support.v4.media.session.PlaybackStateCompat.Builder()
            .setState(
                if (payload.isPaused) android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED
                else android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING,
                payload.elapsedSeconds * 1000L,
                if (payload.isPaused) 0f else 1f,
            )
            .setActions(
                android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
                        android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY,
            )
            .build()
        mediaSession.setPlaybackState(state)

        val metadata = android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(
                android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE,
                payload.titleText,
            )
            .putString(
                android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST,
                payload.subtitleText,
            )
            .putLong(
                android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION,
                if (payload.isIndeterminate) 0L else payload.totalSeconds * 1000L,
            )
            .build()
        mediaSession.setMetadata(metadata)
    }
```

**Note:** `lastNotificationText` is no longer used as a dedup key — the underlying bitmap cache + `setOnlyAlertOnce(true)` already prevent unnecessary churn. Remove the `lastNotificationText` field declaration and the `lastNotificationText = ""` reset line if they become unused (Kotlin compiler will flag them).

- [ ] **Step 6: Leave the transitional update(text) calls alone**

The three existing calls to `notificationHelper.update("Workout paused")` / `"Workout resumed"` / `"Starting workout..."` work unchanged — they still route to the plain-text notification path in the helper. They'll be superseded by the next `processTick` which pushes a full `NotifPayload`. No edits required here.

- [ ] **Step 7: Build the project**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If KSP reports "Internal compiler error: Storage for file-to-id.tab already registered", rerun with `--stacktrace` to surface the real error — likely an unresolved reference or missing import.

If you hit a Windows path-length failure in the worktree (per CLAUDE.md), copy the modified files back to the main repo and run the build there.

- [ ] **Step 8: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all existing tests still pass plus the new `NotifContentFormatterTest` + `BadgeBitmapCacheTest`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/hrcoach/service/WorkoutForegroundService.kt
git commit -m "feat(notification): wire WorkoutForegroundService to MediaStyle helper

- Own MediaSessionCompat lifecycle (create in onCreate, release in onDestroy)
- Cache total planned duration in seconds at workout start (from
  WorkoutConfig.plannedDurationMinutes or segment durations)
- Build a NotifPayload each processTick via NotifContentFormatter and
  push to the helper + update the MediaSession PlaybackState + metadata
- Transitional 'Starting workout...' / 'Workout paused' / 'Workout resumed'
  messages still route through the legacy update(text) path"
```

---

## Task 8: Manual device verification

**Files:** none modified.

This task is a visual sanity check against the v5 mockup and a functional check that pause/resume from the lockscreen actually toggles workout state. Do this on a real device or emulator.

- [ ] **Step 1: Build and install the debug APK**

Run: `./gradlew :app:assembleDebug`
Then: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
(Per CLAUDE.md: use `adb install -r` — not `mobile_install_app` — to preserve Room DB.)

- [ ] **Step 2: Launch the app and start a simulated workout**

Run: `adb shell am start -n com.hrcoach/.MainActivity`

In the app, start a simulated bootcamp run (use SimulationController if available, or start any bootcamp session — BLE/GPS not required for notification rendering).

Or via adb:
- Open the Cardea app
- Tap Workout tab → start any preset / bootcamp session
- Let it run for a few seconds so the first processTick fires

- [ ] **Step 3: Lock the device and screenshot**

Run: `adb shell input keyevent KEYCODE_POWER`  (lock)
Then: `adb shell input keyevent KEYCODE_POWER`  (wake — notification should show)
Then: `adb shell screencap //data/local/tmp/lock.png && adb pull //data/local/tmp/lock.png /tmp/lock.png`

Open `/tmp/lock.png` and visually compare the notification against `.superpowers/brainstorm/816-1776227880/content/approaches-v5.html`. Check:

- [ ] Badge shows gradient (not solid colour) matching the current zone state
- [ ] HR number is large and centred; "BPM" label sits below
- [ ] Title reads `{sessionLabel} · Target {targetHr}`
- [ ] Subtitle reads `{elapsed} / {total} · {delta label}`
- [ ] Progress bar is visible and non-zero when the workout has been running
- [ ] A Pause button is visible in the compact view

If any of these are off, iterate on `BadgeBitmapRenderer` + `NotifContentFormatter` + `WorkoutNotificationHelper` parameters and rebuild.

- [ ] **Step 4: Test pause from the lockscreen**

While the device is locked and the notification is visible, tap the Pause action button via adb:

First get the notification action's coordinates via `adb shell dumpsys notification` and tap them, or simpler — unlock briefly, tap the notification's pause button manually, re-lock.

Verify:
- [ ] Notification title changes to `{sessionLabel} · Paused`
- [ ] Action button changes to `Resume`
- [ ] Progress bar stops advancing

Tap Resume → verify workout continues and title reverts.

- [ ] **Step 5: Verify free run indeterminate progress**

Start a free run instead of a bootcamp session. Lock screen. Verify:

- [ ] Title reads `Free Run`
- [ ] Subtitle ends with `∞` in place of a total duration
- [ ] Progress bar renders as an indeterminate moving bar

- [ ] **Step 6: Commit the verification log (optional)**

If screenshots were captured, commit them into the plan's artifacts directory:

```bash
mkdir -p docs/superpowers/plans/artifacts/2026-04-14-lockscreen-media-notification
cp /tmp/lock.png docs/superpowers/plans/artifacts/2026-04-14-lockscreen-media-notification/device-verification.png
git add docs/superpowers/plans/artifacts/2026-04-14-lockscreen-media-notification/
git commit -m "docs(plan): device verification screenshot for lockscreen notification"
```

---

## Completion checklist

After all tasks are complete:

- [ ] All unit tests pass (`./gradlew :app:testDebugUnitTest`)
- [ ] Debug APK builds without errors (`./gradlew :app:assembleDebug`)
- [ ] Device verification screenshot visually matches the v5 mockup intent (not pixel-perfect — Android chrome has platform variation)
- [ ] Pause/resume from the lockscreen actually toggles workout state
- [ ] Free run indeterminate progress bar works
- [ ] Paused state dims the badge and shows a Resume action
- [ ] No regressions in existing workout flow (start → run → pause → resume → stop)
- [ ] No regressions in existing unit tests

Merge the branch into main only after all checklist items are green.
