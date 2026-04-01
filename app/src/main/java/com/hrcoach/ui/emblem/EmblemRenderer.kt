package com.hrcoach.ui.emblem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.hrcoach.domain.emblem.Emblem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val TWO_PI = (PI * 2).toFloat()
private val HALF_PI = (PI / 2).toFloat()

/**
 * Draws the given [emblem] on this [DrawScope] at [center] with [radius].
 * [gradient] is used as the brush for all strokes and fills.
 * [strokeWidth] defaults to 6% of radius.
 */
fun DrawScope.drawEmblem(
    emblem: Emblem,
    center: Offset,
    radius: Float,
    gradient: Brush,
    strokeWidth: Float = radius * 0.06f,
) {
    when (emblem) {
        Emblem.PULSE -> drawPulse(center, radius, gradient, strokeWidth)
        Emblem.BOLT -> drawBolt(center, radius, gradient, strokeWidth)
        Emblem.SUMMIT -> drawSummit(center, radius, gradient, strokeWidth)
        Emblem.FLAME -> drawFlame(center, radius, gradient, strokeWidth)
        Emblem.COMPASS -> drawCompass(center, radius, gradient, strokeWidth)
        Emblem.SHIELD -> drawShield(center, radius, gradient, strokeWidth)
        Emblem.ASCENT -> drawAscent(center, radius, gradient, strokeWidth)
        Emblem.CROWN -> drawCrown(center, radius, gradient, strokeWidth)
        Emblem.ORBIT -> drawOrbit(center, radius, gradient, strokeWidth)
        Emblem.INFINITY -> drawInfinity(center, radius, gradient, strokeWidth)
        Emblem.DIAMOND -> drawDiamond(center, radius, gradient, strokeWidth)
        Emblem.NOVA -> drawNova(center, radius, gradient, strokeWidth)
        Emblem.VORTEX -> drawVortex(center, radius, gradient, strokeWidth)
        Emblem.ANCHOR -> drawAnchor(center, radius, gradient, strokeWidth)
        Emblem.PHOENIX -> drawPhoenix(center, radius, gradient, strokeWidth)
        Emblem.ARROW -> drawArrow(center, radius, gradient, strokeWidth)
        Emblem.CREST -> drawCrest(center, radius, gradient, strokeWidth)
        Emblem.PRISM -> drawPrism(center, radius, gradient, strokeWidth)
        Emblem.RIPPLE -> drawRipple(center, radius, gradient, strokeWidth)
        Emblem.COMET -> drawComet(center, radius, gradient, strokeWidth)
        Emblem.THRESHOLD -> drawThreshold(center, radius, gradient, strokeWidth)
        Emblem.CIRCUIT -> drawCircuit(center, radius, gradient, strokeWidth)
        Emblem.APEX -> drawApex(center, radius, gradient, strokeWidth)
        Emblem.FORGE -> drawForge(center, radius, gradient, strokeWidth)
    }
}

