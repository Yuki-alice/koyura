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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.graphics.toPixelMap
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
import app.koyura.app.history.HistoryClearAllTag
import app.koyura.app.history.HistoryClearConfirmTag
import app.koyura.app.history.historyContinueTag
import app.koyura.app.history.historyDeleteTag
import app.koyura.app.history.historyFavoriteTag
import app.koyura.app.history.historyRowTag
import app.koyura.app.reader.VeneraReaderService
import app.koyura.app.reader.pngBytes
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.reader.preferences.ReaderPreferencesStore
import app.koyura.data.session.ExtensionSession
import app.koyura.data.session.openExtensionSession
import app.koyura.designsystem.KomikkuCoverImageTag
import app.koyura.domain.reader.preferences.ReadingMode
import app.koyura.features.library.LibraryBottomChangeCategoriesTag
import app.koyura.features.library.LibraryBottomMenuTag
import app.koyura.features.library.LibraryCategoriesSheetTag
import app.koyura.features.library.LibraryCategoryAddTag
import app.koyura.features.library.LibraryCategoryNameTag
import app.koyura.features.library.LibraryCategoryPickerConfirmTag
import app.koyura.features.library.LibraryCategoryPickerTag
import app.koyura.features.library.LibraryClearSelectionTag
import app.koyura.features.library.LibraryDisplayMode
import app.koyura.features.library.LibraryListTag
import app.koyura.features.library.LibraryMenuCategoriesTag
import app.koyura.features.library.LibraryOverflowTag
import app.koyura.features.library.LibraryRootTag
import app.koyura.features.library.LibrarySheetCloseTag
import app.koyura.features.library.libraryCardTag
import app.koyura.features.library.libraryContinueTag
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * 历史变异跨进程验收：删除单条 / 清空历史后，续读位置、收藏、分类与模式必须保留。
 *
 * 环境变量（验收矩阵为每个 action 起独立资料目录、每个 phase 起新进程）：
 * - KOYURA_HISTORY_MUTATION_PROFILE：资料目录；
 * - KOYURA_HISTORY_MUTATION_ACTION：delete | clear；
 * - KOYURA_HISTORY_MUTATION_PHASE：read | mutate | resume。
 *
 * read 准备目标作品（收藏、追更分类、ep_2 第 3 页、RTL）并保存另一部作品的历史；
 * mutate 只走生产历史 UI 做删除/清空，不触碰进度；
 * resume 先核对历史可见性与原进度，再从生产书架 UI 点继续。
 */
class VeneraLibraryHistoryMutationRestartTest {
    @Test
    fun historyMutationsKeepProgressAndResume() {
        val profilePath = System.getenv("KOYURA_HISTORY_MUTATION_PROFILE")
        val action = System.getenv("KOYURA_HISTORY_MUTATION_ACTION")
        val phase = System.getenv("KOYURA_HISTORY_MUTATION_PHASE")
        if (profilePath != null && action != null && phase != null) {
            val profile = File(profilePath)
            require(action == ActionDelete || action == ActionClear) { "unknown action $action" }
            when (phase) {
                "read" -> readAndClose(profile, action)
                "mutate" -> mutateAndClose(profile, action)
                "resume" -> resumeAndContinue(profile, action)
                else -> error("KOYURA_HISTORY_MUTATION_PHASE must be read, mutate or resume")
            }
            return
        }
        for (next in listOf(ActionDelete, ActionClear)) {
            val profile = Files.createTempDirectory("library-history-mutation-$next").toFile()
            try {
                readAndClose(profile, next)
                mutateAndClose(profile, next)
                resumeAndContinue(profile, next)
            } finally {
                profile.deleteRecursively()
            }
        }
    }

    // region read：准备收藏、分类、阅读位置与另一部作品的历史

