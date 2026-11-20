package app.koyura.features.library

import app.koyura.designsystem.DesignTokens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import app.koyura.designsystem.components.AppAlertDialog
import app.koyura.designsystem.components.AppCheckbox
import app.koyura.designsystem.components.AppFilterChip
import app.koyura.designsystem.components.AppSwitch
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.AppTextField
import app.koyura.designsystem.components.UiTheme
import app.koyura.features.resources.Res
import app.koyura.features.resources.library_action_change_categories
import app.koyura.features.resources.library_cancel
import app.koyura.features.resources.library_categories_title
import app.koyura.features.resources.library_category_add
import app.koyura.features.resources.library_category_default
import app.koyura.features.resources.library_category_delete
import app.koyura.features.resources.library_category_empty
import app.koyura.features.resources.library_category_hidden
import app.koyura.features.resources.library_category_hide
import app.koyura.features.resources.library_category_name
import app.koyura.features.resources.library_category_rename
import app.koyura.features.resources.library_category_unhide
import app.koyura.features.resources.library_display
import app.koyura.features.resources.library_edit_categories
import app.koyura.features.resources.library_favorite_choose
import app.koyura.features.resources.library_filter
import app.koyura.features.resources.library_filter_bookmarked
import app.koyura.features.resources.library_filter_completed
import app.koyura.features.resources.library_filter_downloaded
import app.koyura.features.resources.library_filter_lewd
import app.koyura.features.resources.library_filter_started
import app.koyura.features.resources.library_filter_state_exclude
import app.koyura.features.resources.library_filter_state_include
import app.koyura.features.resources.library_filter_state_off
import app.koyura.features.resources.library_filter_unread
import app.koyura.features.resources.library_group
import app.koyura.features.resources.library_group_by_category
import app.koyura.features.resources.library_group_by_source
import app.koyura.features.resources.library_group_by_status
import app.koyura.features.resources.library_mode_comfortable
import app.koyura.features.resources.library_mode_compact
import app.koyura.features.resources.library_mode_cover_only
import app.koyura.features.resources.library_mode_list
import app.koyura.features.resources.library_mode_panorama
import app.koyura.features.resources.library_remove_body
import app.koyura.features.resources.library_remove_confirm
import app.koyura.features.resources.library_remove_title
import app.koyura.features.resources.library_save
import app.koyura.features.resources.library_show_category_labels
import app.koyura.features.resources.library_show_continue
import app.koyura.features.resources.library_show_downloads
import app.koyura.features.resources.library_show_hidden_categories
import app.koyura.features.resources.library_show_language
import app.koyura.features.resources.library_show_unread
import app.koyura.features.resources.library_show_work_count
import app.koyura.features.resources.library_sort
import app.koyura.features.resources.library_sort_alphabetical
import app.koyura.features.resources.library_sort_ascending
import app.koyura.features.resources.library_sort_chapter_fetch_date
import app.koyura.features.resources.library_sort_date_added
import app.koyura.features.resources.library_sort_descending
import app.koyura.features.resources.library_sort_last_read
import app.koyura.features.resources.library_sort_last_update_check
import app.koyura.features.resources.library_sort_latest_chapter
import app.koyura.features.resources.library_sort_random
import app.koyura.features.resources.library_sort_total_chapters
import app.koyura.features.resources.library_sort_unread_count


import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// region 弹层测试锚点

const val LibraryFilterSheetTag = "library-filter-sheet"
const val LibrarySortSheetTag = "library-sort-sheet"
const val LibraryDisplaySheetTag = "library-display-sheet"
const val LibraryGroupSheetTag = "library-group-sheet"
const val LibraryCategoriesSheetTag = "library-categories-sheet"
const val LibraryCategoryPickerTag = "library-category-picker"
const val LibraryCategoryPickerConfirmTag = "library-category-picker-confirm"
const val LibraryRemoveDialogTag = "library-remove-dialog"
const val LibraryRemoveConfirmTag = "library-remove-confirm"
const val LibraryEditCategoriesTag = "library-edit-categories"
const val LibraryCategoryAddTag = "library-category-add"
const val LibraryCategoryNameTag = "library-category-name"
const val LibrarySortDirectionTag = "library-sort-direction"

