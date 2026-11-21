package app.koyura.app.history

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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.koyura.app.ExtensionApp
import app.koyura.app.NavigationBarTestTag
import app.koyura.app.NavigationRailTestTag
import app.koyura.app.browse.BrowseSourcesTabTag
import app.koyura.app.browse.SourceDetailBackTag
import app.koyura.app.browse.SourceSearchQueryTag
import app.koyura.app.browse.SourceSearchSubmitTag
import app.koyura.app.browse.sourceResultTag
import app.koyura.app.browse.venera.VeneraFixtures
import app.koyura.app.reader.pngBytes
import app.koyura.features.reader.comic.ReaderBackTag
import app.koyura.features.reader.comic.ReaderPageIndicatorTag
import app.koyura.features.reader.comic.ReaderRootTestTag
import app.koyura.features.reader.comic.ReaderTapNextTag
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.reader.preferences.ReaderPreferencesStore
import app.koyura.data.session.ExtensionSession
import app.koyura.data.session.openExtensionSession
import app.koyura.domain.reader.preferences.ReadingMode
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * End-to-end acceptance for the History tab with the unmodified upstream Venera example source:
 * work detail -> chapter 2 page 3 -> History -> close and reopen the profile -> resume from History.
 *
 * The list must render from SQLite alone (no network before the Continue tap); resuming reuses the
 * production reader chapter-position and page-key restoration and asserts the saved third page and
 * its image are shown. Also covers a pre-metadata database whose source no longer exists: the row
 * keeps its progress and ID fallback while Continue reports a clear failure.
 *
 * For the cross-process check, run this class twice with the same
 * KOYURA_HISTORY_RESTART_PROFILE and KOYURA_HISTORY_RESTART_PHASE=read then resume.
 * Run each Gradle test task with --rerun so each phase gets a fresh test worker.
 */
class VeneraHistoryFlowTest {
    @Test
    fun historyRestoresSavedChapterAndPageAfterRestart() {
        val profilePath = System.getenv("KOYURA_HISTORY_RESTART_PROFILE")
        if (profilePath != null) {
            val profile = File(profilePath)
            when (System.getenv("KOYURA_HISTORY_RESTART_PHASE")) {
                "read" -> {
                    installExampleSource(profile)
                    readThenOpenHistory(profile, exitFromReader = true)
                    verifySavedReaderState(profile, "read")
                }
                "resume" -> {
                    resumeFromHistory(profile)
                    verifySavedReaderState(profile, "resume")
                }
                else -> error("KOYURA_HISTORY_RESTART_PHASE must be read or resume")
            }
            return
        }

        val profile = Files.createTempDirectory("reader-history-flow").toFile()
        try {
            installExampleSource(profile)
            readThenOpenHistory(profile)
            resumeFromHistory(profile)
        } finally {
            profile.deleteRecursively()
        }
    }

    private fun installExampleSource(profile: File) = runBlocking {
        val seed = openSession(profile, ConcurrentLinkedQueue())
        try {
            seed.service.addRepository("Example", VeneraFixtures.IndexUrl)
            seed.service.installFromContent(
                "Example Source",
                VeneraFixtures.upstreamScriptBytes().decodeToString(),
                expectedKey = SourceKey,
            )
        } finally {
            seed.close()
        }
    }

    private fun verifySavedReaderState(profile: File, phase: String) {
        val row = driver(profile).use { db ->
            ExtensionDb(db).extensionDbQueries.selectProgress(SourceKey, "demo", "ep_2").executeAsOne()
        }
        assertEquals(2L, row.page_index)
        assertEquals("https://example.invalid/images/3.png", row.page_key)
        val mode = runBlocking {
            ReaderPreferencesStore(profile.toOkioPath()).resolve(SourceKey, "demo").mode
        }
        assertEquals(ReadingMode.RIGHT_TO_LEFT, mode)
        println("phase=$phase pid=${ProcessHandle.current().pid()} chapter=ep_2 page=3 key=${row.page_key} mode=$mode")
    }

