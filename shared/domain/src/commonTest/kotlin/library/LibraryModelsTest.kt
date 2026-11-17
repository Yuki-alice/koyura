package app.koyura.domain.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 书架领域模型契约：三元身份、默认值、disabled 跨源入口。
 *
 * 数据语义的真实断言在 `:shared:data` 的 `LibraryStoreTest` /
 * `LibraryMigrationTest`（固定数据集、筛选/批量/隐藏分类/随机种子、升级/二次启动/回滚）。
 */
class LibraryModelsTest {
    @Test
    fun workKindCoversComicAndNovel() {
        assertEquals(listOf(WorkKind.COMIC, WorkKind.NOVEL), WorkKind.values().toList())
    }

    @Test
    fun libraryMangaDefaultsKeepCoverEmpty() {
        val manga = LibraryManga(
            sourceKey = "s1",
            kind = WorkKind.COMIC,
            workId = "w1",
            title = "同名作品",
            addedAtMillis = 1000L,
        )
        // 封面 URL 为空是合法快照：UI 回退首字母色块，不以标题去重。
        assertEquals("", manga.coverUrl)
        assertNull(manga.sourceName)
        assertNull(manga.lastUpdateCheckMillis)
    }

    @Test
    fun queryDefaultsShowAllSortedByName() {
        val query = LibraryQuery()
        assertEquals("", query.search)
        assertNull(query.categoryId)
        assertFalse(query.includeHiddenCategories)
        assertEquals(LibrarySort.NAME, query.sort)
        assertTrue(query.ascending)
    }

    @Test
    fun crossSourceMigrationStaysDisabled() {
        assertFalse(LibraryMigrationGate.CROSS_SOURCE_ENABLED)
        assertFailsWith<UnsupportedOperationException> {
            LibraryMigrationGate.requireCrossSourceEnabled()
        }
    }

    @Test
    fun prefsKeysAreDocumented() {
        assertEquals("display.mode", LibraryPrefsKeys.DISPLAY_MODE)
        assertEquals("display.columns", LibraryPrefsKeys.DISPLAY_COLUMNS)
        assertEquals("filter.spec", LibraryPrefsKeys.FILTER_SPEC)
        assertEquals("sort.spec", LibraryPrefsKeys.SORT_SPEC)
        assertEquals("group.spec", LibraryPrefsKeys.GROUP_SPEC)
    }
}
