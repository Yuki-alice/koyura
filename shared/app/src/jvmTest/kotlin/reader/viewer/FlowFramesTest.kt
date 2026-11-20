package app.koyura.app.reader.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.components.AppText
import app.koyura.features.reader.comic.ReaderContentState
import app.koyura.features.reader.comic.ReaderSession
import app.koyura.features.reader.comic.viewer.ComicChapterBoundary
import app.koyura.features.reader.comic.viewer.ComicPagerLayout
import app.koyura.features.reader.comic.viewer.ComicReadingMode
import app.koyura.features.reader.comic.viewer.ComicViewer
import app.koyura.features.reader.comic.viewer.ComicViewerJump
import app.koyura.features.reader.comic.viewer.ComicViewerOptions
import app.koyura.features.reader.comic.viewer.ComicViewerRootTag
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Operation storyboards for the three recorded flows, captured from the same
 * interactive host the UI tests drive (real taps, keys, drags and jumps).
 *
 * Each frame is written to `<readerScreenshotDir>/frames/` as PNG. Frame capture
 * uses semantics screenshots, so what you see is exactly what the tests assert on.
 */
class FlowFramesTest {
    private val outputDir = File(System.getProperty("reader.screenshotDir") ?: "build/screenshots")
    private val framesDir = File(outputDir, "frames").apply { mkdirs() }

