package app.koyura.source.runtime

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assume.assumeNotNull
import kotlin.test.Test
import kotlin.test.assertEquals

class CommunityHtmlCompatibilityTest {
    @Test
    fun runsUnmodifiedBaoziSearchDetailsAndChapter() = runTest {
        verify(
            key = "baozi",
            hash = "6ccb52e7734e54c6dfcfcb9729adb46409f1e5c9d83bbda54313b066013e4255",
            routes = mapOf(
                "https://cn.bzmgcn.com/search?q=sky" to """
                    <div class="comics-card"><a href="/comic/demo"><amp-img src="/cover.jpg"></amp-img></a>
                    <h3>Sky &amp; Sea</h3><div class="tabs"><span>Adventure</span></div><small>Synopsis</small></div>
                """.trimIndent(),
                "https://cn.bzmgcn.com/comic/demo" to """
                    <div class="l-content"><div><div><amp-img src="/cover.jpg"></amp-img></div></div></div>
                    <h1 class="comics-detail__title">Sky &amp; Sea</h1><h2 class="comics-detail__author">Author</h2>
                    <p class="comics-detail__desc">Synopsis</p><div class="tag-list"><span>Adventure</span></div>
                    <div id="chapter-items"><div class="comics-chapters"><a><div><span>Chapter One</span></div></a></div></div>
                """.trimIndent(),
                "https://appcn.baozimh.com/baozimhapp/comic/chapter/demo/0_0.html" to """
                    <div class="comic-contain"><div class="chapter-img"><img class="comic-contain__item"
                    data-src="https://images.example/acomic/page.jpg"></div></div>
                """.trimIndent(),
            ),
            episode = "0",
            expectedImage = "https://images.example/w640/acomic/page.jpg",
        )
    }

    @Test
    fun runsUnmodifiedMxsSearchDetailsAndChapter() = runTest {
        val base = "https://www.mxshm.top"
        verify(
            key = "mxs",
            hash = "da2c222eda825b77eb3b67ac09ec33b70bc2512cd5fc867fbe9b958df55037f8",
            routes = mapOf(
                "$base/search?keyword=sky" to """
                    <div class="mh-item"><div class="title"><a href="/book/demo">Sky &amp; Sea</a></div>
                    <span><a>Author</a></span><div class="chapter">更新 Chapter One</div></div>
                """.trimIndent(),
                "$base/book/demo" to """
                    <div class="info"><h1>Sky &amp; Sea</h1><div class="subtitle">作者：Author</div>
                    <div class="tip"><a href="/search?tag=Adventure">Adventure</a></div>
                    <p class="content">Synopsis</p></div>
                    <ul id="detail-list-select"><li><a href="/chapter/first">Chapter One</a></li></ul>
                """.trimIndent(),
                "$base/chapter/first" to """<img class="lazy" data-original="https://images.example/page.jpg">""",
            ),
            episode = "first",
            expectedImage = "$base/page.jpg",
        )
    }

    private suspend fun verify(
        key: String,
        hash: String,
        routes: Map<String, String>,
        episode: String,
        expectedImage: String,
    ) {
        val fixtureDir = System.getProperty("reader.venera.community")
        assumeNotNull(fixtureDir)
        val script = File(fixtureDir, "$key.js").readBytes()
        assertEquals(hash, MessageDigest.getInstance("SHA-256").digest(script).toHexString())
        val requests = mutableListOf<String>()
        val runtime = VeneraSourceRuntime.create(VeneraEnvironment("1.16.0", "zh_CN", "macos")) { request ->
            assertEquals(key, request.sourceKey)
            assertEquals("GET", request.method)
            requests += request.url
            VeneraHttpResponse(200, routes.getValue(request.url))
        }
        try {
            val source = runtime.register(script.decodeToString(), "$key.js", expectedKey = key)
            val search = runtime.call(source.key, VeneraCallback.Search, JsonArray(listOf(
                JsonPrimitive("sky"), JsonArray(emptyList()), JsonPrimitive(1),
            ))).jsonObject
            val comic = search.getValue("comics").jsonArray.single().jsonObject
            assertEquals(JsonPrimitive("demo"), comic["id"])
            assertEquals(JsonPrimitive("Sky & Sea"), comic["title"])
            assertEquals(JsonPrimitive(1), search["maxPage"])
            val detail = runtime.call(source.key, VeneraCallback.LoadInfo, args("demo")).jsonObject
            assertEquals(JsonPrimitive("Sky & Sea"), detail["title"])
            assertEquals(JsonPrimitive("Synopsis"), detail["description"])
            assertEquals(JsonPrimitive("Chapter One"), detail.getValue("chapters").jsonObject[episode])
            val pages = runtime.call(source.key, VeneraCallback.LoadEpisode, args("demo", episode)).jsonObject
            assertEquals(args(expectedImage), pages["images"])
            assertEquals(routes.keys.toList(), requests)
            println("$key.js SHA-256 $hash: search.load, comic.loadInfo, comic.loadEp passed (HTML fixtures)")
        } finally {
            runtime.close()
        }
    }

    private fun args(vararg values: String) = JsonArray(values.map(::JsonPrimitive))
}
