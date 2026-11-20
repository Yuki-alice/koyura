package app.koyura.features.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.BrandPlaceholder
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppDivider
import app.koyura.designsystem.components.AppSwitch
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.UiTheme
import app.koyura.features.resources.Res
import app.koyura.features.resources.more_about
import app.koyura.features.resources.more_batch_add
import app.koyura.features.resources.more_categories
import app.koyura.features.resources.more_data_storage
import app.koyura.features.resources.more_download_queue
import app.koyura.features.resources.more_downloaded_only
import app.koyura.features.resources.more_downloaded_only_subtitle
import app.koyura.features.resources.more_help
import app.koyura.features.resources.more_incognito
import app.koyura.features.resources.more_incognito_subtitle
import app.koyura.features.resources.more_settings
import app.koyura.features.resources.more_sponsor
import app.koyura.features.resources.more_statistics
import app.koyura.features.resources.more_update_errors
import org.jetbrains.compose.resources.stringResource

const val MoreRootTestTag = "more-root"
const val MoreSponsorTag = "more-sponsor"

fun moreRowTag(key: String) = "more-row-$key"
fun moreSwitchTag(key: String) = "more-switch-$key"

/**
 * 更多页：设置 + 工具入口列表（白皮书 07 §7.8，行结构见 MoreScreen.kt）。
 *
 * 顶部 64dp 品牌区（上下 56dp 留白）+ 分隔线；普通行 56dp 最小高、
 * 图标 primary、标题 16sp；开关行同式 + M3 Switch；底部赞助按钮
 * 2dp primary 边框 small 圆角居中（Code: Sponsor）。
 *
 * 下载队列、分类、统计与设置是真实页面；更新错误、数据与存储、批量添加、
 * 关于、帮助接到统一工具占位页，保证入口可点与返回栈正确；两个开关为真实页内状态。
 */
@Composable
fun MoreScreen(
    session: MoreSession,
    modifier: Modifier = Modifier,
    onOpenDownloadQueue: (() -> Unit)? = null,
    onOpenCategories: (() -> Unit)? = null,
    onOpenStatistics: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenUtility: ((String) -> Unit)? = null,
) {
    val utilityHandler = onOpenUtility
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag(MoreRootTestTag),
    ) {
        MoreLogoHeader()
        MoreSwitchRow(
            key = "downloaded-only",
            icon = ActionIcon.CloudOff,
            title = stringResource(Res.string.more_downloaded_only),
            subtitle = stringResource(Res.string.more_downloaded_only_subtitle),
            checked = session.downloadedOnly,
            onCheckedChange = session::updateDownloadedOnly,
        )
        MoreSwitchRow(
            key = "incognito",
            icon = ActionIcon.Incognito,
            title = stringResource(Res.string.more_incognito),
            subtitle = stringResource(Res.string.more_incognito_subtitle),
            checked = session.incognito,
            onCheckedChange = session::updateIncognito,
        )
        AppDivider()
        // 下载队列本轮落地为真实页面；其余目标页未落地，入口保持非交互随轮次启用。
        MoreNavRow(
            key = "download-queue",
            icon = ActionIcon.Download,
            title = stringResource(Res.string.more_download_queue),
            onClick = onOpenDownloadQueue,
        )
        MoreNavRow(
            key = "categories",
            icon = ActionIcon.Categories,
            title = stringResource(Res.string.more_categories),
            onClick = onOpenCategories,
        )
        MoreNavRow(
            key = "statistics",
            icon = ActionIcon.Statistics,
            title = stringResource(Res.string.more_statistics),
            onClick = onOpenStatistics,
        )
        MoreNavRow(
            key = "update-errors",
            icon = ActionIcon.UpdateErrors,
            title = stringResource(Res.string.more_update_errors),
            onClick = utilityHandler?.let { cb -> { cb("update-errors") } },
        )
        MoreNavRow(
            key = "data-storage",
            icon = ActionIcon.Storage,
            title = stringResource(Res.string.more_data_storage),
            onClick = utilityHandler?.let { cb -> { cb("data-storage") } },
        )
        MoreNavRow(
            key = "batch-add",
            icon = ActionIcon.BatchAdd,
            title = stringResource(Res.string.more_batch_add),
            onClick = utilityHandler?.let { cb -> { cb("batch-add") } },
        )
        AppDivider()
        MoreNavRow(
            key = "settings",
            icon = ActionIcon.Settings,
            title = stringResource(Res.string.more_settings),
            onClick = onOpenSettings,
        )
        MoreNavRow(
            key = "about",
            icon = ActionIcon.Info,
            title = stringResource(Res.string.more_about),
            onClick = utilityHandler?.let { cb -> { cb("about") } },
        )
        MoreNavRow(
            key = "help",
            icon = ActionIcon.Help,
            title = stringResource(Res.string.more_help),
            onClick = utilityHandler?.let { cb -> { cb("help") } },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = DesignTokens.Spacing.Medium),
            horizontalArrangement = Arrangement.Center,
        ) {
            AppTextButton(
                onClick = {},
                enabled = false,
                border = BorderStroke(
                    // 赞助按钮 2dp primary 边框 small 圆角（Code: Sponsor，07 §7.8）。
                    width = 2.dp,
                    color = UiTheme.colorScheme.primary,
                ),
                shape = UiTheme.shapes.small,
                modifier = Modifier.testTag(MoreSponsorTag),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionIcon(
                        icon = ActionIcon.Favorite,
                        contentDescription = null,
                        tint = UiTheme.colorScheme.primary,
                    )
                    AppText(
                        text = stringResource(Res.string.more_sponsor),
                        color = UiTheme.colorScheme.primary,
                        style = UiTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = DesignTokens.Spacing.ExtraSmall),
                    )
                }
            }
        }
    }
}

