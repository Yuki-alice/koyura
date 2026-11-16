package app.koyura.data.reading

import app.koyura.data.extension.ExtensionService
import app.koyura.data.extension.createTestDriver
import app.koyura.domain.extension.ReadingFailure
import app.koyura.network.applyReaderDefaults
import app.koyura.source.runtime.VeneraEnvironment
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

class ImageMergeReviewProbe {
    @Test
    fun headerOnlyConfigDownloadsOriginalImageKey() = runTest {
        val mock = MockEngine { request ->
            assertEquals("https://example.invalid/u.jpg", request.url.toString())
            assertEquals("https://example.invalid/", request.headers[HttpHeaders.Referrer])
            respond(byteArrayOf(7), HttpStatusCode.OK)
        }
        val config = """() => ({ headers: { Referer: "https://example.invalid/" } })"""
        withReading(HttpClient(mock) { applyReaderDefaults() }, imageScript(config)) { _, reading ->
            assertEquals(listOf(7.toByte()), reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/u.jpg").toList())
        }
    }

    @Test
    fun cancellationReleasesAnActuallyLiveCallback() = runTest {
        val started = CompletableDeferred<Unit>()
        val mock = MockEngine { started.complete(Unit); awaitCancellation() }
        val config = "(key) => ({ url: key, onResponse: (bytes) => bytes })"
        withReading(HttpClient(mock) { applyReaderDefaults() }, imageScript(config)) { service, reading ->
            val load = launch {
                reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/u.jpg")
            }
            started.await()
            assertEquals(1, service.runtime!!.imageCallbackCount(), "a callback must actually be live")
            load.cancelAndJoin()
            assertEquals(0, service.runtime!!.imageCallbackCount(), "live callback count after cancellation")
        }
    }

    @Test
    fun decodeFailureDoesNotLeakAnAlreadyTrackedFunction() = runTest {
        val mock = MockEngine { respond(byteArrayOf(7), HttpStatusCode.OK) }
        val config = "(key) => ({ url: key, method: 7, onResponse: (bytes) => bytes })"
        withReading(HttpClient(mock) { applyReaderDefaults() }, imageScript(config)) { _, reading ->
            assertFailsWith<ReadingFailure.ConfigInvalid> {
                reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/u.jpg")
            }
        }
    }
}
