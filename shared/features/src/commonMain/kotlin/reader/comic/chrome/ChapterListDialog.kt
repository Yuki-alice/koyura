package app.koyura.features.reader.comic.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.koyura.features.resources.Res
import app.koyura.features.resources.reader_chrome_chapters
import app.koyura.features.resources.reader_chrome_no_chapters
import org.jetbrains.compose.resources.stringResource

/** Test hooks for the chapter list dialog. */
const val ReaderChromeChapterDialogTag = "reader-chrome-dialog-chapters"
const val ReaderChromeChapterListTag = "reader-chrome-chapter-list"
const val ReaderChromeChapterCurrentTag = "reader-chrome-chapter-current"
const val ReaderChromeNoChaptersTag = "reader-chrome-no-chapters"
fun readerChromeChapterTag(index: Int) = "reader-chrome-chapter-$index"

/**
 * Chapter list: scrolls to the current chapter on open, highlights it, and emits [OpenChapter]
 * for the picked chapter. Empty works show a placeholder instead of a list.
 */
@Composable
fun ChapterListDialog(
    state: ReaderChromeState,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .testTag(ReaderChromeChapterDialogTag)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.reader_chrome_chapters),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            if (state.chapters.isEmpty()) {
                Text(
                    text = stringResource(Res.string.reader_chrome_no_chapters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .testTag(ReaderChromeNoChaptersTag)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                val current = state.currentChapterIndex.coerceAtLeast(0)
                LazyColumn(
                    state = rememberLazyListState(initialFirstVisibleItemIndex = current),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.testTag(ReaderChromeChapterListTag),
                ) {
                    itemsIndexed(state.chapters, key = { index, chapter -> chapter.id ?: "index-$index" }) { index, chapter ->
                        val selected = index == state.currentChapterIndex
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = chapter.title.ifEmpty { "–" },
                                    maxLines = 1,
                                )
                            },
                            colors = if (selected) {
                                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            } else {
                                ListItemDefaults.colors()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(
                                    if (selected) ReaderChromeChapterCurrentTag
                                    else readerChromeChapterTag(index),
                                )
                                .clickable { onPick(chapter.id) },
                        )
                    }
                }
            }
        }
    }
}
