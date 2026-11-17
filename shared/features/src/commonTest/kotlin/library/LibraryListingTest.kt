package app.koyura.features.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryListingTest {
    private val snapshot = libraryFixtureSnapshot()

    @Test
    fun triStateCyclesOffIncludeExclude() {
        assertEquals(LibraryTriState.INCLUDE, LibraryTriState.DISABLED.next())
        assertEquals(LibraryTriState.EXCLUDE, LibraryTriState.INCLUDE.next())
        assertEquals(LibraryTriState.DISABLED, LibraryTriState.EXCLUDE.next())
    }

    @Test
    fun everySortTypeSeesAllEntries() {
        for (type in LibrarySortType.entries) {
            val ordered = applyLibraryView(
                entries = snapshot.entries,
                links = snapshot.links,
                query = "",
                categoryId = null,
                filters = LibraryFilters(),
                sortType = type,
                ascending = true,
            )
            assertEquals(7, ordered.size, "sort $type")
        }
    }

    @Test
    fun lastReadSortPutsUntouchedLast() {
        val ordered = applyLibraryView(
            entries = snapshot.entries,
            links = snapshot.links,
            query = "",
            categoryId = null,
            filters = LibraryFilters(),
            sortType = LibrarySortType.LAST_READ,
            ascending = false,
        )
        // 最近阅读降序：w2(2000) 第一；从未阅读的 w4/w5/w6/w7 沉底（顺序不做跨语言断言）。
        assertEquals("w2", ordered.first().workId)
        assertEquals(setOf("w4", "w5", "w6", "w7"), ordered.takeLast(4).map { it.workId }.toSet())
    }

    @Test
    fun combinedQueryCategoryAndFilter() {
        val ordered = applyLibraryView(
            entries = snapshot.entries,
            links = snapshot.links,
            query = "双子",
            categoryId = 1L,
            filters = LibraryFilters(lewd = LibraryTriState.EXCLUDE),
            sortType = LibrarySortType.ALPHABETICAL,
            ascending = true,
        )
        assertEquals(listOf("w3"), ordered.map { it.workId })
    }

    @Test
    fun randomSortDeterministicForSeed() {
        fun order(seed: Long) = applyLibraryView(
            entries = snapshot.entries,
            links = snapshot.links,
            query = "",
            categoryId = null,
            filters = LibraryFilters(),
            sortType = LibrarySortType.RANDOM,
            ascending = true,
            randomSeed = seed,
        ).map { it.workId }
        assertEquals(order(7L), order(7L))
        assertEquals(setOf("w1", "w2", "w3", "w4", "w5", "w6", "w7"), order(7L).toSet())
        assertEquals(order(7L).reversed(), applyLibraryView(
            entries = snapshot.entries,
            links = snapshot.links,
            query = "",
            categoryId = null,
            filters = LibraryFilters(),
            sortType = LibrarySortType.RANDOM,
            ascending = false,
            randomSeed = 7L,
        ).map { it.workId })
    }

    @Test
    fun groupBySourceAndStatus() {
        val ordered = applyLibraryView(
            entries = snapshot.entries,
            links = snapshot.links,
            query = "",
            categoryId = null,
            filters = LibraryFilters(),
            sortType = LibrarySortType.ALPHABETICAL,
            ascending = true,
        )
        val bySource = groupLibraryEntries(ordered, LibraryGroupBy.SOURCE)
        assertEquals(listOf("源A", "源B"), bySource.map { it.title })
        assertEquals(5, bySource.first { it.title == "源A" }.entries.size)
        val byStatus = groupLibraryEntries(ordered, LibraryGroupBy.STATUS)
        assertEquals(
            listOf(LibraryStatus.ONGOING.name, LibraryStatus.COMPLETED.name, LibraryStatus.UNKNOWN.name),
            byStatus.map { it.title },
        )
        assertEquals(1, byStatus.first { it.title == LibraryStatus.COMPLETED.name }.entries.size)
        val byCategory = groupLibraryEntries(ordered, LibraryGroupBy.CATEGORY)
        assertEquals(1, byCategory.size)
        assertEquals(7, byCategory.single().entries.size)
    }

    @Test
    fun columnsClampedAndListIsSingle() {
        assertEquals(1, libraryColumnsFor(LibraryDisplayMode.LIST, 4))
        assertEquals(1, libraryColumnsFor(LibraryDisplayMode.COMFORTABLE_GRID_PANORAMA, 4))
        assertEquals(1, libraryColumnsFor(LibraryDisplayMode.COMPACT_GRID, 0))
        assertEquals(6, libraryColumnsFor(LibraryDisplayMode.COMPACT_GRID, 99))
        assertEquals(3, libraryColumnsFor(LibraryDisplayMode.COVER_ONLY_GRID, 3))
        assertEquals(4, libraryColumnsFor(LibraryDisplayMode.COMFORTABLE_GRID, 4))
    }

    @Test
    fun displayModeSerializesLikeUpstream() {
        assertEquals("COMPACT_GRID", LibraryDisplayMode.COMPACT_GRID.serialize())
        assertEquals("COMFORTABLE_GRID", LibraryDisplayMode.COMFORTABLE_GRID.serialize())
        assertEquals("COMFORTABLE_GRID_PANORAMA", LibraryDisplayMode.COMFORTABLE_GRID_PANORAMA.serialize())
        assertEquals("LIST", LibraryDisplayMode.LIST.serialize())
        assertEquals("COVER_ONLY_GRID", LibraryDisplayMode.COVER_ONLY_GRID.serialize())
        assertEquals(LibraryDisplayMode.COMPACT_GRID, LibraryDisplayMode.deserialize("bogus"))
        assertEquals(LibraryDisplayMode.COVER_ONLY_GRID, LibraryDisplayMode.deserialize("COVER_ONLY_GRID"))
    }

    @Test
    fun uncategorizedAndCategoryMembership() {
        assertEquals(listOf("w5"), snapshot.uncategorized().map { it.workId })
        assertTrue(snapshot.categoriesOf(LibraryEntryKey("srcA", LibraryKind.COMIC, "w1")) == setOf(1L))
        assertTrue(snapshot.categoriesOf(LibraryEntryKey("srcA", LibraryKind.COMIC, "w5")).isEmpty())
    }
}
