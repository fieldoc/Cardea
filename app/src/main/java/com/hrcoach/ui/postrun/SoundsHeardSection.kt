package com.hrcoach.ui.postrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.service.audio.CueCopy
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaTheme

/**
 * Post-run "Sounds you heard today" recap. Shown only on the user's first 3 non-sim
 * workouts (gate enforced by [PostRunSummaryViewModel.load]). Lists every cue that
 * fired during the run with its count and plain-English meaning, then links out to
 * the Sound Library for full reference.
 */
@Composable
fun SoundsHeardSection(
    counts: Map<CoachingEvent, Int>,
    onSeeLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (counts.isEmpty()) return

    GlassCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Column {
            Text(
                text = "SOUNDS YOU HEARD TODAY",
                style = MaterialTheme.typography.labelMedium,
                color = CardeaTheme.colors.textSecondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "We fired a few coaching cues during your run. Here's what each meant:",
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textTertiary
            )
            Spacer(Modifier.height(12.dp))

            CueCopy.displayOrder.forEach { event ->
                val count = counts[event] ?: return@forEach
                if (count <= 0) return@forEach
                val entry = CueCopy.forEvent(event)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$count \u00d7 ${entry.title.lowercase()}",
                        style = MaterialTheme.typography.titleSmall,
                        color = CardeaTheme.colors.textPrimary,
                        modifier = Modifier.weight(0.42f)
                    )
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textSecondary,
                        modifier = Modifier.weight(0.58f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onSeeLibrary) {
                    Text(
                        text = "See all sounds \u2192",
                        color = CardeaTheme.colors.textPrimary
                    )
                }
            }
        }
    }
}
