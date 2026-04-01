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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import kotlin.math.*

// ---- Gradient helpers ----

private val GradientStops = arrayOf(
    0.00f to GradientRed,
    0.35f to GradientPink,
    0.65f to GradientBlue,
    1.00f to GradientCyan
)

/** Interpolate the 4-stop Cardea gradient at position [t] (0-1). */
private fun gradientColorAt(t: Float): Color {
    val tc = t.coerceIn(0f, 1f)
    for (i in 0 until GradientStops.size - 1) {
        val (s0, c0) = GradientStops[i]
        val (s1, c1) = GradientStops[i + 1]
        if (tc <= s1) {
            val local = if (s1 == s0) 0f else (tc - s0) / (s1 - s0)
            return lerp(c0, c1, local)
        }
    }
    return GradientStops.last().second
}

/** Project a point onto the 135-degree diagonal for screen-space gradient t. */
private fun screenGradT(x: Float, y: Float, cx: Float, cy: Float, scale: Float): Float {
    val angle = Math.toRadians(135.0)
    val dx = cos(angle).toFloat()
    val dy = sin(angle).toFloat()
    val px = (x - cx) / scale
    val py = (y - cy) / scale
    val proj = px * dx + py * dy
    return ((proj + 1.2f) / 2.4f).coerceIn(0f, 1f)
}

private fun lerp(a: Color, b: Color, t: Float): Color {
    val tc = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * tc,
        green = a.green + (b.green - a.green) * tc,
        blue = a.blue + (b.blue - a.blue) * tc,
        alpha = a.alpha + (b.alpha - a.alpha) * tc
    )
}

// ---- Easing helpers ----

private fun cubicEaseInOut(t: Float): Float {
    val tc = t.coerceIn(0f, 1f)
    return if (tc < 0.5f) 4f * tc * tc * tc
    else 1f - (-2f * tc + 2f).pow(3) / 2f
}

// ---- Path builders ----

/**
 * Parametric heart shape. Starts at bottom cusp (theta=PI), goes UP the left lobe,
 * over the cleft, DOWN the right lobe, back to bottom cusp.
 */
private fun buildHeartPath(cx: Float, cy: Float, scale: Float, n: Int): List<Offset> {
    val points = mutableListOf<Offset>()
    for (i in 0 until n) {
        val frac = i.toFloat() / n
        // Start at PI (bottom cusp), traverse counter-clockwise (left lobe first)
        val theta = PI.toFloat() + frac * (2f * PI.toFloat())

        val sinT = sin(theta)
        val cosT = cos(theta)
        val cos2T = cos(2f * theta)
        val cos3T = cos(3f * theta)
        val cos4T = cos(4f * theta)

        var x = 16f * sinT * sinT * sinT
        var y = -(13f * cosT - 5f * cos2T - 2f * cos3T - cos4T)

        // Normalize
        x /= 17f
        y /= 20f
        y -= 0.05f

        // Slight asymmetry: right lobe wider
        x *= if (x > 0f) 1.04f else 0.988f

        // Cusp rounding at bottom (frac near 0 or 1)
        val cuspDist = min(frac, 1f - frac)
        if (cuspDist < 0.05f) {
            val blend = cuspDist / 0.05f
            y = y * (0.95f + 0.05f * blend)
        }

        points.add(Offset(cx + x * scale, cy + y * scale))
    }
    return points
}

/**
 * ECG waveform as a horizontal line with P-QRS-T displacement.
 * Spans from cx - scale*1.1 to cx + scale*1.1.
 */
private fun buildECGLine(cx: Float, cy: Float, scale: Float, n: Int): List<Offset> {
    val baseY = cy + scale * 0.35f
    val halfW = scale * 1.1f
    val points = mutableListOf<Offset>()

    for (i in 0 until n) {
        val t = i.toFloat() / (n - 1)
        val x = cx - halfW + t * 2f * halfW

        var displacement = 0f

        // P wave: t=0.30-0.40
        if (t in 0.30f..0.40f) {
            val local = (t - 0.30f) / 0.10f
            displacement = 0.12f * sin(local * PI.toFloat())
        }
        // Q dip: t=0.44-0.47
        if (t in 0.44f..0.47f) {
            val local = (t - 0.44f) / 0.03f
            displacement = -0.06f * sin(local * PI.toFloat())
        }
        // QRS spike: t=0.47-0.53
        if (t in 0.47f..0.53f) {
            val local = (t - 0.47f) / 0.06f
            displacement = 0.8f * sin(local * PI.toFloat())
        }
        // S dip: t=0.53-0.57
        if (t in 0.53f..0.57f) {
            val local = (t - 0.53f) / 0.04f
            displacement = -0.15f * sin(local * PI.toFloat())
        }
        // T wave: t=0.63-0.75
        if (t in 0.63f..0.75f) {
            val local = (t - 0.63f) / 0.12f
            displacement = 0.18f * sin(local * PI.toFloat())
        }

        val y = baseY - displacement * scale
        points.add(Offset(x, y))
    }
    return points
}

