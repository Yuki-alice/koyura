package app.koyura.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.koyura.designsystem.DesignTokens

/**
 * 阅读器骨架：顶栏 / 起始侧栏 / 内容 / 底栏 / 提示宿主统一在一处布局，insets 由骨架一次性消费。
 *
 * 复刻 Komikku `presentation-core/.../material/Scaffold.kt` 的要点：顶栏不受高度约束、
 * 起始侧栏（NavigationRail）占位、FAB/底栏高度参与内容 inset。这里用 Foundation 的
 * [Column] + [Row] 做等价结构，避免依赖 Material3 实验性 `Scaffold` 重载：
 * - [contentWindowInsets] 由骨架在根容器上 [windowInsetsPadding] 一次消费，slot 内部不再重复消费；
 * - [contentPadding] 是调用方追加的内容边距（白皮书 03 §3.2 的「Scaffold 内边距 + 8dp」）；
 * - [startBar] 为空时自身无宽度，手机/平板形态切换不改动骨架。
 *
 * 白皮书出处：03 §3.2/§3.3 手机与平板骨架、06 §6.2 底栏 80dp / 侧栏 80dp、9.5 #06/#20。
 */
@Composable
fun ReaderScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    startBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(contentWindowInsets),
        ) {
            topBar()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Box {
                    startBar()
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(contentPadding),
                ) {
                    content()
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = SnackbarBottomPadding),
            ) {
                snackbarHost()
            }
            bottomBar()
        }
    }
}

// 提示宿主位于底栏之上：底栏 80dp（DesignTokens.Sizes.NavigationBarHeight）+ 16dp 间距，
// 白皮书 06 §6.2 / 03 §3.2。
private val SnackbarBottomPadding =
    DesignTokens.Sizes.NavigationBarHeight + DesignTokens.Spacing.Medium
