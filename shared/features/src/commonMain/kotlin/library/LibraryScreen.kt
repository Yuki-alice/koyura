package app.koyura.features.library

import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.components.ModalReaderSheet
import app.koyura.designsystem.components.ReaderPill
import app.koyura.designsystem.components.ReaderTopAppBar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.CrossfadingCover
import app.koyura.designsystem.CrossfadingCoverScope
import app.koyura.designsystem.CoverCornerRadius
import app.koyura.designsystem.CoverDecoder
import app.koyura.designsystem.CoverImageLoader
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.ListGroupHeader
import app.koyura.designsystem.LocalCoverDecoder
import app.koyura.designsystem.LocalCoverImageLoader
import app.koyura.designsystem.components.AppAlertDialog
import app.koyura.designsystem.components.AppButton
import app.koyura.designsystem.components.AppCircularProgress
import app.koyura.designsystem.components.AppDivider
import app.koyura.designsystem.components.AppFilledIconButton
import app.koyura.designsystem.components.AppScrollableTabBar
import app.koyura.designsystem.components.AppSurface
import app.koyura.designsystem.components.AppTab
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.AppTextField
import app.koyura.designsystem.components.UiTheme
import app.koyura.features.resources.Res
import app.koyura.features.resources.library_action_change_categories
import app.koyura.features.resources.library_action_download
import app.koyura.features.resources.library_action_failed
import app.koyura.features.resources.library_action_mark_read
import app.koyura.features.resources.library_action_mark_unread
import app.koyura.features.resources.library_action_migrate
import app.koyura.features.resources.library_action_remove
import app.koyura.features.resources.library_cancel
import app.koyura.features.resources.library_close
import app.koyura.features.resources.library_continue
import app.koyura.features.resources.library_display
import app.koyura.features.resources.library_downloaded_chapters
import app.koyura.features.resources.library_edit_categories
import app.koyura.features.resources.library_empty
import app.koyura.features.resources.library_empty_body
import app.koyura.features.resources.library_empty_browse
import app.koyura.features.resources.library_empty_guide
import app.koyura.features.resources.library_filter
import app.koyura.features.resources.library_global_search
import app.koyura.features.resources.library_group
import app.koyura.features.resources.library_invert_selection
import app.koyura.features.resources.library_load_failed
import app.koyura.features.resources.library_menu_refresh
import app.koyura.features.resources.library_more
import app.koyura.features.resources.library_no_results
import app.koyura.features.resources.library_random_open
import app.koyura.features.resources.library_retry
import app.koyura.features.resources.library_search_clear
import app.koyura.features.resources.library_search_hint
import app.koyura.features.resources.library_select_all
import app.koyura.features.resources.library_sort
import app.koyura.features.resources.library_start
import app.koyura.features.resources.library_tab_all
import app.koyura.features.resources.library_title
import app.koyura.features.resources.library_unread_chapters
import app.koyura.features.resources.library_works_count


import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

// region 测试锚点

const val LibraryRootTag = "library-root"
const val LibraryTitleTag = "library-title"
const val LibraryCountTag = "library-count"
const val LibrarySearchTag = "library-search-query"
const val LibrarySearchToggleTag = "library-search-toggle"
const val LibraryFilterButtonTag = "library-filter-button"
const val LibraryOverflowTag = "library-overflow"
const val LibraryMenuSortTag = "library-menu-sort"
const val LibraryMenuDisplayTag = "library-menu-display"
const val LibraryMenuGroupTag = "library-menu-group"
const val LibraryMenuRandomTag = "library-menu-random"
const val LibraryMenuSelectAllTag = "library-menu-select-all"
const val LibraryMenuCategoriesTag = "library-menu-categories"
const val LibraryMenuRefreshTag = "library-menu-refresh"
const val LibraryMenuGlobalSearchTag = "library-menu-global-search"
const val LibraryTabsTag = "library-tabs"
const val LibraryGridTag = "library-grid"
const val LibraryListTag = "library-list"
const val LibraryPanoramaTag = "library-panorama"
const val LibrarySelectionBarTag = "library-selection-bar"
const val LibrarySelectionCountTag = "library-selection-count"
const val LibrarySelectAllTag = "library-select-all"
const val LibraryInvertSelectionTag = "library-invert-selection"
const val LibraryClearSelectionTag = "library-clear-selection"
const val LibraryBottomMenuTag = "library-bottom-menu"
const val LibraryBottomChangeCategoriesTag = "library-bottom-change-categories"
const val LibraryBottomMarkReadTag = "library-bottom-mark-read"
const val LibraryBottomMarkUnreadTag = "library-bottom-mark-unread"
const val LibraryBottomRemoveTag = "library-bottom-remove"
const val LibraryBottomDownloadTag = "library-bottom-download"
const val LibraryBottomMigrateTag = "library-bottom-migrate"
const val LibraryEmptyTag = "library-empty"
const val LibraryEmptyBrowseTag = "library-empty-browse"
const val LibraryEmptyGuideTag = "library-empty-guide"
const val LibraryLoadingTag = "library-loading"
const val LibraryErrorTag = "library-error"
const val LibraryRetryTag = "library-retry"
const val LibraryNoResultsTag = "library-no-results"
const val LibraryNoResultsClearTag = "library-no-results-clear"
const val LibraryGlobalSearchTag = "library-global-search"
const val LibraryActionErrorTag = "library-action-error"
const val LibrarySheetCloseTag = "library-sheet-close"