fun libraryFilterRowTag(name: String) = "library-filter-$name"
fun librarySortRowTag(type: LibrarySortType) = "library-sort-${type.name}"
fun libraryModeChipTag(mode: LibraryDisplayMode) = "library-mode-${mode.name}"
fun libraryDisplayToggleTag(name: String) = "library-display-$name"
fun libraryGroupRowTag(group: LibraryGroupBy) = "library-groupby-${group.name}"
fun libraryCategoryRowTag(id: Long) = "library-category-$id"
fun libraryPickerRowTag(id: Long) = "library-picker-$id"

// endregion

private fun LibraryTriState.label(): StringResource = when (this) {
    LibraryTriState.DISABLED -> Res.string.library_filter_state_off
    LibraryTriState.INCLUDE -> Res.string.library_filter_state_include
    LibraryTriState.EXCLUDE -> Res.string.library_filter_state_exclude
}

// region 筛选页：六个三态行 + 分类编辑入口

@Composable
fun LibraryFilterPage(
    session: LibrarySession,
    onEditCategories: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val filters = session.snapshot.prefs.filters
    fun cycle(read: (LibraryFilters) -> LibraryTriState, write: (LibraryFilters, LibraryTriState) -> LibraryFilters) {
        scope.launch {
            session.updatePrefs { prefs -> prefs.copy(filters = write(prefs.filters, read(prefs.filters).next())) }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small)
            .testTag(LibraryFilterSheetTag),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
    ) {
        AppText(stringResource(Res.string.library_filter), style = UiTheme.typography.titleMedium)
        LibraryTriStateRow(
            label = stringResource(Res.string.library_filter_downloaded),
            state = filters.downloaded,
            tag = libraryFilterRowTag("downloaded"),
            onClick = { cycle({ it.downloaded }, { f, v -> f.copy(downloaded = v) }) },
        )
        LibraryTriStateRow(
            label = stringResource(Res.string.library_filter_unread),
            state = filters.unread,
            tag = libraryFilterRowTag("unread"),
            onClick = { cycle({ it.unread }, { f, v -> f.copy(unread = v) }) },
        )
        LibraryTriStateRow(
            label = stringResource(Res.string.library_filter_started),
            state = filters.started,
            tag = libraryFilterRowTag("started"),
            onClick = { cycle({ it.started }, { f, v -> f.copy(started = v) }) },
        )
        LibraryTriStateRow(
            label = stringResource(Res.string.library_filter_bookmarked),
            state = filters.bookmarked,
            tag = libraryFilterRowTag("bookmarked"),
            onClick = { cycle({ it.bookmarked }, { f, v -> f.copy(bookmarked = v) }) },
        )
        LibraryTriStateRow(
            label = stringResource(Res.string.library_filter_completed),
            state = filters.completed,
            tag = libraryFilterRowTag("completed"),
            onClick = { cycle({ it.completed }, { f, v -> f.copy(completed = v) }) },
        )
        LibraryTriStateRow(
            label = stringResource(Res.string.library_filter_lewd),
            state = filters.lewd,
            tag = libraryFilterRowTag("lewd"),
            onClick = { cycle({ it.lewd }, { f, v -> f.copy(lewd = v) }) },
        )
        AppTextButton(
            onClick = onEditCategories,
            modifier = Modifier.testTag(LibraryEditCategoriesTag),
        ) {
            AppText(stringResource(Res.string.library_edit_categories))
        }
    }
}

@Composable
private fun LibraryTriStateRow(
    label: String,
    state: LibraryTriState,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag)
            .padding(vertical = DesignTokens.Spacing.Row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = label,
            style = UiTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        AppText(
            text = stringResource(state.label()),
            style = UiTheme.typography.bodyMedium,
            color = UiTheme.colorScheme.primary,
        )
    }
}

// endregion

// region 排序页：九个排序行 + 升降序

private data class SortRow(val type: LibrarySortType, val label: StringResource)

private val SortRows = listOf(
    SortRow(LibrarySortType.ALPHABETICAL, Res.string.library_sort_alphabetical),
    SortRow(LibrarySortType.TOTAL_CHAPTERS, Res.string.library_sort_total_chapters),
    SortRow(LibrarySortType.LAST_READ, Res.string.library_sort_last_read),
    SortRow(LibrarySortType.LAST_UPDATE_CHECK, Res.string.library_sort_last_update_check),
    SortRow(LibrarySortType.UNREAD_COUNT, Res.string.library_sort_unread_count),
    SortRow(LibrarySortType.LATEST_CHAPTER, Res.string.library_sort_latest_chapter),
    SortRow(LibrarySortType.CHAPTER_FETCH_DATE, Res.string.library_sort_chapter_fetch_date),
    SortRow(LibrarySortType.DATE_ADDED, Res.string.library_sort_date_added),
    SortRow(LibrarySortType.RANDOM, Res.string.library_sort_random),
)

