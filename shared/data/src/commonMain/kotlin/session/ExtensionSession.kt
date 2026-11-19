package app.koyura.data.session

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.extension.ExtensionService
import app.koyura.data.history.HistoryStore
import app.koyura.data.history.ensureHistoryVisibilitySchema
import app.koyura.data.extension.RestoreReport
import app.koyura.data.library.LibraryStore
import app.koyura.data.library.ensureLibrarySchema
import app.koyura.data.reading.ComicReadingService
import app.koyura.data.reader.preferences.ReaderPreferencesStore
import app.koyura.source.runtime.VeneraEnvironment
import app.koyura.source.runtime.SourceSecretStore
import app.koyura.source.runtime.SourceSessions
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
    val readerPreferences: ReaderPreferencesStore,
) {
    val comicReading = ComicReadingService(driver, service, httpClient)
    val history = HistoryStore(driver)
    val library = LibraryStore(driver)

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
    secrets: SourceSecretStore? = null,
): ExtensionSession {
    val scriptsDir = appDataDir / "scripts"
    val service = ExtensionService(driver, fileSystem, scriptsDir, httpClient, environment, SourceSessions(secrets))
    try {
        ensureSchema(driver)
        service.open()
        val report = service.restore()
        val readerPreferences = ReaderPreferencesStore(appDataDir, fileSystem)
        return ExtensionSession(service, report, driver, httpClient, readerPreferences)
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
private fun tableColumns(driver: SqlDriver, table: String): Set<String> {
    val columns = mutableSetOf<String>()
    driver.executeQuery(null, "PRAGMA table_info($table)", { cursor ->
        while (cursor.next().value) {
            cursor.getString(1)?.let { columns += it }
        }
        QueryResult.Value(Unit)
    }, 0, null)
    return columns
}

private fun ensureSchema(driver: SqlDriver) {
    val present = mutableSetOf<String>()
    driver.executeQuery(null, "SELECT name FROM sqlite_master WHERE type = 'table'", { cursor ->
        while (cursor.next().value) {
            cursor.getString(0)?.let { present += it }
        }
        QueryResult.Value(Unit)
    }, 0, null)
    val target = ExtensionDb.Schema.version
    if (present.containsAll(extensionTables)) {
        if ("reading_progress" !in present) {
            // Pre-reading database (no progress table yet): create it through migrations,
            // which also bring the display columns.
            ExtensionDb(driver).transaction {
                ExtensionDb.Schema.migrate(driver, 1, target).value
            }
        } else {
            // Reading-era database without the display columns (the generated schema code
            // never stamps PRAGMA user_version, so detection is structural, not stamp-based).
            // ALTERs run in place and keep every committed row; already-migrated databases
            // are untouched.
            val columns = tableColumns(driver, "reading_progress")
            if (!columns.containsAll(listOf("read_at", "work_title", "chapter_title"))) {
                ExtensionDb(driver).transaction {
                    if ("read_at" !in columns) {
                        driver.execute(null, "ALTER TABLE reading_progress ADD COLUMN read_at INTEGER", 0, null)
                    }
                    if ("work_title" !in columns) {
                        driver.execute(null, "ALTER TABLE reading_progress ADD COLUMN work_title TEXT", 0, null)
                    }
                    if ("chapter_title" !in columns) {
                        driver.execute(null, "ALTER TABLE reading_progress ADD COLUMN chapter_title TEXT", 0, null)
                    }
                }
            }
        }
    } else {
        if (present.any { it in extensionTables }) {
            for (table in extensionTables) {
                if (table in present) {
                    driver.execute(null, "DROP TABLE IF EXISTS $table", 0, null)
                }
            }
        }
        ExtensionDb.Schema.create(driver)
    }
    // 书架四表（第 4 版）：已存在 reading 时代的老库不会自动执行 .sqm，
    // 这里幂等补齐并播种默认分类；新库 Schema.create 已建表，此处只剩播种。
    ensureLibrarySchema(driver)
    // 历史可见性表（第 5 版）：删除/清空只插隐藏标记，reading_progress 原样保留。
    ensureHistoryVisibilitySchema(driver)
}
