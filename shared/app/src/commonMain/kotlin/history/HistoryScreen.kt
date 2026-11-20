@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package app.koyura.app.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.koyura.app.browse.AppCoverDecoder
import app.koyura.app.resources.Res
import app.koyura.app.resources.browse_clear_search
import app.koyura.app.resources.browse_error_prefix
import app.koyura.app.resources.browse_retry
import app.koyura.app.resources.history_clear_all
import app.koyura.app.resources.history_clear_confirm
import app.koyura.app.resources.history_clear_confirm_body
import app.koyura.app.resources.history_clear_confirm_title
import app.koyura.app.resources.history_clear_selection
import app.koyura.app.resources.history_continue
import app.koyura.app.resources.history_delete
import app.koyura.app.resources.history_delete_selected_body
import app.koyura.app.resources.history_delete_selected_confirm
import app.koyura.app.resources.history_delete_selected_title
import app.koyura.app.resources.history_empty
import app.koyura.app.resources.history_favorite_add
import app.koyura.app.resources.history_favorite_remove
import app.koyura.app.resources.history_filter
import app.koyura.app.resources.history_filter_clear
import app.koyura.app.resources.history_filter_favorites
import app.koyura.app.resources.history_filter_sources
import app.koyura.app.resources.history_filter_title
import app.koyura.app.resources.history_invert_selection
import app.koyura.app.resources.history_no_results
import app.koyura.app.resources.history_row_actions
import app.koyura.app.resources.history_unread
import app.koyura.app.resources.history_page
import app.koyura.app.resources.history_search
import app.koyura.app.resources.history_select_all
import app.koyura.app.resources.history_selection_count
import app.koyura.app.resources.history_single_chapter
import app.koyura.app.resources.history_title
import app.koyura.app.resources.history_today
import app.koyura.app.resources.history_unknown_time
import app.koyura.app.resources.history_yesterday
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.Cover
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.EmptyScreen
import app.koyura.designsystem.LocalCoverDecoder
import app.koyura.designsystem.LocalCoverImageLoader
import app.koyura.designsystem.SearchField
import app.koyura.designsystem.components.AppAlertDialog
import app.koyura.designsystem.components.AppButton
import app.koyura.designsystem.components.AppCheckbox
import app.koyura.designsystem.components.AppCircularProgress
import app.koyura.designsystem.components.AppDivider
import app.koyura.designsystem.components.AppMenu
import app.koyura.designsystem.components.AppMenuItem
import app.koyura.designsystem.components.AppSurface
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.ReaderTopAppBar
import app.koyura.designsystem.components.UiTheme
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource

internal const val HistoryRootTestTag = "history-root"
internal const val HistorySearchToggleTag = "history-search-toggle"
internal const val HistorySearchTag = "history-search-query"
internal const val HistoryListTag = "history-list"
internal const val HistoryEmptyTag = "history-empty"
internal const val HistoryNoResultsTag = "history-no-results"
internal const val HistoryErrorTag = "history-error"
internal const val HistoryFilterTag = "history-filter"
internal const val HistoryFilterDialogTag = "history-filter-dialog"
internal const val HistoryClearAllTag = "history-clear-all"
internal const val HistoryClearConfirmTag = "history-clear-confirm"
internal const val HistorySelectionBarTag = "history-selection-bar"
internal const val HistorySelectionCountTag = "history-selection-count"
internal const val HistorySelectionActionsTag = "history-selection-actions"
internal const val HistorySelectionDeleteTag = "history-selection-delete"
internal const val HistorySelectionInvertTag = "history-selection-invert"
internal const val HistorySelectionClearTag = "history-selection-clear"
internal const val HistoryBottomInvertTag = "history-bottom-invert"
internal const val HistoryBottomClearTag = "history-bottom-clear"
internal const val HistorySelectAllTag = "history-select-all"
internal const val HistoryDeleteSelectedConfirmTag = "history-delete-selected-confirm"

internal fun historyRowTag(sourceKey: String, workId: String) = "history-row-$sourceKey-$workId"
internal fun historyRowTitleTag(sourceKey: String, workId: String) = "history-row-title-$sourceKey-$workId"
internal fun historyRowDetailTag(sourceKey: String, workId: String) = "history-row-detail-$sourceKey-$workId"
internal fun historyContinueTag(sourceKey: String, workId: String) = "history-continue-$sourceKey-$workId"
internal fun historyDeleteTag(sourceKey: String, workId: String) = "history-delete-$sourceKey-$workId"
internal fun historyFavoriteTag(sourceKey: String, workId: String) = "history-favorite-$sourceKey-$workId"
internal fun historyRowActionsTag(sourceKey: String, workId: String) = "history-row-actions-$sourceKey-$workId"
internal fun historyGroupTag(key: String) = "history-group-$key"

