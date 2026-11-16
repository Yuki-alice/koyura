package dev.reader.data.reading

import dev.reader.data.extension.ExtensionService
import dev.reader.data.extension.createTestDriver
import dev.reader.domain.extension.ReadingFailure
import dev.reader.network.applyReaderDefaults
import dev.reader.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private const val IMG_KEY = "img_src"

private fun imageScript(onImage: String, onThumb: String? = null) = """
    class ImageComicSource extends ComicSource {
        name = "Image";
        key = "$IMG_KEY";
        version = "1.0.0";
        minAppVersion = "1.16.0";
        comic = {
            onImageLoad: $onImage,
            ${onThumb?.let { "onThumbnailLoad: $it," } ?: ""}
            onClickTag() { return null; },
        };
    }
""".trimIndent()

private fun imageScriptNoCallbacks() = """
    class ImageComicSource extends ComicSource {
        name = "Image";
        key = "$IMG_KEY";
        version = "1.0.0";
        minAppVersion = "1.16.0";
        comic = {
            onClickTag() { return null; },
        };
    }
""".trimIndent()

private val readingEnv = VeneraEnvironment("1.16.0", "zh_CN", "test")

private suspend fun withReading(
    client: HttpClient,
    script: String,
    block: suspend (ExtensionService, ComicReadingService) -> Unit,
) {
    val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-img-${Uuid.random()}"
    val driver = createTestDriver()
    val service = ExtensionService(driver, FileSystem.SYSTEM, scriptsDir, client, readingEnv)
    val reading = ComicReadingService(driver, service, client)
    try {
        service.open()
        service.restore()
        service.installFromContent("Image", script, IMG_KEY)
        block(service, reading)
        service.runtime?.let {
            assertEquals(0, it.imageCallbackCount(), "image handles must be released")
        }
    } finally {
        service.close()
        client.close()
        driver.close()
        FileSystem.SYSTEM.deleteRecursively(scriptsDir)
    }
}

class ComicImageCallbacksTest {
    @Test
    fun plainConfigDownloadsWithMethodAndHeaders() = runTest {
        var seenMethod: HttpMethod? = null
        var seenHeaders = mapOf<String, String?>()
        var requests = 0
        val mock = MockEngine { request ->
            requests++
            seenMethod = request.method
            seenHeaders = mapOf(
                "Referer" to request.headers["Referer"],
                "X-Custom" to request.headers["X-Custom"],
            )
            respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val config = """(key) => ({ url: key + "?t=s", method: "post", headers: { "Referer": "https://example.invalid/", "X-Custom": "v" }, data: "hi" })"""
        withReading(client, imageScript(config)) { service, reading ->
            val bytes = reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/u.jpg")
            assertTrue(bytes.contentEquals(byteArrayOf(1, 2, 3)))
            assertEquals(1, requests)
            assertEquals(HttpMethod.Post, seenMethod)
            assertEquals("https://example.invalid/", seenHeaders["Referer"])
            assertEquals("v", seenHeaders["X-Custom"])
        }
    }

    @Test
    fun syncAndAsyncOnResponseTransformBytes() = runTest {
        for (async in listOf(false, true)) {
            val fn = if (async) "async (b) => b.reverse()" else "(b) => b.reverse()"
            val mock = MockEngine { respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK) }
            val client = HttpClient(mock) { applyReaderDefaults() }
            val config = "(key) => ({ url: key, onResponse: $fn })"
            withReading(client, imageScript(config)) { _, reading ->
                assertEquals(
                    listOf(3.toByte(), 2.toByte(), 1.toByte()),
                    reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/u.jpg").toList(),
                )
            }
        }
    }

