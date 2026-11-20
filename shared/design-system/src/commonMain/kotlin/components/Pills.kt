package app.koyura.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.DesignTokens

/**
 * 计数胶囊（白皮书 06 §6.11）：28dp 全圆、6×1dp 内边距、左间距 4dp。
 *
 * @param count 计数文字；书架标题旁按 14sp，Tab 内按 10sp（[fontSizeSp]）。
 * @param onSurface 当前 surface 是否为暗色；决定计数底色 `onBackground` 8% / 12%。
 */
@Composable
fun CountPill(
    count: String,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 14f,
    onSurface: Boolean = false,
) {
    val alpha = if (onSurface) DesignTokens.Alpha.PillCountDark else DesignTokens.Alpha.PillCountLight
    Surface(
        modifier = modifier.padding(start = DesignTokens.Spacing.ExtraSmall),
        shape = RoundedCornerShape(DesignTokens.Radius.ExtraLarge),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = DesignTokens.Spacing.Compact,
                vertical = DesignTokens.Spacing.Hairline,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = fontSizeSp.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

/**
 * 标签 Chip（白皮书 06 §6.12）：8dp 圆角、32dp 高、1dp `outline` 边框、**透明底**。
 *
 * 视觉上是描边胶囊而非实心标签；不要用 `AssistChip` 默认的填充色。
 */
@Composable
fun TagChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SuggestionChip(
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        shape = RoundedCornerShape(DesignTokens.Radius.Small),
        border = BorderStroke(DesignTokens.Spacing.Hairline, MaterialTheme.colorScheme.outline),
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
