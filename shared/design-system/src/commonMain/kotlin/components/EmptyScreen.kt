package app.koyura.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import app.koyura.designsystem.DesignTokens

/** 12 个颜文字，随机挑一个作为空态插画（白皮书 06 §6.8「12 个随机颜文字」）。 */
private val EmptyKaomoji = listOf(
    "(･o･;)",
    "Σ(ಠ_ಠ)",
    "ಥ_ಥ",
    "(˘･_･˘)",
    "(；￣Д￣)",
    "(･Д･。",
    "(╬ಠ益ಠ)",
    "(╥﹏╥)",
    "(⋟﹏⋞)",
    "Ò︵Ó",
    "˙ᯅ˙",
    "(¬_¬)",
)

/** 随机取一个颜文字；调用方用 `remember { randomEmptyKaomoji() }` 固定住，避免重组跳动。 */
fun randomEmptyKaomoji(): String = EmptyKaomoji.random()

/**
 * 统一空态：颜文字（displayMedium 45sp / 0.78）+ 解释文案（bodyMedium / 0.78）+ 可选动作行。
 *
 * 复刻 Komikku `EmptyScreen`：水平 24dp，动作行距文案 24dp，动作用 primary 的 [TextButton]。
 * 不写死任何颜色，深浅/AMOLED 三套主题都只走语义色（9.5 #02/#17/#28）。
 *
 * 白皮书出处：06 §6.8 空状态与图 6-5、9.5 #17。
 *
 * @param emoji 插画文案，可用 `remember { randomEmptyKaomoji() }` 取一个颜文字。
 * @param message 解释文案。
 * @param actionLabel 动作文案；为 null 时不渲染动作。
 * @param onAction 动作回调；为 null 时不渲染动作。
 */
@Composable
fun EmptyScreen(
    emoji: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    illustrationModifier: Modifier = Modifier,
    supportingText: String? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(
        alpha = DesignTokens.Alpha.Secondary,
    )
    StatusContent(modifier) {
        // 颜文字在 RTL 下会被镜像成乱序，强制 LTR（白皮书 06 §6.17）。
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = emoji,
                modifier = illustrationModifier,
                style = MaterialTheme.typography.displayMedium,
                color = muted,
            )
        }
        Text(
            text = message,
            modifier = Modifier.paddingFromBaseline(top = DesignTokens.Spacing.Large),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                modifier = Modifier.padding(top = DesignTokens.Spacing.ExtraSmall),
                color = muted,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        actions?.invoke()
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.padding(top = DesignTokens.Spacing.Large),
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

/** 空态动作按钮（白皮书 06 §6.17）：图标在上、文字在下，间距 4dp。 */
@Composable
fun EmptyActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                icon()
                Spacer(Modifier.height(DesignTokens.Spacing.ExtraSmall))
            }
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 一行等分的空态动作（白皮书 06 §6.17：`Row` + `weight(1f)` + 8dp 间距 + 上边距 24dp）。 */
@Composable
fun EmptyActionRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = DesignTokens.Spacing.Large),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
        content = content,
    )
}


@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AppCircularProgress()
    }
}

@Composable
fun ErrorScreen(
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    StatusContent(modifier) {
        AppText(message, color = UiTheme.colorScheme.error, textAlign = TextAlign.Center)
        Box(Modifier.padding(top = DesignTokens.Spacing.Medium)) { actions() }
    }
}

@Composable
private fun StatusContent(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = DesignTokens.Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}
