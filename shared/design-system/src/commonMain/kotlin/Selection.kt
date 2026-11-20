package app.koyura.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 网格选中态的底：`drawRect(secondary)` 覆盖整格，封面上再叠 76% alpha。
 *
 * 白皮书 08 §3 / 06 §6.3：选中**不是**画 border——边框会在相邻卡片之间留缝，
 * 参考实现是整格铺一层 secondary。`drawRect` 在内容之前绘制，因此封面仍可见但被染色。
 */
fun Modifier.gridSelectedBackground(selected: Boolean, color: Color): Modifier =
    if (!selected) {
        this
    } else {
        drawWithContent {
            drawRect(color = color)
            drawContent()
        }
    }

/** 网格选中时封面的不透明度（`CommonMangaItem.kt:71` `GRID_SELECTED_COVER_ALPHA`）。 */
const val GRID_SELECTED_COVER_ALPHA: Float = 0.76f

/**
 * 列表行选中态的叠加色：亮色 surface 用 22%、暗色用 16%（`Modifier.kt` selectedBackground）。
 * 用 surface 的亮度判定，而不是读系统深色开关——AMOLED/自定义主题下同样成立。
 */
@Composable
fun selectedRowOverlay(): Color {
    val surface = MaterialTheme.colorScheme.surface
    val secondary = MaterialTheme.colorScheme.secondary
    val alpha = if (surface.luminance() < 0.5f) {
        DesignTokens.Alpha.SelectedBackgroundDark
    } else {
        DesignTokens.Alpha.SelectedBackgroundLight
    }
    return remember(secondary, alpha) { secondary.copy(alpha = alpha) }
}
