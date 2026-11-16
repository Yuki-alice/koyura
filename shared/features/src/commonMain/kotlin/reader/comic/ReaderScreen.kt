package app.koyura.features.reader.comic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.koyura.domain.reader.ChapterContent
import app.koyura.features.resources.Res
import app.koyura.features.resources.reader_empty_pages
import app.koyura.features.resources.reader_load_failed
import app.koyura.features.resources.reader_next_chapter
import app.koyura.features.resources.reader_page_failed
import app.koyura.features.resources.reader_page_indicator
import app.koyura.features.resources.reader_prev_chapter
import app.koyura.features.resources.reader_retry
import app.koyura.features.resources.reader_title
import app.koyura.features.resources.reader_back
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Stable UI test hooks, shared with the app production-chain tests. */
const val ReaderRootTestTag = "reader-root"
const val ReaderBackTag = "reader-back"
const val ReaderPageIndicatorTag = "reader-page-indicator"
const val ReaderPrevChapterTag = "reader-prev-chapter"
const val ReaderNextChapterTag = "reader-next-chapter"
const val ReaderTapPrevTag = "reader-tap-prev"
const val ReaderTapNextTag = "reader-tap-next"
const val ReaderTapMenuTag = "reader-tap-menu"

fun readerPageTag(index: Int) = "reader-page-$index"
fun readerPageRetryTag(pageKey: String) = "reader-page-retry-$pageKey"

/**
 * Fullscreen horizontal comic reader.
 *
 * Pages load on demand through [ReaderSession]; only pages that actually scroll into view are
 * requested, failed pages retry individually, and stale results are dropped by the session. The
 * last settled page is persisted, and leaving through the back control persists the page on
 * screen, so a rapid fling followed by an immediate exit still keeps the final page.
 */
@Composable
fun ComicReaderScreen(
    session: ReaderSession,
    sourceKey: String,
    workId: String,
    chapterId: String?,
    title: String,
    chapterIds: List<String>,
    chapterTitles: List<String>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var activeChapterId by rememberSaveable(sourceKey, workId, chapterId) { mutableStateOf(chapterId) }

    LaunchedEffect(sourceKey, workId, chapterId) {
        session.open(sourceKey, workId, chapterId, chapterIds, title, chapterTitles)
    }

    var menuVisible by rememberSaveable { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(ReaderRootTestTag),
    ) {

        when (val state = session.content) {
            ReaderContentState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is ReaderContentState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.reader_load_failed, state.message),
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = {
                    scope.launch { session.open(sourceKey, workId, activeChapterId, chapterIds, title, chapterTitles) }
                }) {
                    Text(stringResource(Res.string.reader_retry))
                }
            }
            is ReaderContentState.Ready -> {
                val content = state.content
                if (content.pageKeys.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.reader_empty_pages))
                    }
                } else {
                    // Rebuilt per chapter open so page state never leaks across chapters.
                    key(session.contentSeq) {
                        ReaderPager(
                            session = session,
                            content = content,
                            onToggleMenu = { menuVisible = !menuVisible },
                        )
                    }
                }
                ReaderChrome(
                    title = title.ifEmpty { content.title },
                    chapterTitle = chapterTitleFor(activeChapterId, chapterIds, chapterTitles),
                    pageText = stringResource(
                        Res.string.reader_page_indicator,
                        session.currentPage + 1,
                        content.pageKeys.size,
                    ),
                    menuVisible = menuVisible,
                    onBack = {
                        scope.launch {
                            session.saveCurrent()
                            onBack()
                        }
                    },
                    onPrevChapter = session.previousChapterId(activeChapterId)?.let { prevId ->
                        {
                            activeChapterId = prevId
                            scope.launch { session.open(sourceKey, workId, prevId, chapterIds, title, chapterTitles) }
                        }
                    },
                    onNextChapter = session.nextChapterId(activeChapterId)?.let { nextId ->
                        {
                            activeChapterId = nextId
                            scope.launch { session.open(sourceKey, workId, nextId, chapterIds, title, chapterTitles) }
                        }
                    },
                )
            }
        }
    }
}

private fun chapterTitleFor(
    chapterId: String?,
    chapterIds: List<String>,
    chapterTitles: List<String>,
): String {
    if (chapterId == null) return ""
    val index = chapterIds.indexOf(chapterId)
    return chapterTitles.getOrNull(index).orEmpty()
}

