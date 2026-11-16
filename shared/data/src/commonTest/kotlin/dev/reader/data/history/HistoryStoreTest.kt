package dev.reader.data.history

import dev.reader.data.extension.ExtensionDb
import dev.reader.data.extension.createTestDriver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * History storage contract: reading_progress stays the single source of truth for
 * chapter and page, while HistoryStore adds recency ordering and display metadata
 * around it without changing any existing write path (writes below go through the
 * same upsertProgress query the production writer uses).
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
            save(db, "s", "new", "ep_1")
            store.stamp("s", "new", "ep_1", readAtMillis = 1_700_000_000_000, workTitle = "New", chapterTitle = "Ch 1")

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
}
