package dev.reader.data.reading

import dev.reader.data.extension.ExtensionService
import dev.reader.data.extension.createTestDriver
import dev.reader.domain.extension.ReadingFailure
import dev.reader.network.applyReaderDefaults
import dev.reader.source.runtime.VeneraCallback
import dev.reader.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private const val READ_KEY = "read_src"

private fun readingScript(images: String, onImageLoad: String? = null) = """
    class ReadSource extends ComicSource {
        name = "Read";
        key = "$READ_KEY";
        version = "1.0.0";
        minAppVersion = "1.16.0";
        url = "https://example.invalid/read.js";
        comic = {
            loadEp: (comicId, epId) => ({ images: $images }),
            ${onImageLoad?.let { "onImageLoad: $it," } ?: ""}
            onClickTag() { return null; },
        };
    }
""".trimIndent()

private fun readingScriptNoCallback() = """
    class ReadSource extends ComicSource {
        name = "Read";
        key = "$READ_KEY";
        version = "1.0.0";
        minAppVersion = "1.16.0";
        url = "https://example.invalid/read.js";
        comic = {
            loadEp: (comicId, epId) => ({ images: ["u1"] }),
            onClickTag() { return null; },
        };
    }
""".trimIndent()

private val readingEnv = VeneraEnvironment("1.16.0", "zh_CN", "test")

private suspend fun readingHarness(
    client: HttpClient,
    block: suspend (ExtensionService, ComicReadingService, okio.Path) -> Unit,
) {
    val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-reading-${Uuid.random()}"
    val driver = createTestDriver()
    val service = ExtensionService(driver, FileSystem.SYSTEM, scriptsDir, client, readingEnv)
    val reading = ComicReadingService(driver, service, client)
    try {
        service.open()
        service.restore()
        block(service, reading, scriptsDir)
    } finally {
        service.close()
        client.close()
        driver.close()
        FileSystem.SYSTEM.deleteRecursively(scriptsDir)
    }
}

