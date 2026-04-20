package com.hrcoach.ui.workout

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaTheme

/**
 * One-time primer shown before the user's first workout. Three skippable slides
 * explaining chime / voice / vibration layering and the predictive-warning concept.
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
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentPadding = PaddingValues(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${slideIndex + 1} of 3",
                    style = MaterialTheme.typography.labelMedium,
                    color = CardeaTheme.colors.textSecondary
                )
                TextButton(onClick = onFinish) {
                    Text(
                        text = "Skip",
                        color = CardeaTheme.colors.textSecondary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when (slideIndex) {
                0 -> Slide1(onSeeLibrary)
                1 -> Slide2()
                else -> Slide3()
            }
            Spacer(Modifier.height(20.dp))
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
private fun Slide1(onSeeLibrary: () -> Unit) {
    Column {
        Text(
            text = "How Cardea coaches you",
            style = MaterialTheme.typography.headlineSmall,
            color = CardeaTheme.colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "You'll hear three kinds of sound during your run:",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary
        )
        Spacer(Modifier.height(8.dp))
        Bullet("Chime", "A quick status ping.")
        Bullet("Voice", "What to do about it.")
        Bullet("Vibration", "Something urgent.")
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onSeeLibrary) {
            Text(
                text = "See all sounds \u2192",
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
                "If you're still out of zone 30 seconds later, you'll hear it again \u2014 with vibration. " +
                "That's escalation \u2014 it means you haven't adjusted yet.",
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
                "Adjust early and you'll stay in. That's a feature, not a bug.",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary
        )
    }
}

@Composable
private fun Bullet(title: String, body: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(CardeaTheme.colors.textSecondary, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = CardeaTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
        }
    }
}
