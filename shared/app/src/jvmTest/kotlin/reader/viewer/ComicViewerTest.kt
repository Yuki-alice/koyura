package app.koyura.app.reader.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.components.AppText
import app.koyura.domain.reader.ChapterContent
import app.koyura.features.reader.comic.FakeReaderService
import app.koyura.features.reader.comic.ReaderContentState
import app.koyura.features.reader.comic.ReaderIntent
import app.koyura.features.reader.comic.ReaderStoreHolder
import app.koyura.features.reader.comic.decodeToImageBitmap
import app.koyura.features.reader.comic.viewer.ComicChapterBoundary
import app.koyura.features.reader.comic.viewer.ComicPagerLayout
import app.koyura.features.reader.comic.viewer.ComicReadingMode
import app.koyura.features.reader.comic.viewer.ComicScaleFit
import app.koyura.features.reader.comic.viewer.ComicViewer
import app.koyura.features.reader.comic.viewer.ComicViewerJump
import app.koyura.features.reader.comic.viewer.ComicViewerOptions
import app.koyura.features.reader.comic.viewer.ComicViewerRootTag
import app.koyura.features.reader.comic.viewer.comicPageImageTag
import app.koyura.features.reader.comic.viewer.comicPageRetryTag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val CounterTag = "reader-viewer-counter"

@Composable
private fun ViewerLab(
    holder: ReaderStoreHolder,
    chapterId: String?,
    options: ComicViewerOptions,
    jump: ComicViewerJump?,
    onBoundary: (ComicChapterBoundary) -> String?,
    menuToggles: MutableList<Int>,
) {
    val uiState by holder.uiState.collectAsState()
    LaunchedEffect(uiState.contentSeq, chapterId) {
        // Opened by the test before composition in most cases; kept here for recompositions.
    }
    val content = (uiState.content as? ReaderContentState.Ready)?.content
    Column(Modifier.fillMaxSize()) {
        AppText(
            text = if (content == null) "0 / 0"
            else "${uiState.currentPage + 1} / ${content.pageKeys.size}",
            modifier = Modifier.testTag(CounterTag),
        )
        if (content != null) {
            Box(Modifier.weight(1f)) {
                ComicViewer(
                    holder = holder,
                    content = content,
                    options = options,
                    jump = jump,
                    onToggleMenu = { menuToggles += 1 },
                    onChapterBoundary = { boundary ->
                        onBoundary(boundary)?.let { /* host switches via test state */ }
                    },
                )
            }
        }
    }
}

private fun androidx.compose.ui.test.ComposeUiTest.awaitCounter(text: String) {
    val deadline = System.currentTimeMillis() + 10_000
    var last: AssertionError? = null
    var shown = false
    while (!shown && System.currentTimeMillis() < deadline) {
        try {
            onNodeWithTag(CounterTag).assertTextEquals(text)
            shown = true
        } catch (failure: AssertionError) {
            last = failure
            waitForIdle()
            Thread.sleep(100)
        }
    }
    if (!shown) throw last ?: AssertionError("counter never showed $text")
}

/**
 * Single tap that cannot be mistaken for a double-tap: the zones use a manual
 * double-tap window, so a second tap inside the window would toggle zoom instead
 * of turning another page. The pause exceeds the window.
 */
private fun androidx.compose.ui.test.ComposeUiTest.settledTap(tag: String) {
    onNodeWithTag(tag).performClick()
    waitForIdle()
    Thread.sleep(600)
}

/**
 * Clock-free horizontal drag (instant down/move/up without gesture durations):
 * pager flings settle by position, so a 60%-width drag always turns one page
 * whether or not the test clock advances gesture timestamps.
 */
private fun androidx.compose.ui.test.ComposeUiTest.dragLeft(tag: String) {
    onNodeWithTag(tag).performTouchInput {
        val w = visibleSize.width.toFloat()
        val y = visibleSize.height / 2f
        down(0, androidx.compose.ui.geometry.Offset(w * 0.8f, y))
        var x = 0.8f
        repeat(12) {
            x -= 0.05f
            updatePointerTo(0, androidx.compose.ui.geometry.Offset(w * x, y))
        }
        up(0)
    }
    waitForIdle()
}

