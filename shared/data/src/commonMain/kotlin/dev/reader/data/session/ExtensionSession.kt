package dev.reader.data.session

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import dev.reader.data.extension.ExtensionDb
import dev.reader.data.extension.ExtensionService
import dev.reader.data.extension.RestoreReport
import dev.reader.data.reading.ComicReadingService
import dev.reader.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/**
 * A fully opened extension stack: persistent [ExtensionService] whose runtime
 * already restored persisted revisions ([report] describes the outcome).
 *
 * Ownership: the session owns the [SqlDriver], the [HttpClient] and the
 * QuickJS runtime inside [service]. [close] releases all three exactly once
 * and is safe to call repeatedly; after closing, the session must not be
 * used. Reopening means opening a new session on the same directories (with a
 * new driver/client pair); file-backed drivers then restore the same rows and
 * scripts.
 */
class ExtensionSession internal constructor(
    val service: ExtensionService,
    val report: RestoreReport,
    private val driver: SqlDriver,
    private val httpClient: HttpClient,
) {
    val comicReading = ComicReadingService(driver, service, httpClient)

    private val mutex = Mutex()
    private var closed = false

    val isClosed: Boolean get() = closed

    suspend fun close() {
        val doClose = mutex.withLock {
            if (closed) false else {
                closed = true
                true
            }
        }
        if (!doClose) return
        withContext(NonCancellable) {
            runCatching { service.close() }
            runCatching { httpClient.close() }
            runCatching { driver.close() }
        }
    }
}

/**
 * Opens (creating when absent) the extension database, prepares the scripts
 * directory and restores persisted revisions into a fresh QuickJS runtime.
 *
 * The schema creation is idempotent, so the first open initializes the
 * database and later opens reuse existing repositories, origins and revisions.
 * If opening or restoring fails (or is cancelled), every resource created for
 * this attempt is released before the error propagates.
 */
suspend fun openExtensionSession(
    driver: SqlDriver,
    fileSystem: FileSystem,
    appDataDir: Path,
    httpClient: HttpClient,
    environment: VeneraEnvironment,
): ExtensionSession {
    val scriptsDir = appDataDir / "scripts"
    val service = ExtensionService(driver, fileSystem, scriptsDir, httpClient, environment)
    try {
        ensureSchema(driver)
        service.open()
        val report = service.restore()
        return ExtensionSession(service, report, driver, httpClient)
    } catch (e: Throwable) {
        // Release everything created for this attempt, even when the failure
        // is a cancellation.
        withContext(NonCancellable) {
            runCatching { service.close() }
            runCatching { httpClient.close() }
            runCatching { driver.close() }
        }
        throw e
    }
}

private val extensionTables = listOf("repositories", "origins", "extensions")

/**
 * Creates the extension schema unless it already exists. Re-running against
 * a current database leaves committed rows intact. A pre-reading database is
 * upgraded here for drivers that leave schema management to their caller;
 * a partially created schema is dropped and recreated (no rows can reference
 * it yet, as writes only happen after a successful open).
 */
private fun ensureSchema(driver: SqlDriver) {
    val present = mutableSetOf<String>()
    driver.executeQuery(null, "SELECT name FROM sqlite_master WHERE type = 'table'", { cursor ->
        while (cursor.next().value) {
            cursor.getString(0)?.let { present += it }
        }
        QueryResult.Value(Unit)
    }, 0, null)
    if (present.containsAll(extensionTables)) {
        if ("reading_progress" !in present) {
            ExtensionDb(driver).transaction {
                ExtensionDb.Schema.migrate(driver, 1, ExtensionDb.Schema.version).value
            }
        }
        return
    }
    if (present.any { it in extensionTables }) {
        for (table in extensionTables) {
            if (table in present) {
                driver.execute(null, "DROP TABLE IF EXISTS $table", 0, null)
            }
        }
    }
    ExtensionDb.Schema.create(driver)
}
