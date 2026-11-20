package app.koyura.app.updates

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.session.openExtensionSession
import app.koyura.features.updates.UpdatePolicy
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
import kotlin.test.assertFalse

/** 智能更新策略生产桥接：真扫描引擎偏好（library_prefs 持久化）与界面契约的往返。 */
class ProductionUpdatePolicyRepositoryTest {

    @Test
    fun policyRoundTripsAndNormalizesTerms() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExtensionDb.Schema.create(it) }
        val temp = Files.createTempDirectory("policy-bridge").toFile()
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
            val repository = ProductionUpdatePolicyRepository(session.updates)
            runBlocking {
                // 默认策略与界面契约默认值一致。
                assertEquals(UpdatePolicy(), repository.policy())
                // 写入经映射与归一化（去首尾空白、丢空项、按小写去重）。
                repository.apply(
                    UpdatePolicy(
                        enabled = false,
                        skipCompleted = false,
                        excludedTitleTerms = listOf("  外传 ", "", "番外", "番外"),
                    ),
                )
                val stored = repository.policy()
                assertFalse(stored.enabled)
                assertFalse(stored.skipCompleted)
                assertEquals(listOf("外传", "番外"), stored.excludedTitleTerms)
            }
        } finally {
            runBlocking { session.close() }
            temp.deleteRecursively()
        }
    }
}