/**
 * Reading history: one row per (source, work), newest first, grouped by day.
 *
 * The list is rendered from SQLite only and survives tab switches, child pages and the bar/rail
 * width switch through the shell's saved-state holder. Deletes/favorites/filters apply on top
 * of the snapshot in the session; persistence for delete/favorite lands with the data-layer
 * store (see notes).
 */
@Composable
fun HistoryScreen(
    session: HistorySession,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    onContinue: (entry: HistoryEntry, chapterIds: List<String>, chapterTitles: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    hasUnread: (HistoryEntry) -> Boolean = { false },
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteSelected by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        session.refresh()
    }

    val filtered = session.visibleEntries().filter { it.matches(query) }
    val sections = filtered.groupedByDate(today())

    CompositionLocalProvider(
        LocalCoverImageLoader provides session.coverLoader(),
        LocalCoverDecoder provides AppCoverDecoder,
    ) {
        Column(modifier = modifier.fillMaxSize().testTag(HistoryRootTestTag)) {
            if (session.selection.isNotEmpty()) {
                HistorySelectionToolbar(
                    count = session.selection.size,
                    onClear = session::clearSelection,
                    onSelectAll = { session.selectAllVisible(filtered) },
                    onInvert = { session.invertSelection(filtered) },
                )
            } else {
            ReaderTopAppBar(
                title = { AppText(stringResource(Res.string.history_title)) },
                actions = {
                    ActionIconButton(
                        icon = if (searching) ActionIcon.Close else ActionIcon.Search,
                        label = stringResource(if (searching) Res.string.browse_clear_search else Res.string.history_search),
                        onClick = {
                            searching = !searching
                            if (!searching) query = ""
                        },
                        modifier = Modifier.testTag(HistorySearchToggleTag),
                    )
                    ActionIconButton(
                        icon = ActionIcon.Filter,
                        label = stringResource(Res.string.history_filter),
                        onClick = { showFilter = true },
                        modifier = Modifier.testTag(HistoryFilterTag),
                    )
                    ActionIconButton(
                        icon = ActionIcon.Checklist,
                        label = stringResource(Res.string.history_clear_all),
                        onClick = { confirmClear = true },
                        enabled = session.entries.isNotEmpty(),
                        modifier = Modifier.testTag(HistoryClearAllTag),
                    )
                },
            )
            }
            if (searching) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(Res.string.history_search),
                    clearLabel = stringResource(Res.string.browse_clear_search),
                    modifier = Modifier.fillMaxWidth()
                        .padding(
                            horizontal = DesignTokens.Spacing.Medium,
                            vertical = DesignTokens.Spacing.Small,
                        )
                        .testTag(HistorySearchTag),
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val load = session.loadState) {
                HistoryLoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppCircularProgress()
                }
                is HistoryLoadState.Failed -> Column(
                    modifier = Modifier.fillMaxSize().padding(DesignTokens.Spacing.Large).testTag(HistoryErrorTag),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Regular),
                ) {
                    AppText(
                        text = stringResource(Res.string.browse_error_prefix, load.message),
                        color = UiTheme.colorScheme.error,
                    )
                    AppButton(onClick = { scope.launch { session.refresh() } }) {
                        AppText(stringResource(Res.string.browse_retry))
                    }
                }
                HistoryLoadState.Ready -> {
                    session.errorMessage?.let { message ->
                        AppText(
                            text = message,
                            color = UiTheme.colorScheme.error,
                            style = UiTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small)
                                .testTag("history-action-error"),
                        )
                    }
                    if (session.entries.isEmpty()) {
                        EmptyState(stringResource(Res.string.history_empty), HistoryEmptyTag)
                    } else if (sections.isEmpty()) {
                        // 本地删除/清空后可见行为空且无查询与筛选时，显示空态而非无结果，
                        // 与刷新后（服务侧已空）一致。
                        if (query.isBlank() &&
                            session.filteredSourceKeys.isEmpty() &&
                            !session.showFavoritesOnly
                        ) {
                            EmptyState(stringResource(Res.string.history_empty), HistoryEmptyTag)
                        } else {
                            EmptyState(stringResource(Res.string.history_no_results), HistoryNoResultsTag)
                        }
                    } else {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().testTag(HistoryListTag),
                            contentPadding = PaddingValues(vertical = DesignTokens.Spacing.Small),
                        ) {
                            sections.forEach { section ->
                                val groupKey = section.date?.toString() ?: "unknown"
                                stickyHeader(key = "history-group-$groupKey") {
                                    HistoryGroupHeader(section.date, groupKey)
                                }
                                items(section.entries, key = { "history-${it.sourceKey}-${it.workId}" }) { entry ->
                                    HistoryRow(
                                        entry = entry,
                                        favorited = session.isFavorite(entry),
                                        hasUnread = hasUnread(entry),
                                        selecting = session.selection.isNotEmpty(),
                                        selected = session.isSelected(entry),
                                        onToggleSelect = { session.toggleSelect(entry) },
                                        onOpen = { onOpenWork(entry.sourceKey, entry.workId, entry.displayTitle) },
                                        onContinue = {
                                            scope.launch {
                                                session.clearError()
                                                try {
                                                    val (ids, titles) = session.resolveChapters(entry)
                                                    onContinue(entry, ids, titles)
                                                } catch (failure: Exception) {
                                                    session.reportError(failure.userMessage())
                                                }
                                            }
                                        },
                                        onDelete = { scope.launch { session.delete(entry) } },
                                        onToggleFavorite = { scope.launch { session.toggleFavorite(entry) } },
                                    )
                                    AppDivider()
                                }
                            }
                        }
                    }
                }
            }
            }
            if (session.selection.isNotEmpty()) {
                HistorySelectionActions(
                    onDelete = { confirmDeleteSelected = true },
                    onInvert = { session.invertSelection(filtered) },
                    onClear = session::clearSelection,
                )
            }
        }
    }

    if (showFilter) {
        HistoryFilterDialog(
            session = session,
            onDismiss = { showFilter = false },
        )
    }
    if (confirmDeleteSelected) {
        AppAlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            modifier = Modifier.testTag(HistoryDeleteSelectedConfirmTag),
            title = { AppText(stringResource(Res.string.history_delete_selected_title)) },
            text = {
                AppText(
                    stringResource(
                        Res.string.history_delete_selected_body,
                        session.selection.size,
                    ),
                )
            },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        confirmDeleteSelected = false
                        scope.launch { session.deleteSelected() }
                    },
                    modifier = Modifier.testTag("history-delete-selected-ok"),
                ) {
                    AppText(
                        text = stringResource(Res.string.history_delete_selected_confirm),
                        color = UiTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                AppTextButton(onClick = { confirmDeleteSelected = false }) {
                    AppText(stringResource(Res.string.history_filter_clear))
                }
            },
        )
    }
    if (confirmClear) {
        AppAlertDialog(
            onDismissRequest = { confirmClear = false },
            modifier = Modifier.testTag(HistoryClearConfirmTag),
            title = { AppText(stringResource(Res.string.history_clear_confirm_title)) },
            text = { AppText(stringResource(Res.string.history_clear_confirm_body)) },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        confirmClear = false
                        scope.launch { session.clearAll() }
                    },
                    modifier = Modifier.testTag("history-clear-confirm-ok"),
                ) { AppText(stringResource(Res.string.history_clear_confirm)) }
            },
            dismissButton = {
                AppTextButton(onClick = { confirmClear = false }) {
                    AppText(stringResource(Res.string.history_filter_clear))
                }
            },
        )
    }
}

