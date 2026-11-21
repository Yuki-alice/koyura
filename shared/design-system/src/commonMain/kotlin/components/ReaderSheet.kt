package app.koyura.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.koyura.designsystem.DesignTokens

/**
 * 自适应弹层：同一份 [content] 在手机上是底部抽屉，在平板上是居中卡片，仅形态不同。
 *
 * 复刻 Komikku `AdaptiveSheet`：手机从底部滑出、顶部 28dp 圆角、可点遮罩关闭；
 * 平板为最大宽 460dp 的居中卡片、四角 28dp、上下 16dp 内边距、禁用滑动手势。
 * 两种形态共用 [SheetCard]，内容组合一次即可。
 *
 * 形态判定：[isTablet] 由调用方按最小宽度传入（白皮书 03 §3.1，应用层的 `smallestScreenWidthDp`
 * 判定不能下沉到 commonMain）；为 null 时按可用宽度 [AutoTabletMinWidth] 自动判定，
 * 取 600dp 对应白皮书「自动模式横屏」阈值，避免旋转时形态抖动。
 *
 * 返回键关闭（9.5 #19）由应用层的 BackHandler 决定顺序，本组件只做可点遮罩关闭（9.5 #08）。
 * 动效 350ms tween（[DesignTokens.Motion.SheetMs]），不使用实验性 ModalBottomSheet。
 *
 * 白皮书出处：03 §3.7、06 §6.7、9.5 #08、9.6 坑 05。
 *
 * @param onDismissRequest 点击遮罩时请求关闭。
 * @param isTablet true 强制平板居中卡片，false 强制手机底部抽屉，null 自动判定。
 * @param visible 控制进出场动画；为 false 时内容被移除。
 * @param scrimColor 遮罩色，默认 `Color.Black` + [DesignTokens.Alpha.Backdrop]。
 * @param content 两种形态共用的内容。
 */
@Composable
fun ReaderSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    isTablet: Boolean? = null,
    visible: Boolean = true,
    scrimColor: Color = Color.Black.copy(alpha = DesignTokens.Alpha.Backdrop),
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val tablet = isTablet ?: (maxWidth >= AutoTabletMinWidth)
        val enterAlphaSpec = tween<Float>(durationMillis = DesignTokens.Motion.SheetMs)
        val exitAlphaSpec = tween<Float>(durationMillis = DesignTokens.Motion.SheetMs)
        val enterOffsetSpec = tween<IntOffset>(durationMillis = DesignTokens.Motion.SheetMs)
        val exitOffsetSpec = tween<IntOffset>(durationMillis = DesignTokens.Motion.SheetMs)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )

        val alignment = if (tablet) Alignment.Center else Alignment.BottomCenter
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = alignment) {
            AnimatedVisibility(
                visible = visible,
                enter = if (tablet) {
                    fadeIn(animationSpec = enterAlphaSpec) + scaleIn(animationSpec = enterAlphaSpec)
                } else {
                    slideInVertically(animationSpec = enterOffsetSpec, initialOffsetY = { it })
                },
                exit = if (tablet) {
                    fadeOut(animationSpec = exitAlphaSpec) + scaleOut(animationSpec = exitAlphaSpec)
                } else {
                    slideOutVertically(animationSpec = exitOffsetSpec, targetOffsetY = { it })
                },
            ) {
                if (tablet) {
                    SheetCard(
                        topOnlyCorners = false,
                        modifier = Modifier
                            .widthIn(max = DesignTokens.Breakpoints.SheetMaxWidth)
                            .padding(vertical = SheetVerticalPadding),
                        content = content,
                    )
                } else {
                    SheetCard(
                        topOnlyCorners = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .swipeDownToDismiss(onDismissRequest),
                        content = content,
                    )
                }
            }
        }
    }
}

/** 两种形态共用的表面：28dp 圆角、`surfaceContainerHigh`、垂直 16dp 内边距。 */
@Composable
private fun SheetCard(
    topOnlyCorners: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = if (topOnlyCorners) {
        RoundedCornerShape(
            topStart = DesignTokens.Radius.ExtraLarge,
            topEnd = DesignTokens.Radius.ExtraLarge,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        )
    } else {
        RoundedCornerShape(DesignTokens.Radius.ExtraLarge)
    }

    Surface(
        modifier = modifier.testTag(ReaderSheetCardTag),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SheetVerticalPadding),
            content = content,
        )
    }
}

// 自动形态判定阈值：白皮书 03 §3.1「自动模式横屏达到 600dp 即启用平板布局」。
private val AutoTabletMinWidth = 600.dp

// 弹层垂直内边距 16dp（白皮书 06 §6.7 / 03 §3.7）。
private val SheetVerticalPadding = DesignTokens.Spacing.Medium

/** 弹层卡片锚点：尺寸采样用（手机满宽、平板 ≤460dp）。 */
const val ReaderSheetCardTag = "reader-sheet-card"

/** Presents an adaptive sheet in its own focus scope so Back dismisses the sheet first. */
@Composable
fun ModalReaderSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        ReaderSheet(onDismissRequest = onDismissRequest, content = content)
    }
}

/**
 * 手机底部弹层的下滑关闭：累计向下拖动超过 56dp 即关闭（白皮书 06 §6.13）。
 *
 * 只做阈值判定，不做跟手位移——跟手位移需要把偏移暴露给父布局，会和 sheet 内滚动争手势；
 * 参考实现也是「过阈值即关闭」。
 */
private fun Modifier.swipeDownToDismiss(onDismiss: () -> Unit): Modifier = this.pointerInput(Unit) {
    var accumulated = 0f
    detectVerticalDragGestures(
        onDragStart = { accumulated = 0f },
        onVerticalDrag = { change, dragAmount ->
            change.consume()
            accumulated += dragAmount
        },
        onDragEnd = {
            if (accumulated > DesignTokens.Sizes.SheetDismissThreshold.toPx()) onDismiss()
        },
        onDragCancel = { accumulated = 0f },
    )
}
