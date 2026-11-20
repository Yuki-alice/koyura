package app.koyura.app.ui

import app.koyura.app.resources.utility_back
import app.koyura.app.resources.Res
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.EmptyScreen
import app.koyura.designsystem.components.UiTheme

/**
 * More 页里尚未完整落地的工具页统一占位。
 *
 * 保持 64dp 顶栏 + 颜文字空态 + 一个返回出口（白皮书 9.5 #17），
 * 让「入口可点、返回栈正确、截图可证伪」先成立；真实页面在后续轮次替换。
 * 颜文字固定，保证离屏截图确定性。
 */
@Composable
fun UtilityPageScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(UiTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DesignTokens.Sizes.TopBarHeight)
                .padding(horizontal = DesignTokens.Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = title,
                style = UiTheme.typography.titleLarge,
                color = UiTheme.colorScheme.onSurface,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            EmptyScreen(
                emoji = "(・∀・)",
                message = message,
                actionLabel = stringResource(Res.string.utility_back),
                onAction = onBack,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
