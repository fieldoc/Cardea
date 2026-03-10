package com.hrcoach.ui.components

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hrcoach.ui.theme.CardeaTextTertiary
import com.hrcoach.ui.theme.GlassHighlight
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientPink

/**
 * Cardea-styled slider. Active track and thumb use GradientBlue; inactive track uses GlassHighlight.
 * Replaces all bare [Slider] usages to prevent Material 3 purple defaults from leaking in.
 */
@Composable
fun CardeaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = GradientBlue,
            activeTrackColor = GradientBlue,
            inactiveTrackColor = GlassHighlight,
            activeTickColor = GradientBlue,
            inactiveTickColor = CardeaTextTertiary
        )
    )
}

/**
 * Cardea-styled switch. Checked state uses GradientPink track; unchecked uses GlassHighlight.
 * Replaces all bare [Switch] usages to enforce token consistency.
 */
@Composable
fun CardeaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = GradientPink,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = GlassHighlight,
            uncheckedThumbColor = CardeaTextTertiary
        )
    )
}
