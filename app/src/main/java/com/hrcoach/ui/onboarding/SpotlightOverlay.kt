package com.hrcoach.ui.onboarding

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.GradientCyan

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

/**
 * Spotlight overlay that dims everything except a circle at [targetOffset].
 * Shows a pulsing ring and a tooltip bubble above the spotlight.
 */
@Composable
fun SpotlightOverlay(
    targetOffset: Offset,
    spotlightRadius: Dp = 32.dp,
    ringColor: Color = GradientCyan,
    tooltipName: String,
    tooltipDescription: String,
    useGradientName: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { spotlightRadius.toPx() }

    val infiniteTransition = rememberInfiniteTransition(label = "spotlight")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringPulse",
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringAlpha",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim with circle cutout
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(Color.Black.copy(alpha = 0.75f))
            drawCircle(
                color = Color.Transparent,
                radius = radiusPx,
                center = targetOffset,
                blendMode = BlendMode.Clear,
            )
        }

        // Pulsing ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = ringColor.copy(alpha = ringAlpha),
                radius = radiusPx * ringScale,
                center = targetOffset,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // Tooltip bubble above spotlight
        if (targetOffset != Offset.Zero) {
            val tooltipY = targetOffset.y - with(density) { (spotlightRadius + 56.dp).toPx() }
            val tooltipX = targetOffset.x

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (tooltipX - with(density) { 100.dp.toPx() }).toInt(),
                            y = tooltipY.toInt(),
                        )
                    }
                    .width(200.dp)
                    .background(CardeaBgSecondary, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = tooltipName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (useGradientName) ringColor else CardeaTextPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tooltipDescription,
                        fontSize = 11.sp,
                        color = CardeaTextSecondary,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}