// ---- Composable ----

@Composable
fun CardeaLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    animate: Boolean = true,
    cycleDurationMs: Int = 10_000
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val pointCount = 200

    // Pre-compute paths (only depend on size)
    val heartPts = remember(sizePx) {
        val s = sizePx * 0.42f
        buildHeartPath(sizePx / 2f, sizePx / 2f, s, pointCount)
    }
    val ecgPts = remember(sizePx) {
        val s = sizePx * 0.42f
        buildECGLine(sizePx / 2f, sizePx / 2f, s, pointCount)
    }

    // 10-second cycle animation
    val infiniteTransition = rememberInfiniteTransition(label = "logoAnim")
    val cycle by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(cycleDurationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "cycle"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val scale = min(w, h) * 0.42f
        val cx = w / 2f
        val cy = h / 2f
        val sw = w * 0.025f
        val n = pointCount

        if (!animate) {
            drawStaticHeart(heartPts, cx, cy, scale, sw, w, h)
            return@Canvas
        }

        when {
            cycle < 0.28f -> {
                val phase = cycle / 0.28f
                drawPhase1ECGDrawOn(ecgPts, phase, cx, cy, scale, sw, n)
            }
            cycle < 0.58f -> {
                val phase = (cycle - 0.28f) / 0.30f
                drawPhase2Morph(ecgPts, heartPts, phase, cx, cy, scale, sw, n)
            }
            else -> {
                drawPhase3Hold(heartPts, cx, cy, scale, sw, w, h)
            }
        }
    }
}

// ---- Drawing functions ----

private fun DrawScope.drawStaticHeart(
    heartPts: List<Offset>,
    cx: Float, cy: Float, scale: Float,
    sw: Float, w: Float, h: Float
) {
    // Glow layer
    drawGlowPath(heartPts, sw, w, h, alpha = 0.08f)
    // Stroke layer
    drawGradientPolyline(heartPts, cx, cy, scale, sw, closed = true)
}

private fun DrawScope.drawPhase1ECGDrawOn(
    ecgPts: List<Offset>,
    rawPhase: Float,
    cx: Float, cy: Float, scale: Float,
    sw: Float, n: Int
) {
    val phase = cubicEaseInOut(rawPhase)
    val centerIdx = n / 2
    val halfSpan = (phase * centerIdx).toInt()

    val startIdx = (centerIdx - halfSpan).coerceAtLeast(0)
    val endIdx = (centerIdx + halfSpan).coerceAtMost(n - 1)

    if (startIdx >= endIdx) return

    val visiblePts = ecgPts.subList(startIdx, endIdx + 1)

    // Draw segments with per-point alpha (tip fade)
    val tipFadeLen = (n * 0.05f).coerceAtLeast(3f)
    for (i in 0 until visiblePts.size - 1) {
        val globalI = startIdx + i
        val p0 = visiblePts[i]
        val p1 = visiblePts[i + 1]

        // Distance from edge for tip fade
        val distFromStart = (globalI - startIdx).toFloat()
        val distFromEnd = (endIdx - globalI).toFloat()
        val edgeDist = min(distFromStart, distFromEnd)
        val alpha = (edgeDist / tipFadeLen).coerceIn(0f, 1f)

        val gradT = screenGradT(
            (p0.x + p1.x) / 2f,
            (p0.y + p1.y) / 2f,
            cx, cy, scale
        )
        val color = gradientColorAt(gradT).copy(alpha = alpha)

        // Velocity-based thickness
        val vel = (p1 - p0).getDistance() / (scale * 0.01f + 1f)
        val thickness = (sw * (0.9f + 1.2f / (1f + vel * 0.5f))).coerceAtMost(sw * 2.8f)

        drawLine(
            color = color,
            start = p0,
            end = p1,
            strokeWidth = thickness,
            cap = StrokeCap.Round
        )
    }

    // White tip dots
    if (halfSpan > 0) {
        val dotRadius = sw * 1.2f
        val leftTip = ecgPts[startIdx]
        val rightTip = ecgPts[endIdx]
        drawCircle(Color.White.copy(alpha = 0.8f), dotRadius, leftTip)
        drawCircle(Color.White.copy(alpha = 0.8f), dotRadius, rightTip)
    }
}