private fun today(): kotlinx.datetime.LocalDate =
    Clock.System.todayIn(TimeZone.currentSystemDefault())

private fun daysBetween(today: kotlinx.datetime.LocalDate, date: kotlinx.datetime.LocalDate): Long =
    today.toEpochDays() - date.toEpochDays()
@Composable
private fun EmptyState(text: String, testTag: String) {
    EmptyScreen(emoji = "(˘･_･˘)", message = text, modifier = Modifier.testTag(testTag))
}

@Composable
private fun HistoryGroupHeader(date: kotlinx.datetime.LocalDate?, groupKey: String) {
    val now = remember { today() }
    val text = when (date) {
        null -> stringResource(Res.string.history_unknown_time)
        now -> stringResource(Res.string.history_today)
        else -> when (daysBetween(now, date)) {
            1L -> stringResource(Res.string.history_yesterday)
            else -> date.toString()
        }
    }
    // Komikku ListGroupHeader: semibold bodyMedium in onSurfaceVariant.
    Box(modifier = Modifier.testTag(historyGroupTag(groupKey))) {
        app.koyura.designsystem.ListGroupHeader(text = text)
    }
}

@Composable
private fun HistoryFilterDialog(
    session: HistorySession,
    onDismiss: () -> Unit,
) {
    // Local draft so toggling sources does not reshuffle the list behind the dialog.
    var draftSources by remember(session.entries) { mutableStateOf(session.filteredSourceKeys) }
    var draftFavorites by remember(session.showFavoritesOnly) { mutableStateOf(session.showFavoritesOnly) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(HistoryFilterDialogTag),
        title = { AppText(stringResource(Res.string.history_filter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall)) {
                AppText(
                    text = stringResource(Res.string.history_filter_sources),
                    style = UiTheme.typography.labelLarge,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                )
                session.availableSources().forEach { sourceKey ->
                    val checked = draftSources.isEmpty() || draftSources.contains(sourceKey)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                draftSources = if (draftSources.contains(sourceKey)) {
                                    // Removing the last explicit source means "all", same as empty.
                                    draftSources - sourceKey
                                } else if (draftSources.isEmpty()) {
                                    session.availableSources().toSet() - sourceKey
                                } else {
                                    draftSources + sourceKey
                                }
                            }
                            .testTag("history-filter-source-$sourceKey"),
                    ) {
                        AppCheckbox(
                            checked = checked,
                            onCheckedChange = null,
                        )
                        AppText(
                            text = sourceKey,
                            style = UiTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { draftFavorites = !draftFavorites }
                        .testTag("history-filter-favorites"),
                ) {
                    AppCheckbox(checked = draftFavorites, onCheckedChange = null)
                    AppText(
                        text = stringResource(Res.string.history_filter_favorites),
                        style = UiTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            AppTextButton(
                onClick = {
                    session.setSourceFilter(
                        if (draftSources.size == session.availableSources().size) emptySet() else draftSources,
                    )
                    session.setFavoritesOnly(draftFavorites)
                    onDismiss()
                },
                modifier = Modifier.testTag("history-filter-apply"),
            ) { AppText(stringResource(Res.string.history_filter)) }
        },
        dismissButton = {
            AppTextButton(
                onClick = {
                    session.clearFilters()
                    onDismiss()
                },
                modifier = Modifier.testTag("history-filter-reset"),
            ) { AppText(stringResource(Res.string.history_filter_clear)) }
        },
    )
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    favorited: Boolean,
    hasUnread: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val chapterLabel = entry.chapterTitle?.takeIf { it.isNotBlank() }
        ?: entry.chapterId
        ?: stringResource(Res.string.history_single_chapter)
    val readTime = remember(entry.readAtMillis) {
        entry.readAtMillis?.let { millis ->
            Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).time
                .toString().take(5)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                // 选择态下点击即切换勾选；平时点击打开作品、长按进入多选（照书架）。
                onClick = { if (selecting) onToggleSelect() else onOpen() },
                onLongClick = { if (!selecting) onToggleSelect() },
            )
            .testTag(historyRowTag(entry.sourceKey, entry.workId))
            .heightIn(min = DesignTokens.Sizes.HistoryItemHeight)
            .padding(horizontal = DesignTokens.Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            AppCheckbox(checked = selected, onCheckedChange = { onToggleSelect() })
        }
        Cover(
            sourceKey = entry.sourceKey,
            coverUrl = entry.cover,
            label = entry.displayTitle,
            // 原版历史行封面约 63×94，填满行高（白皮书 07 §7.6）。
            modifier = Modifier.size(width = DesignTokens.Sizes.HistoryCoverWidth, height = DesignTokens.Sizes.HistoryCoverHeight),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = DesignTokens.Spacing.Medium,
                    end = DesignTokens.Spacing.Small,
                ),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Tight),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
            ) {
                if (hasUnread) {
                    val label = stringResource(Res.string.history_unread)
                    Box(
                        Modifier.size(DesignTokens.Sizes.UnreadDot)
                            .background(UiTheme.colorScheme.secondary, CircleShape)
                            .semantics { contentDescription = label },
                    )
                }
                AppText(
                    text = entry.displayTitle,
                    color = UiTheme.colorScheme.onSurface,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = UiTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).testTag(historyRowTitleTag(entry.sourceKey, entry.workId)),
                )
            }
            AppText(
                text = "$chapterLabel · " + stringResource(Res.string.history_page, entry.pageIndex + 1),
                style = UiTheme.typography.bodySmall,
                color = UiTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = DesignTokens.Alpha.Secondary,
                ),
                modifier = Modifier.testTag(historyRowDetailTag(entry.sourceKey, entry.workId)),
            )
            if (readTime != null) {
                AppText(readTime, style = UiTheme.typography.bodySmall, color = UiTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 选择态只留勾选框：行级动作隐藏，避免与批量动作抢同一个点击区。
        if (!selecting) {
            ActionIconButton(
                icon = ActionIcon.Play,
                label = stringResource(Res.string.history_continue),
                onClick = onContinue,
                modifier = Modifier.testTag(historyContinueTag(entry.sourceKey, entry.workId)),
            )
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                ActionIconButton(
                    icon = ActionIcon.More,
                    label = stringResource(Res.string.history_row_actions),
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag(historyRowActionsTag(entry.sourceKey, entry.workId)),
                )
                AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    val favoriteLabel = stringResource(
                        if (favorited) Res.string.history_favorite_remove else Res.string.history_favorite_add,
                    )
                    AppMenuItem(
                        text = { AppText(favoriteLabel) },
                        onClick = {
                            menuOpen = false
                            onToggleFavorite()
                        },
                        modifier = Modifier.testTag(historyFavoriteTag(entry.sourceKey, entry.workId)),
                    )
                    AppMenuItem(
                        text = { AppText(stringResource(Res.string.history_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                        modifier = Modifier.testTag(historyDeleteTag(entry.sourceKey, entry.workId)),
                    )
                }
            }
        }
    }
}

/** 选择态顶栏：计数 + 关闭 + 全选/反选（结构照书架 `LibrarySelectionToolbar`）。 */
@Composable
private fun HistorySelectionToolbar(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
) {
    ReaderTopAppBar(
        title = {
            AppText(
                text = stringResource(Res.string.history_selection_count, count),
                modifier = Modifier.testTag(HistorySelectionCountTag),
            )
        },
        navigationIcon = {
            ActionIconButton(
                icon = ActionIcon.Close,
                label = stringResource(Res.string.history_clear_selection),
                onClick = onClear,
                modifier = Modifier.testTag(HistorySelectionClearTag),
            )
        },
        actions = {
            ActionIconButton(
                icon = ActionIcon.SelectAll,
                label = stringResource(Res.string.history_select_all),
                onClick = onSelectAll,
                modifier = Modifier.testTag(HistorySelectAllTag),
            )
            ActionIconButton(
                icon = ActionIcon.InvertSelection,
                label = stringResource(Res.string.history_invert_selection),
                onClick = onInvert,
                modifier = Modifier.testTag(HistorySelectionInvertTag),
            )
        },
        modifier = Modifier.testTag(HistorySelectionBarTag),
    )
}

/** 选择态底部动作条：删除 / 反选 / 清除（Surface + 图标按钮，结构照书架底栏）。 */
@Composable
private fun HistorySelectionActions(
    onDelete: () -> Unit,
    onInvert: () -> Unit,
    onClear: () -> Unit,
) {
    AppSurface(
        tonalElevation = DesignTokens.Elevation.AppBarScrolled,
        modifier = Modifier.fillMaxWidth().testTag(HistorySelectionActionsTag),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = DesignTokens.Spacing.Small, vertical = DesignTokens.Spacing.Small),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIconButton(
                icon = ActionIcon.Delete,
                label = stringResource(Res.string.history_delete),
                onClick = onDelete,
                modifier = Modifier.testTag(HistorySelectionDeleteTag),
                contentColor = UiTheme.colorScheme.error,
            )
            ActionIconButton(
                icon = ActionIcon.InvertSelection,
                label = stringResource(Res.string.history_invert_selection),
                onClick = onInvert,
                modifier = Modifier.testTag(HistoryBottomInvertTag),
            )
            ActionIconButton(
                icon = ActionIcon.Close,
                label = stringResource(Res.string.history_clear_selection),
                onClick = onClear,
                modifier = Modifier.testTag(HistoryBottomClearTag),
            )
        }
    }
}

private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Couldn't open"
