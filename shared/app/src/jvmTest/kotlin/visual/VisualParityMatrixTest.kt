package app.koyura.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import app.koyura.app.navigation.AppNavigationState
import app.koyura.app.navigation.CategoriesRoute
import app.koyura.app.navigation.ComicReaderRoute
import app.koyura.app.navigation.DownloadQueueRoute
import app.koyura.app.navigation.GlobalSearchRoute
import app.koyura.app.navigation.SourceDetailRoute
import app.koyura.app.navigation.StatisticsRoute
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.app.reader.pngBytes
import app.koyura.app.visual.GoldenDiffAssertions
import app.koyura.app.visual.GoldenDiffAssertions.ChromeExpectation
import app.koyura.app.visual.GoldenDiffAssertions.ThemePalette
import app.koyura.app.visual.VisualMatrixCatalog
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import app.koyura.domain.reader.ChapterContent
import app.koyura.features.library.FakeLibraryRepository
import app.koyura.features.library.LibraryEntry
import app.koyura.features.library.LibraryEntryKey
import app.koyura.features.library.LibraryKind
import app.koyura.features.library.LocalLibraryRepository
import app.koyura.features.library.defaultLibraryCategories
import app.koyura.features.reader.comic.FakeReaderService
import app.koyura.features.reader.comic.ReaderSession
import app.koyura.features.updates.FakeUpdatesRepository
import app.koyura.features.updates.LocalUpdatesRepository
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

/**
 * P0-3 视觉对齐矩阵：15 页 × 窄(360×800)/宽(1280×800) × 浅/深 = 60 张确定性 PNG。
 *
 * 输出目录取 `reader.screenshotDir`（默认 `build/visual-parity`），文件名规则见
 * [VisualMatrixCatalog]：`visual-<slug>-<narrow|wide>-<light|dark>.png`。
 *
 * 确定性保证：固定 Density(1f)、固定 ReaderTheme、固定 fixture（无 System.currentTimeMillis、
 * 无随机数）；每个用例都新建独立的 navigation / session，避免状态跨形态或主题泄漏。
 * 导航定位只用 `createAppNavigationState()` + `select(NavTab.X)` / `push(Route)`，
 * 优先走真实 `AppScaffold()` 装配；四个尚未实现的页面渲染为明确标注的占位画面。
 *
 * 每张截图都经过 [GoldenDiffAssertions.assertGolden] 的真实断言（尺寸 / 背景采样 /
 * 非背景比例 / 底部导航条或左侧 rail 区块），不存在零断言截图。
 */
class VisualParityMatrixTest {

    private val outputDir = File(System.getProperty("reader.screenshotDir") ?: "build/visual-parity").apply { mkdirs() }

    /** 白皮书 720 dp 断点：360 用底部导航条，1280 用左侧 rail。 */
    private val railWidthThreshold = 720

    /** 固定 fixture 用到的确定色（封面 / 阅读器页），供调色板采样断言放行。 */
    private val fixtureArgb = setOf(
        0x336699,
        0x2B3A55,
        0x7A4A2B,
        0xB5462B,
    )

