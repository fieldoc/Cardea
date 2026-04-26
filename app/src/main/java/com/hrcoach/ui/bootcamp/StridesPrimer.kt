package com.hrcoach.ui.bootcamp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hrcoach.ui.components.CardeaButton
import com.hrcoach.ui.theme.CardeaBgPrimary
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GlassBorder

/**
 * One-time educational primer shown when a user is about to start their first
 * bootcamp **strides** session. Explains what strides are (form, not speed)
 * and what audio cues to expect. Dismissed permanently via the
 * `stridesPrimerSeen` flag in [com.hrcoach.domain.model.AudioSettings].
 *
 * Mirrors the visual conventions of [com.hrcoach.ui.workout.AudioPrimerDialog]:
 * opaque [CardeaBgPrimary] container (so underlying screen content does not
 * bleed through), [GlassBorder] outline, rounded 20dp corners, and
 * [com.hrcoach.ui.components.CardeaButton] for the "Got it" action.
 *
 * @param totalReps Number of strides reps in the upcoming session (e.g. 4, 6,
 *   8, 10). Surfaced verbatim in the body copy so the user knows what they're
 *   in for.
 * @param onDismiss Called from the "Got it" button. The caller is expected to
 *   persist the seen flag (see [BootcampViewModel.dismissStridesPrimer]).
 */
@Composable
fun StridesPrimer(
    totalReps: Int,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
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
            Text(
                text = "Strides — what they are",
                style = MaterialTheme.typography.headlineSmall,
                color = CardeaTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "After your easy run, you'll do $totalReps × 20-second " +
                    "pickups with one minute easy jog between each.",
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTheme.colors.textSecondary
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "These build form and economy — they're not sprints. " +
                    "Aim for ~85–90% effort: fast but smooth, relaxed shoulders, " +
                    "quick feet. If your arms flail, ease off.",
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTheme.colors.textSecondary
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "The app will chime to start each pickup, chime again at " +
                    "20 seconds to ease into the recovery jog, and chime once more " +
                    "when the set is complete.",
                style = MaterialTheme.typography.bodyMedium,
                color = CardeaTheme.colors.textSecondary
            )

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardeaButton(
                    text = "Got it",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    innerPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                )
            }
        }
    }
}
