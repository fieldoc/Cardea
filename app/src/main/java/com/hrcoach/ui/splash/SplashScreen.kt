package com.hrcoach.ui.splash

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.pow

// ─── Constants ────────────────────────────────────────────────────────────────

private val BG = Color(0xFF0D0D14)

private const val PHASE_DRAW_MS  = 1600L
private const val PHASE_HOLD_MS  = 500L
private const val PHASE_FADE_MS  = 500L
private const val CYCLE_TOTAL_MS = PHASE_DRAW_MS + PHASE_HOLD_MS + PHASE_FADE_MS  // 2600ms

private const val WM_TRIGGER_FRAC  = 0.46f   // rawP (0..1 of draw phase) at which R-peak fires
private const val WM_TARGET_OPACITY = 0.75f
private const val FLASH_DUR_MS      = 120L

private const val TRAIL_STROKE = 0.22f
private const val TRAIL_GLOW   = 0.38f
private const val LEAD_FADE    = 0.09f

/**
 * ECG waypoints — normalised x∈[0,1], y∈[0,1].
 * Baseline is y=0.50. R-peak at (0.46, 0.07) — near top of frame.
 * Q dip merged tightly into QRS complex (onset x=0.43, not 0.30).
 */
private val ECG_N = arrayOf(
    floatArrayOf(0.000f, 0.50f),  // left bleed start
    floatArrayOf(0.300f, 0.50f),  // flat PR segment
    floatArrayOf(0.430f, 0.51f),  // Q onset
    floatArrayOf(0.440f, 0.53f),  // Q dip
    floatArrayOf(0.455f, 0.51f),  // Q recovery / pre-R
    floatArrayOf(0.460f, 0.07f),  // R PEAK — needle spike
    floatArrayOf(0.465f, 0.56f),  // S dip
    floatArrayOf(0.480f, 0.52f),  // S recovery
    floatArrayOf(0.540f, 0.44f),  // T wave apex
    floatArrayOf(0.600f, 0.50f),  // T end / ST
    floatArrayOf(0.750f, 0.50f),  // flat baseline
    floatArrayOf(1.000f, 0.50f),  // right bleed end
)

// ─── Gradient ─────────────────────────────────────────────────────────────────

private val STOPS = listOf(
    0.00f to GradientRed,
    0.35f to GradientPink,
    0.65f to GradientBlue,
    1.00f to GradientCyan,
)

private fun gradColor(t: Float, alpha: Float = 1f): Color {
    val tc = t.coerceIn(0f, 1f)
    for (i in 0 until STOPS.size - 1) {
        val (s0, c0) = STOPS[i]; val (s1, c1) = STOPS[i + 1]
        if (tc <= s1) {
            val l = if (s1 == s0) 0f else (tc - s0) / (s1 - s0)
            return lerp(c0, c1, l).copy(alpha = alpha)
        }
    }
    return STOPS.last().second.copy(alpha = alpha)
}

// ─── ECG geometry ─────────────────────────────────────────────────────────────

private data class EcgPt(val pos: Offset, val t: Float)
private data class EcgSeg(val iStart: Int, val cumStart: Float, val cumEnd: Float)

private fun buildEcgPts(tx: Float, ty: Float, tw: Float, th: Float): List<EcgPt> =
    ECG_N.map { a -> EcgPt(Offset(tx + a[0] * tw, ty + a[1] * th), a[0]) }

private fun buildSegs(pts: List<EcgPt>): List<EcgSeg> {
    val lens = (0 until pts.size - 1).map { (pts[it + 1].pos - pts[it].pos).getDistance() }
    val total = lens.sum().coerceAtLeast(0.001f)
    var cum = 0f
    return lens.mapIndexed { i, len ->
        val cs = cum / total; cum += len; EcgSeg(i, cs, cum / total)
    }
}

private fun ptAt(p: Float, pts: List<EcgPt>, segs: List<EcgSeg>): EcgPt {
    val pc = p.coerceIn(0f, 1f)
    for (s in segs) {
        if (pc <= s.cumEnd) {
            val l = if (s.cumEnd == s.cumStart) 0f else (pc - s.cumStart) / (s.cumEnd - s.cumStart)
            val a = pts[s.iStart]; val b = pts[s.iStart + 1]
            return EcgPt(Offset(a.pos.x + (b.pos.x - a.pos.x) * l, a.pos.y + (b.pos.y - a.pos.y) * l), a.t + (b.t - a.t) * l)
        }
    }
    return pts.last()
}

