package com.hrcoach.service.audio

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.hrcoach.domain.model.CoachingEvent

/**
 * Single source of truth for how each [CoachingEvent] is presented to the user.
 * Read by:
 *   - [CueBannerOverlay] during the active workout
 *   - SoundLibraryScreen in settings
 *   - SoundsHeardSection on the post-run summary
 *
 * When adding a new [CoachingEvent] enum value, you MUST add it here AND to
 * [displayOrder] AND to a [Section]. The CueCopyTest will fail otherwise.
 */
object CueCopy {

    data class Entry(
        val title: String,
        val subtitle: String,
        val kind: CueBannerKind,
        val icon: ImageVector
    )

    data class Section(
        val heading: String,
        val caption: String,
        val events: List<CoachingEvent>
    )

    private val entries: Map<CoachingEvent, Entry> = mapOf(
        CoachingEvent.SPEED_UP to Entry(
            title = "Speed up",
            subtitle = "You're slower than your target — pick up the pace.",
            kind = CueBannerKind.ALERT,
            icon = Icons.Default.ArrowUpward
        ),
        CoachingEvent.SLOW_DOWN to Entry(
            title = "Slow down",
            subtitle = "You're working harder than your target — ease off.",
            kind = CueBannerKind.ALERT,
            icon = Icons.Default.ArrowDownward
        ),
        CoachingEvent.SIGNAL_LOST to Entry(
            title = "Signal lost",
            subtitle = "Heart-rate sensor disconnected. Check your strap.",
            kind = CueBannerKind.ALERT,
            icon = Icons.Default.Favorite
        ),
        CoachingEvent.SIGNAL_REGAINED to Entry(
            title = "Signal back",
            subtitle = "Heart-rate sensor reconnected.",
            kind = CueBannerKind.GUIDANCE,
            icon = Icons.Default.Favorite
        ),
        CoachingEvent.RETURN_TO_ZONE to Entry(
            title = "Back in zone",
            subtitle = "You just came back into your target.",
            kind = CueBannerKind.GUIDANCE,
            icon = Icons.Default.Check
        ),
        CoachingEvent.PREDICTIVE_WARNING to Entry(
            title = "Heads up",
            subtitle = "You're in zone now, but trending toward the edge. Adjust early.",
            kind = CueBannerKind.GUIDANCE,
            icon = Icons.Default.Notifications
        ),
        CoachingEvent.SEGMENT_CHANGE to Entry(
            title = "Next interval",
            subtitle = "New interval starting now.",
            kind = CueBannerKind.GUIDANCE,
            icon = Icons.Default.ChevronRight
        ),
        CoachingEvent.KM_SPLIT to Entry(
            title = "Split",
            subtitle = "You just passed a distance marker.",
            kind = CueBannerKind.MILESTONE,
            icon = Icons.Default.Timer
        ),
        CoachingEvent.HALFWAY to Entry(
            title = "Halfway",
            subtitle = "You've reached 50% of your target.",
            kind = CueBannerKind.MILESTONE,
            icon = Icons.Default.Timer
        ),
        CoachingEvent.WORKOUT_COMPLETE to Entry(
            title = "Workout complete",
            subtitle = "You've reached your target distance or time.",
            kind = CueBannerKind.MILESTONE,
            icon = Icons.Default.Check
        ),
        CoachingEvent.IN_ZONE_CONFIRM to Entry(
            title = "Holding zone",
            subtitle = "Cruising nicely — a periodic check-in while you're steady.",
            kind = CueBannerKind.INFO,
            icon = Icons.Default.FavoriteBorder
        ),
    )

    fun forEvent(event: CoachingEvent): Entry =
        entries[event] ?: error("No CueCopy entry for $event. Add it in CueCopy.entries.")

    val displayOrder: List<CoachingEvent> = listOf(
        CoachingEvent.SPEED_UP,
        CoachingEvent.SLOW_DOWN,
        CoachingEvent.SIGNAL_LOST,
        CoachingEvent.SIGNAL_REGAINED,
        CoachingEvent.RETURN_TO_ZONE,
        CoachingEvent.PREDICTIVE_WARNING,
        CoachingEvent.SEGMENT_CHANGE,
        CoachingEvent.KM_SPLIT,
        CoachingEvent.HALFWAY,
        CoachingEvent.WORKOUT_COMPLETE,
        CoachingEvent.IN_ZONE_CONFIRM
    )

    val sections: List<Section> = listOf(
        Section(
            heading = "Zone alerts",
            caption = "Fires when you're outside your heart-rate target for more than 30 seconds.",
            events = listOf(
                CoachingEvent.SPEED_UP,
                CoachingEvent.SLOW_DOWN,
                CoachingEvent.SIGNAL_LOST,
                CoachingEvent.SIGNAL_REGAINED
            )
        ),
        Section(
            heading = "Pace guidance",
            caption = "Coaching that runs even when you're in zone.",
            events = listOf(
                CoachingEvent.RETURN_TO_ZONE,
                CoachingEvent.PREDICTIVE_WARNING,
                CoachingEvent.SEGMENT_CHANGE
            )
        ),
        Section(
            heading = "Milestones",
            caption = "Distance and completion markers.",
            events = listOf(
                CoachingEvent.KM_SPLIT,
                CoachingEvent.HALFWAY,
                CoachingEvent.WORKOUT_COMPLETE
            )
        ),
        Section(
            heading = "Reassurance",
            caption = "A periodic check-in while you're cruising in zone.",
            events = listOf(
                CoachingEvent.IN_ZONE_CONFIRM
            )
        )
    )
}
