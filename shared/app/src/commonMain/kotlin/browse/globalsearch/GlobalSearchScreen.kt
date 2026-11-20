package app.koyura.app.browse.globalsearch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koyura.app.browse.AppCoverDecoder
import app.koyura.app.browse.LocalBrowseSession
import app.koyura.app.resources.Res
import app.koyura.app.resources.browse_clear_search
import app.koyura.app.resources.global_search_empty
import app.koyura.app.resources.global_search_failed
import app.koyura.app.resources.global_search_hint
import app.koyura.app.resources.global_search_prompt
import app.koyura.app.resources.global_search_result_count
import app.koyura.app.resources.global_search_retry
import app.koyura.app.resources.global_search_source_failed
import app.koyura.app.resources.global_search_title
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.Cover
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.LocalCoverDecoder
import app.koyura.designsystem.LocalCoverImageLoader
import app.koyura.designsystem.SearchField
import app.koyura.designsystem.components.AppCircularProgress
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.ReaderTopAppBar
import app.koyura.designsystem.components.UiTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal const val GlobalSearchRootTag = "global-search-root"
internal const val GlobalSearchQueryTag = "global-search-query"
internal const val GlobalSearchSubmitTag = "global-search-submit"
internal const val GlobalSearchListTag = "global-search-list"
internal const val GlobalSearchPromptTag = "global-search-prompt"
internal const val GlobalSearchEmptyTag = "global-search-empty"
internal const val GlobalSearchErrorTag = "global-search-error"

internal fun globalSearchGroupTag(sourceKey: String) = "global-search-group-$sourceKey"
internal fun globalSearchRetryTag(sourceKey: String) = "global-search-retry-$sourceKey"
internal fun globalSearchResultTag(sourceKey: String, workId: String) = "global-search-result-$sourceKey-$workId"

/**
 * 全局搜索页（书架空态与浏览顶栏入口 → 独立子路由）。
 *
 * 搜索框照 `SearchField`，结果按源分组：组头为源名 + 状态（加载中/结果数/失败原因 +
 * 重试该源），作品行照 `BrowseRow` 的封面 + 标题 + 副标题结构。点行进既有作品详情路由。
 */
@Composable
fun GlobalSearchScreen(
    session: GlobalSearchSession,
    onBack: () -> Unit,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val browseSession = LocalBrowseSession.current

    CompositionLocalProvider(
        LocalCoverImageLoader provides browseSession?.coverLoader(),
        LocalCoverDecoder provides AppCoverDecoder,
    ) {
        Column(modifier = modifier.fillMaxSize().testTag(GlobalSearchRootTag)) {
            ReaderTopAppBar(
                title = { AppText(stringResource(Res.string.global_search_title)) },
                navigationIcon = {
                    ActionIconButton(
                        icon = ActionIcon.Back,
                        label = stringResource(Res.string.global_search_title),
                        onClick = onBack,
                    )
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    value = session.query,
                    onValueChange = session::updateQuery,
                    placeholder = stringResource(Res.string.global_search_hint),
                    clearLabel = stringResource(Res.string.browse_clear_search),
                    modifier = Modifier.weight(1f).testTag(GlobalSearchQueryTag),
                )
                ActionIconButton(
                    icon = ActionIcon.Search,
                    label = stringResource(Res.string.global_search_hint),
                    onClick = { scope.launch { session.submit() } },
                    enabled = !session.searching && session.query.isNotBlank(),
                    modifier = Modifier.testTag(GlobalSearchSubmitTag),
                )
            }

            when {
                session.error != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(DesignTokens.Spacing.Large)
                        .testTag(GlobalSearchErrorTag),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
                ) {
                    AppText(
                        text = stringResource(Res.string.global_search_failed, session.error.orEmpty()),
                        color = UiTheme.colorScheme.error,
                        style = UiTheme.typography.bodyMedium,
                    )
                    AppTextButton(onClick = { scope.launch { session.submit() } }) {
                        AppText(stringResource(Res.string.global_search_retry))
                    }
                }
                !session.hasSearched -> AppText(
                    text = stringResource(Res.string.global_search_prompt),
                    style = UiTheme.typography.bodyMedium,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(DesignTokens.Spacing.Large).testTag(GlobalSearchPromptTag),
                )
                !session.searching && session.resultCount == 0 &&
                    session.groups.all { it.state is GlobalSearchSourceState.Failed } -> AppText(
                    text = stringResource(Res.string.global_search_empty),
                    style = UiTheme.typography.bodyMedium,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(DesignTokens.Spacing.Large).testTag(GlobalSearchEmptyTag),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize().testTag(GlobalSearchListTag)) {
                    session.groups.forEach { group ->
                        item(key = "header-${group.sourceKey}") {
                            GlobalSearchGroupHeader(
                                group = group,
                                onRetry = { scope.launch { session.retry(group.sourceKey) } },
                            )
                        }
                        items(group.works.size, key = { index ->
                            "row-${group.sourceKey}-${group.works[index].id}"
                        }) { index ->
                            val work = group.works[index]
                            GlobalSearchResultRow(
                                sourceKey = group.sourceKey,
                                work = work,
                                onOpen = { onOpenWork(group.sourceKey, work.id, work.title) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 组头：源名 + 状态；失败态给「重试该源」。 */
@Composable
private fun GlobalSearchGroupHeader(
    group: GlobalSearchGroup,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.testTag(globalSearchGroupTag(group.sourceKey))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
        ) {
            AppText(
                text = group.sourceName,
                style = UiTheme.typography.titleSmall,
                color = UiTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            when (val state = group.state) {
                GlobalSearchSourceState.Loading -> AppCircularProgress(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                is GlobalSearchSourceState.Ready -> AppText(
                    text = stringResource(Res.string.global_search_result_count, state.works.size),
                    style = UiTheme.typography.bodySmall,
                    color = UiTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = DesignTokens.Alpha.Secondary,
                    ),
                )
                is GlobalSearchSourceState.Failed -> {
                    AppText(
                        text = stringResource(Res.string.global_search_source_failed, state.message),
                        style = UiTheme.typography.bodySmall,
                        color = UiTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextButton(
                        onClick = onRetry,
                        modifier = Modifier.testTag(globalSearchRetryTag(group.sourceKey)),
                    ) {
                        AppText(stringResource(Res.string.global_search_retry))
                    }
                }
            }
        }
    }
}

/** 作品行：共享封面 48×72 + 标题 + 副标题（照 `BrowseRow` 的阅读节奏）。 */
@Composable
private fun GlobalSearchResultRow(
    sourceKey: String,
    work: app.koyura.app.browse.WorkSummary,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag(globalSearchResultTag(sourceKey, work.id))
            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Medium),
    ) {
        Cover(
            sourceKey = sourceKey,
            coverUrl = work.cover,
            label = work.title,
            modifier = Modifier.size(width = 48.dp, height = 72.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = work.title,
                style = UiTheme.typography.bodyMedium,
                color = UiTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (work.subtitle.isNotBlank()) {
                AppText(
                    text = work.subtitle,
                    style = UiTheme.typography.bodySmall,
                    color = UiTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = DesignTokens.Alpha.Secondary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