class ComicReadingServiceTest {
    @Test
    fun openChapterReturnsOrderedPagesAndResumesByKey() = runTest {
        val client = HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) { applyReaderDefaults() }
        readingHarness(client) { service, reading, _ ->
            service.installFromContent("Read", readingScript("""["https://example.invalid/u1","https://example.invalid/u2","https://example.invalid/u3"]"""), READ_KEY)
            val opened = reading.openChapter(READ_KEY, "w", "ep1")
            assertEquals(listOf("https://example.invalid/u1", "https://example.invalid/u2", "https://example.invalid/u3"), opened.pages.map { it.pageKey })
            assertEquals(0, opened.startIndex)
            reading.saveProgress(READ_KEY, "w", "ep1", "https://example.invalid/u2", 1)
            val resumed = reading.openChapter(READ_KEY, "w", "ep1")
            assertEquals(1, resumed.startIndex)
            assertEquals("https://example.invalid/u2", resumed.startPageKey)
        }
    }

    @Test
    fun duplicateImageKeysGetSuffixedPageKeys() = runTest {
        val client = HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) { applyReaderDefaults() }
        readingHarness(client) { service, reading, _ ->
            service.installFromContent("Read", readingScript("""["https://example.invalid/x","https://example.invalid/x","https://example.invalid/y"]"""), READ_KEY)
            val opened = reading.openChapter(READ_KEY, "w", "ep1")
            assertEquals(listOf("https://example.invalid/x", "https://example.invalid/x#2", "https://example.invalid/y"), opened.pages.map { it.pageKey })
            assertEquals(listOf("https://example.invalid/x", "https://example.invalid/x", "https://example.invalid/y"), opened.pages.map { it.imageKey })
        }
    }

    @Test
    fun duplicatePageKeysDoNotCollideWithSourceKeys() = runTest {
        val client = HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) { applyReaderDefaults() }
        readingHarness(client) { service, reading, _ ->
            service.installFromContent("Read", readingScript("""["https://example.invalid/x","https://example.invalid/x","https://example.invalid/x#2"]"""), READ_KEY)
            val opened = reading.openChapter(READ_KEY, "w", "ep1")
            assertEquals(
                listOf("https://example.invalid/x", "https://example.invalid/x#3", "https://example.invalid/x#2"),
                opened.pages.map { it.pageKey },
            )
        }
    }

    @Test
    fun loadImageUsesOnImageLoadConfig() = runTest {
        var seenUrl: String? = null
        var seenReferer: String? = null
        val mock = MockEngine { request ->
            seenUrl = request.url.toString()
            seenReferer = request.headers["Referer"]
            respond(byteArrayOf(9, 8, 7), HttpStatusCode.OK)
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val onImageLoad = """(imageKey, comicId, epId) => ({ url: imageKey + "?token=signed", headers: { "Referer": "https://example.invalid/" } })"""
        readingHarness(client) { service, reading, _ ->
            service.installFromContent("Read", readingScript("""["https://example.invalid/u1"]""", onImageLoad), READ_KEY)
            val bytes = reading.loadPageImage(READ_KEY, "w", "ep1", "https://example.invalid/u1")
            assertTrue(bytes.contentEquals(byteArrayOf(9, 8, 7)))
            assertEquals("https://example.invalid/u1?token=signed", seenUrl)
            assertEquals("https://example.invalid/", seenReferer)
        }
    }

    @Test
    fun loadImageWithoutCallbackFetchesUrlDirectly() = runTest {
        var seenUrl: String? = null
        val mock = MockEngine { request ->
            seenUrl = request.url.toString()
            respond(byteArrayOf(1, 2), HttpStatusCode.OK)
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        readingHarness(client) { service, reading, _ ->
            service.installFromContent("Read", readingScriptNoCallback(), READ_KEY)
            val bytes = reading.loadImageBytes(READ_KEY, "w", "ep1", "https://example.invalid/raw.jpg")
            assertTrue(bytes.contentEquals(byteArrayOf(1, 2)))
            assertEquals("https://example.invalid/raw.jpg", seenUrl)
        }
    }

    @Test
    fun unsupportedTransformReturnsClearError() = runTest {
        val client = HttpClient(MockEngine { respond(byteArrayOf(1), HttpStatusCode.OK) }) { applyReaderDefaults() }
        val onImageLoad = """(imageKey) => ({ url: imageKey, modifyImage: "return image;" })"""
        readingHarness(client) { service, reading, _ ->
            service.installFromContent("Read", readingScript("""["https://example.invalid/u1"]""", onImageLoad), READ_KEY)
            val failure = assertFailsWith<ReadingFailure.TransformUnsupported> {
                reading.loadPageImage(READ_KEY, "w", "ep1", "https://example.invalid/u1")
            }
            assertEquals("modifyImage", failure.kind)
        }
    }

    @Test
    fun relativeImageUrlFailsFast() = runTest {
        val client = HttpClient(MockEngine { respond(byteArrayOf(1), HttpStatusCode.OK) }) { applyReaderDefaults() }
        readingHarness(client) { service, reading, _ ->
            service.installFromContent("Read", readingScript("""["relative-key"]"""), READ_KEY)
            assertFailsWith<ReadingFailure.ConfigInvalid> {
                reading.loadPageImage(READ_KEY, "w", "ep1", "relative-key")
            }
        }
    }

    @Test
    fun unknownSourceAndChapterAreDistinguishable() = runTest {
        val strictScript = """
            class ReadSource extends ComicSource {
                name = "Read";
                key = "$READ_KEY";
                version = "1.0.0";
                minAppVersion = "1.16.0";
                url = "https://example.invalid/read.js";
                comic = {
                    loadEp: (comicId, epId) => {
                        if (typeof epId !== "string" || !epId.startsWith("ep_")) throw new Error("Unknown chapter");
                        return { images: ["u1"] };
                    },
                    onClickTag() { return null; },
                };
            }
        """.trimIndent()
        val client = HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) { applyReaderDefaults() }
        readingHarness(client) { service, reading, _ ->
            service.installFromContent("Read", strictScript, READ_KEY)
            assertFailsWith<ReadingFailure.SourceMissing> { reading.openChapter("nope", "w", "ep_1") }
            assertFailsWith<ReadingFailure.ChapterInvalid> { reading.openChapter(READ_KEY, "w", "bad") }
            assertFailsWith<ReadingFailure.PageNotFound> { reading.loadPageImage(READ_KEY, "w", "ep_1", "gone") }
        }
    }

    @Test
    fun missingPageKeyFallsBackToClampedIndex() = runTest {
        val client = HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) { applyReaderDefaults() }
        readingHarness(client) { service, reading, _ ->
            service.installFromContent("Read", readingScript("""["a","b"]"""), READ_KEY)
            reading.saveProgress(READ_KEY, "w", "ep1", "gone", 9)
            val opened = reading.openChapter(READ_KEY, "w", "ep1")
            assertEquals(1, opened.startIndex)
            assertEquals("b", opened.startPageKey)
        }
    }

    @Test
    fun rapidWritesKeepAConsistentRow() = runTest {
        val client = HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) { applyReaderDefaults() }
        readingHarness(client) { service, reading, _ ->
            service.installFromContent("Read", readingScript("""["u1"]"""), READ_KEY)
            (0 until 50).map { i ->
                async { reading.saveProgress(READ_KEY, "w", "ep1", "k$i", i) }
            }.awaitAll()
            val saved = reading.readProgress(READ_KEY, "w", "ep1")!!
            assertEquals("k${saved.pageIndex}", saved.pageKey)
        }
    }

    @Test
    fun chapterlessWorkRoundTripsWithEmptyMarker() = runTest {
        val client = HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) { applyReaderDefaults() }
        readingHarness(client) { service, reading, _ ->
            service.installFromContent("Read", readingScript("""["u1","u2"]"""), READ_KEY)
            val opened = reading.openChapter(READ_KEY, "w", null)
            assertEquals(2, opened.pages.size)
            reading.saveProgress(READ_KEY, "w", null, "u2", 1)
            val saved = reading.readProgress(READ_KEY, "w", null)
            assertEquals("u2", saved?.pageKey)
            assertEquals(null, saved?.chapterId)
            assertEquals("", reading.recentChapter(READ_KEY, "w"))
        }
    }

    @Test
    fun recentChapterPicksLatest() = runTest {
        val client = HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) { applyReaderDefaults() }
        readingHarness(client) { service, reading, _ ->
            service.installFromContent("Read", readingScript("""["u1"]"""), READ_KEY)
            assertEquals(null, reading.recentChapter(READ_KEY, "w"))
            reading.saveProgress(READ_KEY, "w", "c1", "u1", 0)
            reading.saveProgress(READ_KEY, "w", "c2", "u1", 0)
            assertEquals("c2", reading.recentChapter(READ_KEY, "w"))
        }
    }

    @Test
    fun extensionDatabaseMigratesFromV1PreservingRows() = runTest {
        // Simulate a pre-migration (d77a7df) database: current tables minus
        // reading_progress, with repository/origin/extension rows in place.
        val driver = createTestDriver()
        try {
            driver.execute(null, "DROP TABLE IF EXISTS reading_progress", 0, null)
            driver.execute(null, "DROP INDEX IF EXISTS reading_progress_recent_idx", 0, null)
            val db = dev.reader.data.extension.ExtensionDb(driver)
            db.extensionDbQueries.insertRepository("r1", "Main", "https://example.invalid/repo", 1)
            db.extensionDbQueries.upsertOrigin("k1", "url", null, null, "https://example.invalid/k1.js")
            db.extensionDbQueries.upsertExtension("k1", "K", "1.0.0", "k1.js", "url", null, null, "https://example.invalid/k1.js")
            dev.reader.data.extension.ExtensionDb.Schema.migrate(driver, 1, dev.reader.data.extension.ExtensionDb.Schema.version)
            assertEquals("Main", db.extensionDbQueries.selectRepositoryById("r1").executeAsOne().name)
            assertEquals("https://example.invalid/k1.js", db.extensionDbQueries.selectOriginByKey("k1").executeAsOne().url)
            assertEquals("1.0.0", db.extensionDbQueries.selectExtensionByKey("k1").executeAsOne().version)
            db.extensionDbQueries.upsertProgress("k1", "w", "c", "p0", 0)
            assertEquals("p0", db.extensionDbQueries.selectProgress("k1", "w", "c").executeAsOne().page_key)
        } finally {
            driver.close()
        }
    }
}
