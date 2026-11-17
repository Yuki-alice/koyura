package app.koyura.app.history

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 历史本地态：删除隐藏、收藏心、来源筛选与清空确认。
 *
 * 服务层失败时不隐藏；持久化删除随数据层落地（见 notes），会话层只保证不丢行。
 */
class HistoryLocalStateTest {
    private fun sessionWithTwo(): Pair<HistorySession, FakeHistoryService> {
        val service = FakeHistoryService(
            listOf(
                historyEntry(workId = "demo"),
                historyEntry(workId = "night", sourceKey = "other_source"),
            ),
        )
        return HistorySession(service) to service
    }

    @Test
    fun deleteHidesRowAndRecordsServiceCall() = runTest {
        val (session, service) = sessionWithTwo()
        session.refresh()

        session.delete(session.entries.first { it.workId == "demo" })

        assertEquals(listOf("demo" to "example_source"), service.deletedKeys.map { it.second to it.first })
        assertEquals(listOf("night"), session.visibleEntries().map { it.workId })
        // 删除同时摘掉收藏，避免悬空心态。
        session.toggleFavorite(historyEntry(workId = "night"))
        session.delete(session.entries.first { it.workId == "night" })
        assertTrue(session.visibleEntries().isEmpty())
    }

    @Test
    fun deleteFailureKeepsRowAndReports() = runTest {
        val service = object : HistoryService {
            override suspend fun loadHistory(): List<HistoryEntry> =
                listOf(historyEntry())

            override suspend fun deleteEntry(sourceKey: String, workId: String) {
                throw app.koyura.app.browse.BrowseServiceException("disk gone")
            }
        }
        val session = HistorySession(service)
        session.refresh()

        session.delete(session.entries.single())

        assertEquals(1, session.visibleEntries().size)
        assertEquals("disk gone", session.errorMessage)
    }

    @Test
    fun favoritesOnlyAndSourceFilterCombine() = runTest {
        val (session, _) = sessionWithTwo()
        session.refresh()

        session.toggleFavorite(historyEntry(workId = "demo"))
        assertTrue(session.isFavorite(historyEntry(workId = "demo")))
        assertFalse(session.isFavorite(historyEntry(workId = "night")))

        session.setFavoritesOnly(true)
        assertEquals(listOf("demo"), session.visibleEntries().map { it.workId })

        session.setSourceFilter(setOf("other_source"))
        assertTrue(session.visibleEntries().isEmpty())

        session.clearFilters()
        assertEquals(2, session.visibleEntries().size)
    }

    @Test
    fun clearAllHidesEverything() = runTest {
        val (session, service) = sessionWithTwo()
        session.refresh()

        session.clearAll()

        assertEquals(1, service.clearCalls)
        assertTrue(session.visibleEntries().isEmpty())
        assertTrue(session.favoriteKeys.isEmpty())
    }

    @Test
    fun availableSourcesAreSortedDistinct() = runTest {
        val (session, _) = sessionWithTwo()
        session.refresh()
        assertEquals(listOf("example_source", "other_source"), session.availableSources())
    }
}