    @Test
    fun preMetadataProgressStaysVisibleAndFailsClearlyWhenSourceIsGone() {
        val profile = Files.createTempDirectory("reader-history-legacy").toFile()
        try {
            driver(profile).use { db -> execScript(db, LegacySchema) }
            runComposeUiTest {
                setContent { ExtensionApp { openSession(profile, ConcurrentLinkedQueue()) } }
                selectTab(HistoryTabIndex)
                awaitTag(HistoryRootTestTag)
                awaitTag(historyRowTag("gone_source", "legacy-work"))
                awaitTag(historyGroupTag("unknown"))
                assertEquals("legacy-work", textOf(historyRowTitleTag("gone_source", "legacy-work")))
                val legacyDetail = textOf(historyRowDetailTag("gone_source", "legacy-work"))
                assertTrue("ep_9" in legacyDetail && "7" in legacyDetail, legacyDetail)

                onNodeWithTag(historyContinueTag("gone_source", "legacy-work")).performClick()
                awaitTag("history-action-error")
                // The failure is reported without dropping the row or its saved progress.
                onNodeWithTag(historyRowTag("gone_source", "legacy-work")).assertIsDisplayed()
                assertEquals("legacy-work", textOf(historyRowTitleTag("gone_source", "legacy-work")))
            }
            driver(profile).use { db ->
                val columns = tableColumns(db, "reading_progress")
                assertTrue(columns.containsAll(listOf("read_at", "work_title", "chapter_title")))
                val row = ExtensionDb(db).extensionDbQueries.selectHistoryLatest().executeAsList().single()
                assertEquals("legacy-work", row.work_id)
                assertEquals(6L, row.page_index)
                assertNull(row.read_at)
                assertNull(row.work_title)
            }
        } finally {
            profile.deleteRecursively()
        }
    }

    @Test
    fun missingChapterFailsClearlyWithoutDroppingTheRow() {
        val profile = Files.createTempDirectory("reader-history-stale").toFile()
        try {
            runBlocking {
                val seed = openSession(profile, ConcurrentLinkedQueue())
                try {
                    seed.service.addRepository("Example", VeneraFixtures.IndexUrl)
                    seed.service.installFromContent(
                        "Example Source",
                        VeneraFixtures.upstreamScriptBytes().decodeToString(),
                        expectedKey = SourceKey,
                    )
                } finally {
                    seed.close()
                }
            }
            // The source is installed, but the saved chapter is no longer in its catalog.
            driver(profile).use { db ->
                ExtensionDb(db).extensionDbQueries.upsertProgressWithMeta(
                    source_key = SourceKey,
                    work_id = "demo",
                    chapter_id = "ep_9",
                    page_key = "https://example.invalid/gone.png",
                    page_index = 4,
                    read_at = 1_794_916_800_000,
                    work_title = "星图",
                    chapter_title = "第九章",
                )
            }
            runComposeUiTest {
                setContent { ExtensionApp { openSession(profile, ConcurrentLinkedQueue()) } }
                selectTab(HistoryTabIndex)
                awaitTag(historyRowTag(SourceKey, "demo"))
                val detail = textOf(historyRowDetailTag(SourceKey, "demo"))
                assertTrue("第九章" in detail && "5" in detail, detail)

                onNodeWithTag(historyContinueTag(SourceKey, "demo")).performClick()
                awaitTag("history-action-error")
                val error = textOf("history-action-error")
                assertTrue("no longer listed" in error, error)
                // The failed continue keeps the row and its saved page visible.
                onNodeWithTag(historyRowTag(SourceKey, "demo")).assertIsDisplayed()
                assertEquals(detail, textOf(historyRowDetailTag(SourceKey, "demo")))
            }
            driver(profile).use { db ->
                val row = ExtensionDb(db).extensionDbQueries
                    .selectProgress(SourceKey, "demo", "ep_9").executeAsOne()
                assertEquals("https://example.invalid/gone.png", row.page_key)
                assertEquals(4L, row.page_index)
            }
        } finally {
            profile.deleteRecursively()
        }
    }

    private fun readThenOpenHistory(profile: File, exitFromReader: Boolean = false) {
        val requests = ConcurrentLinkedQueue<String>()
        runComposeUiTest {
            var width by mutableIntStateOf(420)
            setContent {
                Box(Modifier.requiredSize(width.dp, 760.dp)) {
                    ExtensionApp { openSession(profile, requests) }
                }
            }
            openDemoDetail()
            awaitTag("source-detail-chapter-ep_2")
            onNodeWithTag("source-detail-chapter-ep_2").performClick()
            awaitTag("reader-page-image-0")
            awaitDiskPage(profile, pageIndex = 0)
            onNodeWithTag(ReaderTapNextTag).performClick()
            awaitPageIndicator("2 / 3")
            onNodeWithTag(ReaderTapNextTag).performClick()
            awaitThirdPage()
            awaitDiskPage(profile, pageIndex = 2)
            awaitStamped(profile)

            awaitTag(ReaderChromeModeTag)
            onNodeWithTag(ReaderChromeModeTag).performClick()
            val rtl = readerChromeModeOptionTag(ReadingMode.RIGHT_TO_LEFT.name)
            awaitTag(rtl)
            onNodeWithTag(rtl).performClick()
            onNodeWithTag(ReaderChromeModeApplyTag).performClick()
            val store = ReaderPreferencesStore(profile.toOkioPath())
            waitUntil(timeoutMillis = 10_000) {
                runBlocking { store.resolve(SourceKey, "demo").mode } == ReadingMode.RIGHT_TO_LEFT
            }
            awaitThirdPage()
            runOnIdle { width = 1000 }
            assertRtlDoublePageAtThird()
            awaitDiskPage(profile, pageIndex = 2)
            if (exitFromReader) return@runComposeUiTest

            // Back from the reader lands on the shared detail page.
            onNodeWithTag(ReaderBackTag).performClick()
            awaitTag("source-detail")

            // G2：详情/搜索在跨 tab 共享栈，切 tab 不会关掉它们；
            // 先逐层退回，再碰底下的历史 tab。
            onNodeWithTag(SourceDetailBackTag).performClick()
            awaitTag(SourceSearchQueryTag)
            onNodeWithTag("source-search-back").performClick()

            selectTab(HistoryTabIndex)
            awaitTag(historyRowTag(SourceKey, "demo"))
            assertEquals("星图", textOf(historyRowTitleTag(SourceKey, "demo")))
            val detail = textOf(historyRowDetailTag(SourceKey, "demo"))
            assertTrue("第二章" in detail && "3" in detail, detail)
            onNodeWithTag(historyGroupTag(java.time.LocalDate.now().toString())).assertIsDisplayed()

            // The row opens the detail route in the shared stack.
            onNodeWithTag(historyRowTag(SourceKey, "demo")).performClick()
            awaitTag("source-detail-heading")
        }
        assertEquals(
            listOf("/api/comics/demo/chapters/2"),
            requests.filter { "/chapters/" in it },
        )
        assertTrue("/images/3.png" in requests)
    }

