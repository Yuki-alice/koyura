package app.koyura.app.history

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 历史多选：选择集、全选/反选、返回键消费、批量删除与失败续删。
 */
class HistorySelectionTest {
    private fun session(vararg entries: HistoryEntry): Pair<HistorySession, FakeHistoryService> {
        val service = FakeHistoryService(entries.toList())
        return HistorySession(service) to service
    }

    @Test
    fun toggleSelectAndSelectAllFollowVisibleRange() = runTest {
        val (shell, _) = session(
            historyEntry(workId = "a"),
            historyEntry(workId = "b"),
            historyEntry(workId = "c"),
        )
        shell.refresh()

        val first = shell.entries.first { it.workId == "a" }
        shell.toggleSelect(first)
        assertTrue(shell.selection.contains("example_source" to "a"))
        shell.toggleSelect(first)
        assertTrue(shell.selection.isEmpty())

        shell.selectAllVisible()
        assertEquals(3, shell.selection.size)

        // 只反选可见子集：子集外的选择保留。
        shell.invertSelection(shell.entries.take(1))
        assertEquals(2, shell.selection.size)
        assertFalse(shell.selection.contains("example_source" to "a"))
    }

    @Test
    fun consumeBackClearsSelectionThenReleases() = runTest {
        val (shell, _) = session(historyEntry(workId = "a"))
        shell.refresh()
        shell.toggleSelect(shell.entries.single())

        assertEquals(HistoryBackOutcome.ClearedSelection, shell.consumeBack())
        assertTrue(shell.selection.isEmpty())
        assertEquals(HistoryBackOutcome.NotConsumed, shell.consumeBack())
    }

    @Test
    fun deleteSelectedRemovesEverySuccessfullyDeletedRow() = runTest {
        val (shell, service) = session(
            historyEntry(workId = "a"),
            historyEntry(workId = "b"),
        )
        shell.refresh()
        shell.selectAllVisible()

        shell.deleteSelected()

        assertEquals(
            listOf("example_source" to "a", "example_source" to "b"),
            service.deletedKeys,
        )
        assertTrue(shell.selection.isEmpty())
        assertEquals(0, shell.visibleEntries().size)
    }

    @Test
    fun deleteSelectedContinuesAfterSingleFailure() = runTest {
        val (shell, service) = session(
            historyEntry(workId = "a"),
            historyEntry(workId = "b"),
        )
        shell.refresh()
        service.failDeleteKeys = setOf("example_source" to "a")
        shell.selectAllVisible()

        shell.deleteSelected()

        // 失败的一条不阻断整批：b 仍被删除，失败的 a 留在选择集内可重试。
        assertEquals(listOf("example_source" to "b"), service.deletedKeys)
        assertEquals(setOf("example_source" to "a"), shell.selection)
        assertEquals("delete failed", shell.errorMessage)
        assertEquals(listOf("a"), shell.visibleEntries().map { it.workId })
    }
}