    @Test
    fun thumbnailOnResponseApplies() = runTest {
        var requests = 0
        val mock = MockEngine {
            requests++
            respond(byteArrayOf(4, 5), HttpStatusCode.OK)
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val thumb = "(key) => ({ url: key, onResponse: async (b) => { const o = new Uint8Array(b.length * 2); o.set(b); o.set(b, b.length); return o; } })"
        withReading(client, imageScript("(key) => ({ url: key })", thumb)) { _, reading ->
            assertEquals(
                listOf(4.toByte(), 5.toByte(), 4.toByte(), 5.toByte()),
                reading.loadThumbnailBytes(IMG_KEY, "https://example.invalid/t.jpg").toList(),
            )
            assertEquals(1, requests)
        }
    }

    @Test
    fun viewOffsetsNormalizeExactly() = runTest {
        val mock = MockEngine { respond(byteArrayOf(0, 1, 2, 3, 4), HttpStatusCode.OK) }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val config = "(key) => ({ url: key, onResponse: (b) => b.subarray(1, 4) })"
        withReading(client, imageScript(config)) { _, reading ->
            assertEquals(
                listOf(1.toByte(), 2.toByte(), 3.toByte()),
                reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/u.jpg").toList(),
            )
        }
    }

    @Test
    fun invalidTransformResultsAreRejectedWithoutRetry() = runTest {
        var requests = 0
        val mock = MockEngine {
            requests++
            respond(byteArrayOf(9), HttpStatusCode.OK)
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val config = "(key) => ({ url: key, onResponse: (b) => 42 })"
        withReading(client, imageScript(config)) { _, reading ->
            assertFailsWith<ReadingFailure.InvalidTransform> {
                reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/u.jpg")
            }
            assertEquals(1, requests, "no onLoadFailed means a single attempt")
        }
    }

    @Test
    fun httpErrorFallsBackToNewConfig() = runTest {
        val seen = mutableListOf<String>()
        var origReferer: String? = null
        val mock = MockEngine { request ->
            val path = request.url.encodedPath
            if (path == "/orig.jpg") {
                origReferer = request.headers["Referer"]
                seen += "${request.method.value} $path"
                respond("denied", HttpStatusCode.Forbidden)
            } else if (path == "/new.jpg") {
                seen += "${request.method.value} $path"
                assertEquals("hi", (request.body as? TextContent)?.text)
                assertEquals("1", request.headers["X-Retry"])
                respond(byteArrayOf(7), HttpStatusCode.OK)
            } else {
                seen += "${request.method.value} $path"
                respond("gone", HttpStatusCode.NotFound)
            }
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val config = """(key) => ({
            url: "https://example.invalid/orig.jpg",
            headers: { "Referer": "https://example.invalid/" },
            onResponse: (b) => b,
            onLoadFailed: () => ({ url: "https://example.invalid/new.jpg", method: "POST", headers: { "X-Retry": "1" }, data: "hi" }),
        })"""
        withReading(client, imageScript(config)) { _, reading ->
            val bytes = reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/orig.jpg")
            assertTrue(bytes.contentEquals(byteArrayOf(7)))
            assertEquals(listOf("GET /orig.jpg", "POST /new.jpg"), seen)
            assertEquals("https://example.invalid/", origReferer)
        }
    }

    @Test
    fun persistentFailureStopsAfterBudget() = runTest {
        val requested = mutableListOf<String>()
        val mock = MockEngine { request ->
            requested += request.url.toString()
            respond("down", HttpStatusCode.InternalServerError)
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        // Counts onLoadFailed invocations through distinct replacement URLs; each
        // replacement carries onLoadFailed again so the budget (5 retries) is
        // exercised end to end.
        val config = """(key) => ({
            url: "https://example.invalid/img.jpg",
            onLoadFailed: (() => { let n = 0; const retry = () => ({ url: "https://example.invalid/r" + (++n) + ".jpg", onLoadFailed: retry }); return retry; })(),
        })"""
        withReading(client, imageScript(config)) { _, reading ->
            val failure = assertFailsWith<ReadingFailure.HttpError> {
                reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/img.jpg")
            }
            assertEquals(500, failure.status)
            assertEquals(
                listOf(
                    "https://example.invalid/img.jpg",
                    "https://example.invalid/r1.jpg",
                    "https://example.invalid/r2.jpg",
                    "https://example.invalid/r3.jpg",
                    "https://example.invalid/r4.jpg",
                    "https://example.invalid/r5.jpg",
                ),
                requested,
                "one initial fetch plus five onLoadFailed retries",
            )
        }
    }

    @Test
    fun cancelDuringDownloadReleasesHandles() = runTest {
        val started = CompletableDeferred<Unit>()
        val mock = MockEngine {
            started.complete(Unit)
            awaitCancellation()
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val config = "(key) => ({ url: key })"
        withReading(client, imageScript(config)) { service, reading ->
            val load = launch {
                reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/u.jpg")
            }
            started.await()
            load.cancelAndJoin()
            assertEquals(0, service.runtime!!.imageCallbackCount())
        }
    }

    @Test
    fun closedRuntimeEndsLoad() = runTest {
        val client = HttpClient(MockEngine { respond(byteArrayOf(1), HttpStatusCode.OK) }) { applyReaderDefaults() }
        val config = "(key) => ({ url: key })"
        withReading(client, imageScript(config)) { service, reading ->
            service.runtime!!.close()
            assertFailsWith<ReadingFailure.RuntimeClosed> {
                reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/u.jpg")
            }
            service.close()
            assertFailsWith<ReadingFailure.SourceMissing> {
                reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/u.jpg")
            }
        }
    }

    @Test
    fun missingCallbackFallsBackToDirectFetch() = runTest {
        var seenUrl: String? = null
        val mock = MockEngine { request ->
            seenUrl = request.url.toString()
            respond(byteArrayOf(3), HttpStatusCode.OK)
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        withReading(client, imageScriptNoCallbacks()) { _, reading ->
            assertTrue(reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/raw.jpg").contentEquals(byteArrayOf(3)))
            assertEquals("https://example.invalid/raw.jpg", seenUrl)
        }
    }

    @Test
    fun relativeImageUrlIsRejected() = runTest {
        val client = HttpClient(MockEngine { respond(byteArrayOf(1), HttpStatusCode.OK) }) { applyReaderDefaults() }
        withReading(client, imageScriptNoCallbacks()) { _, reading ->
            assertFailsWith<ReadingFailure.ConfigInvalid> {
                reading.loadImageBytes(IMG_KEY, "w", "ep1", "relative.jpg")
            }
        }
    }
}
