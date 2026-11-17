package app.koyura.features.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.koyura.features.resources.Res
import app.koyura.features.resources.library_favorite_add
import app.koyura.features.resources.library_favorite_added
import app.koyura.features.resources.library_favorite_choose


import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

const val LibraryFavoriteButtonTag = "library-favorite-button"
const val LibraryFavoriteCategoriesTag = "library-favorite-categories"

/**
 * 详情页收藏复用件：真实收藏 / 取消收藏 / 选择分类，与书架即时同步。
 *
 * 接线（装配层在 `SourceDetailScreen.MangaHeader` 下插入，见本轨 notes）：
 * ```kotlin
 * LibraryDetailFavoriteRow(
 *     session = librarySession,
 *     entry = NewLibraryEntry(
 *         sourceKey = sourceKey,
 *         workId = workId,
 *         title = detail.title,
 *         coverUrl = detail.cover,
 *         sourceName = sourceKey,
 *         totalChapters = detail.chapters.size,
 *     ),
 * )
 * ```
 * 同一 [LibrarySession] 实例驱动书架页与详情页：收藏后书架页快照同步更新，
 * 取消后书架对应行消失；选择分类经 [LibraryCategoryPickerDialog] 一作多类落盘。
 */
@Composable
fun LibraryDetailFavoriteRow(
    session: LibrarySession,
    entry: NewLibraryEntry,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    val key = LibraryEntryKey(entry.sourceKey, entry.kind, entry.workId)
    val favorite = session.isFavorite(key)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (favorite) {
            Button(
                onClick = {
                    scope.launch { session.setFavorite(entry, false) }
                },
                modifier = Modifier.testTag(LibraryFavoriteButtonTag),
            ) {
                Text(stringResource(Res.string.library_favorite_added))
            }
            OutlinedButton(
                onClick = { picking = true },
                modifier = Modifier.testTag(LibraryFavoriteCategoriesTag),
            ) {
                Text(stringResource(Res.string.library_favorite_choose))
            }
        } else {
            Button(
                onClick = {
                    scope.launch { session.setFavorite(entry, true) }
                },
                modifier = Modifier.testTag(LibraryFavoriteButtonTag),
            ) {
                Text(stringResource(Res.string.library_favorite_add))
            }
        }
    }
    if (picking) {
        LibraryCategoryPickerDialog(
            session = session,
            initial = session.snapshot.categoriesOf(key),
            onConfirm = { ids ->
                scope.launch {
                    if (session.setEntryCategories(key, ids)) picking = false
                }
            },
            onDismiss = { picking = false },
        )
    }
}
