package app.koyura.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.DesignTokens

/**
 * 列表快速滚动条基础：12dp 全圆角 thumb + 细轨道，拖动即时回调归一化进度 0..1。
 *
 * 复刻 Komikku `FastScroller`：仅滚动时显示（停止后立即淡出，不做延迟动画）、
 * 颜色 primary、宽 12dp、距内容区 8dp（由调用方 modifier 的 padding 决定），
 * 支持拖动跳转；气泡提示由调用方在列表侧渲染（本组件只提供基础条与进度回调）。
 *
 * 使用 Foundation [Canvas] + [detectDragGestures] 实现，不引入新依赖也不碰实验 API。
 * 白皮书出处：06 §6.15 快速滚动条、02 §2.7 `FastScrollerThumb`=12dp、9.5 #20/#30。
 *
 * @param visible 是否显示；false 时立即 alpha=0（停止后立即淡出）。
 * @param progress 当前滚动进度 0..1，决定 thumb 纵向位置。
 * @param onProgressChange 拖动时回调归一化进度，调用方据此跳转到列表位置。
 * @param thumbColor thumb 色，默认 primary。
 * @param trackColor 轨道色，默认 onSurface 12%。
 */
@Composable
fun FastScroller(
    visible: Boolean,
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = TrackAlpha),
) {
    val normalized = progress.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .width(DesignTokens.Sizes.FastScrollerThumb)
            .fillMaxHeight()
            .alpha(if (visible) 1f else 0f)
            .pointerInput(onProgressChange) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onProgressChange((offset.y / size.height.toFloat()).coerceIn(0f, 1f))
                    },
                    onDrag = { change, _ ->
                        onProgressChange((change.position.y / size.height.toFloat()).coerceIn(0f, 1f))
                    },
                )
            },
    ) {
        val width = size.width
        val height = size.height
        val radius = CornerRadius(width / 2f)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(width * TrackInsetFraction, 0f),
            size = Size(width * TrackWidthFraction, height),
            cornerRadius = radius,
        )

        val thumbHeight = ThumbLength.toPx().coerceAtMost(height)
        val thumbTop = normalized * (height - thumbHeight)
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(0f, thumbTop),
            size = Size(width, thumbHeight),
            cornerRadius = radius,
        )
    }
}

// 轨道是 thumb 居中的 30% 窄条；thumb 长度 36dp（06 §6.15「宽 12dp、全圆角」）。
private const val TrackInsetFraction = 0.35f
private const val TrackWidthFraction = 0.3f
private const val TrackAlpha = 0.12f
private val ThumbLength = 36.dp
