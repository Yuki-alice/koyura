package app.koyura.features.reader.comic.chrome

import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.ActionIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.koyura.designsystem.DesignTokens
import app.koyura.domain.reader.preferences.OverrideField
import app.koyura.domain.reader.preferences.ReaderLayout
import app.koyura.domain.reader.preferences.ReadingMode
import app.koyura.domain.reader.preferences.WorkReaderOverridePatch
import app.koyura.features.reader.comic.ReaderBackTag
import app.koyura.features.reader.comic.ReaderNextChapterTag
import app.koyura.features.reader.comic.ReaderPageIndicatorTag
import app.koyura.features.reader.comic.ReaderPrevChapterTag
import app.koyura.features.reader.comic.viewer.ComicPagerLayout
import app.koyura.features.reader.comic.viewer.ComicSpread
import app.koyura.features.reader.comic.viewer.buildComicSpreads
import app.koyura.features.resources.Res
import app.koyura.features.resources.reader_back
import app.koyura.features.resources.reader_chrome_cancel
import app.koyura.features.resources.reader_chrome_chapters
import app.koyura.features.resources.reader_chrome_crop_borders
import app.koyura.features.resources.reader_chrome_go
import app.koyura.features.resources.reader_chrome_go_to_page
import app.koyura.features.resources.reader_chrome_layout
import app.koyura.features.resources.reader_chrome_no_chapters
import app.koyura.features.resources.reader_chrome_page_number
import app.koyura.features.resources.reader_chrome_page_out_of_range
import app.koyura.features.resources.reader_chrome_page_of
import app.koyura.features.resources.reader_chrome_page_range_of
import app.koyura.features.resources.reader_chrome_reading_mode
import app.koyura.features.resources.reader_chrome_settings
import app.koyura.features.resources.reader_next_chapter
import app.koyura.features.resources.reader_prev_chapter
import app.koyura.features.resources.reader_title
import org.jetbrains.compose.resources.stringResource

/** Test hooks for the chrome (top/bottom bars, slider, jump dialog). */
const val ReaderChromeRootTag = "reader-chrome-root"
const val ReaderChromeTopBarTag = "reader-chrome-top-bar"
const val ReaderChromeBottomBarTag = "reader-chrome-bottom-bar"
const val ReaderChromeHiddenIndicatorTag = "reader-chrome-hidden-indicator"
const val ReaderChromeChaptersTag = "reader-chrome-chapters"
const val ReaderChromeModeTag = "reader-chrome-mode"
const val ReaderChromeSettingsTag = "reader-chrome-settings"
const val ReaderChromeCropTag = "reader-chrome-crop"
const val ReaderChromeLayoutTag = "reader-chrome-layout"
const val ReaderChromeSliderTag = "reader-chrome-slider"
const val ReaderChromeVerticalSliderTag = "reader-chrome-vertical-slider"
const val ReaderChromePageJumpDialogTag = "reader-chrome-page-jump"
const val ReaderChromePageInputTag = "reader-chrome-page-input"
const val ReaderChromePageGoTag = "reader-chrome-page-go"

/**
 * Top/bottom reader chrome: back, titles, 1-based page indicator, prev/next chapter, chapter
 * slider with drag-preview/commit-on-release, and entries to the chapter list, reading-mode,
 * crop and settings dialogs.
 *
 * Everything is driven by [state]; progress I/O, jumps and chapter switches are the host's job.
 * Only one dialog from [ReaderChromeDialog] is open at a time. Closing a dialog restores focus
 * to the button that opened it.
 */
