package app.koyura.app.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppSwitch
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.UiTheme

/**
 * Browse 的 Feed / Migrate 两个新增一级 tab（白皮书 04 §4.3，原版 SourcesScreen 四 tab）。
 *
 * 本轮只补 UI 骨架与 18+ 开关的页内 Fake 状态，数据来源与迁移行为留给后续轮次；
 * 不引入仓储、不改 BrowseSession 的行为契约。
 */
@Composable
internal fun FeedPanePlaceholder(modifier: Modifier = Modifier) {
    var adultOnly by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(DesignTokens.Spacing.Medium)
            .testTag(BrowseFeedPaneTag),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
    ) {
        AppText(
            text = "Feed",
            style = UiTheme.typography.titleLarge,
            color = UiTheme.colorScheme.onSurface,
        )
        AppText(
            text = "订阅源的最新内容将在这里展示。",
            style = UiTheme.typography.bodyMedium,
            color = UiTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppText(
                text = "显示 18+ 内容",
                style = UiTheme.typography.bodyMedium,
                color = UiTheme.colorScheme.onSurface,
            )
            AppSwitch(
                checked = adultOnly,
                onCheckedChange = { adultOnly = it },
                modifier = Modifier.testTag(BrowseAdultOnlySwitchTag),
            )
        }
    }
}

@Composable
internal fun MigratePanePlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(DesignTokens.Spacing.Medium)
            .testTag(BrowseMigratePaneTag),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
    ) {
        AppText(
            text = "Migrate",
            style = UiTheme.typography.titleLarge,
            color = UiTheme.colorScheme.onSurface,
        )
        AppText(
            text = "跨源迁移入口，数据接线在后续轮次完成。",
            style = UiTheme.typography.bodyMedium,
            color = UiTheme.colorScheme.onSurfaceVariant,
        )
    }
}

const val BrowseFeedPaneTag = "browse-feed-pane"
const val BrowseMigratePaneTag = "browse-migrate-pane"
const val BrowseAdultOnlySwitchTag = "browse-adult-only-switch"
