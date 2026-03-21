package com.hrcoach.ui.bootcamp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.theme.CardeaCtaGradient
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.GradientRed

@Composable
fun BootcampOnboardingCarousel(
    onStartSetup: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 5 })

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CardeaTheme.colors.bgPrimary)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> CarouselPageHook()
                1 -> CarouselPageProblem()
                2 -> CarouselPagePhases()
                3 -> CarouselPageWatches()
                4 -> CarouselPageCta(onStartSetup = onStartSetup)
            }
        }

        // Skip button — top right, visible on pages 0-3
        if (pagerState.currentPage < 4) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 8.dp)
            ) {
                Text(
                    text = "Skip",
                    color = CardeaTheme.colors.textTertiary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Dot indicator — bottom center
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(5) { index ->
                val isActive = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .size(if (isActive) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .then(
                            if (isActive) {
                                Modifier.background(CardeaGradient, CircleShape)
                            } else {
                                Modifier.background(
                                    CardeaTheme.colors.textTertiary.copy(alpha = 0.4f),
                                    CircleShape
                                )
                            }
                        )
                )
            }
        }
    }
}

// --- Card 1: The Hook ---

@Composable
private fun CarouselPageHook() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Your comeback starts here.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Cardea builds a running program around your heart rate \u2014 not a generic plan that assumes where you are.",
            style = MaterialTheme.typography.bodyLarge,
            color = CardeaTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))
        Text(
            text = "Swipe to see how it works",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textTertiary,
            textAlign = TextAlign.Center
        )
    }
}

// --- Card 2: The Problem ---

@Composable
private fun CarouselPageProblem() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "The hardest part of coming back? Not doing too much.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Your brain remembers your old pace. Your heart isn't there yet. Cardea watches your heart rate in real time and keeps you in the right zone.",
            style = MaterialTheme.typography.bodyLarge,
            color = CardeaTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// --- Card 3: How Phases Work ---

@Composable
private fun CarouselPagePhases() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "You'll move through phases as you get fitter.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        // Phase blocks
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhaseBlock("BASE", GradientCyan)
            PhaseArrow()
            PhaseBlock("BUILD", GradientBlue)
            PhaseArrow()
            PhaseBlock("PEAK", GradientPink)
            PhaseArrow()
            PhaseBlock("TAPER", GradientRed)
        }

        Spacer(modifier = Modifier.height(24.dp))

        val phaseDescriptions = listOf(
            "Base \u2014 Build aerobic foundation at easy effort",
            "Build \u2014 Add intensity and longer runs",
            "Peak \u2014 Race-specific sharpening",
            "Taper \u2014 Reduce load, stay fresh for race day"
        )
        phaseDescriptions.forEach { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTheme.colors.textSecondary,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "You don't choose when to advance \u2014 your body does.",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PhaseBlock(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun PhaseArrow() {
    Text(
        text = "\u203A",
        style = MaterialTheme.typography.bodyLarge,
        color = CardeaTheme.colors.textTertiary
    )
}

// --- Card 4: What It Watches ---

@Composable
private fun CarouselPageWatches() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Every run teaches Cardea about you.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        val items = listOf(
            "Training Load" to "How much stress your body is handling",
            "Recovery" to "Whether you're bouncing back between sessions",
            "Efficiency" to "How your pace-to-HR ratio improves over time"
        )
        items.forEach { (title, desc) ->
            WatchItem(title = title, description = desc)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "If you're adapting fast, it pushes. If you need more recovery, it pulls back.",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WatchItem(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Gradient dot + title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(brush = CardeaGradient)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = CardeaTheme.colors.textPrimary
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary,
            modifier = Modifier.padding(start = 18.dp)
        )
    }
}

// --- Card 5: CTA ---

@Composable
private fun CarouselPageCta(onStartSetup: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Setup takes about 2 minutes.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "We'll ask about your goal, your schedule, and where you are right now.",
            style = MaterialTheme.typography.bodyLarge,
            color = CardeaTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))

        // Gradient CTA button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(CardeaCtaGradient)
                .fillMaxWidth()
                .height(56.dp)
                .then(
                    Modifier.clip(RoundedCornerShape(16.dp))
                ),
            contentAlignment = Alignment.Center
        ) {
            TextButton(
                onClick = onStartSetup,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "Start Setup \u2192",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CardeaTheme.colors.onGradient
                )
            }
        }
    }
}