// ---------------------------------------------------------------------------
// 0. Pulse — ECG heartbeat line
// ---------------------------------------------------------------------------
private fun DrawScope.drawPulse(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y
    val pts = listOf(
        Offset(cx - s, cy),
        Offset(cx - s * 0.55f, cy),
        Offset(cx - s * 0.35f, cy - s * 0.55f),
        Offset(cx - s * 0.1f, cy + s * 0.65f),
        Offset(cx + s * 0.15f, cy - s * 0.3f),
        Offset(cx + s * 0.35f, cy + s * 0.15f),
        Offset(cx + s * 0.5f, cy),
        Offset(cx + s, cy),
    )
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, gradient, style = Stroke(sw * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---------------------------------------------------------------------------
// 1. Bolt — Lightning bolt (filled polygon)
// ---------------------------------------------------------------------------
private fun DrawScope.drawBolt(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y
    val pts = listOf(
        Offset(cx + s * 0.15f, cy - s * 0.85f),
        Offset(cx - s * 0.3f, cy - s * 0.05f),
        Offset(cx + s * 0.05f, cy - s * 0.05f),
        Offset(cx - s * 0.15f, cy + s * 0.85f),
        Offset(cx + s * 0.3f, cy + s * 0.05f),
        Offset(cx - s * 0.05f, cy + s * 0.05f),
    )
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, gradient)
}

// ---------------------------------------------------------------------------
// 2. Summit — Mountain peaks
// ---------------------------------------------------------------------------
private fun DrawScope.drawSummit(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y
    val mainPts = listOf(
        Offset(cx - s * 0.85f, cy + s * 0.6f),
        Offset(cx - s * 0.25f, cy - s * 0.5f),
        Offset(cx + s * 0.05f, cy + s * 0.05f),
        Offset(cx + s * 0.3f, cy - s * 0.7f),
        Offset(cx + s * 0.85f, cy + s * 0.6f),
    )
    val mainPath = Path().apply {
        moveTo(mainPts[0].x, mainPts[0].y)
        mainPts.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(mainPath, gradient, style = Stroke(sw * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Snow cap
    val capPts = listOf(
        Offset(cx + s * 0.2f, cy - s * 0.55f),
        Offset(cx + s * 0.3f, cy - s * 0.7f),
        Offset(cx + s * 0.4f, cy - s * 0.55f),
    )
    val capPath = Path().apply {
        moveTo(capPts[0].x, capPts[0].y)
        capPts.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(capPath, gradient, style = Stroke(sw * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---------------------------------------------------------------------------
// 3. Flame
// ---------------------------------------------------------------------------
private fun DrawScope.drawFlame(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    // Outer flame
    val steps = 30
    val outerPts = mutableListOf<Offset>()
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val angle = (-PI * 0.4 + t * PI * 0.8).toFloat()
        var flameR = s * (0.5f + 0.4f * sin(t * PI.toFloat()))
        if (t < 0.15f || t > 0.85f) flameR *= 0.3f
        val x = sin(angle) * flameR * 0.7f
        val y = -cos(angle) * flameR + s * 0.15f
        outerPts.add(Offset(cx + x, cy + y))
    }
    val outerPath = Path().apply {
        moveTo(outerPts[0].x, outerPts[0].y)
        outerPts.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(outerPath, gradient, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Inner flame (thinner, semi-transparent via alpha — we use same brush but thinner)
    val innerSteps = 15
    val innerPts = mutableListOf<Offset>()
    for (i in 0..innerSteps) {
        val t = i.toFloat() / innerSteps
        val angle = (-PI * 0.25 + t * PI * 0.5).toFloat()
        val flameR = s * (0.2f + 0.2f * sin(t * PI.toFloat()))
        val x = sin(angle) * flameR * 0.5f
        val y = -cos(angle) * flameR + s * 0.3f
        innerPts.add(Offset(cx + x, cy + y))
    }
    val innerPath = Path().apply {
        moveTo(innerPts[0].x, innerPts[0].y)
        innerPts.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(innerPath, gradient, style = Stroke(sw * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---------------------------------------------------------------------------
// 4. Compass — 4 cardinal triangles + center dot
// ---------------------------------------------------------------------------
private fun DrawScope.drawCompass(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    fun filledTriangle(pts: List<Offset>) {
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, gradient)
    }

    // N
    filledTriangle(listOf(
        Offset(cx, cy - s * 0.8f),
        Offset(cx + s * 0.12f, cy),
        Offset(cx - s * 0.12f, cy),
    ))
    // E
    filledTriangle(listOf(
        Offset(cx + s * 0.8f, cy),
        Offset(cx, cy + s * 0.12f),
        Offset(cx, cy - s * 0.12f),
    ))
    // S
    filledTriangle(listOf(
        Offset(cx, cy + s * 0.8f),
        Offset(cx - s * 0.12f, cy),
        Offset(cx + s * 0.12f, cy),
    ))
    // W
    filledTriangle(listOf(
        Offset(cx - s * 0.8f, cy),
        Offset(cx, cy - s * 0.12f),
        Offset(cx, cy + s * 0.12f),
    ))

    // Center dot
    drawCircle(gradient, radius = s * 0.06f, center = center)
}

// ---------------------------------------------------------------------------
// 5. Shield — outline + cross
// ---------------------------------------------------------------------------
private fun DrawScope.drawShield(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y
    val outlinePts = listOf(
        Offset(cx - s * 0.55f, cy - s * 0.6f),
        Offset(cx + s * 0.55f, cy - s * 0.6f),
        Offset(cx + s * 0.55f, cy + s * 0.05f),
        Offset(cx, cy + s * 0.75f),
        Offset(cx - s * 0.55f, cy + s * 0.05f),
    )
    val outlinePath = Path().apply {
        moveTo(outlinePts[0].x, outlinePts[0].y)
        outlinePts.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(outlinePath, gradient, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Vertical
    val vPath = Path().apply {
        moveTo(cx, cy - s * 0.4f)
        lineTo(cx, cy + s * 0.45f)
    }
    drawPath(vPath, gradient, style = Stroke(sw * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Horizontal
    val hPath = Path().apply {
        moveTo(cx - s * 0.35f, cy - s * 0.1f)
        lineTo(cx + s * 0.35f, cy - s * 0.1f)
    }
    drawPath(hPath, gradient, style = Stroke(sw * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---------------------------------------------------------------------------
// 6. Ascent — double chevron
// ---------------------------------------------------------------------------
private fun DrawScope.drawAscent(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    val lower = Path().apply {
        moveTo(cx - s * 0.5f, cy + s * 0.3f)
        lineTo(cx, cy - s * 0.1f)
        lineTo(cx + s * 0.5f, cy + s * 0.3f)
    }
    drawPath(lower, gradient, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    val upper = Path().apply {
        moveTo(cx - s * 0.5f, cy - s * 0.15f)
        lineTo(cx, cy - s * 0.55f)
        lineTo(cx + s * 0.5f, cy - s * 0.15f)
    }
    drawPath(upper, gradient, style = Stroke(sw * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---------------------------------------------------------------------------
// 7. Crown
// ---------------------------------------------------------------------------
private fun DrawScope.drawCrown(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y
    val pts = listOf(
        Offset(cx - s * 0.6f, cy + s * 0.4f),
        Offset(cx - s * 0.6f, cy - s * 0.15f),
        Offset(cx - s * 0.3f, cy + s * 0.15f),
        Offset(cx, cy - s * 0.55f),
        Offset(cx + s * 0.3f, cy + s * 0.15f),
        Offset(cx + s * 0.6f, cy - s * 0.15f),
        Offset(cx + s * 0.6f, cy + s * 0.4f),
    )
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, gradient)

    // Base bands
    val band1 = Path().apply {
        moveTo(cx - s * 0.6f, cy + s * 0.4f)
        lineTo(cx + s * 0.6f, cy + s * 0.4f)
    }
    drawPath(band1, gradient, style = Stroke(sw * 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val band2 = Path().apply {
        moveTo(cx - s * 0.6f, cy + s * 0.5f)
        lineTo(cx + s * 0.6f, cy + s * 0.5f)
    }
    drawPath(band2, gradient, style = Stroke(sw * 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---------------------------------------------------------------------------
// 8. Orbit — center dot + two ellipses
// ---------------------------------------------------------------------------
private fun DrawScope.drawOrbit(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    // Center dot
    drawCircle(gradient, radius = s * 0.11f, center = center)

    // First ellipse
    val w = s * 1.5f
    val h = s * 0.65f
    drawArc(
        brush = gradient,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(cx - w / 2, cy - h / 2),
        size = Size(w, h),
        style = Stroke(sw, cap = StrokeCap.Round),
    )

    // Second ellipse — rotated 60 degrees around center
    withTransform({ rotate(60f, center) }) {
        drawArc(
            brush = gradient,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(cx - w / 2, cy - h / 2),
            size = Size(w, h),
            style = Stroke(sw * 0.7f, cap = StrokeCap.Round),
        )
    }
}

// ---------------------------------------------------------------------------
// 9. Infinity — Lemniscate
// ---------------------------------------------------------------------------
private fun DrawScope.drawInfinity(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y
    val steps = 60
    val pts = mutableListOf<Offset>()
    for (i in 0..steps) {
        val t = i.toFloat() / steps * TWO_PI
        val denom = 1f + sin(t) * sin(t)
        val x = s * 0.75f * cos(t) / denom
        val y = s * 0.45f * sin(t) * cos(t) / denom
        pts.add(Offset(cx + x, cy + y))
    }
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, gradient, style = Stroke(sw * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---------------------------------------------------------------------------
// 10. Diamond — gem shape
// ---------------------------------------------------------------------------
private fun DrawScope.drawDiamond(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y
    val outerPts = listOf(
        Offset(cx, cy - s * 0.8f),
        Offset(cx + s * 0.65f, cy - s * 0.15f),
        Offset(cx, cy + s * 0.75f),
        Offset(cx - s * 0.65f, cy - s * 0.15f),
    )
    val outerPath = Path().apply {
        moveTo(outerPts[0].x, outerPts[0].y)
        outerPts.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(outerPath, gradient, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Horizontal facet line at y = -s*0.15
    val hPath = Path().apply {
        moveTo(cx - s * 0.65f, cy - s * 0.15f)
        lineTo(cx + s * 0.65f, cy - s * 0.15f)
    }
    drawPath(hPath, gradient, style = Stroke(sw * 0.5f, cap = StrokeCap.Round))

    // Diagonal facets from top to left/right
    val d1 = Path().apply {
        moveTo(cx, cy - s * 0.8f)
        lineTo(cx - s * 0.65f, cy - s * 0.15f)
        lineTo(cx, cy + s * 0.75f)
    }
    drawPath(d1, gradient, style = Stroke(sw * 0.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val d2 = Path().apply {
        moveTo(cx, cy - s * 0.8f)
        lineTo(cx + s * 0.65f, cy - s * 0.15f)
        lineTo(cx, cy + s * 0.75f)
    }
    drawPath(d2, gradient, style = Stroke(sw * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---------------------------------------------------------------------------
// 11. Nova — 6-pointed starburst (12-point alternating polygon)
// ---------------------------------------------------------------------------
private fun DrawScope.drawNova(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y
    val outerR = s * 0.8f
    val innerR = s * 0.3f
    val pts = (0 until 12).map { i ->
        val angle = -HALF_PI + i * TWO_PI / 12
        val r = if (i % 2 == 0) outerR else innerR
        Offset(cx + cos(angle) * r, cy + sin(angle) * r)
    }
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        pts.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, gradient)
}

// ---------------------------------------------------------------------------
// 12. Vortex — spiral
// ---------------------------------------------------------------------------
private fun DrawScope.drawVortex(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y
    val steps = 60
    var prevX = cx
    var prevY = cy
    for (i in 1..steps) {
        val t = i.toFloat() / steps
        val angle = t * 2.5f * TWO_PI
        val r = s * 0.1f + (s * 0.75f - s * 0.1f) * t
        val x = cx + cos(angle) * r
        val y = cy + sin(angle) * r
        val segPath = Path().apply {
            moveTo(prevX, prevY)
            lineTo(x, y)
        }
        drawPath(segPath, gradient, style = Stroke(sw * (0.5f + t), cap = StrokeCap.Round))
        prevX = x
        prevY = y
    }
}

// ---------------------------------------------------------------------------
// 13. Anchor
// ---------------------------------------------------------------------------
private fun DrawScope.drawAnchor(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    // Shaft
    val shaft = Path().apply {
        moveTo(cx, cy - s * 0.55f)
        lineTo(cx, cy + s * 0.5f)
    }
    drawPath(shaft, gradient, style = Stroke(sw * 1.1f, cap = StrokeCap.Round))

    // Crossbar
    val cross = Path().apply {
        moveTo(cx - s * 0.35f, cy - s * 0.25f)
        lineTo(cx + s * 0.35f, cy - s * 0.25f)
    }
    drawPath(cross, gradient, style = Stroke(sw, cap = StrokeCap.Round))

    // Ring at top — full circle arc
    drawArc(
        brush = gradient,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(cx - s * 0.125f, cy - s * 0.65f - s * 0.125f),
        size = Size(s * 0.25f, s * 0.25f),
        style = Stroke(sw * 0.9f, cap = StrokeCap.Round),
    )

    // Left fluke
    drawArc(
        brush = gradient,
        startAngle = 90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - s * 0.35f - s * 0.25f, cy + s * 0.35f - s * 0.2f),
        size = Size(s * 0.5f, s * 0.4f),
        style = Stroke(sw, cap = StrokeCap.Round),
    )

    // Right fluke
    drawArc(
        brush = gradient,
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx + s * 0.35f - s * 0.25f, cy + s * 0.35f - s * 0.2f),
        size = Size(s * 0.5f, s * 0.4f),
        style = Stroke(sw, cap = StrokeCap.Round),
    )
}

// ---------------------------------------------------------------------------
// 14. Phoenix — wings + head
// ---------------------------------------------------------------------------
private fun DrawScope.drawPhoenix(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    // Left wing
    val leftWing = Path().apply {
        moveTo(cx, cy + s * 0.5f)
        lineTo(cx - s * 0.15f, cy + s * 0.1f)
        lineTo(cx - s * 0.45f, cy - s * 0.2f)
        lineTo(cx - s * 0.7f, cy - s * 0.65f)
        lineTo(cx - s * 0.4f, cy - s * 0.35f)
        lineTo(cx - s * 0.15f, cy - s * 0.5f)
    }
    drawPath(leftWing, gradient, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Right wing (mirror x)
    val rightWing = Path().apply {
        moveTo(cx, cy + s * 0.5f)
        lineTo(cx + s * 0.15f, cy + s * 0.1f)
        lineTo(cx + s * 0.45f, cy - s * 0.2f)
        lineTo(cx + s * 0.7f, cy - s * 0.65f)
        lineTo(cx + s * 0.4f, cy - s * 0.35f)
        lineTo(cx + s * 0.15f, cy - s * 0.5f)
    }
    drawPath(rightWing, gradient, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Head
    drawOval(
        brush = gradient,
        topLeft = Offset(cx - s * 0.075f, cy - s * 0.45f - s * 0.09f),
        size = Size(s * 0.15f, s * 0.18f),
    )
}

// ---------------------------------------------------------------------------
// 15. Arrow — pointing up
// ---------------------------------------------------------------------------
private fun DrawScope.drawArrow(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    // Shaft
    val shaft = Path().apply {
        moveTo(cx, cy + s * 0.65f)
        lineTo(cx, cy - s * 0.45f)
    }
    drawPath(shaft, gradient, style = Stroke(sw * 1.2f, cap = StrokeCap.Round))

    // Arrowhead
    val head = Path().apply {
        moveTo(cx - s * 0.35f, cy - s * 0.1f)
        lineTo(cx, cy - s * 0.65f)
        lineTo(cx + s * 0.35f, cy - s * 0.1f)
    }
    drawPath(head, gradient, style = Stroke(sw * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Fletching
    val fletch1 = Path().apply {
        moveTo(cx - s * 0.2f, cy + s * 0.5f)
        lineTo(cx, cy + s * 0.35f)
    }
    drawPath(fletch1, gradient, style = Stroke(sw * 0.7f, cap = StrokeCap.Round))
    val fletch2 = Path().apply {
        moveTo(cx + s * 0.2f, cy + s * 0.5f)
        lineTo(cx, cy + s * 0.35f)
    }
    drawPath(fletch2, gradient, style = Stroke(sw * 0.7f, cap = StrokeCap.Round))
}

// ---------------------------------------------------------------------------
// 16. Crest — shield + inner 5-pointed star
// ---------------------------------------------------------------------------
private fun DrawScope.drawCrest(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    // Shield
    val shield = Path().apply {
        moveTo(cx - s * 0.5f, cy - s * 0.65f)
        lineTo(cx + s * 0.5f, cy - s * 0.65f)
        lineTo(cx + s * 0.5f, cy)
        lineTo(cx, cy + s * 0.65f)
        lineTo(cx - s * 0.5f, cy)
        close()
    }
    drawPath(shield, gradient, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Inner 5-pointed star centered at [0, -s*0.1]
    val starCx = cx
    val starCy = cy - s * 0.1f
    val outerR = s * 0.25f
    val innerR = s * 0.12f
    val starPts = (0 until 10).map { i ->
        val angle = -HALF_PI + i * TWO_PI / 10
        val r = if (i % 2 == 0) outerR else innerR
        Offset(starCx + cos(angle) * r, starCy + sin(angle) * r)
    }
    val starPath = Path().apply {
        moveTo(starPts[0].x, starPts[0].y)
        starPts.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(starPath, gradient)
}

// ---------------------------------------------------------------------------
// 17. Prism — triangle + rays
// ---------------------------------------------------------------------------
private fun DrawScope.drawPrism(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    // Triangle
    val triangle = Path().apply {
        moveTo(cx, cy - s * 0.6f)
        lineTo(cx + s * 0.55f, cy + s * 0.45f)
        lineTo(cx - s * 0.55f, cy + s * 0.45f)
        close()
    }
    drawPath(triangle, gradient, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // 4 light rays from right side
    for (i in 0 until 4) {
        val yOff = -s * 0.15f + i * s * 0.18f
        val ray = Path().apply {
            moveTo(cx + s * 0.25f, cy + yOff)
            lineTo(cx + s * 0.7f, cy + yOff)
        }
        drawPath(ray, gradient, style = Stroke(sw * 0.6f, cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------------------
// 18. Ripple — center dot + 3 concentric arcs
// ---------------------------------------------------------------------------
private fun DrawScope.drawRipple(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    // Center dot
    drawCircle(gradient, radius = s * 0.06f, center = center)

    // 3 arcs
    for (i in 1..3) {
        val r = s * 0.2f * i
        drawArc(
            brush = gradient,
            startAngle = -144f,  // -PI*0.8 in degrees
            sweepAngle = 288f,   // from -PI*0.8 to PI*0.8
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(sw * (1.2f - i * 0.15f), cap = StrokeCap.Round),
        )
    }
}

// ---------------------------------------------------------------------------
// 19. Comet — head + trail
// ---------------------------------------------------------------------------
private fun DrawScope.drawComet(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    // Head — filled circle
    drawCircle(
        brush = gradient,
        radius = s * 0.125f,
        center = Offset(cx + s * 0.3f, cy - s * 0.3f),
    )

    // 5 trail lines from head toward [-s*0.6, s*0.35]
    val headX = cx + s * 0.3f
    val headY = cy - s * 0.3f
    val tailX = cx - s * 0.6f
    val tailY = cy + s * 0.35f
    for (i in 0 until 5) {
        val t = (i + 1).toFloat() / 5f
        val endX = headX + (tailX - headX) * t
        val endY = headY + (tailY - headY) * t
        val trail = Path().apply {
            moveTo(headX, headY)
            lineTo(endX, endY)
        }
        drawPath(trail, gradient, style = Stroke(sw * (1f - t * 0.7f), cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------------------
// 20. Threshold — doorway arch
// ---------------------------------------------------------------------------
private fun DrawScope.drawThreshold(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    // Left pillar
    val leftPillar = Path().apply {
        moveTo(cx - s * 0.35f, cy - s * 0.6f)
        lineTo(cx - s * 0.35f, cy + s * 0.6f)
    }
    drawPath(leftPillar, gradient, style = Stroke(sw * 1.3f, cap = StrokeCap.Round))

    // Right pillar
    val rightPillar = Path().apply {
        moveTo(cx + s * 0.35f, cy - s * 0.6f)
        lineTo(cx + s * 0.35f, cy + s * 0.6f)
    }
    drawPath(rightPillar, gradient, style = Stroke(sw * 1.3f, cap = StrokeCap.Round))

    // Arch — arc from -180 to 0 degrees (top half of ellipse)
    drawArc(
        brush = gradient,
        startAngle = -180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - s * 0.35f, cy - s * 0.6f - s * 0.25f),
        size = Size(s * 0.7f, s * 0.5f),
        style = Stroke(sw * 1.1f, cap = StrokeCap.Round),
    )

    // 3 small light rays from center of arch
    val archCx = cx
    val archCy = cy - s * 0.6f
    for (i in 0 until 3) {
        val angle = (-HALF_PI - 0.3f + i * 0.3f)
        val ray = Path().apply {
            moveTo(archCx + cos(angle) * s * 0.1f, archCy + sin(angle) * s * 0.1f)
            lineTo(archCx + cos(angle) * s * 0.3f, archCy + sin(angle) * s * 0.3f)
        }
        drawPath(ray, gradient, style = Stroke(sw * 0.6f, cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------------------
// 21. Circuit — hex nodes
// ---------------------------------------------------------------------------
private fun DrawScope.drawCircuit(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    val nodeR = s * 0.55f
    val nodes = (0 until 6).map { i ->
        val angle = i * TWO_PI / 6
        Offset(cx + cos(angle) * nodeR, cy + sin(angle) * nodeR)
    }

    // Connect adjacents
    for (i in 0 until 6) {
        val next = (i + 1) % 6
        val adj = Path().apply {
            moveTo(nodes[i].x, nodes[i].y)
            lineTo(nodes[next].x, nodes[next].y)
        }
        drawPath(adj, gradient, style = Stroke(sw, cap = StrokeCap.Round))
    }

    // Connect alternates (skip 1)
    for (i in 0 until 6) {
        val skip = (i + 2) % 6
        val alt = Path().apply {
            moveTo(nodes[i].x, nodes[i].y)
            lineTo(nodes[skip].x, nodes[skip].y)
        }
        drawPath(alt, gradient, style = Stroke(sw * 0.6f, cap = StrokeCap.Round))
    }

    // Filled dots at each node
    nodes.forEach { node ->
        drawCircle(gradient, radius = s * 0.04f, center = node)
    }
}

// ---------------------------------------------------------------------------
// 22. Apex — mountain + flag
// ---------------------------------------------------------------------------
private fun DrawScope.drawApex(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    // Mountain
    val mountain = Path().apply {
        moveTo(cx - s * 0.75f, cy + s * 0.55f)
        lineTo(cx, cy - s * 0.5f)
        lineTo(cx + s * 0.75f, cy + s * 0.55f)
    }
    drawPath(mountain, gradient, style = Stroke(sw * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Flag pole
    val pole = Path().apply {
        moveTo(cx, cy - s * 0.5f)
        lineTo(cx, cy - s * 0.85f)
    }
    drawPath(pole, gradient, style = Stroke(sw * 0.8f, cap = StrokeCap.Round))

    // Flag triangle (filled)
    val flag = Path().apply {
        moveTo(cx, cy - s * 0.85f)
        lineTo(cx + s * 0.3f, cy - s * 0.75f)
        lineTo(cx, cy - s * 0.65f)
        close()
    }
    drawPath(flag, gradient)
}

// ---------------------------------------------------------------------------
// 23. Forge — anvil + hammer + sparks
// ---------------------------------------------------------------------------
private fun DrawScope.drawForge(center: Offset, radius: Float, gradient: Brush, sw: Float) {
    val s = radius * 0.85f
    val cx = center.x
    val cy = center.y

    // Anvil
    val anvilPts = listOf(
        Offset(cx - s * 0.5f, cy + s * 0.15f),
        Offset(cx - s * 0.35f, cy - s * 0.1f),
        Offset(cx + s * 0.35f, cy - s * 0.1f),
        Offset(cx + s * 0.5f, cy + s * 0.15f),
        Offset(cx + s * 0.3f, cy + s * 0.4f),
        Offset(cx - s * 0.3f, cy + s * 0.4f),
    )
    val anvil = Path().apply {
        moveTo(anvilPts[0].x, anvilPts[0].y)
        anvilPts.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(anvil, gradient, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    // Hammer handle
    val handle = Path().apply {
        moveTo(cx, cy - s * 0.15f)
        lineTo(cx, cy - s * 0.65f)
    }
    drawPath(handle, gradient, style = Stroke(sw, cap = StrokeCap.Round))

    // Hammer head (filled rectangle)
    val head = Path().apply {
        moveTo(cx - s * 0.2f, cy - s * 0.65f)
        lineTo(cx + s * 0.2f, cy - s * 0.65f)
        lineTo(cx + s * 0.2f, cy - s * 0.5f)
        lineTo(cx - s * 0.2f, cy - s * 0.5f)
        close()
    }
    drawPath(head, gradient)

    // 3 spark dots near hammer-anvil contact
    val sparkPositions = listOf(
        Offset(cx - s * 0.15f, cy - s * 0.1f),
        Offset(cx + s * 0.1f, cy - s * 0.18f),
        Offset(cx + s * 0.2f, cy - s * 0.05f),
    )
    sparkPositions.forEach { pos ->
        drawCircle(gradient, radius = s * 0.04f, center = pos)
    }
}
