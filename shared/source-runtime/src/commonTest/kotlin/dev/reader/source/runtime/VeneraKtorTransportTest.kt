package dev.reader.source.runtime

import dev.reader.network.applyReaderDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VeneraKtorTransportTest {
    @Test
    fun postForwardsHeadersAndTextBodyWithoutHostFields() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedPath: String? = null
        var capturedBody: String? = null
        val seenHeaders = mutableMapOf<String, String?>()
        val mock = MockEngine { request ->
            capturedMethod = request.method
            capturedPath = request.url.encodedPathAndQuery
            // Content-Type travels on the text body; other headers stay in headers.
            seenHeaders["Content-Type"] =
                request.headers["Content-Type"] ?: request.body.contentType?.toString()
            seenHeaders["X-Custom"] = request.headers["X-Custom"]
            seenHeaders["cache-time"] = request.headers["cache-time"]
            seenHeaders["prevent-parallel"] = request.headers["prevent-parallel"]
            seenHeaders["http_client"] = request.headers["http_client"]
            seenHeaders["Cache-Time-upper"] = request.headers["Cache-Time"]
            val content = request.body
            capturedBody = when (content) {
                is TextContent -> content.text
                is ByteArrayContent -> content.bytes().decodeToString()
                else -> null
            }
            respond(
                """{"ok":true}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val transport = KtorVeneraHttpTransport(client)
        try {
            val response = transport.execute(
                VeneraHttpRequest(
                    sourceKey = "post_source",
                    method = "POST",
                    url = "https://example.invalid/echo?foo=bar&n=1",
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "X-Custom" to "value",
                        "cache-time" to "no",
                        "prevent-parallel" to "true",
                        "http_client" to "dart:io",
                        "Cache-Time" to "long",
                    ),
                    data = JsonPrimitive("""{"hello":"world"}"""),
                    extra = JsonObject(emptyMap()),
                ),
            )
            assertEquals(HttpMethod.Post, capturedMethod)
            assertEquals("/echo?foo=bar&n=1", capturedPath)
            assertEquals("application/json", seenHeaders["Content-Type"])
            assertEquals("value", seenHeaders["X-Custom"])
            assertNull(seenHeaders["cache-time"], "cache-time must not reach the site")
            assertNull(seenHeaders["prevent-parallel"], "prevent-parallel must not reach the site")
            assertNull(seenHeaders["http_client"], "http_client must not reach the site")
            assertNull(seenHeaders["Cache-Time-upper"], "host fields are case-insensitive")
            // Script already did JSON.stringify: send raw text, not quoted JSON string.
            assertEquals("""{"hello":"world"}""", capturedBody)
            assertEquals(200, response.status)
            assertEquals("""{"ok":true}""", response.body)
        } finally {
            client.close()
        }
    }

    @Test
    fun sendsUpstreamBrowserUserAgentWhenOmitted() = runTest {
        var capturedAgent: String? = null
        var capturedCount = 0
        val mock = MockEngine { request ->
            capturedAgent = request.headers[HttpHeaders.UserAgent]
            capturedCount = request.headers.getAll(HttpHeaders.UserAgent)?.size ?: 0
            respond("ok", HttpStatusCode.OK)
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val transport = KtorVeneraHttpTransport(client)
        try {
            transport.execute(
                VeneraHttpRequest(sourceKey = null, method = "GET", url = "https://example.invalid/ua"),
            )
            assertEquals(KtorVeneraHttpTransport.DEFAULT_BROWSER_USER_AGENT, capturedAgent)
            assertEquals(1, capturedCount)
            assertEquals(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
                capturedAgent,
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun respectsExplicitUserAgentInAnyCasing() = runTest {
        val variants = listOf("user-agent", "User-Agent", "USER-AGENT", "uSeR-aGeNt")
        for (headerName in variants) {
            var capturedAgent: String? = null
            var capturedCount = 0
            val mock = MockEngine { request ->
                capturedAgent = request.headers[HttpHeaders.UserAgent]
                capturedCount = request.headers.getAll(HttpHeaders.UserAgent)?.size ?: 0
                respond("ok", HttpStatusCode.OK)
            }
            val client = HttpClient(mock) { applyReaderDefaults() }
            val transport = KtorVeneraHttpTransport(client)
            try {
                transport.execute(
                    VeneraHttpRequest(
                        sourceKey = null,
                        method = "GET",
                        url = "https://example.invalid/ua",
                        headers = mapOf(headerName to "Custom/$headerName"),
                    ),
                )
                assertEquals("Custom/$headerName", capturedAgent, "header casing: $headerName")
                assertEquals(1, capturedCount, "server must see a single UA for $headerName")
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun injectedExpectSuccessTrueStillReturns404And500() = runTest {
        val mock = MockEngine { request ->
            when (request.url.encodedPath) {
                "/missing" -> respond("not here", HttpStatusCode.NotFound)
                "/broken" -> respond("boom", HttpStatusCode.InternalServerError)
                else -> respond("ok", HttpStatusCode.OK)
            }
        }
        // Even with expectSuccess = true, Venera must see status + body.
        val client = HttpClient(mock) { expectSuccess = true }
        val transport = KtorVeneraHttpTransport(client)
        try {
            val notFound = transport.execute(
                VeneraHttpRequest(sourceKey = null, method = "GET", url = "https://example.invalid/missing"),
            )
            assertEquals(404, notFound.status)
            assertEquals("not here", notFound.body)

            val broken = transport.execute(
                VeneraHttpRequest(sourceKey = null, method = "GET", url = "https://example.invalid/broken"),
            )
            assertEquals(500, broken.status)
            assertEquals("boom", broken.body)
        } finally {
            client.close()
        }
    }

    @Test
    fun joinsDuplicateResponseHeadersWithComma() = runTest {
        val mock = MockEngine {
            respond(
                "ok",
                HttpStatusCode.OK,
                Headers.build {
                    append("X-Multi", "a")
                    append("X-Multi", "b")
                    append("Content-Type", "text/plain")
                },
            )
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val transport = KtorVeneraHttpTransport(client)
        try {
            val response = transport.execute(
                VeneraHttpRequest(
                    sourceKey = null,
                    method = "GET",
                    url = "https://example.invalid/multi",
                ),
            )
            assertEquals(200, response.status)
            assertEquals("ok", response.body)
            // Upstream joins with ',' (no space).
            assertEquals("a,b", response.headers["X-Multi"])
        } finally {
            client.close()
        }
    }

    @Test
    fun returns404And500WithoutThrowing() = runTest {
        val mock = MockEngine { request ->
            when (request.url.encodedPath) {
                "/missing" -> respond("not here", HttpStatusCode.NotFound)
                "/broken" -> respond("boom", HttpStatusCode.InternalServerError)
                else -> respond("ok", HttpStatusCode.OK)
            }
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val transport = KtorVeneraHttpTransport(client)
        try {
            val notFound = transport.execute(
                VeneraHttpRequest(sourceKey = null, method = "GET", url = "https://example.invalid/missing"),
            )
            assertEquals(404, notFound.status)
            assertEquals("not here", notFound.body)

            val broken = transport.execute(
                VeneraHttpRequest(sourceKey = null, method = "GET", url = "https://example.invalid/broken"),
            )
            assertEquals(500, broken.status)
            assertEquals("boom", broken.body)
        } finally {
            client.close()
        }
    }

    @Test
    fun scriptSees404BodyViaFullChain() = runTest {
        val mock = MockEngine {
            respond("not here", HttpStatusCode.NotFound)
        }
        withKtorRuntime(mock) { runtime ->
            val source = runtime.register(postEchoSource, expectedKey = "post_source")
            val result = runtime.call(
                source.key,
                VeneraCallback.LoadInfo,
                JsonArray(listOf(JsonPrimitive("404"))),
            ).jsonObject
            assertEquals(JsonPrimitive(404), result["status"])
            assertEquals(JsonPrimitive("not here"), result["body"])
        }
    }

    @Test
    fun throwsOnTransportFailure() = runTest {
        val mock = MockEngine {
            throw IllegalStateException("connection failed")
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        val transport = KtorVeneraHttpTransport(client)
        try {
            assertFailsWith<IllegalStateException> {
                transport.execute(
                    VeneraHttpRequest(sourceKey = null, method = "GET", url = "https://example.invalid/down"),
                )
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun scriptErrorOnTransportFailureAndNextCallSucceeds() = runTest {
        var fail = true
        val mock = MockEngine {
            if (fail) throw IllegalStateException("connection failed")
            respond("""{"ok":true}""", HttpStatusCode.OK)
        }
        withKtorRuntime(mock) { runtime ->
            val source = runtime.register(postEchoSource, expectedKey = "post_source")
            val failure = assertFailsWith<VeneraScriptException> {
                runtime.call(source.key, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive("x"))))
            }
            assertContains(failure.message.orEmpty(), "connection failed")
            fail = false
            val result = runtime.call(
                source.key,
                VeneraCallback.LoadInfo,
                JsonArray(listOf(JsonPrimitive("x"))),
            ).jsonObject
            assertEquals(JsonPrimitive(200), result["status"])
        }
    }

    @Test
    fun cancellationPropagatesAndNextCallSucceeds() = runTest {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val mock = MockEngine { request ->
            if (request.url.encodedPath == "/wait") {
                try {
                    started.complete(Unit)
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
            respond("ready", HttpStatusCode.OK)
        }
        withKtorRuntime(mock) { runtime ->
            val source = runtime.register(
                """
                class WaitSource extends ComicSource {
                    name = "Wait";
                    key = "wait_source";
                    version = "1.0.0";
                    minAppVersion = "1.16.0";
                    comic = {
                        loadInfo(id) {
                            if (id === "wait") return Network.get("https://example.invalid/wait").then(r => ({ title: r.body }));
                            return { title: "ok:" + id };
                        },
                        onClickTag() { return null; },
                    };
                }
                """.trimIndent(),
                expectedKey = "wait_source",
            )
            val call = launch {
                runtime.call(source.key, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive("wait"))))
            }
            started.await()
            call.cancelAndJoin()
            cancelled.await()
            assertTrue(runtime.registeredSources().isNotEmpty())
            val again = runtime.call(
                source.key,
                VeneraCallback.LoadInfo,
                JsonArray(listOf(JsonPrimitive("ok"))),
            ).jsonObject
            assertEquals(JsonPrimitive("ok:ok"), again["title"])
        }
    }

    private suspend fun withKtorRuntime(
        mock: MockEngine,
        block: suspend (VeneraSourceRuntime) -> Unit,
    ) {
        val client = HttpClient(mock) { applyReaderDefaults() }
        val transport = KtorVeneraHttpTransport(client, ownsClient = true)
        val runtime = VeneraSourceRuntime.create(VeneraEnvironment("1.16.0", "zh_CN", "test"), transport)
        try {
            block(runtime)
        } finally {
            runtime.close()
            transport.close()
        }
    }

    private val postEchoSource = """
        class PostSource extends ComicSource {
            name = "Post";
            key = "post_source";
            version = "1.0.0";
            minAppVersion = "1.16.0";
            comic = {
                loadInfo(id) {
                    const path = id === "404" ? "https://example.invalid/missing" : "https://example.invalid/echo";
                    return Network.post(
                        path,
                        { "Content-Type": "application/json", "X-Custom": "v", "cache-time": "no" },
                        JSON.stringify({ id: id }),
                        null
                    ).then(r => ({ status: r.status, body: r.body }));
                },
                onClickTag() { return null; },
            };
        }
    """.trimIndent()
}
