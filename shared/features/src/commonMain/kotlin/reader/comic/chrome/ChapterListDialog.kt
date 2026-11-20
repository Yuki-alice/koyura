package app.koyura.features.reader.comic.chrome

import app.koyura.designsystem.DesignTokens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import app.koyura.designsystem.components.AppListItem
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.UiTheme
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
fun readerChromeChapterPositionTag(index: Int) = "reader-chrome-chapter-position-$index"

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
                .background(UiTheme.colorScheme.surface, UiTheme.shapes.large)
                .padding(vertical = DesignTokens.Spacing.Small),
        ) {
            AppText(
                text = stringResource(Res.string.reader_chrome_chapters),
                style = UiTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = DesignTokens.Spacing.Large, vertical = DesignTokens.Spacing.Regular),
            )
            if (state.chapters.isEmpty()) {
                AppText(
                    text = stringResource(Res.string.reader_chrome_no_chapters),
                    style = UiTheme.typography.bodyMedium,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .testTag(ReaderChromeNoChaptersTag)
                        .padding(horizontal = DesignTokens.Spacing.Large, vertical = DesignTokens.Spacing.Medium),
                )
            } else {
                val current = state.currentChapterIndex.coerceAtLeast(0)
                LazyColumn(
                    state = rememberLazyListState(initialFirstVisibleItemIndex = current),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Tight),
                    modifier = Modifier.testTag(ReaderChromeChapterListTag),
                ) {
                    itemsIndexed(state.chapters, key = { index, chapter -> chapter.id ?: "index-$index" }) { index, chapter ->
                        val selected = index == state.currentChapterIndex
                        AppListItem(
                            headlineContent = {
                                AppText(
                                    text = chapter.title.ifEmpty { "–" },
                                    maxLines = 1,
                                )
                            },
                            // Position within the work ("3 / 12"): the domain chapter model
                            // carries no date/scanlator/download state yet (reader track notes),
                            // so the list shows order + current highlight only.
                            trailingContent = {
                                AppText(
                                    text = "${index + 1} / ${state.chapters.size}",
                                    style = UiTheme.typography.bodySmall,
                                    color = UiTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag(readerChromeChapterPositionTag(index)),
                                )
                            },
                            containerColor = if (selected) UiTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Unspecified,
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
