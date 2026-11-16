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
import app.koyura.app.browse.BrowseService
import app.koyura.app.browse.ExtensionDetailsScreen
import app.koyura.app.browse.FakeBrowseService
import app.koyura.app.browse.LocalBrowseService
import app.koyura.app.browse.SourceDetailScreen
import app.koyura.app.browse.SourceSearchScreen
import app.koyura.app.browse.WorkChapter
import app.koyura.app.browse.WorkDetail
import app.koyura.app.browse.WorkSearchPage
import app.koyura.app.browse.WorkSummary
import app.koyura.app.history.FakeHistoryService
import app.koyura.app.history.HistoryScreen
import app.koyura.app.history.HistorySession
import app.koyura.app.history.historyEntry
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

/**
 * Komikku visual alignment matrix (task A).
 *
 * Renders 420 dp (bottom bar) and 1280 dp (navigation rail) in light and dark themes for the
 * shell, Sources/Extensions, work detail and history, plus one loading/empty/error set. Run with
 * `-PreaderScreenshotDir=build/komikku-a` for the delivery bundle.
 *
 * Reference: `reference/komikku` at 383ffaa816, original files under
 * `app/src/main/java/eu/kanade/` (HomeScreen, TachiyomiTheme, BrowseTab, Sources/Extensions/
 * BrowseSource/ExtensionDetails screens, MangaScreen + MangaChapterListItem, HistoryScreen).
 */
