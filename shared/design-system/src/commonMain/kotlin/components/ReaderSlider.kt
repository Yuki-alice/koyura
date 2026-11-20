package app.koyura.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlin.math.roundToInt
import app.koyura.designsystem.DesignTokens

/** Page navigator with a fixed-height pill and an integer slider that commits on release. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    sliderModifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
    onValueChangeFinished: (() -> Unit)? = null,
    startLabel: String = value.toString(),
    endLabel: String = valueRange.last.toString(),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val range = valueRange.first.toFloat()..valueRange.last.toFloat()
    val sliderState = remember(range, steps) { SliderState(value.toFloat(), steps.coerceAtLeast(0), range) }
    SideEffect { sliderState.value = value.toFloat().coerceIn(range.start, range.endInclusive) }
    val colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelMedium

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DesignTokens.Sizes.MinimumTouchTarget)
            .clip(RoundedCornerShape(DesignTokens.Sizes.ReaderSliderRadius))
            .background(containerColor)
            .padding(horizontal = DesignTokens.Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
    ) {
        Box(contentAlignment = Alignment.CenterEnd) {
            Text(endLabel, color = Color.Transparent, style = labelStyle, modifier = Modifier.clearAndSetSemantics {})
            Text(startLabel, color = labelColor, style = labelStyle)
        }
        Slider(
            state = sliderState,
            onValueChange = { onValueChange(it.roundToInt()) },
            modifier = Modifier.weight(1f).then(sliderModifier),
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            colors = colors,
            thumb = {
                Box(
                    Modifier.size(DesignTokens.Sizes.ReaderSliderThumb)
                        .background(if (enabled) colors.thumbColor else colors.disabledThumbColor, CircleShape),
                )
            },
        )
        Text(endLabel, color = labelColor, style = labelStyle)
    }
}
