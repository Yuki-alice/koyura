package app.koyura.app.browse.venera

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.session.openExtensionSession
import app.koyura.domain.extension.SearchOptionValue
import app.koyura.domain.extension.SearchOptionsSnapshot
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 搜索选项桥接：注入搜索通道（`session.service.browse`）后 searchWorks 应用源声明
 * 的选项默认值与持久化取值；未注入（测试/旧装配）回退空选项。
 */
class SearchOptionsBridgeTest {

    @Test
    fun searchAppliesDeclaredDefaultsAndPersistedChoices() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExtensionDb.Schema.create(it) }
        val temp = Files.createTempDirectory("search-bridge").toFile()
        val session = runBlocking {
            openExtensionSession(
                driver,
                FileSystem.SYSTEM,
                temp.toOkioPath(),
                HttpClient(MockEngine { respond("ok") }),
                VeneraEnvironment("1.16.0", "en", "test"),
            )
        }
        try {
            runBlocking {
                session.service.installFromContent(
                    "options.js",
                    """
                    class Options extends ComicSource {
                        name = "options"; key = "options"; version = "1.0.0"; minAppVersion = "1.0.0";
                        search = {
                            optionList: [
                                { label: "Status", type: "select",
                                  options: ["all-全部", "ongoing-连载", "end-完结"], default: "all" },
                            ],
                            load: (query, options) => ({
                                comics: [new Comic({ id: "q", title: (options[0] ?? "none") + "/" + query })],
                                maxPage: 1,
                            }),
                        };
                    }
                    """.trimIndent(),
                )
            }
            val bridged = VeneraBrowseService(
                extensions = session.service,
                search = session.service.browse,
            )
            val fallback = VeneraBrowseService(extensions = session.service)
            runBlocking {
                // 声明默认值生效：all（而非空回退的 none）。
                assertEquals("all/hi", bridged.searchWorks("options", "hi").works.single().title)
                // 未注入通道：回退空选项，脚本拿到 undefined。
                assertEquals("none/hi", fallback.searchWorks("options", "hi").works.single().title)
                // 持久化当前取值后生效。
                bridged.setSourceSearchOptions(
                    "options",
                    SearchOptionsSnapshot(listOf(SearchOptionValue.Single("ongoing"))),
                )
                assertEquals("ongoing/hi", bridged.searchWorks("options", "hi").works.single().title)
                // 保存搜索按当前快照落库并可列出。
                val saved = bridged.saveSearch("options", "Running", "hi")
                assertEquals("Running", saved?.name)
                assertEquals(1, bridged.savedSearches("options").size)
            }
        } finally {
            runBlocking { session.close() }
            temp.deleteRecursively()
        }
    }
}
