package dev.reader.app.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import dev.reader.app.reader.ReaderPageState
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.reader.app.AppScaffold
import dev.reader.app.browse.BrowseService
import dev.reader.app.browse.BrowseSession
import dev.reader.app.browse.FakeBrowseService
import dev.reader.app.browse.WorkDetail
import dev.reader.app.browse.WorkChapter
import dev.reader.app.navigation.SourceDetailRoute
import dev.reader.app.navigation.createAppNavigationState
import dev.reader.designsystem.ReaderTheme
import dev.reader.designsystem.navigation.NavTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


private fun demoDetail(): WorkDetail = WorkDetail(
    id = "demo",
    title = "星图",
    subtitle = "作者",
    description = "简介",
    chapters = listOf(WorkChapter("ep_1", "第一章"), WorkChapter("ep_2", "第二章")),
)

private fun demoSingleDetail(): WorkDetail = WorkDetail(
    id = "single",
    title = "Single",
    chapters = emptyList(),
)

private fun demoReader(pages: Map<String, Int>): FakeReaderService {
    val bytes = pages.mapValues { (_, rgb) -> pngBytes(rgb) }
    return FakeReaderService(
        chapters = mutableMapOf(
            Triple("example_source", "demo", "ep_1") to ChapterContent(
                chapterId = "ep_1",
                title = "第一章",
                pageKeys = listOf("p0", "p1", "p2", "p3", "p4"),
                startPageIndex = 0,
            ),
            Triple("example_source", "demo", "ep_2") to ChapterContent(
                chapterId = "ep_2",
                title = "第二章",
                pageKeys = listOf("q0", "q1", "q2"),
                startPageIndex = 0,
            ),
            Triple("example_source", "single", null) to ChapterContent(
                chapterId = null,
                title = "",
                pageKeys = listOf("s0", "s1"),
                startPageIndex = 0,
            ),
        ),
        pageBytes = bytes.toMutableMap(),
    )
}

private fun demoBrowse(): FakeBrowseService = FakeBrowseService().apply {
    workDetails = mapOf("demo" to demoDetail(), "single" to demoSingleDetail())
}

private fun androidx.compose.ui.test.ComposeUiTest.awaitIndicator(text: String) {
    val deadline = System.currentTimeMillis() + 10000
    var shown = false
    var lastError: AssertionError? = null
    while (!shown && System.currentTimeMillis() < deadline) {
        try {
            onNodeWithTag(ReaderPageIndicatorTag).assertTextEquals(text)
            shown = true
        } catch (failure: AssertionError) {
            lastError = failure
            waitForIdle()
            Thread.sleep(100)
        }
    }
    if (!shown) throw lastError ?: AssertionError("indicator never showed $text")
}

