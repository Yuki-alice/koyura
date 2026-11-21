package app.koyura.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.koyura.app.browse.BrowsePane
import app.koyura.app.browse.BrowseSession
import app.koyura.app.browse.FakeBrowseService
import app.koyura.app.browse.LocalBrowseService
import app.koyura.app.browse.WorkChapter
import app.koyura.app.browse.WorkDetail
import app.koyura.app.browse.WorkSearchPage
import app.koyura.app.browse.WorkSummary
import app.koyura.app.history.FakeHistoryService
import app.koyura.app.history.HistorySession
import app.koyura.app.history.historyEntry
import app.koyura.app.navigation.ComicReaderRoute
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.app.reader.pngBytes
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import app.koyura.domain.reader.ChapterContent
import app.koyura.features.library.FakeLibraryRepository
import app.koyura.features.library.LibraryEntry
import app.koyura.features.library.LibraryEntryKey
import app.koyura.features.library.LibraryKind
import app.koyura.features.library.LibraryPrefs
import app.koyura.features.library.LocalLibraryRepository
import app.koyura.features.library.defaultLibraryCategories
import app.koyura.features.reader.comic.FakeReaderService
import app.koyura.features.reader.comic.ReaderStoreFactory
import app.koyura.features.reader.comic.ReaderStoreHolder
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

/**
 * V0-05 目标画布基线重拍：360×720（手机窄）/ 1280×800（平板宽）× 浅 / 深。
 *
 * 既有截图用例画布写死（420 / 1100 / 1280，多数仅浅色），`reader.screenshotDir`
 * 虽可经 `-PreaderScreenshotDir` 传参，宽高无传参入口，故此处只做加法新增：
 * 五类主页面（书架 / 浏览 / 详情 / 历史 / 阅读器）× 窄 / 宽 × 浅 / 深 = 20 张，
 * 输出到 `-PreaderScreenshotDir=build/v0-05` 指定的新目录，不覆盖既有输出，
 * 不动既有文件与断言。
 *
 * Run with `-PreaderScreenshotDir=build/v0-05`.
 */
