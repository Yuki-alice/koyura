package app.koyura.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.koyura.designsystem.DesignTokens

/**
 * 页码滑块：原版「圆点 thumb + 药丸轨道 + 两端页码」形态。
 *
 * 复刻 Komikku `presentation-core/.../material/Slider.kt` 的 Int 包装语义：
 * - [valueRange] 用 [IntProgression] 表达首末页，`steps` 默认为 `(last - first)`，
 *   与 M3 `steps = 0` 含义一致（两端之间无中间停靠点）；
 * - [onValueChangeFinished] 原样透传给 M3 [Slider]，拖拽结束才落盘页码；
 * - 显示值取整，避免浮点页码。
 *
 * 轨道与 thumb 均为 M3 [Slider] 默认药丸/圆点形状，颜色取主题 primary；
 * 两端页码用 `labelMedium` / `onSurfaceVariant`，最小触摸高度取
 * [DesignTokens.Sizes.ReaderSliderRadius] × 2（24dp 半径 → 48dp 命中区）。
 *
 * 白皮书出处：06 §6.10 阅读器滑块、02 §2.7 sizes/radius、9.5 #20。
 *
 * @param value 当前页，会被约束到 [valueRange] 内。
 * @param valueRange 页码范围（只使用 first/last，step 不参与 M3 Slider 的等距 steps）。
 * @param startLabel 左端页码文案，默认取范围首项。
 * @param endLabel 右端页码文案，默认取范围末项。
 */
@Composable
fun ReaderSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntProgression,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = (valueRange.last - valueRange.first).coerceAtLeast(0),
    onValueChangeFinished: (() -> Unit)? = null,
    startLabel: String = valueRange.first.toString(),
    endLabel: String = valueRange.last.toString(),
) {
    val range = valueRange.first.toFloat()..valueRange.last.toFloat()
    val colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DesignTokens.Sizes.ReaderSliderRadius * 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
    ) {
        Text(
            text = startLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value.toFloat().coerceIn(range.start, range.endInclusive),
            onValueChange = { onValueChange(it.toInt()) },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            valueRange = range,
            steps = steps.coerceAtLeast(0),
            onValueChangeFinished = onValueChangeFinished,
            colors = colors,
        )
        Text(
            text = endLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
