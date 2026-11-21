package app.koyura.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.DesignTokens

/** 章节下载的五个状态（白皮书 08 §4：枚举 5 个、视觉 4 组，QUEUE 无进度环）。 */
enum class ChapterDownloadState { NOT_DOWNLOADED, QUEUE, DOWNLOADING, DOWNLOADED, ERROR }

/**
 * 章节下载指示器（白皮书 06 §6.7）。
 *
 * 26dp 直径 + 13dp 描边 + `StrokeCap.Butt` + 0 间隙——这是刻意的"扇形"，不是细环；
 * 箭头 19dp。五个状态里 QUEUE 只画箭头不画环（还没有真实进度），ERROR 用 error 色。
 *
 * 全部用 Canvas 自绘，避免依赖 material-icons：勾与叹号也在这里画。
 */
@Composable
fun ChapterDownloadIndicator(
    state: ChapterDownloadState,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    onClick: (() -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val background = MaterialTheme.colorScheme.background
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = modifier.size(DesignTokens.Sizes.DownloadIndicator).then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        val size = DesignTokens.Sizes.DownloadIndicator
        val stroke = DesignTokens.Sizes.DownloadProgressStroke
        val arrow = DesignTokens.Sizes.DownloadArrow
        when (state) {
            ChapterDownloadState.NOT_DOWNLOADED -> Canvas(Modifier.size(size)) {
                drawDownloadArrow(arrow.toPx(), primary.copy(alpha = DesignTokens.Alpha.Secondary))
            }

            ChapterDownloadState.QUEUE -> Canvas(Modifier.size(size)) {
                drawDownloadArrow(arrow.toPx(), primary)
            }

            ChapterDownloadState.DOWNLOADING -> {
                val fraction = progress.coerceIn(0f, 1f)
                Canvas(Modifier.size(size)) {
                    // 描边居中在 26dp 边界上：画布要内缩半个描边，否则扇形会被裁掉一半。
                    val inset = stroke.toPx() / 2f
                    drawArc(
                        color = primary,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(this.size.width - stroke.toPx(), this.size.height - stroke.toPx()),
                        style = Stroke(width = stroke.toPx(), cap = StrokeCap.Butt),
                    )
                    val arrowColor = if (fraction > 0.5f) background else primary
                    drawDownloadArrow(arrow.toPx(), arrowColor)
                }
            }

            ChapterDownloadState.DOWNLOADED -> Canvas(Modifier.size(size)) {
                drawCheck(arrow.toPx(), primary)
            }

            ChapterDownloadState.ERROR -> Canvas(Modifier.size(size)) {
                drawErrorMark(arrow.toPx(), error)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDownloadArrow(sizePx: Float, color: Color) {
    val center = Offset(this.size.width / 2f, this.size.height / 2f)
    val half = sizePx / 2f
    val shaftTop = center.y - half
    val headBase = center.y + half * 0.05f
    val tip = center.y + half
    val headHalf = half * 0.55f
    drawLine(
        color = color,
        start = Offset(center.x, shaftTop),
        end = Offset(center.x, headBase),
        strokeWidth = sizePx * 0.14f,
        cap = StrokeCap.Butt,
    )
    val head = Path().apply {
        moveTo(center.x, tip)
        lineTo(center.x - headHalf, headBase)
        lineTo(center.x + headHalf, headBase)
        close()
    }
    drawPath(head, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheck(sizePx: Float, color: Color) {
    val center = Offset(this.size.width / 2f, this.size.height / 2f)
    val half = sizePx / 2f
    val path = Path().apply {
        moveTo(center.x - half * 0.7f, center.y)
        lineTo(center.x - half * 0.15f, center.y + half * 0.55f)
        lineTo(center.x + half * 0.75f, center.y - half * 0.6f)
    }
    drawPath(path, color, style = Stroke(width = sizePx * 0.16f, cap = StrokeCap.Butt))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawErrorMark(sizePx: Float, color: Color) {
    val center = Offset(this.size.width / 2f, this.size.height / 2f)
    val half = sizePx / 2f
    drawLine(
        color = color,
        start = Offset(center.x, center.y - half * 0.85f),
        end = Offset(center.x, center.y + half * 0.3f),
        strokeWidth = sizePx * 0.16f,
        cap = StrokeCap.Butt,
    )
    drawCircle(color, radius = sizePx * 0.08f, center = Offset(center.x, center.y + half * 0.75f))
}

/** 便于测试断言：指示器直径与描边来自同一组令牌。 */
val ChapterDownloadIndicatorSize = DesignTokens.Sizes.DownloadIndicator
val ChapterDownloadIndicatorStroke = DesignTokens.Sizes.DownloadProgressStroke