fun libraryCardTag(sourceKey: String, workId: String) = "library-card-$sourceKey-$workId"
fun libraryCardTitleTag(sourceKey: String, workId: String) = "library-card-title-$sourceKey-$workId"
fun libraryTabTag(id: Long?) = if (id == null) "library-tab-all" else "library-tab-$id"
fun libraryUnreadBadgeTag(sourceKey: String, workId: String) = "library-unread-$sourceKey-$workId"
fun libraryDownloadBadgeTag(sourceKey: String, workId: String) = "library-download-$sourceKey-$workId"
fun libraryLanguageBadgeTag(sourceKey: String, workId: String) = "library-language-$sourceKey-$workId"
fun libraryContinueTag(sourceKey: String, workId: String) = "library-continue-$sourceKey-$workId"
fun libraryGroupHeaderTag(title: String) = "library-group-$title"

// endregion

/** 书架页内弹层：均为页内状态，不进返回栈（对齐原版设置弹层行为）。 */
private enum class LibrarySheet {
    FILTER,
    SORT,
    DISPLAY,
    GROUP,
    CATEGORIES,
    PICKER,
    REMOVE,
}

/**
 * 书架根页：工具栏 + 分类页签 + 紧凑/松散/全景网格 + 列表 + 徽标 + 多选底部菜单。
 *
 * - 根路由沿用 `TabRootRoute(NavTab.Library)`，由装配层把占位内容换成此页（见 notes 接线段）；
 *   本页不新增路由、不持有返回栈，弹层均为页内状态。
 * - 选择态下底栏隐藏由装配层读取 [LibrarySession.selection] 实现，本页只暴露状态。
 * - 封面经 [LibrarySession.coverBytes] 单次加载（可见即加载 + 一屏预取），
 *   空 URL 与失败回退首字母占位；网格封面统一 2:3、圆角 8dp。
 */
@Composable
fun LibraryScreen(
    session: LibrarySession,
    decodeCover: (ByteArray) -> ImageBitmap?,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    onContinue: (entry: LibraryEntry) -> Unit,
    onOpenBrowse: () -> Unit = {},
    onGlobalSearch: (() -> Unit)? = null,
    onGuide: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<LibrarySheet?>(null) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        session.refresh()
    }

    Column(modifier = modifier.fillMaxSize().testTag(LibraryRootTag)) {
        if (session.selection.isNotEmpty()) {
            LibrarySelectionToolbar(session = session)
        } else {
            LibraryToolbar(
                session = session,
                searching = searching,
                onToggleSearch = { searching = !searching },
                onOpenSheet = { sheet = it },
                onRandomOpen = { entry ->
                    onOpenWork(entry.sourceKey, entry.workId, entry.displayTitle)
                },
                onGlobalSearch = onGlobalSearch,
            )
        }
        LibraryCategoryTabs(session = session)
        session.actionError?.let { message ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag(LibraryActionErrorTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(
                    text = stringResource(Res.string.library_action_failed, message),
                    color = UiTheme.colorScheme.error,
                    style = UiTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                AppTextButton(onClick = { session.clearActionError() }) {
                    AppText(stringResource(Res.string.library_cancel))
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val load = session.loadState) {
                LibraryLoadState.Loading -> Box(
                    modifier = Modifier.fillMaxSize().testTag(LibraryLoadingTag),
                    contentAlignment = Alignment.Center,
                ) {
                    AppCircularProgress()
                }
                is LibraryLoadState.Failed -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp).testTag(LibraryErrorTag),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppText(
                        text = stringResource(Res.string.library_load_failed, load.message),
                        color = UiTheme.colorScheme.error,
                    )
                    AppButton(
                        onClick = { scope.launch { session.refresh() } },
                        modifier = Modifier.testTag(LibraryRetryTag),
                    ) {
                        AppText(stringResource(Res.string.library_retry))
                    }
                }
                LibraryLoadState.Unavailable,
                LibraryLoadState.Ready,
                -> CrossfadingCoverScope {
                    LibraryContent(
                        session = session,
                        decodeCover = decodeCover,
                        onOpenWork = onOpenWork,
                        onContinue = onContinue,
                        onOpenBrowse = onOpenBrowse,
                        onGlobalSearch = onGlobalSearch,
                        onGuide = onGuide,
                    )
                }
            }
        }
        if (session.selection.isNotEmpty()) {
            LibraryBottomMenu(
                session = session,
                onChangeCategories = { sheet = LibrarySheet.PICKER },
                onRemove = { sheet = LibrarySheet.REMOVE },
            )
        }
    }

    val current = sheet
    if (current != null) {
        LibrarySheetHost(
            sheet = current,
            session = session,
            onDismiss = { sheet = null },
            onOpenSheet = { sheet = it },
        )
    }
}

// region 工具栏

