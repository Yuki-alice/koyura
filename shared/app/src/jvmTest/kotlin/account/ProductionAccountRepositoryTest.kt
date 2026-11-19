package app.koyura.app.account

import app.koyura.features.account.AccountFieldType
import app.koyura.features.account.AccountState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 账号区生产桥接：`ExtensionService` 查询与动作到界面契约的映射（脚本夹具驱动）。 */
class ProductionAccountRepositoryTest {

    @Test
    fun mapsConfiguredStatesAndActions() {
        val harness = app.koyura.app.browse.venera.VeneraHarness()
        try {
            runBlocking {
                harness.service.open()
                harness.service.restore()
                harness.service.installFromContent(
                    "plain.js",
                    """
                    class Plain extends ComicSource {
                        name = "plain"; key = "plain"; version = "1.0.0"; minAppVersion = "1.0.0";
                    }
                    """.trimIndent(),
                )
                harness.service.installFromContent(
                    "mixed.js",
                    """
                    class Mixed extends ComicSource {
                        name = "mixed"; key = "mixed"; version = "1.0.0"; minAppVersion = "1.0.0";
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
                    }
                    """.trimIndent(),
                )
            }
            val repository = ProductionAccountRepository(harness.service)
            runBlocking {
                // 无 account 声明 → 整块隐藏。
                assertIs<AccountState.NotConfigured>(repository.state("plain"))

                // 未登录：字段按声明顺序、注册外链透传；宿主无平台浏览器 → 网页登录不可用。
                val loggedOut = assertIs<AccountState.LoggedOut>(repository.state("mixed"))
                assertEquals(
                    listOf(
                        app.koyura.features.account.AccountFieldConfig("Server", AccountFieldType.TEXT),
                        app.koyura.features.account.AccountFieldConfig("User", AccountFieldType.TEXT),
                        app.koyura.features.account.AccountFieldConfig("Passphrase", AccountFieldType.PASSWORD),
                    ),
                    loggedOut.fields,
                )
                assertEquals("https://example.test/register", loggedOut.registerUrl)
                assertFalse(loggedOut.webLoginSupported)
                assertFalse(repository.captureWebLogin("mixed"))

                // 表单登录：按声明顺序传值；脚本拒绝凭据时异常上抛（会话层记 actionError），
                // 成功后 FORM 态 + 信息行动态取值。
                assertFailsWith<Exception> { runBlocking { repository.login("mixed", listOf("demo", "alice", "wrong")) } }
                assertTrue(repository.login("mixed", listOf("demo", "alice", "secret")))
                val loggedIn = assertIs<AccountState.LoggedIn>(repository.state("mixed"))
                assertTrue(loggedIn.formLogin)
                assertEquals(
                    listOf(
                        app.koyura.features.account.AccountInfoItem("Source", null),
                        app.koyura.features.account.AccountInfoItem("Profile", "demo/alice"),
                    ),
                    loggedIn.infoItems,
                )

                // 注销回未登录态。
                assertTrue(repository.logout("mixed"))
                assertIs<AccountState.LoggedOut>(repository.state("mixed"))
            }
        } finally {
            harness.close()
        }
    }
}
