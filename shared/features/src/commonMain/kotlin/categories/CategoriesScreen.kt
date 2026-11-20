package app.koyura.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.DesignTokens
import app.koyura.features.library.LibraryCategory
import app.koyura.features.library.LibraryLoadState
import app.koyura.features.library.LibrarySession
import app.koyura.features.library.rememberLibrarySession
import app.koyura.features.resources.Res
import app.koyura.features.resources.categories_add
import app.koyura.features.resources.categories_create_title
import app.koyura.features.resources.categories_delete_body
import app.koyura.features.resources.categories_delete_confirm
import app.koyura.features.resources.categories_delete_title
import app.koyura.features.resources.categories_empty
import app.koyura.features.resources.categories_hide
import app.koyura.features.resources.categories_load_failed
import app.koyura.features.resources.categories_name
import app.koyura.features.resources.categories_rename_title
import app.koyura.features.resources.categories_retry
import app.koyura.features.resources.categories_title
import app.koyura.features.resources.categories_unhide
import app.koyura.features.resources.library_cancel
import app.koyura.features.resources.library_category_default
import app.koyura.features.resources.library_category_hidden
import app.koyura.features.resources.library_save
import app.koyura.features.resources.more_categories
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

// region 测试锚点

const val CategoriesRootTag = "categories-root"
const val CategoriesListTag = "categories-list"
const val CategoriesEmptyTag = "categories-empty"
const val CategoriesLoadingTag = "categories-loading"
const val CategoriesErrorTag = "categories-error"
const val CategoriesRetryTag = "categories-retry"
const val CategoriesAddTag = "categories-add"
const val CategoriesDialogTag = "categories-dialog"
const val CategoriesNameFieldTag = "categories-name-field"
const val CategoriesDialogConfirmTag = "categories-dialog-confirm"
const val CategoriesDeleteConfirmTag = "categories-delete-confirm"

fun categoriesRowTag(id: Long) = "categories-row-$id"
fun categoriesUpTag(id: Long) = "categories-up-$id"
fun categoriesDownTag(id: Long) = "categories-down-$id"
fun categoriesHideTag(id: Long) = "categories-hide-$id"
fun categoriesDeleteTag(id: Long) = "categories-delete-$id"

// endregion

/** 分类页当前打开的对话框（同一时刻只开一个）。 */
private sealed interface CategoriesDialog {
    data object Create : CategoriesDialog
    data class Rename(val category: LibraryCategory) : CategoriesDialog
    data class Delete(val category: LibraryCategory) : CategoriesDialog
}

