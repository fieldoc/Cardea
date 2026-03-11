package com.hrcoach.ui.splash

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.components.CardeaLogo
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextSecondary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700, delayMillis = 300, easing = LinearOutSlowInEasing),
        label = "wordmark"
    )
    val taglineAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700, delayMillis = 500, easing = LinearOutSlowInEasing),
        label = "tagline"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(2400L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaBgPrimary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CardeaLogo(
                size = 110.dp,
                animate = true
            )
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "CARDEA",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp
                ),
                color = CardeaTextPrimary,
                modifier = Modifier.graphicsLayer { alpha = wordmarkAlpha }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "HEART-LED PERFORMANCE",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = CardeaTextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.graphicsLayer { alpha = taglineAlpha }
            )
        }
    }
}