class ReaderFlowTest {
    @Test
    fun startReadTurnToThirdThenContinueAfterRecreate() = runComposeUiTest {
        val browse = demoBrowse()
        val reader = demoReader(mapOf("p0" to 0xFF0000, "p1" to 0x00FF00, "p2" to 0x0000FF, "p3" to 0xFFFF00, "p4" to 0xFF00FF))
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        navigation.push(SourceDetailRoute("example_source", "demo", "星图"))
        setContent {
            ReaderTheme {
                AppScaffold(
                    navigation,
                    useRail = false,
                    browseService = browse,
                    browseSession = BrowseSession(browse),
                    readerService = reader,
                )
            }
        }

        // No progress yet: only Start is offered, and nothing has been saved.
        onNodeWithTag("reading-start").assertIsDisplayed()
        assertTrue(onAllNodesWithTag("reading-continue").fetchSemanticsNodes().isEmpty())
        assertTrue(reader.saveCalls.isEmpty())

        // Start reading: the initial page restores nothing and writes nothing.
        onNodeWithTag("reading-start").performClick()
        onNodeWithTag(ReaderPageIndicatorTag).assertTextEquals("1 / 5")
        assertTrue(reader.saveCalls.isEmpty(), "initial page must not be written, got ${reader.saveCalls}")
        onNodeWithTag(readerPageTag(0)).assertIsDisplayed()

        // Turn to the third page.
        onNodeWithTag(ReaderTapNextTag).performClick()
        onNodeWithTag(ReaderTapNextTag).performClick()
        awaitIndicator("3 / 5")
        onNodeWithTag(readerPageTag(2)).assertIsDisplayed()

        // Back returns to the detail page and keeps the last displayed page.
        onNodeWithTag(ReaderBackTag).performClick()
        onNodeWithTag("source-detail-heading").assertIsDisplayed()
        assertEquals(
            FakeReaderService.SaveCall("example_source", "demo", "ep_1", 2),
            reader.saveCalls.last(),
        )

        // Destroy the interface and create a new one: continue resumes the third page.
        val recreated = createAppNavigationState(initialTab = NavTab.Browse)
        recreated.push(SourceDetailRoute("example_source", "demo", "星图"))
        setContent {
            ReaderTheme {
                AppScaffold(
                    recreated,
                    useRail = false,
                    browseService = browse,
                    browseSession = BrowseSession(browse),
                    readerService = reader,
                )
            }
        }
        onNodeWithTag("reading-continue").assertIsDisplayed()
        onNodeWithTag("reading-continue").performClick()
        awaitIndicator("3 / 5")
        onNodeWithTag(readerPageTag(2)).assertIsDisplayed()
    }

    @Test
    fun chapterRowOpensThatChapter() = runComposeUiTest {
        val browse = demoBrowse()
        val reader = demoReader(mapOf("q0" to 0x123456, "q1" to 0x654321, "q2" to 0xABCDEF))
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        navigation.push(SourceDetailRoute("example_source", "demo", "星图"))
        setContent {
            ReaderTheme {
                AppScaffold(
                    navigation,
                    useRail = false,
                    browseService = browse,
                    browseSession = BrowseSession(browse),
                    readerService = reader,
                )
            }
        }

        onNodeWithTag("source-detail-chapter-ep_2").performClick()
        onNodeWithTag(ReaderPageIndicatorTag).assertTextEquals("1 / 3")
        onNodeWithTag(ReaderPrevChapterTag).assertIsEnabled()
        onNodeWithTag(ReaderNextChapterTag).assertIsNotEnabled()
    }

    @Test
    fun chapterlessWorkStartsWithoutChapter() = runComposeUiTest {
        val browse = demoBrowse()
        val reader = demoReader(mapOf("s0" to 0x111111, "s1" to 0x222222))
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        navigation.push(SourceDetailRoute("example_source", "single", "Single"))
        setContent {
            ReaderTheme {
                AppScaffold(
                    navigation,
                    useRail = false,
                    browseService = browse,
                    browseSession = BrowseSession(browse),
                    readerService = reader,
                )
            }
        }

        onNodeWithTag("reading-start").performClick()
        onNodeWithTag(ReaderPageIndicatorTag).assertTextEquals("1 / 2")
        onNodeWithTag(ReaderPrevChapterTag).assertIsNotEnabled()
        onNodeWithTag(ReaderNextChapterTag).assertIsNotEnabled()

        onNodeWithTag(ReaderTapNextTag).performClick()
        awaitIndicator("2 / 2")

        onNodeWithTag(ReaderBackTag).performClick()
        assertEquals(
            FakeReaderService.SaveCall("example_source", "single", null, 1),
            reader.saveCalls.last(),
        )
    }

    @Test
    fun skiaDecodesTestPng() {
        val bytes = pngBytes(0xFF0000)
        val image = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        assertEquals(4, image.width)
        assertEquals(4, image.height)
    }

