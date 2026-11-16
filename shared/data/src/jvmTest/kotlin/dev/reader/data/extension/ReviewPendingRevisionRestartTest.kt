package dev.reader.data.extension

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.reader.network.applyReaderDefaults
import dev.reader.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class ReviewPendingRevisionRestartTest {
    @Test
    fun restartDuringCandidateInitRestoresLastUsableRevision() = runTest {
        val reachedInit = CompletableDeferred<Unit>()
        val disk = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-review-pending-${Uuid.random()}"
        val originals = root / "running" / "scripts"
        val restarted = root / "restarted" / "scripts"
        disk.createDirectories(originals)
        disk.createDirectories(restarted)
        val originalDatabase = root / "running" / "extensions.db"
        val restartDatabase = root / "restarted" / "extensions.db"
        val environment = VeneraEnvironment("1.16.0", "zh_CN", "test")
        val runningHttp = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/candidate-init") {
                reachedInit.complete(Unit)
                awaitCancellation()
            }
            respond(script("2.0.0", candidateInit), HttpStatusCode.OK)
        }) { applyReaderDefaults() }
        val restartHttp = HttpClient(MockEngine { request ->
            check(request.url.encodedPath == "/candidate-init")
            respond("candidate init failed", HttpStatusCode.InternalServerError)
        }) { applyReaderDefaults() }
        try {
            val originalDriver = JdbcSqliteDriver("jdbc:sqlite:$originalDatabase")
            ExtensionDb.Schema.create(originalDriver)
            val running = ExtensionService(originalDriver, disk, originals, runningHttp, environment)
            val oldFile = try {
                running.open()
                running.restore()
                val old = running.installFromContent("Review", script("1.0.0"), "review")
                val update = launch { running.update("review") }
                try {
                    reachedInit.await()
                    // The updater is awaiting HTTP, so the on-disk state is stable. Copy it before
                    // cancelling to simulate abrupt process death without running the error handler.
                    disk.copy(originalDatabase, restartDatabase)
                    disk.list(originals).forEach { path -> disk.copy(path, restarted / path.name) }
                } finally {
                    update.cancelAndJoin()
                }
                old.fileName
            } finally {
                running.close()
                originalDriver.close()
            }
            val restartDriver = JdbcSqliteDriver("jdbc:sqlite:$restartDatabase")
            val recovered = ExtensionService(restartDriver, disk, restarted, restartHttp, environment)
            try {
                recovered.open()
                val report = recovered.restore()
                assertEquals(
                    Triple("1.0.0", 1, true),
                    Triple(recovered.listInstalled().single().version, report.registered, disk.exists(restarted / oldFile)),
                    "Reopening the on-disk state during init must retain the last usable revision",
                )
            } finally {
                recovered.close()
                restartDriver.close()
            }
        } finally {
            runningHttp.close()
            restartHttp.close()
            disk.deleteRecursively(root)
        }
    }

    private val candidateInit = """
        async init() {
            const response = await Network.get('https://example.invalid/candidate-init');
            if (response.status !== 200) throw new Error('init failed');
        }
    """.trimIndent()

    private fun script(version: String, init: String = "") = """
        class ReviewSource extends ComicSource {
            name = "Review";
            key = "review";
            version = "$version";
            minAppVersion = "1.16.0";
            url = "https://example.invalid/review.js";
            $init
            comic = { loadInfo: () => ({ title: "$version" }) };
        }
    """.trimIndent()
}