@Composable
private fun LibraryToolbar(
    session: LibrarySession,
    searching: Boolean,
    onToggleSearch: () -> Unit,
    onOpenSheet: (LibrarySheet) -> Unit,
    onRandomOpen: (LibraryEntry) -> Unit,
    onGlobalSearch: (() -> Unit)?,
) {
    var overflow by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        ReaderTopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(
                        text = stringResource(Res.string.library_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).testTag(LibraryTitleTag),
                    )
                    if (session.snapshot.prefs.showWorkCount) {
                        AppSurface(shape = UiTheme.shapes.small, tonalElevation = 2.dp) {
                            AppText(
                                // 同一可见范围：隐藏关闭时仅归属隐藏分类的作品不计入总数。
                                text = session.countForTab(null).toString(),
                                style = UiTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag(LibraryCountTag),
                            )
                        }
                    }
                }
            },
            actions = {
                ActionIconButton(
                    icon = if (searching) ActionIcon.Close else ActionIcon.Search,
                    label = stringResource(if (searching) Res.string.library_search_clear else Res.string.library_search_hint),
                    onClick = onToggleSearch,
                    modifier = Modifier.testTag(LibrarySearchToggleTag),
                )
                ActionIconButton(
                    icon = ActionIcon.Filter,
                    label = stringResource(Res.string.library_filter),
                    onClick = { onOpenSheet(LibrarySheet.FILTER) },
                    modifier = Modifier.testTag(LibraryFilterButtonTag),
                )
                ActionIconButton(
                    icon = ActionIcon.More,
                    label = stringResource(Res.string.library_more),
                    onClick = { overflow = true },
                    modifier = Modifier.testTag(LibraryOverflowTag),
                )
            },
        )
        if (overflow) {
            LibraryOverflowDialog(
                session = session,
                onDismiss = { overflow = false },
                onOpenSheet = {
                    overflow = false
                    onOpenSheet(it)
                },
                onRandomOpen = {
                    overflow = false
                    onRandomOpen(it)
                },
                onGlobalSearch = onGlobalSearch?.let { global ->
                    {
                        overflow = false
                        global()
                    }
                },
            )
        }
        if (searching) {
            AppTextField(
                value = session.query,
                onValueChange = { session.updateQuery(it) },
                placeholder = { AppText(stringResource(Res.string.library_search_hint)) },
                singleLine = true,
                trailingIcon = {
                    if (session.query.isNotEmpty()) {
                        ActionIconButton(
                            icon = ActionIcon.Close,
                            label = stringResource(Res.string.library_search_clear),
                            onClick = { session.updateQuery("") },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .testTag(LibrarySearchTag),
            )
        }
    }
}

/**
 * 溢出菜单：对话框形态（无进入动画，离屏测试可达），选项与原版三点菜单一致。
 */
@Composable
private fun LibraryOverflowDialog(
    session: LibrarySession,
    onDismiss: () -> Unit,
    onOpenSheet: (LibrarySheet) -> Unit,
    onRandomOpen: (LibraryEntry) -> Unit,
    onGlobalSearch: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(stringResource(Res.string.library_more)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LibraryOverflowRow(
                    label = stringResource(Res.string.library_sort),
                    tag = LibraryMenuSortTag,
                    onClick = { onOpenSheet(LibrarySheet.SORT) },
                )
                LibraryOverflowRow(
                    label = stringResource(Res.string.library_display),
                    tag = LibraryMenuDisplayTag,
                    onClick = { onOpenSheet(LibrarySheet.DISPLAY) },
                )
                LibraryOverflowRow(
                    label = stringResource(Res.string.library_group),
                    tag = LibraryMenuGroupTag,
                    onClick = { onOpenSheet(LibrarySheet.GROUP) },
                )
                LibraryOverflowRow(
                    label = stringResource(Res.string.library_random_open),
                    tag = LibraryMenuRandomTag,
                    onClick = {
                        session.visibleEntries.randomOrNull()?.let(onRandomOpen)
                    },
                )
                LibraryOverflowRow(
                    label = stringResource(Res.string.library_select_all),
                    tag = LibraryMenuSelectAllTag,
                    onClick = { session.selectAllVisible() },
                )
                LibraryOverflowRow(
                    label = stringResource(Res.string.library_edit_categories),
                    tag = LibraryMenuCategoriesTag,
                    onClick = { onOpenSheet(LibrarySheet.CATEGORIES) },
                )
                LibraryOverflowRow(
                    label = stringResource(Res.string.library_menu_refresh),
                    tag = LibraryMenuRefreshTag,
                    onClick = {
                        scope.launch { session.refresh() }
                    },
                )
                // 全局搜索暂无生产页面与路由：无回调时禁用（保留测试锚点，生产不响应）。
                LibraryOverflowRow(
                    label = stringResource(Res.string.library_global_search),
                    tag = LibraryMenuGlobalSearchTag,
                    onClick = { onGlobalSearch?.invoke() },
                    enabled = onGlobalSearch != null,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                AppText(stringResource(Res.string.library_close))
            }
        },
    )
}

