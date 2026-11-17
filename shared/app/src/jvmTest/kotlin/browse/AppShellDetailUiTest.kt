package app.koyura.app.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.KomikkuCoverImageTag
import app.koyura.designsystem.ReaderTheme
import kotlin.test.Test

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
        onNodeWithTag(KomikkuCoverImageTag).assertIsDisplayed()
        // 四键行：收藏可点，追踪/网页禁用，进度无存档时禁用。
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
        onNodeWithTag("detail-chapter-download-ep_2").assertIsDisplayed()
        onNodeWithTag("reading-start").assertDoesNotExist()

        // 收藏为本地视觉态，点击不崩且保持显示。
        onNodeWithTag("detail-favorite").performClick()
        onNodeWithTag("detail-favorite").assertIsDisplayed()
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
        onNodeWithTag("source-detail-heading").assertIsDisplayed()
        onNodeWithTag("source-detail-chapter-ep_1").assertIsDisplayed()
        onNodeWithTag("source-detail-chapter-ep_2").assertIsDisplayed()
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
        onNodeWithTag("source-pin-src:en:manga").performClick()
        onNodeWithTag("source-pin-src:en:manga").assertIsDisplayed()
        kotlin.test.assertEquals(setOf("src:en:manga"), session.state.pinnedSourceIds)
        // 全部行都带置顶键，无 Latest 恒无之外的回归。
        onAllNodesWithTag("source-pin-src:ja:manga").assertCountEquals(1)
    }
}
