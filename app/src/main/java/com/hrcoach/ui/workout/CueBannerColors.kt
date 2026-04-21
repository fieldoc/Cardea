package com.hrcoach.ui.workout

import androidx.compose.ui.graphics.Color
import com.hrcoach.service.audio.CueBannerKind
import com.hrcoach.ui.theme.GradientPink
import com.hrcoach.ui.theme.ZoneGreen
import com.hrcoach.ui.theme.ZoneRed

/**
 * Single source of truth for cue-banner border tints. Used by the in-workout
 * [CueBannerOverlay] (alpha 0.5) and the [com.hrcoach.ui.account.SoundLibraryScreen]
 * rows (alpha 0.4). Pass [alpha] per call site; keep the base hues consistent.
 */
fun cueBannerBorderColor(kind: CueBannerKind, alpha: Float): Color = when (kind) {
    CueBannerKind.ALERT -> ZoneRed.copy(alpha = alpha)
    CueBannerKind.GUIDANCE -> GradientPink.copy(alpha = alpha)
    CueBannerKind.MILESTONE -> ZoneGreen.copy(alpha = alpha)
    CueBannerKind.INFO -> Color.White.copy(alpha = alpha * 0.4f)
}
