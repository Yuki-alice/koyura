package app.koyura.source.runtime

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class SourceRequestPolicyTest {
    @Test
    fun isolatesResponseCacheBySourceSessionHeadersAndRepresentation() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val client = HttpClient(MockEngine { request ->
            seen += request
            respond(seen.size.toString(), headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=utf-8"))
        })
        val sessions = SourceSessions()
        val transport = KtorVeneraHttpTransport(client, sessions = sessions)
        val request = VeneraHttpRequest("alpha", "GET", "https://example.test/chapter?a=1")
        try {
            assertEquals("1", transport.execute(request).body)
            val cached = transport.execute(request)
            assertEquals("1", cached.body)
            assertEquals("true", cached.headers["venera-cache"])
            assertEquals("2", transport.execute(request.copy(sourceKey = "beta")).body)
            assertEquals("3", transport.execute(request.copy(url = "https://example.test/chapter?a=2")).body)
            val authorized = request.copy(headers = mapOf("Authorization" to "private"))
            assertEquals("4", transport.execute(authorized).body)
            assertEquals("4", transport.execute(authorized.copy(headers = mapOf("authorization" to "private"))).body)
            val bytes = transport.executeBytes(request)
            assertContentEquals("5".encodeToByteArray(), bytes.body)
            bytes.body[0] = 0
            assertContentEquals("5".encodeToByteArray(), transport.executeBytes(request).body)
            assertContentEquals("6".encodeToByteArray(), transport.executeImage(request).body)

            sessions.setCookies("alpha", request.url, listOf(SourceCookie("sid", "first")))
            assertEquals("7", transport.execute(request).body)
            assertEquals("sid=first", seen.last().headers[HttpHeaders.Cookie])
            sessions.clear("alpha")
            assertEquals("8", transport.execute(request).body)
            assertNull(seen.last().headers[HttpHeaders.Cookie])
            assertEquals("9", transport.execute(request.copy(headers = mapOf("Cache-Time" to "no"))).body)
            assertNull(seen.last().headers["cache-time"])
            assertEquals("10", transport.execute(request).body)
        } finally {
            transport.close()
            sessions.close()
            client.close()
        }
    }

    @Test
    fun responseMutationHeaderErrorsAndPayloadBypassShortCache() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls++
            val headers = when (request.url.encodedPath) {
                "/cookie" -> headersOf(HttpHeaders.SetCookie, "sid=$calls; Path=/")
                "/no-store" -> headersOf(HttpHeaders.CacheControl, "no-store")
                else -> headersOf()
            }
            val body = if (request.url.encodedPath == "/large") "a".repeat(262_145) else "ok"
            val status = if (request.url.encodedPath == "/error") HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK
            respond(body, status = status, headers = headers)
        })
        val sessions = SourceSessions()
        val transport = KtorVeneraHttpTransport(client, sessions = sessions)
        try {
            val request = VeneraHttpRequest("alpha", "GET", "https://example.test")
            for (path in listOf("/cookie", "/no-store", "/large", "/error")) {
                repeat(2) { transport.execute(request.copy(url = request.url + path)) }
            }
            repeat(2) { transport.execute(request.copy(method = "POST", data = JsonPrimitive("form"))) }
            repeat(2) { transport.execute(request.copy(data = JsonPrimitive("get payload"))) }
            repeat(2) { transport.execute(request.copy(headers = mapOf(HttpHeaders.Range to "bytes=0-1"))) }
            assertEquals(14, calls)
            assertEquals("2", sessions.cookies("alpha", request.url).single().value)
        } finally {
            transport.close()
            sessions.close()
            client.close()
        }
    }

    @Test
    fun queuedRequestCannotReadOrChangeANewerAccountSession() = runTest {
        val cookies = Channel<String?>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            cookies.send(request.headers[HttpHeaders.Cookie])
            release.await()
            respond("ok", headers = headersOf(HttpHeaders.SetCookie, "seen=old; Path=/"))
        })
        val sessions = SourceSessions()
        val transport = KtorVeneraHttpTransport(client, sessions = sessions)
        val request = VeneraHttpRequest("alpha", "GET", "https://example.test",
            headers = mapOf("prevent-parallel" to "true"))
        sessions.setCookies("alpha", request.url, listOf(SourceCookie("sid", "old")))
        try {
            val first = async { transport.execute(request) }
            assertEquals("sid=old", cookies.receive())
            val queued = async(start = CoroutineStart.UNDISPATCHED) { transport.execute(request) }
            sessions.clear("alpha")
            sessions.setCookies("alpha", request.url, listOf(SourceCookie("sid", "new")))
            release.complete(Unit)
            assertNull(cookies.receive())
            first.await()
            queued.await()
            assertEquals(listOf(SourceCookie("sid", "new", path = "/")), sessions.cookies("alpha", request.url))
        } finally {
            transport.close()
            sessions.close()
            client.close()
        }
    }

    @Test
    fun ttlAndBoundsUseMonotonicTime() {
        val clock = TestTimeSource()
        val policy = SourceRequestPolicy(clock)
        val key = "target".encodeUtf8()
        val text = SourceRequestPolicy.Response.Text(VeneraHttpResponse(200, "body"))
        policy.save(key, text)
        clock += 5.seconds
        assertNull(policy.lookup(key, null))
        assertNotNull(policy.lookup(key, "long"))
        clock += 6.hours
        assertNull(policy.lookup(key, "long"))
        policy.save(key, text.copy(value = text.value.copy(headers = mapOf("Cache-Control" to "max-age=2"))))
        clock += 2.seconds
        assertNull(policy.lookup(key, "long"))
        repeat(257) { policy.save(it.toString().encodeUtf8(), text) }
        assertNull(policy.lookup("0".encodeUtf8(), null))
        assertNotNull(policy.lookup("256".encodeUtf8(), null))
        val oversized = SourceRequestPolicy.Response.Bytes(VeneraBytesResponse(200, ByteArray(1_048_577)))
        policy.save(key, oversized)
        assertNull(policy.lookup(key, "long"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun matchingOptInWaitersSerializeAndCancellationReleasesTheirSlot() = runTest {
        val path = SourceRequestPolicy.Path("alpha", "GET", "https://example.test")
        val other = path.copy(sourceKey = "beta")
        val policy = SourceRequestPolicy()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val active = async {
            policy.serialize(path, true) {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val cancelled = async { policy.serialize(path, true) { error("Cancelled waiter entered") } }
        val next = async { policy.serialize(path, true) { "next" } }
        runCurrent()
        assertFalse(next.isCompleted)
        assertTrue(policy.serialize(other, true) { true })
        assertTrue(policy.serialize(path, false) { true })
        cancelled.cancelAndJoin()
        active.cancelAndJoin()
        assertEquals("next", next.await())
    }
}
