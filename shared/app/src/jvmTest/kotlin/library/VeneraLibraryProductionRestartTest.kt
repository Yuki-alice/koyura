package app.koyura.app.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.koyura.app.ExtensionApp
import app.koyura.app.NavigationBarTestTag
import app.koyura.app.NavigationRailTestTag
import app.koyura.app.browse.BrowseExtensionsTabTag
import app.koyura.app.browse.BrowseManageStoresTag
import app.koyura.app.browse.BrowseSourcesTabTag
import app.koyura.app.browse.CatalogDialogTag
import app.koyura.app.browse.CatalogVariantId
import app.koyura.app.browse.SourceSearchQueryTag
import app.koyura.app.browse.SourceSearchSubmitTag
import app.koyura.app.browse.StoresScreenTestTag
import app.koyura.app.browse.extensionRowTag
import app.koyura.app.browse.sourceResultTag
import app.koyura.app.browse.storePreviewTag
import app.koyura.app.browse.venera.VeneraFixtures
import app.koyura.app.history.historyContinueTag
import app.koyura.app.history.historyRowTag
import app.koyura.app.reader.pngBytes
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.reader.preferences.ReaderPreferencesStore
import app.koyura.data.session.ExtensionSession
import app.koyura.data.session.openExtensionSession
import app.koyura.designsystem.DefaultCoverMemoryCache
import app.koyura.designsystem.CoverImageTag
import app.koyura.domain.reader.preferences.ReadingMode
import app.koyura.features.library.LibraryBottomChangeCategoriesTag
import app.koyura.features.library.LibraryBottomMenuTag
import app.koyura.features.library.LibraryCategoriesSheetTag
import app.koyura.features.library.LibraryCategoryAddTag
import app.koyura.features.library.LibraryCategoryNameTag
import app.koyura.features.library.LibraryClearSelectionTag
import app.koyura.features.library.LibraryCategoryPickerConfirmTag
import app.koyura.features.library.LibraryCategoryPickerTag
import app.koyura.features.library.LibraryDisplayMode
import app.koyura.features.library.LibraryDisplaySheetTag
import app.koyura.features.library.LibraryListTag
import app.koyura.features.library.LibraryMenuCategoriesTag
import app.koyura.features.library.LibraryMenuDisplayTag
import app.koyura.features.library.LibraryOverflowTag
import app.koyura.features.library.LibraryRootTag
import app.koyura.features.library.LibrarySheetCloseTag
import app.koyura.features.library.libraryCardTag
import app.koyura.features.library.libraryContinueTag
import app.koyura.features.library.libraryModeChipTag
import app.koyura.features.library.libraryPickerRowTag
import app.koyura.features.library.libraryTabTag
import app.koyura.features.reader.comic.ReaderBackTag
import app.koyura.features.reader.comic.ReaderPageIndicatorTag
import app.koyura.features.reader.comic.ReaderTapNextTag
import app.koyura.features.reader.comic.chrome.ReaderChromeModeApplyTag
import app.koyura.features.reader.comic.chrome.ReaderChromeModeTag
import app.koyura.features.reader.comic.chrome.readerChromeModeOptionTag
import app.koyura.network.applyReaderDefaults
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * 生产书架跨进程验收：真实共享应用装配 + 文件型 SQLite + QuickJS +
 * 未改动的上游 Venera 示例脚本 + 固定 HTTP 响应。
 *
 * - 无环境变量时三阶段在同一进程内顺序执行（本地开发）；
 * - 设置 KOYURA_LIBRARY_E2E_PROFILE 后按 KOYURA_LIBRARY_E2E_PHASE
 *   （read / resume-library / resume-history）在不同进程各跑一阶段，
 *   同一资料目录。每个 Gradle 阶段用 --rerun 起新 worker，即新进程。
 */
class VeneraLibraryProductionRestartTest {
    @Test
    fun libraryPersistsFavoritesCategoriesAndResumeAcrossRestart() {
        val profilePath = System.getenv("KOYURA_LIBRARY_E2E_PROFILE")
        if (profilePath != null) {
            val profile = File(profilePath)
            when (System.getenv("KOYURA_LIBRARY_E2E_PHASE")) {
                "read" -> readAndClose(profile)
                "resume-library" -> resumeFromLibrary(profile)
                "resume-history" -> resumeFromHistory(profile)
                else -> error("KOYURA_LIBRARY_E2E_PHASE must be read, resume-library or resume-history")
            }
        } else {
            val profile = Files.createTempDirectory("library-production-restart").toFile()
            try {
                readAndClose(profile)
                resumeFromLibrary(profile)
                resumeFromHistory(profile)
            } finally {
                profile.deleteRecursively()
            }
        }
    }