@Composable
fun LibrarySortPage(session: LibrarySession) {
    val scope = rememberCoroutineScope()
    val prefs = session.snapshot.prefs
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small)
            .testTag(LibrarySortSheetTag),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
    ) {
        AppText(stringResource(Res.string.library_sort), style = UiTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                scope.launch { session.updatePrefs { it.copy(ascending = !it.ascending) } }
            }.testTag(LibrarySortDirectionTag).padding(vertical = DesignTokens.Spacing.Row),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(if (prefs.ascending) Res.string.library_sort_ascending else Res.string.library_sort_descending),
                style = UiTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            AppSwitch(
                checked = prefs.ascending,
                onCheckedChange = { checked ->
                    scope.launch { session.updatePrefs { it.copy(ascending = checked) } }
                },
            )
        }
        SortRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch { session.updatePrefs { it.copy(sortType = row.type) } }
                }.testTag(librarySortRowTag(row.type)).padding(vertical = DesignTokens.Spacing.Row),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(
                    text = stringResource(row.label),
                    style = UiTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                AppText(
                    text = if (prefs.sortType == row.type) "●" else "○",
                    color = UiTheme.colorScheme.primary,
                )
            }
        }
    }
}

// endregion

// region 显示页：五种模式 + 列数滑杆 + 七个复选

@Composable
fun LibraryDisplayPage(session: LibrarySession) {
    val scope = rememberCoroutineScope()
    val prefs = session.snapshot.prefs
    fun toggle(read: (LibraryPrefs) -> Boolean, write: (LibraryPrefs, Boolean) -> LibraryPrefs): () -> Unit = {
        scope.launch { session.updatePrefs { write(it, !read(it)) } }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small)
            .testTag(LibraryDisplaySheetTag),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
    ) {
        AppText(stringResource(Res.string.library_display), style = UiTheme.typography.titleMedium)
        @Composable
        fun modeChip(mode: LibraryDisplayMode, label: StringResource) {
            AppFilterChip(
                selected = prefs.displayMode == mode,
                onClick = {
                    scope.launch { session.updatePrefs { it.copy(displayMode = mode) } }
                },
                label = { AppText(stringResource(label)) },
                modifier = Modifier.testTag(libraryModeChipTag(mode)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small)) {
            modeChip(LibraryDisplayMode.COMPACT_GRID, Res.string.library_mode_compact)
            modeChip(LibraryDisplayMode.COMFORTABLE_GRID, Res.string.library_mode_comfortable)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small)) {
            modeChip(LibraryDisplayMode.COMFORTABLE_GRID_PANORAMA, Res.string.library_mode_panorama)
            modeChip(LibraryDisplayMode.LIST, Res.string.library_mode_list)
            modeChip(LibraryDisplayMode.COVER_ONLY_GRID, Res.string.library_mode_cover_only)
        }
        LibraryCheckRow(
            label = stringResource(Res.string.library_show_unread),
            checked = prefs.showUnreadBadges,
            tag = libraryDisplayToggleTag("unread"),
            onToggle = toggle({ it.showUnreadBadges }, { p, v -> p.copy(showUnreadBadges = v) }),
        )
        LibraryCheckRow(
            label = stringResource(Res.string.library_show_downloads),
            checked = prefs.showDownloadBadges,
            tag = libraryDisplayToggleTag("downloads"),
            onToggle = toggle({ it.showDownloadBadges }, { p, v -> p.copy(showDownloadBadges = v) }),
        )
        LibraryCheckRow(
            label = stringResource(Res.string.library_show_language),
            checked = prefs.showLanguageBadges,
            tag = libraryDisplayToggleTag("language"),
            onToggle = toggle({ it.showLanguageBadges }, { p, v -> p.copy(showLanguageBadges = v) }),
        )
        LibraryCheckRow(
            label = stringResource(Res.string.library_show_continue),
            checked = prefs.showContinueButton,
            tag = libraryDisplayToggleTag("continue"),
            onToggle = toggle({ it.showContinueButton }, { p, v -> p.copy(showContinueButton = v) }),
        )
        LibraryCheckRow(
            label = stringResource(Res.string.library_show_category_labels),
            checked = prefs.showCategoryLabels,
            tag = libraryDisplayToggleTag("category-labels"),
            onToggle = toggle({ it.showCategoryLabels }, { p, v -> p.copy(showCategoryLabels = v) }),
        )
        LibraryCheckRow(
            label = stringResource(Res.string.library_show_hidden_categories),
            checked = prefs.showHiddenCategories,
            tag = libraryDisplayToggleTag("hidden-categories"),
            onToggle = toggle({ it.showHiddenCategories }, { p, v -> p.copy(showHiddenCategories = v) }),
        )
        LibraryCheckRow(
            label = stringResource(Res.string.library_show_work_count),
            checked = prefs.showWorkCount,
            tag = libraryDisplayToggleTag("work-count"),
            onToggle = toggle({ it.showWorkCount }, { p, v -> p.copy(showWorkCount = v) }),
        )
    }
}

