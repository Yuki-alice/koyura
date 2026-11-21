package app.koyura.app.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.koyura.app.reader.pngBytes
import app.koyura.app.sourceui.FakeClipboard
import app.koyura.app.sourceui.FakeUrlOpener
import app.koyura.app.sourceui.LocalSourceUiHost
import app.koyura.app.sourceui.SourceUiHost
import app.koyura.designsystem.CoverImageTag
import app.koyura.designsystem.CoverPlaceholderTag
import app.koyura.designsystem.ReaderTheme
import app.koyura.features.downloads.FakeDownloadRepository
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertNotEquals

/**
 * 详情视觉回归：工具栏返回、四键行、标签 chips、元数据、目录行（富二级行 + 禁用下载位）。
 */
class AppShellDetailUiTest {
    private val coverUrl = "https://example.invalid/ok.png"
    private val okPng: ByteArray =
        java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )

    private fun detailService(): FakeBrowseService {
        val service = FakeBrowseService()
        service.workDetails = mapOf(
            "demo" to WorkDetail(
                id = "demo",
                title = "星图",
                subtitle = "作者 · 连载中",
                cover = coverUrl,
                description = "一张星图指引的长途旅行。",
                tags = mapOf("题材" to listOf("冒险", "奇幻")),
                chapters = listOf(
                    WorkChapter(
                        id = "ep_2",
                        title = "第二章",
                        scanlator = "汉化组",
                        dateMillis = 1_794_916_800_000,
                        pageCount = 18,
                    ),
                    WorkChapter(id = "ep_1", title = "第一章"),
                ),
                updateTime = "2026-01-01",
                url = "https://example.invalid/demo",
            ),
        )
        service.coverBytes = mapOf("src-demo" to coverUrl to okPng)
        return service
    }

    @Test
    fun detailShowsToolbarCoverActionsChipsMetaAndRows() = runComposeUiTest {
        val service = detailService()
        val session = BrowseSession(service)
        setContent {
            ReaderTheme {
                SourceDetailScreen(
                    session = session,
                    sourceKey = "src-demo",
                    workId = "demo",
                    fallbackTitle = "星图",
                    onBack = {},
                )
            }
        }
        waitForIdle()

        // 顶部工具栏与可见返回键。
        onNodeWithTag(SourceDetailToolbarTag).assertIsDisplayed()
        onNodeWithTag(SourceDetailBackTag).assertIsDisplayed()
        onNodeWithTag("source-detail-heading").assertIsDisplayed()
        // 真实封面（非占位）。
        onNodeWithTag(CoverImageTag).assertIsDisplayed()
        // 四键行：收藏可点；追踪无后端保持禁用；网页有 url 时可点（fixture 带 url）；进度无存档时禁用。
        onNodeWithTag("detail-favorite").assertIsDisplayed()
        onNodeWithTag("detail-tracking").assertIsDisplayed()
        onNodeWithTag("detail-progress").assertIsDisplayed()
        onNodeWithTag("detail-webview").assertIsDisplayed()
        // 标签 chips 与元数据行。
        onNodeWithTag("detail-tags").assertIsDisplayed()
        onNodeWithTag("detail-chip-题材-0").assertIsDisplayed()
        onNodeWithTag("detail-meta").assertIsDisplayed()
        // 目录行：富二级行 + 下载位（禁用）+ 无阅读器时无开始键。
        onNodeWithTag("source-detail-chapter-ep_2").assertIsDisplayed()
        // 下载位是 40dp 纯绘制状态层，被行内可点击语义合并，只能查未合并树。
        onNodeWithTag("detail-chapter-download-ep_2", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("reading-start").assertDoesNotExist()

        // 收藏为本地视觉态，点击不崩且保持显示。
        onNodeWithTag("detail-favorite").performClick()
        onNodeWithTag("detail-favorite").assertIsDisplayed()
    }

    @Test
    fun detailDownloadButtonEnqueuesPausesAndResumesChapter() = runComposeUiTest {
        val service = detailService()
        val session = BrowseSession(service)
        val downloads = FakeDownloadRepository()
        setContent {
            ReaderTheme {
                SourceDetailScreen(
                    session = session,
                    sourceKey = "src-demo",
                    workId = "demo",
                    fallbackTitle = "星图",
                    onBack = {},
                    downloads = downloads,
                )
            }
        }
        waitForIdle()

        fun jobs() = kotlinx.coroutines.runBlocking { downloads.downloadJobs("src-demo", "demo") }
        val tag = "detail-chapter-download-ep_2"
        // 首次点按入队，再次点按暂停，第三次点按恢复。
        onNodeWithTag(tag, useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 5_000) { jobs().containsKey("ep_2") }
        onNodeWithTag(tag, useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 5_000) {
            jobs()["ep_2"]?.status == app.koyura.domain.downloads.DownloadStatus.PAUSED
        }
        onNodeWithTag(tag, useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 5_000) {
            jobs()["ep_2"]?.status == app.koyura.domain.downloads.DownloadStatus.QUEUED
        }
    }

    @Test
    fun detailWebviewButtonOpensWorkUrl() = runComposeUiTest {
        val service = detailService()
        val session = BrowseSession(service)
        val opener = FakeUrlOpener()
        setContent {
            ReaderTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSourceUiHost provides SourceUiHost(FakeClipboard(), opener),
                ) {
                    SourceDetailScreen(
                        session = session,
                        sourceKey = "src-demo",
                        workId = "demo",
                        fallbackTitle = "星图",
                        onBack = {},
                    )
                }
            }
        }
        waitForIdle()

        // fixture 详情带 url，网页键可用；点按走平台 opener。
        onNodeWithTag("detail-webview").assertIsEnabled()
        onNodeWithTag("detail-webview").performClick()
        waitUntil(timeoutMillis = 5_000) { opener.opened.contains("https://example.invalid/demo") }
    }

    @Test
    fun detailWideWindowKeepsHeaderAndChapters() = runComposeUiTest {
        val service = detailService()
        val session = BrowseSession(service)
        setContent {
            ReaderTheme {
                Box(Modifier.width(1000.dp)) {
                    SourceDetailScreen(
                        session = session,
                        sourceKey = "src-demo",
                        workId = "demo",
                        fallbackTitle = "星图",
                        onBack = {},
                    )
                }
            }
        }
        waitForIdle()

        // 宽屏双栏：左信息右目录同屏。
        onNodeWithTag("detail-master-pane").assertWidthIsEqualTo(450.dp)
        onNodeWithTag("source-detail-heading").assertIsDisplayed()
        onNodeWithTag("source-detail-chapter-ep_1").assertIsDisplayed()
        onNodeWithTag("source-detail-chapter-ep_2").assertIsDisplayed()
    }

    @Test
    fun detailThemeAndScrollEvidence() = runComposeUiTest {
        val service = detailService()
        val seeded = checkNotNull(service.workDetails["demo"]).copy(
            cover = "fixture:seed",
            chapters = List(12) { WorkChapter(id = "chapter-$it", title = "Chapter ${it + 1}") },
        )
        service.workDetails = mapOf(
            "seeded" to seeded,
            "static" to seeded.copy(cover = ""),
        )
        service.coverBytes = mapOf(("src-demo" to seeded.cover) to pngBytes(0x169773))
        val session = BrowseSession(service)
        val work = androidx.compose.runtime.mutableStateOf("static")
        setContent {
            ReaderTheme(darkTheme = false) {
                Box(Modifier.size(360.dp, 720.dp)) {
                    SourceDetailScreen(
                        session = session,
                        sourceKey = "src-demo",
                        workId = work.value,
                        fallbackTitle = "星图",
                        onBack = {},
                    )
                }
            }
        }
        onNodeWithTag(CoverPlaceholderTag).assertIsDisplayed()
        val page = onNodeWithTag(SourceDetailTestTag)
        val staticColor = page.captureToImage().corner()
        page.captureToImage().saveDetailEvidence("detail-static-top")
        runOnIdle { work.value = "seeded" }
        onNodeWithTag(CoverImageTag).assertIsDisplayed()
        waitUntil(timeoutMillis = 5_000) { page.captureToImage().corner() != staticColor }
        val seededColor = page.captureToImage().corner()
        page.captureToImage().saveDetailEvidence("detail-seed-top")
        onNodeWithTag("detail-suggestions").performScrollTo().assertIsDisplayed()
        assertNotEquals(seededColor, page.captureToImage().corner())
        page.captureToImage().saveDetailEvidence("detail-seed-scrolled")
    }

    private fun ImageBitmap.corner(): Int {
        val pixels = IntArray(1)
        readPixels(pixels, startX = width - 4, startY = 4, width = 1, height = 1)
        return pixels.single()
    }

    private fun ImageBitmap.saveDetailEvidence(name: String) {
        val dir = File(System.getProperty("reader.screenshotDir") ?: "build/visual-parity", "detail-evidence")
        dir.mkdirs()
        val pixels = IntArray(width * height)
        readPixels(pixels)
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        output.setRGB(0, 0, width, height, pixels, 0, width)
        ImageIO.write(output, "png", File(dir, "$name.png"))
    }

    @Test
    fun recentGroupAndPinRenderInSourcesPane() = runComposeUiTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        kotlinx.coroutines.runBlocking { session.refresh() }
        session.markSourceOpened("src:en:manga")
        setContent {
            ReaderTheme {
                BrowseScreen(
                    session = session,
                    onOpenExtension = {},
                    onManageStores = {},
                )
            }
        }
        waitForIdle()

        // 最近使用分组行与语言分组行共存，锚点不重复。
        onNodeWithTag("recent-source-row-src:en:manga").assertIsDisplayed()
        onNodeWithTag("source-row-src:en:manga").assertIsDisplayed()
        // 置顶为行级开关，点击后会话态更新。
        onNodeWithTag("recent-source-pin-src:en:manga").performClick()
        onNodeWithTag("recent-source-pin-src:en:manga").assertIsDisplayed()
        kotlin.test.assertEquals(setOf("src:en:manga"), session.state.pinnedSourceIds)
        // 全部行都带置顶键，无 Latest 恒无之外的回归。
        onAllNodesWithTag("source-pin-src:ja:manga").assertCountEquals(1)
    }
}
