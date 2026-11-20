package app.koyura.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.DesignTokens

/** 一个 Tab 的数据描述：[key] 作为稳定标识与选中比较值，[badgeCount] 为空或 0 时不显示计数药丸。 */
data class ReaderTabItem(
    val key: String,
    val label: String,
    val badgeCount: Int? = null,
)

/**
 * 一行 Tab：选中项为 `secondaryContainer` 药丸 + 同色文字，未选中透明底 + `onSurface` 文字。
 *
 * 复刻 Komikku `PrimaryScrollableTabRow` 的读法，但按任务要求把选中指示改为药丸
 * （与 9.3「选中药丸导航」一致）；未选中文字遵循 9.6 坑 09 用 `onSurface` 而非 78% 透明。
 * 每个 Tab 高度 48dp，满足 9.5 #20 触摸目标。
 *
 * 滚动/分页动画（fadeThrough 200ms，9.5 #09）由页面层的内容切换负责，Tab 行自身不加动画。
 *
 * 白皮书出处：06 §6.3、9.3 选中药丸导航、9.5 #09/#20、02 §2.7 alpha。
 */
@Composable
fun ReaderTabs(
    tabs: List<ReaderTabItem>,
    selectedKey: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TabRowHeight)
            .selectableGroup()
            .padding(horizontal = DesignTokens.Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            ReaderPillTab(
                label = tab.label,
                selected = tab.key == selectedKey,
                onClick = { onTabSelected(tab.key) },
                modifier = Modifier.weight(1f),
                badgeCount = tab.badgeCount,
            )
        }
    }
}

/**
 * 单枚药丸 Tab。选中态用 [MaterialTheme.colorScheme.secondaryContainer] 填充与
 * `onSecondaryContainer` 内容色；未选中态透明底、`onSurface` 内容色，无过渡动画（9.6 坑 08）。
 *
 * @param label Tab 文案，同时作为无障碍标签。
 * @param selected 是否选中。
 * @param onClick 点击回调。
 * @param badgeCount 可选计数，渲染为 10sp 的 `onBackground` 8%/12% 叠加药丸（06 §6.4 / 06 §6.3）。
 */
@Composable
fun ReaderPillTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int? = null,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .height(TabRowHeight)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = DesignTokens.Spacing.Small),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (badgeCount != null && badgeCount > 0) {
            Spacer(modifier = Modifier.width(DesignTokens.Spacing.ExtraSmall))
            val pillAlpha = if (isSystemInDarkTheme()) {
                DesignTokens.Alpha.PillCountDark
            } else {
                DesignTokens.Alpha.PillCountLight
            }
            Text(
                text = badgeCount.toString(),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = pillAlpha),
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
    }
}

// 48dp 触摸目标（白皮书 9.5 #20）；计数 10sp 见 06 §6.3/§6.4。
private val TabRowHeight = 48.dp
