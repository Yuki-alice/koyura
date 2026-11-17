package app.koyura.app.reader.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.ReaderTheme
import app.koyura.features.reader.comic.FakeReaderService
import app.koyura.features.reader.comic.ReaderContentState
import app.koyura.features.reader.comic.ReaderSession
import app.koyura.features.reader.comic.viewer.ComicPagerLayout
import app.koyura.features.reader.comic.viewer.ComicReadingMode
import app.koyura.features.reader.comic.viewer.ComicScaleFit
import app.koyura.features.reader.comic.viewer.ComicViewer
import app.koyura.features.reader.comic.viewer.ComicViewerJump
import app.koyura.features.reader.comic.viewer.ComicViewerOptions
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

/**
 * Same-data, same-canvas captures of the B-viewer host.
 *
 * All shots use [ViewerFixture] chapter 2 (seven numbered pages: cover, wide spread,
 * portrait, long strip, portrait, retry page, odd tail), so reviewers can compare
 * modes, double-page pairing, the long strip and the failure affordance directly.
 * Canvas sizes match the production narrow/wide hosts (420x780 / 1280x800, density 1).
 */
class ComicViewerScreenshotTest {
    private val outputDir = File(System.getProperty("reader.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun openSession(
        chapterId: String = ViewerFixture.CHAPTER_2,
        service: FakeReaderService = ViewerFixture.service(),
        chapterIds: List<String> = ViewerFixture.chapterIds,
    ): ReaderSession {
        val session = ViewerFixture.session(service)
        runBlocking {
            session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, chapterId, chapterIds)
        }
        return session
    }

