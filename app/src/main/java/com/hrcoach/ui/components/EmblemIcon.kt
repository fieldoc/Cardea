package com.hrcoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextTertiary

// ─── EmblemDef ───────────────────────────────────────────────────────────────

data class EmblemDef(
    val displayName: String,
    val draw: DrawScope.(size: Size, brush: Brush) -> Unit
)

// ─── EmblemRegistry ──────────────────────────────────────────────────────────

object EmblemRegistry {

    private val registry: LinkedHashMap<String, EmblemDef> = linkedMapOf(

        "pulse" to EmblemDef("Pulse") { size, brush ->
            val w = size.width; val h = size.height
            val path = Path().apply {
                moveTo(w * 0.05f, h * 0.50f)
                lineTo(w * 0.20f, h * 0.50f)
                lineTo(w * 0.30f, h * 0.30f)
                lineTo(w * 0.40f, h * 0.75f)
                lineTo(w * 0.50f, h * 0.15f)
                lineTo(w * 0.60f, h * 0.65f)
                lineTo(w * 0.68f, h * 0.40f)
                lineTo(w * 0.75f, h * 0.50f)
                lineTo(w * 0.95f, h * 0.50f)
            }
            drawPath(path, brush, style = Stroke(width = w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        },

        "bolt" to EmblemDef("Bolt") { size, brush ->
            val w = size.width; val h = size.height
            val path = Path().apply {
                moveTo(w * 0.60f, h * 0.05f)
                lineTo(w * 0.28f, h * 0.48f)
                lineTo(w * 0.48f, h * 0.48f)
                lineTo(w * 0.40f, h * 0.95f)
                lineTo(w * 0.72f, h * 0.52f)
                lineTo(w * 0.52f, h * 0.52f)
                close()
            }
            drawPath(path, brush)
        },

        "summit" to EmblemDef("Summit") { size, brush ->
            val w = size.width; val h = size.height
            // Left peak (smaller)
            val left = Path().apply {
                moveTo(w * 0.05f, h * 0.88f)
                lineTo(w * 0.35f, h * 0.35f)
                lineTo(w * 0.60f, h * 0.88f)
                close()
            }
            // Right peak (taller)
            val right = Path().apply {
                moveTo(w * 0.38f, h * 0.88f)
                lineTo(w * 0.68f, h * 0.12f)
                lineTo(w * 0.95f, h * 0.88f)
                close()
            }
            drawPath(left, brush)
            drawPath(right, brush)
        },

        "flame" to EmblemDef("Flame") { size, brush ->
            val w = size.width; val h = size.height
            val outer = Path().apply {
                moveTo(w * 0.50f, h * 0.05f)
                cubicTo(w * 0.75f, h * 0.20f, w * 0.90f, h * 0.45f, w * 0.85f, h * 0.65f)
                cubicTo(w * 0.80f, h * 0.85f, w * 0.62f, h * 0.95f, w * 0.50f, h * 0.95f)
                cubicTo(w * 0.38f, h * 0.95f, w * 0.20f, h * 0.85f, w * 0.15f, h * 0.65f)
                cubicTo(w * 0.10f, h * 0.45f, w * 0.25f, h * 0.20f, w * 0.50f, h * 0.05f)
                close()
            }
            drawPath(outer, brush)
            // Inner dark cutout oval
            drawOval(
                color = Color(0xFF050505),
                topLeft = Offset(w * 0.35f, h * 0.55f),
                size = Size(w * 0.30f, h * 0.28f)
            )
        },

        "compass" to EmblemDef("Compass") { size, brush ->
            val w = size.width; val h = size.height
            // N point
            val north = Path().apply {
                moveTo(w * 0.50f, h * 0.05f)
                lineTo(w * 0.40f, h * 0.40f)
                lineTo(w * 0.60f, h * 0.40f)
                close()
            }
            // S point
            val south = Path().apply {
                moveTo(w * 0.50f, h * 0.95f)
                lineTo(w * 0.40f, h * 0.60f)
                lineTo(w * 0.60f, h * 0.60f)
                close()
            }
            // W point
            val west = Path().apply {
                moveTo(w * 0.05f, h * 0.50f)
                lineTo(w * 0.40f, h * 0.40f)
                lineTo(w * 0.40f, h * 0.60f)
                close()
            }
            // E point
            val east = Path().apply {
                moveTo(w * 0.95f, h * 0.50f)
                lineTo(w * 0.60f, h * 0.40f)
                lineTo(w * 0.60f, h * 0.60f)
                close()
            }
            drawPath(north, brush)
            drawPath(south, brush)
            drawPath(west, brush)
            drawPath(east, brush)
            drawCircle(brush, radius = w * 0.08f, center = Offset(w * 0.50f, h * 0.50f))
        },

        "shield" to EmblemDef("Shield") { size, brush ->
            val w = size.width; val h = size.height
            val sw = w * 0.065f
            val shield = Path().apply {
                moveTo(w * 0.50f, h * 0.05f)
                lineTo(w * 0.92f, h * 0.22f)
                lineTo(w * 0.92f, h * 0.58f)
                cubicTo(w * 0.92f, h * 0.78f, w * 0.72f, h * 0.92f, w * 0.50f, h * 0.97f)
                cubicTo(w * 0.28f, h * 0.92f, w * 0.08f, h * 0.78f, w * 0.08f, h * 0.58f)
                lineTo(w * 0.08f, h * 0.22f)
                close()
            }
            drawPath(shield, brush, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Cross
            drawLine(brush, start = Offset(w * 0.50f, h * 0.28f), end = Offset(w * 0.50f, h * 0.72f), strokeWidth = sw)
            drawLine(brush, start = Offset(w * 0.28f, h * 0.50f), end = Offset(w * 0.72f, h * 0.50f), strokeWidth = sw)
        },

        "ascent" to EmblemDef("Ascent") { size, brush ->
            val w = size.width; val h = size.height
            val sw = w * 0.065f
            // Outer chevron
            val outer = Path().apply {
                moveTo(w * 0.10f, h * 0.72f)
                lineTo(w * 0.50f, h * 0.28f)
                lineTo(w * 0.90f, h * 0.72f)
            }
            // Inner chevron
            val inner = Path().apply {
                moveTo(w * 0.22f, h * 0.88f)
                lineTo(w * 0.50f, h * 0.52f)
                lineTo(w * 0.78f, h * 0.88f)
            }
            drawPath(outer, brush, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(inner, brush, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        },

        "crown" to EmblemDef("Crown") { size, brush ->
            val w = size.width; val h = size.height
            val crown = Path().apply {
                // Base bar
                moveTo(w * 0.10f, h * 0.78f)
                lineTo(w * 0.90f, h * 0.78f)
                lineTo(w * 0.90f, h * 0.62f)
                // Right point
                lineTo(w * 0.75f, h * 0.35f)
                // Middle (tallest) point
                lineTo(w * 0.50f, h * 0.20f)
                // Left point
                lineTo(w * 0.25f, h * 0.35f)
                lineTo(w * 0.10f, h * 0.62f)
                close()
            }
            drawPath(crown, brush)
        },

        "orbit" to EmblemDef("Orbit") { size, brush ->
            val w = size.width; val h = size.height
            val sw = w * 0.055f
            // Center circle
            drawCircle(brush, radius = w * 0.10f, center = Offset(w * 0.50f, h * 0.50f))
            // Ring 1 (tilted -30 degrees) — approximated as oval
            val ring1 = Path().apply {
                addOval(Rect(Offset(w * 0.08f, h * 0.20f), Size(w * 0.84f, h * 0.60f)))
            }
            drawPath(ring1, brush, style = Stroke(width = sw))
            // Ring 2 (tilted 30 degrees) — different oval ratio
            val ring2 = Path().apply {
                addOval(Rect(Offset(w * 0.20f, h * 0.10f), Size(w * 0.60f, h * 0.80f)))
            }
            drawPath(ring2, brush, style = Stroke(width = sw))
        },

        "infinity" to EmblemDef("Infinity") { size, brush ->
            val w = size.width; val h = size.height
            val sw = w * 0.065f
            val path = Path().apply {
                // Left lobe
                moveTo(w * 0.50f, h * 0.50f)
                cubicTo(w * 0.50f, h * 0.20f, w * 0.10f, h * 0.20f, w * 0.10f, h * 0.50f)
                cubicTo(w * 0.10f, h * 0.80f, w * 0.50f, h * 0.80f, w * 0.50f, h * 0.50f)
                // Right lobe
                cubicTo(w * 0.50f, h * 0.20f, w * 0.90f, h * 0.20f, w * 0.90f, h * 0.50f)
                cubicTo(w * 0.90f, h * 0.80f, w * 0.50f, h * 0.80f, w * 0.50f, h * 0.50f)
            }
            drawPath(path, brush, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        },

        "diamond" to EmblemDef("Diamond") { size, brush ->
            val w = size.width; val h = size.height
            val sw = w * 0.055f
            // Outer gem outline
            val gem = Path().apply {
                moveTo(w * 0.50f, h * 0.05f)  // top
                lineTo(w * 0.92f, h * 0.38f)  // upper-right
                lineTo(w * 0.50f, h * 0.95f)  // bottom point
                lineTo(w * 0.08f, h * 0.38f)  // upper-left
                close()
            }
            drawPath(gem, brush, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Horizontal facet line
            drawLine(brush, start = Offset(w * 0.08f, h * 0.38f), end = Offset(w * 0.92f, h * 0.38f), strokeWidth = sw)
            // Facet lines from top to corners
            drawLine(brush, start = Offset(w * 0.50f, h * 0.05f), end = Offset(w * 0.30f, h * 0.38f), strokeWidth = sw * 0.7f)
            drawLine(brush, start = Offset(w * 0.50f, h * 0.05f), end = Offset(w * 0.70f, h * 0.38f), strokeWidth = sw * 0.7f)
            // Center facet to bottom point
            drawLine(brush, start = Offset(w * 0.50f, h * 0.38f), end = Offset(w * 0.50f, h * 0.95f), strokeWidth = sw * 0.7f)
        },

        "nova" to EmblemDef("Nova") { size, brush ->
            val w = size.width; val h = size.height
            val cx = w * 0.50f; val cy = h * 0.50f
            val outer = w * 0.48f; val inner = w * 0.22f
            val path = Path()
            val points = 6
            for (i in 0 until points * 2) {
                val angle = Math.PI * i / points - Math.PI / 2
                val radius = if (i % 2 == 0) outer else inner
                val x = cx + (radius * Math.cos(angle)).toFloat()
                val y = cy + (radius * Math.sin(angle)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, brush)
        },

        "heart" to EmblemDef("Heart") { size, brush ->
            val w = size.width; val h = size.height
            val heart = Path().apply {
                moveTo(w * 0.50f, h * 0.88f)
                cubicTo(w * 0.50f, h * 0.88f, w * 0.08f, h * 0.58f, w * 0.08f, h * 0.35f)
                cubicTo(w * 0.08f, h * 0.12f, w * 0.28f, h * 0.05f, w * 0.50f, h * 0.28f)
                cubicTo(w * 0.72f, h * 0.05f, w * 0.92f, h * 0.12f, w * 0.92f, h * 0.35f)
                cubicTo(w * 0.92f, h * 0.58f, w * 0.50f, h * 0.88f, w * 0.50f, h * 0.88f)
                close()
            }
            drawPath(heart, brush)
        },

        "wave" to EmblemDef("Wave") { size, brush ->
            val w = size.width; val h = size.height
            val sw = w * 0.065f
            // Upper wave (bold)
            val wave1 = Path().apply {
                moveTo(w * 0.05f, h * 0.40f)
                cubicTo(w * 0.20f, h * 0.20f, w * 0.35f, h * 0.20f, w * 0.50f, h * 0.40f)
                cubicTo(w * 0.65f, h * 0.60f, w * 0.80f, h * 0.60f, w * 0.95f, h * 0.40f)
            }
            drawPath(wave1, brush, style = Stroke(width = sw, cap = StrokeCap.Round))
            // Lower wave (faint)
            val wave2 = Path().apply {
                moveTo(w * 0.05f, h * 0.62f)
                cubicTo(w * 0.20f, h * 0.45f, w * 0.35f, h * 0.45f, w * 0.50f, h * 0.62f)
                cubicTo(w * 0.65f, h * 0.78f, w * 0.80f, h * 0.78f, w * 0.95f, h * 0.62f)
            }
            drawPath(wave2, Brush.linearGradient(listOf(Color(0x80FF4D5A), Color(0x8000E5FF))),
                style = Stroke(width = sw * 0.65f, cap = StrokeCap.Round))
        },

        "spiral" to EmblemDef("Spiral") { size, brush ->
            val w = size.width; val h = size.height
            val sw = w * 0.055f
            val cx = w * 0.50f; val cy = h * 0.50f
            val path = Path()
            var first = true
            val turns = 3.0
            val steps = 120
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val angle = (t * turns * 2 * Math.PI - Math.PI / 2).toFloat()
                val radius = (t * w * 0.43f).toFloat()
                val x = cx + radius * Math.cos(angle.toDouble()).toFloat()
                val y = cy + radius * Math.sin(angle.toDouble()).toFloat()
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            }
            drawPath(path, brush, style = Stroke(width = sw, cap = StrokeCap.Round))
        },

        "trident" to EmblemDef("Trident") { size, brush ->
            val w = size.width; val h = size.height
            val sw = w * 0.065f
            // Shaft
            drawLine(brush, start = Offset(w * 0.50f, h * 0.15f), end = Offset(w * 0.50f, h * 0.92f), strokeWidth = sw, cap = StrokeCap.Round)
            // Crossbar
            drawLine(brush, start = Offset(w * 0.22f, h * 0.55f), end = Offset(w * 0.78f, h * 0.55f), strokeWidth = sw, cap = StrokeCap.Round)
            // Left prong
            val left = Path().apply {
                moveTo(w * 0.22f, h * 0.55f)
                lineTo(w * 0.22f, h * 0.30f)
                cubicTo(w * 0.22f, h * 0.18f, w * 0.36f, h * 0.15f, w * 0.36f, h * 0.28f)
            }
            drawPath(left, brush, style = Stroke(width = sw, cap = StrokeCap.Round))
            // Center prong
            val center = Path().apply {
                moveTo(w * 0.50f, h * 0.15f)
                cubicTo(w * 0.50f, h * 0.08f, w * 0.64f, h * 0.08f, w * 0.58f, h * 0.20f)
            }
            drawPath(center, brush, style = Stroke(width = sw * 0.0f, cap = StrokeCap.Round))
            // Right prong
            val right = Path().apply {
                moveTo(w * 0.78f, h * 0.55f)
                lineTo(w * 0.78f, h * 0.30f)
                cubicTo(w * 0.78f, h * 0.18f, w * 0.64f, h * 0.15f, w * 0.64f, h * 0.28f)
            }
            drawPath(right, brush, style = Stroke(width = sw, cap = StrokeCap.Round))
            // Top prong (center)
            drawLine(brush, start = Offset(w * 0.50f, h * 0.15f), end = Offset(w * 0.50f, h * 0.55f), strokeWidth = sw, cap = StrokeCap.Round)
        },

        "comet" to EmblemDef("Comet") { size, brush ->
            val w = size.width; val h = size.height
            val sw = w * 0.055f
            // Comet head (upper-right)
            drawCircle(brush, radius = w * 0.14f, center = Offset(w * 0.72f, h * 0.28f))
            // Tail lines (lower-left)
            drawLine(brush, start = Offset(w * 0.60f, h * 0.40f), end = Offset(w * 0.12f, h * 0.88f), strokeWidth = sw * 1.2f, cap = StrokeCap.Round)
            drawLine(brush, start = Offset(w * 0.62f, h * 0.50f), end = Offset(w * 0.20f, h * 0.88f), strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
            drawLine(brush, start = Offset(w * 0.64f, h * 0.58f), end = Offset(w * 0.32f, h * 0.88f), strokeWidth = sw * 0.5f, cap = StrokeCap.Round)
        },

        "prism" to EmblemDef("Prism") { size, brush ->
            val w = size.width; val h = size.height
            val sw = w * 0.055f
            // Triangle outline
            val triangle = Path().apply {
                moveTo(w * 0.50f, h * 0.05f)
                lineTo(w * 0.92f, h * 0.88f)
                lineTo(w * 0.08f, h * 0.88f)
                close()
            }
            drawPath(triangle, brush, style = Stroke(width = sw, join = StrokeJoin.Round))
            // Refracted rays from right side
            val rayColors = listOf(Color(0xFFFF4D5A), Color(0xFF4D61FF), Color(0xFF00E5FF))
            val rayAngles = listOf(0.08f, 0.16f, 0.24f)
            rayAngles.forEachIndexed { i, dy ->
                drawLine(
                    color = rayColors[i],
                    start = Offset(w * 0.70f, h * (0.48f + dy)),
                    end = Offset(w * 0.94f, h * (0.48f + dy)),
                    strokeWidth = sw * 0.8f,
                    cap = StrokeCap.Round
                )
            }
        },

        "ripple" to EmblemDef("Ripple") { size, brush ->
            val w = size.width; val h = size.height
            val cx = Offset(w * 0.50f, h * 0.50f)
            val sw = w * 0.055f
            // Center dot
            drawCircle(brush, radius = w * 0.06f, center = cx)
            // 3 concentric rings of decreasing opacity
            drawCircle(brush, radius = w * 0.18f, center = cx, style = Stroke(width = sw))
            drawCircle(
                brush = Brush.linearGradient(listOf(Color(0xAAFF4D5A), Color(0xAA00E5FF))),
                radius = w * 0.32f, center = cx, style = Stroke(width = sw * 0.75f)
            )
            drawCircle(
                brush = Brush.linearGradient(listOf(Color(0x55FF4D5A), Color(0x5500E5FF))),
                radius = w * 0.45f, center = cx, style = Stroke(width = sw * 0.5f)
            )
        },

        "crescent" to EmblemDef("Crescent") { size, brush ->
            val w = size.width; val h = size.height
            // Crescent: outer circle minus inner circle offset to the right
            val outer = Path().apply {
                addOval(Rect(Offset(w * 0.10f, h * 0.10f), Size(w * 0.65f, h * 0.65f)))
            }
            val inner = Path().apply {
                addOval(Rect(Offset(w * 0.22f, h * 0.10f), Size(w * 0.60f, h * 0.60f)))
            }
            val crescent = Path().apply {
                op(outer, inner, androidx.compose.ui.graphics.PathOperation.Difference)
            }
            drawPath(crescent, brush)
            // Two star dots
            drawCircle(brush, radius = w * 0.04f, center = Offset(w * 0.82f, h * 0.30f))
            drawCircle(brush, radius = w * 0.025f, center = Offset(w * 0.90f, h * 0.50f))
        },

        "wings" to EmblemDef("Wings") { size, brush ->
            val w = size.width; val h = size.height
            // Left wing
            val leftWing = Path().apply {
                moveTo(w * 0.50f, h * 0.50f)
                cubicTo(w * 0.42f, h * 0.38f, w * 0.18f, h * 0.28f, w * 0.05f, h * 0.42f)
                cubicTo(w * 0.02f, h * 0.55f, w * 0.15f, h * 0.65f, w * 0.30f, h * 0.60f)
                cubicTo(w * 0.08f, h * 0.72f, w * 0.02f, h * 0.80f, w * 0.10f, h * 0.85f)
                cubicTo(w * 0.20f, h * 0.90f, w * 0.40f, h * 0.72f, w * 0.50f, h * 0.58f)
                close()
            }
            // Right wing (mirrored)
            val rightWing = Path().apply {
                moveTo(w * 0.50f, h * 0.50f)
                cubicTo(w * 0.58f, h * 0.38f, w * 0.82f, h * 0.28f, w * 0.95f, h * 0.42f)
                cubicTo(w * 0.98f, h * 0.55f, w * 0.85f, h * 0.65f, w * 0.70f, h * 0.60f)
                cubicTo(w * 0.92f, h * 0.72f, w * 0.98f, h * 0.80f, w * 0.90f, h * 0.85f)
                cubicTo(w * 0.80f, h * 0.90f, w * 0.60f, h * 0.72f, w * 0.50f, h * 0.58f)
                close()
            }
            drawPath(leftWing, brush)
            drawPath(rightWing, brush)
            // Center body dot
            drawCircle(brush, radius = w * 0.06f, center = Offset(w * 0.50f, h * 0.50f))
        },

        "helix" to EmblemDef("Helix") { size, brush ->
            val w = size.width; val h = size.height
            val sw = w * 0.06f
            // Two intertwined S-curves
            val strand1 = Path().apply {
                moveTo(w * 0.35f, h * 0.05f)
                cubicTo(w * 0.80f, h * 0.20f, w * 0.20f, h * 0.50f, w * 0.65f, h * 0.50f)
                cubicTo(w * 0.80f, h * 0.50f, w * 0.80f, h * 0.80f, w * 0.65f, h * 0.95f)
            }
            val strand2 = Path().apply {
                moveTo(w * 0.65f, h * 0.05f)
                cubicTo(w * 0.20f, h * 0.20f, w * 0.80f, h * 0.50f, w * 0.35f, h * 0.50f)
                cubicTo(w * 0.20f, h * 0.50f, w * 0.20f, h * 0.80f, w * 0.35f, h * 0.95f)
            }
            drawPath(strand1, brush, style = Stroke(width = sw, cap = StrokeCap.Round))
            drawPath(strand2, Brush.linearGradient(listOf(Color(0xFFFF2DA6), Color(0xFF4D61FF))),
                style = Stroke(width = sw, cap = StrokeCap.Round))
        },

        "focus" to EmblemDef("Focus") { size, brush ->
            val w = size.width; val h = size.height
            val cx = Offset(w * 0.50f, h * 0.50f)
            val sw = w * 0.055f
            // Two concentric circles
            drawCircle(brush, radius = w * 0.42f, center = cx, style = Stroke(width = sw))
            drawCircle(brush, radius = w * 0.25f, center = cx, style = Stroke(width = sw))
            // Center dot
            drawCircle(brush, radius = w * 0.05f, center = cx)
            // 4 tick marks N/S/E/W
            val tickOuter = w * 0.48f; val tickInner = w * 0.42f
            listOf(0.0, Math.PI / 2, Math.PI, 3 * Math.PI / 2).forEach { angle ->
                val outerPt = Offset(cx.x + (tickOuter * Math.cos(angle - Math.PI / 2)).toFloat(),
                    cx.y + (tickOuter * Math.sin(angle - Math.PI / 2)).toFloat())
                val innerPt = Offset(cx.x + (tickInner * Math.cos(angle - Math.PI / 2)).toFloat(),
                    cx.y + (tickInner * Math.sin(angle - Math.PI / 2)).toFloat())
                drawLine(brush, start = innerPt, end = outerPt, strokeWidth = sw, cap = StrokeCap.Round)
            }
        },

        "laurel" to EmblemDef("Laurel") { size, brush ->
            val w = size.width; val h = size.height
            val sw = w * 0.055f
            // Left branch
            val leftBranch = Path().apply {
                moveTo(w * 0.50f, h * 0.92f)
                cubicTo(w * 0.25f, h * 0.80f, w * 0.08f, h * 0.55f, w * 0.12f, h * 0.25f)
            }
            drawPath(leftBranch, brush, style = Stroke(width = sw, cap = StrokeCap.Round))
            // Left leaves
            listOf(
                Triple(0.20f, 0.72f, -40.0),
                Triple(0.14f, 0.52f, -55.0),
                Triple(0.14f, 0.35f, -65.0)
            ).forEach { (bx, by, _) ->
                val lx = bx.toFloat(); val ly = by.toFloat()
                val leaf = Path().apply {
                    moveTo(w * lx, h * ly)
                    cubicTo(w * (lx - 0.10f), h * (ly - 0.06f), w * (lx - 0.08f), h * (ly + 0.04f), w * lx, h * ly)
                }
                drawPath(leaf, brush, style = Stroke(width = sw * 0.8f, cap = StrokeCap.Round))
            }
            // Right branch (mirrored)
            val rightBranch = Path().apply {
                moveTo(w * 0.50f, h * 0.92f)
                cubicTo(w * 0.75f, h * 0.80f, w * 0.92f, h * 0.55f, w * 0.88f, h * 0.25f)
            }
            drawPath(rightBranch, brush, style = Stroke(width = sw, cap = StrokeCap.Round))
            // Right leaves
            listOf(0.80f to 0.72f, 0.86f to 0.52f, 0.86f to 0.35f).forEach { (bx, by) ->
                val leaf = Path().apply {
                    moveTo(w * bx, h * by)
                    cubicTo(w * (bx + 0.10f), h * (by - 0.06f), w * (bx + 0.08f), h * (by + 0.04f), w * bx, h * by)
                }
                drawPath(leaf, brush, style = Stroke(width = sw * 0.8f, cap = StrokeCap.Round))
            }
            // Top dot
            drawCircle(brush, radius = w * 0.04f, center = Offset(w * 0.50f, h * 0.14f))
        }
    )

    /** Ordered list of all 24 emblem IDs. */
    val allIds: List<String> = registry.keys.toList()

    /** Returns the display name for [id], or null if not found. */
    fun displayName(id: String): String? = registry[id]?.displayName

    /** Returns the draw lambda for [id], or null if not found. */
    fun draw(id: String): (DrawScope.(Size, Brush) -> Unit)? = registry[id]?.draw
}

// ─── EmblemIcon ──────────────────────────────────────────────────────────────

/**
 * Renders a single Canvas-drawn athletic emblem at [size].
 *
 * @param emblemId  ID from [EmblemRegistry.allIds]
 * @param size      Rendered size of the canvas
 * @param tinted    true = uses [CardeaGradient]; false = subtle gray brush
 */
@Composable
fun EmblemIcon(
    emblemId: String,
    size: Dp,
    modifier: Modifier = Modifier,
    tinted: Boolean = true
) {
    val drawFn = EmblemRegistry.draw(emblemId)
    Canvas(modifier = modifier.size(size)) {
        if (drawFn != null) {
            val brush: Brush = if (tinted) {
                CardeaGradient
            } else {
                Brush.linearGradient(listOf(CardeaTextTertiary, CardeaTextTertiary))
            }
            drawFn(this.size, brush)
        }
    }
}

// ─── EmblemIconWithRing ───────────────────────────────────────────────────────

/**
 * Renders [EmblemIcon] wrapped in the Cardea gradient ring pattern:
 * outer gradient circle → inner dark circle → icon centered inside.
 *
 * @param emblemId  ID from [EmblemRegistry.allIds]
 * @param ringSize  Outer ring diameter
 * @param iconSize  Icon diameter (should be smaller than ringSize)
 */
@Composable
fun EmblemIconWithRing(
    emblemId: String,
    ringSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(ringSize)
            .clip(CircleShape)
            .background(CardeaGradient)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ringSize - 3.dp)
                .clip(CircleShape)
                .background(CardeaBgPrimary)
        ) {
            EmblemIcon(
                emblemId = emblemId,
                size = iconSize,
                tinted = true
            )
        }
    }
}
