package app.koyura.data.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.session.openExtensionSession
import app.koyura.domain.library.LibraryQuery
import app.koyura.domain.library.WorkKind
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
import kotlin.test.assertFailsWith
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

private const val DisplayColumnsV3 = """
ALTER TABLE reading_progress ADD COLUMN read_at INTEGER;
ALTER TABLE reading_progress ADD COLUMN work_title TEXT;
ALTER TABLE reading_progress ADD COLUMN chapter_title TEXT;
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

private fun tableNames(driver: JdbcSqliteDriver): Set<String> {
    val names = mutableSetOf<String>()
    driver.executeQuery(null, "SELECT name FROM sqlite_master WHERE type = 'table'", { cursor ->
        while (cursor.next().value) {
            cursor.getString(0)?.let { names += it }
        }
        app.cash.sqldelight.db.QueryResult.Value(Unit)
    }, 0, null)
    return names
}

/**
 * 书架迁移三项（沿用 `HistoryMigrationTest` 范式：文件库 + 真实 `openExtensionSession`）：
 * 升级（老库变空书架、进度原样保留、默认分类播种一次）、二次启动（无 DDL 报错、行保留）、
 * 回滚（迁移事务任一步失败则新条目/关联/进度复制全回滚，旧条目保留）。
 */
class LibraryMigrationTest {
    @Test
    fun legacyV3DatabaseUpgradesToEmptyLibrary() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-library-upgrade-${Uuid.random()}"
        val driver = createFileBackedDriver(root)
        try {
            execScript(driver, TablesV1)
            execScript(driver, ReadingProgressV2)
            execScript(driver, DisplayColumnsV3)
            driver.execute(null, "INSERT INTO repositories(id, name, url, enabled) VALUES('r', 'Repo', 'https://example.invalid/index.json', 1)", 0, null)
            driver.execute(null, "INSERT INTO extensions(source_key, name, version, file_name, origin_kind, origin_repository_id, origin_repository_name, origin_url) VALUES('example_source', 'Example', '1.0.0', 'example_source@1.0.0.js', 'repository', 'r', 'Repo', 'https://example.invalid/example.js')", 0, null)
            driver.execute(null, "INSERT INTO reading_progress(source_key, work_id, chapter_id, page_key, page_index) VALUES('example_source', 'demo', 'ep_1', 'https://example.invalid/2.png', 2)", 0, null)

            val session = openExtensionSession(driver, FileSystem.SYSTEM, root / "app", quietClient(), readingEnv)
            try {
                // 四表结构落地（老四表原样保留）。
                val names = tableNames(driver)
                assertTrue(
                    names.containsAll(
                        setOf(
                            "repositories", "origins", "extensions", "reading_progress",
                            "library_manga", "library_category", "library_manga_category", "library_prefs",
                        ),
                    ),
                )
                assertEquals(
                    setOf("source_key", "kind", "work_id", "title", "cover_url", "source_name", "added_at", "last_update_check"),
                    tableColumns(driver, "library_manga"),
                )
                assertEquals(
                    setOf("id", "name", "sort_order", "hidden", "is_default"),
                    tableColumns(driver, "library_category"),
                )
                assertEquals(
                    setOf("source_key", "kind", "work_id", "category_id"),
                    tableColumns(driver, "library_manga_category"),
                )
                assertEquals(setOf("pref_key", "pref_value"), tableColumns(driver, "library_prefs"))

                // 老库升级后书架为空，reading_progress 原样保留。
                assertTrue(session.library.listEntries().isEmpty())
                assertNull(session.library.getPref("display.mode"))
                val progress = ExtensionDb(driver).extensionDbQueries
                    .selectProgress("example_source", "demo", "ep_1").executeAsOne()
                assertEquals(2L, progress.page_index)

                // 默认分类仅播种一次。
                val categories = session.library.listCategories()
                assertEquals(1, categories.size)
                assertEquals("默认", categories.single().name)
                assertTrue(categories.single().isDefault)
            } finally {
                session.close()
            }
        } finally {
            driver.close()
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    @Test
    fun secondStartKeepsLibraryWithoutDdlErrors() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-library-reopen-${Uuid.random()}"
        val appDir = root / "app"
        val first = createFileBackedDriver(root)
        try {
            val session = openExtensionSession(first, FileSystem.SYSTEM, appDir, quietClient(), readingEnv)
            try {
                session.library.addToLibrary("s1", WorkKind.COMIC, "w1", "收藏作品", "", "源一", 1000L)
                session.library.setPref("display.mode", "compactGrid")
                session.history.save("s1", "w1", "ep_1", "p0", 0, 111L, "收藏作品", "第一话")
            } finally {
                session.close()
            }

            // 同版二次启动：无 DDL 报错，行与偏好保留，默认分类仍唯一。
            val second = createFileBackedDriver(root)
            try {
                val reopened = openExtensionSession(second, FileSystem.SYSTEM, appDir, quietClient(), readingEnv)
                try {
                    assertEquals(1, reopened.library.listEntries().size)
                    assertEquals("compactGrid", reopened.library.getPref("display.mode"))
                    assertEquals(1, reopened.library.listCategories().count { it.isDefault })
                    assertEquals(0, reopened.history.latestEntries().single().pageIndex)
                } finally {
                    reopened.close()
                }
            } finally {
                second.close()
            }
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    @Test
    fun failedSourceMigrationRollsBackEverything() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-library-rollback-${Uuid.random()}"
        val driver = createFileBackedDriver(root)
        try {
            val session = openExtensionSession(driver, FileSystem.SYSTEM, root / "app", quietClient(), readingEnv)
            try {
                val library = session.library
                val ongoing = library.createCategory("连载")
                library.addToLibrary("old_key", WorkKind.COMIC, "m1", "迁移作品", "", "旧源", 1000L)
                library.setWorkCategories("old_key", WorkKind.COMIC, "m1", listOf(ongoing.id))
                session.history.save("old_key", "m1", "ep_1", "p0", 3, 200L, "迁移作品", "第一话")
                ExtensionDb(driver).extensionDbQueries
                    .upsertProgress("old_key", "m1", "ep_2", "q0", 1L)

                driver.execute(null, FailLibraryMigrateTrigger, 0, null)
                assertFailsWith<Exception> {
                    library.migrateSource("old_key", "new_key")
                }

                // 新 key 下无任何残留：条目、归属、进度复制全回滚。
                assertTrue(library.listEntries(LibraryQuery(search = "迁移作品")).single().manga.sourceKey == "old_key")
                assertNull(
                    ExtensionDb(driver).extensionDbQueries
                        .selectLibraryByKey("new_key", "COMIC", "m1").executeAsOneOrNull(),
                )
                assertTrue(
                    ExtensionDb(driver).extensionDbQueries
                        .selectMappingsBySource("new_key").executeAsList().isEmpty(),
                )
                assertTrue(
                    ExtensionDb(driver).extensionDbQueries
                        .selectProgressBySource("new_key").executeAsList().isEmpty(),
                )
                // 旧条目保留：条目、归属、两行进度原样。
                assertEquals(1, library.listEntries(LibraryQuery(search = "迁移作品")).size)
                assertEquals(
                    listOf(ongoing.id),
                    ExtensionDb(driver).extensionDbQueries
                        .selectMappingsForWork("old_key", "COMIC", "m1").executeAsList().map { it.category_id },
                )
                assertEquals(2, ExtensionDb(driver).extensionDbQueries.selectProgressBySource("old_key").executeAsList().size)

                // 移除注入的失败后迁移成功：条目 + 归属并集 + 两行进度全复制，旧行保留。
                driver.execute(null, "DROP TRIGGER fail_library_migrate", 0, null)
                val report = library.migrateSource("old_key", "new_key")
                assertEquals(1, report.copiedEntries)
                assertEquals(1, report.copiedMappings)
                assertEquals(2, report.copiedProgressRows)
                assertEquals(0, report.mergedProgressRows)
                assertEquals(2, ExtensionDb(driver).extensionDbQueries.selectProgressBySource("new_key").executeAsList().size)
                assertEquals(2, ExtensionDb(driver).extensionDbQueries.selectProgressBySource("old_key").executeAsList().size)
            } finally {
                session.close()
            }
        } finally {
            driver.close()
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    @Test
    fun mergeEntryUnionsCategoriesAndKeepsNewerProgress() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "reader-library-merge-${Uuid.random()}"
        val driver = createFileBackedDriver(root)
        try {
            val session = openExtensionSession(driver, FileSystem.SYSTEM, root / "app", quietClient(), readingEnv)
            try {
                val library = session.library
                val cat1 = library.createCategory("分类一")
                val cat2 = library.createCategory("分类二")
                library.addToLibrary("s1", WorkKind.COMIC, "wA", "作品A", "", "源一", 1000L)
                library.setWorkCategories("s1", WorkKind.COMIC, "wA", listOf(cat1.id))
                library.addToLibrary("s2", WorkKind.COMIC, "wB", "作品B", "", "源二", 2000L)
                library.setWorkCategories("s2", WorkKind.COMIC, "wB", listOf(cat2.id))

                session.history.save("s1", "wA", "ch1", "p5", 5, 200L, "作品A", "1")
                session.history.save("s1", "wA", "ch2", "p1", 1, 100L, "作品A", "2")
                session.history.save("s2", "wB", "ch1", "p2", 2, 100L, "作品B", "1")
                session.history.save("s2", "wB", "ch3", "p0", 0, 300L, "作品B", "3")

                val report = library.mergeEntry(
                    Triple("s1", WorkKind.COMIC, "wA"),
                    Triple("s2", WorkKind.COMIC, "wB"),
                )
                assertEquals(0, report.copiedEntries)
                assertEquals(1, report.copiedMappings)
                assertEquals(1, report.copiedProgressRows)
                assertEquals(1, report.mergedProgressRows)

                // 分类归属取并集。
                val merged = library.listEntries(LibraryQuery(search = "作品B")).single()
                assertEquals(setOf(cat1.id, cat2.id), merged.categoryIds.toSet())
                // 同章节以较新者为准：ch1 取 A 的 page 5；ch2 追加复制；ch3 不动。
                val db = ExtensionDb(driver)
                assertEquals(5, db.extensionDbQueries.selectProgress("s2", "wB", "ch1").executeAsOne().page_index)
                assertEquals(1, db.extensionDbQueries.selectProgress("s2", "wB", "ch2").executeAsOne().page_index)
                assertEquals(0, db.extensionDbQueries.selectProgress("s2", "wB", "ch3").executeAsOne().page_index)
                // 旧条目保留。
                assertEquals(1, library.listEntries(LibraryQuery(search = "作品A")).size)
            } finally {
                session.close()
            }
        } finally {
            driver.close()
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    @Test
    fun veneraExampleFixtureCopyStaysReadOnlyReference() {
        // 上游 `reference/.../doc/examples/minimal_source.js` 只读引用：
        // 各模块 jvmTest/resources/venera 下改名存放本地副本（不得修改上游），
        // 此处断言副本存在且与上游稳定 key 一致。
        val url = checkNotNull(javaClass.getResource("/venera/library_minimal_source.js")) {
            "Missing renamed local copy shared/data/src/jvmTest/resources/venera/library_minimal_source.js"
        }
        val text = url.readText()
        assertTrue(text.contains("example_source"))
        assertTrue(text.contains("class ExampleSource extends ComicSource"))
    }

    private fun createFileBackedDriver(root: Path): JdbcSqliteDriver {
        FileSystem.SYSTEM.createDirectories(root)
        return JdbcSqliteDriver("jdbc:sqlite:${root / "extensions.db"}")
    }

    private companion object {
        const val FailLibraryMigrateTrigger = """
            CREATE TRIGGER fail_library_migrate BEFORE INSERT ON library_manga
            BEGIN SELECT RAISE(ABORT, 'simulated migration failure'); END
        """
    }
}
