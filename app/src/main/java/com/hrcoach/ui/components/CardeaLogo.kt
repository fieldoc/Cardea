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
    val gradientColorStops = remember {
        arrayOf(
            0.00f to GradientRed,
            0.35f to GradientPink,
            0.65f to GradientBlue,
            1.00f to GradientCyan
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "logoAnim")

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

        val outerLength = PathMeasure().apply { setPath(outerHeart, false) }.length
        val hairpinLength = hairpin?.let {
            PathMeasure().apply { setPath(it, false) }.length
        } ?: 0f
        val totalLength = outerLength + hairpinLength

        // Glow layer — BlurMaskFilter soft bloom, drawn underneath stroke
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

        // Stroke layer — dash-phase draw-on animation
        // Leading dot: always show a tiny visible segment so frame-0 is not blank
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

/**
 * Smooth rounded heart — two symmetric bezier lobes meeting at the bottom point.
 * Coordinates are fractions of [w] x [h].
 */
private fun buildOuterHeartPath(w: Float, h: Float): Path = Path().apply {
    moveTo(w * 0.50f, h * 0.90f)
    cubicTo(w * 0.50f, h * 0.90f, w * 0.08f, h * 0.62f, w * 0.08f, h * 0.38f)
    cubicTo(w * 0.08f, h * 0.14f, w * 0.28f, h * 0.06f, w * 0.50f, h * 0.30f)
    cubicTo(w * 0.72f, h * 0.06f, w * 0.92f, h * 0.14f, w * 0.92f, h * 0.38f)
    cubicTo(w * 0.92f, h * 0.62f, w * 0.50f, h * 0.90f, w * 0.50f, h * 0.90f)
    close()
}

/**
 * Hairpin loop inside the left lobe — the road crossover that gives the logo its
 * running-route identity. Starts near the apex, loops around the inside of the
 * left lobe, exits back toward center.
 */
private fun buildHairpinPath(w: Float, h: Float): Path = Path().apply {
    moveTo(w * 0.50f, h * 0.30f)
    cubicTo(w * 0.42f, h * 0.18f, w * 0.22f, h * 0.14f, w * 0.20f, h * 0.30f)
    cubicTo(w * 0.18f, h * 0.44f, w * 0.32f, h * 0.50f, w * 0.44f, h * 0.42f)
}
