package dev.reader.data.history

import dev.reader.data.extension.ExtensionDb
import dev.reader.data.extension.createTestDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * History storage contract: reading_progress stays the single source of truth for
 * chapter and page. [HistoryStore.save] commits position and display metadata in one
 * statement, while the pre-existing [ExtensionDb.extensionDbQueries] upsert remains
 * the position-only writer for callers without a history store.
 */
class HistoryStoreTest {
    private fun save(
        db: ExtensionDb,
        sourceKey: String,
        workId: String,
        chapterId: String,
        pageKey: String = "p0",
        pageIndex: Int = 0,
    ) {
        db.extensionDbQueries.upsertProgress(sourceKey, workId, chapterId, pageKey, pageIndex.toLong())
    }

    @Test
    fun freshDatabaseHasNoHistory() = runTest {
        val driver = createTestDriver()
        try {
            assertTrue(HistoryStore(driver).latestEntries().isEmpty())
        } finally {
            driver.close()
        }
    }

    @Test
    fun latestRowPerWorkIsReturnedNewestFirst() = runTest {
        val driver = createTestDriver()
        try {
            val db = ExtensionDb(driver)
            val store = HistoryStore(driver)
            // Two chapters of the same work: only the latest save shows.
            save(db, "s", "w", "ep_1")
            save(db, "s", "w", "ep_2")
            save(db, "s", "other", "")

            val entries = store.latestEntries()
            assertEquals(listOf("other", "w"), entries.map { it.workId })
            assertEquals("ep_2", entries.single { it.workId == "w" }.chapterId)
            // Pre-metadata saves carry no reading time and keep the raw ids.
            assertTrue(entries.all { it.readAtMillis == null })
            assertTrue(entries.all { it.workTitle == null })
        } finally {
            driver.close()
        }
    }

    @Test
    fun stampedEntriesSortBeforeUnknownOnes() = runTest {
        val driver = createTestDriver()
        try {
            val db = ExtensionDb(driver)
            val store = HistoryStore(driver)
            save(db, "s", "old", "ep_1")
            store.save(
                sourceKey = "s",
                workId = "new",
                chapterId = "ep_1",
                pageKey = "p0",
                pageIndex = 0,
                readAtMillis = 1_700_000_000_000,
                workTitle = "New",
                chapterTitle = "Ch 1",
            )

            val entries = store.latestEntries()
            assertEquals(listOf("new", "old"), entries.map { it.workId })
            val stamped = entries.first()
            assertEquals(1_700_000_000_000, stamped.readAtMillis)
            assertEquals("New", stamped.workTitle)
            assertEquals("Ch 1", stamped.chapterTitle)
            assertNull(entries.last().readAtMillis)
        } finally {
            driver.close()
        }
    }

    @Test
    fun sameWorkIdInDifferentSourcesStaysIndependent() = runTest {
        val driver = createTestDriver()
        try {
            val db = ExtensionDb(driver)
            val store = HistoryStore(driver)
            save(db, "s1", "demo", "", pageIndex = 0)
            save(db, "s2", "demo", "", pageIndex = 3)

            val entries = store.latestEntries()
            assertEquals(2, entries.size)
            assertEquals(setOf("s1", "s2"), entries.map { it.sourceKey }.toSet())
            // The most recently saved row comes first.
            assertEquals("s2", entries.first().sourceKey)
            assertEquals(3, entries.first().pageIndex)
        } finally {
            driver.close()
        }
    }

    @Test
    fun chapterlessProgressKeepsNullChapterId() = runTest {
        val driver = createTestDriver()
        try {
            val db = ExtensionDb(driver)
            val store = HistoryStore(driver)
            save(db, "s", "single", "")

            val entry = store.latestEntries().single()
            assertNull(entry.chapterId)
            assertEquals("p0", entry.pageKey)
        } finally {
            driver.close()
        }
    }

    @Test
    fun existingUpsertKeepsWorkingAfterMigration() = runTest {
        val driver = createTestDriver()
        try {
            val db = ExtensionDb(driver)
            // The pre-existing write path is untouched: same call, same row.
            save(db, "s", "w", "ep_9", pageKey = "img", pageIndex = 4)
            val row = db.extensionDbQueries
                .selectProgress("s", "w", "ep_9").executeAsOne()
            assertEquals("img", row.page_key)
            assertEquals(4L, row.page_index)
            // New columns default to NULL for writer paths that predate them.
            assertNull(row.read_at)
            assertNull(row.work_title)
            assertNull(row.chapter_title)
        } finally {
            driver.close()
        }
    }