    private fun render(
        name: String,
        widthDp: Int,
        heightDp: Int,
        session: ReaderSession,
        options: ComicViewerOptions,
        jumpPage: Int? = null,
    ) {
        val content = (session.content as? ReaderContentState.Ready)?.content
            ?: error("chapter not opened for $name")
        val jump = jumpPage?.let { ComicViewerJump(requestId = 1000L + it, contentSeq = session.contentSeq, pageIndex = it) }
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(
                width = widthDp,
                height = heightDp,
                density = Density(1f),
            ) {
                ReaderTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        Column(Modifier.fillMaxSize()) {
                            Text(
                                text = "${options.mode.id} ${options.pagerLayout.id} ${session.currentPage + 1} / ${content.pageKeys.size}",
                                color = Color.White,
                                modifier = Modifier.testTag("reader-viewer-shot-label"),
                            )
                            ComicViewer(
                                session = session,
                                content = content,
                                options = options,
                                jump = jump,
                                onToggleMenu = {},
                                onChapterBoundary = {},
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            try {
                // Let the open, jump and first page loads settle before capturing.
                scene.render(2_000_000_000)
                scene.render(2_500_000_000)
                val image = scene.render(3_000_000_000)
                File(outputDir, "$name.png").writeBytes(
                    image.encodeToData(EncodedImageFormat.PNG)!!.bytes,
                )
            } finally {
                scene.close()
            }
        }
    }

    private fun options(
        mode: ComicReadingMode,
        layout: ComicPagerLayout = ComicPagerLayout.SINGLE_PAGE,
        invert: Boolean = false,
        fit: ComicScaleFit = ComicScaleFit.FIT,
    ) = ComicViewerOptions(
        mode = mode,
        pagerLayout = layout,
        firstPageSingle = true,
        invertDoublePages = invert,
        scaleFit = fit,
    )

    @Test
    fun captureLtrNarrow() {
        val session = openSession()
        render("viewer-ltr-narrow", 420, 780, session, options(ComicReadingMode.LEFT_TO_RIGHT))
    }

    @Test
    fun captureRtlNarrow() {
        val session = openSession()
        render("viewer-rtl-narrow", 420, 780, session, options(ComicReadingMode.RIGHT_TO_LEFT))
    }

    @Test
    fun captureVerticalNarrow() {
        val session = openSession()
        render("viewer-vertical-narrow", 420, 780, session, options(ComicReadingMode.VERTICAL))
    }

    @Test
    fun captureWebtoonNarrow() {
        val session = openSession()
        render("viewer-webtoon-narrow", 420, 780, session, options(ComicReadingMode.WEBTOON))
    }

    @Test
    fun captureContinuousNarrow() {
        val session = openSession()
        render(
            "viewer-continuous-narrow", 420, 780, session,
            options(ComicReadingMode.CONTINUOUS_VERTICAL),
        )
    }

    @Test
    fun captureDoubleWide() {
        val session = openSession()
        render(
            "viewer-double-wide", 1280, 800, session,
            options(ComicReadingMode.LEFT_TO_RIGHT, ComicPagerLayout.DOUBLE_PAGES),
            // Pair [q2, q3]: proves adjacent pairing with the cover solo and the
            // wide q1 isolated after decode. Page 3 is the master in both the
            // pre-decode and post-decode pairings, so the jump lands stably.
            jumpPage = 3,
        )
    }

    @Test
    fun captureDoubleWideInverted() {
        val session = openSession()
        render(
            "viewer-double-wide-inverted", 1280, 800, session,
            options(ComicReadingMode.RIGHT_TO_LEFT, ComicPagerLayout.DOUBLE_PAGES, invert = true),
            jumpPage = 3,
        )
    }

    @Test
    fun captureDoubleWideSpread() {
        val session = openSession()
        render(
            "viewer-double-wide-spread", 1280, 800, session,
            options(ComicReadingMode.LEFT_TO_RIGHT, ComicPagerLayout.DOUBLE_PAGES),
            // The 800x400 landscape q1 is isolated solo once decoded, mirroring
            // Komikku's runtime page-split for wide images.
            jumpPage = 1,
        )
    }

    @Test
    fun captureWebtoonLongStrip() {
        val session = openSession()
        render(
            "viewer-webtoon-long", 420, 780, session,
            options(ComicReadingMode.WEBTOON),
            jumpPage = 3,
        )
    }

    @Test
    fun capturePagerLongFitWidth() {
        val session = openSession()
        render(
            "viewer-pager-long-fitwidth", 420, 780, session,
            options(ComicReadingMode.LEFT_TO_RIGHT, fit = ComicScaleFit.FIT_WIDTH),
            jumpPage = 3,
        )
    }

    @Test
    fun captureFailedThenRetried() {
        val service = ViewerFixture.service()
        val session = ViewerFixture.session(service)
        runBlocking {
            session.open(ViewerFixture.SOURCE, ViewerFixture.WORK, ViewerFixture.CHAPTER_2, ViewerFixture.chapterIds)
        }
        render(
            "viewer-failed", 420, 780, session,
            options(ComicReadingMode.LEFT_TO_RIGHT),
            jumpPage = 5,
        )
        runBlocking { session.retryPage("q5") }
        render(
            "viewer-failed-retried", 420, 780, session,
            options(ComicReadingMode.LEFT_TO_RIGHT),
            jumpPage = 5,
        )
    }

    @Test
    fun captureCropOff() {
        val service = ViewerFixture.cropService()
        val session = openSession(ViewerFixture.CROP_CHAPTER, service, ViewerFixture.cropChapterIds)
        render(
            "viewer-crop-off", 420, 780, session,
            ComicViewerOptions(
                mode = ComicReadingMode.LEFT_TO_RIGHT,
                pagerLayout = ComicPagerLayout.SINGLE_PAGE,
                cropBorders = false,
            ),
            jumpPage = 0,
        )
    }

    @Test
    fun captureCropOffBorderless() {
        val service = ViewerFixture.cropService()
        val session = openSession(ViewerFixture.CROP_CHAPTER, service, ViewerFixture.cropChapterIds)
        render(
            "viewer-crop-off-borderless", 420, 780, session,
            ComicViewerOptions(
                mode = ComicReadingMode.LEFT_TO_RIGHT,
                pagerLayout = ComicPagerLayout.SINGLE_PAGE,
                cropBorders = false,
            ),
            jumpPage = 1,
        )
    }

    @Test
    fun captureCropOnBorderless() {
        val service = ViewerFixture.cropService()
        val session = openSession(ViewerFixture.CROP_CHAPTER, service, ViewerFixture.cropChapterIds)
        render(
            "viewer-crop-on-borderless", 420, 780, session,
            ComicViewerOptions(
                mode = ComicReadingMode.LEFT_TO_RIGHT,
                pagerLayout = ComicPagerLayout.SINGLE_PAGE,
                cropBorders = true,
            ),
            jumpPage = 1,
        )
    }

    @Test
    fun captureCropOn() {
        val service = ViewerFixture.cropService()
        val session = openSession(ViewerFixture.CROP_CHAPTER, service, ViewerFixture.cropChapterIds)
        render(
            "viewer-crop-on", 420, 780, session,
            ComicViewerOptions(
                mode = ComicReadingMode.LEFT_TO_RIGHT,
                pagerLayout = ComicPagerLayout.SINGLE_PAGE,
                cropBorders = true,
            ),
            jumpPage = 0,
        )
    }
}
