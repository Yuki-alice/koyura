package app.koyura.data.library

import app.cash.sqldelight.db.SqlDriver
import app.koyura.data.extension.ExtensionDb

/**
 * 书架表的文件级幂等保障（与 `4.sqm` 同 DDL）。
 *
 * 生成的 `Schema` 代码从不写 `PRAGMA user_version`，老库升级靠结构探测而非版本号
 * （沿用 `ExtensionSession.ensureSchema` 与 `HistoryMigrationTest` 的既有范式），
 * 所以已存在的 reading 时代数据库不会自动执行 `.sqm`：此处用
 * `CREATE TABLE / INDEX IF NOT EXISTS` 补齐四表，再用单条原子语句按
 * “表空时插入一次”播种默认分类。同版二次启动无 DDL 报错，老库升级后书架为空
 * 且 `reading_progress` 原样保留。
 */
internal fun ensureLibrarySchema(driver: SqlDriver) {
    driver.execute(null, LibraryMangaDdl, 0, null)
    driver.execute(null, LibraryMangaTitleIdxDdl, 0, null)
    driver.execute(null, LibraryCategoryDdl, 0, null)
    driver.execute(null, LibraryMangaCategoryDdl, 0, null)
    driver.execute(null, LibraryMangaCategoryCatIdxDdl, 0, null)
    driver.execute(null, LibraryPrefsDdl, 0, null)
    // 与 ExtensionDbQueries.seedDefaultCategory 同语义的原子播种（表空才插入）。
    ExtensionDb(driver).extensionDbQueries.seedDefaultCategory()
}

private const val LibraryMangaDdl = """
CREATE TABLE IF NOT EXISTS library_manga(
  source_key TEXT NOT NULL,
  kind TEXT NOT NULL,
  work_id TEXT NOT NULL,
  title TEXT NOT NULL DEFAULT '',
  cover_url TEXT NOT NULL DEFAULT '',
  source_name TEXT,
  added_at INTEGER NOT NULL DEFAULT 0,
  last_update_check INTEGER,
  PRIMARY KEY (source_key, kind, work_id)
)
"""

private const val LibraryMangaTitleIdxDdl =
    "CREATE INDEX IF NOT EXISTS library_manga_title_idx ON library_manga(title)"

private const val LibraryCategoryDdl = """
CREATE TABLE IF NOT EXISTS library_category(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  hidden INTEGER NOT NULL DEFAULT 0,
  is_default INTEGER NOT NULL DEFAULT 0
)
"""

private const val LibraryMangaCategoryDdl = """
CREATE TABLE IF NOT EXISTS library_manga_category(
  source_key TEXT NOT NULL,
  kind TEXT NOT NULL,
  work_id TEXT NOT NULL,
  category_id INTEGER NOT NULL,
  PRIMARY KEY (source_key, kind, work_id, category_id)
)
"""

private const val LibraryMangaCategoryCatIdxDdl =
    "CREATE INDEX IF NOT EXISTS library_manga_category_cat_idx ON library_manga_category(category_id)"

private const val LibraryPrefsDdl = """
CREATE TABLE IF NOT EXISTS library_prefs(
  pref_key TEXT PRIMARY KEY,
  pref_value TEXT NOT NULL
)
"""
