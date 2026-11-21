package app.koyura.app.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
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
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.koyura.app.ExtensionApp
import app.koyura.app.NavigationBarTestTag
import app.koyura.app.NavigationRailTestTag
import app.koyura.app.browse.BrowseSourcesTabTag
import app.koyura.app.browse.DetailFabTag
import app.koyura.app.browse.SourceSearchQueryTag
import app.koyura.app.browse.SourceSearchSubmitTag
import app.koyura.app.browse.sourceResultTag
import app.koyura.app.browse.venera.VeneraFixtures
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.session.ExtensionSession
import app.koyura.data.session.openExtensionSession
import app.koyura.features.reader.comic.ReaderPageIndicatorTag
import app.koyura.features.reader.comic.ReaderRootTestTag
import app.koyura.features.reader.comic.ReaderTapNextTag
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * With no environment variables the two runs are part of the normal JVM suite.
 * Set READER_COMIC_RESTART_PROFILE and run READER_COMIC_RESTART_PHASE=read and =resume in
 * consecutive test workers to verify restart across JVM processes using the same profile.
 */
class VeneraReaderRestartTest {
    @Test
    fun restoresSameChapterAndPageInProductionShell() {
        val profilePath = System.getenv("READER_COMIC_RESTART_PROFILE")
        if (profilePath != null) {
            val profile = File(profilePath)
            when (System.getenv("READER_COMIC_RESTART_PHASE")) {
                "read" -> readAndClose(profile)
                "resume" -> resume(profile)
                else -> error("READER_COMIC_RESTART_PHASE must be read or resume")
            }
        } else {
            val profile = Files.createTempDirectory("reader-comic-restart").toFile()
            try {
                readAndClose(profile)
                resume(profile)
            } finally {
                profile.deleteRecursively()
            }
        }
    }

    private fun readAndClose(profile: File) {
        runBlocking {
            val seed = open(profile)
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
        // Match the schema of the installed build before comic progress was introduced.
        driver(profile).use {
            it.execute(null, "DROP TABLE reading_progress", 0, null)
            it.execute(null, "PRAGMA user_version = 1", 0, null)
        }
        val requests = ConcurrentLinkedQueue<String>()
        runComposeUiTest {
            setContent {
                Box(Modifier.requiredSize(420.dp, 760.dp)) {
                    ExtensionApp { open(profile, requests) }
                }
            }
            openDetail()
            awaitTag("source-detail-chapter-ep_2")
            onNodeWithTag("source-detail-chapter-ep_2").performClick()
            awaitTag("reader-page-image-0")
            awaitDiskPage(profile, pageIndex = 0)
            onNodeWithTag(ReaderTapNextTag).performClick()
            awaitPageIndicator("2 / 3")
            onNodeWithTag(ReaderTapNextTag).performClick()
            awaitThirdPage()
            awaitDiskPage(profile, pageIndex = 2)
        }
        assertChapterRequests(requests)
        runBlocking {
            val reopened = open(profile)
            try {
                val progress = assertNotNull(reopened.comicReading.readLatestProgress(SourceKey, "demo"))
                assertEquals("ep_2", progress.chapterId)
                assertEquals(2, progress.pageIndex)
                assertEquals("https://example.invalid/images/3.png", progress.pageKey)
                assertEquals(VeneraFixtures.IndexUrl, reopened.service.listRepositories().single().url)
            } finally {
                reopened.close()
            }
        }
        println("JVM ${ProcessHandle.current().pid()}: saved ep_2 page 3 with original extension and repository")
    }

    private fun resume(profile: File) {
        val requests = ConcurrentLinkedQueue<String>()
        runComposeUiTest {
            // Startup restores the installed script from disk and builds fresh data and UI services.
            setContent {
                Box(Modifier.requiredSize(420.dp, 760.dp)) {
                    ExtensionApp { open(profile, requests) }
                }
            }
            openDetail()
            awaitTag(DetailFabTag)
            onNodeWithTag(DetailFabTag).performClick()
            awaitThirdPage()
        }
        assertChapterRequests(requests)
        assertEquals("/images/3.png", requests.first { it.startsWith("/images/") })
        println("JVM ${ProcessHandle.current().pid()}: restored ep_2 and rendered its saved third page")
    }

    private fun ComposeUiTest.openDetail() {
        // Scoped to the shell bar/rail: the Library page owns tab semantics for its
        // own category row.
        val tabs = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            (hasAnyAncestor(hasTestTag(NavigationBarTestTag)) or
                hasAnyAncestor(hasTestTag(NavigationRailTestTag)))
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(tabs).fetchSemanticsNodes().size == 5
        }
        onAllNodes(tabs)[3].performClick()
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

    private fun ComposeUiTest.awaitDiskPage(profile: File, pageIndex: Long) {
        driver(profile).use { driver ->
            val queries = ExtensionDb(driver).extensionDbQueries
            waitUntil(timeoutMillis = 10_000) {
                queries.selectProgress(SourceKey, "demo", "ep_2").executeAsOneOrNull()?.page_index == pageIndex
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

    private fun ComposeUiTest.awaitPageIndicator(value: String) {
        waitUntil(timeoutMillis = 10_000) { runCatching { onNodeWithTag(ReaderPageIndicatorTag).assertTextEquals(value) }.isSuccess }
    }

    private fun assertChapterRequests(requests: Collection<String>) {
        assertEquals(listOf("/api/comics/demo/chapters/2"), requests.filter { "/chapters/" in it })
        assertTrue("/images/3.png" in requests)
    }

    private suspend fun open(
        profile: File,
        requests: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue(),
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

    private companion object {
        const val SourceKey = "example_source"
    }
}
