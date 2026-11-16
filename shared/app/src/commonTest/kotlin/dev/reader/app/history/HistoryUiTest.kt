package dev.reader.app.history

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryUiTest {
    private val today = LocalDate(2026, 11, 17)

    private fun entry(
        source: String = "example_source",
        work: String = "demo",
        chapter: String? = "ep_2",
        readAt: Long? = 1_794_916_800_000, // 2026-11-17T12:00:00Z
    ) = historyEntry(
        sourceKey = source,
        workId = work,
        chapterId = chapter,
        readAtMillis = readAt,
    )

    @Test
    fun groupsByLocalDayNewestFirstWithUnknownAtEnd() {
        val entries = listOf(
            entry(work = "old-unknown", readAt = null),
            entry(work = "yesterday-work", readAt = 1_794_830_400_000), // 2026-11-16T12:00:00Z
            entry(work = "today-work", readAt = 1_794_916_800_000),
            entry(work = "older-work", readAt = 1_794_744_000_000), // 2026-11-15T12:00:00Z
        )

        val sections = entries.groupedByDate(today)

        assertEquals(4, sections.size)
        assertEquals(LocalDate(2026, 11, 17), sections[0].date)
        assertEquals(listOf("today-work"), sections[0].entries.map { it.workId })
        assertEquals(LocalDate(2026, 11, 16), sections[1].date)
        assertEquals(LocalDate(2026, 11, 15), sections[2].date)
        assertEquals(null, sections[3].date)
        assertEquals(listOf("old-unknown"), sections[3].entries.map { it.workId })
    }

    @Test
    fun sameWorkSameDayKeepsRecencyOrder() {
        val first = entry(work = "a", readAt = 1_794_916_800_000)
        val second = entry(work = "b", readAt = 1_794_916_800_001)
        val sections = listOf(first, second).groupedByDate(today)

        assertEquals(1, sections.size)
        assertEquals(listOf("a", "b"), sections.single().entries.map { it.workId })
    }

    @Test
    fun searchMatchesTitleWorkIdSourceAndChapters() {
        val entry = entry()

        assertTrue(entry.matches("星图"))
        assertTrue(entry.matches("demo"))
        assertTrue(entry.matches("example_source"))
        assertTrue(entry.matches("ep_2"))
        assertTrue(entry.matches("第二章"))
        assertTrue(entry.matches(""))
        assertTrue(entry.matches("  "))
        assertFalse(entry.matches("zzz-no-such-work"))
    }

    @Test
    fun searchFallsBackToIdsWhenTitleMissing() {
        val entry = entry(work = "demo-9", chapter = "ch-7").copy(workTitle = null, chapterTitle = null)

        assertTrue(entry.matches("demo-9"))
        assertTrue(entry.matches("ch-7"))
        assertEquals("demo-9", entry.displayTitle)
    }

    @Test
    fun displayTitlePrefersWorkTitle() {
        assertEquals("星图", entry().displayTitle)
        assertEquals("demo", entry().copy(workTitle = "").displayTitle)
        assertEquals("demo", entry().copy(workTitle = null).displayTitle)
    }
}