@Composable
private fun ReaderPager(
    session: ReaderSession,
    content: ChapterContent,
    onToggleMenu: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = content.startPageIndex) { content.pageKeys.size }
    val focusRequester = remember { FocusRequester() }

    fun turn(delta: Int) {
        scope.launch {
            val target = (pagerState.currentPage + delta).coerceIn(0, content.pageKeys.size - 1)
            if (target != pagerState.currentPage) {
                pagerState.animateScrollToPage(target)
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect(session::markDisplayed)
    }

    // Load the active page and its neighbours without delaying the recorded position.
    LaunchedEffect(pagerState, content.pageKeys) {
        snapshotFlow { pagerState.currentPage }.collect { index ->
            session.ensurePage(content.pageKeys[index])
            if (index > 0) session.ensurePage(content.pageKeys[index - 1])
            if (index + 1 < content.pageKeys.size) session.ensurePage(content.pageKeys[index + 1])
        }
    }

    // Write settled positions, including page one when opening a new chapter.
    LaunchedEffect(pagerState, content.pageKeys) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { index ->
            session.savePage(index)
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.PageDown -> {
                        turn(1)
                        true
                    }
                    Key.DirectionLeft, Key.PageUp -> {
                        turn(-1)
                        true
                    }
                    else -> false
                }
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
        ) { index ->
            val pageKey = content.pageKeys[index]
            ReaderPage(
                index = index,
                pageKey = pageKey,
                state = session.pages[pageKey],
                onRetry = { scope.launch { session.retryPage(pageKey) } },
            )
        }

        // Tap zones sit above the pager but below the chrome: edges turn pages, the centre
        // toggles the controls.
        Row(Modifier.fillMaxSize()) {
            val tapModifier = Modifier.weight(1f).fillMaxSize()
            val interaction = remember { MutableInteractionSource() }
            Box(
                tapModifier
                    .clickable(interaction, null, onClick = { turn(-1) })
                    .testTag(ReaderTapPrevTag),
            )
            Box(
                tapModifier
                    .clickable(interaction, null, onClick = onToggleMenu)
                    .testTag(ReaderTapMenuTag),
            )
            Box(
                tapModifier
                    .clickable(interaction, null, onClick = { turn(1) })
                    .testTag(ReaderTapNextTag),
            )
        }
    }
}

@Composable
private fun ReaderPage(
    index: Int,
    pageKey: String,
    state: ReaderPageState?,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().testTag(readerPageTag(index)),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            null, ReaderPageState.Loading -> CircularProgressIndicator()
            is ReaderPageState.Ready -> {
                val bitmap = remember(state.bytes) {
                    runCatching { state.bytes.decodeToImageBitmap() }.getOrNull()
                }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = "page image",
                        modifier = Modifier.fillMaxSize().testTag("reader-page-image-$index"),
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(Res.string.reader_page_failed),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.testTag(readerPageRetryTag(pageKey) + "-decode"),
                        ) {
                            Text(stringResource(Res.string.reader_retry))
                        }
                    }
                }
            }
            is ReaderPageState.Failed -> Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.reader_page_failed),
                    color = MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier.testTag(readerPageRetryTag(pageKey)),
                ) {
                    Text(stringResource(Res.string.reader_retry))
                }
            }
        }
    }
}

@Composable
private fun ReaderChrome(
    title: String,
    chapterTitle: String,
    pageText: String,
    menuVisible: Boolean,
    onBack: () -> Unit,
    onPrevChapter: (() -> Unit)?,
    onNextChapter: (() -> Unit)?,
) {
    if (!menuVisible) return
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .safeDrawingPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, modifier = Modifier.testTag(ReaderBackTag)) {
                Text(stringResource(Res.string.reader_back))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title.ifEmpty { stringResource(Res.string.reader_title) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
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
                text = pageText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(ReaderPageIndicatorTag).padding(end = 12.dp),
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { onPrevChapter?.invoke() },
                enabled = onPrevChapter != null,
                modifier = Modifier.testTag(ReaderPrevChapterTag),
            ) { Text(stringResource(Res.string.reader_prev_chapter)) }
            TextButton(
                onClick = { onNextChapter?.invoke() },
                enabled = onNextChapter != null,
                modifier = Modifier.testTag(ReaderNextChapterTag),
            ) { Text(stringResource(Res.string.reader_next_chapter)) }
        }
    }
}
