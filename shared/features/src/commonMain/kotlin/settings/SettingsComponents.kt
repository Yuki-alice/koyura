package app.koyura.features.settings

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.DecorativeActionIcon
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppSlider
import app.koyura.designsystem.components.AppSwitch
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.UiTheme

/** HeadingItem：14sp SemiBold / onSurfaceVariant / 24/10 内边距（白皮书 06 §6.5、07 §7.9）。 */
@Composable
internal fun SettingsSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    AppText(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = DesignTokens.Spacing.Large,
                vertical = DesignTokens.Spacing.Small + DesignTokens.Spacing.Tight,
            ),
        color = UiTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        style = UiTheme.typography.bodyMedium.copy(fontSize = 14.sp),
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
    selected: Boolean = false,
    twoPane: Boolean = false,
) {
    val clickModifier = if (enabled && onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    // 平板双栏左栏（白皮书 06 §6.9 套 C）：行外 8dp、选中 24dp 圆角 + surfaceVariant。
    val containerModifier = if (twoPane) {
        modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.Small)
            .clip(RoundedCornerShape(DesignTokens.Radius.SettingsSelected))
            .background(if (selected) UiTheme.colorScheme.surfaceVariant else Color.Transparent)
    } else {
        modifier.fillMaxWidth()
    }
    val contentColor = if (twoPane && selected) {
        UiTheme.colorScheme.onSurfaceVariant
    } else {
        UiTheme.colorScheme.onSurface
    }
    Row(
        modifier = containerModifier
            .then(clickModifier)
            .heightIn(min = DesignTokens.Sizes.SettingsItemMinHeight)
            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Medium)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            DecorativeActionIcon(
                icon = icon,
                tint = if (enabled) UiTheme.colorScheme.primary else UiTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(DesignTokens.Spacing.Medium))
        }
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = title,
                color = when {
                    !enabled -> UiTheme.colorScheme.onSurface.copy(alpha = DesignTokens.Alpha.Disabled)
                    danger -> UiTheme.colorScheme.error
                    else -> contentColor
                },
                style = UiTheme.typography.titleLarge.copy(fontSize = 16.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                AppText(
                    text = subtitle,
                    color = UiTheme.colorScheme.onSurfaceVariant.copy(alpha = DesignTokens.Alpha.Secondary),
                    style = UiTheme.typography.bodySmall,
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
            AppSwitch(
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
    val stackValue = LocalDensity.current.fontScale >= 1.5f
    SettingsRow(
        title = title,
        modifier = modifier,
        icon = icon,
        subtitle = if (stackValue) listOfNotNull(subtitle, currentValue).joinToString("\n") else subtitle,
        onClick = onClick,
        enabled = enabled,
        testTag = testTag,
        trailing = {
            if (!stackValue) {
                AppText(
                    text = currentValue,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                    style = UiTheme.typography.bodyMedium,
                )
                Spacer(Modifier.width(DesignTokens.Spacing.ExtraSmall))
            }
            DecorativeActionIcon(icon = ActionIcon.Next)
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
            AppText(
                text = title,
                modifier = Modifier.weight(1f),
                color = UiTheme.colorScheme.onSurface,
                style = UiTheme.typography.titleLarge.copy(fontSize = 16.sp),
            )
            AppText(
                text = valueText,
                color = UiTheme.colorScheme.onSurfaceVariant,
                style = UiTheme.typography.bodyMedium,
            )
        }
        AppSlider(
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
    AppText(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = DesignTokens.Spacing.Large,
                vertical = DesignTokens.Spacing.ExtraLarge,
            ),
        color = UiTheme.colorScheme.onSurfaceVariant,
        style = UiTheme.typography.bodyMedium,
    )
}

/** 主题预览色：只取语义色板，不写死 hex；19 套主题循环映射到 6 个语义色。 */
@Composable
internal fun previewSwatchColor(ordinal: Int): Color = when (ordinal % 6) {
    0 -> UiTheme.colorScheme.primary
    1 -> UiTheme.colorScheme.secondary
    2 -> UiTheme.colorScheme.tertiary
    3 -> UiTheme.colorScheme.primaryContainer
    4 -> UiTheme.colorScheme.secondaryContainer
    else -> UiTheme.colorScheme.tertiaryContainer
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
                .height(DesignTokens.Sizes.ThemePreviewSwatch)
                .background(color, RoundedCornerShape(DesignTokens.Radius.Small)),
        )
        Spacer(Modifier.height(DesignTokens.Spacing.ExtraSmall))
        Box(
            Modifier
                .fillMaxWidth()
                .height(DesignTokens.Spacing.Medium)
                .background(
                    UiTheme.colorScheme.surfaceVariant,
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
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "settings-segment",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DesignTokens.Sizes.MinimumTouchTarget)
            .background(
                UiTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(DesignTokens.Radius.Medium),
            )
            .padding(DesignTokens.Spacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (item in items) {
            val isSelected = item == selected
            val bg = if (isSelected) UiTheme.colorScheme.primaryContainer else Color.Transparent
            val fg = if (isSelected) {
                UiTheme.colorScheme.onPrimaryContainer
            } else {
                UiTheme.colorScheme.onSurfaceVariant
            }
            AppText(
                text = labelOf(item),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = DesignTokens.Sizes.MinimumTouchTarget)
                    .clickable { onSelect(item) }
                    .background(bg, shape = RoundedCornerShape(DesignTokens.Radius.Small))
                    .padding(horizontal = DesignTokens.Spacing.Small, vertical = DesignTokens.Spacing.Small)
                    .testTag("$testTagPrefix-$item"),
                color = fg,
                style = UiTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 套 B（白皮书 06 §6.9 `SettingsItems`）：24dp/10dp 内边距、控件与文字间距 24dp、`bodyMedium`。
 *
 * 用于旧式列表条目（Checkbox/Radio/Slider/Select）；新的 Settings 子页仍用 [SettingsRow]（套 A）。
 */
@Composable
internal fun SettingsLegacyRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    control: (@Composable () -> Unit)? = null,
    testTag: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = DesignTokens.Spacing.Large, vertical = DesignTokens.Spacing.Row)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Large),
    ) {
        control?.invoke()
        AppText(
            text = label,
            modifier = Modifier.weight(1f),
            color = UiTheme.colorScheme.onSurface,
            style = UiTheme.typography.bodyMedium,
        )
    }
}

/**
 * 套 3 组头 `PreferenceGroupHeader`（白皮书 06 §6.10）：`secondary` 色、Normal 字重、
 * 上 14dp / 下 8dp、水平 16dp。Settings 子页用它；列表页仍用 `ListGroupHeader`。
 */
@Composable
internal fun PreferenceGroupHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    AppText(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = DesignTokens.Spacing.Medium,
                end = DesignTokens.Spacing.Medium,
                top = 14.dp,
                bottom = DesignTokens.Spacing.Small,
            ),
        color = UiTheme.colorScheme.secondary,
        style = UiTheme.typography.bodyMedium,
    )
}
