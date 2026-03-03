package com.hrcoach.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.HrCoachThemeTokens

@Composable
fun HrRing(
    hr: Int,
    isConnected: Boolean,
    zoneColor: Color,
    pulseScale: Float,
    onConnectHr: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(160.dp)
            .scale(if (isConnected) pulseScale else 1f)
            .then(
                if (!isConnected) Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onConnectHr)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val strokePx = 8.dp.toPx()
            val radius = size.minDimension / 2f - strokePx / 2f
            if (isConnected) {
                drawCircle(
                    color = zoneColor.copy(alpha = 0.12f),
                    radius = radius,
                    style = Stroke(width = strokePx)
                )
                drawArc(
                    brush = CardeaGradient,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            } else {
                drawCircle(
                    color = CardeaTextTertiary,
                    radius = radius,
                    style = Stroke(width = strokePx)
                )
            }
        }

        if (isConnected) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (hr > 0) hr.toString() else "---",
                    style = MaterialTheme.typography.displayLarge,
                    color = CardeaTextPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "bpm",
                    style = MaterialTheme.typography.labelSmall,
                    color = HrCoachThemeTokens.subtleText
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(CardeaGradient)
                )
                Text(
                    text = "Connect HR",
                    style = MaterialTheme.typography.titleSmall,
                    color = CardeaTextPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "monitor",
                    style = MaterialTheme.typography.labelSmall,
                    color = CardeaTextSecondary
                )
            }
        }
    }
}
