package app.koyura.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.DesignTokens

/**
 * 设置项行：最小高度 56dp（≥ 9.5 #20 的 48dp 触摸目标），水平内边距 16dp。
 *
 * 复刻 Komikku `PreferenceItem`：24dp 图标槽 + 8dp 间距、标题 16sp（titleLarge 字阶）、
 * 副标题 12sp / 0.78 且最多 10 行、控件在行尾右内边距 16dp。
 * 平板选中态按 9.5 #27 / 06 §6.5 用 24dp 圆角 + `surfaceVariant` 背景，无过渡动画（9.6 坑 08）。
 *
 * @param icon 起始图标槽，调用方负责 contentDescription（通过自身 Icon/ActionIcon）。
 * @param control 行尾控件槽（Switch/Checkbox 等）。
 * @param selected 选中态；切换仅改背景，不改位置。
 * @param onClick 为空时行不可点击。
 */
@Composable
fun SettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    control: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(
        if (selected) DesignTokens.Radius.SettingsSelected else 0.dp,
    )
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.Transparent
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = DesignTokens.Sizes.SettingsItemMinHeight)
        .clip(shape)
        .background(containerColor)
        .padding(horizontal = DesignTokens.Spacing.Medium)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .then(modifier)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(modifier = Modifier.size(SettingsIconSize)) {
                icon()
            }
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.Small))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = DesignTokens.Alpha.Secondary,
                    ),
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (control != null) {
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.Small))
            Box(modifier = Modifier.padding(vertical = DesignTokens.Spacing.Small)) {
                control()
            }
        }
    }
}

/**
 * 设置分组：可选标题 + 若干 [SettingsItem]，全宽无卡片（白皮书 06 §6.5「设置主页无卡片」）。
 *
 * 组标题用 14sp SemiBold / `onSurfaceVariant`，对应白皮书 `Typography.header` 的语义。
 */
@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.padding(
                    horizontal = DesignTokens.Spacing.Medium,
                    vertical = DesignTokens.Spacing.Small,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        content()
    }
}

// 图标槽 24dp（白皮书 06 §6.5 设置项「icon 24dp」）。
private val SettingsIconSize = 24.dp
