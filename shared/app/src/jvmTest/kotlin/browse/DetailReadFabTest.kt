package app.koyura.app.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.ReaderTheme
import app.koyura.features.library.FakeLibraryRepository
import app.koyura.features.library.LibraryPrefs
import app.koyura.features.library.ReadButtonStart
import app.koyura.features.reader.comic.FakeReaderService
import app.koyura.features.reader.comic.demoReaderService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 详情页继续阅读 FAB：显隐、点击动作、吸边判定与位置落盘。
 *
 * 拖动手势在 JVM 语义测试里用 swipe 模拟；真实手感与边缘吸附动画待真机验收
 *（报告登记）。
 */
class DetailReadFabTest {
    private val coverUrl = "https://example.invalid/ok.png"

    private fun detailService() = FakeBrowseService().apply {
        workDetails = mapOf(
            "demo" to WorkDetail(
                id = "demo",
                title = "星图",
                subtitle = "作者 · 连载中",
                cover = coverUrl,
                description = "简介",
                tags = mapOf("题材" to listOf("冒险")),
                chapters = listOf(
                    WorkChapter(id = "ep_2", title = "第二章"),
                    WorkChapter(id = "ep_1", title = "第一章"),
                ),
            ),
        )
    }

    private fun snapUnchangedOnCenter(): Boolean {
        // 中心恰好半分位时按右侧处理（不吸左）。
        assertFalse(snapFabToStart(fabCenterX = 180f, layoutWidth = 360f))
        assertTrue(snapFabToStart(fabCenterX = 179f, layoutWidth = 360f))
        assertTrue(snapFabToStart(fabCenterX = 10f, layoutWidth = 360f))
        assertFalse(snapFabToStart(fabCenterX = 350f, layoutWidth = 360f))
        return true
    }

    @Test
    fun snapRuleUsesCenterAgainstMidpoint() {
        assertTrue(snapUnchangedOnCenter())
    }

    @Test
    fun fabHiddenWithoutReaderService() = runComposeUiTest {
        val session = BrowseSession(detailService())
        setContent {
            ReaderTheme {
                SourceDetailScreen(
                    session = session,
                    sourceKey = "src-demo",
                    workId = "demo",
                    fallbackTitle = "星图",
                    onBack = {},
                    readerService = null,
                )
            }
        }
        waitForIdle()

        onAllNodesWithTag(DetailFabTag).assertCountEquals(0)
        onAllNodesWithTag("reading-start").assertCountEquals(0)
    }

    @Test
    fun fabVisibleWithReaderAndStartsFirstChapter() = runComposeUiTest {
        val library = FakeLibraryRepository(prefs = LibraryPrefs())
        val reader: FakeReaderService = demoReaderService()
        var opened: Pair<String?, List<String>>? = null
        val session = BrowseSession(detailService())
        setContent {
            ReaderTheme {
                Box(Modifier.width(400.dp)) {
                    SourceDetailScreen(
                        session = session,
                        sourceKey = "src-demo",
                        workId = "demo",
                        fallbackTitle = "星图",
                        onBack = {},
                        readerService = reader,
                        librarySession = app.koyura.features.library.LibrarySession(library),
                        onOpenChapter = { chapterId, chapterIds, _ -> opened = chapterId to chapterIds },
                    )
                }
            }
        }
        waitForIdle()

        onNodeWithTag(DetailFabTag).assertIsDisplayed()
        onNodeWithTag(DetailFabTag).performClick()
        waitForIdle()

        assertEquals("ep_2", opened?.first)
        assertEquals(listOf("ep_2", "ep_1"), opened?.second)
    }

    @Test
    fun fabDragPersistsSnappedSideToLibraryPrefs() = runComposeUiTest {
        val library = FakeLibraryRepository(prefs = LibraryPrefs())
        val librarySession = app.koyura.features.library.LibrarySession(library)
        val session = BrowseSession(detailService())
        setContent {
            ReaderTheme {
                Box(Modifier.width(400.dp)) {
                    SourceDetailScreen(
                        session = session,
                        sourceKey = "src-demo",
                        workId = "demo",
                        fallbackTitle = "星图",
                        onBack = {},
                        readerService = demoReaderService(),
                        librarySession = librarySession,
                        onOpenChapter = { _, _, _ -> },
                    )
                }
            }
        }
        waitForIdle()

        // 默认吸附右侧；向左拖过画布中点 → 落盘 Start。
        onNodeWithTag(DetailFabTag).performTouchInput {
            down(center)
            moveBy(Offset(-260f, 0f))
            up()
        }
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { library.prefs.readButtonPosition == ReadButtonStart }
        assertEquals(ReadButtonStart, library.prefs.readButtonPosition)

        // 再向右拖回 → 落盘 End。
        onNodeWithTag(DetailFabTag).performTouchInput {
            down(center)
            moveBy(Offset(260f, 0f))
            up()
        }
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { library.prefs.readButtonPosition == "End" }
        assertEquals("End", library.prefs.readButtonPosition)
    }

    @Test
    fun fabRestoresPersistedStartSide() = runComposeUiTest {
        val library = FakeLibraryRepository(prefs = LibraryPrefs(readButtonPosition = ReadButtonStart))
        val librarySession = app.koyura.features.library.LibrarySession(library)
        // 偏好随快照加载：先刷新一次再组合。
        kotlinx.coroutines.runBlocking { librarySession.refresh() }
        val session = BrowseSession(detailService())
        setContent {
            ReaderTheme {
                Box(Modifier.width(400.dp)) {
                    SourceDetailScreen(
                        session = session,
                        sourceKey = "src-demo",
                        workId = "demo",
                        fallbackTitle = "星图",
                        onBack = {},
                        readerService = demoReaderService(),
                        librarySession = librarySession,
                        onOpenChapter = { _, _, _ -> },
                    )
                }
            }
        }
        waitForIdle()

        // 位置偏好在构建时生效：FAB 呈现在左侧（x 起点小于画布中点）。
        val node = onNodeWithTag(DetailFabTag).fetchSemanticsNode()
        assertTrue(node.positionInRoot.x < 200f, "expected FAB at start, x=${node.positionInRoot.x}")
    }
}
