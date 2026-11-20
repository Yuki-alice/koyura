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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.DesignTokens

const val SettingsRootTestTag = "settings-root"
const val SettingsMasterPaneTestTag = "settings-master-pane"
const val SettingsDetailPaneTestTag = "settings-detail-pane"
const val SettingsSubpageRootTestTag = "settings-subpage-root"

private val SettingsTwoPaneMasterWidth = 320.dp

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
                    modifier = Modifier
                        .width(SettingsTwoPaneMasterWidth)
                        .fillMaxSize(),
                )
                SettingsDetailPane(
                    session = session,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
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
) {
    SettingsSubpageContent(
        session = session,
        subpage = subpage,
        modifier = modifier
            .fillMaxSize()
            .testTag(SettingsSubpageRootTestTag),
        onBack = onBack,
        showBackButton = true,
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
                    MaterialTheme.colorScheme.surfaceContainerLow
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .verticalScroll(rememberScrollState())
            .testTag(SettingsMasterPaneTestTag),
    ) {
        if (!isTwoPane && onBack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DesignTokens.Sizes.SettingsItemMinHeight)
                    .padding(horizontal = DesignTokens.Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIconButton(icon = ActionIcon.Back, label = "Back", onClick = onBack)
            }
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
                            Modifier.background(MaterialTheme.colorScheme.surfaceVariant, selectedShape)
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
                ActionIcon(
                    icon = page.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(DesignTokens.Spacing.Medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = page.label,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                    )
                    Text(
                        text = page.summary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DesignTokens.Alpha.Secondary),
                        style = MaterialTheme.typography.bodySmall,
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
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .testTag(SettingsDetailPaneTestTag),
    ) {
        SettingsSubpageContent(
            session = session,
            subpage = session.uiState.selectedSubpage,
            modifier = Modifier.fillMaxSize(),
            onBack = null,
            showBackButton = false,
        )
    }
}
