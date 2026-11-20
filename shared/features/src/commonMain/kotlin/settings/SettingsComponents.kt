package app.koyura.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.DesignTokens

/** HeadingItem：14sp SemiBold / onSurfaceVariant / 24/10 内边距（白皮书 06 §6.5、07 §7.9）。 */
@Composable
internal fun SettingsSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = DesignTokens.Spacing.Large,
                vertical = DesignTokens.Spacing.Small + 2.dp,
            ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
    )
}

/**
 * BasePreferenceWidget：56dp 最小高、16/16 内边距、24dp 主色图标、16sp 标题、12sp/0.78 副标题。
 * [onClick] 为空时退化为不可点的展示行（用于禁用项与只读说明）。
 */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ActionIcon? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    trailing: @Composable () -> Unit = {},
    testTag: String? = null,
) {
    val clickModifier = if (enabled && onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .heightIn(min = DesignTokens.Sizes.SettingsItemMinHeight)
            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Medium)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            ActionIcon(
                icon = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(DesignTokens.Spacing.Medium))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DesignTokens.Alpha.Disabled)
                    danger -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DesignTokens.Alpha.Secondary),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(DesignTokens.Spacing.Small))
        trailing()
    }
}

/** 开关行：父行不拦截点击，避免与 Switch 的 toggleable 重复消费。 */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ActionIcon? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    SettingsRow(
        title = title,
        modifier = modifier,
        icon = icon,
        subtitle = subtitle,
        enabled = enabled,
        testTag = testTag,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.then(if (testTag != null) Modifier.testTag("$testTag-switch") else Modifier),
            )
        },
    )
}

/** 列表选择行：行尾显示当前值 + 右箭头，适合打开子页 / 切换枚举。 */
@Composable
internal fun SettingsChoiceRow(
    title: String,
    currentValue: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ActionIcon? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    SettingsRow(
        title = title,
        modifier = modifier,
        icon = icon,
        subtitle = subtitle,
        onClick = onClick,
        enabled = enabled,
        testTag = testTag,
        trailing = {
            Text(
                text = currentValue,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(DesignTokens.Spacing.ExtraSmall))
            ActionIcon(icon = ActionIcon.Next, contentDescription = null)
        },
    )
}

/** 滑块行：标题 + 当前值 + M3 Slider，触摸目标由 Slider 自身保证。 */
@Composable
internal fun SettingsSliderRow(
    title: String,
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    testTag: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
            )
            Text(
                text = valueText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Slider(
            value = value.coerceIn(valueRange),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 统一空态文案：Tracking / Connections 等未连接页面的占位说明。 */
@Composable
internal fun SettingsEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = DesignTokens.Spacing.Large,
                vertical = DesignTokens.Spacing.ExtraLarge,
            ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** 主题预览色：只取语义色板，不写死 hex；19 套主题循环映射到 6 个语义色。 */
@Composable
internal fun previewSwatchColor(ordinal: Int): Color = when (ordinal % 6) {
    0 -> MaterialTheme.colorScheme.primary
    1 -> MaterialTheme.colorScheme.secondary
    2 -> MaterialTheme.colorScheme.tertiary
    3 -> MaterialTheme.colorScheme.primaryContainer
    4 -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.tertiaryContainer
}

/** 主题卡内的两段式预览：上段取语义色，下段 surfaceVariant 形成对比。 */
@Composable
internal fun SettingsPreviewSwatch(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(color, RoundedCornerShape(DesignTokens.Radius.Small)),
        )
        Spacer(Modifier.height(DesignTokens.Spacing.ExtraSmall))
        Box(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(DesignTokens.Radius.ExtraSmall),
                ),
        )
    }
}

/** 横向分段控件：48dp 高，选中态 primaryContainer，未选中 onSurfaceVariant。 */
@Composable
internal fun <T> SettingsSegmentedControl(
    items: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "settings-segment",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(DesignTokens.Radius.Medium),
            )
            .padding(DesignTokens.Spacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in items) {
            val isSelected = item == selected
            val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            val fg = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = labelOf(item),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clickable { onSelect(item) }
                    .background(bg, shape = RoundedCornerShape(DesignTokens.Radius.Small))
                    .padding(horizontal = DesignTokens.Spacing.Small, vertical = DesignTokens.Spacing.Small)
                    .testTag("$testTagPrefix-$item"),
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