@Composable
fun ReaderChrome(
    state: ReaderChromeState,
    onAction: (ReaderChromeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Focus recovery: remember which invoker opened the dialog, restore on close.
    val chaptersFocus = remember { FocusRequester() }
    val modeFocus = remember { FocusRequester() }
    val settingsFocus = remember { FocusRequester() }
    val indicatorFocus = remember { FocusRequester() }
    var lastInvoker by remember { mutableStateOf<FocusRequester?>(null) }

    fun openDialog(dialog: ReaderChromeDialog, invoker: FocusRequester) {
        lastInvoker = invoker
        onAction(ReaderChromeAction.SetDialog(dialog))
    }

    // Komikku ReaderAppBars parity: surface@3dp, 亮 0.95 / 暗 0.90（白皮书 02 §2.7）。
    val readerBarAlpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        DesignTokens.Alpha.ReaderBarDark
    } else {
        DesignTokens.Alpha.ReaderBarLight
    }

    LaunchedEffect(state.dialog) {
        if (state.dialog == ReaderChromeDialog.None) {
            lastInvoker?.requestFocus()
            lastInvoker = null
        }
    }

    // The root stays composed while hidden so the bars can play Komikku's exit slide+fade and
    // the hidden page indicator stays observable (old contract returned early here; the app-track
    // ChromeBehaviorTest.hiddenMenuRendersNothing expectation needs updating — see track notes).
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .testTag(ReaderChromeRootTag),
    ) {
        // Dialog placement follows the app window, not the dialog window (Komikku breakpoint).
        val compactDialog = isCompactDialog(minOf(maxWidth, maxHeight).value.toInt())
        // Spread-aware slider labels (Komikku ChapterNavigator "4-5 / 190"): rebuild the pager
        // spreads the same way the viewer does, minus runtime wide-page splits (unknown here).
        val isLandscape = maxWidth > maxHeight
        val isRtl = state.effective.mode == ReadingMode.RIGHT_TO_LEFT
        val isStrip = state.effective.mode == ReadingMode.WEBTOON ||
            state.effective.mode == ReadingMode.CONTINUOUS_VERTICAL
        val pagerSpreads = remember(
            state.pageCount,
            state.effective.layout,
            state.effective.firstPageSingle,
            isLandscape,
        ) {
            buildComicSpreads(
                pageCount = state.pageCount,
                layout = ComicPagerLayout.fromId(state.effective.layout.name),
                isLandscape = isLandscape,
                firstPageSingle = state.effective.firstPageSingle,
            )
        }
        // Komikku parity (ReaderAppBars): bars slide vertically over 200ms while fading over
        // 150ms. The top bar moves through the top edge, the bottom bar through the bottom edge.
        AnimatedVisibility(
            visible = state.menuVisible,
            enter = slideInVertically(animationSpec = tween(DesignTokens.Motion.ReaderBarsSlideMs)) { -it } +
                fadeIn(animationSpec = tween(DesignTokens.Motion.ReaderBarsFadeMs)),
            exit = slideOutVertically(animationSpec = tween(DesignTokens.Motion.ReaderBarsSlideMs)) { -it } +
                fadeOut(animationSpec = tween(DesignTokens.Motion.ReaderBarsFadeMs)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .testTag(ReaderChromeTopBarTag),
        ) {
        // Top bar: back, work title + chapter subtitle, 1-based indicator (tap = jump dialog).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = readerBarAlpha))
                .safeDrawingPadding()
                .padding(horizontal = DesignTokens.Spacing.ExtraSmall, vertical = DesignTokens.Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIconButton(
                icon = ActionIcon.Back,
                label = stringResource(Res.string.reader_back),
                onClick = { onAction(ReaderChromeAction.Back) },
                modifier = Modifier.testTag(ReaderBackTag),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.workTitle.ifEmpty { stringResource(Res.string.reader_title) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                val chapterTitle = state.chapters.getOrNull(state.currentChapterIndex)?.title.orEmpty()
                if (chapterTitle.isNotEmpty()) {
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = pageLabel(state.pageIndex, state.pageCount),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .testTag(ReaderPageIndicatorTag)
                    .focusRequester(indicatorFocus)
                    .clickable(enabled = state.pageCount > 1) {
                        openDialog(ReaderChromeDialog.PageJump, indicatorFocus)
                    }
                    .padding(end = 12.dp),
            )
        }
        }
        // Hidden state (Komikku ReaderActivity): only the centered page indicator stays, so the
        // reader keeps position context while the canvas is fully visible. Taps still toggle the
        // menu through the viewer tap zones; scrolling never re-shows it.
        AnimatedVisibility(
            visible = !state.menuVisible,
            enter = fadeIn(animationSpec = tween(DesignTokens.Motion.ReaderBarsFadeMs)),
            exit = fadeOut(animationSpec = tween(DesignTokens.Motion.ReaderBarsFadeMs)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = readerBarAlpha),
                modifier = Modifier.testTag(ReaderChromeHiddenIndicatorTag),
            ) {
                Text(
                    text = pageLabel(state.pageIndex, state.pageCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        // Bottom block: chapter navigator row, then action row.
        AnimatedVisibility(
            visible = state.menuVisible,
            enter = slideInVertically(animationSpec = tween(DesignTokens.Motion.ReaderBarsSlideMs)) { it } +
                fadeIn(animationSpec = tween(DesignTokens.Motion.ReaderBarsFadeMs)),
            exit = slideOutVertically(animationSpec = tween(DesignTokens.Motion.ReaderBarsSlideMs)) { it } +
                fadeOut(animationSpec = tween(DesignTokens.Motion.ReaderBarsFadeMs)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .testTag(ReaderChromeBottomBarTag),
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = readerBarAlpha))
                .safeDrawingPadding()
                .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
            ) {
                ActionIconButton(
                    icon = ActionIcon.Previous,
                    label = stringResource(Res.string.reader_prev_chapter),
                    onClick = { prevChapterId(state)?.let { onAction(ReaderChromeAction.OpenChapter(it)) } },
                    enabled = state.hasPrevChapter,
                    modifier = Modifier.testTag(ReaderPrevChapterTag),
                )
                if (state.pageCount > 1) {
                    PageSlider(
                        pageIndex = state.pageIndex,
                        pageCount = state.pageCount,
                        onJump = { onAction(ReaderChromeAction.JumpToPage(it)) },
                        modifier = Modifier.weight(1f),
                        isRtl = isRtl,
                        spreadEndFor = { spreadEndForPage(pagerSpreads, it) },
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                ActionIconButton(
                    icon = ActionIcon.Next,
                    label = stringResource(Res.string.reader_next_chapter),
                    onClick = { nextChapterId(state)?.let { onAction(ReaderChromeAction.OpenChapter(it)) } },
                    enabled = state.hasNextChapter,
                    modifier = Modifier.testTag(ReaderNextChapterTag),
                )
            }
            Spacer(Modifier.height(DesignTokens.Spacing.ExtraSmall))
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ActionIconButton(
                    icon = ActionIcon.Chapters,
                    label = stringResource(Res.string.reader_chrome_chapters),
                    onClick = { openDialog(ReaderChromeDialog.ChapterList, chaptersFocus) },
                    modifier = Modifier
                        .testTag(ReaderChromeChaptersTag)
                        .focusRequester(chaptersFocus),
                )
                ActionIconButton(
                    icon = modeIcon(state.effective.mode),
                    label = stringResource(Res.string.reader_chrome_reading_mode),
                    onClick = { openDialog(ReaderChromeDialog.ReadingMode, modeFocus) },
                    modifier = Modifier
                        .testTag(ReaderChromeModeTag)
                        .focusRequester(modeFocus),
                )
                // Layout switcher (Komikku bottom bar shows it on pagers only): cycles the
                // effective layout the same way the crop button flips the crop override.
                if (!isStrip) {
                    ActionIconButton(
                        icon = ActionIcon.Layout,
                        label = stringResource(Res.string.reader_chrome_layout),
                        onClick = {
                            onAction(
                                ReaderChromeAction.ChangeWorkOverride(
                                    WorkReaderOverridePatch(
                                        layout = OverrideField.Set(nextReaderLayout(state.effective.layout)),
                                    ),
                                ),
                            )
                        },
                        modifier = Modifier.testTag(ReaderChromeLayoutTag),
                    )
                }
                ActionIconButton(
                    icon = if (state.effective.cropBorders) ActionIcon.Crop else ActionIcon.CropOff,
                    label = stringResource(Res.string.reader_chrome_crop_borders),
                    onClick = {
                        onAction(
                            ReaderChromeAction.ChangeWorkOverride(
                                WorkReaderOverridePatch(
                                    cropBorders = OverrideField.Set(!state.effective.cropBorders),
                                ),
                            ),
                        )
                    },
                    modifier = Modifier.testTag(ReaderChromeCropTag),
                )
                ActionIconButton(
                    icon = ActionIcon.Settings,
                    label = stringResource(Res.string.reader_chrome_settings),
                    onClick = { openDialog(ReaderChromeDialog.Settings, settingsFocus) },
                    modifier = Modifier
                        .testTag(ReaderChromeSettingsTag)
                        .focusRequester(settingsFocus),
                )
            }
        }
        }
        // Vertical strip slider (Komikku NavBarType.VerticalLeft/Right): a slim trailing rail
        // for WEBTOON/CONTINUOUS_VERTICAL that drives the same jump as the bottom slider.
        AnimatedVisibility(
            visible = state.menuVisible && isStrip && state.pageCount > 1,
            enter = fadeIn(animationSpec = tween(DesignTokens.Motion.ReaderBarsFadeMs)),
            exit = fadeOut(animationSpec = tween(DesignTokens.Motion.ReaderBarsFadeMs)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            // 自适应竖向轨（原版 ChapterNavigator）：整条高度随画布，宽度由内容决定。
            VerticalPageSlider(
                pageIndex = state.pageIndex,
                pageCount = state.pageCount,
                onJump = { onAction(ReaderChromeAction.JumpToPage(it)) },
                modifier = Modifier.fillMaxHeight(),
            )
        }

    when (state.dialog) {
        ReaderChromeDialog.ChapterList -> ChapterListDialog(
            state = state,
            onPick = {
                onAction(ReaderChromeAction.OpenChapter(it))
                onAction(ReaderChromeAction.SetDialog(ReaderChromeDialog.None))
            },
            onDismiss = { onAction(ReaderChromeAction.SetDialog(ReaderChromeDialog.None)) },
        )
        ReaderChromeDialog.PageJump -> PageJumpDialog(
            pageCount = state.pageCount,
            onJump = {
                onAction(ReaderChromeAction.JumpToPage(it))
                onAction(ReaderChromeAction.SetDialog(ReaderChromeDialog.None))
            },
            onDismiss = { onAction(ReaderChromeAction.SetDialog(ReaderChromeDialog.None)) },
        )
        ReaderChromeDialog.ReadingMode -> ReadingModeDialog(
            current = state.override?.mode,
            allowDefault = true,
            compact = compactDialog,
            onApply = {
                onAction(
                    ReaderChromeAction.ChangeWorkOverride(
                        WorkReaderOverridePatch(
                            mode = it?.let { mode -> OverrideField.Set(mode) } ?: OverrideField.Clear,
                        ),
                    ),
                )
                onAction(ReaderChromeAction.SetDialog(ReaderChromeDialog.None))
            },
            onDismiss = { onAction(ReaderChromeAction.SetDialog(ReaderChromeDialog.None)) },
        )
        ReaderChromeDialog.Settings -> ReaderSettingsDialog(
            state = state,
            onAction = onAction,
            onDismiss = { onAction(ReaderChromeAction.SetDialog(ReaderChromeDialog.None)) },
        )
        ReaderChromeDialog.None -> Unit
        }
    }
}

@Composable
private fun pageLabel(pageIndex: Int, pageCount: Int): String =
    if (pageCount > 0) {
        stringResource(Res.string.reader_chrome_page_of, pageIndex + 1, pageCount)
    } else {
        "0 / 0"
    }

internal fun prevChapterId(state: ReaderChromeState): String? {
    val index = state.currentChapterIndex
    if (index <= 0) return null
    return state.chapters.getOrNull(index - 1)?.id
}

internal fun nextChapterId(state: ReaderChromeState): String? {
    val index = state.currentChapterIndex
    if (index < 0 || index + 1 >= state.chapters.size) return null
    return state.chapters.getOrNull(index + 1)?.id
}

/**
 * Second page of the spread holding [pageIndex] (0-based), or null for solo spreads and
 * out-of-range pages. Powers the double-page "4-5 / 190" navigator text (Komikku ChapterNavigator).
 */
fun spreadEndForPage(spreads: List<ComicSpread>, pageIndex: Int): Int? {
    val spread = spreads.firstOrNull { pageIndex in it.pageIndices } ?: return null
    if (spread.pageIndices.size < 2) return null
    return spread.pageIndices.firstOrNull { it != pageIndex }
}

/**
 * 1-based range prefix for the navigator text: "4-5" for spreads, "4" for solo pages.
 * Pure so the mapping stays unit-testable; the "/ total" suffix is applied by the composables.
 */
fun spreadRangePrefix(pageIndex: Int, spreadEnd: Int?): String =
    if (spreadEnd == null || spreadEnd == pageIndex) "${pageIndex + 1}"
    else "${pageIndex + 1}-${spreadEnd + 1}"

private fun modeIcon(mode: ReadingMode): ActionIcon = when (mode) {
    ReadingMode.LEFT_TO_RIGHT -> ActionIcon.ModeLeftToRight
    ReadingMode.RIGHT_TO_LEFT -> ActionIcon.ModeRightToLeft
    ReadingMode.VERTICAL -> ActionIcon.ModeVertical
    ReadingMode.CONTINUOUS_VERTICAL -> ActionIcon.ModeContinuous
    ReadingMode.WEBTOON -> ActionIcon.ModeWebtoon
}

/** Layout cycle for the pager-only switcher: SINGLE -> DOUBLE -> AUTO -> SINGLE. */
fun nextReaderLayout(current: ReaderLayout): ReaderLayout = when (current) {
    ReaderLayout.SINGLE_PAGE -> ReaderLayout.DOUBLE_PAGES
    ReaderLayout.DOUBLE_PAGES -> ReaderLayout.AUTOMATIC
    ReaderLayout.AUTOMATIC -> ReaderLayout.SINGLE_PAGE
}

/**
 * Chapter slider: dragging only previews the 1-based page, [onJump] fires once on release and
 * only when the page actually changed. RTL mirrors the drag direction (Komikku ChapterNavigator
 * LayoutDirection mirror); double-page spreads preview "4-5" instead of "4"; every drag tick
 * plays a text-handle haptic (Komikku drag feedback).
 */
@Composable
private fun PageSlider(
    pageIndex: Int,
    pageCount: Int,
    onJump: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isRtl: Boolean = false,
    spreadEndFor: (Int) -> Int? = { null },
) {
    var preview by remember(pageIndex, pageCount) { mutableStateOf(pageIndex) }
    val haptic = LocalHapticFeedback.current
    val direction = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column(modifier) {
            Slider(
                value = (preview + 1).toFloat(),
                onValueChange = {
                    preview = (it.toInt() - 1).coerceIn(0, pageCount - 1)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onValueChangeFinished = {
                    if (preview != pageIndex) onJump(preview)
                },
                valueRange = 1f..pageCount.toFloat(),
                steps = (pageCount - 2).coerceAtLeast(0),
                modifier = Modifier.testTag(ReaderChromeSliderTag),
            )
            Text(
                text = pageRangeLabel(preview, spreadEndFor(preview), pageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun pageRangeLabel(pageIndex: Int, spreadEnd: Int?, pageCount: Int): String =
    if (spreadEnd == null || spreadEnd == pageIndex || pageCount <= 0) {
        pageLabel(pageIndex, pageCount)
    } else {
        stringResource(
            Res.string.reader_chrome_page_range_of,
            spreadRangePrefix(pageIndex, spreadEnd),
            pageCount,
        )
    }

/** Page-number jump dialog with range validation; invalid input never emits. */
@Composable
private fun PageJumpDialog(
    pageCount: Int,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val parsed = input.toIntOrNull()
    val valid = parsed != null && parsed in 1..pageCount
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .testTag(ReaderChromePageJumpDialogTag)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .padding(DesignTokens.Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.reader_chrome_go_to_page),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(6) },
                label = { Text(stringResource(Res.string.reader_chrome_page_number)) },
                singleLine = true,
                isError = input.isNotEmpty() && !valid,
                supportingText = {
                    Text(
                        stringResource(
                            Res.string.reader_chrome_page_out_of_range,
                            1,
                            pageCount,
                        ),
                    )
                },
                modifier = Modifier.testTag(ReaderChromePageInputTag).fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.reader_chrome_cancel))
                }
                Button(
                    onClick = { if (valid) onJump(parsed!! - 1) },
                    enabled = valid,
                    modifier = Modifier.testTag(ReaderChromePageGoTag),
                ) {
                    Text(stringResource(Res.string.reader_chrome_go))
                }
            }
        }
    }
}

/** Small helper so tests and hosts share the chapter-boundary math. */
fun ReaderChromeState.clampedPage(index: Int): Int =
    index.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
