package dev.reader.source.runtime

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VeneraExampleCompatibilityTest {
    @Test
    fun runsUnmodifiedVeneraNextExample() = runTest {
        val file = File(System.getProperty("reader.venera.example"))
        if (System.getProperty("reader.venera.required").toBoolean()) {
            assertTrue(file.isFile, "Venera-Next example was not found at $file")
        }
        assumeTrue("The local Venera-Next checkout supplies this fixture", file.isFile)
        val script = file.readBytes()
        val actualHash = MessageDigest.getInstance("SHA-256").digest(script).toHexString()
        assertEquals("41ff3cdc08f188080d1d346aba7bbaf9b6d6e762942e792ec897e6408fe8459d", actualHash)

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
        val requests = mutableListOf<String>()
        val runtime = VeneraSourceRuntime.create(VeneraEnvironment("1.16.0", "zh_CN", "macos")) { request ->
            delay(5)
            assertEquals("example_source", request.sourceKey)
            assertEquals("GET", request.method)
            assertEquals(mapOf("Accept" to "application/json"), request.headers)
            assertTrue(request.url.startsWith(api))
            val path = request.url.removePrefix(api)
            requests += path
            VeneraHttpResponse(200, routes.getValue(path))
        }
        try {
            val source = runtime.register(script.decodeToString(), file.name, expectedKey = "example_source")
            assertEquals("Example Source", source.name)
            assertEquals("1.0.0", source.version)
            assertEquals("1.16.0", source.minAppVersion)
            assertEquals("https://example.invalid/sources/example.js", source.url)
            assertEquals(listOf("example_source"), runtime.registeredSources().map { it.key })
            assertEquals(
                setOf("search.load", "comic.loadInfo", "comic.loadEp", "comic.onImageLoad", "comic.onThumbnailLoad", "comic.onClickTag"),
                source.callbacks.map { it.path }.toSet(),
            )

            val search = runtime.call(
                source.key,
                VeneraCallback.Search,
                JsonArray(listOf(JsonPrimitive("星 图"), JsonArray(emptyList()), JsonPrimitive(2))),
            ).jsonObject
            assertEquals(JsonPrimitive(3), search["maxPage"])
            val comic = search.getValue("comics").jsonArray.single().jsonObject
            assertEquals(JsonPrimitive("demo"), comic["id"])
            assertEquals(JsonPrimitive("星图"), comic["title"])
            assertEquals(JsonArray(listOf(JsonPrimitive("冒险"))), comic.getValue("tags").jsonArray)

            val detail = runtime.call(source.key, VeneraCallback.LoadInfo, args("demo")).jsonObject
            assertEquals(JsonPrimitive("作者"), detail["subtitle"])
            assertEquals(JsonPrimitive("2026-09-01"), detail["updateTime"])
            assertEquals(listOf("ep_2", "ep_1"), detail.getValue("chapters").jsonObject.keys.toList())

            val episode = runtime.call(source.key, VeneraCallback.LoadEpisode, args("demo", "ep_2")).jsonObject
            assertEquals(JsonArray(listOf(JsonPrimitive(image))), episode.getValue("images").jsonArray)

            val imageConfig = runtime.call(source.key, VeneraCallback.LoadImage, args(image, "demo", "ep_2")).jsonObject
            assertEquals(JsonPrimitive(image), imageConfig["url"])
            assertEquals(JsonPrimitive("https://example.invalid/"), imageConfig.getValue("headers").jsonObject["Referer"])
            val thumbnail = runtime.call(source.key, VeneraCallback.LoadThumbnail, args(cover)).jsonObject
            assertEquals(JsonPrimitive(cover), thumbnail["url"])
            assertEquals(imageConfig["headers"], thumbnail["headers"])

            val tag = runtime.call(source.key, VeneraCallback.ClickTag, args("genre", "冒险")).jsonObject
            assertEquals(JsonPrimitive("search"), tag["page"])
            assertEquals(JsonPrimitive("冒险"), tag.getValue("attributes").jsonObject["text"])
            assertEquals(routes.keys.toList(), requests)

            println("Venera fixture SHA-256: $actualHash")
            println("${source.key}: ${source.callbacks.joinToString { it.path }} passed")
        } finally {
            runtime.close()
        }
    }

    private fun args(vararg values: String) = JsonArray(values.map(::JsonPrimitive))

    private val api = "https://example.invalid/api"
    private val cover = "https://example.invalid/cover.jpg"
    private val image = "https://example.invalid/images/2.jpg"
}
