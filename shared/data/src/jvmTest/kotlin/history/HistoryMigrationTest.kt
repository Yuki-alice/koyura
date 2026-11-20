package app.koyura.data.history

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.session.openExtensionSession
import app.koyura.network.applyReaderDefaults
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private val readingEnv = VeneraEnvironment("1.16.0", "zh_CN", "test")

private fun quietClient() = HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) {
    applyReaderDefaults()
}

private const val TablesV1 = """
CREATE TABLE repositories(
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  url TEXT NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1
);
CREATE TABLE origins(
  source_key TEXT PRIMARY KEY,
  kind TEXT NOT NULL,
  repository_id TEXT,
  repository_name TEXT,
  url TEXT
);
CREATE TABLE extensions(
  source_key TEXT NOT NULL,
  name TEXT NOT NULL,
  version TEXT NOT NULL,
  file_name TEXT NOT NULL,
  origin_kind TEXT NOT NULL,
  origin_repository_id TEXT,
  origin_repository_name TEXT,
  origin_url TEXT
);
"""

private const val ReadingProgressV2 = """
CREATE TABLE reading_progress(
  source_key TEXT NOT NULL,
  work_id TEXT NOT NULL,
  chapter_id TEXT NOT NULL,
  page_key TEXT NOT NULL,
  page_index INTEGER NOT NULL,
  PRIMARY KEY (source_key, work_id, chapter_id)
);
CREATE INDEX reading_progress_work_idx ON reading_progress(source_key, work_id);
"""

private fun execScript(driver: JdbcSqliteDriver, script: String) {
    script.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { statement ->
        driver.execute(null, "$statement;", 0, null)
    }
}

private fun tableColumns(driver: JdbcSqliteDriver, table: String): Set<String> {
    val columns = mutableSetOf<String>()
    driver.executeQuery(null, "PRAGMA table_info($table)", { cursor ->
        while (cursor.next().value) {
            cursor.getString(1)?.let { columns += it }
        }
        app.cash.sqldelight.db.QueryResult.Value(Unit)
    }, 0, null)
    return columns
}

/**
 * Startup paths across schema eras. Every path must leave committed rows intact,
 * land on the current schema version and keep old progress readable.
 */
class HistoryMigrationTest {
    @Test
    fun freshDatabaseCreatesCurrentSchema() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-history-fresh-${Uuid.random()}"
        val driver = createFileBackedDriver(root)
        try {
            val session = openExtensionSession(driver, FileSystem.SYSTEM, root / "app", quietClient(), readingEnv)
            try {
                assertEquals(
                    setOf("source_key", "work_id", "chapter_id", "page_key", "page_index", "read_at", "work_title", "chapter_title", "read_duration_ms"),
                    tableColumns(driver, "reading_progress"),
                )
                val store = HistoryStore(driver)
                assertTrue(store.latestEntries().isEmpty())
                // New saves through the existing writer stay readable.
                ExtensionDb(driver).extensionDbQueries.upsertProgress("s", "w", "ep_1", "p", 2)
                val entry = store.latestEntries().single()
                assertEquals("ep_1", entry.chapterId)
                assertEquals(2, entry.pageIndex)
                assertNull(entry.readAtMillis)
            } finally {
                session.close()
            }
        } finally {
            driver.close()
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    @Test
    fun legacyDatabaseWithoutProgressTableMigrates() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-history-legacy-${Uuid.random()}"
        val driver = createFileBackedDriver(root)
        try {
            execScript(driver, TablesV1)
            driver.execute(null, "INSERT INTO repositories(id, name, url, enabled) VALUES('r', 'Repo', 'https://example.invalid/index.json', 1)", 0, null)
            driver.execute(null, "INSERT INTO extensions(source_key, name, version, file_name, origin_kind, origin_repository_id, origin_repository_name, origin_url) VALUES('example_source', 'Example', '1.0.0', 'example_source@1.0.0.js', 'repository', 'r', 'Repo', 'https://example.invalid/example.js')", 0, null)
            // The generated schema in these builds never stamps user_version; the upgrade
            // must detect the era structurally instead of trusting the pragma.

            val session = openExtensionSession(driver, FileSystem.SYSTEM, root / "app", quietClient(), readingEnv)
            try {
                assertEquals(
                    setOf("source_key", "work_id", "chapter_id", "page_key", "page_index", "read_at", "work_title", "chapter_title", "read_duration_ms"),
                    tableColumns(driver, "reading_progress"),
                )
                // The legacy extension row and its repository survive the migration.
                assertEquals(listOf("example_source"), session.service.listInstalled().map { it.sourceKey })
                assertEquals(
                    listOf("https://example.invalid/index.json"),
                    session.service.listRepositories().map { it.url },
                )
                assertTrue(HistoryStore(driver).latestEntries().isEmpty())
            } finally {
                session.close()
            }
        } finally {
            driver.close()
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    @Test
    fun v2ProgressRowsSurviveMigrationWithUnknownTime() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-history-v2-${Uuid.random()}"
        val driver = createFileBackedDriver(root)
        try {
            execScript(driver, TablesV1)
            execScript(driver, ReadingProgressV2)
            driver.execute(null, "INSERT INTO repositories(id, name, url, enabled) VALUES('r', 'Repo', 'https://example.invalid/index.json', 1)", 0, null)
            driver.execute(null, "INSERT INTO extensions(source_key, name, version, file_name, origin_kind, origin_repository_id, origin_repository_name, origin_url) VALUES('example_source', 'Example', '1.0.0', 'example_source@1.0.0.js', 'repository', 'r', 'Repo', 'https://example.invalid/example.js')", 0, null)
            driver.execute(null, "INSERT INTO reading_progress(source_key, work_id, chapter_id, page_key, page_index) VALUES('example_source', 'demo', 'ep_1', 'https://example.invalid/2.png', 2)", 0, null)
            // Same as production: no user_version stamp, so detection is structural.

            val session = openExtensionSession(driver, FileSystem.SYSTEM, root / "app", quietClient(), readingEnv)
            try {
                assertEquals(
                    setOf("source_key", "work_id", "chapter_id", "page_key", "page_index", "read_at", "work_title", "chapter_title", "read_duration_ms"),
                    tableColumns(driver, "reading_progress"),
                )
                // Existing extensions and repositories keep working after the in-place upgrade.
                assertEquals(listOf("example_source"), session.service.listInstalled().map { it.sourceKey })
                assertEquals(
                    listOf("https://example.invalid/index.json"),
                    session.service.listRepositories().map { it.url },
                )
                // Old progress keeps its chapter and page but gains no invented timestamp.
                val entries = HistoryStore(driver).latestEntries()
                val entry = entries.single()
                assertEquals("demo", entry.workId)
                assertEquals("ep_1", entry.chapterId)
                assertEquals(2, entry.pageIndex)
                assertNull(entry.readAtMillis)
                assertEquals(0L, session.statistics.load().readDurationMillis)
                // The unchanged writer still works after migration.
                ExtensionDb(driver).extensionDbQueries.upsertProgress("example_source", "demo", "ep_1", "https://example.invalid/3.png", 3)
                assertEquals(3, HistoryStore(driver).latestEntries().single().pageIndex)
            } finally {
                session.close()
            }
        } finally {
            driver.close()
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    private fun createFileBackedDriver(root: Path): JdbcSqliteDriver {
        FileSystem.SYSTEM.createDirectories(root)
        return JdbcSqliteDriver("jdbc:sqlite:${root / "extensions.db"}")
    }
}
