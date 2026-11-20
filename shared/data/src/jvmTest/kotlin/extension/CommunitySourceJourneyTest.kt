package app.koyura.data.extension

import app.koyura.data.session.ExtensionSession
import app.koyura.data.session.openExtensionSession
import app.koyura.domain.extension.SourceAccountFieldType
import app.koyura.domain.extension.SourceLoginState
import app.koyura.domain.extension.SearchOptionValue.Single
import app.koyura.domain.extension.SearchOptionsSnapshot
import app.koyura.domain.downloads.DownloadChapter
import app.koyura.domain.downloads.DownloadStatus
import app.koyura.network.createDefaultHttpClient
import app.koyura.source.runtime.SourceSecretStore
import app.koyura.source.runtime.SourceCookie
import app.koyura.source.runtime.VeneraCallback
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpSendPipeline
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class CommunitySourceJourneyTest {
    @Test
    fun manhuaguiHtmlFixtureReadsAndRestartsOffline() = runTest {
        val page = png()
        val client = HttpClient(MockEngine { request ->
            when (request.url.host to request.url.encodedPath) {
                "www.manhuagui.com" to "/s/sky_p1.html" -> respond("""
                    <div class="result-count"><strong>sky</strong><strong>1</strong></div>
                    <div class="book-result"><ul><li class="cf"><div class="book-detail"><dl>
                    <dt><a href="/comic/demo/">Sky</a></dt></dl></div></li></ul></div>
                """.trimIndent())
                "www.manhuagui.com" to "/comic/demo/" -> respond("""
                    <div class="book-cont"><div class="book-title"><h1>Sky</h1><h2>Sea</h2></div>
                    <div class="hcover"><img src="//example.test/cover"></div><div id="intro-all"><p>Synopsis</p></div>
                    <div class="detail-list">${"<span>−</span>".repeat(8)}<span>2026-01-02</span></div></div>
                    <div class="chapter"><h4><span>Volume</span></h4>
                    <ul class="chapter-list"><li><a href="/comic/demo/1.html"><span>First</span></a></li></ul></div>
                """.trimIndent())
                "www.manhuagui.com" to "/comic/demo/1.html" -> respond(
                    "<script></script>".repeat(4) +
                    """<script>function fixture(){}('{"0":["1.2","3.2"],"4":"/5/","6":{7:8,9:"a"}}',62,11,'GYSwNgpgzgPgjDAVgBwOYwEw2QQwC4AWMAJhALYD2MUYME8MZ1OZykQA',0,{}))</script>""",
                )
                "us.hamreus.com" to "/demo/1.jpg", "us.hamreus.com" to "/demo/2.jpg" -> {
                    assertEquals("https://www.manhuagui.com/", request.headers[HttpHeaders.Referrer])
                    respond(page, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                }
                else -> error("Unexpected route ${request.url.encodedPath}")
            }
        })
        journey(manhuagui, client, "sky")
    }

    @Test
    fun komgaAccountFixtureAuthenticatesAndRestartsOffline() = runTest {
        val page = png()
        val client = HttpClient(MockEngine { request ->
            assertEquals("Basic Zml4dHVyZTpzYW1wbGU=", request.headers[HttpHeaders.Authorization])
            when (request.url.encodedPath) {
                "/api/v2/users/me" -> respond("""{"id":"fixture"}""")
                "/api/v1/libraries" -> respond("""[{"id":"library","name":"Fixture"}]""")
                "/api/v1/tags/series", "/api/v1/languages", "/api/v1/collections", "/api/v1/genres" -> respond("[]")
                "/api/v1/series", "/api/v1/series/latest", "/api/v1/series/updated" ->
                    respond("""{"content":[{"id":"demo","metadata":{"title":"Sky"}}],"totalPages":1}""")
                "/api/v1/series/demo" -> respond("""{"id":"demo","metadata":{"title":"Sky"}}""")
                "/api/v1/series/demo/books" -> respond("""
                    {"content":[{"id":"first","metadata":{"title":"First"},
                      "media":{"status":"READY","mediaType":"application/zip","pagesCount":2}}]}
                """.trimIndent())
                "/api/v1/books/first/pages" -> respond("""[{"number":1,"mediaType":"image/png"},{"number":2,"mediaType":"image/png"}]""")
                "/api/v1/books/first/pages/1", "/api/v1/books/first/pages/2" ->
                    respond(page, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                else -> error("Unexpected route ${request.url.encodedPath}")
            }
        })
        journey(komga, client, "sky", account = "fixture" to "sample")
    }

    @Test
    fun comicWalkerFixtureUsesComputeForImagesAndRestartsOffline() = runTest {
        val page = png()
        val key = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val transformed = ByteArray(page.size) { index -> (page[index].toInt() xor key[index % key.size].toInt()).toByte() }
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            when (path) {
                "/lookup" -> respond("""{"version":"1.0"}""")
                "/v1/users" -> respond("""{"resources":{"access_token":"fixture"}}""")
                else -> {
                    assertEquals("Bearer fixture", request.headers[HttpHeaders.Authorization])
                    when (path) {
                        "/v1/search/comics" -> respond("""{"resources":[{"id":"demo","title":"Sky"}]}""")
                        "/v2/screens/comics/demo" -> respond("""{"resources":{"detail":{"title":"Sky"},"episode_total_count":1}}""")
                        "/v1/comics/demo/episodes" -> respond("""{"resources":[{"id":"ep","title":"First","plans":[{"type":"free"}]}]}""")
                        "/v1/episodes/ep" -> respond("""{"plans":[{"type":"free"}]}""")
                        "/v1/screens/comics/demo/episodes/ep/viewer" -> respond("""
                            {"resources":{"manuscripts":[{"drm_image_url":"https://walker.example.test/1.png?a=1",
                            "drm_hash":"010102030405060708"},{"drm_image_url":"https://walker.example.test/2.png?a=1",
                            "drm_hash":"010102030405060708"}]}}
                        """.trimIndent())
                        "/1.png", "/2.png" -> {
                            assertEquals(setOf("a"), request.url.parameters.names())
                            respond(transformed, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                        }
                        else -> error("Unexpected ComicWalker fixture path: $path")
                    }
                }
            }
        })
        journey(comicWalker, client, "sky", expectedImage = page)
    }

    @Test
    fun mangaDexFixtureReplaysSearchReadsForumCookiesAndRetriesImages() = runTest {
        val page = png()
        val comic = """{"id":"demo","attributes":{"title":{"en":"Sky"},"altTitles":[],"tags":[],"status":"ongoing"},"relationships":[]}"""
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/manga" -> {
                    assertEquals("safe", request.url.parameters["contentRating[]"])
                    respond("""{"total":1,"data":[$comic]}""")
                }
                "/manga/demo" -> respond("""{"data":$comic}""")
                "/manga/demo/feed" -> respond("""{"total":1,"data":[{"id":"ep","attributes":{"chapter":"1","translatedLanguage":"en"}}]}""")
                "/statistics/manga/demo" -> respond("""{"statistics":{"demo":{"comments":{"threadId":1,"repliesCount":1}}}}""")
                "/threads/1/page-1" -> {
                    assertEquals("sid=forum-fixture", request.headers[HttpHeaders.Cookie])
                    respond("""
                        <article class="message" id="post-1">
                        <div class="message-name">Sam</div><time>2026-11-20</time>
                        <div class="bbWrapper"><script>hidden</script>Hello &amp; welcome<br>Second line</div>
                        </article><div class="pageNav-page"><a>2</a></div>
                    """.trimIndent(), headers = headersOf(HttpHeaders.ContentType, "text/html"))
                }
                "/at-home/server/ep" -> respond("""
                    {"baseUrl":"https://pages.example.test","chapter":{"hash":"hash",
                    "data":["original.png","second.png"],"dataSaver":["first.png","second.png"]}}
                """.trimIndent())
                "/data-saver/hash/first.png", "/data-saver/hash/second.png" ->
                    respond("fixture fallback", HttpStatusCode.ServiceUnavailable)
                "/data/hash/original.png", "/data/hash/second.png" -> {
                    assertEquals("https://mangadex.org/", request.headers[HttpHeaders.Referrer])
                    respond(page, headers = headersOf(HttpHeaders.ContentType, "image/png"))
                }
                else -> error("Unexpected MangaDex fixture path: ${request.url.encodedPath}")
            }
        })
        journey(mangaDex, client, "sky", expectedImage = page, webSession = true) { session, work ->
            val accounts = session.service.accounts
            assertTrue(assertNotNull(session.service.sourceAccountConfig(mangaDex.key)).declaresWebLogin)
            assertTrue(accounts.loginWithWebView(mangaDex.key) { url, check ->
                assertFalse(check(WebLoginPage(url, "Login")))
                val accepted = WebLoginPage("https://forums.mangadex.org/account/", "Account")
                assertTrue(check(accepted))
                WebLoginCapture(accepted, listOf(SourceCookie("sid", "forum-fixture", path = "/")), JsonObject(emptyMap()))
            })
            val result = mangaDexComments(session, work)
            assertEquals(JsonPrimitive(2), result.getValue("maxPage"))
            val comment = result.getValue("comments").jsonArray.single().jsonObject
            assertEquals(JsonPrimitive("Sam"), comment["userName"])
            assertEquals(JsonPrimitive("Hello & welcome\nSecond line"), comment["content"])
        }
    }

    @Test
    fun comicWalkerPublicCatalogue() = runTest(timeout = 180.seconds) {
        assumeTrue("Explicit live regression only", live())
        val client = createDefaultHttpClient()
        try {
            val publicOnly = client.config {
                install("PublicChapterOnly") {
                    sendPipeline.intercept(HttpSendPipeline.State) {
                        check(context.url.encodedPath != "/v1/users/me/rental_episodes") {
                            "Catalogue selection requires a temporary rental, outside this public sample"
                        }
                    }
                }
            }
            journey(comicWalker, publicOnly, "見える子ちゃん", live = true, freeChapterOnly = true)
        } finally {
            client.close()
        }
    }

    @Test
    fun mangaDexPublicCatalogueAndForum() = runTest(timeout = 180.seconds) {
        assumeTrue("Explicit live regression only", live())
        journey(mangaDex, createDefaultHttpClient(), "Bocchi the Rock!", live = true) { session, work ->
            assertTrue(assertNotNull(session.service.sourceAccountConfig(mangaDex.key)).declaresWebLogin)
            try {
                val comments = mangaDexComments(session, work).getValue("comments").jsonArray
                println("manga_dex: public comment callback returned ${comments.size} comments")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                println("manga_dex: public comment callback blocked: ${failure.message?.lineSequence()?.firstOrNull()}")
            }
        }
    }

    private suspend fun mangaDexComments(session: ExtensionSession, work: String): JsonObject =
        session.service.call(mangaDex.key, VeneraCallback.Comments,
            JsonArray(listOf(JsonPrimitive(work), JsonNull, JsonPrimitive(1), JsonNull))).jsonObject

    @Test
    fun manhuaguiPublicWebsite() = runTest(timeout = 180.seconds) {
        assumeTrue("Explicit live regression only", live())
        journey(manhuagui, createDefaultHttpClient(), "航海王", live = true)
    }

    @Test
    fun komgaPublicDemo() = runTest(timeout = 180.seconds) {
        assumeTrue("Explicit live regression only", live())
        // The Komga project publishes these demo credentials in its introduction.
        journey(komga, createDefaultHttpClient(), "Space Adventures", account = "demo@komga.org" to "komga-demo", live = true)
    }

    private suspend fun journey(
        source: CommunityScript,
        client: HttpClient,
        keyword: String,
        account: Pair<String, String>? = null,
        live: Boolean = false,
        expectedImage: ByteArray? = null,
        webSession: Boolean = false,
        freeChapterOnly: Boolean = false,
        afterDetail: suspend (ExtensionSession, String) -> Unit = { _, _ -> },
    ) {
        val path = System.getProperty("reader.venera.community.e2e")
        try {
            assumeTrue("Community fixture directory is required", path != null)
            val script = File(path, source.filename).readBytes()
            assertEquals(source.sha256, MessageDigest.getInstance("SHA-256").digest(script).toHexString())
            val files = FileSystem.SYSTEM
            val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "community-journey-${Uuid.random()}"
            val secrets = MutableSecrets()
            try {
                val first = open(root, client, secrets, online = true)
                val progress = try {
                    first.service.installFromContent(source.filename, script.decodeToString(), source.key)
                    println("${source.key}: installed, SHA-256 ${source.sha256}")
                    if (account != null) {
                        val form = assertNotNull(first.service.sourceAccountConfig(source.key))
                        assertEquals(listOf(SourceAccountFieldType.TEXT, SourceAccountFieldType.PASSWORD), form.fields.map { it.type })
                        assertTrue(first.service.accounts.loginForm(source.key, listOf(account.first, account.second)))
                        assertEquals(SourceLoginState.FORM, first.service.sourceLoginState(source.key))
                        assertTrue(first.service.runtime!!.describe(source.key, app.koyura.source.runtime.SourceConfigField.AccountLoggedIn)
                            == JsonPrimitive(true))
                        println("${source.key}: account login passed")
                        val explore = first.service.browse.explorePages(source.key).first()
                        assertTrue(first.service.browse.explore(source.key, explore).jsonArray.isNotEmpty())
                        println("${source.key}: explore passed")
                    }
                    val browse = first.service.browse
                    if (source == mangaDex) {
                        browse.setSearchOptions(source.key, SearchOptionsSnapshot(listOf(Single("any"), Single("safe"), Single("any"))))
                    }
                    val saved = browse.saveSearch(source.key, "Journey", keyword)
                    val search = browse.searchSaved(saved)
                    val items = search.jsonObject.getValue("comics").jsonArray
                    assertTrue(items.isNotEmpty())
                    println("${source.key}: search passed (${items.size} results)")
                    val workId = items.first().jsonObject.getValue("id").jsonPrimitive.content
                    val detail = first.service.call(source.key, VeneraCallback.LoadInfo,
                        JsonArray(listOf(JsonPrimitive(workId)))).jsonObject
                    assertTrue(detail.getValue("title").jsonPrimitive.content.isNotBlank())
                    val chapters = (detail["chapters"] as? JsonObject)?.entries.orEmpty().flatMap { (id, children) ->
                        (children as? JsonObject)?.entries?.map { it.key to it.value } ?: listOf(id to children)
                    }
                    println("${source.key}: detail passed (${chapters.size} chapters)")
                    afterDetail(first, workId)
                    val chapterId = if (freeChapterOnly) {
                        val candidates = chapters.filterNot { (_, title) -> title.jsonPrimitive.content.startsWith("❌") }
                        check(candidates.isNotEmpty()) { "Catalogue offers no publicly readable chapters" }
                        candidates.first().first
                    } else chapters.firstOrNull()?.first
                    val opened = first.comicReading.openChapter(source.key, workId, chapterId)
                    assertTrue(opened.pages.isNotEmpty())
                    println("${source.key}: chapter passed (${opened.pages.size} pages)")
                    val resume = (opened.pages.lastIndex).coerceAtMost(1)
                    val bytes = first.comicReading.loadImageBytes(source.key, workId, chapterId, opened.pages[resume].imageKey)
                    if (expectedImage != null) assertContentEquals(expectedImage, bytes)
                    val decoded = ImageIO.read(bytes.inputStream())
                    val preview = bytes.take(12).toByteArray().decodeToString()
                    if (live && preview.startsWith("RIFF") && preview.endsWith("WEBP")) {
                        println("${source.key}: online WebP image received (${bytes.size} bytes)")
                    } else {
                        assertNotNull(decoded, "Unsupported image response (prefix ${bytes.take(12).toByteArray().toHexString()})")
                        println("${source.key}: online image decoded")
                    }
                    val target = DownloadChapter(source.key, workId, chapterId)
                    val queued = first.downloads.enqueue(target)
                    val completed = withContext(Dispatchers.Default) {
                        withTimeout(if (live) 120_000 else 10_000) {
                            first.downloads.observe(target).filterNotNull().first {
                                it.status in setOf(DownloadStatus.COMPLETE, DownloadStatus.FAILED)
                            }
                        }
                    }
                    assertEquals(DownloadStatus.COMPLETE, completed.status)
                    assertEquals(queued.id, completed.id)
                    assertEquals(opened.pages.size, completed.downloadedPages)
                    first.comicReading.saveProgress(source.key, workId, chapterId, opened.pages[resume].pageKey, resume)
                    println("${source.key}: download committed (${completed.downloadedPages} pages)")
                    Snapshot(workId, chapterId, resume, bytes)
                } finally {
                    first.close()
                }
                val offline = HttpClient(MockEngine { error("Offline reading attempted a request") })
                val reopened = open(root, offline, secrets, online = false)
                try {
                    if (account != null) assertEquals(SourceLoginState.FORM, reopened.service.sourceLoginState(source.key))
                    if (webSession) assertEquals(SourceLoginState.WEB, reopened.service.sourceLoginState(source.key))
                    val opened = reopened.comicReading.openChapter(source.key, progress.workId, progress.chapterId)
                    assertEquals(progress.index, opened.startIndex)
                    assertContentEquals(progress.image, reopened.comicReading.loadImageBytes(
                        source.key, progress.workId, progress.chapterId, opened.pages[progress.index].imageKey,
                    ))
                    println("${source.key}: offline restart and saved position passed")
                } finally {
                    reopened.close()
                }
            } finally {
                files.deleteRecursively(root, mustExist = false)
            }
        } finally {
            client.close()
        }
    }

    private suspend fun open(root: Path, client: HttpClient, secrets: SourceSecretStore, online: Boolean): ExtensionSession =
        openExtensionSession(createFileBackedTestDriver(root, "fixture"), FileSystem.SYSTEM, root / "app",
            client, VeneraEnvironment("1.16.0", "zh_CN", "macos"), secrets, isOnline = { online })

    private fun live() = System.getProperty("reader.venera.community.live").toBoolean()

    private fun png(): ByteArray = ByteArrayOutputStream().use {
        check(ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", it))
        it.toByteArray()
    }

    private class MutableSecrets : SourceSecretStore {
        private val values = java.util.concurrent.ConcurrentHashMap<String, String>()
        override fun read(sourceKey: String) = values[sourceKey]
        override fun write(sourceKey: String, value: String?) {
            if (value == null) values.remove(sourceKey) else values[sourceKey] = value
        }
    }

    private data class Snapshot(val workId: String, val chapterId: String?, val index: Int, val image: ByteArray)
    private data class CommunityScript(val filename: String, val key: String, val sha256: String)

    private val comicWalker = CommunityScript("comic_walker.js", "comic_walker", "d039289f0aacda36990f090f93779bf54116e4d0e20c626c7ac29c73ec1e3513")
    private val mangaDex = CommunityScript("manga_dex.js", "manga_dex", "c8beb84262da8fa3922ff0392fc2f79f03ee36ae08933d10571172ef6c8ce21d")
    private val manhuagui = CommunityScript("manhuagui.js", "ManHuaGui", "a5bf6c633e0ce728361dded097424b829134d618128f72b571e42525e8950128")
    private val komga = CommunityScript("komga.js", "komga", "2aa5b4e7d4b32bf282f86168b22af091d7436f4e3ad603defb7b6df738a36908")
}