@Composable
private fun LibraryOverflowRow(
    label: String,
    tag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    AppTextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        AppText(
            text = label,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LibrarySelectionToolbar(session: LibrarySession) {
    ReaderTopAppBar(
        title = {
            AppText(
                text = session.selection.size.toString(),
                modifier = Modifier.testTag(LibrarySelectionCountTag),
            )
        },
        navigationIcon = {
            ActionIconButton(
                icon = ActionIcon.Close,
                label = stringResource(Res.string.library_cancel),
                onClick = session::clearSelection,
                modifier = Modifier.testTag(LibraryClearSelectionTag),
            )
        },
        actions = {
            ActionIconButton(
                icon = ActionIcon.SelectAll,
                label = stringResource(Res.string.library_select_all),
                onClick = session::selectAllVisible,
                modifier = Modifier.testTag(LibrarySelectAllTag),
            )
            ActionIconButton(
                icon = ActionIcon.InvertSelection,
                label = stringResource(Res.string.library_invert_selection),
                onClick = session::invertSelection,
                modifier = Modifier.testTag(LibraryInvertSelectionTag),
            )
        },
        modifier = Modifier.testTag(LibrarySelectionBarTag),
    )
}

// endregion

// region 分类页签

@Composable
private fun LibraryCategoryTabs(session: LibrarySession) {
    val tabs = session.tabs
    if (tabs.size <= 1 && session.snapshot.categories.isEmpty()) return
    val selected = tabs.indexOfFirst { it?.id == session.activeCategoryId }.takeIf { it >= 0 } ?: 0
    AppScrollableTabBar(
        selectedTabIndex = selected,
        modifier = Modifier.fillMaxWidth().testTag(LibraryTabsTag),
        edgePadding = 16.dp,
    ) {
        tabs.forEachIndexed { index, category ->
            // 同一可见范围：页签计数按隐藏开关过滤，仅归属隐藏的在“全部”中不计。
            val count = session.countForTab(category?.id)
            AppTab(
                selected = index == selected,
                onClick = { session.selectCategory(category?.id) },
                modifier = Modifier.testTag(libraryTabTag(category?.id)),
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppText(
                            category?.name ?: stringResource(Res.string.library_tab_all),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (session.snapshot.prefs.showWorkCount) {
                            ReaderPill(text = count.toString(), countStyle = true)
                        }
                    }
                },
            )
        }
    }
}

// endregion

// region 内容：网格 / 列表 / 空态

@Composable
private fun LibraryContent(
    session: LibrarySession,
    decodeCover: (ByteArray) -> ImageBitmap?,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    onContinue: (entry: LibraryEntry) -> Unit,
    onOpenBrowse: () -> Unit,
    onGlobalSearch: (() -> Unit)?,
    onGuide: (() -> Unit)?,
) {
    if (session.snapshot.entries.isEmpty()) {
        LibraryEmptyState(
            onOpenBrowse = onOpenBrowse,
            onGlobalSearch = onGlobalSearch,
            onGuide = onGuide,
        )
        return
    }
    if (session.visibleEntries.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).testTag(LibraryNoResultsTag),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(
                text = stringResource(Res.string.library_no_results),
                style = UiTheme.typography.bodyMedium,
                color = UiTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppButton(
                    onClick = { session.updateQuery("") },
                    modifier = Modifier.testTag(LibraryNoResultsClearTag),
                ) {
                    AppText(stringResource(Res.string.library_search_clear))
                }
                // 全局搜索暂无生产目的地：无回调时禁用（保留测试锚点，生产不响应）。
                AppTextButton(
                    onClick = { onGlobalSearch?.invoke() },
                    enabled = onGlobalSearch != null,
                    modifier = Modifier.testTag(LibraryGlobalSearchTag),
                ) {
                    AppText(stringResource(Res.string.library_global_search))
                }
            }
        }
        return
    }
    when (session.snapshot.prefs.displayMode) {
        LibraryDisplayMode.LIST -> LibraryListContent(
            session = session,
            decodeCover = decodeCover,
            onOpenWork = onOpenWork,
            onContinue = onContinue,
        )
        LibraryDisplayMode.COMFORTABLE_GRID_PANORAMA -> LibraryPanoramaContent(
            session = session,
            decodeCover = decodeCover,
            onOpenWork = onOpenWork,
            onContinue = onContinue,
        )
        LibraryDisplayMode.COMPACT_GRID,
        LibraryDisplayMode.COMFORTABLE_GRID,
        LibraryDisplayMode.COVER_ONLY_GRID,
        -> LibraryGridContent(
            session = session,
            decodeCover = decodeCover,
            onOpenWork = onOpenWork,
            onContinue = onContinue,
        )
    }
}

@Composable
private fun LibraryEmptyState(
    onOpenBrowse: () -> Unit,
    onGlobalSearch: (() -> Unit)?,
    onGuide: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(LibraryEmptyTag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 空状态颜文字（白皮书 06 §6.8 / 9.5 #17）：固定文案保证离屏截图确定性。
        AppText(
            text = "(・∀・)",
            style = UiTheme.typography.displayMedium,
            color = UiTheme.colorScheme.onSurfaceVariant.copy(
                alpha = DesignTokens.Alpha.Secondary,
            ),
            modifier = Modifier.testTag("library-empty-kaomoji"),
        )
        AppText(
            text = stringResource(Res.string.library_empty),
            style = UiTheme.typography.headlineSmall,
        )
        AppText(
            text = stringResource(Res.string.library_empty_body),
            style = UiTheme.typography.bodyMedium,
            color = UiTheme.colorScheme.onSurfaceVariant,
        )
        AppButton(
            onClick = onOpenBrowse,
            modifier = Modifier.testTag(LibraryEmptyBrowseTag),
        ) {
            AppText(stringResource(Res.string.library_empty_browse))
        }
        // 指南与全局搜索暂无生产目的地：无回调时禁用（保留测试锚点），空书架浏览始终可用。
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextButton(
                onClick = { onGuide?.invoke() },
                enabled = onGuide != null,
                modifier = Modifier.testTag(LibraryEmptyGuideTag),
            ) {
                AppText(stringResource(Res.string.library_empty_guide))
            }
            AppTextButton(
                onClick = { onGlobalSearch?.invoke() },
                enabled = onGlobalSearch != null,
                modifier = Modifier.testTag(LibraryGlobalSearchTag),
            ) {
                AppText(stringResource(Res.string.library_global_search))
            }
        }
    }
}

