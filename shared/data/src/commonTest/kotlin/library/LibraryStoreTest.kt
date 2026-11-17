package app.koyura.data.library

import app.koyura.data.extension.createTestDriver
import app.koyura.data.history.HistoryStore
import app.koyura.domain.library.LibraryPrefsKeys
import app.koyura.domain.library.LibraryQuery
import app.koyura.domain.library.LibrarySort
import app.koyura.domain.library.WorkKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 书架仓储契约测试：筛选 / 批量收藏 / 隐藏分类 / 随机排序确定性种子。
 *
 * 数据集见 [LibraryFixture]；源失效（未安装）时收藏仍可见由
 * [sourceGoneEntriesStayVisible] 覆盖（书架行独立于 `extensions` 表，不做源过滤）。
 */
class LibraryStoreTest {
    @Test
    fun duplicateFavoriteReturnsOriginalRow() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            val first = store.addToLibrary("s1", WorkKind.COMIC, "w1", "原标题", "", "源一", 1000L)
            val second = store.addToLibrary("s1", WorkKind.COMIC, "w1", "新标题", "https://example.invalid/n.png", "源一改", 9999L)

            assertEquals(first, second)
            assertEquals("原标题", second.title)
            assertEquals(1000L, second.addedAtMillis)
            assertEquals(1, store.listEntries().size)
        } finally {
            driver.close()
        }
    }

    @Test
    fun novelRowsAreRejectedInMangaRound() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            assertFailsWith<IllegalArgumentException> {
                store.addToLibrary("s1", WorkKind.NOVEL, "n1", "小说", "", null, 1L)
            }
            assertTrue(store.listEntries().isEmpty())
        } finally {
            driver.close()
        }
    }

    @Test
    fun sameTitleAcrossSourcesStaysIndependent() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            LibraryFixture.seed(store)

            val found = store.listEntries(LibraryQuery(search = LibraryFixture.SAME_TITLE))
            assertEquals(2, found.size)
            assertEquals(setOf("s1", "s2"), found.map { it.manga.sourceKey }.toSet())
        } finally {
            driver.close()
        }
    }

    @Test
    fun sameTitleSameSourceDifferentIdsStayIndependent() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            LibraryFixture.seed(store)

            val found = store.listEntries(LibraryQuery(search = LibraryFixture.ONGOING_TITLE))
            assertEquals(setOf("w2", "w3"), found.map { it.manga.workId }.toSet())
        } finally {
            driver.close()
        }
    }

    @Test
    fun uncategorizedWorkIsListedWithEmptyCategories() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            LibraryFixture.seed(store)

            val entry = store.listEntries(LibraryQuery(search = "无分类作品")).single()
            assertTrue(entry.categoryIds.isEmpty())
        } finally {
            driver.close()
        }
    }

    @Test
    fun hiddenCategoryExcludedByDefault() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            val seeded = LibraryFixture.seed(store)

            val visible = store.listEntries().map { it.manga.workId }.toSet()
            assertTrue("w5" !in visible)
            assertTrue("w4" in visible)

            val withHidden = store.listEntries(LibraryQuery(includeHiddenCategories = true))
            assertTrue(withHidden.any { it.manga.workId == "w5" })

            // 显式按隐藏分类过滤时仍可查到该分类作品。
            val onlyHidden = store.listEntries(LibraryQuery(categoryId = seeded.hiddenArchiveId))
            assertEquals(listOf("w5"), onlyHidden.map { it.manga.workId })

            assertTrue(store.listCategories(includeHidden = false).none { it.hidden })
        } finally {
            driver.close()
        }
    }

    @Test
    fun emptyCategoryExistsButHasNoWorks() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            val seeded = LibraryFixture.seed(store)

            assertTrue(store.listCategories().any { it.id == seeded.emptyCategoryId })
            assertTrue(store.listEntries(LibraryQuery(categoryId = seeded.emptyCategoryId)).isEmpty())
        } finally {
            driver.close()
        }
    }

    @Test
    fun categorySortSearchRenameAndBatchAssign() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            val seeded = LibraryFixture.seed(store)

            // 搜索命中源名快照（隐藏分类作品默认被排除，故为 5 而非 6）。
            assertEquals(5, store.listEntries(LibraryQuery(search = "源一")).size)

            // 批量分配：无分类作品与单行本进入“连载”。
            store.batchAssignToCategory(
                listOf(
                    Triple("s1", WorkKind.COMIC, "w4"),
                    Triple("s1", WorkKind.COMIC, "w6"),
                ),
                seeded.ongoingId,
            )
            assertEquals(
                setOf("w1", "w9", "w2", "w3", "w4", "w6"),
                store.listEntries(LibraryQuery(categoryId = seeded.ongoingId)).map { it.manga.workId }.toSet(),
            )

            // 改名与排序。
            store.renameCategory(seeded.ongoingId, "追更")
            store.reorderCategories(listOf(seeded.emptyCategoryId, seeded.ongoingId, seeded.hiddenArchiveId, seeded.defaultId))
            val names = store.listCategories().map { it.name }
            assertEquals(listOf("空分类", "追更", "隐藏存档", "默认"), names)
        } finally {
            driver.close()
        }
    }

    @Test
    fun deleteCategoryKeepsWorksAsUncategorized() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            val seeded = LibraryFixture.seed(store)

            store.deleteCategory(seeded.hiddenArchiveId)

            val entry = store.listEntries(LibraryQuery(search = "隐藏分类作品")).single()
            assertTrue(entry.categoryIds.isEmpty())
            assertTrue(store.listCategories().none { it.id == seeded.hiddenArchiveId })
        } finally {
            driver.close()
        }
    }

    @Test
    fun chapterlessProgressMapsEmptyToNull() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            val history = HistoryStore(driver)
            LibraryFixture.seed(store, history)

            val progress = store.latestProgress("s1", "w6")
            assertEquals(null, progress?.chapterId)
            assertEquals(0, progress?.pageIndex)
            assertEquals("s0", progress?.pageKey)
        } finally {
            driver.close()
        }
    }

    @Test
    fun sourceGoneEntriesStayVisible() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            // 从未安装任何源：书架行独立于 extensions 表。
            store.addToLibrary("ghost_source", WorkKind.COMIC, "w1", "幽灵源作品", "", "已卸载的源", 1L)

            val found = store.listEntries(LibraryQuery(search = "幽灵源"))
            assertEquals(1, found.size)
            assertNull(store.latestProgress("ghost_source", "w1"))
        } finally {
            driver.close()
        }
    }

    @Test
    fun prefsRoundTrip() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)

            store.setPref(LibraryPrefsKeys.DISPLAY_MODE, "compactGrid")
            store.setPref(LibraryPrefsKeys.DISPLAY_COLUMNS, "3")
            assertEquals("compactGrid", store.getPref(LibraryPrefsKeys.DISPLAY_MODE))
            assertEquals(mapOf(LibraryPrefsKeys.DISPLAY_MODE to "compactGrid", LibraryPrefsKeys.DISPLAY_COLUMNS to "3"), store.allPrefs())

            store.deletePref(LibraryPrefsKeys.DISPLAY_COLUMNS)
            assertNull(store.getPref(LibraryPrefsKeys.DISPLAY_COLUMNS))
        } finally {
            driver.close()
        }
    }

    @Test
    fun randomSortIsDeterministicWithSeed() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            LibraryFixture.seed(store)

            val first = store.listEntries(LibraryQuery(sort = LibrarySort.RANDOM, randomSeed = 42L)).map { it.manga.workId }
            val second = store.listEntries(LibraryQuery(sort = LibrarySort.RANDOM, randomSeed = 42L)).map { it.manga.workId }
            assertEquals(first, second)
            // 同一种子给出全集的一个确定性排列（默认隐藏分类作品被排除在外）。
            assertEquals(store.listEntries().map { it.manga.workId }.toSet(), first.toSet())

            val byName = store.listEntries(LibraryQuery(sort = LibrarySort.NAME)).map { it.manga.workId }
            val byNameDesc = store.listEntries(LibraryQuery(sort = LibrarySort.NAME, ascending = false)).map { it.manga.workId }
            assertEquals(byName.reversed(), byNameDesc)

            val byAdded = store.listEntries(LibraryQuery(sort = LibrarySort.DATE_ADDED)).map { it.manga.workId }
            assertEquals("w1", byAdded.first())
        } finally {
            driver.close()
        }
    }

    @Test
    fun refreshSnapshotKeepsAddedAt() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            store.addToLibrary("s1", WorkKind.COMIC, "w1", "旧标题", "", "源一", 1000L)
            store.refreshSnapshot("s1", WorkKind.COMIC, "w1", "新标题", "https://example.invalid/n.png", "源一", 2000L)

            val entry = store.listEntries().single()
            assertEquals("新标题", entry.manga.title)
            assertEquals("https://example.invalid/n.png", entry.manga.coverUrl)
            assertEquals(1000L, entry.manga.addedAtMillis)
            assertEquals(2000L, entry.manga.lastUpdateCheckMillis)
        } finally {
            driver.close()
        }
    }

    @Test
    fun removeFromLibraryKeepsProgressHistory() = runTest {
        val driver = createTestDriver()
        try {
            val store = LibraryStore(driver)
            val history = HistoryStore(driver)
            LibraryFixture.seed(store, history)

            store.removeFromLibrary("s1", WorkKind.COMIC, "w6")

            assertTrue(store.listEntries(LibraryQuery(search = "单行本")).isEmpty())
            // 取消收藏不删进度：历史与续读派生仍可读。
            assertEquals(0, history.latestEntries().single { it.workId == "w6" }.pageIndex)
        } finally {
            driver.close()
        }
    }
}