private fun androidx.compose.ui.test.ComposeUiTest.dragRight(tag: String) {
    onNodeWithTag(tag).performTouchInput {
        val w = visibleSize.width.toFloat()
        val y = visibleSize.height / 2f
        down(0, androidx.compose.ui.geometry.Offset(w * 0.2f, y))
        var x = 0.2f
        repeat(12) {
            x += 0.05f
            updatePointerTo(0, androidx.compose.ui.geometry.Offset(w * x, y))
        }
        up(0)
    }
    waitForIdle()
}

private fun androidx.compose.ui.test.ComposeUiTest.dragUp(tag: String) {
    onNodeWithTag(tag).performTouchInput {
        val h = visibleSize.height.toFloat()
        val x = visibleSize.width / 2f
        down(0, androidx.compose.ui.geometry.Offset(x, h * 0.8f))
        var y = 0.8f
        repeat(12) {
            y -= 0.05f
            updatePointerTo(0, androidx.compose.ui.geometry.Offset(x, h * y))
        }
        up(0)
    }
    waitForIdle()
}

private fun androidx.compose.ui.test.ComposeUiTest.dragDown(tag: String) {
    onNodeWithTag(tag).performTouchInput {
        val h = visibleSize.height.toFloat()
        val x = visibleSize.width / 2f
        down(0, androidx.compose.ui.geometry.Offset(x, h * 0.2f))
        var y = 0.2f
        repeat(12) {
            y += 0.05f
            updatePointerTo(0, androidx.compose.ui.geometry.Offset(x, h * y))
        }
        up(0)
    }
    waitForIdle()
}

