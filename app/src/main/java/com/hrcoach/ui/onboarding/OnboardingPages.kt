package com.hrcoach.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
import com.hrcoach.ui.theme.CardeaCtaGradient
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
import com.hrcoach.util.PermissionGate
import kotlinx.coroutines.delay

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

// ── Screen 4: BLE ───────────────────────────────────────────────────

@Composable
fun BlePage(permissionGranted: Boolean, onPermissionResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    val blePerms = remember { PermissionGate.blePermissions() }
    val alreadyGranted = remember(permissionGranted) {
        blePerms.isEmpty() || PermissionGate.hasPermissions(context, blePerms)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onPermissionResult(results.values.all { it })
    }

    LaunchedEffect(Unit) {
        if (alreadyGranted) onPermissionResult(true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Your HR Monitor",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Cardea connects to Bluetooth heart rate monitors for real-time coaching",
            fontSize = 14.sp,
            color = CardeaTextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Animated BLE waves
        val infiniteTransition = rememberInfiniteTransition(label = "ble")
        val waveScale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bleWave",
        )
        val waveAlpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bleAlpha",
        )

        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                for (i in 1..3) {
                    drawCircle(
                        color = GradientCyan.copy(alpha = waveAlpha / i),
                        radius = 24.dp.toPx() * waveScale * i,
                        center = center,
                        style = Stroke(width = 1.5f.dp.toPx()),
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = "Bluetooth",
                tint = GradientCyan,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Compatibility cards
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = ZoneGreen,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Works with Bluetooth (BLE)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CardeaTextPrimary,
                    )
                    Text(
                        text = "Coospo, Polar, Garmin, Wahoo and most chest straps",
                        fontSize = 12.sp,
                        color = CardeaTextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = ZoneRed,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ANT+ only \u2014 not supported",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CardeaTextPrimary,
                    )
                    Text(
                        text = "Some older devices use ANT+ exclusively. Check your device specs.",
                        fontSize = 12.sp,
                        color = CardeaTextSecondary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "You'll connect your monitor in the Workout tab when you're ready to run",
            fontSize = 12.sp,
            color = CardeaTextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 260.dp),
        )

        // Permission request button (only if needed)
        if (blePerms.isNotEmpty() && !alreadyGranted && !permissionGranted) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassHighlight)
                    .clickable { launcher.launch(blePerms.toTypedArray()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Allow Bluetooth Access",
                    fontSize = 14.sp,
                    color = CardeaTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (permissionGranted || alreadyGranted) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, null, tint = ZoneGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Bluetooth ready", fontSize = 12.sp, color = ZoneGreen)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Screen 5: GPS ───────────────────────────────────────────────────

@Composable
fun GpsPage(permissionGranted: Boolean, onPermissionResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    val gpsPerms = remember { PermissionGate.locationPermissions() }
    val alreadyGranted = remember(permissionGranted) {
        PermissionGate.hasPermissions(context, gpsPerms)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onPermissionResult(results.values.all { it })
    }

    LaunchedEffect(Unit) {
        if (alreadyGranted) onPermissionResult(true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Track Your Runs",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "GPS maps your route and measures distance and pace in real time",
            fontSize = 14.sp,
            color = CardeaTextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Route illustration
        val infiniteTransition = rememberInfiniteTransition(label = "gps")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "gpsPulse",
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val w = size.width
            val h = size.height
            val routePath = Path().apply {
                moveTo(w * 0.1f, h * 0.8f)
                cubicTo(w * 0.25f, h * 0.2f, w * 0.4f, h * 0.6f, w * 0.55f, h * 0.3f)
                cubicTo(w * 0.7f, h * 0.1f, w * 0.8f, h * 0.5f, w * 0.9f, h * 0.4f)
            }
            drawPath(
                path = routePath,
                brush = Brush.linearGradient(
                    colors = listOf(GradientRed, GradientCyan),
                    start = Offset(w * 0.1f, 0f),
                    end = Offset(w * 0.9f, 0f),
                ),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            // Pulsing end marker
            drawCircle(
                color = GradientCyan.copy(alpha = pulseAlpha),
                radius = 6.dp.toPx(),
                center = Offset(w * 0.9f, h * 0.4f),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Metric strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricItem("5.2", "km")
            MetricItem("5:42", "/km")
            MetricItem("29:38", "time")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Permission request
        if (!alreadyGranted && !permissionGranted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassHighlight)
                    .clickable { launcher.launch(gpsPerms.toTypedArray()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Allow Location Access",
                    fontSize = 14.sp,
                    color = CardeaTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (permissionGranted || alreadyGranted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, null, tint = ZoneGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Location ready", fontSize = 12.sp, color = ZoneGreen)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MetricItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = CardeaTextPrimary,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = CardeaTextTertiary,
        )
    }
}

// ── Screen 6: Alerts ────────────────────────────────────────────────

@Composable
fun AlertsPage(permissionGranted: Boolean, onPermissionResult: (Boolean) -> Unit) {
    val context = LocalContext.current
    val notifPerms = remember { PermissionGate.notificationPermissions() }
    val alreadyGranted = remember(permissionGranted) {
        notifPerms.isEmpty() || PermissionGate.hasPermissions(context, notifPerms)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        onPermissionResult(results.values.all { it })
    }

    LaunchedEffect(Unit) {
        if (alreadyGranted) onPermissionResult(true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Stay in the Zone",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTextPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Audio tones guide you without looking at your phone",
            fontSize = 14.sp,
            color = CardeaTextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Alert cards
        AlertInfoCard(
            title = "In Zone",
            description = "A quick confirmation tone when you're right where you should be",
            tintColor = ZoneGreen,
        )
        Spacer(modifier = Modifier.height(8.dp))
        AlertInfoCard(
            title = "Ease Up",
            description = "A descending tone when your heart rate is climbing too high",
            tintColor = ZoneAmber,
        )
        Spacer(modifier = Modifier.height(8.dp))
        AlertInfoCard(
            title = "Pick It Up",
            description = "An ascending tone when you need to push a little harder",
            tintColor = ZoneRed,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Music callout
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.widthIn(max = 260.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = CardeaTextTertiary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Alerts layer over your music without pausing it",
                fontSize = 12.sp,
                color = CardeaTextTertiary,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Permission request
        if (notifPerms.isNotEmpty() && !alreadyGranted && !permissionGranted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassHighlight)
                    .clickable { launcher.launch(notifPerms.toTypedArray()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Allow Notifications",
                    fontSize = 14.sp,
                    color = CardeaTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (permissionGranted || alreadyGranted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, null, tint = ZoneGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Notifications ready", fontSize = 12.sp, color = ZoneGreen)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AlertInfoCard(title: String, description: String, tintColor: Color) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tintColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tintColor,
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = CardeaTextSecondary,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

// ── Screen 7: Tab Tour ──────────────────────────────────────────────

@Composable
fun TabTourPage() {
    val tabs = remember {
        listOf(
            TabInfo("Home", "Your dashboard \u2014 training status, streaks, next session", Icons.Filled.Home, false),
            TabInfo("Workout", "Start runs, connect HR monitor, bootcamp sessions", Icons.Filled.FavoriteBorder, true),
            TabInfo("History", "Past runs with route maps and HR charts", Icons.AutoMirrored.Filled.List, false),
            TabInfo("Progress", "Trends, volume, and fitness analytics", Icons.AutoMirrored.Filled.ShowChart, false),
            TabInfo("Account", "Settings, audio, theme, and profile", Icons.Filled.Person, false),
        )
    }

    var currentTabIndex by remember { mutableIntStateOf(0) }
    val tabPositions = remember { mutableStateMapOf<Int, Offset>() }

    LaunchedEffect(currentTabIndex) {
        val delayMs = if (tabs[currentTabIndex].isHighlighted) 3500L else 2500L
        delay(delayMs)
        currentTabIndex = (currentTabIndex + 1) % tabs.size
    }

    val currentTab = tabs[currentTabIndex]

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${currentTabIndex + 1} of ${tabs.size}",
                color = CardeaTextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 24.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = currentTab.icon,
                contentDescription = currentTab.name,
                tint = if (currentTab.isHighlighted) GradientRed else CardeaTextPrimary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = currentTab.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (currentTab.isHighlighted) GradientRed else CardeaTextPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = currentTab.description,
                fontSize = 14.sp,
                color = CardeaTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 260.dp),
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Mock bottom nav bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(CardeaBgSecondary),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { currentTabIndex = index }
                            .onGloballyPositioned { coords ->
                                val pos = coords.localToRoot(Offset.Zero)
                                val center = Offset(
                                    pos.x + coords.size.width / 2f,
                                    pos.y + coords.size.height / 2f,
                                )
                                tabPositions[index] = center
                            }
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.name,
                            tint = if (index == currentTabIndex) CardeaTextPrimary else CardeaTextTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = tab.name,
                            fontSize = 10.sp,
                            color = if (index == currentTabIndex) CardeaTextPrimary else CardeaTextTertiary,
                        )
                    }
                }
            }
        }

        // Spotlight overlay
        val targetOffset = tabPositions[currentTabIndex] ?: Offset.Zero
        if (targetOffset != Offset.Zero) {
            SpotlightOverlay(
                targetOffset = targetOffset,
                ringColor = if (currentTab.isHighlighted) GradientRed else GradientCyan,
                tooltipName = currentTab.name,
                tooltipDescription = currentTab.description,
                useGradientName = currentTab.isHighlighted,
            )
        }
    }
}

private data class TabInfo(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val isHighlighted: Boolean,
)

// ── Screen 8: Launch Pad ────────────────────────────────────────────

@Composable
fun LaunchPadPage(onStartBootcamp: () -> Unit, onExploreFirst: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "launchpad")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "checkRing",
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "checkRingAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Gradient checkmark circle with pulsing ring
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(
                    color = GradientCyan.copy(alpha = ringAlpha),
                    radius = 36.dp.toPx() * ringScale,
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CardeaCtaGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Complete",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "You're All Set!",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CardeaTextPrimary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Cardea's 8-week Bootcamp builds your aerobic base with progressive HR zone training. Most runners see measurable improvement by week 3.",
            fontSize = 14.sp,
            color = CardeaTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.widthIn(max = 280.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Primary CTA: Start Bootcamp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CardeaCtaGradient)
                .clickable { onStartBootcamp() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Start Bootcamp Setup",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = CardeaTextPrimary,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Secondary: Explore first
        Text(
            text = "Explore the app first",
            fontSize = 14.sp,
            color = CardeaTextTertiary,
            modifier = Modifier.clickable { onExploreFirst() },
        )
    }
}