    @Test
    fun failedPageRetriesIndividually() = runComposeUiTest {
        val browse = demoBrowse()
        val reader = demoReader(mapOf("p0" to 0xFF0000))
        reader.pageFailures["p0"] = 1
        val readerSession = ReaderSession(reader)
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        navigation.push(SourceDetailRoute("example_source", "demo", "星图"))
        setContent {
            ReaderTheme {
                AppScaffold(
                    navigation,
                    useRail = false,
                    browseService = browse,
                    browseSession = BrowseSession(browse),
                    readerService = reader,
                    readerSession = readerSession,
                )
            }
        }

        onNodeWithTag("reading-start").performClick()
        onNodeWithTag("reader-page-retry-p0").assertIsDisplayed()
        readerSession.retryPage("p0")
        waitForIdle()
        // The retry reloads through the session; poll until the error row is gone.
        val retryGoneBy = System.currentTimeMillis() + 10000
        while (onAllNodesWithTag("reader-page-retry-p0").fetchSemanticsNodes().isNotEmpty() &&
            System.currentTimeMillis() < retryGoneBy
        ) {
            waitForIdle()
            Thread.sleep(100)
        }
        waitForIdle()
        val leftover = onAllNodesWithTag("reader-page-retry-p0").fetchSemanticsNodes()
        val images = onAllNodesWithTag("reader-page-image-0").fetchSemanticsNodes()
        assertTrue(
            readerSession.pages["p0"] is dev.reader.app.reader.ReaderPageState.Ready,
            "pages=" + readerSession.pages,
        )
        assertTrue(
            leftover.isEmpty(),
            "leftover=" + leftover.size +
                " images=" + images.size + " pages=" + readerSession.pages,
        )
        onNodeWithTag(ReaderPageIndicatorTag).assertTextEquals("1 / 5")
    }

    @Test
    fun nextChapterSwitchesContentAndKeepsHistory() = runComposeUiTest {
        val browse = demoBrowse()
        val reader = demoReader(
            mapOf("p0" to 0xFF0000, "p1" to 0x00FF00, "q0" to 0x0000FF, "q1" to 0xFFFF00, "q2" to 0x00FFFF),
        )
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        navigation.push(SourceDetailRoute("example_source", "demo", "星图"))
        setContent {
            ReaderTheme {
                AppScaffold(
                    navigation,
                    useRail = true,
                    browseService = browse,
                    browseSession = BrowseSession(browse),
                    readerService = reader,
                )
            }
        }

        onNodeWithTag("reading-start").performClick()
        onNodeWithTag(ReaderPrevChapterTag).assertIsNotEnabled()
        onNodeWithTag(ReaderNextChapterTag).assertIsEnabled()
        onNodeWithTag(ReaderNextChapterTag).performClick()
        awaitIndicator("1 / 3")
        onNodeWithTag(ReaderPrevChapterTag).assertIsEnabled()
        onNodeWithTag(ReaderNextChapterTag).assertIsNotEnabled()

        // Back returns to the detail page, not to the previous chapter.
        onNodeWithTag(ReaderBackTag).performClick()
        onNodeWithTag("source-detail-heading").assertIsDisplayed()
    }

    @Test
    fun keyboardArrowsTurnPages() = runComposeUiTest {
        val browse = demoBrowse()
        val reader = demoReader(mapOf("p0" to 0xFF0000, "p1" to 0x00FF00, "p2" to 0x0000FF))
        val navigation = createAppNavigationState(initialTab = NavTab.Browse)
        navigation.push(SourceDetailRoute("example_source", "demo", "星图"))
        setContent {
            ReaderTheme {
                AppScaffold(
                    navigation,
                    useRail = false,
                    browseService = browse,
                    browseSession = BrowseSession(browse),
                    readerService = reader,
                )
            }
        }

        onNodeWithTag("reading-start").performClick()
        onNodeWithTag(ReaderRootTestTag).performKeyInput {
            pressKey(Key.DirectionRight)
        }
        awaitIndicator("2 / 5")
        onNodeWithTag(ReaderRootTestTag).performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        awaitIndicator("1 / 5")
    }
}