/** 品牌区：64dp 图标 onSurface tint，上下 56dp 留白，底部分隔线（Code: LogoHeader）。 */
@Composable
private fun MoreLogoHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 品牌占位：library_books 官方字形（64dp + onSurface tint），待品牌视觉定稿后替换。
        BrandPlaceholder(
            modifier = Modifier
                // 上下 56dp 留白、图标 64dp（Code: LogoHeader.kt:27-29）。
                .padding(vertical = 56.dp)
                .size(64.dp),
        )
        AppDivider()
    }
}

/** 开关行：56dp 最小高、16dp 双边距、图标 primary、标题 16sp、副标题 12sp/0.78。 */
@Composable
private fun MoreSwitchRow(
    key: String,
    icon: ActionIcon,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DesignTokens.Sizes.SettingsItemMinHeight)
            .padding(
                horizontal = DesignTokens.Spacing.Medium,
                vertical = DesignTokens.Spacing.Medium,
            )
            .testTag(moreRowTag(key)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(
            icon = icon,
            contentDescription = null,
            tint = UiTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = DesignTokens.Spacing.Medium),
        ) {
            AppText(
                // 设置项标题：titleLarge 字阶强制 16sp（白皮书 02 §2.6 被改写字号表）。
                text = title,
                style = UiTheme.typography.titleLarge.copy(fontSize = 16.sp),
                color = UiTheme.colorScheme.onSurface,
            )
            AppText(
                text = subtitle,
                style = UiTheme.typography.bodySmall,
                color = UiTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = DesignTokens.Alpha.Secondary,
                ),
            )
        }
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(moreSwitchTag(key)),
        )
    }
}

/** 导航行：与开关行同规格，无副标题；有目标页时可点，无回调时保持非交互。 */
@Composable
private fun MoreNavRow(
    key: String,
    icon: ActionIcon,
    title: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = DesignTokens.Sizes.SettingsItemMinHeight)
            .padding(
                horizontal = DesignTokens.Spacing.Medium,
                vertical = DesignTokens.Spacing.Medium,
            )
            .testTag(moreRowTag(key)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(
            icon = icon,
            contentDescription = null,
            tint = UiTheme.colorScheme.primary,
        )
        AppText(
            text = title,
            style = UiTheme.typography.titleLarge.copy(fontSize = 16.sp),
            color = UiTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = DesignTokens.Spacing.Medium),
        )
    }
}
