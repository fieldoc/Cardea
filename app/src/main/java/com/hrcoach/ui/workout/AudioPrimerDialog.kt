package com.hrcoach.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GlassBorder
import com.hrcoach.ui.theme.GlassHighlight

/**
 * One-time primer shown before the user's first workout. Three skippable slides
 * explaining chime / voice / vibration layering and the predictive-warning concept.
 *
 * Design notes:
 * - Uses an OPAQUE [CardeaBgPrimary] container (NOT [GlassCard]) so Training screen
 *   content — including the Start Run CTA gradient — does not bleed through the modal.
 *   Earlier implementation wrapped in `GlassCard`, whose semi-transparent
 *   `DarkGlassFillBrush` let the pink CTA stripe show through behind the Voice row.
 * - Bullets use the same icon language as `AudioSettingsSection` (VolumeUp / Mic /
 *   Notifications) so returning users recognize the same vocabulary on the first run
 *   AND inside the mid-run settings sheet.
 * - Slide indicator is a three-dot progress row (Cardea CTA gradient dot for active)
 *   rather than "1 of 3" raw text, matching the visual density of the rest of the
 *   design system.
 *
 * Gated by [com.hrcoach.domain.model.AudioSettings.audioPrimerShown] — once
 * dismissed or finished, the caller must set the flag true.
 *
 * [onFinish] fires when the user completes or skips the primer. Callers typically
 * persist `audioPrimerShown = true` here and (optionally) proceed to start the run.
 * [onSeeLibrary] fires when the user taps "See all sounds ->" on slide 1 — callers
 * should mark the primer shown (so it never re-opens) and navigate to the library;
 * they should NOT auto-start the workout.
 */
@Composable
fun AudioPrimerDialog(
    onFinish: () -> Unit,
    onSeeLibrary: () -> Unit,
) {
    var slideIndex by remember { mutableStateOf(0) }
    Dialog(
        onDismissRequest = onFinish,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardeaBgPrimary)
                .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            // Header: progress dots + skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProgressDots(current = slideIndex, total = 3)
                TextButton(
                    onClick = onFinish,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Skip",
                        style = MaterialTheme.typography.labelMedium,
                        color = CardeaTheme.colors.textSecondary
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            when (slideIndex) {
                0 -> Slide1(onSeeLibrary)
                1 -> Slide2()
                else -> Slide3()
            }

            Spacer(Modifier.height(22.dp))

            CardeaButton(
                text = if (slideIndex < 2) "Next" else "Got it \u2014 start my run",
                onClick = {
                    if (slideIndex < 2) slideIndex += 1 else onFinish()
                },
                modifier = Modifier.fillMaxWidth(),
                innerPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun ProgressDots(current: Int, total: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(total) { i ->
            val active = i == current
            Box(
                modifier = Modifier
                    .size(width = if (active) 20.dp else 6.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (active) CardeaTheme.colors.accentPink
                        else CardeaTheme.colors.glassBorder
                    )
            )
        }
    }
}

@Composable
private fun Slide1(onSeeLibrary: () -> Unit) {
    Column {
        Text(
            text = "How Cardea coaches you",
            style = MaterialTheme.typography.headlineSmall,
            color = CardeaTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "You'll hear three kinds of sound during your run:",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary
        )
        Spacer(Modifier.height(16.dp))
        IconBullet(
            icon = Icons.Default.VolumeUp,
            title = "Chime",
            body = "A quick status ping."
        )
        Spacer(Modifier.height(10.dp))
        IconBullet(
            icon = Icons.Default.Mic,
            title = "Voice",
            body = "What to do about it."
        )
        Spacer(Modifier.height(10.dp))
        IconBullet(
            icon = Icons.Default.Notifications,
            title = "Vibration",
            body = "Something urgent."
        )
        Spacer(Modifier.height(14.dp))
        TextButton(
            onClick = onSeeLibrary,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp)
        ) {
            Text(
                text = "See all sounds \u2192",
                style = MaterialTheme.typography.titleSmall,
                color = CardeaTheme.colors.textPrimary
            )
        }
    }
}

@Composable
private fun Slide2() {
    Column {
        Text(
            text = "When you drift",
            style = MaterialTheme.typography.headlineSmall,
            color = CardeaTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "If you drift out of your target zone, Cardea gives you a chime and a voice cue.\n\n" +
                "Still out 30 seconds later? You'll hear it again, this time with a vibration \u2014 a " +
                "stronger nudge so you don't miss it.\n\n" +
                "On Full voice, the cue also tells you how far off you are \u2014 for example, " +
                "\u201CSpeed up. 9 under.\u201D",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary
        )
    }
}

@Composable
private fun Slide3() {
    Column {
        Text(
            text = "A heads-up, before you drift",
            style = MaterialTheme.typography.headlineSmall,
            color = CardeaTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Sometimes Cardea coaches you while you're still in zone. If your heart rate is " +
                "trending toward the edge, you'll hear a heads-up like \"Pace climbing \u2014 ease off.\"\n\n" +
                "Catching drift early means smaller adjustments \u2014 and you stay in zone longer.",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary
        )
    }
}

/**
 * Icon-based bullet for the audio primer. Uses the same icon-box pattern as
 * `AudioSettingsSection.SettingIconBox` (30dp rounded-square glass chip) so users
 * see the same visual vocabulary in the primer as in the settings screens.
 */
@Composable
private fun IconBullet(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GlassHighlight)
                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CardeaTheme.colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.padding(top = 2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = CardeaTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
        }
    }
}
