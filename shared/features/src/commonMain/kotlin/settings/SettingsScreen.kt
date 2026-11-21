package app.koyura.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import app.koyura.features.resources.Res
import app.koyura.features.resources.settings_back
import app.koyura.features.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.DecorativeActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.UiTheme

const val SettingsRootTestTag = "settings-root"
const val SettingsMasterPaneTestTag = "settings-master-pane"
const val SettingsDetailPaneTestTag = "settings-detail-pane"
const val SettingsSubpageRootTestTag = "settings-subpage-root"

private val SettingsTwoPaneMasterWidth = DesignTokens.Breakpoints.SettingsMasterWidth

/**
 * 窗口是否为双栏：沿用导航轨的「最小边」思路，旋转时不抖动；阈值取自 DesignTokens，
 * 不依赖 app 模块（features 不能反向依赖 app）。
 */
internal fun usesSettingsTwoPane(width: Dp, height: Dp): Boolean {
    val portrait = height >= width
    val smallestDp = minOf(width.value, height.value)
    val thresholdDp = if (portrait) {
        DesignTokens.Breakpoints.TabletAutomaticPortraitDp
    } else {
        DesignTokens.Breakpoints.TabletAutomaticLandscapeDp
    }
    return smallestDp >= thresholdDp
}

/**
 * 设置主页：手机为「列表 → push 子页」，平板为左列表 + 右详情的 master-detail。
 *
 * 手机点击行时先更新 session 的选中项，再调用 [onOpenSubpage] 交给路由层 push；
 * 平板点击只更新选中项，不调用 [onOpenSubpage]（右栏直接刷新）。返回键由调用方接线
 * [onBack]（手机子页可传 [SettingsSubpageScreen] 的 onBack）。
 */
@Composable
fun SettingsScreen(
    session: SettingsSession,
    modifier: Modifier = Modifier,
    onOpenSubpage: ((SettingsSubpage) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onOpenOnboarding: (() -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(SettingsRootTestTag),
    ) {
        if (usesSettingsTwoPane(maxWidth, maxHeight)) {
            Row(modifier = Modifier.fillMaxSize()) {
                SettingsMasterPane(
                    session = session,
                    isTwoPane = true,
                    onOpenSubpage = null,
                    onBack = onBack,
                    modifier = Modifier
                        .width(SettingsTwoPaneMasterWidth)
                        .fillMaxSize(),
                )
                SettingsDetailPane(
                    session = session,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    onOpenOnboarding = onOpenOnboarding,
                )
            }
        } else {
            SettingsMasterPane(
                session = session,
                isTwoPane = false,
                onOpenSubpage = onOpenSubpage,
                modifier = Modifier.fillMaxSize(),
                onBack = onBack,
            )
        }
    }
}

/** 手机 push 出来的设置子页：由路由层作为独立 destination 承载。 */
@Composable
fun SettingsSubpageScreen(
    session: SettingsSession,
    subpage: SettingsSubpage,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenOnboarding: (() -> Unit)? = null,
) {
    SettingsSubpageContent(
        session = session,
        subpage = subpage,
        modifier = modifier
            .fillMaxSize()
            .testTag(SettingsSubpageRootTestTag),
        onBack = onBack,
        showBackButton = true,
        onOpenOnboarding = onOpenOnboarding,
    )
}

@Composable
private fun SettingsMasterPane(
    session: SettingsSession,
    isTwoPane: Boolean,
    onOpenSubpage: ((SettingsSubpage) -> Unit)?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .background(
                if (isTwoPane) {
                    UiTheme.colorScheme.surfaceContainerLow
                } else {
                    UiTheme.colorScheme.surface
                },
            )
            .verticalScroll(rememberScrollState())
            .testTag(SettingsMasterPaneTestTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DesignTokens.Sizes.SettingsItemMinHeight)
                .padding(horizontal = DesignTokens.Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                ActionIconButton(icon = ActionIcon.Back, label = stringResource(Res.string.settings_back), onClick = onBack)
            }
            AppText(
                text = stringResource(Res.string.settings_title),
                style = UiTheme.typography.titleLarge,
                color = UiTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = DesignTokens.Spacing.Small),
            )
        }
        for (page in SettingsSubpage.entries) {
            val selected = isTwoPane && session.uiState.selectedSubpage == page
            val selectedShape = RoundedCornerShape(DesignTokens.Radius.SettingsSelected)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isTwoPane) DesignTokens.Spacing.Small else 0.dp,
                        vertical = if (isTwoPane) DesignTokens.Spacing.ExtraSmall else 0.dp,
                    )
                    .heightIn(min = DesignTokens.Sizes.SettingsItemMinHeight)
                    .then(
                        if (selected) {
                            Modifier.background(UiTheme.colorScheme.surfaceVariant, selectedShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable {
                        session.selectSubpage(page)
                        if (!isTwoPane) onOpenSubpage?.invoke(page)
                    }
                    .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small)
                    .testTag("$SettingsMasterPaneTestTag-${page.name}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DecorativeActionIcon(
                icon = page.icon,
                    tint = UiTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(DesignTokens.Spacing.Medium))
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = page.displayLabel(),
                        color = UiTheme.colorScheme.onSurface,
                        style = UiTheme.typography.titleLarge.copy(fontSize = 16.sp),
                    )
                    AppText(
                        text = page.displaySummary(),
                        color = UiTheme.colorScheme.onSurfaceVariant.copy(alpha = DesignTokens.Alpha.Secondary),
                        style = UiTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsDetailPane(
    session: SettingsSession,
    modifier: Modifier = Modifier,
    onOpenOnboarding: (() -> Unit)? = null,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(UiTheme.colorScheme.surface)
            .testTag(SettingsDetailPaneTestTag),
    ) {
        SettingsSubpageContent(
            session = session,
            subpage = session.uiState.selectedSubpage,
            modifier = Modifier.fillMaxSize(),
            onBack = null,
            showBackButton = false,
            onOpenOnboarding = onOpenOnboarding,
        )
    }
}