@Composable
private fun LibraryGridContent(
    session: LibrarySession,
    decodeCover: (ByteArray) -> ImageBitmap?,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    onContinue: (entry: LibraryEntry) -> Unit,
) {
    val mode = session.snapshot.prefs.displayMode
    val gridState = rememberLazyGridState()
    val sections = session.visibleSections
    val flat = remember(sections) { sections.flatMap { it.entries } }
    LibraryGridPrefetch(state = gridState, entries = flat, session = session)
    // 自适应列（白皮书 07:56，Code: LazyLibraryGrid.kt:22 自适应档 128dp，
    // 间距 CommonMangaItem.kt:55-56 各 4dp；列数偏好已移除）。
    LazyVerticalGrid(
        columns = GridCells.Adaptive(DesignTokens.Breakpoints.AdaptiveGridMinItemDp.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize().testTag(LibraryGridTag),
        contentPadding = PaddingValues(DesignTokens.Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
    ) {
        sections.forEach { section ->
            if (section.title.isNotEmpty()) {
                item(key = "group-${section.title}", span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.testTag(libraryGroupHeaderTag(section.title))) {
                        ListGroupHeader(text = section.title)
                    }
                }
            }
            items(section.entries, key = { "library-${it.sourceKey}-${it.workId}" }) { entry ->
                when (mode) {
                    LibraryDisplayMode.COVER_ONLY_GRID -> LibraryCoverOnlyCard(
                        entry = entry,
                        session = session,
                        decodeCover = decodeCover,
                        onOpenWork = onOpenWork,
                        onContinue = onContinue,
                    )
                    LibraryDisplayMode.COMFORTABLE_GRID -> LibraryComfortableCard(
                        entry = entry,
                        session = session,
                        decodeCover = decodeCover,
                        onOpenWork = onOpenWork,
                        onContinue = onContinue,
                    )
                    else -> LibraryCompactCard(
                        entry = entry,
                        session = session,
                        decodeCover = decodeCover,
                        onOpenWork = onOpenWork,
                        onContinue = onContinue,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryGridPrefetch(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    entries: List<LibraryEntry>,
    session: LibrarySession,
) {
    // 滚动位置只在 effect 内经 snapshotFlow 订阅，不在 composition 中直接读取
    // layoutInfo，避免布局-重组反馈抖动（离屏测试下 waitForIdle 永不到）。
    LaunchedEffect(state, entries) {
        androidx.compose.runtime.snapshotFlow {
            state.firstVisibleItemIndex to
                state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        }.collect { (first, visibleCount) ->
            if (entries.isEmpty()) return@collect
            val last = (first + visibleCount * 2).coerceAtMost(entries.size - 1)
            val from = first.coerceIn(0, entries.size - 1)
            if (from <= last) session.prefetchCovers(entries.subList(from, last + 1))
        }
    }
}

@Composable
private fun LibraryListContent(
    session: LibrarySession,
    decodeCover: (ByteArray) -> ImageBitmap?,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    onContinue: (entry: LibraryEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    val sections = session.visibleSections
    val flat = remember(sections) { sections.flatMap { it.entries } }
    // 同网格：滚动位置只在 effect 内订阅，不直接读进 composition。
    LaunchedEffect(listState, flat) {
        androidx.compose.runtime.snapshotFlow {
            listState.firstVisibleItemIndex to
                listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        }.collect { (first, visibleCount) ->
            if (flat.isEmpty()) return@collect
            val last = (first + visibleCount * 2).coerceAtMost(flat.size - 1)
            val from = first.coerceIn(0, flat.size - 1)
            if (from <= last) session.prefetchCovers(flat.subList(from, last + 1))
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag(LibraryListTag),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        sections.forEach { section ->
            if (section.title.isNotEmpty()) {
                stickyHeader(key = "group-${section.title}") {
                    Box(modifier = Modifier.testTag(libraryGroupHeaderTag(section.title))) {
                        ListGroupHeader(text = section.title)
                    }
                }
            }
            items(section.entries, key = { "library-${it.sourceKey}-${it.workId}" }) { entry ->
                LibraryListRow(
                    entry = entry,
                    session = session,
                    decodeCover = decodeCover,
                    onOpenWork = onOpenWork,
                    onContinue = onContinue,
                )
                AppDivider()
            }
        }
    }
}

@Composable
private fun LibraryPanoramaContent(
    session: LibrarySession,
    decodeCover: (ByteArray) -> ImageBitmap?,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    onContinue: (entry: LibraryEntry) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(LibraryPanoramaTag),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        session.visibleSections.forEach { section ->
            if (section.title.isNotEmpty()) {
                stickyHeader(key = "group-${section.title}") {
                    Box(modifier = Modifier.testTag(libraryGroupHeaderTag(section.title))) {
                        ListGroupHeader(text = section.title)
                    }
                }
            }
            items(section.entries, key = { "library-${it.sourceKey}-${it.workId}" }) { entry ->
                LibraryPanoramaCard(
                    entry = entry,
                    session = session,
                    decodeCover = decodeCover,
                    onOpenWork = onOpenWork,
                    onContinue = onContinue,
                )
            }
        }
    }
}

// endregion

// region 卡片与徽标

private fun LibraryCardModifier(
    entry: LibraryEntry,
    session: LibrarySession,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
): Modifier = Modifier
    .combinedClickable(
        onClick = {
            if (session.selection.isNotEmpty()) {
                session.toggleSelect(entry.key)
            } else {
                onOpenWork(entry.sourceKey, entry.workId, entry.displayTitle)
            }
        },
        onLongClick = { session.toggleSelect(entry.key) },
    )
    .testTag(libraryCardTag(entry.sourceKey, entry.workId))

@Composable
private fun LibraryCoverImage(
    entry: LibraryEntry,
    session: LibrarySession,
    decodeCover: (ByteArray) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    // 书架封面走全仓唯一 A 轨管线：加载器委托会话单次取图（失败抛错转 null 回退占位），
    // 解码器沿用调用方注入的平台实现；内存缓存沿用默认按源隔离实例。
    val loader = remember(session, entry.sourceKey, entry.coverUrl) {
        CoverImageLoader { _, _ ->
            session.coverBytes(entry)
        }
    }
    val decoder = remember(decodeCover) {
        CoverDecoder { bytes -> decodeCover(bytes) }
    }
    CompositionLocalProvider(
        LocalCoverImageLoader provides loader,
        LocalCoverDecoder provides decoder,
    ) {
        val selected = entry.key in session.selection
        val outline = if (selected) {
            Modifier.border(DesignTokens.Sizes.SelectionOutline, UiTheme.colorScheme.secondary, RoundedCornerShape(CoverCornerRadius))
        } else {
            Modifier
        }
        Box(modifier) {
            CrossfadingCover(
                sourceKey = entry.sourceKey,
                coverUrl = entry.coverUrl,
                label = entry.displayTitle,
                modifier = Modifier.matchParentSize().alpha(
                    if (selected) DesignTokens.Alpha.GridSelectedCover else 1f,
                ),
            )
            if (selected) Box(Modifier.matchParentSize().then(outline))
        }
    }
}

/** 封面徽标：左上未读/下载胶囊，右上语言+图源徽标（对齐原版 LibraryBadges）。 */
@Composable
private fun BoxScope.LibraryCoverBadges(
    entry: LibraryEntry,
    session: LibrarySession,
    onContinue: (entry: LibraryEntry) -> Unit,
) {
    val prefs = session.snapshot.prefs
    Column(
        modifier = Modifier.align(Alignment.TopStart).padding(DesignTokens.Spacing.ExtraSmall),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
    ) {
        if (prefs.showUnreadBadges && entry.unreadCount > 0) {
            AppSurface(
                shape = RoundedCornerShape(DesignTokens.Radius.ExtraSmall),
                color = UiTheme.colorScheme.primary,
                modifier = Modifier.height(DesignTokens.Sizes.BadgeHeight)
                    .testTag(libraryUnreadBadgeTag(entry.sourceKey, entry.workId)),
            ) {
                AppText(
                    text = stringResource(Res.string.library_unread_chapters, entry.unreadCount.toInt()),
                    style = UiTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = UiTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(DesignTokens.Sizes.BadgeContentPadding),
                )
            }
        }
        if (prefs.showDownloadBadges && entry.downloadedCount > 0) {
            AppSurface(
                shape = RoundedCornerShape(DesignTokens.Radius.ExtraSmall),
                color = UiTheme.colorScheme.tertiary,
                modifier = Modifier.height(DesignTokens.Sizes.BadgeHeight)
                    .testTag(libraryDownloadBadgeTag(entry.sourceKey, entry.workId)),
            ) {
                AppText(
                    text = stringResource(Res.string.library_downloaded_chapters, entry.downloadedCount.toInt()),
                    style = UiTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = UiTheme.colorScheme.onTertiary,
                    modifier = Modifier.padding(DesignTokens.Sizes.BadgeContentPadding),
                )
            }
        }
    }
    if (prefs.showLanguageBadges && (entry.language.isNotBlank() || entry.sourceName.isNotBlank())) {
        AppSurface(
            shape = RoundedCornerShape(DesignTokens.Radius.ExtraSmall),
            color = UiTheme.colorScheme.surfaceVariant,
            modifier = Modifier.align(Alignment.TopEnd).padding(DesignTokens.Spacing.ExtraSmall)
                .height(DesignTokens.Sizes.BadgeHeight)
                .testTag(libraryLanguageBadgeTag(entry.sourceKey, entry.workId)),
        ) {
            AppText(
                text = entry.language.uppercase().ifBlank { entry.sourceName.take(2).uppercase() },
                style = UiTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = UiTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(DesignTokens.Sizes.BadgeContentPadding),
            )
        }
    }
    if (session.selection.contains(entry.key)) {
        Box(
            modifier = Modifier.align(Alignment.Center)
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(UiTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            ActionIcon(ActionIcon.Checklist, null, tint = UiTheme.colorScheme.onPrimary)
        }
    } else if (prefs.showContinueButton && entry.canContinue) {
        // 白皮书 06 §6.6 Continue 钮：small 圆角、primaryContainer 90% 容器；
        // 尺寸按 Code（CommonMangaItem.kt:62,65 小尺寸档 28dp 钮 / 16dp 图标）。
        AppFilledIconButton(
            onClick = { onContinue(entry) },
            shape = RoundedCornerShape(DesignTokens.Radius.Small),
            containerColor = UiTheme.colorScheme.primaryContainer.copy(
                alpha = DesignTokens.Alpha.ContinueReading,
            ),
            contentColor = UiTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(DesignTokens.Spacing.ExtraSmall)
                .size(DesignTokens.Sizes.ContinueButtonSize)
                .testTag(libraryContinueTag(entry.sourceKey, entry.workId)),
        ) {
            ActionIcon(
                icon = ActionIcon.Play,
                contentDescription = stringResource(
                    if (entry.hasStarted) Res.string.library_continue else Res.string.library_start,
                ),
                modifier = Modifier.size(DesignTokens.Sizes.ContinueButtonIconSize),
            )
        }
    }
}

@Composable
private fun libraryLabelColor(entry: LibraryEntry, secondary: Boolean = false): Color {
    val read = entry.unreadCount == 0L && (entry.totalChapters > 0 || entry.hasStarted)
    val alpha = when {
        read -> DesignTokens.Alpha.Disabled
        secondary -> DesignTokens.Alpha.Secondary
        else -> 1f
    }
    return UiTheme.colorScheme.onSurface.copy(alpha = alpha)
}

@Composable
private fun LibraryCompactCard(
    entry: LibraryEntry,
    session: LibrarySession,
    decodeCover: (ByteArray) -> ImageBitmap?,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    onContinue: (entry: LibraryEntry) -> Unit,
) {
    Column(modifier = LibraryCardModifier(entry, session, onOpenWork)) {
        Box {
            LibraryCoverImage(
                entry = entry,
                session = session,
                decodeCover = decodeCover,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            )
            LibraryCoverBadges(entry = entry, session = session, onContinue = onContinue)
        }
        AppText(
            text = entry.displayTitle,
            color = libraryLabelColor(entry),
            style = UiTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).testTag(libraryCardTitleTag(entry.sourceKey, entry.workId)),
        )
    }
}

@Composable
private fun LibraryComfortableCard(
    entry: LibraryEntry,
    session: LibrarySession,
    decodeCover: (ByteArray) -> ImageBitmap?,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    onContinue: (entry: LibraryEntry) -> Unit,
) {
    Column(modifier = LibraryCardModifier(entry, session, onOpenWork)) {
        Box {
            LibraryCoverImage(
                entry = entry,
                session = session,
                decodeCover = decodeCover,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            )
            LibraryCoverBadges(entry = entry, session = session, onContinue = onContinue)
        }
        AppText(
            text = entry.displayTitle,
            color = libraryLabelColor(entry),
            style = UiTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp).testTag(libraryCardTitleTag(entry.sourceKey, entry.workId)),
        )
        AppText(
            text = entry.sourceName,
            style = UiTheme.typography.bodySmall,
            color = libraryLabelColor(entry, secondary = true),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibraryCoverOnlyCard(
    entry: LibraryEntry,
    session: LibrarySession,
    decodeCover: (ByteArray) -> ImageBitmap?,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    onContinue: (entry: LibraryEntry) -> Unit,
) {
    Box(modifier = LibraryCardModifier(entry, session, onOpenWork)) {
        LibraryCoverImage(
            entry = entry,
            session = session,
            decodeCover = decodeCover,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
        )
        LibraryCoverBadges(entry = entry, session = session, onContinue = onContinue)
    }
}

/**
 * 列表行：固定 96dp 行高，封面 56x80（沿用历史行占位尺寸）。
 */
@Composable
private fun LibraryListRow(
    entry: LibraryEntry,
    session: LibrarySession,
    decodeCover: (ByteArray) -> ImageBitmap?,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    onContinue: (entry: LibraryEntry) -> Unit,
) {
    Row(
        modifier = LibraryCardModifier(entry, session, onOpenWork)
            .fillMaxWidth()
            .heightIn(min = DesignTokens.Sizes.HistoryItemHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            LibraryCoverImage(
                entry = entry,
                session = session,
                decodeCover = decodeCover,
                modifier = Modifier.size(width = 56.dp, height = 80.dp),
            )
            if (session.selection.contains(entry.key)) {
                // 多选态：secondary 16–22% 行叠加（白皮书 02 §2.7 / 08 §8.2），亮暗按 surface 明度区分。
                val selectedOverlay = if (UiTheme.colorScheme.surface.luminance() < 0.5f) {
                    DesignTokens.Alpha.SelectedBackgroundDark
                } else {
                    DesignTokens.Alpha.SelectedBackgroundLight
                }
                Box(
                    modifier = Modifier.size(width = 56.dp, height = 80.dp)
                        .background(UiTheme.colorScheme.secondary.copy(alpha = selectedOverlay)),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText("✓", color = UiTheme.colorScheme.onSecondary)
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 16.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AppText(
                text = entry.displayTitle,
                color = libraryLabelColor(entry),
                style = UiTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(libraryCardTitleTag(entry.sourceKey, entry.workId)),
            )
            val secondary = buildList {
                if (entry.unreadCount > 0) add(stringResource(Res.string.library_unread_chapters, entry.unreadCount.toInt()))
                if (entry.downloadedCount > 0) add(
                    stringResource(Res.string.library_downloaded_chapters, entry.downloadedCount.toInt()),
                )
                if (entry.totalChapters > 0) add(entry.totalChapters.toString())
            }.joinToString(" · ")
            if (secondary.isNotEmpty()) {
                AppText(
                    text = secondary,
                    style = UiTheme.typography.bodySmall,
                    color = libraryLabelColor(entry, secondary = true),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AppText(
                text = entry.sourceName,
                style = UiTheme.typography.labelSmall,
                color = libraryLabelColor(entry, secondary = true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (session.snapshot.prefs.showContinueButton && entry.canContinue) {
            AppFilledIconButton(
                onClick = { onContinue(entry) },
                shape = RoundedCornerShape(DesignTokens.Radius.Small),
                containerColor = UiTheme.colorScheme.primaryContainer.copy(
                    alpha = DesignTokens.Alpha.ContinueReading,
                ),
                contentColor = UiTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(DesignTokens.Sizes.ContinueButtonSize)
                    .testTag(libraryContinueTag(entry.sourceKey, entry.workId)),
            ) {
                ActionIcon(
                    icon = ActionIcon.Play,
                    contentDescription = stringResource(
                        if (entry.hasStarted) Res.string.library_continue else Res.string.library_start,
                    ),
                    modifier = Modifier.size(DesignTokens.Sizes.ContinueButtonIconSize),
                )
            }
        }
    }
}

@Composable
private fun LibraryPanoramaCard(
    entry: LibraryEntry,
    session: LibrarySession,
    decodeCover: (ByteArray) -> ImageBitmap?,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    onContinue: (entry: LibraryEntry) -> Unit,
) {
    Box(
        modifier = LibraryCardModifier(entry, session, onOpenWork)
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(CoverCornerRadius)),
    ) {
        LibraryCoverImage(
            entry = entry,
            session = session,
            decodeCover = decodeCover,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier.fillMaxSize()
                .background(UiTheme.colorScheme.surface.copy(alpha = 0.55f)),
        )
        LibraryCoverBadges(entry = entry, session = session, onContinue = onContinue)
        AppText(
            text = entry.displayTitle,
            color = libraryLabelColor(entry),
            style = UiTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                .testTag(libraryCardTitleTag(entry.sourceKey, entry.workId)),
        )
    }
}

// endregion

// region 底部批量操作

@Composable
private fun LibraryBottomMenu(
    session: LibrarySession,
    onChangeCategories: () -> Unit,
    onRemove: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    AppSurface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().testTag(LibraryBottomMenuTag),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIconButton(
                icon = ActionIcon.Repository,
                label = stringResource(Res.string.library_action_change_categories),
                onClick = onChangeCategories,
                modifier = Modifier.testTag(LibraryBottomChangeCategoriesTag),
            )
            ActionIconButton(
                icon = ActionIcon.Checklist,
                label = stringResource(Res.string.library_action_mark_read),
                onClick = { scope.launch { session.markSelectedAsRead(true) } },
                modifier = Modifier.testTag(LibraryBottomMarkReadTag),
            )
            ActionIconButton(
                icon = ActionIcon.Schedule,
                label = stringResource(Res.string.library_action_mark_unread),
                onClick = { scope.launch { session.markSelectedAsRead(false) } },
                modifier = Modifier.testTag(LibraryBottomMarkUnreadTag),
            )
            // 离线下载在后续轮次（更新与离线轨）提供，本轮只留 disabled 入口。
            ActionIconButton(
                icon = ActionIcon.Download,
                label = stringResource(Res.string.library_action_download),
                onClick = {},
                enabled = false,
                modifier = Modifier.testTag(LibraryBottomDownloadTag),
            )
            // 跨源迁移合并入口本轮只留 disabled 入口，不实现。
            ActionIconButton(
                icon = ActionIcon.Sync,
                label = stringResource(Res.string.library_action_migrate),
                onClick = {},
                enabled = false,
                modifier = Modifier.testTag(LibraryBottomMigrateTag),
            )
            ActionIconButton(
                icon = ActionIcon.Delete,
                label = stringResource(Res.string.library_action_remove),
                onClick = onRemove,
                modifier = Modifier.testTag(LibraryBottomRemoveTag),
                contentColor = UiTheme.colorScheme.error,
            )
        }
    }
}

// endregion

// region 弹层宿主

@Composable
private fun LibrarySheetHost(
    sheet: LibrarySheet,
    session: LibrarySession,
    onDismiss: () -> Unit,
    onOpenSheet: (LibrarySheet) -> Unit,
) {
    val scope = rememberCoroutineScope()
    when (sheet) {
        LibrarySheet.REMOVE -> LibraryRemoveDialog(
            count = session.selection.size,
            onConfirm = {
                scope.launch {
                    if (session.removeSelected()) onDismiss()
                }
            },
            onDismiss = onDismiss,
        )
        LibrarySheet.PICKER -> LibraryCategoryPickerDialog(
            session = session,
            initial = emptySet(),
            onConfirm = { ids ->
                scope.launch {
                    if (session.setCategoriesForSelected(ids)) onDismiss()
                }
            },
            onDismiss = onDismiss,
        )
        else -> ModalReaderSheet(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = DesignTokens.Breakpoints.DialogMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                when (sheet) {
                    LibrarySheet.FILTER -> LibraryFilterPage(
                        session = session,
                        onEditCategories = { onOpenSheet(LibrarySheet.CATEGORIES) },
                    )
                    LibrarySheet.SORT -> LibrarySortPage(session = session)
                    LibrarySheet.DISPLAY -> LibraryDisplayPage(session = session)
                    LibrarySheet.GROUP -> LibraryGroupPage(session = session)
                    LibrarySheet.CATEGORIES -> LibraryCategoriesPage(session = session)
                    else -> Unit
                }
                AppTextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                        .testTag(LibrarySheetCloseTag),
                ) {
                    AppText(stringResource(Res.string.library_close))
                }
            }
        }
    }
}

// endregion
