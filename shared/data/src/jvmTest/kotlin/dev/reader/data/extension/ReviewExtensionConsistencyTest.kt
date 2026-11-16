package dev.reader.data.extension

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.reader.network.applyReaderDefaults
import dev.reader.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class ReviewExtensionConsistencyTest {
    @Test
    fun installedSourceRunsInitOnce() = runTest {
        val hits = AtomicInteger()
        val client = HttpClient(MockEngine {
            hits.incrementAndGet()
            respond("ok", HttpStatusCode.OK)
        }) { applyReaderDefaults() }
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-review-init-${Uuid.random()}"
        val driver = createTestDriver()
        val service = newService(driver, FileSystem.SYSTEM, root, client)
        try {
            service.open()
            service.restore()
            service.installFromContent("Review", script("1.0.0", withInit = true), "review")
            assertEquals(1, hits.get(), "Installing one source runs its init hook once")
        } finally {
            service.close()
            driver.close()
            client.close()
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    @Test
    fun interruptedReplacementRestoresMatchingRowsAndLiveScript() = runTest {
        var interrupt = false
        val system = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-review-restart-${Uuid.random()}"
        system.createDirectories(root)
        val disk = object : ForwardingFileSystem(system) {
            override fun atomicMove(source: Path, target: Path) {
                super.atomicMove(source, target)
                if (interrupt) throw SimulatedInterruption()
            }
        }
        val database = "jdbc:sqlite:${root / "extensions.db"}"
        val client = HttpClient(MockEngine { respond(script("2.0.0"), HttpStatusCode.OK) }) { applyReaderDefaults() }
        try {
            val driver = JdbcSqliteDriver(database)
            ExtensionDb.Schema.create(driver)
            val first = newService(driver, disk, root / "scripts", client)
            try {
                first.open()
                first.restore()
                first.installFromContent("Review", script("1.0.0"), "review")
                interrupt = true
                assertFailsWith<SimulatedInterruption> { first.update("review") }
            } finally {
                first.close()
                driver.close()
            }
            val reopened = JdbcSqliteDriver(database)
            val second = newService(reopened, system, root / "scripts", client)
            try {
                second.open()
                assertEquals(1, second.restore().registered)
                val persisted = ExtensionDb(reopened).extensionDbQueries.selectExtensionByKey("review").executeAsOne().version
                val running = second.runtime!!.registeredSources().single().version
                assertEquals(persisted, running, "The restarted registry and persistent metadata must describe the same revision")
            } finally {
                second.close()
                reopened.close()
            }
        } finally {
            client.close()
            system.deleteRecursively(root)
        }
    }

    private class SimulatedInterruption : Error()

    private fun newService(driver: app.cash.sqldelight.db.SqlDriver, disk: FileSystem, scripts: Path, client: HttpClient) =
        ExtensionService(driver, disk, scripts, client, VeneraEnvironment("1.16.0", "zh_CN", "test"))

    private fun script(version: String, withInit: Boolean = false) = """
        class ReviewSource extends ComicSource {
            name = "Review";
            key = "review";
            version = "$version";
            minAppVersion = "1.16.0";
            url = "https://example.invalid/review.js";
            ${if (withInit) "async init() { await Network.get('https://example.invalid/init'); }" else ""}
        }
    """.trimIndent()
}
