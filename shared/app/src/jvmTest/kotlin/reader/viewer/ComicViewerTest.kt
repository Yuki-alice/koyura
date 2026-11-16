package app.koyura.app.reader.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.designsystem.ReaderTheme
import app.koyura.features.reader.comic.ReaderContentState
import app.koyura.features.reader.comic.ReaderSession
import app.koyura.features.reader.comic.viewer.ComicChapterBoundary
import app.koyura.features.reader.comic.viewer.ComicPagerLayout
import app.koyura.features.reader.comic.viewer.ComicReadingMode
import app.koyura.features.reader.comic.viewer.ComicViewer
import app.koyura.features.reader.comic.viewer.ComicViewerJump
import app.koyura.features.reader.comic.viewer.ComicViewerOptions
import app.koyura.features.reader.comic.viewer.ComicViewerRootTag
import app.koyura.features.reader.comic.viewer.comicPageImageTag
import app.koyura.features.reader.comic.viewer.comicPageRetryTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val CounterTag = "reader-viewer-counter"

@Composable
private fun ViewerLab(
    session: ReaderSession,
    chapterId: String?,
    options: ComicViewerOptions,
    jump: ComicViewerJump?,
    onBoundary: (ComicChapterBoundary) -> String?,
    menuToggles: MutableList<Int>,
) {
    LaunchedEffect(session.contentSeq, chapterId) {
        // Opened by the test before composition in most cases; kept here for recompositions.
    }
    val content = (session.content as? ReaderContentState.Ready)?.content
    Column(Modifier.fillMaxSize()) {
        Text(
            text = if (content == null) "0 / 0"
            else "${session.currentPage + 1} / ${content.pageKeys.size}",
            modifier = Modifier.testTag(CounterTag),
        )
        if (content != null) {
            Box(Modifier.weight(1f)) {
                ComicViewer(
                    session = session,
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
        val session = ViewerFixture.session(service)
        var options by mutableStateOf(ComicViewerOptions(mode = ComicReadingMode.LEFT_TO_RIGHT))
        val menus = mutableListOf<Int>()
        var chapter by mutableStateOf(ViewerFixture.CHAPTER_2)
        setContent {
            ReaderTheme {
                LaunchedEffect(chapter) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, chapter, ViewerFixture.chapterIds)
                }
                ViewerLab(session, chapter, options, null, { null }, menus)
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
        val session = ViewerFixture.session(service)
        val options = ComicViewerOptions(mode = ComicReadingMode.RIGHT_TO_LEFT)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds)
                }
                ViewerLab(session, ViewerFixture.CHAPTER_2, options, null, { null }, mutableListOf())
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
        val session = ViewerFixture.session(service)
        val options = ComicViewerOptions(mode = ComicReadingMode.VERTICAL)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_1, ViewerFixture.chapterIds)
                }
                ViewerLab(session, ViewerFixture.CHAPTER_1, options, null, { null }, mutableListOf())
            }
        }

        awaitCounter("1 / 3")
        settledTap("reader-tap-next")
        awaitCounter("2 / 3")
        dragUp(ComicViewerRootTag)
        awaitCounter("3 / 3")
        dragDown(ComicViewerRootTag)
        awaitCounter("2 / 3")
        onNodeWithTag(ComicViewerRootTag).performKeyInput { pressKey(Key.DirectionDown) }
        awaitCounter("3 / 3")
        onNodeWithTag(ComicViewerRootTag).performKeyInput { pressKey(Key.DirectionUp) }
        awaitCounter("2 / 3")
    }

    @Test
    fun webtoonBoundaryReportsNextAndHostSwitches() = runComposeUiTest {
        val service = ViewerFixture.service()
        val session = ViewerFixture.session(service)
        var chapter by mutableStateOf(ViewerFixture.CHAPTER_1)
        val options = ComicViewerOptions(mode = ComicReadingMode.WEBTOON)
        val seen = mutableListOf<ComicChapterBoundary>()
        setContent {
            ReaderTheme {
                LaunchedEffect(chapter) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, chapter, ViewerFixture.chapterIds)
                }
                val content = (session.content as? ReaderContentState.Ready)?.content
                Column(Modifier.fillMaxSize()) {
                    Text(
                        text = if (content == null) "0 / 0"
                        else "${session.currentPage + 1} / ${content.pageKeys.size}",
                        modifier = Modifier.testTag(CounterTag),
                    )
                    if (content != null) {
                        ComicViewer(
                            session = session,
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
        val session = ViewerFixture.session(service)
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
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_1, ViewerFixture.chapterIds)
                }
                ViewerLab(session, ViewerFixture.CHAPTER_1, options, null, { null }, mutableListOf())
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
        val session = ViewerFixture.session(service)
        var jump by mutableStateOf<ComicViewerJump?>(null)
        val options = ComicViewerOptions(
            mode = ComicReadingMode.LEFT_TO_RIGHT,
            pagerLayout = ComicPagerLayout.DOUBLE_PAGES,
        )
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds)
                }
                ViewerLab(session, ViewerFixture.CHAPTER_2, options, jump, { null }, mutableListOf())
            }
        }

        awaitCounter("1 / 7")
        // Jump onto the wide page: the visible spread decodes it, the spread
        // splits, and the viewer re-anchors on the same master page.
        jump = ComicViewerJump(requestId = 21L, contentSeq = session.contentSeq, pageIndex = 1)
        awaitCounter("2 / 7")
        // Jump past it: with q1 isolated, [q2, q3] pair up and q2 is the master.
        // (Pre-split pairing would report q3 instead.)
        jump = ComicViewerJump(requestId = 22L, contentSeq = session.contentSeq, pageIndex = 3)
        awaitCounter("3 / 7")
        onNodeWithTag(comicPageImageTag(2)).assertIsDisplayed()
        onNodeWithTag(comicPageImageTag(3)).assertIsDisplayed()
    }

    @Test
    fun jumpConsumesOnceAndIgnoresStaleChapter() = runComposeUiTest {
        val service = ViewerFixture.service()
        val session = ViewerFixture.session(service)
        var jump by mutableStateOf<ComicViewerJump?>(null)
        val options = ComicViewerOptions(mode = ComicReadingMode.LEFT_TO_RIGHT)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds)
                }
                ViewerLab(session, ViewerFixture.CHAPTER_2, options, jump, { null }, mutableListOf())
            }
        }

        awaitCounter("1 / 7")
        val seq = session.contentSeq
        jump = ComicViewerJump(requestId = 7L, contentSeq = seq, pageIndex = 4)
        awaitCounter("5 / 7")

        // Same request re-emitted: consumed once, stays.
        jump = ComicViewerJump(requestId = 7L, contentSeq = seq, pageIndex = 0)
        waitForIdle()
        Thread.sleep(300)
        awaitCounter("5 / 7")

        // Stale chapter command ignored after switching chapters.
        val staleSeq = seq
        // Switch host chapter directly through the session.
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_1, ViewerFixture.chapterIds)
                }
                ViewerLab(session, ViewerFixture.CHAPTER_1, options, jump, { null }, mutableListOf())
            }
        }
        awaitCounter("1 / 3")
        jump = ComicViewerJump(requestId = 8L, contentSeq = staleSeq, pageIndex = 6)
        waitForIdle()
        Thread.sleep(300)
        awaitCounter("1 / 3")
    }

    @Test
    fun modeSwitchKeepsSameOriginalPage() = runComposeUiTest {
        val service = ViewerFixture.service()
        val session = ViewerFixture.session(service)
        var options by mutableStateOf(ComicViewerOptions(mode = ComicReadingMode.LEFT_TO_RIGHT))
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds)
                }
                ViewerLab(session, ViewerFixture.CHAPTER_2, options, null, { null }, mutableListOf())
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
    fun failedPageRetriesWithSameKey() = runComposeUiTest {
        val service = ViewerFixture.service()
        val session = ViewerFixture.session(service)
        var jump by mutableStateOf<ComicViewerJump?>(null)
        val options = ComicViewerOptions(mode = ComicReadingMode.WEBTOON)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds)
                }
                ViewerLab(session, ViewerFixture.CHAPTER_2, options, jump, { null }, mutableListOf())
            }
        }

        awaitCounter("1 / 7")
        jump = ComicViewerJump(requestId = 1L, contentSeq = session.contentSeq, pageIndex = 5)
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
        // Retry through the session (same affordance the pager error row uses).
        session.retryPage("q5")
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
        val session = ViewerFixture.session(service)
        val options = ComicViewerOptions(mode = ComicReadingMode.LEFT_TO_RIGHT)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds)
                }
                ViewerLab(session, ViewerFixture.CHAPTER_2, options, null, { null }, mutableListOf())
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
        val first = ViewerFixture.session(service)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    service.savedProgress[ViewerFixture.SOURCE to ViewerFixture.WORK] =
                        app.koyura.domain.reader.ReaderProgress(ViewerFixture.CHAPTER_2, 2)
                    first.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds)
                }
                ViewerLab(first, ViewerFixture.CHAPTER_2, ComicViewerOptions(), null, { null }, mutableListOf())
            }
        }
        awaitCounter("3 / 7")
        val content = (first.content as ReaderContentState.Ready).content
        assertEquals("q2", content.pageKeys[first.currentPage])

        // Recreate the viewer (new session, same service): same page key restores.
        val second = ViewerFixture.session(service)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    second.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds)
                }
                ViewerLab(second, ViewerFixture.CHAPTER_2, ComicViewerOptions(), null, { null }, mutableListOf())
            }
        }
        awaitCounter("3 / 7")
        val content2 = (second.content as ReaderContentState.Ready).content
        assertEquals("q2", content2.pageKeys[second.currentPage])
    }
}