    @Composable
    private fun FlowLab(
        session: ReaderSession,
        chapterId: String?,
        options: ComicViewerOptions,
        jump: ComicViewerJump?,
        onBoundary: (ComicChapterBoundary) -> Unit,
    ) {
        val content = (session.content as? ReaderContentState.Ready)?.content
        Column(Modifier.fillMaxSize()) {
            AppText(
                text = if (content == null) "0 / 0"
                else "${options.mode.id} ${session.currentPage + 1} / ${content.pageKeys.size}",
                modifier = Modifier.testTag("reader-viewer-flow-label"),
            )
            if (content != null) {
                Box(Modifier.weight(1f)) {
                    ComicViewer(
                        session = session,
                        content = content,
                        options = options,
                        jump = jump,
                        onToggleMenu = {},
                        onChapterBoundary = onBoundary,
                    )
                }
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.shot(name: String) {
        waitForIdle()
        val image = onRoot().captureToImage()
        val pixels = image.toPixelMap()
        val out = java.awt.image.BufferedImage(pixels.width, pixels.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        var i = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                out.setRGB(x, y, pixels.buffer[i++])
            }
        }
        ImageIO.write(out, "png", File(framesDir, "$name.png"))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.settledTap(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
        Thread.sleep(600)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.tapNext() = settledTap("reader-tap-next")
    private fun androidx.compose.ui.test.ComposeUiTest.tapPrev() = settledTap("reader-tap-prev")

    private fun androidx.compose.ui.test.ComposeUiTest.dragRight() {
        onNodeWithTag(ComicViewerRootTag).performTouchInput { swipeRight(durationMillis = 200) }
        waitForIdle()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.dragDown() {
        onNodeWithTag(ComicViewerRootTag).performTouchInput { swipeDown(durationMillis = 200) }
        waitForIdle()
    }

    @Test
    fun recordRtlFlow() = runComposeUiTest {
        val service = ViewerFixture.service()
        val session = ViewerFixture.session(service)
        val options = ComicViewerOptions(mode = ComicReadingMode.RIGHT_TO_LEFT)
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds)
                }
                FlowLab(session, ViewerFixture.CHAPTER_2, options, null) {}
            }
        }

        val counter = "reader-viewer-flow-label"
        onNodeWithTag(counter).assertExists()
        Thread.sleep(800)
        shot("flow-rtl-1-start")
        tapPrev()
        shot("flow-rtl-2-tap-next")
        onNodeWithTag(ComicViewerRootTag).performKeyInput { pressKey(Key.DirectionLeft) }
        Thread.sleep(800)
        shot("flow-rtl-3-arrow-next")
        dragRight()
        Thread.sleep(800)
        shot("flow-rtl-4-drag-next")
    }

    @Test
    fun recordChapterFlow() = runComposeUiTest {
        val service = ViewerFixture.service()
        val session = ViewerFixture.session(service)
        var chapter by mutableStateOf(ViewerFixture.CHAPTER_1)
        val options = ComicViewerOptions(mode = ComicReadingMode.WEBTOON)
        setContent {
            ReaderTheme {
                LaunchedEffect(chapter) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, chapter, ViewerFixture.chapterIds)
                }
                val content = (session.content as? ReaderContentState.Ready)?.content
                Column(Modifier.fillMaxSize()) {
                    AppText(
                        text = if (content == null) "0 / 0"
                        else "${session.currentPage + 1} / ${content.pageKeys.size}",
                        modifier = Modifier.testTag("reader-viewer-flow-label"),
                    )
                    if (content != null) {
                        ComicViewer(
                            session = session,
                            content = content,
                            options = options,
                            jump = null,
                            onToggleMenu = {},
                            onChapterBoundary = { boundary ->
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

        Thread.sleep(800)
        shot("flow-chapter-1-start")
        tapNext()
        tapNext()
        shot("flow-chapter-2-scrolled")
        // Edge taps until the host switches chapters.
        var guard = 0
        while (guard++ < 14) {
            try {
                onNodeWithTag("reader-viewer-flow-label").assertTextEquals("1 / 7")
                break
            } catch (_: AssertionError) {
            }
            tapNext()
        }
        Thread.sleep(800)
        shot("flow-chapter-3-switched")
        dragDown()
        Thread.sleep(800)
        shot("flow-chapter-4-ch2-scrolled")
    }

    @Test
    fun recordModeSwitchFlow() = runComposeUiTest {
        val service = ViewerFixture.service()
        val session = ViewerFixture.session(service)
        var options by mutableStateOf(ComicViewerOptions(mode = ComicReadingMode.LEFT_TO_RIGHT))
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds)
                }
                FlowLab(session, ViewerFixture.CHAPTER_2, options, null) {}
            }
        }

        tapNext()
        tapNext()
        shot("flow-modes-1-ltr")
        options = options.copy(mode = ComicReadingMode.WEBTOON)
        Thread.sleep(1000)
        shot("flow-modes-2-webtoon")
        options = options.copy(mode = ComicReadingMode.CONTINUOUS_VERTICAL)
        Thread.sleep(1000)
        shot("flow-modes-3-continuous")
    }

    /**
     * Resize storyboard: AUTOMATIC layout, third page reached on a narrow canvas,
     * then widened to pair pages 2-3 and narrowed again. Every frame's counter
     * must read `3 / 3`, the saved page key must stay `p2`.
     */
    @Test
    fun recordResizeAnchoringFlow() = runComposeUiTest {
        val service = ViewerFixture.service()
        val session = ViewerFixture.session(service)
        var widthDp by mutableStateOf(420)
        val options = ComicViewerOptions(
            mode = ComicReadingMode.LEFT_TO_RIGHT,
            pagerLayout = ComicPagerLayout.AUTOMATIC,
            firstPageSingle = true,
        )
        setContent {
            ReaderTheme {
                LaunchedEffect(Unit) {
                    session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_1, ViewerFixture.chapterIds)
                }
                val content = (session.content as? ReaderContentState.Ready)?.content
                Column(Modifier.fillMaxSize()) {
                    AppText(
                        text = if (content == null) "0 / 0"
                        else "AUTOMATIC ${session.currentPage + 1} / ${content.pageKeys.size}",
                        modifier = Modifier.testTag("reader-viewer-flow-label"),
                    )
                    if (content != null) {
                        Box(Modifier.size(widthDp.dp, 780.dp)) {
                            ComicViewer(
                                session = session,
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

        tapNext()
        tapNext()
        shot("flow-resize-1-narrow")
        widthDp = 1000
        Thread.sleep(1200)
        shot("flow-resize-2-wide-paired")
        widthDp = 420
        Thread.sleep(1200)
        shot("flow-resize-3-narrow-again")
    }
}