@Composable
private fun LibraryCheckRow(
    label: String,
    checked: Boolean,
    tag: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = label,
            style = UiTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        AppCheckbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

// endregion

// region 分组页：分类 / 图源 / 状态

@Composable
fun LibraryGroupPage(session: LibrarySession) {
    val scope = rememberCoroutineScope()
    val groupBy = session.snapshot.prefs.groupBy
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small)
            .testTag(LibraryGroupSheetTag),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
    ) {
        AppText(stringResource(Res.string.library_group), style = UiTheme.typography.titleMedium)
        LibraryGroupRow(
            label = stringResource(Res.string.library_group_by_category),
            selected = groupBy == LibraryGroupBy.CATEGORY,
            tag = libraryGroupRowTag(LibraryGroupBy.CATEGORY),
            onClick = {
                scope.launch { session.updatePrefs { it.copy(groupBy = LibraryGroupBy.CATEGORY) } }
            },
        )
        LibraryGroupRow(
            label = stringResource(Res.string.library_group_by_source),
            selected = groupBy == LibraryGroupBy.SOURCE,
            tag = libraryGroupRowTag(LibraryGroupBy.SOURCE),
            onClick = {
                scope.launch { session.updatePrefs { it.copy(groupBy = LibraryGroupBy.SOURCE) } }
            },
        )
        LibraryGroupRow(
            label = stringResource(Res.string.library_group_by_status),
            selected = groupBy == LibraryGroupBy.STATUS,
            tag = libraryGroupRowTag(LibraryGroupBy.STATUS),
            onClick = {
                scope.launch { session.updatePrefs { it.copy(groupBy = LibraryGroupBy.STATUS) } }
            },
        )
    }
}

@Composable
private fun LibraryGroupRow(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag)
            .padding(vertical = DesignTokens.Spacing.Row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = label,
            style = UiTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        AppText(
            text = if (selected) "●" else "○",
            color = UiTheme.colorScheme.primary,
        )
    }
}

// endregion

// region 分类管理：新增 / 重命名 / 隐藏 / 删除 / 排序

