package app.koyura.app.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.koyura.app.LocalAppSettingsSession
import app.koyura.app.resources.Res
import app.koyura.app.resources.browse_feed_adult
import app.koyura.app.resources.browse_feed_empty
import app.koyura.app.resources.browse_feed_title
import app.koyura.app.resources.browse_migrate_empty
import app.koyura.app.resources.browse_migrate_open_source
import app.koyura.app.resources.browse_migrate_title
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppSwitch
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.EmptyScreen
import app.koyura.designsystem.components.UiTheme
import app.koyura.features.library.LibraryEntry
import app.koyura.features.library.LocalLibraryRepository
import app.koyura.features.updates.LocalUpdatesRepository
import app.koyura.features.updates.UpdateWork
import org.jetbrains.compose.resources.stringResource

/**
 * Browse 的 Feed tab（白皮书 04 §4.3）：读真实的更新 feed，按最近更新列出作品。
 *
 * 没有独立 feed 服务时，更新 feed 就是"最近有新区章的书架作品"，比再造一个假数据源诚实。
 * 18+ 开关写进全局设置（不再是页内 remember），与其他页面共享同一个偏好。
 */
@Composable
internal fun FeedPane(
    modifier: Modifier = Modifier,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit = { _, _, _ -> },
) {
    val repository = LocalUpdatesRepository.current
    val updates by produceState<List<UpdateWork>>(emptyList(), repository) {
        value = runCatching { repository?.loadUpdates() }.getOrNull().orEmpty()
    }
    val settings = LocalAppSettingsSession.current
    val adult = settings?.uiState?.preferences?.showAdultSources ?: false
    val adultLabel = stringResource(Res.string.browse_feed_adult)
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(BrowseFeedPaneTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AppText(
                text = stringResource(Res.string.browse_feed_title),
                style = UiTheme.typography.titleLarge,
                color = UiTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = adultLabel,
                    style = UiTheme.typography.bodyMedium,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = DesignTokens.Spacing.Small),
                )
                AppSwitch(
                    checked = adult,
                    onCheckedChange = { value -> settings?.update { it.copy(showAdultSources = value) } },
                    modifier = Modifier
                        .testTag(BrowseAdultOnlySwitchTag)
                        .semantics { contentDescription = adultLabel },
                )
            }
        }
        if (updates.isEmpty()) {
            EmptyScreen(
                emoji = "(・∀・)",
                message = stringResource(Res.string.browse_feed_empty),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(updates, key = { it.sourceKey + "/" + it.workId }) { work ->
                    FeedRow(work, onClick = { onOpenWork(work.sourceKey, work.workId, work.title) })
                }
            }
        }
    }
}

@Composable
private fun FeedRow(work: UpdateWork, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Regular)
            .testTag("browse-feed-${work.sourceKey}-${work.workId}"),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Tight),
    ) {
        AppText(
            text = work.title.ifEmpty { work.workId },
            style = UiTheme.typography.bodyLarge,
            maxLines = 1,
        )
        val latest = work.chapters.firstOrNull()?.title.orEmpty()
        if (latest.isNotEmpty()) {
            AppText(
                text = latest,
                style = UiTheme.typography.bodySmall,
                color = UiTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Browse 的 Migrate tab：列出书架作品，并给出「在源中搜索」入口。
 *
 * 跨源自动迁移在当前契约里恒不可用（`LibraryRepository.migrationSupported = false`），
 * 所以这里不伪造迁移结果，只提供真实可做的下一步：打开作品所在源继续查找。
 */
@Composable
internal fun MigratePane(
    modifier: Modifier = Modifier,
    onOpenSource: (sourceKey: String, sourceName: String) -> Unit = { _, _ -> },
) {
    val repository = LocalLibraryRepository.current
    val entries by produceState<List<LibraryEntry>>(emptyList(), repository) {
        value = runCatching { repository?.load()?.entries }.getOrNull().orEmpty()
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(BrowseMigratePaneTag),
    ) {
        AppText(
            text = stringResource(Res.string.browse_migrate_title),
            style = UiTheme.typography.titleLarge,
            color = UiTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small),
        )
        if (entries.isEmpty()) {
            EmptyScreen(
                emoji = "(・∀・)",
                message = stringResource(Res.string.browse_migrate_empty),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.sourceKey + "/" + it.workId }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            AppText(
                                text = entry.title.ifEmpty { entry.workId },
                                style = UiTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            AppText(
                                text = entry.sourceName.ifEmpty { entry.sourceKey },
                                style = UiTheme.typography.bodySmall,
                                color = UiTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { onOpenSource(entry.sourceKey, entry.sourceName) },
                        ) {
                            AppText(stringResource(Res.string.browse_migrate_open_source))
                        }
                    }
                }
            }
        }
    }
}

const val BrowseFeedPaneTag = "browse-feed-pane"
const val BrowseMigratePaneTag = "browse-migrate-pane"
const val BrowseAdultOnlySwitchTag = "browse-adult-only-switch"