    private fun renderOne(
        name: String,
        width: Int,
        height: Int,
        dark: Boolean,
        content: @Composable () -> Unit,
    ) {
        val palette = if (dark) ThemePalette.dark() else ThemePalette.light()
        val chrome = if (width < railWidthThreshold) ChromeExpectation.BottomBar else ChromeExpectation.Rail
        // Compose Desktop 会触碰 AWT 资源，必须在 EDT 上渲染与断言。
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(
                width = width,
                height = height,
                density = Density(1f),
            ) {
                ReaderTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize().background(Color(palette.backgroundArgb))) {
                        content()
                    }
                }
            }
            try {
                // 第一帧装载，第二帧在 2.5 s 处落定 AnimatedContent / NavDisplay 的进入动画。
                scene.render(2_000_000_000)
                // 让 browse / history 等 LaunchedEffect 刷新落定，与既有截图用例保持一致。
                Thread.sleep(800)
                val image = scene.render(2_500_000_000)
                val bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
                File(outputDir, "$name.png").writeBytes(bytes)
                // 从落盘 PNG 再解码一次：得到已分配像素的 raster bitmap，readPixels 稳定；
                // 同时断言对象正是写盘的那张 PNG，而不是尚未落定的 scene surface。
                val stableImage = org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                GoldenDiffAssertions.assertGolden(
                    image = stableImage,
                    expectedWidth = width,
                    expectedHeight = height,
                    palette = palette,
                    chrome = chrome,
                    extraAllowedArgb = fixtureArgb,
                )
            } finally {
                scene.close()
            }
        }
    }

    /** 一个页面用例展开成 4 态：窄/宽 × 浅/深。每态都新建内容工厂返回的全新装配。 */
    private fun matrix(
        slug: String,
        contentFactory: (width: Int) -> @Composable () -> Unit,
    ) {
        // 保证生成文件名与白皮书对照目录一一对应，避免报告里出现孤儿键。
        check(VisualMatrixCatalog.pages.any { it.slug == slug }) { "slug $slug 不在 VisualMatrixCatalog 中" }
        for ((width, form) in listOf(360 to "narrow", 1280 to "wide")) {
            for ((dark, theme) in listOf(false to "light", true to "dark")) {
                renderOne(
                    name = "visual-$slug-$form-$theme",
                    width = width,
                    height = CANVAS_HEIGHT,
                    dark = dark,
                    content = contentFactory(width),
                )
            }
        }
    }

    /** 未实现页面的确定性占位画面：居中卡片明写「未实现占位」，shell 仍走真实装配。 */
    private fun placeholderContent(
        navigation: AppNavigationState,
        useRail: Boolean,
        label: String,
    ): @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            AppScaffold(navigation = navigation, useRail = useRail)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(16.dp),
                ) {
                    // 主色条让「未实现占位」在 golden-diff 的非背景比例上也有足够信号。
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = "未实现占位 / PLACEHOLDER",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    // ---- 1. 引导 Onboarding（未实现占位） ----
    @Test
    fun onboardingPlaceholderMatrix() = matrix("onboarding-placeholder", { width ->
        val navigation = createAppNavigationState()
        navigation.select(NavTab.More)
        placeholderContent(navigation, width >= railWidthThreshold, "引导 Onboarding")
    })

    // ---- 2. 书架 Library ----
    @Test
    fun libraryMatrix() = matrix("library", { width ->
        val repository = demoLibraryRepository()
        val navigation = createAppNavigationState()
        navigation.select(NavTab.Library)
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalLibraryRepository provides repository) {
                AppScaffold(navigation = navigation, useRail = width >= railWidthThreshold)
            }
        }
        content
    })

    // ---- 3. 浏览 Browse ----
    @Test
    fun browseMatrix() = matrix("browse", { width ->
        val service = demoBrowseService()
        val navigation = createAppNavigationState()
        navigation.select(NavTab.Browse)
        val session = browseReadySession(service)
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalBrowseService provides service) {
                AppScaffold(
                    navigation = navigation,
                    useRail = width >= railWidthThreshold,
                    browseService = service,
                    browseSession = session,
                )
            }
        }
        content
    })

    // ---- 4. 详情 Detail ----
    @Test
    fun detailMatrix() = matrix("detail", { width ->
        val service = demoBrowseService()
        val navigation = createAppNavigationState()
        navigation.select(NavTab.Browse)
        navigation.push(SourceDetailRoute("example_source", "demo", "星图"))
        val session = browseReadySession(service)
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalBrowseService provides service) {
                AppScaffold(
                    navigation = navigation,
                    useRail = width >= railWidthThreshold,
                    browseService = service,
                    browseSession = session,
                )
            }
        }
        content
    })

    // ---- 5. 阅读器 Reader（全屏；断言仍检查对应区域的非空内容） ----
    @Test
    fun readerMatrix() = matrix("reader", { width ->
        val (browse, reader, readerSession) = readerTriple()
        val navigation = createAppNavigationState()
        navigation.select(NavTab.Browse)
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
        val content: @Composable () -> Unit = {
            AppScaffold(
                navigation = navigation,
                useRail = width >= railWidthThreshold,
                browseService = browse,
                browseSession = browseSession,
                readerService = reader,
                readerSession = readerSession,
            )
        }
        content
    })

    // ---- 6. 历史 History ----
    @Test
    fun historyMatrix() = matrix("history", { width ->
        val history = demoHistoryService()
        val navigation = createAppNavigationState()
        navigation.select(NavTab.History)
        val session = HistorySession(history)
        val content: @Composable () -> Unit = {
            AppScaffold(
                navigation = navigation,
                useRail = width >= railWidthThreshold,
                browseService = null,
                historyService = history,
                historySession = session,
            )
        }
        content
    })

    // ---- 7. 更新 Updates ----
    @Test
    fun updatesMatrix() = matrix("updates", { width ->
        val repository = FakeUpdatesRepository(FakeUpdatesRepository.sampleEntries())
        val navigation = createAppNavigationState()
        navigation.select(NavTab.Updates)
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalUpdatesRepository provides repository) {
                AppScaffold(navigation = navigation, useRail = width >= railWidthThreshold)
            }
        }
        content
    })

    // ---- 8. 更多 More ----
    @Test
    fun moreMatrix() = matrix("more", { width ->
        val navigation = createAppNavigationState()
        navigation.select(NavTab.More)
        val content: @Composable () -> Unit = {
            AppScaffold(navigation = navigation, useRail = width >= railWidthThreshold)
        }
        content
    })

    // ---- 9. 设置 Settings（未实现占位） ----
    @Test
    fun settingsPlaceholderMatrix() = matrix("settings-placeholder", { width ->
        val navigation = createAppNavigationState()
        navigation.select(NavTab.More)
        placeholderContent(navigation, width >= railWidthThreshold, "设置 Settings")
    })

    // ---- 10. 统计 Statistics ----
    @Test
    fun statisticsMatrix() = matrix("statistics", { width ->
        val navigation = createAppNavigationState()
        navigation.select(NavTab.More)
        navigation.push(StatisticsRoute)
        val content: @Composable () -> Unit = {
            AppScaffold(navigation = navigation, useRail = width >= railWidthThreshold)
        }
        content
    })

    // ---- 11. 分类 Categories ----
    @Test
    fun categoriesMatrix() = matrix("categories", { width ->
        val repository = demoLibraryRepository()
        val navigation = createAppNavigationState()
        navigation.select(NavTab.More)
        navigation.push(CategoriesRoute)
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalLibraryRepository provides repository) {
                AppScaffold(navigation = navigation, useRail = width >= railWidthThreshold)
            }
        }
        content
    })

    // ---- 12. 下载队列 Download queue ----
    @Test
    fun downloadQueueMatrix() = matrix("download-queue", { width ->
        val navigation = createAppNavigationState()
        navigation.select(NavTab.More)
        navigation.push(DownloadQueueRoute)
        val content: @Composable () -> Unit = {
            AppScaffold(navigation = navigation, useRail = width >= railWidthThreshold)
        }
        content
    })

    // ---- 13. 更新错误 Update errors（未实现占位） ----
    @Test
    fun updateErrorsPlaceholderMatrix() = matrix("update-errors-placeholder", { width ->
        val navigation = createAppNavigationState()
        navigation.select(NavTab.Updates)
        placeholderContent(navigation, width >= railWidthThreshold, "更新错误 Update errors")
    })

    // ---- 14. 关于 / 新功能 About（未实现占位） ----
    @Test
    fun aboutPlaceholderMatrix() = matrix("about-placeholder", { width ->
        val navigation = createAppNavigationState()
        navigation.select(NavTab.More)
        placeholderContent(navigation, width >= railWidthThreshold, "关于 / 新功能 About")
    })

    // ---- 15. 次级工具页 Secondary tools（真实 GlobalSearchRoute） ----
    @Test
    fun secondaryToolsMatrix() = matrix("secondary-tools", { width ->
        val service = demoBrowseService()
        val navigation = createAppNavigationState()
        navigation.select(NavTab.Browse)
        navigation.push(GlobalSearchRoute)
        val session = browseReadySession(service)
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalBrowseService provides service) {
                AppScaffold(
                    navigation = navigation,
                    useRail = width >= railWidthThreshold,
                    browseService = service,
                    browseSession = session,
                )
            }
        }
        content
    })

    // ---- 固定 fixture ----

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

    /** 时间戳全部用 historyEntry 的默认值，不依赖当前时间。 */
    private fun demoHistoryService(): FakeHistoryService = FakeHistoryService(
        listOf(
            historyEntry(),
            historyEntry(
                workId = "night",
                workTitle = "夜行记",
                chapterTitle = "第一话",
                pageIndex = 0,
                sourceKey = "example_source",
            ),
            historyEntry(
                workId = "oneshot",
                workTitle = "同名单话",
                chapterTitle = null,
                chapterId = null,
                pageIndex = 4,
                sourceKey = "other_source",
                readAtMillis = null,
            ),
        ),
    )

    private fun demoLibraryRepository(): FakeLibraryRepository = FakeLibraryRepository(
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

    private fun browseReadySession(service: FakeBrowseService, pane: app.koyura.app.browse.BrowsePane = app.koyura.app.browse.BrowsePane.Sources): BrowseSession {
        val session = BrowseSession(service)
        runBlocking { session.refresh() }
        session.selectPane(pane)
        session.searchSources("")
        return session
    }

    private fun readerTriple(): Triple<FakeBrowseService, FakeReaderService, ReaderSession> {
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
        return Triple(browse, reader, ReaderSession(reader))
    }

    private companion object {
        const val CANVAS_HEIGHT = 800
    }
}