class KomikkuShellScreenshotsTest {
    private val outputDir = File(System.getProperty("reader.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    private fun render(name: String, width: Int, height: Int, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
                ReaderTheme(darkTheme = dark) {
                    Box(Modifier.fillMaxSize().background(if (dark) Color.Black else Color.White)) { content() }
                }
            }
            try {
                scene.render(2_000_000_000)
                // Let LaunchedEffect refreshes (browse/history/detail) land before capturing.
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

    private fun browseReadySession(service: FakeBrowseService, pane: BrowsePane = BrowsePane.Sources): BrowseSession {
        val session = BrowseSession(service)
        runBlocking { session.refresh() }
        session.selectPane(pane)
        session.searchSources("")
        return session
    }

    @Test
    fun shellMatrix() {
        val cases = listOf(
            Triple("a-shell-420-light", 420, false),
            Triple("a-shell-420-dark", 420, true),
            Triple("a-shell-1280-light", 1280, false),
            Triple("a-shell-1280-dark", 1280, true),
        )
        for ((name, width, dark) in cases) {
            render(name, width, 800, dark) {
                Box(Modifier.size(width.dp, 800.dp)) { AppShell() }
            }
        }
    }

    @Test
    fun browseMatrix() {
        val panes = listOf(
            "sources" to BrowsePane.Sources,
            "extensions" to BrowsePane.Extensions,
        )
        for ((paneName, pane) in panes) {
            for ((theme, dark) in listOf("light" to false, "dark" to true)) {
                for ((widthName, width, useRail) in listOf(
                    Triple("420", 420, false),
                    Triple("1280", 1280, true),
                )) {
                    val service = demoBrowseService()
                    val navigation = createAppNavigationState(initialTab = NavTab.Browse)
                    val session = browseReadySession(service, pane)
                    val name = "a-browse-$paneName-$widthName-$theme"
                    render(name, width, 800, dark) {
                        Box(Modifier.size(width.dp, 800.dp)) {
                            CompositionLocalProvider(LocalBrowseService provides service) {
                                AppScaffold(navigation = navigation, useRail = useRail, browseService = service, browseSession = session)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun detailMatrix() {
        for ((theme, dark) in listOf("light" to false, "dark" to true)) {
            for ((widthName, width, useRail) in listOf(
                Triple("420", 420, false),
                Triple("1280", 1280, true),
            )) {
                val service = demoBrowseService()
                val navigation = createAppNavigationState(initialTab = NavTab.Browse)
                navigation.push(app.koyura.app.navigation.SourceDetailRoute("example_source", "demo", "星图"))
                val session = browseReadySession(service)
                val name = "a-detail-$widthName-$theme"
                render(name, width, 900, dark) {
                    Box(Modifier.size(width.dp, 900.dp)) {
                        CompositionLocalProvider(LocalBrowseService provides service) {
                            AppScaffold(navigation = navigation, useRail = useRail, browseService = service, browseSession = session)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun historyMatrix() {
        for ((theme, dark) in listOf("light" to false, "dark" to true)) {
            for ((widthName, width, useRail) in listOf(
                Triple("420", 420, false),
                Triple("1280", 1280, true),
            )) {
                val history = demoHistoryService()
                val navigation = createAppNavigationState(initialTab = NavTab.History)
                val session = HistorySession(history)
                val name = "a-history-$widthName-$theme"
                render(name, width, 800, dark) {
                    Box(Modifier.size(width.dp, 800.dp)) {
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
    fun stateMatrix() {
        // Browse loading: gate the snapshot so the spinner stays visible.
        run {
            val service = FakeBrowseService()
            val gate = FakeBrowseService.Gate()
            service.gate = gate
            service.gateKey = "load"
            val navigation = createAppNavigationState(initialTab = NavTab.Browse)
            val session = BrowseSession(service)
            render("a-state-browse-loading-420-light", 420, 800, false) {
                Box(Modifier.size(420.dp, 800.dp)) {
                    CompositionLocalProvider(LocalBrowseService provides service) {
                        AppScaffold(navigation = navigation, useRail = false, browseService = service, browseSession = session)
                    }
                }
            }
            gate.open()
        }
        // Browse error: the next snapshot load fails, the Failed state offers a retry.
        run {
            val service = FakeBrowseService()
            service.loadFailure = "Index unreachable"
            val navigation = createAppNavigationState(initialTab = NavTab.Browse)
            val session = BrowseSession(service)
            render("a-state-browse-error-420-light", 420, 800, false) {
                Box(Modifier.size(420.dp, 800.dp)) {
                    CompositionLocalProvider(LocalBrowseService provides service) {
                        AppScaffold(navigation = navigation, useRail = false, browseService = service, browseSession = session)
                    }
                }
            }
        }
        // Browse empty + history empty: no sources and no history rows.
        run {
            val service = FakeBrowseService(sources = emptyList(), extensions = emptyList(), stores = emptyList())
            val navigation = createAppNavigationState(initialTab = NavTab.Browse)
            val session = browseReadySession(service)
            render("a-state-browse-empty-420-light", 420, 800, false) {
                Box(Modifier.size(420.dp, 800.dp)) {
                    CompositionLocalProvider(LocalBrowseService provides service) {
                        AppScaffold(navigation = navigation, useRail = false, browseService = service, browseSession = session)
                    }
                }
            }
        }
        run {
            val history = FakeHistoryService(emptyList())
            val navigation = createAppNavigationState(initialTab = NavTab.History)
            val session = HistorySession(history)
            render("a-state-history-empty-420-light", 420, 800, false) {
                Box(Modifier.size(420.dp, 800.dp)) {
                    AppScaffold(navigation = navigation, useRail = false, browseService = null, historyService = history, historySession = session)
                }
            }
        }
        // History error + detail error.
        run {
            val history = FakeHistoryService(emptyList())
            history.failNext = "Database unavailable"
            val navigation = createAppNavigationState(initialTab = NavTab.History)
            val session = HistorySession(history)
            render("a-state-history-error-420-light", 420, 800, false) {
                Box(Modifier.size(420.dp, 800.dp)) {
                    AppScaffold(navigation = navigation, useRail = false, browseService = null, historyService = history, historySession = session)
                }
            }
        }
        run {
            val service = FakeBrowseService()
            val navigation = createAppNavigationState(initialTab = NavTab.Browse)
            navigation.push(app.koyura.app.navigation.SourceDetailRoute("example_source", "missing", "Missing"))
            val session = browseReadySession(service)
            render("a-state-detail-error-420-light", 420, 800, false) {
                Box(Modifier.size(420.dp, 800.dp)) {
                    CompositionLocalProvider(LocalBrowseService provides service) {
                        AppScaffold(navigation = navigation, useRail = false, browseService = service, browseSession = session)
                    }
                }
            }
        }
        // Extension details surface.
        run {
            val service = demoBrowseService()
            val navigation = createAppNavigationState(initialTab = NavTab.Browse)
            navigation.push(app.koyura.app.navigation.ExtensionDetailsRoute("store:1::com.example.installed"))
            val session = browseReadySession(service, BrowsePane.Extensions)
            render("a-state-extension-details-420-light", 420, 800, false) {
                Box(Modifier.size(420.dp, 800.dp)) {
                    CompositionLocalProvider(LocalBrowseService provides service) {
                        AppScaffold(navigation = navigation, useRail = false, browseService = service, browseSession = session)
                    }
                }
            }
        }
    }
}
