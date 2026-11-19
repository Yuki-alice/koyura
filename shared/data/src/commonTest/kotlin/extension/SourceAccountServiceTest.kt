package app.koyura.data.extension

import app.koyura.data.session.openExtensionSession
import app.koyura.domain.extension.SourceAccountField
import app.koyura.domain.extension.SourceAccountFieldType
import app.koyura.domain.extension.SourceAccountInfo
import app.koyura.domain.extension.SourceAccountInfoConfig
import app.koyura.domain.extension.SourceLoginState
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
            val config = assertNotNull(extensions.sourceAccountConfig("account"))
            assertTrue(config.declaresWebLogin)
            assertTrue(config.fields.isEmpty())
            assertEquals(SourceLoginState.LOGGED_OUT, extensions.sourceLoginState("account"))
            assertTrue(accounts.loginWithWebView("account", browser))
            assertEquals(JsonPrimitive("local:cookie"), extensions.runtime!!.call("account", VeneraCallback.LoadInfo))
            assertEquals(SourceLoginState.WEB, extensions.sourceLoginState("account"))
            assertFalse(accounts.reLogin("account"))
            val pending = assertNotNull(accounts.captureWebLogin("account", browser))
            accounts.logout("account")
            assertFalse(pending.complete())
            assertEquals(SourceLoginState.LOGGED_OUT, extensions.sourceLoginState("account"))
            assertEquals(JsonNull, extensions.runtime!!.call("account", VeneraCallback.LoadInfo))
        } finally {
            session.close()
            files.deleteRecursively(directory, mustExist = false)
        }
    }

    @Test
    fun exposesAccountShapesAndReplaysSuccessfulFormValues() = runTest {
        val files = FileSystem.SYSTEM
        val directory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "source-forms-${Uuid.random()}"
        val session = openExtensionSession(
            createTestDriver(), files, directory, HttpClient(MockEngine { respond("ok") }),
            VeneraEnvironment("1.16.0", "en", "test"),
        )
        try {
            val extensions = session.service
            val accounts = extensions.accounts
            suspend fun install(key: String, definition: String) {
                extensions.installFromContent("$key.js", """
                    class Source extends ComicSource {
                        name = "$key"; key = "$key"; version = "1.0.0"; minAppVersion = "1.0.0";
                        $definition
                        comic = { loadInfo: () => this.deleteData("profile") };
                    }
                """.trimIndent())
            }
            install("none", "")
            install("basic", """
                account = { login: async (user, password) => {
                    if (user !== "alice" || password !== "secret") throw new Error("Invalid");
                    return false;
                } };
            """)
            install("mixed", """
                account = {
                    login: async (server, user, password) => {
                        if ([server, user, password].join("/") !== "demo/alice/secret") throw new Error("Invalid");
                        this.saveData("profile", server + "/" + user);
                    },
                    loginWithWebview: { url: "https://example.test/login", checkStatus: () => true },
                    registerWebsite: "https://example.test/register",
                    infoItems: [
                        { title: "Source" },
                        { title: "Profile", data: () => this.loadData("profile") },
                        { title: "Plan", async data() { return "Free"; } },
                    ],
                };
                constructor() {
                    super();
                    this.account.login.fields = [
                        { label: "Server", type: "text" },
                        { label: "User", type: "username" },
                        { label: "Passphrase", type: "password" },
                    ];
                }
            """)
            install("cookies", """
                account = { loginWithCookies: {
                    fields: ["sid", "csrf"],
                    validate: async values => values.join("/") === "first/second",
                } };
            """)

            assertNull(extensions.sourceAccountConfig("none"))
            assertEquals(listOf("Username", "Password"), extensions.sourceAccountConfig("basic")!!.fields.map { it.label })
            accounts.login("basic", "alice", "secret")
            assertEquals(SourceLoginState.FORM, extensions.sourceLoginState("basic"))

            val mixed = assertNotNull(extensions.sourceAccountConfig("mixed"))
            assertTrue(mixed.declaresWebLogin)
            assertEquals("https://example.test/register", mixed.registerWebsite)
            assertEquals(listOf(
                SourceAccountField("Server", SourceAccountFieldType.TEXT),
                SourceAccountField("User", SourceAccountFieldType.TEXT),
                SourceAccountField("Passphrase", SourceAccountFieldType.PASSWORD),
            ), mixed.fields)
            assertEquals(listOf(
                SourceAccountInfoConfig("Source", false),
                SourceAccountInfoConfig("Profile", true),
                SourceAccountInfoConfig("Plan", true),
            ), mixed.infoItems)
            assertEquals(SourceLoginState.LOGGED_OUT, extensions.sourceLoginState("mixed"))
            assertFailsWith<IllegalArgumentException> { accounts.loginForm("mixed", listOf("alice")) }
            assertTrue(accounts.loginForm("mixed", listOf("demo", "alice", "secret")))
            assertEquals(SourceLoginState.FORM, extensions.sourceLoginState("mixed"))
            assertEquals(listOf(
                SourceAccountInfo("Source", null), SourceAccountInfo("Profile", "demo/alice"), SourceAccountInfo("Plan", "Free"),
            ), extensions.sourceAccountInfo("mixed"))
            extensions.runtime!!.call("mixed", VeneraCallback.LoadInfo)
            assertNull(extensions.sourceAccountInfo("mixed")[1].value)
            assertTrue(accounts.reLogin("mixed"))
            assertEquals("demo/alice", extensions.sourceAccountInfo("mixed")[1].value)
            accounts.logout("mixed")
            assertFalse(accounts.reLogin("mixed"))

            assertEquals(listOf(
                SourceAccountField("sid", SourceAccountFieldType.COOKIES),
                SourceAccountField("csrf", SourceAccountFieldType.COOKIES),
            ), extensions.sourceAccountConfig("cookies")!!.fields)
            assertFalse(accounts.loginForm("cookies", listOf("second", "first")))
            assertEquals(SourceLoginState.LOGGED_OUT, extensions.sourceLoginState("cookies"))
            assertTrue(accounts.loginForm("cookies", listOf("first", "second")))
            assertEquals(SourceLoginState.WEB, extensions.sourceLoginState("cookies"))
            assertFalse(accounts.reLogin("cookies"))
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
