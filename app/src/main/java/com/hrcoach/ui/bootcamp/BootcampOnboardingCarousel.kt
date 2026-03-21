package com.hrcoach.ui.bootcamp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hrcoach.ui.components.CardeaLogo
import com.hrcoach.ui.components.GlassCard
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
        CardeaLogo(size = 72.dp)
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Training that adapts to you.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Cardea builds a running programme around your heart rate \u2014 not a generic plan that assumes where you are.",
            style = MaterialTheme.typography.bodyLarge,
            color = CardeaTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
        Spacer(modifier = Modifier.height(40.dp))

        // Subtle gradient divider line
        GradientDivider()

        Spacer(modifier = Modifier.height(16.dp))
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
        // Canvas-drawn pulse line — on-brand HR visual
        PulseLine(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Most runners push too hard on easy days.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Pace alone doesn\u2019t tell you what\u2019s happening inside. Cardea tracks your heart rate in real time and keeps effort where it should be \u2014 so easy days stay easy and hard days count.",
                style = MaterialTheme.typography.bodyLarge,
                color = CardeaTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
            )
        }
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
            text = "You\u2019ll move through phases as you get fitter.",
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

        Spacer(modifier = Modifier.height(20.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            PhaseDescriptionRow(GradientCyan, "Base", "Aerobic foundation at easy effort")
            PhaseDescriptionRow(GradientBlue, "Build", "More intensity, longer runs")
            PhaseDescriptionRow(GradientPink, "Peak", "Race-specific sharpening")
            PhaseDescriptionRow(GradientRed, "Taper", "Reduced load, staying fresh")
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "You don\u2019t choose when to advance \u2014 your body does.",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PhaseDescriptionRow(color: Color, label: String, description: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        // Small colored bar accent
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary
        )
    }
}

@Composable
private fun PhaseBlock(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
    Canvas(modifier = Modifier.size(width = 12.dp, height = 12.dp)) {
        val midY = size.height / 2f
        val path = Path().apply {
            moveTo(0f, midY * 0.4f)
            lineTo(size.width * 0.7f, midY)
            lineTo(0f, midY * 1.6f)
        }
        drawPath(
            path,
            color = Color.White.copy(alpha = 0.25f),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
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
            text = "Cardea learns from every run.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        WatchItem(
            title = "Training Load",
            description = "How much stress your body is handling"
        )
        Spacer(modifier = Modifier.height(10.dp))
        WatchItem(
            title = "Recovery",
            description = "Whether you\u2019re bouncing back between sessions"
        )
        Spacer(modifier = Modifier.height(10.dp))
        WatchItem(
            title = "Efficiency",
            description = "How your pace-to-HR ratio improves over time"
        )

        Spacer(modifier = Modifier.height(24.dp))

        GradientDivider()

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Adapting fast? It pushes. Need recovery? It pulls back.",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WatchItem(title: String, description: String) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Gradient ring indicator
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(
                    brush = CardeaGradient,
                    style = Stroke(width = 2.5f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CardeaTheme.colors.textPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textSecondary
                )
            }
        }
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
        CardeaLogo(size = 56.dp, animate = false)
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Two minutes to set up.\nThen we build your plan.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your goal, your schedule, your current fitness \u2014 that\u2019s all we need.",
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
                .height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            TextButton(
                onClick = onStartSetup,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "Start Setup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CardeaTheme.colors.onGradient
                )
            }
        }
    }
}

// --- Shared visual elements ---

@Composable
private fun GradientDivider(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth(0.4f)
            .height(2.dp)
    ) {
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    GradientPink.copy(alpha = 0.5f),
                    GradientBlue.copy(alpha = 0.5f),
                    Color.Transparent
                )
            ),
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height
        )
    }
}

@Composable
private fun PulseLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val midY = h * 0.5f

        val path = Path().apply {
            // Flat lead-in
            moveTo(0f, midY)
            lineTo(w * 0.20f, midY)

            // P-wave (small bump)
            quadraticTo(w * 0.24f, midY - h * 0.12f, w * 0.28f, midY)

            // Flat segment
            lineTo(w * 0.32f, midY)

            // QRS complex (sharp spike)
            lineTo(w * 0.35f, midY + h * 0.15f)   // Q dip
            lineTo(w * 0.40f, midY - h * 0.45f)    // R peak
            lineTo(w * 0.45f, midY + h * 0.20f)    // S dip
            lineTo(w * 0.48f, midY)                  // return

            // T-wave (broader bump)
            lineTo(w * 0.54f, midY)
            quadraticTo(w * 0.60f, midY - h * 0.18f, w * 0.66f, midY)

            // Flat trail-out
            lineTo(w, midY)
        }

        val gradient = Brush.linearGradient(
            colors = listOf(
                GradientCyan.copy(alpha = 0.15f),
                GradientCyan.copy(alpha = 0.6f),
                GradientBlue,
                GradientPink,
                GradientRed.copy(alpha = 0.6f),
                GradientRed.copy(alpha = 0.15f)
            ),
            start = Offset.Zero,
            end = Offset(w, 0f)
        )

        // Glow pass — wider, lower opacity
        drawPath(
            path,
            brush = gradient,
            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            alpha = 0.3f
        )

        // Sharp pass
        drawPath(
            path,
            brush = gradient,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
