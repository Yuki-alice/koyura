package app.koyura.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp

/**
 * 分组卡片容器（白皮书 07 §7.10 统计页）：`surfaceContainer` 底色、[DesignTokens.Radius.Large]
 * 圆角、16dp 内边距，撑满内容宽度。
 *
 * 卡片标题不放在容器内——调用方在卡片外先渲染标题再放卡片（白皮书「标题在卡片外」），
 * 卡片间距 8dp 同理由调用方控制。
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    // 统计页用 28dp（白皮书 07 §7.10）；其他页面沿用 16dp。
    cornerRadius: Dp = DesignTokens.Radius.Large,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(
                horizontal = DesignTokens.Spacing.Medium,
                vertical = DesignTokens.Spacing.Medium,
            ),
        content = content,
    )
}
