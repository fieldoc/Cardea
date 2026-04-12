package com.hrcoach.ui.workout

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTheme

@Composable
fun HrRing(
    hr: Int,
    isConnected: Boolean,
    zoneColor: Color,
    pulseScale: Float,
    onConnectHr: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Only run glow animation when connected — avoids per-frame ticks when idle
    val glowAlpha = if (isConnected) {
        val infiniteTransition = rememberInfiniteTransition(label = "ringGlow")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glow"
        )
        alpha
    } else 0f

    val colors = CardeaTheme.colors
    Box(
        modifier = modifier
            .size(200.dp)
            .scale(if (isConnected) pulseScale else 1f)
            .then(
                if (!isConnected) Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onConnectHr)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        val disconnectedRingColor = colors.textTertiary
        Canvas(modifier = Modifier.size(200.dp)) {
            val strokePx = 6.dp.toPx()
            val segmentStrokePx = 3.dp.toPx()
            val radius = size.minDimension / 2f - strokePx / 2f
            
            if (isConnected) {
                // Background track (segmented)
                val segments = 60
                val sweep = 360f / segments
                for (i in 0 until segments) {
                    drawArc(
                        color = zoneColor.copy(alpha = 0.1f),
                        startAngle = i * sweep - 90f + 1f,
                        sweepAngle = sweep - 2f,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                    )
                }

                // Main progress ring
                drawArc(
                    brush = CardeaGradient,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    topLeft = androidx.compose.ui.geometry.Offset(strokePx / 2f, strokePx / 2f),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - strokePx,
                        size.height - strokePx
                    )
                )

                // Glow layer (soft behind the ring)
                drawCircle(
                    color = zoneColor.copy(alpha = 0.05f * glowAlpha),
                    radius = radius + 4.dp.toPx(),
                    style = Stroke(width = 12.dp.toPx())
                )
            } else {
                drawCircle(
                    color = disconnectedRingColor.copy(alpha = 0.5f),
                    radius = radius,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        if (isConnected) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (hr > 0) hr.toString() else "---",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace // Precision feel
                    ),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "BPM",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = zoneColor // Use zone color for the label for emphasis
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "CONNECT",
                    style = MaterialTheme.typography.titleSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardeaGradient)
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "HR MONITOR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}