    // region read：预览 → 安装 → 搜索 → 详情收藏 → 新建分类并归类 → 开第 2 章第 3 页

    private fun readAndClose(profile: File) {
        // 全局封面内存缓存按源隔离但跨测试共享：冷启动断言前先清空，保证可复现。
        DefaultCoverMemoryCache.clear()
        val storeId = seedRepository(profile)
        val requests = ConcurrentLinkedQueue<String>()
        runComposeUiTest {
            var width by mutableIntStateOf(420)
            setContent {
                Box(Modifier.requiredSize(width.dp, 760.dp)) {
                    ExtensionApp { open(profile, requests) }
                }
            }

            // 仓库预览 → 安装（生产 UI）。
            shellTabs()[3].performClick()
            awaitTag(BrowseExtensionsTabTag)
            onNodeWithTag(BrowseExtensionsTabTag).performClick()
            onNodeWithTag(BrowseManageStoresTag).performClick()
            awaitTag(StoresScreenTestTag)
            onNodeWithTag(storePreviewTag(storeId)).performClick()
            awaitTag(CatalogDialogTag)
            val installTag = "catalog-install-${CatalogVariantId(storeId, SourceKey, VeneraFixtures.ScriptUrl).key}"
            awaitTag(installTag)
            onNodeWithTag(installTag).performClick()
            onNodeWithTag("catalog-close").performClick()
            onNodeWithTag("stores-back").performClick()
            awaitTag(extensionRowTag("$storeId::$SourceKey"))

            // 搜索 → 详情。结果行封面走真实源会话加载。
            onNodeWithTag(BrowseSourcesTabTag).performClick()
            awaitTag("source-row-$SourceKey")
            onNodeWithTag("source-row-$SourceKey").performClick()
            awaitTag(SourceSearchQueryTag)
            onNodeWithTag(SourceSearchQueryTag).performTextInput("星")
            onNodeWithTag(SourceSearchSubmitTag).performClick()
            awaitTag(sourceResultTag(WorkId))
            // 可点击行合并语义子树：行内封面节点只在未合并树可见。
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag(CoverImageTag, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue("/cover.jpg" in requests, "cover must load through comic.onThumbnailLoad, got $requests")
            onNodeWithTag(sourceResultTag(WorkId)).performClick()
            awaitTag(DetailHeadingTag)
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag(CoverImageTag, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            capture("read-detail-cover")

            // 详情收藏（与书架同一记录）。
            awaitTag(DetailFavoriteTag)
            onNodeWithTag(DetailFavoriteTag).performClick()

            // 书架即时出现该收藏。
            shellTabs()[0].performClick()
            awaitTag(LibraryRootTag)
            awaitTag(libraryCardTag(SourceKey, WorkId))
            capture("read-shelf-favorited")

            // 新建分类并归类（生产 UI）。
            node(LibraryOverflowTag).performClick()
            scrollAndClick(LibraryMenuCategoriesTag)
            awaitTag(LibraryCategoriesSheetTag)
            node(LibraryCategoryNameTag).performTextReplacement(CategoryName)
            scrollAndClick(LibraryCategoryAddTag)
            val categoryId = waitCategoryId(profile, CategoryName)
            scrollAndClick(LibrarySheetCloseTag)
            awaitTag(libraryTabTag(categoryId))
            node(libraryCardTag(SourceKey, WorkId)).performTouchInput { longClick() }
            awaitTag(LibraryBottomMenuTag)
            node(LibraryBottomChangeCategoriesTag).performClick()
            awaitTag(LibraryCategoryPickerTag)
            node(libraryPickerRowTag(categoryId)).performClick()
            node(LibraryCategoryPickerConfirmTag).performClick()
            // 归类不自动清选择：手动清除后回到常规工具栏。
            node(LibraryClearSelectionTag).performClick()
            // 切到该分类页签：作品仍在。
            node(libraryTabTag(categoryId)).performClick()
            awaitTag(libraryCardTag(SourceKey, WorkId))

            // 网格 → 列表切换后仍可打开作品。
            node(LibraryOverflowTag).performClick()
            scrollAndClick(LibraryMenuDisplayTag)
            awaitTag(LibraryDisplaySheetTag)
            node(libraryModeChipTag(LibraryDisplayMode.LIST)).performClick()
            scrollAndClick(LibrarySheetCloseTag)
            awaitTag(LibraryListTag)
            node(libraryCardTag(SourceKey, WorkId)).performClick()
            awaitTag(DetailHeadingTag)

            // 从详情开第 2 章，翻到第 3 页，等落盘。
            awaitTag("source-detail-chapter-ep_2")
            onNodeWithTag("source-detail-chapter-ep_2").performClick()
            awaitTag("reader-page-image-0")
            awaitDiskPage(profile, pageIndex = 0)
            onNodeWithTag(ReaderTapNextTag).performClick()
            awaitPageIndicator("2 / 3")
            onNodeWithTag(ReaderTapNextTag).performClick()
            awaitThirdPage()
            awaitDiskPage(profile, pageIndex = 2)

            // 切 RTL 并从窄屏缩到宽屏（反向亦可），等模式与进度落盘。
            awaitTag(ReaderChromeModeTag)
            onNodeWithTag(ReaderChromeModeTag).performClick()
            val rtl = readerChromeModeOptionTag(ReadingMode.RIGHT_TO_LEFT.name)
            awaitTag(rtl)
            onNodeWithTag(rtl).performClick()
            onNodeWithTag(ReaderChromeModeApplyTag).performClick()
            awaitMode(profile, ReadingMode.RIGHT_TO_LEFT)
            awaitThirdPage()
            runOnIdle { width = 1000 }
            assertRtlDoublePageAtThird()
            awaitDiskPage(profile, pageIndex = 2)
        }
        assertChapterRequests(requests)
        verifySavedLibraryState(profile, "read", categoryIdOf(profile, CategoryName))
        println("phase=read pid=${ProcessHandle.current().pid()} profile=$profile")
    }

    // endregion

    // region resume-library：新进程从书架继续

    private fun resumeFromLibrary(profile: File) {
        val requests = ConcurrentLinkedQueue<String>()
        runComposeUiTest {
            setContent {
                Box(Modifier.requiredSize(1000.dp, 760.dp)) {
                    ExtensionApp { open(profile, requests) }
                }
            }
            shellTabs()[0].performClick()
            awaitTag(LibraryRootTag)
            awaitTag(libraryCardTag(SourceKey, WorkId))
            // 偏好（列表模式）与分类随重启恢复。
            awaitTag(LibraryListTag)
            val categoryId = waitCategoryId(profile, CategoryName)
            awaitTag(libraryTabTag(categoryId))
            onNodeWithTag(libraryTabTag(categoryId)).performClick()
            awaitTag(libraryCardTag(SourceKey, WorkId))
            capture("resume-library-shelf")

            // 点继续：恢复到 ep_2 第 3 页、同一稳定页键与 RTL，不翻页不改模式。
            onNodeWithTag(libraryTabTag(null)).performClick()
            awaitTag(libraryCardTag(SourceKey, WorkId))
            awaitTag(libraryContinueTag(SourceKey, WorkId))
            capture("resume-library-continue")
            onNodeWithTag(libraryContinueTag(SourceKey, WorkId)).performClick()
            assertRtlDoublePageAtThird()
            awaitTag(ReaderBackTag)
            awaitMode(profile, ReadingMode.RIGHT_TO_LEFT)

            // 返回后仍在书架路径。
            onNodeWithTag(ReaderBackTag).performClick()
            awaitTag(LibraryRootTag)
            awaitTag(libraryCardTag(SourceKey, WorkId))
        }
        verifySavedLibraryState(profile, "resume-library", categoryIdOf(profile, CategoryName))
        println("phase=resume-library pid=${ProcessHandle.current().pid()} profile=$profile")
    }

    // endregion

    // region resume-history：第三个进程从历史继续

    private fun resumeFromHistory(profile: File) {
        val requests = ConcurrentLinkedQueue<String>()
        runComposeUiTest {
            setContent {
                Box(Modifier.requiredSize(1000.dp, 760.dp)) {
                    ExtensionApp { open(profile, requests) }
                }
            }
            shellTabs()[2].performClick()
            awaitTag(historyRowTag(SourceKey, WorkId))
            // 列表数据只读本地 SQLite：展示前无任何数据请求（封面按需加载除外）。
            assertTrue(requests.none { "/api/" in it }, "History must render offline, but made $requests")
            capture("resume-history-list")
            onNodeWithTag(historyContinueTag(SourceKey, WorkId)).performClick()
            assertRtlDoublePageAtThird()
            capture("resume-history-reading")
            awaitMode(profile, ReadingMode.RIGHT_TO_LEFT)
        }
        verifySavedLibraryState(profile, "resume-history", categoryIdOf(profile, CategoryName))
        println("phase=resume-history pid=${ProcessHandle.current().pid()} profile=$profile")
    }

    // endregion

    // region 核对：SQLite 提交态 + 打印位置

    private fun verifySavedLibraryState(profile: File, phase: String, categoryId: Long) {
        val reopened = runBlocking { open(profile, ConcurrentLinkedQueue()) }
        try {
            val repository = ProductionLibraryRepository(
                store = reopened.library,
                history = reopened.history,
                thumbnails = { sourceKey, imageKey ->
                    reopened.comicReading.loadThumbnailBytes(sourceKey, imageKey)
                },
            )
            val snapshot = runBlocking { repository.load() }
            val entry = snapshot.entries.singleOrNull { it.workId == WorkId }
            assertNotNull(entry, "phase=$phase: library must keep $WorkId")
            assertEquals(setOf(categoryId), snapshot.categoriesOf(entry.key), "phase=$phase: category must persist")
            assertEquals(LibraryDisplayMode.LIST, snapshot.prefs.displayMode, "phase=$phase: list mode must persist")

            val progress = runBlocking {
                assertNotNull(
                    reopened.comicReading.readLatestProgress(SourceKey, WorkId),
                    "phase=$phase: progress must persist",
                )
            }
            assertEquals("ep_2", progress.chapterId)
            assertEquals(2, progress.pageIndex)
            assertEquals("https://example.invalid/images/3.png", progress.pageKey)
            val mode = runBlocking {
                ReaderPreferencesStore(profile.toOkioPath()).resolve(SourceKey, WorkId).mode
            }
            assertEquals(ReadingMode.RIGHT_TO_LEFT, mode)
            println(
                "phase=$phase pid=${ProcessHandle.current().pid()} work=$SourceKey/$WorkId " +
                    "category=$CategoryName($categoryId) chapter=ep_2 page=3 " +
                    "key=${progress.pageKey} mode=$mode",
            )
        } finally {
            runBlocking { reopened.close() }
        }
    }

    private fun categoryIdOf(profile: File, name: String): Long {
        driver(profile).use { db ->
            return ExtensionDb(db).extensionDbQueries.selectCategoriesOrdered()
                .executeAsList().first { it.name == name }.id
        }
    }

    private fun waitCategoryId(profile: File, name: String): Long {
        // 分类行由仓储在 SQLite 提交后才可见；轮询直到出现。
        var id: Long? = null
        val deadline = System.currentTimeMillis() + 15_000
        while (id == null) {
            id = runCatching { categoryIdOf(profile, name) }.getOrNull()
            if (id == null) {
                if (System.currentTimeMillis() > deadline) error("category $name never committed")
                Thread.sleep(200)
            }
        }
        return id
    }

    // endregion

    // region 会话与断言辅助

    private fun seedRepository(profile: File): String = runBlocking {
        val seed = open(profile, ConcurrentLinkedQueue())
        try {
            seed.service.addRepository("Example", VeneraFixtures.IndexUrl)
            seed.service.listRepositories().single().id
        } finally {
            seed.close()
        }
    }

    private suspend fun open(
        profile: File,
        requests: ConcurrentLinkedQueue<String>,
    ): ExtensionSession {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            requests.add(path)
            when {
                path == "/index.json" -> respond(VeneraFixtures.catalogJson())
                request.url.host == "example.invalid" && path == "/sources/example.js" ->
                    respond(VeneraFixtures.upstreamScriptBytes())
                path == "/api/search" -> respond(VeneraFixtures.searchJson())
                path == "/api/comics/demo" -> respond(VeneraFixtures.detailJson())
                path == "/api/comics/demo/chapters/2" -> respond(
                    """{"images":["https://example.invalid/images/1.png","https://example.invalid/images/2.png","https://example.invalid/images/3.png"]}""",
                )
                path == "/cover.jpg" -> respond(
                    pngBytes(CoverRgb),
                    headers = headersOf(HttpHeaders.ContentType, "image/png"),
                )
                path == "/images/1.png" || path == "/images/2.png" || path == "/images/3.png" -> {
                    val color = when (path) {
                        "/images/1.png" -> 0xFF0000
                        "/images/2.png" -> 0x00FF00
                        else -> 0x0000FF
                    }
                    respond(pngBytes(color), headers = headersOf(HttpHeaders.ContentType, "image/png"))
                }
                else -> respond("not found", HttpStatusCode.NotFound)
            }
        }
        return openExtensionSession(
            driver(profile),
            FileSystem.SYSTEM,
            profile.toOkioPath(),
            HttpClient(engine) { applyReaderDefaults() },
            VeneraEnvironment("1.16.0", "zh_CN", "desktop"),
        )
    }

    private fun driver(profile: File): JdbcSqliteDriver =
        JdbcSqliteDriver("jdbc:sqlite:${File(profile, "extensions.db").absolutePath}")

    private fun ComposeUiTest.shellTabs(): SemanticsNodeInteractionCollection {
        val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            (hasAnyAncestor(hasTestTag(NavigationBarTestTag)) or
                hasAnyAncestor(hasTestTag(NavigationRailTestTag)))
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(tabs).fetchSemanticsNodes().size == 5
        }
        return onAllNodes(tabs)
    }

