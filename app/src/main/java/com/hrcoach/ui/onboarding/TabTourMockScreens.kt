package com.hrcoach.ui.onboarding

/**
 * Simplified mock screens shown during the tab tour onboarding page.
 *
 * These mirror the real app screens' structure and visual language.
 * When a real screen's layout changes significantly, update the
 * corresponding mock here to keep the onboarding accurate.
 *
 * Design tokens (colors, GlassCard, gradients) are shared with the
 * production screens, so theming changes propagate automatically.
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.ZoneAmber
import com.hrcoach.ui.theme.ZoneGreen

// ── Home Tab ───────────────────────────────────────────────────────

@Composable
internal fun MockHomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Good morning", fontSize = 12.sp, color = CardeaTheme.colors.textTertiary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "Dashboard",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = CardeaTheme.colors.textPrimary,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Bootcamp hero card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(GradientRed.copy(alpha = 0.2f), GradientCyan.copy(alpha = 0.1f))
                    )
                )
                .padding(12.dp),
        ) {
            Column {
                Text("Next Session", fontSize = 10.sp, color = CardeaTheme.colors.textTertiary, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Week 1 \u2022 Day 2", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CardeaTheme.colors.textPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text("30 min \u2022 Zone 2 easy run", fontSize = 12.sp, color = CardeaTheme.colors.textSecondary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Stat chips row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MockStatChip("3", "streak", Modifier.weight(1f))
            MockStatChip("12", "runs", Modifier.weight(1f))
            MockStatChip("42k", "total", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MockStatChip(value: String, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CardeaTheme.colors.textPrimary)
            Text(label, fontSize = 10.sp, color = CardeaTheme.colors.textTertiary)
        }
    }
}

// ── Workout Tab ────────────────────────────────────────────────────

@Composable
internal fun MockWorkoutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Start a Run",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = CardeaTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // HR monitor status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardeaTheme.colors.glassHighlight)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Bluetooth, null, tint = GradientCyan, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("HR Monitor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CardeaTheme.colors.textPrimary)
                Text("Tap to connect", fontSize = 11.sp, color = CardeaTheme.colors.textTertiary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Zone selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CardeaTheme.colors.glassHighlight)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ZoneGreen),
                contentAlignment = Alignment.Center,
            ) {
                Text("Z2", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("Target Zone", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CardeaTheme.colors.textPrimary)
                Text("Aerobic Base \u2022 114\u2013133 bpm", fontSize = 11.sp, color = CardeaTheme.colors.textTertiary)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Start button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardeaGradient),
            contentAlignment = Alignment.Center,
        ) {
            Text("Start Workout", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ── History Tab ────────────────────────────────────────────────────

@Composable
internal fun MockHistoryScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            "Run History",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = CardeaTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))

        MockRunEntry("Today", "5.2 km", "29:38", ZoneGreen)
        Spacer(modifier = Modifier.height(8.dp))
        MockRunEntry("Yesterday", "3.8 km", "22:15", ZoneAmber)
        Spacer(modifier = Modifier.height(8.dp))
        MockRunEntry("Mon", "6.1 km", "35:42", ZoneGreen)
    }
}

@Composable
private fun MockRunEntry(day: String, distance: String, time: String, zoneColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(zoneColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(day, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CardeaTheme.colors.textPrimary)
            Text(distance, fontSize = 11.sp, color = CardeaTheme.colors.textTertiary)
        }
        Text(time, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = CardeaTheme.colors.textSecondary, fontFamily = FontFamily.Monospace)
    }
}

// ── Progress Tab ───────────────────────────────────────────────────

@Composable
internal fun MockProgressScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            "Progress",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = CardeaTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Weekly volume bar chart
        Text("WEEKLY VOLUME", fontSize = 10.sp, color = CardeaTheme.colors.textTertiary, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            val bars = listOf(0.4f, 0.6f, 0.3f, 0.8f, 0.7f, 0.5f, 0.9f)
            val labels = listOf("M", "T", "W", "T", "F", "S", "S")
            bars.zip(labels).forEach { (height, label) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .fillMaxHeight(height)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (height >= 0.8f) Brush.linearGradient(listOf(GradientRed, GradientCyan))
                                else Brush.linearGradient(listOf(CardeaTheme.colors.glassHighlight, CardeaTheme.colors.glassHighlight))
                            )
                    )
                    Text(label, fontSize = 9.sp, color = CardeaTheme.colors.textTertiary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fitness trend line
        Text("FITNESS TREND", fontSize = 10.sp, color = CardeaTheme.colors.textTertiary, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            val w = size.width
            val h = size.height
            val trendPath = Path().apply {
                moveTo(0f, h * 0.8f)
                cubicTo(w * 0.2f, h * 0.7f, w * 0.4f, h * 0.5f, w * 0.6f, h * 0.4f)
                cubicTo(w * 0.8f, h * 0.3f, w * 0.9f, h * 0.25f, w, h * 0.2f)
            }
            drawPath(
                path = trendPath,
                brush = Brush.linearGradient(listOf(GradientRed, GradientCyan)),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

// ── Account Tab ────────────────────────────────────────────────────

@Composable
internal fun MockAccountScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            "Account",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = CardeaTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))

        MockSettingsItem(Icons.Filled.Person, "Profile")
        Spacer(modifier = Modifier.height(6.dp))
        MockSettingsItem(Icons.Filled.Notifications, "Audio Alerts")
        Spacer(modifier = Modifier.height(6.dp))
        MockSettingsItem(Icons.Filled.MyLocation, "Maps & GPS")
    }
}

@Composable
private fun MockSettingsItem(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardeaTheme.colors.glassHighlight)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = CardeaTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = CardeaTheme.colors.textPrimary)
    }
}
