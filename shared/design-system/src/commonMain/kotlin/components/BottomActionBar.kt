package app.koyura.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.DesignTokens
import kotlinx.coroutines.withTimeoutOrNull

/** 底部动作栏的一项。 */
data class BottomAction(
    val icon: ActionIcon,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val tint: Color? = null,
)

/**
 * 底部动作栏（白皮书 06 §6.15）。
 *
 * 仅上圆角 16dp、`surfaceContainerHigh` 底、按钮 48×48、`Row` 间距 12dp / 水平内边距 8dp。
 * 长按 1 秒展开标签（`labelSmall` 11sp），展开后每项权重由 1f 变 2f——这是详情页
 * "不知道图标含义时按住看名称"的交互，不是常驻标签。
 */
@Composable
fun BottomActionBar(
    actions: List<BottomAction>,
    modifier: Modifier = Modifier,
    holdToRevealMs: Long = 1000L,
) {
    var expanded by remember { mutableStateOf(false) }
    val weight by animateFloatAsState(if (expanded) 2f else 1f, label = "bottomActionWeight")
    val holdModifier = Modifier.pointerInput(holdToRevealMs) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val released = withTimeoutOrNull(holdToRevealMs) { waitForUpOrCancellation() }
            if (released == null) expanded = true
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth().then(holdModifier),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(
            topStart = DesignTokens.Sizes.BottomActionMenuRadius,
            topEnd = DesignTokens.Sizes.BottomActionMenuRadius,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.Spacing.Small, vertical = DesignTokens.Spacing.Small),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Regular),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action ->
                Box(modifier = Modifier.weight(weight), contentAlignment = Alignment.Center) {
                    if (expanded) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ActionIconButton(
                                icon = action.icon,
                                label = action.label,
                                onClick = action.onClick,
                                enabled = action.enabled,
                                contentColor = action.tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(DesignTokens.Sizes.MinimumTouchTarget),
                            )
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        ActionIconButton(
                            icon = action.icon,
                            label = action.label,
                            onClick = action.onClick,
                            enabled = action.enabled,
                            contentColor = action.tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.height(DesignTokens.Sizes.MinimumTouchTarget),
                        )
                    }
                }
            }
        }
    }
}
