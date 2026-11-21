package app.koyura.designsystem.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.DesignTokens

/**
 * 顶栏三态（白皮书 06 §6.1 / 04 §4.6）。
 *
 * - [RESTING]：透明/表面色容器，标题 `titleLarge` 22sp 单行，副标题 `bodyMedium` 跑马灯
 *   （`repeatDelayMillis = 2000`）。
 * - [SEARCH]：标题换成 18sp Normal 的 `BasicTextField`，**无容器、无边框、无下划线**；
 *   左侧 ✕ 清空并退出搜索。
 * - [ACTION]：多选/动作模式，容器抬到 `surfaceColorAtElevation(3.dp)`，左侧 ✕、标题为选中
 *   计数，右侧全选/反选。
 *
 * 详情页的双 alpha 变体仍由 [ReaderTopAppBar] 承担，两者职责不重叠。
 */
enum class KoyuraAppBarMode { RESTING, SEARCH, ACTION }

@Composable
fun KoyuraAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    mode: KoyuraAppBarMode = KoyuraAppBarMode.RESTING,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    searchPlaceholder: String = "",
    onSearchSubmit: () -> Unit = {},
    onSearchDismiss: () -> Unit = {},
    actionCount: Int = 0,
    onActionDismiss: () -> Unit = {},
    onSelectAll: (() -> Unit)? = null,
    onInvertSelection: (() -> Unit)? = null,
    selectAllLabel: String = "",
    invertSelectionLabel: String = "",
    onBack: (() -> Unit)? = null,
    navigationLabel: String = "",
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
) {
    val container = if (mode == KoyuraAppBarMode.ACTION) {
        MaterialTheme.colorScheme.surfaceColorAtElevation(DesignTokens.Elevation.AppBarScrolled)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(DesignTokens.Elevation.AppBarResting)
    }
    Surface(modifier = modifier.fillMaxWidth(), color = container) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .heightIn(min = DesignTokens.Sizes.TopBarHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
        ) {
            when (mode) {
                KoyuraAppBarMode.SEARCH -> {
                    ActionIconButton(
                        icon = ActionIcon.Close,
                        label = navigationLabel,
                        onClick = onSearchDismiss,
                        modifier = Modifier.padding(start = DesignTokens.Spacing.Small),
                    )
                    AppBarSearchField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = searchPlaceholder,
                        onSubmit = onSearchSubmit,
                        modifier = Modifier.weight(1f).padding(end = DesignTokens.Spacing.Medium),
                    )
                }

                KoyuraAppBarMode.ACTION -> {
                    ActionIconButton(
                        icon = ActionIcon.Close,
                        label = navigationLabel,
                        onClick = onActionDismiss,
                        modifier = Modifier.padding(start = DesignTokens.Spacing.Small),
                    )
                    Text(
                        text = actionCount.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (onSelectAll != null) {
                        ActionIconButton(
                            icon = ActionIcon.SelectAll,
                            label = selectAllLabel,
                            onClick = onSelectAll,
                        )
                    }
                    if (onInvertSelection != null) {
                        ActionIconButton(
                            icon = ActionIcon.InvertSelection,
                            label = invertSelectionLabel,
                            onClick = onInvertSelection,
                        )
                    }
                    Row(
                        modifier = Modifier.padding(end = DesignTokens.Spacing.ExtraSmall),
                        content = actions,
                    )
                }

                KoyuraAppBarMode.RESTING -> {
                    if (onBack != null) {
                        ActionIconButton(
                            icon = ActionIcon.Back,
                            label = navigationLabel,
                            onClick = onBack,
                            modifier = Modifier.padding(start = DesignTokens.Spacing.ExtraSmall),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (onBack == null) DesignTokens.Spacing.Medium else DesignTokens.Spacing.ExtraSmall),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(repeatDelayMillis = 2000),
                            )
                        }
                    }
                    Row(content = actions)
                }
            }
        }
    }
}

/**
 * 搜索态的裸输入框：18sp Normal、无容器。
 *
 * 白皮书 06 §6.1 的 `container = {}` 等价物——不用 `TextField`，直接画光标与占位文本，
 * 这样 AppBar 上不会出现胶囊底或下划线。
 */
@Composable
private fun AppBarSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val textStyle = MaterialTheme.typography.titleMedium.copy(
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
    )
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = textStyle,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = DesignTokens.Alpha.Secondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    }
}

/** 静息态只读标题（供不需要动作槽的页面复用）。 */
@Composable
fun KoyuraTopBarTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.basicMarquee(repeatDelayMillis = 2000),
            )
        }
    }
}

/** 动作模式容器色，供页面自绘顶栏时复用（3dp tonal）。 */
@Composable
fun actionModeContainerColor() = MaterialTheme.colorScheme.surfaceColorAtElevation(
    DesignTokens.Elevation.AppBarScrolled,
)