class V005BaselineCanvasesTest {
    private val outputDir = File(System.getProperty("reader.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun render(
        name: String,
        width: Int,
        height: Int,
        dark: Boolean,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
                ReaderTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize().background(if (dark) Color.Black else Color.White)) { content() }
                }
            }
            try {
                scene.render(2_000_000_000)
                // Let LaunchedEffect refreshes (browse/history/detail/library/reader) land before capturing.
                Thread.sleep(800)
                val image = scene.render(3_000_000_000)
                File(outputDir, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            } finally {
                scene.close()
            }
        }
    }

    private fun demoBrowseService(): FakeBrowseService {
        val service = FakeBrowseService()
        service.searchPages = mapOf(
            "star" to WorkSearchPage(
                listOf(
                    WorkSummary(id = "demo", title = "星图", subtitle = "作者", cover = "", tags = listOf("冒险")),
                    WorkSummary(id = "night", title = "夜行记", subtitle = "作者二", cover = "", tags = emptyList()),
                ),
                maxPage = 1,
            ),
        )
        service.workDetails = mapOf(
            "demo" to WorkDetail(
                id = "demo",
                title = "星图",
                subtitle = "作者 · 连载中",
                cover = "",
                description = "一张星图指引的长途旅行。Komikku 详情页对齐：封面占位、标题、副标题、简介展开、开始/继续按钮、章节计数与目录行。",
                tags = mapOf("题材" to listOf("冒险", "奇幻")),
                chapters = listOf(
                    WorkChapter(id = "ep_3", title = "第三章"),
                    WorkChapter(id = "ep_2", title = "第二章"),
                    WorkChapter(id = "ep_1", title = "第一章"),
                ),
            ),
        )
        return service
    }

    private fun demoHistoryService(): FakeHistoryService {
        val now = System.currentTimeMillis()
        return FakeHistoryService(
            listOf(
                historyEntry(workId = "demo", workTitle = "星图", chapterTitle = "第二章", pageIndex = 2, readAtMillis = now),
                historyEntry(workId = "night", workTitle = "夜行记", chapterTitle = "第一话", pageIndex = 0, sourceKey = "example_source", readAtMillis = now - 86_400_000),
                historyEntry(workId = "oneshot", workTitle = "同名单话", chapterTitle = null, chapterId = null, pageIndex = 4, sourceKey = "other_source", readAtMillis = null),
            ),
        )
    }

    private fun demoLibraryRepository(): FakeLibraryRepository {
        return FakeLibraryRepository(
            entries = listOf(
                LibraryEntry(
                    sourceKey = "srcA", workId = "w1", title = "同名作品",
                    coverUrl = "https://img.example/cover.png", sourceName = "源A", language = "zh",
                    totalChapters = 10, unreadCount = 3, downloadedCount = 2,
                    lastReadAtMillis = 1000L, favoriteAtMillis = 10L,
                    continueChapterId = "c3", continueChapterTitle = "第3话", continuePageIndex = 5,
                ),
                LibraryEntry(
                    sourceKey = "srcB", workId = "w2", title = "About Broken",
                    coverUrl = "https://img.example/broken.png", sourceName = "源B", language = "en",
                    totalChapters = 4, unreadCount = 0, favoriteAtMillis = 20L,
                ),
                LibraryEntry(
                    sourceKey = "srcA", workId = "w4", title = "空封面",
                    coverUrl = "", sourceName = "源A", language = "",
                    totalChapters = 2, favoriteAtMillis = 30L,
                ),
            ),
            categories = defaultLibraryCategories(),
            links = mapOf(
                LibraryEntryKey("srcA", LibraryKind.COMIC, "w1") to setOf(1L),
                LibraryEntryKey("srcB", LibraryKind.COMIC, "w2") to setOf(1L),
                LibraryEntryKey("srcA", LibraryKind.COMIC, "w4") to setOf(3L),
            ),
            thumbnailBytes = mapOf("https://img.example/cover.png" to pngBytes(0x336699)),
            thumbnailFailures = mutableMapOf("https://img.example/broken.png" to "404"),
        )
    }

    private fun browseReadySession(service: FakeBrowseService, pane: BrowsePane = BrowsePane.Sources): BrowseSession {
        val session = BrowseSession(service)
        runBlocking { session.refresh() }
        session.selectPane(pane)
        session.searchSources("")
        return session
    }

    private fun readerTriple(): Triple<FakeBrowseService, FakeReaderService, ReaderStoreHolder> {
        val browse = FakeBrowseService()
        val reader = FakeReaderService(
            chapters = mutableMapOf(
                Triple("example_source", "demo", "ep_1") to ChapterContent(
                    chapterId = "ep_1",
                    title = "第一章",
                    pageKeys = listOf("shot0", "shot1", "shot2"),
                    startPageIndex = 0,
                ),
            ),
            pageBytes = mutableMapOf(
                "shot0" to pngBytes(0x2B3A55),
                "shot1" to pngBytes(0x7A4A2B),
                "shot2" to pngBytes(0xB5462B),
            ),
        )
        return Triple(browse, reader, ReaderStoreHolder(ReaderStoreFactory().create(reader)))
    }

    private fun canvases(): List<Triple<String, Int, Boolean>> = listOf(
        Triple("360", 360, false),
        Triple("1280", 1280, true),
    )

    @Test
    fun libraryBaseline() {
        for ((widthName, width, useRail) in canvases()) {
            for ((theme, dark) in listOf("light" to false, "dark" to true)) {
                val repository = demoLibraryRepository()
                val navigation = createAppNavigationState(initialTab = NavTab.Library)
                render("v005-library-$widthName-$theme", width, if (width == 360) 720 else 800, dark) {
                    Box(Modifier.size(width.dp, if (width == 360) 720.dp else 800.dp)) {
                        CompositionLocalProvider(LocalLibraryRepository provides repository) {
                            AppScaffold(navigation = navigation, useRail = useRail)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun browseBaseline() {
        for ((widthName, width, useRail) in canvases()) {
            for ((theme, dark) in listOf("light" to false, "dark" to true)) {
                val service = demoBrowseService()
                val navigation = createAppNavigationState(initialTab = NavTab.Browse)
                val session = browseReadySession(service)
                render("v005-browse-$widthName-$theme", width, if (width == 360) 720 else 800, dark) {
                    Box(Modifier.size(width.dp, if (width == 360) 720.dp else 800.dp)) {
                        CompositionLocalProvider(LocalBrowseService provides service) {
                            AppScaffold(navigation = navigation, useRail = useRail, browseService = service, browseSession = session)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun detailBaseline() {
        for ((widthName, width, useRail) in canvases()) {
            for ((theme, dark) in listOf("light" to false, "dark" to true)) {
                val service = demoBrowseService()
                val navigation = createAppNavigationState(initialTab = NavTab.Browse)
                navigation.push(app.koyura.app.navigation.SourceDetailRoute("example_source", "demo", "星图"))
                val session = browseReadySession(service)
                render("v005-detail-$widthName-$theme", width, if (width == 360) 720 else 800, dark) {
                    Box(Modifier.size(width.dp, if (width == 360) 720.dp else 800.dp)) {
                        CompositionLocalProvider(LocalBrowseService provides service) {
                            AppScaffold(navigation = navigation, useRail = useRail, browseService = service, browseSession = session)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun historyBaseline() {
        for ((widthName, width, useRail) in canvases()) {
            for ((theme, dark) in listOf("light" to false, "dark" to true)) {
                val history = demoHistoryService()
                val navigation = createAppNavigationState(initialTab = NavTab.History)
                val session = HistorySession(history)
                render("v005-history-$widthName-$theme", width, if (width == 360) 720 else 800, dark) {
                    Box(Modifier.size(width.dp, if (width == 360) 720.dp else 800.dp)) {
                        AppScaffold(
                            navigation = navigation,
                            useRail = useRail,
                            browseService = null,
                            historyService = history,
                            historySession = session,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun readerBaseline() {
        for ((widthName, width, useRail) in canvases()) {
            for ((theme, dark) in listOf("light" to false, "dark" to true)) {
                val (browse, reader, holder) = readerTriple()
                val navigation = createAppNavigationState(initialTab = NavTab.Browse)
                navigation.push(
                    ComicReaderRoute(
                        sourceKey = "example_source",
                        workId = "demo",
                        chapterId = "ep_1",
                        title = "星图",
                        chapterIds = listOf("ep_1"),
                        chapterTitles = listOf("第一章"),
                    ),
                )
                val browseSession = BrowseSession(browse)
                render("v005-reader-$widthName-$theme", width, if (width == 360) 720 else 800, dark) {
                    Box(Modifier.size(width.dp, if (width == 360) 720.dp else 800.dp)) {
                        AppScaffold(
                            navigation,
                            useRail = useRail,
                            browseService = browse,
                            browseSession = browseSession,
                            readerService = reader,
                            readerHolder = holder,
                        )
                    }
                }
            }
        }
    }
}
