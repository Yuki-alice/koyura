package app.koyura.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import app.koyura.designsystem.DesignTokens

/**
 * 全局横幅的七种语义（白皮书 06 §6.16）。
 *
 * 每个语义只映射到一对 `container / content` 角色色，页面不写死色值。
 */
enum class BannerKind {
    DOWNLOADED_ONLY,
    INCOGNITO,
    INDEXING,
    RESTORING,
    SYNCING,
    UPDATING,
    WARNING,
}

/**
 * 全局横幅：14sp 居中、16dp 内边距、容器语义色。
 *
 * `applyStatusBarInset` 只在**栈顶**那一条传 true；多条堆叠时其余传 false，
 * 否则状态栏高度会被重复计算（白皮书 06 §6.16 的显式要求）。
 *
 * @param progress 0..1 的进度；仅 RESTORING / SYNCING / UPDATING 会显示百分比。
 */
@Composable
fun StatusBanner(
    kind: BannerKind,
    text: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    applyStatusBarInset: Boolean = false,
) {
    val pair = bannerColors(kind)
    val showProgress = progress != null && kind in setOf(
        BannerKind.RESTORING,
        BannerKind.SYNCING,
        BannerKind.UPDATING,
    )
    val label = if (showProgress) {
        "$text ${formatBannerPercent(progress!!)}"
    } else {
        text
    }
    var content = modifier.fillMaxWidth()
    if (applyStatusBarInset) {
        content = content.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    }
    Surface(modifier = content, color = pair.first) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth().padding(DesignTokens.Spacing.Medium),
            color = pair.second,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 横幅栈：按顺序堆叠多条横幅，只有第一条带状态栏 inset。
 *
 * 展开/收起用 `expandVertically` / `shrinkVertically`，时长跟随 M3 默认。
 */
@Composable
fun BannerStack(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) { content() }
}

/** 单条横幅的入场动画包装。 */
@Composable
fun AnimatedBanner(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        content()
    }
}

@Composable
private fun bannerColors(kind: BannerKind): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (kind) {
        BannerKind.DOWNLOADED_ONLY, BannerKind.UPDATING -> scheme.tertiary to scheme.onTertiary
        BannerKind.INCOGNITO -> scheme.primary to scheme.onPrimary
        BannerKind.INDEXING, BannerKind.SYNCING -> scheme.secondary to scheme.onSecondary
        BannerKind.RESTORING, BannerKind.WARNING -> scheme.error to scheme.onError
    }
}

/** 百分比：向下取整、0 位小数（白皮书 06 §6.16）。 */
internal fun formatBannerPercent(progress: Float): String {
    val percent = kotlin.math.floor(progress.coerceIn(0f, 1f) * 100f).toInt()
    return "$percent%"
}
