package com.hrcoach.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.components.CardeaLogo
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // Phase-based sequencer: 0=hidden, 1=logo in, 2=wordmark, 3=tagline+ekg
    var phase by remember { mutableIntStateOf(0) }

    // Ambient radial glow behind the logo
    val glowAlpha by animateFloatAsState(
        targetValue = if (phase >= 1) 0.18f else 0f,
        animationSpec = tween(1400, easing = FastOutSlowInEasing),
        label = "glow"
    )

    // Logo: spring scale from 0.72 → 1.0 (slight bounce = heartbeat thump)
    val logoScale by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0.72f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(350),
        label = "logoAlpha"
    )

    // Wordmark: slide from right + fade
    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(600, easing = LinearOutSlowInEasing),
        label = "wordmarkAlpha"
    )
    val wordmarkSlide by animateFloatAsState(
        targetValue = if (phase >= 2) 0f else 28f,
        animationSpec = tween(550, easing = LinearOutSlowInEasing),
        label = "wordmarkSlide"
    )

    // Tagline: rise up + fade
    val taglineAlpha by animateFloatAsState(
        targetValue = if (phase >= 3) 1f else 0f,
        animationSpec = tween(500, easing = LinearOutSlowInEasing),
        label = "taglineAlpha"
    )
    val taglineRise by animateFloatAsState(
        targetValue = if (phase >= 3) 0f else 10f,
        animationSpec = tween(500, easing = LinearOutSlowInEasing),
        label = "taglineRise"
    )

    // EKG line: draws outward from center once tagline is visible
    val ekgProgress by animateFloatAsState(
        targetValue = if (phase >= 3) 1f else 0f,
        animationSpec = tween(900, easing = LinearOutSlowInEasing),
        label = "ekgProgress"
    )

    LaunchedEffect(Unit) {
        phase = 1
        delay(340L)
        phase = 2
        delay(270L)
        phase = 3
        delay(2500L)  // enough for the ECG→heart morph to complete (2800ms cycle, morph finishes at ~1.6s + hold)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaTheme.colors.bgPrimary),
        contentAlignment = Alignment.Center
    ) {
        // Radial ambient glow — brand blue/pink bleeding from center
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to GradientBlue.copy(alpha = glowAlpha * 0.85f),
                        0.45f to GradientPink.copy(alpha = glowAlpha * 0.35f),
                        1.0f to Color.Transparent
                    ),
                    center = center,
                    radius = size.minDimension * 0.65f
                )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CardeaLogo(
                size = 110.dp,
                animate = true,
                cycleDurationMs = 2_800,
                modifier = Modifier.graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                    alpha = logoAlpha
                }
            )

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "CARDEA",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp
                ),
                color = CardeaTheme.colors.textPrimary,
                modifier = Modifier.graphicsLayer {
                    alpha = wordmarkAlpha
                    translationX = wordmarkSlide * density
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "HEART-LED PERFORMANCE",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = CardeaTheme.colors.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier.graphicsLayer {
                    alpha = taglineAlpha
                    translationY = -taglineRise * density
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            // EKG accent line — brand gradient drawing outward from center
            Canvas(
                modifier = Modifier
                    .width(180.dp)
                    .height(2.dp)
                    .graphicsLayer { alpha = taglineAlpha }
            ) {
                val halfW = size.width / 2f
                val drawnHalf = halfW * ekgProgress
                if (drawnHalf > 0f) {
                    drawLine(
                        brush = Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to GradientRed,
                                0.35f to GradientPink,
                                0.65f to GradientBlue,
                                1f to GradientCyan
                            ),
                            start = Offset(halfW - drawnHalf, 0f),
                            end = Offset(halfW + drawnHalf, 0f)
                        ),
                        start = Offset(halfW - drawnHalf, size.height / 2f),
                        end = Offset(halfW + drawnHalf, size.height / 2f),
                        strokeWidth = size.height,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
