package app.koyura.features.library

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibrarySessionTest {
    private fun sessionOf(
        snapshot: LibrarySnapshot = libraryFixtureSnapshot(),
        repository: FakeLibraryRepository? = null,
    ): Pair<LibrarySession, FakeLibraryRepository> {
        val fake = repository ?: FakeLibraryRepository(
            entries = snapshot.entries.toMutableList(),
            categories = snapshot.categories.toMutableList(),
            links = snapshot.links.toMutableMap(),
            prefs = snapshot.prefs,
        )
        return LibrarySession(fake) to fake
    }

    @Test
    fun refreshLoadsFixtureAndHidesHiddenCategoryByDefault() = runTest {
        val (session, _) = sessionOf()
        assertIs<LibraryLoadState.Loading>(session.loadState)
        session.refresh()
        assertIs<LibraryLoadState.Ready>(session.loadState)
        assertEquals(7, session.snapshot.entries.size)
        // 默认不展示隐藏分类页签：全部 + 默认 + 空分类。
        assertEquals(listOf(null, 1L, 3L), session.tabs.map { it?.id })
        assertEquals(5, session.snapshot.entriesIn(1L).size)
        assertEquals(1, session.snapshot.entriesIn(2L).size)
        assertTrue(session.snapshot.entriesIn(3L).isEmpty())
        assertEquals(1, session.snapshot.uncategorized().size)
    }

    @Test
    fun emptySnapshotRendersEmpty() = runTest {
        val (session, _) = sessionOf(emptyLibrarySnapshot())
        session.refresh()
        assertIs<LibraryLoadState.Ready>(session.loadState)
        assertTrue(session.visibleEntries.isEmpty())
        assertEquals(listOf(null), session.tabs.map { it?.id })
    }

    @Test
    fun loadFailureKeepsOldEntriesAndReports() = runTest {
        val (session, fake) = sessionOf()
        session.refresh()
        fake.failures["load"] = "network down"
        session.refresh()
        assertIs<LibraryLoadState.Failed>(session.loadState)
        assertEquals(7, session.snapshot.entries.size)
        session.refresh()
        assertIs<LibraryLoadState.Ready>(session.loadState)
    }

    @Test
    fun searchNarrowsToRealData() = runTest {
        val (session, _) = sessionOf()
        session.refresh()
        session.updateQuery("同名作品")
        assertEquals(setOf("w1", "w2"), session.visibleEntries.map { it.workId }.toSet())
        session.updateQuery("zzz-no-match")
        assertTrue(session.visibleEntries.isEmpty())
        session.updateQuery("")
        assertEquals(7, session.visibleEntries.size)
    }

    @Test
    fun categoryTabFiltersEntries() = runTest {
        val (session, _) = sessionOf()
        session.refresh()
        session.selectCategory(1L)
        assertEquals(setOf("w1", "w2", "w3", "w4", "w7"), session.visibleEntries.map { it.workId }.toSet())
        session.selectCategory(3L)
        assertTrue(session.visibleEntries.isEmpty())
        session.selectCategory(null)
        assertEquals(7, session.visibleEntries.size)
    }

    @Test
    fun hiddenCategoryShownOnlyWithPref() = runTest {
        val (session, _) = sessionOf()
        session.refresh()
        assertFalse(session.tabs.any { it?.id == 2L })
        session.updatePrefs { it.copy(showHiddenCategories = true) }
        assertTrue(session.tabs.any { it?.id == 2L })
        session.selectCategory(2L)
        assertEquals(listOf("w6"), session.visibleEntries.map { it.workId })
        // 落盘：新会话读回同一偏好。
        val revived = LibrarySession(
            FakeLibraryRepository(
                entries = session.snapshot.entries.toMutableList(),
                categories = session.snapshot.categories.toMutableList(),
                links = session.snapshot.links.toMutableMap(),
                prefs = session.snapshot.prefs,
            ),
        )
        revived.refresh()
        assertTrue(revived.snapshot.prefs.showHiddenCategories)
    }

    @Test
    fun filtersApplyToRealData() = runTest {
        val (session, _) = sessionOf()
        session.refresh()
        suspend fun only(
            transform: (LibraryFilters) -> LibraryFilters,
            expected: Set<String>,
        ) {
            session.updatePrefs { it.copy(filters = transform(LibraryFilters())) }
            assertEquals(expected, session.visibleEntries.map { it.workId }.toSet())
        }
        only({ it.copy(unread = LibraryTriState.INCLUDE) }, setOf("w1", "w3", "w5", "w6", "w7"))
        only({ it.copy(unread = LibraryTriState.EXCLUDE) }, setOf("w2", "w4"))
        only({ it.copy(downloaded = LibraryTriState.INCLUDE) }, setOf("w1"))
        only({ it.copy(started = LibraryTriState.INCLUDE) }, setOf("w1", "w2", "w3", "w5"))
        only({ it.copy(bookmarked = LibraryTriState.INCLUDE) }, setOf("w2"))
        only({ it.copy(completed = LibraryTriState.INCLUDE) }, setOf("w2"))
        only({ it.copy(lewd = LibraryTriState.INCLUDE) }, setOf("w4"))
        only({ it.copy(lewd = LibraryTriState.EXCLUDE) }, setOf("w1", "w2", "w3", "w5", "w6", "w7"))
    }

    @Test
    fun sortOrdersAndRandomSeedIsDeterministic() = runTest {
        val (session, _) = sessionOf()
        session.refresh()
        session.updatePrefs { it.copy(sortType = LibrarySortType.DATE_ADDED, ascending = true) }
        assertEquals(
            listOf("w1", "w2", "w3", "w4", "w5", "w6", "w7"),
            session.visibleEntries.map { it.workId },
        )
        session.updatePrefs { it.copy(ascending = false) }
        assertEquals(
            listOf("w7", "w6", "w5", "w4", "w3", "w2", "w1"),
            session.visibleEntries.map { it.workId },
        )
        session.updatePrefs { it.copy(sortType = LibrarySortType.ALPHABETICAL, ascending = true) }
        val titles = session.visibleEntries.map { it.displayTitle }
        assertEquals(titles.sorted(), titles)
        // 随机排序：同一种子两次结果一致，且为全集排列。
        session.updatePrefs { it.copy(sortType = LibrarySortType.RANDOM, randomSeed = 42L) }
        val first = session.visibleEntries.map { it.workId }
        session.updatePrefs { it.copy(sortType = LibrarySortType.ALPHABETICAL) }
        session.updatePrefs { it.copy(sortType = LibrarySortType.RANDOM, randomSeed = 42L) }
        val second = session.visibleEntries.map { it.workId }
        assertEquals(first, second)
        assertEquals(setOf("w1", "w2", "w3", "w4", "w5", "w6", "w7"), first.toSet())
    }

    @Test
    fun batchFavoriteRemoveAndMarkRead() = runTest {
        val (session, _) = sessionOf()
        session.refresh()
        session.toggleSelect(LibraryEntryKey("srcA", LibraryKind.COMIC, "w1"))
        session.toggleSelect(LibraryEntryKey("srcA", LibraryKind.COMIC, "w3"))
        assertEquals(2, session.selection.size)
        assertTrue(session.markSelectedAsRead(true))
        assertEquals(
            0L,
            session.snapshot.entries.first { it.workId == "w1" }.unreadCount,
        )
        assertEquals(
            0L,
            session.snapshot.entries.first { it.workId == "w3" }.unreadCount,
        )
        assertTrue(session.markSelectedAsRead(false))
        assertTrue(
            session.snapshot.entries.first { it.workId == "w1" }.unreadCount > 0,
        )
        // 批量移除需确认后调用：确认后选择清空、行消失。
        assertTrue(session.removeSelected())
        assertTrue(session.selection.isEmpty())
        assertEquals(5, session.snapshot.entries.size)
        assertFalse(session.isFavorite(LibraryEntryKey("srcA", LibraryKind.COMIC, "w1")))
    }

    @Test
    fun duplicateFavoriteKeepsOriginalRow() = runTest {
        val (session, fake) = sessionOf()
        session.refresh()
        val before = session.snapshot.entries.first { it.workId == "w1" }
        val ok = session.setFavorite(
            NewLibraryEntry(sourceKey = "srcA", workId = "w1", title = "改名后的标题"),
            true,
        )
        assertTrue(ok)
        val after = session.snapshot.entries.first { it.workId == "w1" }
        assertEquals(before.favoriteAtMillis, after.favoriteAtMillis)
        assertEquals(before.title, after.title)
        assertEquals(1, fake.calls["addToLibrary:w1"])
    }

    @Test
    fun novelRowsAreRejected() = runTest {
        val (session, fake) = sessionOf()
        session.refresh()
        // 仓储层直接拒绝 NOVEL 行写入。
        assertFailsWith<LibraryRepositoryException> {
            fake.addToLibrary(
                NewLibraryEntry(sourceKey = "srcA", kind = LibraryKind.NOVEL, workId = "n1"),
            )
        }
        // setFavorite 把仓储异常转为 false + actionError，不抛到 UI。
        val ok = session.setFavorite(
            NewLibraryEntry(sourceKey = "srcA", kind = LibraryKind.NOVEL, workId = "n1"),
            true,
        )
        assertFalse(ok)
        assertTrue(session.actionError?.isNotBlank() == true)
        assertFalse(session.snapshot.entries.any { it.workId == "n1" })
        session.clearActionError()
        assertNull(session.actionError)
    }

    @Test
    fun mutationFailureKeepsSnapshotAndReports() = runTest {
        val (session, fake) = sessionOf()
        session.refresh()
        session.toggleSelect(LibraryEntryKey("srcA", LibraryKind.COMIC, "w1"))
        fake.failures["removeFromLibrary"] = "disk full"
        assertFalse(session.removeSelected())
        assertEquals(7, session.snapshot.entries.size)
        assertTrue(session.actionError?.contains("disk full") == true)
        // 选择保留，可重试。
        assertEquals(1, session.selection.size)
    }

    @Test
    fun categoryCrudRoundTrip() = runTest {
        val (session, _) = sessionOf()
        session.refresh()
        assertTrue(session.addCategory("新分类"))
        val added = session.snapshot.categories.first { it.name == "新分类" }
        assertTrue(session.renameCategory(added.id, "改名"))
        assertTrue(session.setCategoryHidden(added.id, true))
        assertTrue(session.snapshot.categories.first { it.id == added.id }.isHidden)
        assertTrue(session.removeCategory(3L))
        assertFalse(session.snapshot.categories.any { it.id == 3L })
    }

    @Test
    fun backClearsSelectionThenSearch() = runTest {
        val (session, _) = sessionOf()
        session.refresh()
        assertFalse(session.wantsBack)
        session.updateQuery("同名")
        assertTrue(session.wantsBack)
        session.toggleSelect(LibraryEntryKey("srcA", LibraryKind.COMIC, "w1"))
        assertEquals(LibraryBackOutcome.ClearedSelection, session.consumeBack())
        assertTrue(session.selection.isEmpty())
        assertEquals(LibraryBackOutcome.ClearedSearch, session.consumeBack())
        assertEquals("", session.query)
        assertEquals(LibraryBackOutcome.NotConsumed, session.consumeBack())
    }

    @Test
    fun coversLoadOnceIsolateBySourceAndFailOnce() = runTest {
        val (session, fake) = sessionOf()
        session.refresh()
        val normal = session.snapshot.entries.first { it.workId == "w1" }
        session.ensureCover(normal)
        assertIs<LibraryCoverState.Ready>(session.covers[coverCacheKey(normal.sourceKey, normal.coverUrl)])
        session.ensureCover(normal)
        assertEquals(1, fake.thumbnailLoadCounts["srcA\nhttps://img.example/a1.png"])
        // 同 URL 不同源隔离：各打一次。
        val otherSource = normal.copy(sourceKey = "srcB")
        session.ensureCover(otherSource)
        assertEquals(1, fake.thumbnailLoadCounts["srcB\nhttps://img.example/a1.png"])
        // 损坏图：单次失败即记 Failed，不重试。
        val broken = session.snapshot.entries.first { it.workId == "w2" }
        session.ensureCover(broken)
        assertIs<LibraryCoverState.Failed>(session.covers[coverCacheKey(broken.sourceKey, broken.coverUrl)])
        session.ensureCover(broken)
        assertEquals(1, fake.thumbnailLoadCounts["srcB\nhttps://img.example/broken.png"])
        // 空字符串：零网络、直接 Empty。
        val empty = session.snapshot.entries.first { it.workId == "w4" }
        session.ensureCover(empty)
        assertIs<LibraryCoverState.Empty>(session.covers[coverCacheKey(empty.sourceKey, empty.coverUrl)])
        assertNull(fake.thumbnailLoadCounts["srcA\n"])
        // 预取只请求给定条目。
        fake.thumbnailLoadCounts.clear()
        session.covers.clear()
        session.prefetchCovers(listOf(normal, empty))
        assertEquals(1, fake.thumbnailLoadCounts.size)
    }

    @Test
    fun migrationEntryStaysDisabled() = runTest {
        val (_, fake) = sessionOf()
        assertFalse(fake.migrationSupported)
    }

    @Test
    fun chapterlessEntryHasNullChapterId() = runTest {
        val (session, _) = sessionOf()
        session.refresh()
        val single = session.snapshot.entries.first { it.workId == "w7" }
        assertNull(single.continueChapterId)
        assertTrue(single.canContinue)
        assertFalse(single.hasStarted)
    }
}