// ─── Animation helpers ────────────────────────────────────────────────────────

/**
 * Piecewise speed remap: pre/post QRS regions traverse at constant speed;
 * QRS complex (x=0.43..0.50) is compressed to 4% of animation time.
 * Makes the R-peak feel like an electrical event — sudden, not gradual.
 */
private fun speedRemap(p: Float): Float {
    val qIn = 0.43f; val qOut = 0.50f; val qT = 0.04f; val nq = 1f - qT
    val pre = qIn; val post = 1f - qOut; val tot = pre + post
    val preT = (pre / tot) * nq; val postT = (post / tot) * nq
    return when {
        p < qIn  -> (p / qIn) * preT
        p > qOut -> preT + qT + ((p - qOut) / post) * postT
        else     -> { val l = (p - qIn) / (qOut - qIn); preT + (1f - (1f - l).pow(3f)) * qT }
    }
}

/** Stroke narrows to 0.62× at R-peak, creating a hotter electrical appearance. */
private fun qrsWidthFactor(t: Float): Float {
    val dist = abs(t - 0.46f)
    if (dist > 0.055f) return 1f
    val l = 1f - dist / 0.055f
    return 1f - 0.38f * l * l
}

// ─── Drawing ──────────────────────────────────────────────────────────────────

/**
 * Glow pass: single blurred path draw using LinearGradient shader.
 * Glow trail persists longer than the crisp stroke (TRAIL_GLOW).
 */
