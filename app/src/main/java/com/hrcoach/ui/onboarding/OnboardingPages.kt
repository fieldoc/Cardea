package com.hrcoach.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrcoach.ui.components.CardeaLogo
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaBgSecondary
import com.hrcoach.ui.theme.CardeaGradient
import com.hrcoach.ui.theme.CardeaTextPrimary
import com.hrcoach.ui.theme.CardeaTextSecondary
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GlassHighlight
import com.hrcoach.ui.theme.GradientCyan
import com.hrcoach.ui.theme.GradientRed
import com.hrcoach.ui.theme.ZoneAmber
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed

private val ZoneOrange = Color(0xFFFF8C00)

// ── Screen 1: Welcome ───────────────────────────────────────────────

@Composable
fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                GradientRed.copy(alpha = 0.15f),
                                Color.Transparent,
                            ),
                            radius = 200f,
                        )
                    )
            )
            CardeaLogo(size = 120.dp, animate = true)
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Meet Cardea",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTextPrimary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your AI running coach that listens to your heart. Real-time zone coaching, adaptive training plans, and audio alerts that keep you in the zone.",
            fontSize = 14.sp,
            color = CardeaTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.widthIn(max = 280.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "TRAIN SMARTER. RUN STRONGER.",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = CardeaTextTertiary,
            letterSpacing = 2.sp,
        )
    }
}

// ── Screen 2: Profile ───────────────────────────────────────────────

@Composable
fun ProfilePage(
    uiState: OnboardingUiState,
    onAgeChanged: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onToggleWeightUnit: () -> Unit,
    onHrMaxOverrideChanged: (String) -> Unit,
    onToggleHrMaxOverride: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Tell Us About You",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "We'll use this to personalize your training zones",
            fontSize = 14.sp,
            color = CardeaTextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Age input
        OutlinedTextField(
            value = uiState.age,
            onValueChange = onAgeChanged,
            label = { Text("Age") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GradientCyan,
                unfocusedBorderColor = GlassBorder,
                focusedLabelColor = GradientCyan,
                unfocusedLabelColor = CardeaTextTertiary,
                cursorColor = GradientCyan,
                focusedTextColor = CardeaTextPrimary,
                unfocusedTextColor = CardeaTextPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Weight input with unit toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = uiState.weight,
                onValueChange = onWeightChanged,
                label = { Text("Weight (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GradientCyan,
                    unfocusedBorderColor = GlassBorder,
                    focusedLabelColor = GradientCyan,
                    unfocusedLabelColor = CardeaTextTertiary,
                    cursorColor = GradientCyan,
                    focusedTextColor = CardeaTextPrimary,
                    unfocusedTextColor = CardeaTextPrimary,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlassHighlight)
                    .clickable { onToggleWeightUnit() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = if (uiState.weightUnit == WeightUnit.LBS) "lbs" else "kg",
                    color = CardeaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // HRmax card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(CardeaGradient)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Estimated Max Heart Rate",
                fontSize = 12.sp,
                color = CardeaTextTertiary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            val hrMaxText = uiState.estimatedHrMax?.toString() ?: "\u2014"
            Text(
                text = "$hrMaxText bpm",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(
                    brush = if (uiState.estimatedHrMax != null) CardeaGradient else null,
                ),
                color = if (uiState.estimatedHrMax == null) CardeaTextTertiary else Color.Unspecified,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Calculated as 220 \u2212 your age",
                fontSize = 12.sp,
                color = CardeaTextTertiary,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "I know my actual HRmax",
                fontSize = 13.sp,
                color = CardeaTextSecondary,
                modifier = Modifier.clickable { onToggleHrMaxOverride() },
            )

            if (uiState.isHrMaxOverrideExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.hrMaxOverride,
                    onValueChange = onHrMaxOverrideChanged,
                    label = { Text("Your HRmax (120-220)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GradientRed,
                        unfocusedBorderColor = GlassBorder,
                        focusedLabelColor = GradientRed,
                        unfocusedLabelColor = CardeaTextTertiary,
                        cursorColor = GradientRed,
                        focusedTextColor = CardeaTextPrimary,
                        unfocusedTextColor = CardeaTextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Screen 3: Zones ─────────────────────────────────────────────────

private data class ZoneInfo(
    val label: String,
    val name: String,
    val lowPct: Float,
    val highPct: Float,
    val color: Color,
)

@Composable
fun ZonesPage(effectiveHrMax: Int?) {
    val zones = remember {
        listOf(
            ZoneInfo("Z1", "Easy / Recovery", 0.50f, 0.60f, ZoneGreen),
            ZoneInfo("Z2", "Aerobic Base", 0.60f, 0.70f, ZoneGreen),
            ZoneInfo("Z3", "Tempo", 0.70f, 0.80f, ZoneAmber),
            ZoneInfo("Z4", "Threshold", 0.80f, 0.90f, ZoneOrange),
            ZoneInfo("Z5", "Max Effort", 0.90f, 1.00f, ZoneRed),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Heart Rate Zones",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Cardea coaches you in real-time to stay in the right zone",
            fontSize = 14.sp,
            color = CardeaTextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Continuous zone color bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        ) {
            zones.forEach { zone ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(zone.color)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Zone rows
        zones.forEach { zone ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(zone.color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = zone.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = zone.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CardeaTextPrimary,
                    )
                    Text(
                        text = "${(zone.lowPct * 100).toInt()}-${(zone.highPct * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = CardeaTextTertiary,
                    )
                }

                if (effectiveHrMax != null) {
                    val low = (effectiveHrMax * zone.lowPct).toInt()
                    val high = (effectiveHrMax * zone.highPct).toInt()
                    Text(
                        text = "$low-$high",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = zone.color,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = " bpm",
                        fontSize = 11.sp,
                        color = CardeaTextTertiary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (effectiveHrMax != null) {
            Text(
                text = "BPM ranges based on your max of $effectiveHrMax bpm",
                fontSize = 11.sp,
                color = CardeaTextTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Screens 4-8: Stubs (replaced in Tasks 7-9) ─────────────────────

@Composable
fun BlePage(permissionGranted: Boolean, onPermissionResult: (Boolean) -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("BLE") }
}

@Composable
fun GpsPage(permissionGranted: Boolean, onPermissionResult: (Boolean) -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("GPS") }
}

@Composable
fun AlertsPage(permissionGranted: Boolean, onPermissionResult: (Boolean) -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Alerts") }
}

@Composable
fun TabTourPage() {
    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Tab Tour") }
}

@Composable
fun LaunchPadPage(onStartBootcamp: () -> Unit, onExploreFirst: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Launch Pad") }
}
