package app.koyura.features.reader.comic.chrome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppSlider
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.UiTheme

/**
 * 条漫模式的竖排页码滑杆（自适应版，对照 Komikku `ChapterNavigator` 的
 * 旋转布局实现：`transformOrigin(0,0)` + 交换约束的 `layout` + `weight(1f)`）。
 *
 * 结构与原版一致：整条竖向占满可用高度，上下是当前页/总页文本，中间滑杆
 * 旋转 90° 后按「宽高互换」测量，因此高度完全由画布决定（不再固定 280/64dp）。
 * 值域与步进沿用底部滑杆：`1..pageCount`，`steps = pageCount - 2`，拖动触发
 * TextHandleMove 触觉，松手才真正跳页。
 */
@Composable
fun VerticalPageSlider(
    pageIndex: Int,
    pageCount: Int,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var preview by remember(pageIndex, pageCount) { mutableStateOf(pageIndex) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = DesignTokens.Spacing.Small, horizontal = DesignTokens.Spacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppText(
            text = "${preview + 1}",
            style = UiTheme.typography.bodySmall,
            color = UiTheme.colorScheme.onSurface,
        )
        Box(modifier = Modifier.weight(1f)) {
            AppSlider(
                value = (preview + 1).toFloat(),
                onValueChange = {
                    preview = (it.toInt() - 1).coerceIn(0, pageCount - 1)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onValueChangeFinished = {
                    if (preview != pageIndex) onJump(preview)
                },
                valueRange = 1f..pageCount.toFloat(),
                steps = (pageCount - 2).coerceAtLeast(0),
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        rotationZ = 90f
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    // 宽高互换：旋转后的视觉长度等于容器高度，厚度随容器宽度自适应。
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            Constraints(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                                minHeight = constraints.minWidth,
                                maxHeight = constraints.maxWidth,
                            ),
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(0, -placeable.height)
                        }
                    }
                    .testTag(ReaderChromeVerticalSliderTag),
            )
        }
        AppText(
            text = pageCount.toString(),
            style = UiTheme.typography.bodySmall,
            color = UiTheme.colorScheme.onSurfaceVariant.copy(
                alpha = DesignTokens.Alpha.Secondary,
            ),
        )
        Spacer(Modifier.padding(top = DesignTokens.Spacing.ExtraSmall))
    }
}
