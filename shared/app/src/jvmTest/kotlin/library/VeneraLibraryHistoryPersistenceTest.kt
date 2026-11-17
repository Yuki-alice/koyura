package app.koyura.app.library

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.v2.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.koyura.app.history.VeneraHistoryService
import app.koyura.app.reader.VeneraReaderService
import app.koyura.data.session.ExtensionSession
import app.koyura.data.session.openExtensionSession
import app.koyura.designsystem.KomikkuCoverImageTag
import app.koyura.designsystem.KomikkuCoverPlaceholderTag
import app.koyura.designsystem.ReaderTheme
import app.koyura.domain.library.WorkKind
import app.koyura.features.library.LibraryDisplayMode
import app.koyura.features.library.LibraryScreen
import app.koyura.features.library.LibrarySession
import app.koyura.features.reader.comic.decodeToImageBitmap
import app.koyura.network.applyReaderDefaults
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * 历史持久化与旧库升级：独立资料目录，关闭重开会话模拟重启。
 *
 * - 单条删除 / 清空 / 收藏心直写 SQLite，重启后仍生效；书架收藏、分类与
 *   章节进度独立存放，清空后仍可从书架续读，重新阅读后再次出现在历史。
 * - 旧库（扩展表 + 进度表，无书架四表）升级：已安装源与原漫画进度保留，
 *   书架四表落地后收藏可用，二次启动行保留。
 * - 失败封面经生产栈（真实会话 + 共用封面组件）回退占位，不抛到组合外。
 */
class VeneraLibraryHistoryPersistenceTest {
    @Test
    fun historyDeleteClearAndFavoriteSurviveRestart() {
        val profile = Files.createTempDirectory("library-history-persistence").toFile()
        try {
            var session = openProfile(profile)
            try {
                val reader = VeneraReaderService(session.comicReading, session.history)
                val history = VeneraHistoryService(session.history, session.library)
                runBlocking {
                    reader.saveProgress(SourceKey, WorkId, "ep_2", PageKey, 2, WorkTitle, "第三话")
                    reader.saveProgress(SourceKey, OtherWorkId, "c1", "p0", 0, "另一部", "第一话")
                }
                assertEquals(2, runBlocking { history.loadHistory() }.size)

                // 收藏心与书架同一记录。
                runBlocking {
                    history.setFavorite(SourceKey, WorkId, WorkTitle, CoverUrl, "Example Source", true)
                }
                runBlocking { session.close() }
                session = openProfile(profile)
                val reopenedHistory = VeneraHistoryService(session.history, session.library)
                assertEquals(
                    setOf(SourceKey to WorkId),
                    runBlocking { reopenedHistory.favoriteKeys() },
                )
                val withCover = runBlocking { reopenedHistory.loadHistory() }
                    .single { it.workId == WorkId }
                assertEquals(CoverUrl, withCover.cover)

                // 单条删除重启后仍生效。
                runBlocking { reopenedHistory.deleteEntry(SourceKey, OtherWorkId) }
                runBlocking { session.close() }
                session = openProfile(profile)
                val afterDelete = VeneraHistoryService(session.history, session.library)
                assertEquals(listOf(WorkId), runBlocking { afterDelete.loadHistory() }.map { it.workId })

                // 清空后历史为空，但书架收藏仍在；重新阅读后再次出现。
                runBlocking { afterDelete.clearAll() }
                runBlocking { session.close() }
                session = openProfile(profile)
                val afterClear = VeneraHistoryService(session.history, session.library)
                assertTrue(runBlocking { afterClear.loadHistory() }.isEmpty())
                assertEquals(
                    1,
                    runBlocking { session.library.listEntries() }.size,
                    "clearing history must keep the library favorite",
                )
                val reread = VeneraReaderService(session.comicReading, session.history)
                runBlocking {
                    reread.saveProgress(SourceKey, WorkId, "ep_2", PageKey, 2, WorkTitle, "第三话")
                }
                assertEquals(1, runBlocking { afterClear.loadHistory() }.size)
            } finally {
                runBlocking { session.close() }
            }
        } finally {
            profile.deleteRecursively()
        }
    }

