package dev.reader.data.reading

import dev.reader.data.extension.ExtensionDb
import dev.reader.data.extension.ExtensionService
import dev.reader.data.extension.createFileBackedTestDriver
import dev.reader.domain.extension.OpenedChapter
import dev.reader.network.applyReaderDefaults
import dev.reader.source.runtime.VeneraCallback
import dev.reader.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ComicReadingFixtureTest {
    private val environment = VeneraEnvironment("1.16.0", "zh_CN", "macos")

    @Test
    fun unmodifiedScriptReadsChapterImagesAndResumesAfterReopen() = runTest {
        val file = File(System.getProperty("reader.venera.example"))
        if (System.getProperty("reader.venera.required").toBoolean()) {
            assertTrue(file.isFile, "Venera-Next example was not found at $file")
        }
        assumeTrue("The local Venera-Next checkout supplies this fixture", file.isFile)
        val script = file.readBytes()
        val actualHash = MessageDigest.getInstance("SHA-256").digest(script).toHexString()
        assertEquals("41ff3cdc08f188080d1d346aba7bbaf9b6d6e762942e792ec897e6408fe8459d", actualHash)

        val api = "https://example.invalid/api"
        val img1 = "https://example.invalid/images/2-1.jpg"
        val img2 = "https://example.invalid/images/2-2.jpg"
        val img3 = "https://example.invalid/images/2-3.jpg"
        val seenReferers = mutableListOf<String?>()
        val scriptUrl = "https://example.invalid/sources/example.js"
        val mock = MockEngine { request ->
            if (request.method == HttpMethod.Get && request.url.toString() == scriptUrl) {
                respond(script.decodeToString(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/javascript"))
            } else if (request.url.toString().startsWith(api)) {
                val path = request.url.encodedPathAndQuery.removePrefix("/api")
                when {
                    path.startsWith("/comics/demo/chapters/") ->
                        respond("""{"images":["$img1","$img2","$img3"]}""", HttpStatusCode.OK)
                    else -> respond("not found", HttpStatusCode.NotFound)
                }
            } else {
                seenReferers += request.headers["Referer"]
                val bytes = when (request.url.toString()) {
                    img1 -> byteArrayOf(1)
                    img2 -> byteArrayOf(2, 3)
                    img3 -> byteArrayOf(4, 5, 6)
                    else -> byteArrayOf(0)
                }
                respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
            }
        }
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-reading-${Uuid.random()}"
        val dbTag = "reading-${Uuid.random()}"
        val client = HttpClient(mock) { applyReaderDefaults() }
        val driver = createFileBackedTestDriver(root, dbTag)
        dev.reader.data.extension.ExtensionDb.Schema.create(driver)
        val service = ExtensionService(driver, FileSystem.SYSTEM, root / "scripts", client, environment)
        val reading = ComicReadingService(driver, service, client)
        val installed = try {
            service.open()
            service.restore()
            service.installFromUrl(scriptUrl, expectedKey = "example_source")
        } catch (e: Throwable) {
            service.close()
            throw e
        }
        try {
            val opened = reading.openChapter(installed.sourceKey, "demo", "ep_2")
            assertEquals(listOf(img1, img2, img3), opened.pages.map { it.imageKey })
            assertEquals(3, opened.pages.size)
            assertEquals(0, opened.startIndex)
            val first = reading.loadPageImage(installed.sourceKey, "demo", "ep_2", img2)
            assertTrue(first.contentEquals(byteArrayOf(2, 3)))
            assertTrue(seenReferers.isNotEmpty())
            assertTrue(seenReferers.all { it == "https://example.invalid/" }, "Referer: $seenReferers")
            reading.saveProgress(installed.sourceKey, "demo", "ep_2", img3, 2)
            service.close()
            driver.close()
        } catch (e: Throwable) {
            try {
                service.close()
            } catch (_: Exception) {
            }
            runCatching { driver.close() }
            throw e
        }
        // Reopen from the same path: the third page must still be selected.
        val client2 = HttpClient(mock) { applyReaderDefaults() }
        val driver2 = createFileBackedTestDriver(root, dbTag)
        val service2 = ExtensionService(driver2, FileSystem.SYSTEM, root / "scripts", client2, environment)
        val reading2 = ComicReadingService(driver2, service2, client2)
        try {
            service2.open()
            val report = service2.restore()
            assertEquals(1, report.registered)
            val reopened: OpenedChapter = reading2.openChapter(installed.sourceKey, "demo", "ep_2")
            assertEquals(2, reopened.startIndex)
            assertEquals(img3, reopened.startPageKey)
            val bytes = reading2.loadPageImage(installed.sourceKey, "demo", "ep_2", img3)
            assertTrue(bytes.contentEquals(byteArrayOf(4, 5, 6)))
            println("Venera fixture SHA-256: $actualHash")
            println("${installed.sourceKey}: chapter images, Referer, bytes, order and reopen resume passed")
        } finally {
            service2.close()
            driver2.close()
            client.close()
            client2.close()
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
