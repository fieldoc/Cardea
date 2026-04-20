package com.hrcoach.ui.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hrcoach.service.audio.CueBanner
import com.hrcoach.service.audio.CueBannerKind
import com.hrcoach.service.audio.CueCopy
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed

/**
 * Transient pill overlay shown above the active workout screen whenever a coaching
 * event fires. Displays [CueCopy] title + subtitle for ~3.5 s (auto-cleared by
 * [com.hrcoach.service.WorkoutState.flashCueBanner]).
 *
 * Rendered regardless of voice verbosity — this is a transparency feature so users
 * who silenced the voice still know what fired.
 */
@Composable
fun CueBannerOverlay(banner: CueBanner?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = banner != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        banner?.let { BannerPill(it) }
    }
}

@Composable
private fun BannerPill(banner: CueBanner) {
    val borderColor = when (banner.kind) {
        CueBannerKind.ALERT -> ZoneRed.copy(alpha = 0.5f)
        CueBannerKind.GUIDANCE -> Color(0x80FF4D5A)
        CueBannerKind.MILESTONE -> ZoneGreen.copy(alpha = 0.5f)
        CueBannerKind.INFO -> Color(0x33FFFFFF)
    }
    // Soft dark glass fill — strengthen a bit for readability over gradient bg
    val fill = Brush.verticalGradient(
        colors = listOf(Color(0x33000000), Color(0x22000000))
    )
    val icon = CueCopy.forEvent(banner.event).icon

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(brush = fill)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .padding(PaddingValues(horizontal = 12.dp, vertical = 10.dp))
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CardeaTheme.colors.textPrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = banner.title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = CardeaTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = banner.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = CardeaTheme.colors.textSecondary
            )
        }
    }
}
