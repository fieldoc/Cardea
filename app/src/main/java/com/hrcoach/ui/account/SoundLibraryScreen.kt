package com.hrcoach.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.hrcoach.domain.model.CoachingEvent
import com.hrcoach.service.audio.CueBannerKind
import com.hrcoach.service.audio.CueCopy
import com.hrcoach.service.audio.EarconPlayer
import com.hrcoach.service.audio.StridesEarcon
import com.hrcoach.ui.components.GlassCard
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.workout.cueBannerBorderColor

@Composable
fun SoundLibraryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember { EarconPlayer(context) }
    DisposableEffect(player) { onDispose { player.destroy() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CardeaTheme.colors.bgPrimary)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = CardeaTheme.colors.textPrimary
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Sound Library",
                style = MaterialTheme.typography.headlineSmall,
                color = CardeaTheme.colors.textPrimary
            )
        }

        Text(
            text = "Cardea coaches you with chimes, voice, and vibration. Tap any cue to preview.",
            style = MaterialTheme.typography.bodyMedium,
            color = CardeaTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CueCopy.sections.forEach { section ->
                item(key = "h-${section.heading}") {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = section.heading.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CardeaTheme.colors.textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = section.caption,
                            style = MaterialTheme.typography.bodySmall,
                            color = CardeaTheme.colors.textTertiary
                        )
                    }
                }
                items(section.events, key = { it.name }) { event ->
                    SoundRow(event = event, onPreview = { player.play(event) })
                }
            }
            // Strides timer chimes are a separate audio category (not routed
            // through CoachingEventRouter) so they live outside CueCopy.sections.
            // Render them as their own section here so users can audition the
            // three chimes before their first strides session.
            item(key = "h-strides") {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "STRIDES TIMER",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = CardeaTheme.colors.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Chimes that drive the 20s-on / 60s-off rep timer during a strides session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CardeaTheme.colors.textTertiary
                    )
                }
            }
            items(stridesEntries, key = { "stride-${it.kind.name}" }) { entry ->
                StridesSoundRow(entry = entry, onPreview = { player.playStridesEvent(entry.kind) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private data class StridesEntry(
    val kind: StridesEarcon,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

private val stridesEntries: List<StridesEntry> = listOf(
    StridesEntry(
        kind = StridesEarcon.GO,
        title = "Strides go",
        subtitle = "Start of each 20-second pickup. Smooth, not sprint.",
        icon = Icons.Default.PlayCircleOutline
    ),
    StridesEntry(
        kind = StridesEarcon.EASE,
        title = "Strides ease",
        subtitle = "End of the pickup — ease into a 60-second easy jog.",
        icon = Icons.Default.PauseCircleOutline
    ),
    StridesEntry(
        kind = StridesEarcon.SET_COMPLETE,
        title = "Strides complete",
        subtitle = "Final rep done. Finish your run easy.",
        icon = Icons.Default.SportsScore
    )
)

@Composable
private fun StridesSoundRow(entry: StridesEntry, onPreview: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        borderColor = cueBannerBorderColor(CueBannerKind.GUIDANCE, alpha = 0.4f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = CardeaTheme.colors.textPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = CardeaTheme.colors.textPrimary
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textSecondary
                )
            }
            IconButton(onClick = onPreview) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Preview ${entry.title}",
                    tint = CardeaTheme.colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun SoundRow(event: CoachingEvent, onPreview: () -> Unit) {
    val entry = CueCopy.forEvent(event)
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        borderColor = cueBannerBorderColor(entry.kind, alpha = 0.4f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = CardeaTheme.colors.textPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = CardeaTheme.colors.textPrimary
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CardeaTheme.colors.textSecondary
                )
            }
            IconButton(onClick = onPreview) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Preview ${entry.title}",
                    tint = CardeaTheme.colors.textPrimary
                )
            }
        }
    }
}

