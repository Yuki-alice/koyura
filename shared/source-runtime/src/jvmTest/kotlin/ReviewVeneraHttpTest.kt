package app.koyura.source.runtime

import com.sun.net.httpserver.HttpServer
import app.koyura.network.createDefaultHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.net.InetSocketAddress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewVeneraHttpTest {
    @Test
    fun engineReturnsLowercaseNamesForUpstreamJsLookup() = runTest {
        withLoopback { transport, url ->
            val response = transport.execute(VeneraHttpRequest("review", "GET", url))
            assertEquals("text/plain", response.headers["content-type"], "Actual: ${response.headers}")
        }
    }

    @Test
    fun engineUsesUpstreamBrowserUserAgentByDefault() = runTest {
        withLoopback { transport, url ->
            val response = transport.execute(VeneraHttpRequest("review", "GET", url))
            assertEquals(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
                response.body,
            )
        }
    }

    @Test
    fun injectedClientStillReturns404ToTheScript() = runTest {
        val engine = MockEngine { respond("not found", HttpStatusCode.NotFound) }
        val client = HttpClient(engine) { expectSuccess = true }
        try {
            val transport = KtorVeneraHttpTransport(client)
            val result = transport.execute(VeneraHttpRequest("review", "GET", "https://example.invalid/none"))
            assertEquals(404, result.status)
        } finally {
            client.close()
            engine.close()
        }
    }

    private suspend fun withLoopback(block: suspend (KtorVeneraHttpTransport, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = exchange.requestHeaders.getFirst("User-Agent").orEmpty().encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "text/plain")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val client = createDefaultHttpClient()
        try {
            block(KtorVeneraHttpTransport(client), "http://127.0.0.1:${server.address.port}/")
        } finally {
            client.close()
            server.stop(0)
        }
    }
}