    private fun resumeFromHistory(profile: File) {
        val requests = ConcurrentLinkedQueue<String>()
        runComposeUiTest {
            setContent {
                Box(Modifier.requiredSize(1000.dp, 760.dp)) {
                    ExtensionApp { openSession(profile, requests) }
                }
            }
            selectTab(HistoryTabIndex)
            awaitTag(historyRowTag(SourceKey, "demo"))
            // The list comes from SQLite only: nothing was fetched to display it.
            assertTrue(requests.isEmpty(), "History must render offline, but made $requests")
            val detail = textOf(historyRowDetailTag(SourceKey, "demo"))
            assertTrue("第二章" in detail && "3" in detail, detail)

            onNodeWithTag(historyContinueTag(SourceKey, "demo")).performClick()
            assertRtlDoublePageAtThird()
            onNodeWithTag(ReaderChromeModeTag).performClick()
            val rtl = readerChromeModeOptionTag(ReadingMode.RIGHT_TO_LEFT.name)
            awaitTag(rtl)
            onNodeWithTag(rtl).assertIsSelected()
            onNodeWithTag(ReaderChromeModeApplyTag).performClick()
            awaitTag(ReaderBackTag)
            assertTrue("/images/3.png" in requests)
            assertEquals(
                setOf("/api/comics/demo", "/api/comics/demo/chapters/2"),
                requests.filter { "/api/" in it }.toSet(),
            )

            // Back from the reader returns to the History tab, not to the work detail page.
            onNodeWithTag(ReaderBackTag).performClick()
            awaitTag(HistoryRootTestTag)
            awaitTag(historyRowTag(SourceKey, "demo"))
        }
    }

    private fun ComposeUiTest.assertRtlDoublePageAtThird() {
        awaitThirdPage()
        awaitTag("reader-page-image-1")
        val secondPage = onNodeWithTag("reader-page-image-1").fetchSemanticsNode().boundsInRoot
        val thirdPage = onNodeWithTag("reader-page-image-2").fetchSemanticsNode().boundsInRoot
        assertTrue(thirdPage.center.x < secondPage.center.x, "expected the RTL double spread")
    }

    private fun ComposeUiTest.openDemoDetail() {
        selectTab(BrowseTabIndex)
        awaitTag(BrowseSourcesTabTag)
        onNodeWithTag(BrowseSourcesTabTag).performClick()
        awaitTag("source-row-example_source")
        onNodeWithTag("source-row-example_source").performClick()
        awaitTag(SourceSearchQueryTag)
        onNodeWithTag(SourceSearchQueryTag).performTextInput("星")
        onNodeWithTag(SourceSearchSubmitTag).performClick()
        awaitTag(sourceResultTag("demo"))
        onNodeWithTag(sourceResultTag("demo")).performClick()
        awaitTag("source-detail-heading")
    }

    private fun ComposeUiTest.selectTab(index: Int) {
        // Scoped to the shell bar/rail: the Library page owns tab semantics for its
        // own category row.
        val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            (hasAnyAncestor(hasTestTag(NavigationBarTestTag)) or
                hasAnyAncestor(hasTestTag(NavigationRailTestTag)))
        waitUntil(timeoutMillis = 15_000) {
            onAllNodes(tabs).fetchSemanticsNodes().size == 5
        }
        onAllNodes(tabs)[index].performClick()
    }