    @Test
    fun saveCommitsPositionAndMetadataTogether() = runTest {
        val driver = createTestDriver()
        try {
            val db = ExtensionDb(driver)
            val store = HistoryStore(driver)
            store.save("s", "w", "ep_2", pageKey = "p2", pageIndex = 1, readAtMillis = 111, workTitle = "Original Title", chapterTitle = "Second")
            store.save("s", "w", "ep_2", pageKey = "p3", pageIndex = 2, readAtMillis = 222, workTitle = "New Title", chapterTitle = "Second")

            val row = db.extensionDbQueries.selectProgress("s", "w", "ep_2").executeAsOne()
            assertEquals("p3", row.page_key)
            assertEquals(2L, row.page_index)
            assertEquals(222L, row.read_at)
            assertEquals("New Title", row.work_title)
            assertEquals("Second", row.chapter_title)

            val entry = store.latestEntries().single()
            assertEquals("p3", entry.pageKey)
            assertEquals(2, entry.pageIndex)
            assertEquals("New Title", entry.workTitle)
            assertEquals(222L, entry.readAtMillis)
        } finally {
            driver.close()
        }
    }

    @Test
    fun failedSaveKeepsPreviousPageTitleAndTime() = runTest {
        val driver = createTestDriver()
        try {
            val db = ExtensionDb(driver)
            val store = HistoryStore(driver)
            store.save("s", "w", "ep_2", pageKey = "p2", pageIndex = 1, readAtMillis = 111, workTitle = "Original Title", chapterTitle = "Second")
            // Inject the failure on the actual write path: every save now aborts before
            // the row is inserted or replaced.
            driver.execute(null, FailSaveTrigger, 0, null)

            assertFailsWith<Throwable> {
                store.save("s", "w", "ep_2", pageKey = "p3", pageIndex = 2, readAtMillis = 222, workTitle = "New Title", chapterTitle = "Second")
            }

            val row = db.extensionDbQueries.selectProgress("s", "w", "ep_2").executeAsOne()
            assertEquals("p2", row.page_key)
            assertEquals(1L, row.page_index)
            assertEquals(111L, row.read_at)
            assertEquals("Original Title", row.work_title)
            assertEquals("Second", row.chapter_title)

            val entry = store.latestEntries().single()
            assertEquals("p2", entry.pageKey)
            assertEquals(1, entry.pageIndex)
            assertEquals("Original Title", entry.workTitle)
            assertEquals(111L, entry.readAtMillis)

            // The same save succeeds once the injected failure is removed, proving the
            // failure came from the trigger and not from the arguments.
            driver.execute(null, "DROP TRIGGER fail_save", 0, null)
            store.save("s", "w", "ep_2", pageKey = "p3", pageIndex = 2, readAtMillis = 222, workTitle = "New Title", chapterTitle = "Second")
            val committed = db.extensionDbQueries.selectProgress("s", "w", "ep_2").executeAsOne()
            assertEquals("p3", committed.page_key)
            assertEquals("New Title", committed.work_title)
            assertEquals(222L, committed.read_at)
        } finally {
            driver.close()
        }
    }

    @Test
    fun failedFirstSaveLeavesNoHalfWrittenRow() = runTest {
        val driver = createTestDriver()
        try {
            val store = HistoryStore(driver)
            driver.execute(null, FailSaveTrigger, 0, null)

            assertFailsWith<Throwable> {
                store.save("s", "new-work", "ep_1", pageKey = "p1", pageIndex = 0, readAtMillis = 111, workTitle = "Title", chapterTitle = "One")
            }

            assertTrue(store.latestEntries().isEmpty())
            assertNull(
                ExtensionDb(driver).extensionDbQueries
                    .selectProgress("s", "new-work", "ep_1").executeAsOneOrNull(),
            )
        } finally {
            driver.close()
        }
    }

    private companion object {
        const val FailSaveTrigger = """
            CREATE TRIGGER fail_save BEFORE INSERT ON reading_progress
            BEGIN SELECT RAISE(ABORT, 'simulated metadata failure'); END
        """
    }
}
