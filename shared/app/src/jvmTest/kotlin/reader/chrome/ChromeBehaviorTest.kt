package app.koyura.app.reader.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import app.koyura.designsystem.ReaderTheme
import app.koyura.domain.reader.preferences.ReaderSettings
import app.koyura.features.reader.comic.ReaderBackTag
import app.koyura.features.reader.comic.ReaderNextChapterTag
import app.koyura.features.reader.comic.ReaderPageIndicatorTag
import app.koyura.features.reader.comic.ReaderPrevChapterTag
import app.koyura.features.reader.comic.chrome.ChapterEntry
import app.koyura.features.reader.comic.chrome.ReaderChrome
import app.koyura.features.reader.comic.chrome.ReaderChromeAction
import app.koyura.features.reader.comic.chrome.ReaderChromeChapterCurrentTag
import app.koyura.features.reader.comic.chrome.ReaderChromeChapterDialogTag
import app.koyura.features.reader.comic.chrome.ReaderChromeChapterListTag
import app.koyura.features.reader.comic.chrome.ReaderChromeChaptersTag
import app.koyura.features.reader.comic.chrome.ReaderChromeCropTag
import app.koyura.features.reader.comic.chrome.ReaderChromeDialog
import app.koyura.features.reader.comic.chrome.ReaderChromeModeDialogTag
import app.koyura.features.reader.comic.chrome.ReaderChromeNoChaptersTag
import app.koyura.features.reader.comic.chrome.ReaderChromePageGoTag
import app.koyura.features.reader.comic.chrome.ReaderChromePageInputTag
import app.koyura.features.reader.comic.chrome.ReaderChromePageJumpDialogTag
import app.koyura.features.reader.comic.chrome.ReaderChromeRootTag
import app.koyura.features.reader.comic.chrome.ReaderChromeSettingsDialogTag
import app.koyura.features.reader.comic.chrome.ReaderChromeSliderTag
import app.koyura.features.reader.comic.chrome.ReaderChromeState
import app.koyura.features.reader.comic.chrome.readerChromeChapterTag
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun demoChapters() = listOf(ChapterEntry("ep_1", "第一章"), ChapterEntry("ep_2", "第二章"))

private fun demoState() = ReaderChromeState(
    workTitle = "星图",
    chapterId = "ep_2",
    chapters = demoChapters(),
    pageIndex = 2,
    pageCount = 3,
    menuVisible = true,
    hasPrevChapter = true,
    hasNextChapter = false,
    global = ReaderSettings(),
    override = null,
)

class ChromeBehaviorTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setHostContent(host: ChromeTestHost) {
        rule.setContent {
            ReaderTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize()) {
                    ReaderChrome(
                        state = host.state,
                        onAction = host::onAction,
                    )
                }
            }
        }
    }

    private fun setHostContentWithToggle(host: ChromeTestHost) {
        rule.setContent {
            ReaderTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    androidx.compose.material3.Button(
                        onClick = { host.onAction(ReaderChromeAction.SetMenuVisible(!host.state.menuVisible)) },
                        modifier = Modifier.testTag("chrome-test-toggle"),
                    ) { androidx.compose.material3.Text("toggle") }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        ReaderChrome(
                            state = host.state,
                            onAction = host::onAction,
                        )
                    }
                }
            }
        }
    }

    private fun jumpActions(host: ChromeTestHost) =
        host.actions.filterIsInstance<ReaderChromeAction.JumpToPage>()

    @Test
    fun hiddenMenuRendersNothingAndToggleRestoresIt() {
        val host = ChromeTestHost(demoState())
        setHostContentWithToggle(host)

        rule.onNodeWithTag(ReaderChromeRootTag).assertIsDisplayed()

        rule.onNodeWithTag("chrome-test-toggle").performClick()
        rule.onNodeWithTag(ReaderChromeRootTag).assertDoesNotExist()

        rule.onNodeWithTag("chrome-test-toggle").performClick()
        rule.onNodeWithTag(ReaderChromeRootTag).assertIsDisplayed()
        // Top and bottom bars cover both edges at once.
        rule.onNodeWithTag(ReaderBackTag).assertIsDisplayed()
        rule.onNodeWithTag(ReaderChromeSliderTag).assertIsDisplayed()
    }

    @Test
    fun chapterDialogHighlightsCurrentAndPicksChapter() {
        val host = ChromeTestHost(demoState())
        setHostContent(host)

        rule.onNodeWithTag(ReaderChromeChaptersTag).performClick()
        rule.onNodeWithTag(ReaderChromeChapterDialogTag).assertIsDisplayed()
        // The current chapter carries its own tag; tapping it re-opens the same chapter.
        rule.onNodeWithTag(ReaderChromeChapterCurrentTag).assertIsDisplayed()
        rule.onNodeWithTag(ReaderChromeChapterCurrentTag).performClick()
        assertEquals(
            listOf("ep_2"),
            host.actions.filterIsInstance<ReaderChromeAction.OpenChapter>().map { it.chapterId },
        )
        rule.onNodeWithTag(ReaderChromeChapterDialogTag).assertDoesNotExist()

        rule.onNodeWithTag(ReaderChromeChaptersTag).performClick()
        rule.onNodeWithTag(ReaderChromeChapterDialogTag).assertIsDisplayed()
        // The list opens scrolled to the current (last) chapter; off-screen rows need a scroll.
        rule.onNodeWithTag(ReaderChromeChapterListTag)
            .performSemanticsAction(SemanticsActions.ScrollToIndex) { it(0) }
        rule.onNodeWithTag(readerChromeChapterTag(0)).performClick()
        val opens = host.actions.filterIsInstance<ReaderChromeAction.OpenChapter>()
        assertEquals("ep_1", opens.last().chapterId)
        rule.onNodeWithTag(ReaderChromeChapterDialogTag).assertDoesNotExist()
        // Host applies the switch: dialog closes and the chapter changes.
        assertEquals("ep_1", host.state.chapterId)
    }

    @Test
    fun prevNextButtonsDisableAtBoundaries() {
        val first = ChromeTestHost(
            demoState().copy(chapterId = "ep_1", hasPrevChapter = false, hasNextChapter = true),
        )
        setHostContent(first)
        rule.onNodeWithTag(ReaderPrevChapterTag).assertIsNotEnabled()
        rule.onNodeWithTag(ReaderNextChapterTag).assertIsEnabled()

        val last = ChromeTestHost(demoState())
        setHostContent(last)
        rule.onNodeWithTag(ReaderPrevChapterTag).assertIsEnabled()
        rule.onNodeWithTag(ReaderNextChapterTag).assertIsNotEnabled()
    }

    @Test
    fun emptyChaptersAndPagesRenderPlaceholders() {
        val host = ChromeTestHost(
            demoState().copy(
                chapterId = null,
                chapters = emptyList(),
                pageIndex = 0,
                pageCount = 0,
                hasPrevChapter = false,
                hasNextChapter = false,
            ),
        )
        setHostContent(host)

        rule.onNodeWithTag(ReaderPageIndicatorTag).assertTextEquals("0 / 0")
        rule.onNodeWithTag(ReaderChromeSliderTag).assertDoesNotExist()

        rule.onNodeWithTag(ReaderChromeChaptersTag).performClick()
        rule.onNodeWithTag(ReaderChromeNoChaptersTag).assertIsDisplayed()
    }

    @Test
    fun pageJumpDialogValidatesBoundsAndEmitsOnce() {
        val host = ChromeTestHost(demoState())
        setHostContent(host)

        rule.onNodeWithTag(ReaderPageIndicatorTag).performClick()
        rule.onNodeWithTag(ReaderChromePageJumpDialogTag).assertIsDisplayed()

        rule.onNodeWithTag(ReaderChromePageInputTag).performTextInput("0")
        rule.onNodeWithTag(ReaderChromePageGoTag).assertIsNotEnabled()
        rule.onNodeWithTag(ReaderChromePageInputTag).performTextClearance()
        rule.onNodeWithTag(ReaderChromePageInputTag).performTextInput("99")
        rule.onNodeWithTag(ReaderChromePageGoTag).assertIsNotEnabled()

        host.actions.clear()
        rule.onNodeWithTag(ReaderChromePageInputTag).performTextClearance()
        rule.onNodeWithTag(ReaderChromePageInputTag).performTextInput("2")
        rule.onNodeWithTag(ReaderChromePageGoTag).assertIsEnabled()
        rule.onNodeWithTag(ReaderChromePageGoTag).performClick()

        assertEquals(listOf(1), jumpActions(host).map { it.pageIndex })
        rule.onNodeWithTag(ReaderChromePageJumpDialogTag).assertDoesNotExist()
    }

    @Test
    fun sliderDragEmitsExactlyOneJump() {
        val host = ChromeTestHost(demoState().copy(pageIndex = 0))
        setHostContent(host)

        // Desktop-native mouse drag: the touch-emulated swipe does not drive the
        // Material3 slider drag in this harness, the mouse path does.
        val bounds = rule.onNodeWithTag(ReaderChromeSliderTag).fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag(ReaderChromeSliderTag).performMouseInput {
            val y = bounds.height / 2f
            moveTo(Offset(bounds.width * 0.05f, y))
            press()
            moveTo(Offset(bounds.width * 0.95f, y))
            release()
        }
        rule.waitForIdle()

        val jumps = jumpActions(host)
        assertEquals(1, jumps.size, "drag must commit exactly one jump")
        assertEquals(2, jumps.single().pageIndex)
        assertEquals(2, host.state.pageIndex)
    }

    @Test
    fun dialogCloseRestoresInvokerFocus() {
        val host = ChromeTestHost(demoState())
        setHostContent(host)

        val chapters = rule.onNodeWithTag(ReaderChromeChaptersTag)
        chapters.performSemanticsAction(SemanticsActions.RequestFocus)
        chapters.assertIsFocused()

        chapters.performClick()
        rule.onNodeWithTag(ReaderChromeChapterDialogTag).assertIsDisplayed()

        // Close by picking the current chapter (the production close path for picks).
        rule.onNodeWithTag(ReaderChromeChapterCurrentTag).performClick()
        rule.onNodeWithTag(ReaderChromeChapterDialogTag).assertDoesNotExist()
        chapters.assertIsFocused()
    }

    @Test
    fun keyboardEnterOpensDialogFromFocusedButton() {
        val host = ChromeTestHost(demoState())
        setHostContent(host)

        val chapters = rule.onNodeWithTag(ReaderChromeChaptersTag)
        chapters.performSemanticsAction(SemanticsActions.RequestFocus)
        chapters.performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        rule.onNodeWithTag(ReaderChromeChapterDialogTag).assertIsDisplayed()
    }

    @Test
    fun cropToggleEmitsWorkOverridePatch() {
        val host = ChromeTestHost(demoState())
        setHostContent(host)

        rule.onNodeWithTag(ReaderChromeCropTag).performClick()
        val patches = host.actions.filterIsInstance<ReaderChromeAction.ChangeWorkOverride>()
        assertTrue(patches.isNotEmpty())
        assertEquals(true, host.state.effective.cropBorders)
    }
}
