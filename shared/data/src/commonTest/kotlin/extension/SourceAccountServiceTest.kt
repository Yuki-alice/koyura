package app.koyura.data.extension

import app.koyura.data.session.openExtensionSession
import app.koyura.source.runtime.SourceCookie
import app.koyura.source.runtime.VeneraCallback
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SourceAccountServiceTest {
    @Test
    fun handsOffAcceptedPageBeforeAwaitingSuccessAndInvalidatesPendingLoginOnLogout() = runTest {
        val files = FileSystem.SYSTEM
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "source-account-${Uuid.random()}"
        val session = openExtensionSession(
            createTestDriver(), files, directory, HttpClient(MockEngine { respond("ok") }),
            VeneraEnvironment("1.16.0", "en", "test"),
        )
        try {
            val extensions = session.service
            extensions.installFromContent("account.js", script)
            val browser = WebLoginBrowser { url, check ->
                assertEquals("https://example.test/login", url)
                assertFalse(check(WebLoginPage(url, "Login")))
                val page = WebLoginPage("https://example.test/home", "Signed in")
                assertTrue(check(page))
                WebLoginCapture(page, listOf(SourceCookie("sid", "cookie")), buildJsonObject { put("user", "local") })
            }
            val accounts = extensions.accounts
            assertTrue(accounts.loginWithWebView("account", browser))
            assertEquals(JsonPrimitive("local:cookie"), extensions.runtime!!.call("account", VeneraCallback.LoadInfo))
            val pending = assertNotNull(accounts.captureWebLogin("account", browser))
            accounts.logout("account")
            assertFalse(pending.complete())
            assertEquals(JsonNull, extensions.runtime!!.call("account", VeneraCallback.LoadInfo))
        } finally {
            session.close()
            files.deleteRecursively(directory, mustExist = false)
        }
    }

    private val script = """
        class Source extends ComicSource {
            name = "Test Account"; key = "account"; version = "1.0.0";
            minAppVersion = "1.0.0";
            account = {
                loginWithWebview: {
                    url: "https://example.test/login",
                    checkStatus: (url, title) => url.endsWith("/home") && title === "Signed in",
                    onLoginSuccess: async () => {
                        await Promise.resolve();
                        const storage = this.loadData("_localStorage");
                        const sid = Network.getCookies("https://example.test/home").find(c => c.name === "sid");
                        this.saveData("result", storage.user + ":" + sid.value);
                    }
                }
            };
            comic = { loadInfo: () => this.loadData("result") };
        }
    """.trimIndent()
}
