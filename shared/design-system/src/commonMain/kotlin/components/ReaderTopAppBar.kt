package app.koyura.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.DesignTokens

/**
 * 顶栏：首屏透明沉浸，滚动后表面色按 [scrollFraction] 渐显并抬升高度。
 *
 * 复刻 Komikku 详情页 AppBar 的「透明 → surface@3dp」行为（白皮书 9.5 #11 / 9.6 坑 07）：
 * - 容器色取 `surface.copy(alpha = scrolledContainerAlpha * scrollFraction)`，fraction=0 时完全透明；
 * - tonalElevation 从 [DesignTokens.Elevation.AppBarResting] 线性插值到 [DesignTokens.Elevation.AppBarScrolled]；
 * - 滚动进度的计算留在页面层（避免引入实验性 `TopAppBarScrollBehavior`），本组件只消费 0..1 的归一化值。
 *
 * [scrollFraction] 为 0 表示首项可见（透明），为 1 表示已滚动（不透明）。
 * 深色/AMOLED 场景调用方应把 [scrolledContainerAlpha] 覆写为 [DesignTokens.Alpha.ReaderBarDark]。
 *
 * 白皮书出处：06 §6.1 AppBar、02 §2.7 alpha/elevation、9.5 #11/#20。
 */
@Composable
fun ReaderTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    scrollFraction: Float = 0f,
    subtitle: String? = null,
    titleAlpha: Float = 1f,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    scrolledContainerAlpha: Float = 1f,
    // CMP commonMain 没有 statusBars 扩展；用安全区的顶边等价（白皮书 08 edge-to-edge）。
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
) {
    val fraction = scrollFraction.coerceIn(0f, 1f)
    val elevation: Dp = DesignTokens.Elevation.AppBarResting +
        (DesignTokens.Elevation.AppBarScrolled - DesignTokens.Elevation.AppBarResting) * fraction

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(elevation).copy(
            alpha = (scrolledContainerAlpha * fraction).coerceIn(0f, 1f),
        ),
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .heightIn(min = ReaderTopAppBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null) {
                Box(
                    modifier = Modifier.size(NavigationSlotSize),
                    contentAlignment = Alignment.Center,
                ) {
                    navigationIcon()
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                    Text(
                        text = title,
                        color = contentColor.copy(alpha = titleAlpha),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subtitle != null) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        Text(
                            text = subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(content = actions)
        }
    }
}

// M3 SmallTopAppBar 默认 64dp（白皮书 06 §6.1）；图标槽 48dp 满足 9.5 #20 触摸目标。
val ReaderTopAppBarHeight = 64.dp
private val NavigationSlotSize = 48.dp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    androidx.compose.material3.TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}