private fun DrawScope.drawPhase2Morph(
    ecgPts: List<Offset>,
    heartPts: List<Offset>,
    rawPhase: Float,
    cx: Float, cy: Float, scale: Float,
    sw: Float, n: Int
) {
    val morphedPts = mutableListOf<Offset>()
    val halfN = n / 2f

    for (i in 0 until n) {
        val ecg = ecgPts[i]
        val heart = heartPts[i]

        // Stagger: endpoints (near bottom cusp) start first
        val distFromEdge = min(i.toFloat(), (n - 1 - i).toFloat()) / halfN
        val staggerDelay = distFromEdge * 0.18f
        val localT = ((rawPhase - staggerDelay) / (1f - staggerDelay)).coerceIn(0f, 1f)
        var eased = cubicEaseInOut(localT)

        // Elastic overshoot near end
        if (localT > 0.7f) {
            val overT = (localT - 0.7f) / 0.3f
            val overshoot = sin(overT * PI.toFloat()) * 0.05f
            eased = (eased + overshoot).coerceAtMost(1.05f)
        }

        val x = ecg.x + (heart.x - ecg.x) * eased
        val y = ecg.y + (heart.y - ecg.y) * eased
        morphedPts.add(Offset(x, y))
    }

    // Soft glow grows in
    val glowAlpha = (rawPhase * 0.08f).coerceAtMost(0.08f)
    if (rawPhase > 0.3f) {
        drawGlowPath(morphedPts, sw, size.width, size.height, alpha = glowAlpha)
    }

    // Draw the morphed polyline
    drawGradientPolyline(morphedPts, cx, cy, scale, sw, closed = rawPhase > 0.85f)
}

private fun DrawScope.drawPhase3Hold(
    heartPts: List<Offset>,
    cx: Float, cy: Float, scale: Float,
    sw: Float, w: Float, h: Float
) {
    // Subtle ambient glow
    drawGlowPath(heartPts, sw, w, h, alpha = 0.08f)
    // Static heart
    drawGradientPolyline(heartPts, cx, cy, scale, sw, closed = true)
}

// ---- Shared rendering helpers ----

/**
 * Draw a polyline with per-segment gradient color and velocity-based stroke width.
 */
private fun DrawScope.drawGradientPolyline(
    pts: List<Offset>,
    cx: Float, cy: Float, scale: Float,
    sw: Float, closed: Boolean
) {
    if (pts.size < 2) return

    var prevVel = 0f
    val count = if (closed) pts.size else pts.size - 1

    // Glow pass (wider, lower alpha)
    for (i in 0 until count) {
        val p0 = pts[i]
        val p1 = pts[(i + 1) % pts.size]
        val rawVel = (p1 - p0).getDistance() / (scale * 0.01f + 1f)
        val vel = rawVel * 0.6f + prevVel * 0.4f
        prevVel = vel
        val thickness = (sw * (0.9f + 1.2f / (1f + vel * 0.5f))).coerceAtMost(sw * 2.8f)
        val gradT = screenGradT((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f, cx, cy, scale)
        val color = gradientColorAt(gradT).copy(alpha = 0.03f)
        drawLine(color, p0, p1, strokeWidth = thickness * 3.5f, cap = StrokeCap.Round)
    }

    // Main stroke pass
    prevVel = 0f
    for (i in 0 until count) {
        val p0 = pts[i]
        val p1 = pts[(i + 1) % pts.size]
        val rawVel = (p1 - p0).getDistance() / (scale * 0.01f + 1f)
        val vel = rawVel * 0.6f + prevVel * 0.4f
        prevVel = vel
        val thickness = (sw * (0.9f + 1.2f / (1f + vel * 0.5f))).coerceAtMost(sw * 2.8f)
        val gradT = screenGradT((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f, cx, cy, scale)
        val color = gradientColorAt(gradT)
        drawLine(color, p0, p1, strokeWidth = thickness, cap = StrokeCap.Round)
    }
}

/**
 * Draw a blurred glow behind a set of points using BlurMaskFilter.
 */
private fun DrawScope.drawGlowPath(
    pts: List<Offset>,
    sw: Float, w: Float, h: Float,
    alpha: Float
) {
    if (pts.size < 2) return

    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            lineTo(pts[i].x, pts[i].y)
        }
        close()
    }

    val blurRadius = w * 0.12f
    drawIntoCanvas { canvas ->
        val glowPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = sw * 3f
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
                maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
                this.alpha = (alpha * 255).toInt()
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
        canvas.drawPath(path, glowPaint)
    }
}
