package com.hrcoach.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.hrcoach.ui.theme.CardeaTheme
import com.hrcoach.ui.theme.GradientBlue
import com.hrcoach.ui.theme.GradientPink

@Composable
fun CardeaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val colors = CardeaTheme.colors
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = GradientPink,
            activeTrackColor = GradientPink,
            inactiveTrackColor = colors.glassHighlight,
            activeTickColor = GradientPink,
            inactiveTickColor = colors.textTertiary
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun cardeaSegmentedButtonColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = CardeaTheme.colors.glassHighlight,
    activeContentColor = GradientBlue,
    activeBorderColor = GradientBlue,
    inactiveContainerColor = Color.Transparent,
    inactiveContentColor = CardeaTheme.colors.textSecondary,
    inactiveBorderColor = CardeaTheme.colors.glassBorder
)

@Composable
fun CardeaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = CardeaTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = GradientPink,
            checkedThumbColor = colors.onGradient,
            uncheckedTrackColor = colors.glassHighlight,
            uncheckedThumbColor = colors.textTertiary
        )
    )
}