@Composable
fun LibraryCategoriesPage(session: LibrarySession) {
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small)
            .testTag(LibraryCategoriesSheetTag),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
    ) {
        AppText(stringResource(Res.string.library_categories_title), style = UiTheme.typography.titleMedium)
        if (session.snapshot.orderedCategories.isEmpty()) {
            AppText(
                text = stringResource(Res.string.library_category_empty),
                style = UiTheme.typography.bodyMedium,
                color = UiTheme.colorScheme.onSurfaceVariant,
            )
        }
        val ordered = session.snapshot.orderedCategories
        ordered.forEachIndexed { index, category ->
            Column(
                modifier = Modifier.fillMaxWidth().testTag(libraryCategoryRowTag(category.id)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AppText(
                        text = category.name,
                        style = UiTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (category.isDefault) {
                        AppText(
                            text = stringResource(Res.string.library_category_default),
                            style = UiTheme.typography.labelSmall,
                            color = UiTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (category.isHidden) {
                        AppText(
                            text = stringResource(Res.string.library_category_hidden),
                            style = UiTheme.typography.labelSmall,
                            color = UiTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (renaming == category.id) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
                    ) {
                        AppTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag(LibraryCategoryNameTag),
                        )
                        AppTextButton(onClick = {
                            scope.launch {
                                if (session.renameCategory(category.id, renameText)) renaming = null
                            }
                        }) {
                            AppText(stringResource(Res.string.library_save))
                        }
                        AppTextButton(onClick = { renaming = null }) {
                            AppText(stringResource(Res.string.library_cancel))
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppTextButton(
                            onClick = {
                                val ids = ordered.map { it.id }.toMutableList()
                                if (index > 0) {
                                    ids[index] = ids[index - 1].also { ids[index - 1] = ids[index] }
                                    scope.launch { session.setCategoryOrder(ids) }
                                }
                            },
                            enabled = index > 0,
                        ) {
                            AppText("↑")
                        }
                        AppTextButton(
                            onClick = {
                                val ids = ordered.map { it.id }.toMutableList()
                                if (index < ids.size - 1) {
                                    ids[index] = ids[index + 1].also { ids[index + 1] = ids[index] }
                                    scope.launch { session.setCategoryOrder(ids) }
                                }
                            },
                            enabled = index < ordered.size - 1,
                        ) {
                            AppText("↓")
                        }
                        AppTextButton(onClick = {
                            renaming = category.id
                            renameText = category.name
                        }) {
                            AppText(stringResource(Res.string.library_category_rename))
                        }
                        AppTextButton(onClick = {
                            scope.launch { session.setCategoryHidden(category.id, !category.isHidden) }
                        }) {
                            AppText(
                                stringResource(
                                    if (category.isHidden) Res.string.library_category_unhide
                                    else Res.string.library_category_hide,
                                ),
                            )
                        }
                        AppTextButton(onClick = {
                            scope.launch { session.removeCategory(category.id) }
                        }) {
                            AppText(
                                stringResource(Res.string.library_category_delete),
                                color = UiTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
            modifier = Modifier.fillMaxWidth().padding(top = DesignTokens.Spacing.Small),
        ) {
            AppTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { AppText(stringResource(Res.string.library_category_name)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag(LibraryCategoryNameTag),
            )
            AppTextButton(
                onClick = {
                    scope.launch {
                        if (session.addCategory(newName)) newName = ""
                    }
                },
                enabled = newName.isNotBlank(),
                modifier = Modifier.testTag(LibraryCategoryAddTag),
            ) {
                AppText(stringResource(Res.string.library_category_add))
            }
        }
    }
}

// endregion

// region 分类选择器（一作多类：批量改分类与详情收藏共用）

@Composable
fun LibraryCategoryPickerDialog(
    session: LibrarySession,
    initial: Set<Long>,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var checked by remember(initial) { mutableStateOf(initial) }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppText(
                stringResource(Res.string.library_favorite_choose),
                modifier = Modifier.testTag(LibraryCategoryPickerTag),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (session.snapshot.orderedCategories.isEmpty()) {
                    AppText(
                        text = stringResource(Res.string.library_category_empty),
                        style = UiTheme.typography.bodyMedium,
                    )
                }
                session.snapshot.orderedCategories.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                checked = if (checked.contains(category.id)) {
                                    checked - category.id
                                } else {
                                    checked + category.id
                                }
                            }
                            .testTag(libraryPickerRowTag(category.id)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppCheckbox(
                            checked = checked.contains(category.id),
                            onCheckedChange = { on ->
                                checked = if (on) checked + category.id else checked - category.id
                            },
                        )
                        AppText(
                            text = category.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(
                onClick = { onConfirm(checked) },
                modifier = Modifier.testTag(LibraryCategoryPickerConfirmTag),
            ) {
                AppText(stringResource(Res.string.library_action_change_categories))
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                AppText(stringResource(Res.string.library_cancel))
            }
        },
    )
}

// endregion

// region 移除确认

@Composable
fun LibraryRemoveDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(LibraryRemoveDialogTag),
        title = { AppText(stringResource(Res.string.library_remove_title, count)) },
        text = { AppText(stringResource(Res.string.library_remove_body)) },
        confirmButton = {
            AppTextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(LibraryRemoveConfirmTag),
            ) {
                AppText(
                    stringResource(Res.string.library_remove_confirm),
                    color = UiTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) {
                AppText(stringResource(Res.string.library_cancel))
            }
        },
    )
}

// endregion
