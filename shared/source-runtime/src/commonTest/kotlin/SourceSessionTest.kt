package app.koyura.source.runtime

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SourceSessionTest {
    @Test
    fun separatesSourcesAndRedirectsAndPersistsTextAndImageCookies() = runTest {
        val secrets = MemorySecrets()
        val seen = mutableListOf<Pair<String, String?>>()
        val client = HttpClient(MockEngine { request ->
            seen += request.url.toString() to request.headers[HttpHeaders.Cookie]
            when (request.url.encodedPath) {
                "/login" -> respond("", HttpStatusCode.Found, headers {
                    append(HttpHeaders.Location, "https://comics.example/media/welcome")
                    append(HttpHeaders.SetCookie, "sid=alpha; Path=/media; Secure; HttpOnly")
                })
                "/leave" -> respond("", HttpStatusCode.Found, headers {
                    append(HttpHeaders.Location, "https://elsewhere.example/media/welcome")
                })
                else -> respond("image or html")
            }
        })
        fun request(key: String, path: String, headers: Map<String, String> = emptyMap()) =
            VeneraHttpRequest(key, "GET", "https://comics.example$path", headers)
        val environment = VeneraEnvironment("1.16.0", "en", "desktop")
        val sessions = SourceSessions(secrets)
        val transport = KtorVeneraHttpTransport(client, sessions = sessions)
        val live = VeneraSourceRuntime.create(environment, sessions, transport)
        try {
            live.register(script("one"), expectedKey = "one")
            live.register(script("two"), expectedKey = "two")
            assertEquals(JsonPrimitive("alpha"), live.call("one", VeneraCallback.Search))
            assertEquals(JsonPrimitive("missing"), live.call("two", VeneraCallback.SearchNext))
            assertEquals(JsonPrimitive("one"), live.call("one", VeneraCallback.LoadInfo))
            transport.execute(request("one", "/leave", mapOf("Cookie" to "explicit=value")))
            assertEquals(
                listOf(
                    "https://comics.example/login" to null,
                    "https://comics.example/media/welcome" to "sid=alpha",
                    "https://comics.example/leave" to "explicit=value",
                    "https://elsewhere.example/media/welcome" to null,
                ),
                seen,
            )
        } finally {
            live.close()
            transport.close()
        }
        val reopened = SourceSessions(secrets)
        val reopenedTransport = KtorVeneraHttpTransport(client, sessions = reopened)
        try {
            assertEquals(JsonPrimitive("one"), reopened.getData("one", "owner"))
            reopenedTransport.executeBytes(request("one", "/media/image"))
            reopenedTransport.executeBytes(request("two", "/media/image"))
            assertEquals(listOf("sid=alpha", null), seen.takeLast(2).map { it.second })
            reopened.clear("one")
            assertNull(SourceSessions(secrets).cookies("one", "https://comics.example/media/image").singleOrNull())
        } finally {
            reopenedTransport.close()
            client.close()
        }
    }

    @Test
    fun discardedReplacementLeavesPersistedDataFromCurrentInstance() = runTest {
        val secrets = MemorySecrets()
        val sessions = SourceSessions(secrets)
        val live = VeneraSourceRuntime.create(VeneraEnvironment("1.16.0", "en", "desktop"), sessions) {
            error("Unexpected HTTP request")
        }
        try {
            val source = script("one")
            live.register(source, expectedKey = "one")
            assertFailsWith<VeneraScriptException> {
                live.replace("one", source.replace("this.key); }", "'candidate'); throw new Error('bad'); }"))
            }
            assertEquals(JsonPrimitive("one"), SourceSessions(secrets).getData("one", "owner"))
            live.replace("one", source.replace("this.key); }", "'candidate'); }"))
            assertEquals(JsonPrimitive("candidate"), live.call("one", VeneraCallback.LoadInfo))
            live.repairDisplaced("one")
            assertEquals(JsonPrimitive("one"), live.call("one", VeneraCallback.LoadInfo))
        } finally {
            live.close()
        }
    }

    private fun script(key: String) = """
        class Example extends ComicSource {
            name = "Example";
            version = "1.0.0";
            minAppVersion = "1.0.0";
            key = "$key";
            init() { this.saveData("owner", this.key); }
            search = {
                load: async () => {
                    await Network.get("https://comics.example/login");
                    return Network.getCookies("https://comics.example/media/welcome")[0].value;
                },
                loadNext: () => Network.getCookies("https://comics.example/media/welcome")[0]?.value ?? "missing"
            };
            comic = { loadInfo: () => this.loadData("owner") };
        }
    """.trimIndent()

    private class MemorySecrets : SourceSecretStore {
        private val values = mutableMapOf<String, String>()
        override fun read(sourceKey: String) = values[sourceKey]
        override fun write(sourceKey: String, value: String?) {
            if (value == null) values.remove(sourceKey) else values[sourceKey] = value
        }
    }
}