private fun DrawScope.drawGlowPass(
    pts: List<EcgPt>, segs: List<EcgSeg>,
    progress: Float, strokePx: Float, alpha: Float, blurPx: Float,
    traceX: Float, traceW: Float,
) {
    if (progress <= 0f) return
    // Build visible sub-path up to progress
    val path = Path()
    val n = 120
    for (i in 0..n) {
        val frac = (i.toFloat() / n) * progress
        val pt = ptAt(frac, pts, segs)
        if (i == 0) path.moveTo(pt.pos.x, pt.pos.y) else path.lineTo(pt.pos.x, pt.pos.y)
    }
    drawIntoCanvas { canvas ->
        canvas.drawPath(path, Paint().also { cp ->
            cp.asFrameworkPaint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                this.strokeWidth = strokePx
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                shader = LinearGradient(
                    traceX, 0f, traceX + traceW, 0f,
                    intArrayOf(
                        GradientRed.copy(alpha = alpha).toArgb(),
                        GradientPink.copy(alpha = alpha).toArgb(),
                        GradientBlue.copy(alpha = alpha).toArgb(),
                        GradientCyan.copy(alpha = alpha).toArgb(),
                    ),
                    floatArrayOf(0f, 0.35f, 0.65f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
        })
    }
}

/**
 * Crisp pass: per-segment drawLine with:
 * - exact gradient color per segment midpoint
 * - QRS stroke-width modulation (narrower = hotter)
 * - tail fade (oldest segments fade out, trace "draws fresh")
 * - lead fade (tip blends in)
 */
private fun DrawScope.drawCrispPass(
    pts: List<EcgPt>, segs: List<EcgSeg>,
    progress: Float, sw: Float, traceOpacity: Float,
) {
    val n = 280
    for (i in 0 until n) {
        val p0n = i.toFloat() / n
        val p1n = (i + 1).toFloat() / n
        if (p1n > progress) break

        var alpha = traceOpacity

        // Tail fade — smooth ramp: 0 at oldest visible, 1 at trail cutoff
        if (p0n < progress - TRAIL_STROKE) {
            alpha *= ((p0n - (progress - TRAIL_STROKE)) / TRAIL_STROKE + 1f).coerceIn(0f, 1f)
        }
        // Lead fade
        val lead = progress - p1n
        if (lead < LEAD_FADE) alpha *= lead / LEAD_FADE

        if (alpha <= 0.002f) continue

        val p0 = ptAt(p0n, pts, segs); val p1 = ptAt(p1n, pts, segs)
        val midT = (p0.t + p1.t) / 2f
        drawLine(
            color = gradColor(midT, alpha),
            start = p0.pos, end = p1.pos,
            strokeWidth = sw * qrsWidthFactor(midT),
            cap = StrokeCap.Round,
        )
    }
}

// ─── Composable ───────────────────────────────────────────────────────────────

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val density = LocalDensity.current

    // Animation frame state — single source of truth
    var cycleStartMs    by remember { mutableLongStateOf(-1L) }
    var currentFrameMs  by remember { mutableLongStateOf(0L) }
    var wmAlpha         by remember { mutableFloatStateOf(0f) }
    // R-peak flash: progress value when fired (used to look up canvas position), abs timestamp
    var flashProgress   by remember { mutableFloatStateOf(-1f) }
    var flashAbsMs      by remember { mutableLongStateOf(-1L) }

    // Animation loop — frame-accurate via withFrameMillis
    LaunchedEffect(Unit) {
        var lastCycleIdx = -1
        var rPeakFired  = false
        var wmFired     = false
        var wmFadeAbsMs = -1L

        while (isActive) {
            androidx.compose.runtime.withFrameMillis { frameMs ->
                if (cycleStartMs < 0L) cycleStartMs = frameMs
                currentFrameMs = frameMs

                val total    = frameMs - cycleStartMs
                // Cap at cycle 0 — if nav is slow (e.g. cloud restore still running), hold on
                // the final frame rather than restart the animation. Old behavior: restart every
                // 2.6s forever, which looks like a hang/glitch to the user.
                val cycleIdx = (total / CYCLE_TOTAL_MS).toInt().coerceAtMost(0)
                val elapsed  = if ((total / CYCLE_TOTAL_MS).toInt() >= 1) {
                    // Freeze at end-of-hold, before fade — keeps the fully-drawn glyph on screen
                    // while we wait for nav instead of fading to a black frame.
                    PHASE_DRAW_MS + PHASE_HOLD_MS - 1
                } else {
                    total % CYCLE_TOTAL_MS
                }

                // Per-cycle reset
                if (cycleIdx != lastCycleIdx) {
                    lastCycleIdx = cycleIdx
                    rPeakFired  = false
                    wmFired     = false
                    wmFadeAbsMs = -1L
                    flashAbsMs  = -1L
                    flashProgress = -1f
                }

                // Trigger wordmark + R-peak flash at WM_TRIGGER_FRAC of draw phase
                if (elapsed < PHASE_DRAW_MS) {
                    val rawP = elapsed.toFloat() / PHASE_DRAW_MS
                    if (!rPeakFired && rawP >= WM_TRIGGER_FRAC) {
                        rPeakFired   = true
                        flashProgress = speedRemap(rawP)
                        flashAbsMs   = frameMs
                    }
                    if (!wmFired && rawP >= WM_TRIGGER_FRAC) {
                        wmFired     = true
                        wmFadeAbsMs = frameMs
                    }
                }

                // Compute wordmark alpha — fades in after R-peak, fades out with trace
                wmAlpha = when {
                    wmFadeAbsMs < 0 -> 0f
                    elapsed < PHASE_DRAW_MS + PHASE_HOLD_MS -> {
                        ((frameMs - wmFadeAbsMs).toFloat() / 650f).coerceIn(0f, 1f) * WM_TARGET_OPACITY
                    }
                    else -> {
                        val fp = (elapsed - PHASE_DRAW_MS - PHASE_HOLD_MS).toFloat() / PHASE_FADE_MS
                        ((frameMs - wmFadeAbsMs).toFloat() / 650f).coerceIn(0f, 1f) *
                                WM_TARGET_OPACITY * (1f - fp * fp).coerceAtLeast(0f)
                    }
                }
            }
        }
    }

    // Navigate away after one full cycle
    LaunchedEffect(Unit) {
        delay(CYCLE_TOTAL_MS + 200L)
        onFinished()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(BG),
        contentAlignment = Alignment.Center,
    ) {
        val widthPx  = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val traceW = widthPx * 0.74f
        val traceH = heightPx * 0.26f
        val traceX = (widthPx - traceW) / 2f
        val traceY = (heightPx - traceH) / 2f - heightPx * 0.04f
        val sw     = (widthPx * 0.003f).coerceAtLeast(1.5f)

        // Build ECG geometry once per screen size
        val pts  = remember(widthPx, heightPx) { buildEcgPts(traceX, traceY, traceW, traceH) }
        val segs = remember(pts) { buildSegs(pts) }

        // Wordmark Y: pinned to trace bounding box + viewport-relative gap
        val wmOffsetY = with(density) { (traceY + traceH * 0.95f + heightPx * 0.055f - heightPx / 2f).toDp() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (cycleStartMs < 0L) return@Canvas

            val total   = currentFrameMs - cycleStartMs
            // Mirror the frame-loop cap: after the first cycle, freeze at end-of-hold so the
            // trace stays fully drawn while we wait for nav, instead of fading to black or
            // re-animating from scratch.
            val elapsed = if ((total / CYCLE_TOTAL_MS).toInt() >= 1) {
                PHASE_DRAW_MS + PHASE_HOLD_MS - 1
            } else {
                total % CYCLE_TOTAL_MS
            }

            // Derive progress and traceOpacity from phase
            val (progress, traceOpacity) = when {
                elapsed < PHASE_DRAW_MS -> {
                    val rawP = elapsed.toFloat() / PHASE_DRAW_MS
                    speedRemap(rawP) to 1f
                }
                elapsed < PHASE_DRAW_MS + PHASE_HOLD_MS -> 1f to 1f
                else -> {
                    val fp = (elapsed - PHASE_DRAW_MS - PHASE_HOLD_MS).toFloat() / PHASE_FADE_MS
                    1f to (1f - fp * fp).coerceAtLeast(0f)
                }
            }

            if (progress <= 0f) return@Canvas

            // Pass 0: wide corona glow
            drawGlowPass(pts, segs, progress, sw * 11f, 0.11f * traceOpacity, sw * 4f, traceX, traceW)
            // Pass 1: medium bloom
            drawGlowPass(pts, segs, progress, sw * 3f, 0.22f * traceOpacity, sw * 1.1f, traceX, traceW)
            // Pass 2: crisp gradient stroke with tail fade + width modulation
            drawCrispPass(pts, segs, progress, sw, traceOpacity)

            // Tip dot — white hot core with gradient glow halo
            if (progress > 0.02f && progress < 0.995f) {
                val tip    = ptAt(progress, pts, segs)
                val tipVis = (progress / 0.04f).coerceAtMost(1f) *
                        ((1f - progress) / 0.04f).coerceAtMost(1f) * traceOpacity

                // Glow halo
                drawIntoCanvas { canvas ->
                    canvas.drawCircle(tip.pos, sw * 2.8f, Paint().also { p ->
                        p.asFrameworkPaint().apply {
                            isAntiAlias = true
                            style = android.graphics.Paint.Style.FILL
                            maskFilter = BlurMaskFilter(sw * 2.5f, BlurMaskFilter.Blur.NORMAL)
                            color = gradColor(tip.t, 0.5f * tipVis).toArgb()
                        }
                    })
                }
                // Crisp white core
                drawCircle(
                    color = Color.White.copy(alpha = 0.92f * tipVis),
                    radius = sw * 1.8f,
                    center = tip.pos,
                )
            }

            // R-peak luminance flash — expanding radial white pulse at spike moment
            if (flashAbsMs > 0L && flashProgress >= 0f) {
                val age = (currentFrameMs - flashAbsMs).coerceAtLeast(0L)
                if (age < FLASH_DUR_MS) {
                    val fp           = age.toFloat() / FLASH_DUR_MS
                    val flashAlpha   = 0.38f * (1f - fp) * (1f - fp) * traceOpacity
                    val flashRadius  = sw * (1.5f + fp * 9f)
                    val flashPos     = ptAt(flashProgress, pts, segs)
                    drawCircle(
                        color  = Color.White.copy(alpha = flashAlpha),
                        radius = flashRadius,
                        center = flashPos.pos,
                    )
                }
            }
        }

        // Wordmark — DM Mono-style monospace, fades in at R-peak, out with trace
        Text(
            text       = "CARDEA",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Light,
            fontSize   = 14.sp,
            letterSpacing = 4.5.sp,   // ≈ 0.32em at 14sp
            color      = Color.White.copy(alpha = wmAlpha),
            modifier   = Modifier.offset(y = wmOffsetY),
        )
    }
}