    private fun readAndClose(profile: File, action: String) {
        val storeId = seedRepositoryAndOtherHistory(profile)
        val requests = ConcurrentLinkedQueue<String>()
        runComposeUiTest {
            var width by mutableIntStateOf(420)
            setContent {
                Box(Modifier.requiredSize(width.dp, 760.dp)) {
                    ExtensionApp { open(profile, requests) }
                }
            }

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

            onNodeWithTag(BrowseSourcesTabTag).performClick()
            awaitTag("source-row-$SourceKey")
            onNodeWithTag("source-row-$SourceKey").performClick()
            awaitTag(SourceSearchQueryTag)
            onNodeWithTag(SourceSearchQueryTag).performTextInput("星")
            onNodeWithTag(SourceSearchSubmitTag).performClick()
            awaitTag(sourceResultTag(WorkId))
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithTag(KomikkuCoverImageTag, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag(sourceResultTag(WorkId)).performClick()
            awaitTag(DetailHeadingTag)

            awaitTag(DetailFavoriteTag)
            onNodeWithTag(DetailFavoriteTag).performClick()

            shellTabs()[0].performClick()
            awaitTag(LibraryRootTag)
            awaitTag(libraryCardTag(SourceKey, WorkId))

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
            node(LibraryClearSelectionTag).performClick()

            onNodeWithTag(libraryTabTag(categoryId)).performClick()
            awaitTag(libraryCardTag(SourceKey, WorkId))
            onNodeWithTag(libraryCardTag(SourceKey, WorkId)).performClick()
            awaitTag(DetailHeadingTag)
            awaitTag("source-detail-chapter-ep_2")
            onNodeWithTag("source-detail-chapter-ep_2").performClick()
            awaitTag("reader-page-image-0")
            awaitDiskPage(profile, pageIndex = 0)
            onNodeWithTag(ReaderTapNextTag).performClick()
            awaitPageIndicator("2 / 3")
            onNodeWithTag(ReaderTapNextTag).performClick()
            awaitThirdPage()
            awaitDiskPage(profile, pageIndex = 2)

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
        verifyReadState(profile, action, categoryIdOf(profile, CategoryName))
        println("phase=read action=$action pid=${ProcessHandle.current().pid()} profile=$profile")
    }

    // endregion

    // region mutate：只走生产历史 UI，不触碰进度

    private fun mutateAndClose(profile: File, action: String) {
        runComposeUiTest {
            setContent {
                Box(Modifier.requiredSize(420.dp, 760.dp)) {
                    ExtensionApp { open(profile, ConcurrentLinkedQueue()) }
                }
            }
            shellTabs()[2].performClick()
            awaitTag(historyRowTag(SourceKey, WorkId))
            awaitTag(historyRowTag(SourceKey, OtherWorkId))
            when (action) {
                ActionDelete -> {
                    onNodeWithTag(historyDeleteTag(SourceKey, WorkId)).performClick()
                    waitUntil(timeoutMillis = 15_000) {
                        onAllNodesWithTag(historyRowTag(SourceKey, WorkId))
                            .fetchSemanticsNodes().isEmpty()
                    }
                    // 单条删除只影响目标作品。
                    onNodeWithTag(historyRowTag(SourceKey, OtherWorkId)).assertIsDisplayed()
                }
                ActionClear -> {
                    onNodeWithTag(HistoryClearAllTag).performClick()
                    awaitTag(HistoryClearConfirmTag)
                    onNodeWithTag("history-clear-confirm-ok").performClick()
                    waitUntil(timeoutMillis = 15_000) {
                        onAllNodesWithTag(historyRowTag(SourceKey, WorkId))
                            .fetchSemanticsNodes().isEmpty() &&
                            onAllNodesWithTag(historyRowTag(SourceKey, OtherWorkId))
                                .fetchSemanticsNodes().isEmpty()
                    }
                }
                else -> error("unknown action $action")
            }
            capture("mutation-$action")
        }
        // 只核对列表可见性与隐藏标记，不读取不断言进度（留给 resume）。
        val check = openEphemeral(profile)
        try {
            val entries = runBlocking { check.history.latestEntries() }
            val hidden = checkHidden(profile)
            if (action == ActionDelete) {
                assertTrue(entries.none { it.workId == WorkId }, "mutate/$action: demo must hide")
                assertTrue(entries.any { it.workId == OtherWorkId }, "mutate/$action: other must stay")
                assertTrue(hidden.contains(SourceKey to WorkId), "mutate/$action: marker must persist")
            } else {
                assertTrue(entries.isEmpty(), "mutate/$action: history must hide all")
                assertTrue(hidden.contains(SourceKey to WorkId))
                assertTrue(hidden.contains(SourceKey to OtherWorkId))
            }
        } finally {
            runBlocking { check.close() }
        }
        println("phase=mutate action=$action pid=${ProcessHandle.current().pid()} profile=$profile")
    }

    // endregion

    // region resume：先核对可见性与原进度，再从书架 UI 点继续

    private fun resumeAndContinue(profile: File, action: String) {
        // 先核对（只读，不改进度）：原进度行必须原样保留。
        val check = openEphemeral(profile)
        try {
            val progress = assertNotNull(
                runBlocking { check.comicReading.readLatestProgress(SourceKey, WorkId) },
                "resume/$action: progress must survive the mutation",
            )
            assertEquals("ep_2", progress.chapterId)
            assertEquals(2, progress.pageIndex)
            assertEquals(PageKey, progress.pageKey)
            val mode = runBlocking {
                ReaderPreferencesStore(profile.toOkioPath()).resolve(SourceKey, WorkId).mode
            }
            assertEquals(ReadingMode.RIGHT_TO_LEFT, mode)
            val entries = runBlocking { check.history.latestEntries() }
            if (action == ActionDelete) {
                assertTrue(entries.none { it.workId == WorkId })
                assertTrue(entries.any { it.workId == OtherWorkId })
            } else {
                assertTrue(entries.isEmpty())
            }
            val stored = runBlocking { check.library.listEntries() }
            assertEquals(1, stored.size)
            println(
                "resume-check action=$action pid=${ProcessHandle.current().pid()} " +
                    "chapter=${progress.chapterId} page=3 key=${progress.pageKey} mode=$mode " +
                    "favorites=${stored.size}",
            )
        } finally {
            runBlocking { check.close() }
        }

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
            val categoryId = waitCategoryId(profile, CategoryName)
            awaitTag(libraryTabTag(categoryId))
            onNodeWithTag(libraryTabTag(categoryId)).performClick()
            awaitTag(libraryCardTag(SourceKey, WorkId))
            capture("resume-$action-shelf")
            onNodeWithTag(libraryTabTag(null)).performClick()
            awaitTag(libraryCardTag(SourceKey, WorkId))
            awaitTag(libraryContinueTag(SourceKey, WorkId))
            onNodeWithTag(libraryContinueTag(SourceKey, WorkId)).performClick()
            // 恢复原章、原页键、原页码与原模式，不提前翻页不改模式。
            assertRtlDoublePageAtThird()
            awaitTag(ReaderBackTag)
            awaitMode(profile, ReadingMode.RIGHT_TO_LEFT)
            onNodeWithTag(ReaderBackTag).performClick()
            awaitTag(LibraryRootTag)
            awaitTag(libraryCardTag(SourceKey, WorkId))
        }
        verifyReadState(profile, action, categoryIdOf(profile, CategoryName))
        println("phase=resume action=$action pid=${ProcessHandle.current().pid()} profile=$profile")
    }

    // endregion

    // region 核对与会话辅助

    private fun verifyReadState(profile: File, action: String, categoryId: Long) {
        val reopened = openEphemeral(profile)
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
            assertNotNull(entry, "$action: library must keep $WorkId")
            assertEquals(setOf(categoryId), snapshot.categoriesOf(entry.key), "$action: category must persist")
            val otherHistory = runBlocking { reopened.history.latestEntries() }
                .any { it.workId == OtherWorkId }
            if (action == ActionDelete) {
                assertTrue(otherHistory, "$action: other work must stay visible")
            }
            val progress = assertNotNull(
                runBlocking { reopened.comicReading.readLatestProgress(SourceKey, WorkId) },
                "$action: progress must persist",
            )
            assertEquals("ep_2", progress.chapterId)
            assertEquals(2, progress.pageIndex)
            assertEquals(PageKey, progress.pageKey)
            val mode = runBlocking {
                ReaderPreferencesStore(profile.toOkioPath()).resolve(SourceKey, WorkId).mode
            }
            assertEquals(ReadingMode.RIGHT_TO_LEFT, mode)
            println(
                "check action=$action pid=${ProcessHandle.current().pid()} work=$SourceKey/$WorkId " +
                    "category=$CategoryName($categoryId) chapter=ep_2 page=3 key=${progress.pageKey} mode=$mode",
            )
        } finally {
            runBlocking { reopened.close() }
        }
    }