    private fun ComposeUiTest.awaitTag(tag: String) {
        waitUntil(conditionDescription = "visible node: $tag", timeoutMillis = 15_000) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1 && onNodeWithTag(tag).isDisplayed()
        }
    }

    private fun ComposeUiTest.node(tag: String) =
        onNodeWithTag(tag, useUnmergedTree = true)

    private fun ComposeUiTest.scrollAndClick(tag: String) {
        node(tag).performScrollTo()
        node(tag).performClick()
    }

    private fun ComposeUiTest.awaitDiskPage(profile: File, pageIndex: Long) {
        driver(profile).use { db ->
            val queries = ExtensionDb(db).extensionDbQueries
            waitUntil(timeoutMillis = 10_000) {
                queries.selectProgress(SourceKey, WorkId, "ep_2").executeAsOneOrNull()?.page_index == pageIndex
            }
        }
    }

    private fun ComposeUiTest.awaitPageIndicator(value: String) {
        waitUntil(timeoutMillis = 10_000) {
            runCatching { onNodeWithTag(ReaderPageIndicatorTag).assertTextEquals(value) }.isSuccess
        }
    }

    private fun ComposeUiTest.awaitThirdPage() {
        awaitPageIndicator("3 / 3")
        awaitTag("reader-page-image-2")
        onNodeWithText("第二章").assertIsDisplayed()
    }

    private fun ComposeUiTest.assertRtlDoublePageAtThird() {
        awaitThirdPage()
        awaitTag("reader-page-image-1")
        val secondPage = onNodeWithTag("reader-page-image-1").fetchSemanticsNode().boundsInRoot
        val thirdPage = onNodeWithTag("reader-page-image-2").fetchSemanticsNode().boundsInRoot
        assertTrue(thirdPage.center.x < secondPage.center.x, "expected the RTL double spread")
    }

    private fun ComposeUiTest.awaitMode(profile: File, mode: ReadingMode) {
        val store = ReaderPreferencesStore(profile.toOkioPath())
        waitUntil(timeoutMillis = 10_000) {
            runBlocking { store.resolve(SourceKey, WorkId).mode } == mode
        }
    }

    private fun assertChapterRequests(requests: Collection<String>) {
        assertEquals(listOf("/api/comics/demo/chapters/2"), requests.filter { "/chapters/" in it })
        assertTrue("/images/3.png" in requests)
        assertTrue("/cover.jpg" in requests)
    }

    private fun ComposeUiTest.capture(name: String) {
        val dir = File(System.getProperty("reader.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
        val pixels = onRoot().captureToImage().toPixelMap()
        val awt = java.awt.image.BufferedImage(
            pixels.width, pixels.height, java.awt.image.BufferedImage.TYPE_INT_ARGB,
        )
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                val packed = (color.alpha * 255).roundToInt().coerceIn(0, 255) shl 24 or
                    ((color.red * 255).roundToInt().coerceIn(0, 255) shl 16) or
                    ((color.green * 255).roundToInt().coerceIn(0, 255) shl 8) or
                    (color.blue * 255).roundToInt().coerceIn(0, 255)
                awt.setRGB(x, y, packed)
            }
        }
        val out = File(dir, "library-$name.png")
        javax.imageio.ImageIO.write(awt, "png", out)
        println("screenshot: ${out.absolutePath}")
    }

    private companion object {
        const val SourceKey = "example_source"
        const val WorkId = "demo"
        const val CategoryName = "追更"
        const val DetailHeadingTag = "source-detail-heading"
        const val DetailFavoriteTag = "detail-favorite"
        const val CoverRgb = 0x336699
    }

    // endregion
}
