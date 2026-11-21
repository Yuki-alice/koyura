package app.koyura.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.DesignTokens

/**
 * 徽标组（白皮书 06 §6.6）：18dp 高、整组裁 4dp 圆角、子块直角。
 *
 * 单个徽标自身**不加圆角**——如果每块都圆角，相邻两块之间会出现"两个圆角挨着"的裂缝；
 * 整组只裁剪一次，视觉上才是连体色块。
 */
@Composable
fun BadgeGroup(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(shape)
            .height(DesignTokens.Sizes.BadgeHeight),
        content = content,
    )
}

/** 组内单块徽标：直角、3×1dp 内边距、`bodySmall` Medium 12sp。 */
@Composable
fun GroupBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
    textColor: Color = MaterialTheme.colorScheme.onSecondary,
    shape: Shape = RectangleShape,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(shape)
            .background(color)
            .padding(DesignTokens.Sizes.BadgeContentPadding),
        color = textColor,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** 语言徽标（有国旗图标时）：透明底、固定 25×18dp，图标由调用方绘制。 */
@Composable
fun IconBadge(
    modifier: Modifier = Modifier,
    color: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(color)
            .width(25.dp)
            .height(DesignTokens.Sizes.BadgeHeight),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        content()
    }
}
