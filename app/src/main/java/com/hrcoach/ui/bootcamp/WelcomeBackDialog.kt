package com.hrcoach.ui.bootcamp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hrcoach.ui.theme.CardeaAccentPink
import com.hrcoach.ui.theme.CardeaCtaGradient3Stop
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientPink

// ─── Welcome Back Dialog ────────────────────────────────────────────────────

/**
 * Restructured post-gap "Welcome Back" dialog. Renders the disclosure as two
 * labeled sections (SCHEDULE always; INTENSITY when applicable) with concrete
 * numbers em-highlighted in textPrimary. Replaces the prior single-string
 * dialog where tier-easing copy silently overrode the schedule rewind.
 *
 * See `docs/cardea/2026-05-05-welcome-back-disclosure.md`.
 */
@Composable
fun WelcomeBackDialog(
    disclosure: WelcomeBackDisclosure,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (CardeaTheme.colors.isDark) CardeaTheme.colors.bgSecondary
                        else CardeaTheme.colors.bgPrimary
                    )
                    .border(
                        width = 1.dp,
                        color = CardeaTheme.colors.glassBorderStrong,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                // ── Title row (icon + "Welcome Back") ────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = if (CardeaTheme.colors.isDark) GradientPink else CardeaAccentPink,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Welcome Back",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                        lineHeight = 20.sp,
                        color = CardeaTheme.colors.textPrimary
                    )
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = CardeaTheme.colors.glassBorder
                )

                // ── SCHEDULE section ─────────────────────────────────────
                DisclosureSection(
                    label = "Schedule",
                    body = scheduleBody(disclosure.schedule)
                )

                // ── INTENSITY section (optional) ─────────────────────────
                disclosure.intensity?.let { intensity ->
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = CardeaTheme.colors.glassBorder
                    )
                    DisclosureSection(
                        label = "Intensity",
                        body = intensityBody(intensity)
                    )
                }

                // ── Footer CTA — single-source-of-emphasis gradient ──────
                Box(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardeaCtaGradient3Stop)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Got it",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclosureSection(label: String, body: AnnotatedString) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            color = CardeaTheme.colors.textTertiary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = CardeaTheme.colors.textSecondary
        )
    }
}

/**
 * Build the SCHEDULE section body, em-highlighting the concrete numbers (week
 * counts, sessions cleared, phase name) in textPrimary against the textSecondary
 * narrative — matches the design's `<em>` styling.
 */
@Composable
private fun scheduleBody(change: WelcomeBackDisclosure.ScheduleChange): AnnotatedString {
    val emStyle = SpanStyle(
        color = CardeaTheme.colors.textPrimary,
        fontWeight = FontWeight.SemiBold
    )
    return when (change) {
        is WelcomeBackDisclosure.ScheduleChange.WeekRollback -> buildAnnotatedString {
            append("Rolled back from week ")
            withStyle(emStyle) { append(change.fromWeek.toString()) }
            append(" to week ")
            withStyle(emStyle) { append(change.toWeek.toString()) }
            append(" of ")
            withStyle(emStyle) { append(change.phaseName) }
            append(". Cleared ")
            withStyle(emStyle) { append(change.sessionsCleared.toString()) }
            append(" upcoming sessions.")
        }
        is WelcomeBackDisclosure.ScheduleChange.PhaseStartReset -> buildAnnotatedString {
            append("Rewound to the start of ")
            withStyle(emStyle) { append(change.phaseName) }
            append(". Cleared ")
            withStyle(emStyle) { append(change.sessionsCleared.toString()) }
            append(" upcoming sessions.")
        }
        is WelcomeBackDisclosure.ScheduleChange.FullReset -> buildAnnotatedString {
            append("Reset to the start of ")
            withStyle(emStyle) { append(change.phaseName) }
            append(". Cleared ")
            withStyle(emStyle) { append(change.sessionsCleared.toString()) }
            append(" upcoming sessions.")
        }
    }
}

@Composable
private fun intensityBody(intensity: WelcomeBackDisclosure.IntensityChange): AnnotatedString {
    val emStyle = SpanStyle(
        color = CardeaTheme.colors.textPrimary,
        fontWeight = FontWeight.SemiBold
    )
    return when (intensity) {
        WelcomeBackDisclosure.IntensityChange.TierEased -> buildAnnotatedString {
            append("Eased your training intensity to match your current fitness.")
        }
        WelcomeBackDisclosure.IntensityChange.DiscoveryRun -> buildAnnotatedString {
            append("Your first run will be a ")
            withStyle(emStyle) { append("Discovery Run") }
            append(" to gauge where you are now.")
        }
    }
}

// ─── Schedule Rewind Breadcrumb ─────────────────────────────────────────────
//
// Ambient pill above the week strip. Persists after dialog dismissal until the
// runner completes a session in the new engine-week (auto-cleared by the VM)
// or taps the X (in-memory dismiss flag). Tier-3 ambient label per Cardea
// hierarchy: glass surface, no gradient — never competes with the next-up card.

@Composable
fun ScheduleRewindBreadcrumb(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(CircleShape)
            .background(CardeaTheme.colors.glassSurface)
            .border(
                width = 1.dp,
                color = CardeaTheme.colors.glassBorder,
                shape = CircleShape
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Restore,
            contentDescription = null,
            tint = if (CardeaTheme.colors.isDark) GradientPink else CardeaAccentPink,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "Adjusted after break",
            fontSize = 12.sp,
            color = CardeaTheme.colors.textSecondary,
            lineHeight = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Dismiss",
            tint = CardeaTheme.colors.textTertiary,
            modifier = Modifier
                .size(14.dp)
                .clickable(onClick = onDismiss)
        )
    }
}
