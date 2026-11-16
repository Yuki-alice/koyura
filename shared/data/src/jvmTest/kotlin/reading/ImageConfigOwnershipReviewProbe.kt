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

class ImageConfigOwnershipReviewProbe {
    @Test
    fun decodeFailureOfInitialConfigReleasesItsHandle() = runTest {
        val mock = MockEngine { error("Invalid image config must not start HTTP") }
        val config = """(key) => ({ url: key, headers: { "X-Example": 7 }, onResponse: (bytes) => bytes })"""
        withReading(HttpClient(mock) { applyReaderDefaults() }, imageScript(config)) { _, reading ->
            assertFailsWith<ReadingFailure.ConfigInvalid> {
                reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/u.jpg")
            }
        }
    }

    @Test
    fun decodeFailureOfRetryConfigReleasesBothConfigs() = runTest {
        var attempts = 0
        val mock = MockEngine { attempts++; respond("forbidden", HttpStatusCode.Forbidden) }
        val config = """(key) => ({
            url: key,
            onLoadFailed: () => ({ url: key, headers: { "X-Example": 7 }, onResponse: (bytes) => bytes }),
        })"""
        withReading(HttpClient(mock) { applyReaderDefaults() }, imageScript(config)) { _, reading ->
            assertFailsWith<ReadingFailure.ConfigInvalid> {
                reading.loadImageBytes(IMG_KEY, "w", "ep1", "https://example.invalid/u.jpg")
            }
            assertEquals(1, attempts)
        }
    }
}