    private fun seedRepositoryAndOtherHistory(profile: File): String = runBlocking {
        val seed = openEphemeral(profile)
        try {
            seed.service.addRepository("Example", VeneraFixtures.IndexUrl)
            // 另一部作品的历史：delete 用例中断言它不受影响。
            VeneraReaderService(seed.comicReading, seed.history).saveProgress(
                SourceKey, OtherWorkId, "c1", "p0", 0, "另一部", "第一话",
            )
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

    private fun openEphemeral(profile: File): ExtensionSession = runBlocking {
        openExtensionSession(
            driver(profile),
            FileSystem.SYSTEM,
            profile.toOkioPath(),
            HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) { applyReaderDefaults() },
            VeneraEnvironment("1.16.0", "zh_CN", "test"),
        )
    }

    private fun driver(profile: File): JdbcSqliteDriver =
        JdbcSqliteDriver("jdbc:sqlite:${File(profile, "extensions.db").absolutePath}")

    private fun checkHidden(profile: File): Set<Pair<String, String>> {
        driver(profile).use { driver ->
            return ExtensionDb(driver).extensionDbQueries.selectHiddenHistory()
                .executeAsList().map { it.source_key to it.work_id }.toSet()
        }
    }

    private fun categoryIdOf(profile: File, name: String): Long {
        driver(profile).use { db ->
            return ExtensionDb(db).extensionDbQueries.selectCategoriesOrdered()
                .executeAsList().first { it.name == name }.id
        }
    }

    private fun waitCategoryId(profile: File, name: String): Long {
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
                queries.selectProgress(SourceKey, WorkId, "ep_2")
                    .executeAsOneOrNull()?.page_index == pageIndex
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
            runBlocking { store.resolve(SourceKey, WorkId) }.mode == mode
        }
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
        val out = File(dir, "library-mutation-$name.png")
        javax.imageio.ImageIO.write(awt, "png", out)
        println("screenshot: ${out.absolutePath}")
    }

    private companion object {
        const val ActionDelete = "delete"
        const val ActionClear = "clear"
        const val SourceKey = "example_source"
        const val WorkId = "demo"
        const val OtherWorkId = "other"
        const val CategoryName = "追更"
        const val DetailHeadingTag = "source-detail-heading"
        const val DetailFavoriteTag = "detail-favorite"
        const val CoverRgb = 0x336699
        const val PageKey = "https://example.invalid/images/3.png"
    }

    // endregion
}
