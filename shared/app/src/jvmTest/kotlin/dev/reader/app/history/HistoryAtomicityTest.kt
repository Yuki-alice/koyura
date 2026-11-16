package dev.reader.app.history

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.reader.app.reader.VeneraReaderService
import dev.reader.data.extension.ExtensionDb
import dev.reader.data.session.openExtensionSession
import dev.reader.network.applyReaderDefaults
import dev.reader.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * Saving a reading position is one atomic write of page key, page index, read time and both
 * display titles. This reproduces the review probe with the production [VeneraReaderService] and
 * real SQLite: the first save commits p2 with its title, an injected failure aborts the second
 * save, and the database must still hold the first row for both the detail continue path and the
 * history list.
 *
 * The failure is injected with a `BEFORE INSERT` trigger, which sits on the repaired write path
 * (a single `INSERT OR REPLACE`). The original probe's `BEFORE UPDATE OF read_at` trigger only
 * covered the old two-statement implementation.
 */
class HistoryAtomicityTest {
    @Test
    fun failedSaveKeepsPreviousPageTitleAndTime() {
        val dir = Files.createTempDirectory("history-atomicity").toFile()
        try {
            runBlocking {
                val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
                val session = openExtensionSession(
                    driver,
                    FileSystem.SYSTEM,
                    dir.toOkioPath(),
                    HttpClient(MockEngine { respondOk() }) { applyReaderDefaults() },
                    VeneraEnvironment("1.16.0", "en", "test"),
                )
                try {
                    val reader = VeneraReaderService(session.comicReading, session.history)
                    reader.saveProgress(SourceKey, WorkId, ChapterId, "p2", 1, "Original Title", "Second")
                    val committed = row(driver)
                    assertEquals("p2", committed.page_key)
                    assertEquals("Original Title", committed.work_title)
                    assertNotNull(committed.read_at)

                    driver.execute(null, FailSaveTrigger, 0, null)
                    val failure = assertFailsWith<Throwable> {
                        reader.saveProgress(SourceKey, WorkId, ChapterId, "p3", 2, "New Title", "Second")
                    }
                    // The trigger is what aborted the write, not a validation error in the adapter.
                    assertTrue(
                        failure.message?.contains("simulated metadata failure") == true,
                        "expected the injected failure, got: ${failure.message}",
                    )

                    val kept = row(driver)
                    assertEquals("p2", kept.page_key)
                    assertEquals(1L, kept.page_index)
                    assertEquals("Original Title", kept.work_title)
                    assertEquals("Second", kept.chapter_title)
                    assertEquals(committed.read_at, kept.read_at)

                    // Detail continue reads the same preserved row.
                    val latest = assertNotNull(session.comicReading.readLatestProgress(SourceKey, WorkId))
                    assertEquals(ChapterId, latest.chapterId)
                    assertEquals("p2", latest.pageKey)
                    assertEquals(1, latest.pageIndex)

                    // History shows the old page and title without inventing new metadata.
                    val entry = VeneraHistoryService(session.history).loadHistory().single()
                    assertEquals("Original Title", entry.workTitle)
                    assertEquals("Second", entry.chapterTitle)
                    assertEquals("p2", entry.pageKey)
                    assertEquals(1, entry.pageIndex)
                    assertEquals(committed.read_at, entry.readAtMillis)

                    // Once the injected failure is removed the same call commits, proving the
                    // abort came from the trigger.
                    driver.execute(null, "DROP TRIGGER fail_save", 0, null)
                    reader.saveProgress(SourceKey, WorkId, ChapterId, "p3", 2, "New Title", "Second")
                    val updated = row(driver)
                    assertEquals("p3", updated.page_key)
                    assertEquals(2L, updated.page_index)
                    assertEquals("New Title", updated.work_title)
                    assertTrue(updated.read_at!! >= committed.read_at!!)
                } finally {
                    session.close()
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun failedFirstSaveLeavesNoRowBehind() {
        val dir = Files.createTempDirectory("history-atomicity-first").toFile()
        try {
            runBlocking {
                val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
                val session = openExtensionSession(
                    driver,
                    FileSystem.SYSTEM,
                    dir.toOkioPath(),
                    HttpClient(MockEngine { respondOk() }) { applyReaderDefaults() },
                    VeneraEnvironment("1.16.0", "en", "test"),
                )
                try {
                    val reader = VeneraReaderService(session.comicReading, session.history)
                    driver.execute(null, FailSaveTrigger, 0, null)

                    assertFailsWith<Throwable> {
                        reader.saveProgress(SourceKey, WorkId, ChapterId, "p1", 0, "Title", "One")
                    }

                    assertNull(
                        ExtensionDb(driver).extensionDbQueries
                            .selectProgress(SourceKey, WorkId, ChapterId).executeAsOneOrNull(),
                    )
                    assertNull(session.comicReading.readLatestProgress(SourceKey, WorkId))
                    assertTrue(VeneraHistoryService(session.history).loadHistory().isEmpty())
                } finally {
                    session.close()
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun row(driver: JdbcSqliteDriver) =
        ExtensionDb(driver).extensionDbQueries
            .selectProgress(SourceKey, WorkId, ChapterId).executeAsOne()

    private companion object {
        const val SourceKey = "review_source"
        const val WorkId = "work"
        const val ChapterId = "chapter"

        // The repaired writer is a single INSERT OR REPLACE, so aborting INSERT covers
        // the whole atomic write. The old probe's BEFORE UPDATE trigger is obsolete.
        val FailSaveTrigger = """
            CREATE TRIGGER fail_save BEFORE INSERT ON reading_progress
            BEGIN SELECT RAISE(ABORT, 'simulated metadata failure'); END
        """.trimIndent()
    }
}