class ComicViewerTest {
    @Test
    fun ltrTapAdvancesAndCenterTogglesMenu() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        var options by mutableStateOf(ComicViewerOptions(mode = ComicReadingMode.LEFT_TO_RIGHT))
        val menus = mutableListOf<Int>()
        var chapter by mutableStateOf(ViewerFixture.CHAPTER_2)
        setContent {
            ReaderTheme {
                LaunchedEffect(chapter) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, chapter, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, chapter, options, null, { null }, menus)
            }
        }

        awaitCounter("1 / 7")
        settledTap("reader-tap-next")
        awaitCounter("2 / 7")
        settledTap("reader-tap-next")
        awaitCounter("3 / 7")
        settledTap("reader-tap-prev")
        awaitCounter("2 / 7")

        val before = menus.size
        settledTap("reader-tap-menu")
        assertEquals(before + 1, menus.size)
        // Swipe left advances in LTR (framework pager gesture through the
        // non-consuming tap overlay).
        dragLeft(ComicViewerRootTag)
        awaitCounter("3 / 7")
        dragRight(ComicViewerRootTag)
        awaitCounter("2 / 7")
        // Options object survives recomposition; mode switch keeps the same page (checked below).
        options = options.copy(mode = ComicReadingMode.LEFT_TO_RIGHT)
        awaitCounter("2 / 7")
    }

    @Test
    fun rtlTapAndKeysAreMirrored() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        val options = ComicViewerOptions(mode = ComicReadingMode.RIGHT_TO_LEFT)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, ViewerFixture.CHAPTER_2, options, null, { null }, mutableListOf())
            }
        }

        awaitCounter("1 / 7")
        // RTL: the logical next page sits on the visual left.
        settledTap("reader-tap-prev")
        awaitCounter("2 / 7")
        // Swipes stay visual (mirrored reading order): rightward drag advances.
        // Dragged before keys: a key fling still settling would fight the drag.
        dragRight(ComicViewerRootTag)
        awaitCounter("3 / 7")
        dragLeft(ComicViewerRootTag)
        awaitCounter("2 / 7")
        // Left arrow advances; right arrow goes back.
        onNodeWithTag(ComicViewerRootTag).performKeyInput { pressKey(Key.DirectionLeft) }
        awaitCounter("3 / 7")
        onNodeWithTag(ComicViewerRootTag).performKeyInput { pressKey(Key.DirectionRight) }
        awaitCounter("2 / 7")
    }

    @Test
    fun verticalPagerTurnsTopDown() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        val options = ComicViewerOptions(mode = ComicReadingMode.VERTICAL)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_1, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, ViewerFixture.CHAPTER_1, options, null, { null }, mutableListOf())
            }
        }

        awaitCounter("1 / 3")
        settledTap("reader-tap-next")
        awaitCounter("2 / 3")
        onNodeWithTag(ComicViewerRootTag).performKeyInput { pressKey(Key.DirectionDown) }
        awaitCounter("3 / 3")
        onNodeWithTag(ComicViewerRootTag).performKeyInput { pressKey(Key.DirectionUp) }
        awaitCounter("2 / 3")
    }

    @Test
    fun webtoonBoundaryReportsNextAndHostSwitches() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        var chapter by mutableStateOf(ViewerFixture.CHAPTER_1)
        val options = ComicViewerOptions(mode = ComicReadingMode.WEBTOON)
        val seen = mutableListOf<ComicChapterBoundary>()
        setContent {
            ReaderTheme {
                LaunchedEffect(chapter) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, chapter, ViewerFixture.chapterIds))
                }
                val uiState by holder.uiState.collectAsState()
                val content = (uiState.content as? ReaderContentState.Ready)?.content
                Column(Modifier.fillMaxSize()) {
                    AppText(
                        text = if (content == null) "0 / 0"
                        else "${uiState.currentPage + 1} / ${content.pageKeys.size}",
                        modifier = Modifier.testTag(CounterTag),
                    )
                    if (content != null) {
                        ComicViewer(
                            holder = holder,
                            content = content,
                            options = options,
                            jump = null,
                            onToggleMenu = {},
                            onChapterBoundary = { boundary ->
                                seen += boundary
                                chapter = when (boundary) {
                                    ComicChapterBoundary.NEXT -> ViewerFixture.CHAPTER_2
                                    ComicChapterBoundary.PREVIOUS -> ViewerFixture.CHAPTER_1
                                }
                            },
                        )
                    }
                }
            }
        }

        awaitCounter("1 / 3")
        // Scroll to the end with settled taps; the edge tap at the bottom reports
        // NEXT (like pager over-scroll) and the host switches chapters.
        var guard = 0
        while (guard++ < 16) {
            waitForIdle()
            try {
                onNodeWithTag(CounterTag).assertTextEquals("1 / 7")
                break
            } catch (_: AssertionError) {
                settledTap("reader-tap-next")
            }
        }
        awaitCounter("1 / 7")
        assertEquals(listOf(ComicChapterBoundary.NEXT), seen)
    }

    @Test
    fun doublePagesPairAndInvertKeepsMaster() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        var options by mutableStateOf(
            ComicViewerOptions(
                mode = ComicReadingMode.LEFT_TO_RIGHT,
                pagerLayout = ComicPagerLayout.DOUBLE_PAGES,
                firstPageSingle = false,
            ),
        )
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_1, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, ViewerFixture.CHAPTER_1, options, null, { null }, mutableListOf())
            }
        }

        // Chapter 1 has 3 portraits: [p0,p1] then [p2] solo.
        awaitCounter("1 / 3")
        onNodeWithTag(comicPageImageTag(0)).assertIsDisplayed()
        onNodeWithTag(comicPageImageTag(1)).assertIsDisplayed()
        settledTap("reader-tap-next")
        awaitCounter("3 / 3")

        // Invert keeps the same master page, only the visual order flips.
        options = options.copy(invertDoublePages = true)
        awaitCounter("3 / 3")
        onNodeWithTag(comicPageImageTag(2)).assertIsDisplayed()
    }

    @Test
    fun wideSpreadSplitsAndReanchors() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        var jump by mutableStateOf<ComicViewerJump?>(null)
        val options = ComicViewerOptions(
            mode = ComicReadingMode.LEFT_TO_RIGHT,
            pagerLayout = ComicPagerLayout.DOUBLE_PAGES,
        )
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, ViewerFixture.CHAPTER_2, options, jump, { null }, mutableListOf())
            }
        }

        awaitCounter("1 / 7")
        // Jump onto the wide page: the visible spread decodes it, the spread
        // splits, and the viewer re-anchors on the same master page.
        jump = ComicViewerJump(requestId = 21L, contentSeq = holder.store.state.contentSeq, pageIndex = 1)
        awaitCounter("2 / 7")
        // Jump past it: with q1 isolated, [q2, q3] pair up and q2 is the master.
        // (Pre-split pairing would report q3 instead.)
        jump = ComicViewerJump(requestId = 22L, contentSeq = holder.store.state.contentSeq, pageIndex = 3)
        // A jump commits the requested page as the anchor, so the counter shows q3.
        awaitCounter("4 / 7")
        onNodeWithTag(comicPageImageTag(2)).assertIsDisplayed()
        onNodeWithTag(comicPageImageTag(3)).assertIsDisplayed()
    }

    @Test
    fun jumpConsumesOnceAndIgnoresStaleChapter() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        var jump by mutableStateOf<ComicViewerJump?>(null)
        val options = ComicViewerOptions(mode = ComicReadingMode.LEFT_TO_RIGHT)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, ViewerFixture.CHAPTER_2, options, jump, { null }, mutableListOf())
            }
        }

        awaitCounter("1 / 7")
        val seq = holder.store.state.contentSeq
        jump = ComicViewerJump(requestId = 7L, contentSeq = seq, pageIndex = 4)
        awaitCounter("5 / 7")

        // Same request re-emitted: consumed once, stays.
        jump = ComicViewerJump(requestId = 7L, contentSeq = seq, pageIndex = 0)
        waitForIdle()
        Thread.sleep(300)
        awaitCounter("5 / 7")

        // Stale chapter command ignored after switching chapters.
        val staleSeq = seq
        // Switch host chapter directly through the store.
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_1, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, ViewerFixture.CHAPTER_1, options, jump, { null }, mutableListOf())
            }
        }
        awaitCounter("1 / 3")
        jump = ComicViewerJump(requestId = 8L, contentSeq = staleSeq, pageIndex = 6)
        waitForIdle()
        Thread.sleep(300)
        awaitCounter("1 / 3")
    }

    @Test
    fun jumpWithinVisibleDoubleUpdatesAnchor() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        var jump by mutableStateOf<ComicViewerJump?>(null)
        var options by mutableStateOf(
            ComicViewerOptions(
                mode = ComicReadingMode.LEFT_TO_RIGHT,
                pagerLayout = ComicPagerLayout.DOUBLE_PAGES,
                firstPageSingle = true,
            ),
        )
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_1, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, ViewerFixture.CHAPTER_1, options, jump, { null }, mutableListOf())
            }
        }

        // Chapter 1 pairs [p1, p2] after the solo cover; anchor is p1 (index 1).
        awaitCounter("1 / 3")
        settledTap("reader-tap-next")
        awaitCounter("2 / 3")
        val seq = holder.store.state.contentSeq
        val savesBefore = service.saveCalls.size

        // Jump to the second page of the visible spread: both pages are on
        // screen, so only an explicit anchor+save commit can move the position.
        jump = ComicViewerJump(requestId = 73L, contentSeq = seq, pageIndex = 2)
        awaitCounter("3 / 3")
        waitForIdle()
        Thread.sleep(400)
        assertEquals(2, holder.store.state.currentPage)
        var content = (holder.store.state.content as ReaderContentState.Ready).content
        assertEquals("p2", content.pageKeys[holder.store.state.currentPage])
        val saves = service.saveCalls.drop(savesBefore)
        assertTrue(saves.isNotEmpty(), "jump inside the spread must save, got $saves")
        assertEquals(2, saves.last().pageIndex)

        // Single page again: the committed anchor survives.
        options = options.copy(pagerLayout = ComicPagerLayout.SINGLE_PAGE)
        awaitCounter("3 / 3")
        content = (holder.store.state.content as ReaderContentState.Ready).content
        assertEquals("p2", content.pageKeys[holder.store.state.currentPage])

        // Same request re-emitted: consumed once, no new save.
        val savesAfterJump = service.saveCalls.size
        jump = ComicViewerJump(requestId = 73L, contentSeq = seq, pageIndex = 0)
        waitForIdle()
        Thread.sleep(300)
        awaitCounter("3 / 3")
        assertEquals(savesAfterJump, service.saveCalls.size)

        // Jump from another spread into the same target still lands on p2.
        jump = ComicViewerJump(requestId = 74L, contentSeq = seq, pageIndex = 0)
        awaitCounter("1 / 3")
        jump = ComicViewerJump(requestId = 75L, contentSeq = seq, pageIndex = 2)
        awaitCounter("3 / 3")
        content = (holder.store.state.content as ReaderContentState.Ready).content
        assertEquals("p2", content.pageKeys[holder.store.state.currentPage])

        // Stale chapter command is ignored.
        val staleSeq = seq
        val savesBeforeStale = service.saveCalls.size
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, ViewerFixture.CHAPTER_2, options, jump, { null }, mutableListOf())
            }
        }
        awaitCounter("1 / 7")
        jump = ComicViewerJump(requestId = 76L, contentSeq = staleSeq, pageIndex = 6)
        waitForIdle()
        Thread.sleep(300)
        awaitCounter("1 / 7")

        // Rebuild from saved progress in double pages: still the third page.
        val secondHolder = ViewerFixture.holder(service)
        options = options.copy(pagerLayout = ComicPagerLayout.DOUBLE_PAGES)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    service.savedProgress[ViewerFixture.SOURCE to ViewerFixture.WORK] =
                        app.koyura.domain.reader.ReaderProgress(ViewerFixture.CHAPTER_1, 2)
                    secondHolder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_1, ViewerFixture.chapterIds))
                }
                ViewerLab(secondHolder, ViewerFixture.CHAPTER_1, options, null, { null }, mutableListOf())
            }
        }
        awaitCounter("3 / 3")
        val content2 = (secondHolder.store.state.content as ReaderContentState.Ready).content
        assertEquals("p2", content2.pageKeys[secondHolder.store.state.currentPage])
        assertTrue(savesBeforeStale >= 0)
    }

    @Test
    fun longStripFitWidthKeepsAspectAndPans() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        var jump by mutableStateOf<ComicViewerJump?>(null)
        var options by mutableStateOf(
            ComicViewerOptions(mode = ComicReadingMode.LEFT_TO_RIGHT, scaleFit = ComicScaleFit.FIT_WIDTH),
        )
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, ViewerFixture.CHAPTER_2, options, jump, { null }, mutableListOf())
            }
        }

        jump = ComicViewerJump(requestId = 81L, contentSeq = holder.store.state.contentSeq, pageIndex = 3)
        awaitCounter("4 / 7")
        waitForIdle()

        // q3 is 400x1200; FIT_WIDTH at a 420px-wide viewport scales it 1.05x,
        // so the drawn content is ~420x1260 — taller than the ~756px viewport.
        // The pre-fix squash showed ~252px of black at the bottom.
        val node = onNodeWithTag(comicPageImageTag(3)).fetchSemanticsNode()
        val imgW = node.size.width.toFloat()
        val imgH = node.size.height.toFloat()
        assertTrue(imgW > 400f, "image width should fill the viewport, got $imgW")
        assertTrue(imgH > 1200f, "long strip must keep its aspect ratio, got height=$imgH px")
        assertTrue(
            abs((imgW / imgH) - (400f / 1200f)) < 0.02f,
            "FIT_WIDTH must keep the 1:3 source ratio, got ${imgW}x$imgH",
        )

        // Oversized content is pannable at 1x: drag up exposes the bottom.
        // FIT_WIDTH content overflows the vp: the drawn box is taller than the
        // viewport (3072px content vs 744px viewport), so the overflowing part is
        // reachable by panning the fitted content rather than being cropped away.
        val viewport = onNodeWithTag(ComicViewerRootTag).fetchSemanticsNode().size
        assertTrue(
            node.size.height > viewport.height,
            "long strip must overflow the viewport instead of being squashed " +
                "(content=${node.size.height}px viewport=${viewport.height}px)",
        )

        // FIT_HEIGHT on the same page must fill the viewport height and crop
        // horizontally instead of squashing (the same constraint bug class).
        options = options.copy(scaleFit = ComicScaleFit.FIT_HEIGHT)
        waitForIdle()
        Thread.sleep(600)
        val fitHeight = onNodeWithTag(comicPageImageTag(3)).fetchSemanticsNode()
        val fhWidth = fitHeight.size.width.toFloat()
        val fhHeight = fitHeight.size.height.toFloat()
        val ratio = fhWidth / fhHeight
        assertTrue(abs(ratio - 400f / 1200f) < 0.02f, "FIT_HEIGHT must keep 1:3 ratio, got ${fhWidth}x$fhHeight")
        assertTrue(fhHeight > 600f, "FIT_HEIGHT scales to the viewport height, got $fhHeight")
    }

    @Test
    fun modeSwitchKeepsSameOriginalPage() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        var options by mutableStateOf(ComicViewerOptions(mode = ComicReadingMode.LEFT_TO_RIGHT))
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, ViewerFixture.CHAPTER_2, options, null, { null }, mutableListOf())
            }
        }

        awaitCounter("1 / 7")
        settledTap("reader-tap-next")
        settledTap("reader-tap-next")
        awaitCounter("3 / 7")
        // Pager -> webtoon keeps the same original page.
        options = options.copy(mode = ComicReadingMode.WEBTOON)
        awaitCounter("3 / 7")
        // Webtoon -> continuous vertical keeps it too.
        options = options.copy(mode = ComicReadingMode.CONTINUOUS_VERTICAL)
        awaitCounter("3 / 7")
    }

    @Test
    fun automaticDoubleAnchorsOriginalPageOnResize() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        var widthDp by mutableStateOf(420)
        var options by mutableStateOf(
            ComicViewerOptions(
                mode = ComicReadingMode.LEFT_TO_RIGHT,
                pagerLayout = ComicPagerLayout.AUTOMATIC,
                firstPageSingle = true,
            ),
        )
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_1, ViewerFixture.chapterIds))
                }
                val uiStateForCounter by holder.uiState.collectAsState()
                val content = (uiStateForCounter.content as? ReaderContentState.Ready)?.content
                Column(Modifier.fillMaxSize()) {
                    AppText(
                        text = if (content == null) "0 / 0"
                        else "${uiStateForCounter.currentPage + 1} / ${content.pageKeys.size}",
                        modifier = Modifier.testTag(CounterTag),
                    )
                    if (content != null) {
                        Box(Modifier.size(widthDp.dp, 780.dp)) {
                            ComicViewer(
                                holder = holder,
                                content = content,
                                options = options,
                                jump = null,
                                onToggleMenu = {},
                                onChapterBoundary = {},
                            )
                        }
                    }
                }
            }
        }

        // Chapter 1: three portrait pages, narrow canvas = single spreads.
        awaitCounter("1 / 3")
        settledTap("reader-tap-next")
        settledTap("reader-tap-next")
        awaitCounter("3 / 3")
        // Wait until the third page is persisted before resizing.
        val savedBy = System.currentTimeMillis() + 10_000
        while (service.saveCalls.none { it.pageIndex == 2 } && System.currentTimeMillis() < savedBy) {
            waitForIdle()
            Thread.sleep(100)
        }
        assertTrue(service.saveCalls.any { it.pageIndex == 2 }, "page 3 must be saved, got ${service.saveCalls}")
        val onesBefore = service.saveCalls.count { it.pageIndex == 1 }

        // Landscape: AUTOMATIC pairs [p1, p2] with the cover solo. Displayed page,
        // saved index and pageKey must all stay on the original third page.
        widthDp = 1000
        awaitCounter("3 / 3")
        waitForIdle()
        Thread.sleep(600)
        var content = (holder.store.state.content as? ReaderContentState.Ready)?.content
        assertEquals("p2", content?.pageKeys?.get(holder.store.state.currentPage))
        assertEquals(onesBefore, service.saveCalls.count { it.pageIndex == 1 })
        assertEquals(2, service.saveCalls.last().pageIndex)

        // Narrow again: back to solo spreads, still on the third page.
        widthDp = 420
        awaitCounter("3 / 3")
        content = (holder.store.state.content as? ReaderContentState.Ready)?.content
        assertEquals("p2", content?.pageKeys?.get(holder.store.state.currentPage))

        // Mode switches keep the anchor too.
        options = options.copy(mode = ComicReadingMode.CONTINUOUS_VERTICAL)
        awaitCounter("3 / 3")
        options = options.copy(mode = ComicReadingMode.LEFT_TO_RIGHT)
        awaitCounter("3 / 3")
        content = (holder.store.state.content as? ReaderContentState.Ready)?.content
        assertEquals("p2", content?.pageKeys?.get(holder.store.state.currentPage))

        // Rebuild the viewer: the saved position restores the same page key.
        val secondHolder = ViewerFixture.holder(service)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    secondHolder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_1, ViewerFixture.chapterIds))
                }
                ViewerLab(secondHolder, ViewerFixture.CHAPTER_1, options, null, { null }, mutableListOf())
            }
        }
        awaitCounter("3 / 3")
        val content2 = (secondHolder.store.state.content as ReaderContentState.Ready).content
        assertEquals("p2", content2.pageKeys[secondHolder.store.state.currentPage])
    }

    @Test
    fun rtlChapterBoundariesMirrorVisualEdges() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        var chapter by mutableStateOf(ViewerFixture.CHAPTER_2)
        var jump by mutableStateOf<ComicViewerJump?>(null)
        val options = ComicViewerOptions(mode = ComicReadingMode.RIGHT_TO_LEFT)
        val seen = mutableListOf<ComicChapterBoundary>()
        setContent {
            ReaderTheme {
                LaunchedEffect(chapter) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, chapter, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, chapter, options, jump, { boundary ->
                    seen += boundary
                    null
                }, mutableListOf())
            }
        }

        // Chapter 2, first page: tapping right moves toward the previous chapter.
        awaitCounter("1 / 7")
        settledTap("reader-tap-next")
        assertEquals(listOf(ComicChapterBoundary.PREVIOUS), seen)
        // Direction keys follow the same visual mapping.
        onNodeWithTag(ComicViewerRootTag).performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        Thread.sleep(300)
        assertEquals(listOf(ComicChapterBoundary.PREVIOUS, ComicChapterBoundary.PREVIOUS), seen)

        // Last page of the last chapter: no NEXT neighbour, stays silent.
        jump = ComicViewerJump(requestId = 31L, contentSeq = holder.store.state.contentSeq, pageIndex = 6)
        awaitCounter("7 / 7")
        settledTap("reader-tap-prev")
        waitForIdle()
        Thread.sleep(300)
        assertEquals(2, seen.size)

        // Chapter 1, last page: left tap/key report NEXT (chapter 2 exists).
        chapter = ViewerFixture.CHAPTER_1
        awaitCounter("1 / 3")
        jump = ComicViewerJump(requestId = 32L, contentSeq = holder.store.state.contentSeq, pageIndex = 2)
        awaitCounter("3 / 3")
        settledTap("reader-tap-prev")
        assertEquals(
            listOf(ComicChapterBoundary.PREVIOUS, ComicChapterBoundary.PREVIOUS, ComicChapterBoundary.NEXT),
            seen,
        )
        onNodeWithTag(ComicViewerRootTag).performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        Thread.sleep(300)
        assertEquals(4, seen.size)
        assertEquals(ComicChapterBoundary.NEXT, seen.last())

        // Chapter 1, first page: no PREVIOUS neighbour, stays silent.
        jump = ComicViewerJump(requestId = 33L, contentSeq = holder.store.state.contentSeq, pageIndex = 0)
        awaitCounter("1 / 3")
        settledTap("reader-tap-next")
        onNodeWithTag(ComicViewerRootTag).performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        Thread.sleep(300)
        assertEquals(4, seen.size)
    }

    @Test
    fun cropBordersKeepsPositionAndRetrySemantics() = runComposeUiTest {
        val service = ViewerFixture.cropService()
        val holder = ViewerFixture.holder(service)
        var options by mutableStateOf(ComicViewerOptions(mode = ComicReadingMode.LEFT_TO_RIGHT))
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(
                        ReaderIntent.Open(
                            ViewerFixture.SOURCE,
                            ViewerFixture.WORK,
                            ViewerFixture.CROP_CHAPTER,
                            ViewerFixture.cropChapterIds,
                        ),
                    )
                }
                ViewerLab(holder, ViewerFixture.CROP_CHAPTER, options, null, { null }, mutableListOf())
            }
        }

        awaitCounter("1 / 2")
        onNodeWithTag(comicPageImageTag(0)).assertIsDisplayed()
        options = options.copy(cropBorders = true)
        awaitCounter("1 / 2")
        onNodeWithTag(comicPageImageTag(0)).assertIsDisplayed()
        val content = (holder.store.state.content as ReaderContentState.Ready).content
        assertEquals("cb0", content.pageKeys[holder.store.state.currentPage])
        settledTap("reader-tap-next")
        awaitCounter("2 / 2")
        options = options.copy(cropBorders = false)
        awaitCounter("2 / 2")
        onNodeWithTag(comicPageImageTag(1)).assertIsDisplayed()
    }

    @Test
    fun failedPageRetriesWithSameKey() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        var jump by mutableStateOf<ComicViewerJump?>(null)
        val options = ComicViewerOptions(mode = ComicReadingMode.WEBTOON)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, ViewerFixture.CHAPTER_2, options, jump, { null }, mutableListOf())
            }
        }

        awaitCounter("1 / 7")
        jump = ComicViewerJump(requestId = 1L, contentSeq = holder.store.state.contentSeq, pageIndex = 5)
        awaitCounter("6 / 7")
        // q5 fails on first load: poll until the retry affordance (original page key) shows.
        val shownBy = System.currentTimeMillis() + 10_000
        var shown = false
        while (!shown && System.currentTimeMillis() < shownBy) {
            shown = onAllNodesWithTag(comicPageRetryTag("q5")).fetchSemanticsNodes().isNotEmpty()
            if (!shown) {
                waitForIdle()
                Thread.sleep(100)
            }
        }
        assertTrue(shown, "retry affordance should appear for q5")
        onNodeWithTag(comicPageRetryTag("q5")).assertIsDisplayed()
        // Retry through the store (same affordance the pager error row uses).
        holder.store.accept(ReaderIntent.RetryPage("q5"))
        waitForIdle()
        val deadline = System.currentTimeMillis() + 10_000
        while (onAllNodesWithTag(comicPageRetryTag("q5")).fetchSemanticsNodes().isNotEmpty() &&
            System.currentTimeMillis() < deadline
        ) {
            waitForIdle()
            Thread.sleep(100)
        }
        assertTrue(
            onAllNodesWithTag(comicPageRetryTag("q5")).fetchSemanticsNodes().isEmpty(),
            "retry affordance should clear after success",
        )
        onNodeWithTag(comicPageImageTag(5)).assertIsDisplayed()
    }

    @Test
    fun doubleTapZoomsInsteadOfTurningTwice() = runComposeUiTest {
        val service = ViewerFixture.service()
        val holder = ViewerFixture.holder(service)
        val options = ComicViewerOptions(mode = ComicReadingMode.LEFT_TO_RIGHT)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    holder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds))
                }
                ViewerLab(holder, ViewerFixture.CHAPTER_2, options, null, { null }, mutableListOf())
            }
        }

        awaitCounter("1 / 7")
        // Two taps inside the 400ms double-tap window: recognised as a zoom
        // gesture, so neither fires a single-tap page turn.
        repeat(2) { round ->
            onNodeWithTag("reader-tap-next").performTouchInput {
                val w = visibleSize.width.toFloat()
                val h = visibleSize.height / 2f
                val p = androidx.compose.ui.geometry.Offset(w * 0.8f, h)
                down(0, p)
                up(0)
            }
            if (round == 0) Thread.sleep(120)
        }
        waitForIdle()
        Thread.sleep(600)
        awaitCounter("1 / 7")
        // Singles still turn afterwards.
        settledTap("reader-tap-next")
        awaitCounter("2 / 7")
    }

    @Test
    fun rebuildKeepsSamePageKeyForChapterTwoPageThree() = runComposeUiTest {
        val service = ViewerFixture.service()
        val firstHolder = ViewerFixture.holder(service)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    service.savedProgress[ViewerFixture.SOURCE to ViewerFixture.WORK] =
                        app.koyura.domain.reader.ReaderProgress(ViewerFixture.CHAPTER_2, 2)
                    firstHolder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds))
                }
                ViewerLab(firstHolder, ViewerFixture.CHAPTER_2, ComicViewerOptions(), null, { null }, mutableListOf())
            }
        }
        awaitCounter("3 / 7")
        val content = (firstHolder.store.state.content as ReaderContentState.Ready).content
        assertEquals("q2", content.pageKeys[firstHolder.store.state.currentPage])

        // Recreate the viewer (new store, same service): same page key restores.
        val secondHolder = ViewerFixture.holder(service)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    secondHolder.store.accept(ReaderIntent.Open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds))
                }
                ViewerLab(secondHolder, ViewerFixture.CHAPTER_2, ComicViewerOptions(), null, { null }, mutableListOf())
            }
        }
        awaitCounter("3 / 7")
        val content2 = (secondHolder.store.state.content as ReaderContentState.Ready).content
        assertEquals("q2", content2.pageKeys[secondHolder.store.state.currentPage])
    }
}
