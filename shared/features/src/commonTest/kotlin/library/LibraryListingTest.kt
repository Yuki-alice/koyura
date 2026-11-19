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
    fun adaptiveGridColumnsFollowCanvasWidth() {
        // 白皮书 07:56：360dp 画布 2 列量级、1280dp 画布 9 列量级（内边距 8dp、间距 4dp）。
        assertEquals(2, adaptiveGridColumns(360))
        assertEquals(9, adaptiveGridColumns(1280))
        assertEquals(1, adaptiveGridColumns(100))
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

    @Test
    fun groupBySourceKeepsCaseDifferingNames() {
        // 只有大小写不同的来源名称：分组不断言合并，只断言两组并存、作品无丢失、
        // 大小写无关有序（iOS 无 toSortedMap，同样走本实现）。
        val entries = listOf(
            LibraryEntry(sourceKey = "s1", workId = "a", title = "甲", sourceName = "Example"),
            LibraryEntry(sourceKey = "s2", workId = "b", title = "乙", sourceName = "EXAMPLE"),
            LibraryEntry(sourceKey = "s3", workId = "c", title = "丙", sourceName = "beta"),
        )
        val sections = groupLibraryEntries(entries, LibraryGroupBy.SOURCE)
        assertEquals(3, sections.sumOf { it.entries.size }, "no work may be lost in grouping")
        assertEquals(listOf("beta", "Example", "EXAMPLE"), sections.map { it.title })
        assertEquals(listOf("c"), sections.first { it.title == "beta" }.entries.map { it.workId })
    }

    @Test
    fun groupByStatusKeepsRankOrder() {
        val entries = listOf(
            LibraryEntry(sourceKey = "s", workId = "u", title = "u", status = LibraryStatus.UNKNOWN),
            LibraryEntry(sourceKey = "s", workId = "c", title = "c", status = LibraryStatus.COMPLETED),
            LibraryEntry(sourceKey = "s", workId = "o", title = "o", status = LibraryStatus.ONGOING),
        )
        val sections = groupLibraryEntries(entries, LibraryGroupBy.STATUS)
        assertEquals(
            listOf(LibraryStatus.ONGOING.name, LibraryStatus.COMPLETED.name, LibraryStatus.UNKNOWN.name),
            sections.map { it.title },
        )
        assertEquals(3, sections.sumOf { it.entries.size })
    }
}