/**
 * 分类管理独立页（白皮书 07 §7.11，More → 分类）。
 *
 * 行是 56dp 标准行：名称 bodyMedium + 默认/已隐藏标记，行尾上移/下移与隐藏、删除动作；
 * 点行体打开重命名对话框，右下角扩展 FAB 新建，删除走标准 AlertDialog 确认。
 * 排序用行尾按钮调 `setCategoryOrder`（拖拽不做，见交付报告）。
 *
 * 数据直接走 [app.koyura.features.library.LocalLibraryRepository] 提供的真实书架契约：
 * 会话与书架页共用同一仓储，任何一次写入都以仓储提交后的完整快照为准。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    session: LibrarySession = rememberLibrarySession(),
) {
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<CategoriesDialog?>(null) }

    LaunchedEffect(Unit) {
        session.refresh()
    }

    Box(modifier = modifier.fillMaxSize().testTag(CategoriesRootTag)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(Res.string.categories_title)) },
                navigationIcon = {
                    ActionIconButton(
                        icon = ActionIcon.Back,
                        label = stringResource(Res.string.more_categories),
                        onClick = onBack,
                    )
                },
            )
            session.actionError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.ExtraSmall)
                        .testTag("categories-action-error"),
                )
            }
            val ordered = session.snapshot.orderedCategories
            when (val load = session.loadState) {
                LibraryLoadState.Loading -> Box(
                    modifier = Modifier.fillMaxSize().testTag(CategoriesLoadingTag),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is LibraryLoadState.Failed -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(DesignTokens.Spacing.Large)
                        .testTag(CategoriesErrorTag),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
                ) {
                    Text(
                        text = stringResource(Res.string.categories_load_failed, load.message),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = { scope.launch { session.refresh() } },
                        modifier = Modifier.testTag(CategoriesRetryTag),
                    ) {
                        Text(stringResource(Res.string.categories_retry))
                    }
                }
                LibraryLoadState.Unavailable, LibraryLoadState.Ready ->
                    if (ordered.isEmpty()) {
                        CategoriesEmptyState()
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().testTag(CategoriesListTag)) {
                            itemsIndexed(ordered, key = { _, category -> category.id }) { index, category ->
                                CategoryRow(
                                    category = category,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < ordered.lastIndex,
                                    onMove = { offset ->
                                        val ids = ordered.map { it.id }.toMutableList()
                                        val target = index + offset
                                        if (target in ids.indices) {
                                            ids[index] = ids[target].also { ids[target] = ids[index] }
                                            scope.launch { session.setCategoryOrder(ids) }
                                        }
                                    },
                                    onRename = { dialog = CategoriesDialog.Rename(category) },
                                    onToggleHidden = {
                                        scope.launch { session.setCategoryHidden(category.id, !category.isHidden) }
                                    },
                                    onDelete = { dialog = CategoriesDialog.Delete(category) },
                                )
                            }
                        }
                    }
            }
        }
        ExtendedFloatingActionButton(
            text = { Text(stringResource(Res.string.categories_add)) },
            icon = { ActionIcon(icon = ActionIcon.Categories, contentDescription = null) },
            onClick = { dialog = CategoriesDialog.Create },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(DesignTokens.Spacing.Medium)
                .testTag(CategoriesAddTag),
        )
    }

    when (val current = dialog) {
        CategoriesDialog.Create -> CategoryNameDialog(
            title = stringResource(Res.string.categories_create_title),
            initial = "",
            onConfirm = { name ->
                scope.launch {
                    if (session.addCategory(name)) dialog = null
                }
            },
            onDismiss = { dialog = null },
        )
        is CategoriesDialog.Rename -> CategoryNameDialog(
            title = stringResource(Res.string.categories_rename_title),
            initial = current.category.name,
            onConfirm = { name ->
                scope.launch {
                    if (session.renameCategory(current.category.id, name)) dialog = null
                }
            },
            onDismiss = { dialog = null },
        )
        is CategoriesDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            modifier = Modifier.testTag(CategoriesDialogTag),
            title = { Text(stringResource(Res.string.categories_delete_title)) },
            text = {
                Text(stringResource(Res.string.categories_delete_body, current.category.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (session.removeCategory(current.category.id)) dialog = null
                        }
                    },
                    modifier = Modifier.testTag(CategoriesDeleteConfirmTag),
                ) {
                    Text(
                        text = stringResource(Res.string.categories_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) {
                    Text(stringResource(Res.string.library_cancel))
                }
            },
        )
        null -> Unit
    }
}

/** 空态：displayMedium 颜文字（0.78）+ bodyMedium 文案（白皮书 06 §6.8，同 Updates 空态）。 */
@Composable
private fun CategoriesEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DesignTokens.Spacing.Large)
            .testTag(CategoriesEmptyTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "(˘･_･˘)",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = DesignTokens.Alpha.Secondary,
            ),
        )
        Text(
            text = stringResource(Res.string.categories_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = DesignTokens.Alpha.Secondary,
            ),
        )
    }
}

/** 56dp 标准行：名称 + 标记 + 行尾上移/下移/隐藏/删除。 */
@Composable
private fun CategoryRow(
    category: LibraryCategory,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DesignTokens.Sizes.ListItemHeight)
            .clickable(onClick = onRename)
            .padding(
                start = DesignTokens.Spacing.Medium,
                end = DesignTokens.Spacing.ExtraSmall,
                top = DesignTokens.Spacing.ExtraSmall,
                bottom = DesignTokens.Spacing.ExtraSmall,
            )
            .testTag(categoriesRowTag(category.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = DesignTokens.Spacing.Small),
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall)) {
                if (category.isDefault) {
                    Text(
                        text = stringResource(Res.string.library_category_default),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (category.isHidden) {
                    Text(
                        text = stringResource(Res.string.library_category_hidden),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(
            onClick = { onMove(-1) },
            enabled = canMoveUp,
            modifier = Modifier.testTag(categoriesUpTag(category.id)),
        ) {
            Text("↑")
        }
        TextButton(
            onClick = { onMove(1) },
            enabled = canMoveDown,
            modifier = Modifier.testTag(categoriesDownTag(category.id)),
        ) {
            Text("↓")
        }
        ActionIconButton(
            icon = ActionIcon.Incognito,
            label = stringResource(
                if (category.isHidden) Res.string.categories_unhide else Res.string.categories_hide,
            ),
            onClick = onToggleHidden,
            modifier = Modifier.testTag(categoriesHideTag(category.id)),
        )
        ActionIconButton(
            icon = ActionIcon.Delete,
            label = stringResource(Res.string.categories_delete_confirm),
            onClick = onDelete,
            modifier = Modifier.testTag(categoriesDeleteTag(category.id)),
            contentColor = MaterialTheme.colorScheme.error,
        )
    }
}

/** 新建/重命名共用对话框：单个名称输入（写法对齐 LibrarySheets 分类区）。 */
@Composable
private fun CategoryNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(CategoriesDialogTag),
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(Res.string.categories_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(CategoriesNameFieldTag),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag(CategoriesDialogConfirmTag),
            ) {
                Text(stringResource(Res.string.library_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.library_cancel))
            }
        },
    )
}