    @Test
    fun legacyDatabaseUpgradeKeepsSourcesAndProgress() {
        val profile = Files.createTempDirectory("library-legacy-upgrade").toFile()
        try {
            val dbFile = File(profile, "extensions.db").absolutePath
            val legacy = JdbcSqliteDriver("jdbc:sqlite:$dbFile")
            try {
                execScript(legacy, TablesV1)
                execScript(legacy, ReadingProgressV2)
                execScript(legacy, DisplayColumnsV3)
                legacy.execute(
                    null,
                    "INSERT INTO repositories(id, name, url, enabled) VALUES('r', 'Repo', 'https://example.invalid/index.json', 1)",
                    0, null,
                )
                legacy.execute(
                    null,
                    "INSERT INTO extensions(source_key, name, version, file_name, origin_kind, origin_repository_id, origin_repository_name, origin_url) VALUES('example_source', 'Example', '1.0.0', 'example_source@1.0.0.js', 'repository', 'r', 'Repo', 'https://example.invalid/example.js')",
                    0, null,
                )
                legacy.execute(
                    null,
                    "INSERT INTO reading_progress(source_key, work_id, chapter_id, page_key, page_index, read_at, work_title, chapter_title) VALUES('example_source', 'demo', 'ep_1', 'https://example.invalid/2.png', 2, 111, '星图', '第一话')",
                    0, null,
                )
            } finally {
                legacy.close()
            }

            var session = openProfile(profile)
            try {
                // 已安装源与仓库行保留（缺脚本只记恢复失败，不删行）。
                assertEquals(
                    "https://example.invalid/index.json",
                    session.service.listRepositories().single().url,
                )
                assertEquals(
                    "example_source",
                    session.service.listInstalled().single().sourceKey,
                )
                // 原漫画进度保留。
                val progress = runBlocking {
                    session.comicReading.readLatestProgress(SourceKey, WorkId)
                }
                assertEquals("ep_1", progress?.chapterId)
                assertEquals(2, progress?.pageIndex)
                // 书架四表落地后收藏可用。
                runBlocking {
                    session.library.addToLibrary(
                        SourceKey, WorkKind.COMIC, WorkId, WorkTitle, CoverUrl, "Example Source",
                        addedAtMillis = 1000L,
                    )
                }
                runBlocking { session.close() }
                // 二次启动行保留。
                session = openProfile(profile)
                assertEquals(1, runBlocking { session.library.listEntries() }.size)
                assertEquals(
                    "ep_1",
                    runBlocking { session.comicReading.readLatestProgress(SourceKey, WorkId) }?.chapterId,
                )
            } finally {
                runBlocking { session.close() }
            }
        } finally {
            profile.deleteRecursively()
        }
    }

    @Test
    fun failedCoverFallsBackToPlaceholderThroughProductionStack() {
        val profile = Files.createTempDirectory("library-cover-failure").toFile()
        try {
            val session = openProfile(profile)
            try {
                runBlocking {
                    session.library.addToLibrary(
                        SourceKey, WorkKind.COMIC, WorkId, WorkTitle, CoverUrl, "Example Source",
                        addedAtMillis = 1000L,
                    )
                }
                val repository = ProductionLibraryRepository(
                    store = session.library,
                    history = session.history,
                    thumbnails = { _, _ -> throw IllegalStateException("simulated thumbnail failure") },
                )
                val librarySession = LibrarySession(repository)
                runComposeUiTest {
                    setContent {
                        ReaderTheme {
                            LibraryScreen(
                                session = librarySession,
                                decodeCover = { bytes ->
                                    runCatching { bytes.decodeToImageBitmap() }.getOrNull()
                                },
                                onOpenWork = { _, _, _ -> },
                                onContinue = {},
                            )
                        }
                    }
                    waitUntil(timeoutMillis = 15_000) {
                        librarySession.snapshot.entries.isNotEmpty() &&
                            librarySession.covers.isNotEmpty()
                    }
                    // 卡片自身的单次加载周期随后才收敛：等占位出现再断言。
                    try {
                        waitUntil(timeoutMillis = 15_000) {
                            onAllNodesWithTag(KomikkuCoverPlaceholderTag, useUnmergedTree = true)
                                .fetchSemanticsNodes().isNotEmpty()
                        }
                    } catch (e: Throwable) {
                        onRoot(useUnmergedTree = true).printToLog("COVER-FAILURE-TREE")
                        throw e
                    }
                    onAllNodesWithTag(KomikkuCoverImageTag, useUnmergedTree = true).assertCountEquals(0)
                }

                // 偏好经生产栈落盘，同库新实例读回一致。
                runBlocking {
                    librarySession.updatePrefs { it.copy(displayMode = LibraryDisplayMode.LIST, columns = 3) }
                }
                val reloaded = ProductionLibraryRepository(
                    store = session.library,
                    history = session.history,
                    thumbnails = { _, _ -> byteArrayOf() },
                )
                val prefs = runBlocking { reloaded.load() }.prefs
                assertEquals(LibraryDisplayMode.LIST, prefs.displayMode)
                assertEquals(3, prefs.columns)
            } finally {
                runBlocking { session.close() }
            }
        } finally {
            profile.deleteRecursively()
        }
    }

    private fun openProfile(profile: File): ExtensionSession = runBlocking {
        openExtensionSession(
            JdbcSqliteDriver("jdbc:sqlite:${File(profile, "extensions.db").absolutePath}"),
            FileSystem.SYSTEM,
            profile.toOkioPath(),
            HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }) { applyReaderDefaults() },
            VeneraEnvironment("1.16.0", "zh_CN", "test"),
        )
    }

    private fun execScript(driver: JdbcSqliteDriver, script: String) {
        script.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { statement ->
            driver.execute(null, "$statement;", 0, null)
        }
    }

    private companion object {
        const val SourceKey = "example_source"
        const val WorkId = "demo"
        const val OtherWorkId = "other"
        const val WorkTitle = "星图"
        const val CoverUrl = "https://example.invalid/cover.jpg"
        const val PageKey = "https://example.invalid/images/3.png"

        const val TablesV1 = """
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
        const val ReadingProgressV2 = """
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
        const val DisplayColumnsV3 = """
ALTER TABLE reading_progress ADD COLUMN read_at INTEGER;
ALTER TABLE reading_progress ADD COLUMN work_title TEXT;
ALTER TABLE reading_progress ADD COLUMN chapter_title TEXT;
"""
    }
}
