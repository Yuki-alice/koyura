package app.koyura.app.updates

import app.koyura.domain.library.LibraryManga
import app.koyura.domain.library.WorkKind
import app.koyura.domain.updates.DetectedChapter
import app.koyura.domain.updates.LibraryUpdates
import app.koyura.domain.updates.UpdateFailure
import app.koyura.domain.updates.UpdateScan
import app.koyura.domain.updates.WorkUpdate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProductionUpdatesRepositoryTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun manga(workId: String) = LibraryManga(
        sourceKey = "example_source",
        kind = WorkKind.COMIC,
        workId = workId,
        title = "Demo $workId",
        coverUrl = "https://example.invalid/cover.jpg",
    )

    private fun work(vararg chapters: DetectedChapter) = WorkUpdate(
        work = manga("demo"),
        checkedAtMillis = now,
        newChapters = chapters.toList(),
    )

    private fun scanner(
        feed: List<WorkUpdate> = emptyList(),
        scan: List<WorkUpdate> = emptyList(),
        failures: List<UpdateFailure> = emptyList(),
    ) = object : LibraryUpdates {
        override suspend fun checkNow() = UpdateScan(scan, failures)
        override suspend fun retryFailures() = UpdateScan(scan, failures)
        override suspend fun feed() = feed
        override suspend fun failures() = failures
    }

    @Test
    fun loadUpdatesMapsFeedFieldsAndRelativeDates() = runTest {
        val repository = ProductionUpdatesRepository(
            updates = scanner(
                feed = listOf(
                    work(
                        DetectedChapter("ch-3", "Chapter 3", now, isRead = false),
                        DetectedChapter("ch-2", "Chapter 2", now - day, isRead = true),
                        DetectedChapter("ch-1", "Chapter 1", now - 3 * day, isRead = false),
                    ),
                ),
            ),
            nowMillis = { now },
        )
        val entries = repository.loadUpdates()
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("example_source", entry.sourceKey)
        assertEquals("demo", entry.workId)
        assertEquals("Demo demo", entry.title)
        assertEquals("https://example.invalid/cover.jpg", entry.coverUrl)
        assertEquals(listOf("Today", "Yesterday", "3 days ago"), entry.chapters.map { it.dateText })
        assertEquals(listOf("ch-3", "ch-2", "ch-1"), entry.chapters.map { it.id })
    }

    @Test
    fun checkAndRetryReturnFreshScanResultsAndKeepFailuresAside() = runTest {
        val scanned = work(DetectedChapter("ch-9", "Chapter 9", now, isRead = false))
        val repository = ProductionUpdatesRepository(
            updates = scanner(
                feed = emptyList(),
                scan = listOf(scanned),
                failures = listOf(UpdateFailure(manga("broken"), now, "source offline")),
            ),
            nowMillis = { now },
        )
        assertEquals(0, repository.loadUpdates().size)
        val checked = repository.checkNow()
        assertEquals(1, checked.size)
        assertEquals("ch-9", checked.single().chapters.single().id)
        assertEquals(1, repository.retryFailures().size)
    }

    @Test
    fun relativeDateClampsFutureAndBoundaries() {
        assertEquals("Today", relativeDate(now, now))
        assertEquals("Today", relativeDate(now, now + day))
        assertEquals("Yesterday", relativeDate(now, now - day))
        assertEquals("2 days ago", relativeDate(now, now - 2 * day))
    }
}
