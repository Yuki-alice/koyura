package app.koyura.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import app.koyura.designsystem.DesignTokens

/**
 * 2:3 固定比例封面容器：尺寸由调用方 [modifier] 决定，本组件只锁比例并裁 4dp 圆角，
 * 加载图片前后共用同一个盒子，因此不会产生 layout shift（9.5 #15 / 9.6 坑 10）。
 *
 * [content] 在 [BoxScope] 内渲染，调用方用 `matchParentSize()` 放图或放占位，
 * 二者共享同一比例与圆角；封面圆角取 [DesignTokens.Radius.ExtraSmall]（06 §6.9 / Covers.kt）。
 *
 * 白皮书出处：03 §3.6 封面 2:3、06 §6.9 封面卡片、9.5 #15。
 */
@Composable
fun CoverAspect(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(CoverAspectRatio)
            .clip(RoundedCornerShape(DesignTokens.Radius.ExtraSmall)),
        content = content,
    )
}

/**
 * 便捷占位：同样的 2:3 盒子里放首字母色块（`surfaceVariant` / `onSurfaceVariant`），
 * 与加载后的 [CoverAspect] 内容形态完全一致，切换不跳动。
 *
 * @param label 取首字母作为占位字符；空白时显示 `?`。
 */
@Composable
fun CoverAspect(
    label: String,
    modifier: Modifier = Modifier,
) {
    CoverAspect(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val CoverAspectRatio = 2f / 3f
