package dev.reader.data.extension

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
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ExtensionLifecycleFixtureTest {
    @Test
    fun installsUnmodifiedFixtureAndCallsSearchAndDetail() = runTest {
        val file = File(System.getProperty("reader.venera.example"))
        if (System.getProperty("reader.venera.required").toBoolean()) {
            assertTrue(file.isFile, "Venera-Next example was not found at $file")
        }
        assumeTrue("The local Venera-Next checkout supplies this fixture", file.isFile)
        val script = file.readBytes()
        val actualHash = MessageDigest.getInstance("SHA-256").digest(script).toHexString()
        assertEquals("41ff3cdc08f188080d1d346aba7bbaf9b6d6e762942e792ec897e6408fe8459d", actualHash)

        val api = "https://example.invalid/api"
        val cover = "https://example.invalid/cover.jpg"
        val image = "https://example.invalid/images/2.jpg"
        val routes = linkedMapOf(
            "/search?q=%E6%98%9F%20%E5%9B%BE&page=2" to """
                {"items":[{"id":"demo","title":"星图","author":"作者","cover":"$cover","tags":["冒险"]}],"totalPages":3}
            """.trimIndent(),
            "/comics/demo" to """
                {"title":"星图","author":"作者","cover":"$cover","description":"简介","updatedOn":"2026-09-01",
                 "chapters":[{"id":"2","title":"第二章"},{"id":"1","title":"第一章"}]}
            """.trimIndent(),
            "/comics/demo/chapters/2" to """{"images":["$image"]}""",
        )
        val scriptUrl = "https://example.invalid/sources/example.js"
        val scriptText = script.decodeToString()
        val mock = MockEngine { request ->
            delay(5)
            if (request.method == HttpMethod.Get && request.url.toString() == scriptUrl) {
                respond(scriptText, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/javascript"))
            } else {
                assertTrue(request.url.toString().startsWith(api), "unexpected ${request.url}")
                val path = request.url.encodedPathAndQuery.removePrefix("/api")
                respond(routes.getValue(path), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val scriptsDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-ext-${Uuid.random()}"
        val service = ExtensionService(
            driver = createTestDriver(),
            fileSystem = FileSystem.SYSTEM,
            scriptsDir = scriptsDir,
            httpClient = client,
            environment = VeneraEnvironment("1.16.0", "zh_CN", "macos"),
        )
        try {
            service.open()
            service.restore()
            val installed = service.installFromUrl(scriptUrl, expectedKey = "example_source")
            assertEquals("1.0.0", installed.version)
            val runtime = service.runtime!!
            val search = runtime.call(
                installed.sourceKey,
                VeneraCallback.Search,
                JsonArray(listOf(JsonPrimitive("星 图"), JsonArray(emptyList()), JsonPrimitive(2))),
            ).jsonObject
            assertEquals(JsonPrimitive(3), search["maxPage"])
            assertEquals(JsonPrimitive("demo"), search.getValue("comics").jsonArray.single().jsonObject["id"])
            val detail = runtime.call(installed.sourceKey, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive("demo")))).jsonObject
            assertEquals(JsonPrimitive("作者"), detail["subtitle"])
            println("Venera fixture SHA-256: $actualHash")
            println("${installed.sourceKey}: installed ${installed.version}, search+detail passed (service)")
        } finally {
            service.close()
            client.close()
            FileSystem.SYSTEM.deleteRecursively(scriptsDir)
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
