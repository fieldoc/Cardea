package com.hrcoach.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
        drawOrbitRing(gradient, w, h)
        drawHeart(gradient, w, h)
        drawEcgLine(gradient, w, h)
    }
}

private fun DrawScope.drawOrbitRing(gradient: Brush, w: Float, h: Float) {
    val ringStroke = w * 0.06f
    withTransform({ rotate(-25f, pivot = Offset(w / 2f, h / 2f)) }) {
        drawOval(
            brush = gradient,
            topLeft = Offset(w * 0.02f, h * 0.30f),
            size = Size(w * 0.96f, h * 0.40f),
            style = Stroke(width = ringStroke * 2f, cap = StrokeCap.Round),
            alpha = 0.18f
        )
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
        val cx = w * 0.50f; val cy = h * 0.52f
        val hw = w * 0.30f; val hh = h * 0.26f
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
    drawPath(path = path, brush = gradient,
        style = Stroke(width = w * 0.06f, cap = StrokeCap.Round, join = StrokeJoin.Round), alpha = 0.20f)
    drawPath(path = path, brush = gradient,
        style = Stroke(width = w * 0.025f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