    private fun ComposeUiTest.awaitDiskPage(profile: File, pageIndex: Long) {
        driver(profile).use { db ->
            val queries = ExtensionDb(db).extensionDbQueries
            waitUntil(timeoutMillis = 10_000) {
                queries.selectProgress(SourceKey, "demo", "ep_2")
                    .executeAsOneOrNull()?.page_index == pageIndex
            }
        }
    }

    private fun ComposeUiTest.awaitStamped(profile: File) {
        driver(profile).use { db ->
            val queries = ExtensionDb(db).extensionDbQueries
            waitUntil(timeoutMillis = 10_000) {
                queries.selectHistoryLatest().executeAsList().any { row ->
                    row.source_key == SourceKey && row.work_id == "demo" &&
                        row.read_at != null && row.work_title == "星图" && row.chapter_title == "第二章"
                }
            }
        }
    }

    private fun ComposeUiTest.awaitThirdPage() {
        awaitPageIndicator("3 / 3")
        awaitTag("reader-page-image-2")
        // G2：阅读器是独立叠层，底层详情页仍在组合；裸文本会同时命中两层，按阅读器根限定。
        val readerTitle = hasTestTag(ReaderRootTestTag) and hasAnyDescendant(hasText("第二章", substring = true))
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(readerTitle).fetchSemanticsNodes().size == 1
        }
        onAllNodes(readerTitle)[0].assertIsDisplayed()
    }

    private fun ComposeUiTest.awaitTag(tag: String) {
        waitUntil(conditionDescription = "visible node: $tag", timeoutMillis = 15_000) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1 && onNodeWithTag(tag).isDisplayed()
        }
    }

    private fun ComposeUiTest.textOf(tag: String): String =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun ComposeUiTest.awaitPageIndicator(value: String) {
        waitUntil(timeoutMillis = 10_000) {
            runCatching { onNodeWithTag(ReaderPageIndicatorTag).assertTextEquals(value) }.isSuccess
        }
    }

    private suspend fun openSession(
        profile: File,
        requests: ConcurrentLinkedQueue<String>,
    ): ExtensionSession {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            requests.add(path)
            when (path) {
                "/api/search" -> respond(VeneraFixtures.searchJson())
                "/api/comics/demo" -> respond(VeneraFixtures.detailJson())
                "/api/comics/demo/chapters/2" -> respond(
                    """{"images":["https://example.invalid/images/1.png","https://example.invalid/images/2.png","https://example.invalid/images/3.png"]}""",
                )
                "/images/1.png", "/images/2.png", "/images/3.png" -> {
                    assertEquals("https://example.invalid/", request.headers[HttpHeaders.Referrer])
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

    private fun tableColumns(driver: SqlDriver, table: String): Set<String> {
        val columns = mutableSetOf<String>()
        driver.executeQuery(null, "PRAGMA table_info($table)", { cursor ->
            while (cursor.next().value) {
                cursor.getString(1)?.let { columns += it }
            }
            app.cash.sqldelight.db.QueryResult.Value(Unit)
        }, 0, null)
        return columns
    }

    private fun execScript(driver: SqlDriver, script: String) {
        script.split(";").map(String::trim).filter(String::isNotEmpty).forEach { statement ->
            driver.execute(null, statement, 0, null)
        }
    }

    private companion object {
        const val SourceKey = "example_source"
        const val HistoryTabIndex = 2
        const val BrowseTabIndex = 3

        // d77a7df-era schema: extension tables plus reading_progress before display metadata.
        val LegacySchema = """
            CREATE TABLE repositories(
              id TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              url TEXT NOT NULL,
              enabled INTEGER NOT NULL DEFAULT 1
            );
            CREATE TABLE origins(
              source_key TEXT PRIMARY KEY,
              kind TEXT NOT NULL,
              repository_id TEXT,
              repository_name TEXT,
              url TEXT
            );
            CREATE TABLE extensions(
              source_key TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              version TEXT NOT NULL,
              file_name TEXT NOT NULL,
              origin_kind TEXT NOT NULL,
              origin_repository_id TEXT,
              origin_repository_name TEXT,
              origin_url TEXT
            );
            CREATE TABLE reading_progress(
              source_key TEXT NOT NULL,
              work_id TEXT NOT NULL,
              chapter_id TEXT NOT NULL,
              page_key TEXT NOT NULL,
              page_index INTEGER NOT NULL,
              PRIMARY KEY (source_key, work_id, chapter_id)
            );
            CREATE INDEX reading_progress_work_idx ON reading_progress(source_key, work_id);
            INSERT INTO reading_progress(source_key, work_id, chapter_id, page_key, page_index)
            VALUES('gone_source', 'legacy-work', 'ep_9', 'https://legacy.invalid/7.png', 6);
        """.trimIndent()
    }
}
