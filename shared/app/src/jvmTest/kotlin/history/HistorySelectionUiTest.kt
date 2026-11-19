package app.koyura.app.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.app.browse.FakeBrowseService
import app.koyura.app.history.FakeHistoryService
import app.koyura.app.history.HistoryDeleteSelectedConfirmTag
import app.koyura.app.history.HistoryRootTestTag
import app.koyura.app.history.HistoryScreen
import app.koyura.app.history.HistorySelectionActionsTag
import app.koyura.app.history.HistorySelectionBarTag
import app.koyura.app.history.HistorySelectionCountTag
import app.koyura.app.history.HistorySelectionDeleteTag
import app.koyura.app.history.HistorySelectionClearTag
import app.koyura.app.history.HistorySession
import app.koyura.app.history.historyEntry
import app.koyura.app.history.historyRowTag
import app.koyura.designsystem.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 历史多选 UI：长按进入选择态、计数、批量删除确认与清除。
 */
class HistorySelectionUiTest {
    private fun session(vararg entries: app.koyura.app.history.HistoryEntry): Pair<HistorySession, FakeHistoryService> {
        val service = FakeHistoryService(entries.toList())
        return HistorySession(service, FakeBrowseService()) to service
    }

    @Test
    fun longPressSelectsAndBulkDeleteConfirms() = runComposeUiTest {
        val (shell, service) = session(
            historyEntry(workId = "a"),
            historyEntry(workId = "b"),
        )
        setContent {
            ReaderTheme {
                HistoryScreen(
                    session = shell,
                    onOpenWork = { _, _, _ -> },
                    onContinue = { _, _, _ -> },
                )
            }
        }
        waitForIdle()

        onNodeWithTag(HistoryRootTestTag).assertIsDisplayed()
        // 长按第一行进入多选（照书架），顶栏切计数、底栏出现批量动作。
        onNodeWithTag(historyRowTag("example_source", "a"))
            .performTouchInput { longClick() }
        waitForIdle()
        onNodeWithTag(HistorySelectionBarTag).assertIsDisplayed()
        onNodeWithTag(HistorySelectionCountTag).assertIsDisplayed()
        onNodeWithTag(HistorySelectionActionsTag).assertIsDisplayed()
        assertEquals(1, shell.selection.size)

        // 选择态下点第二行即追加选择。
        onNodeWithTag(historyRowTag("example_source", "b")).performClick()
        waitForIdle()
        assertEquals(2, shell.selection.size)

        // 删除需二次确认；确认后两行同时消失。
        onNodeWithTag(HistorySelectionDeleteTag).performClick()
        waitForIdle()
        onNodeWithTag(HistoryDeleteSelectedConfirmTag).assertIsDisplayed()
        onNodeWithTag("history-delete-selected-ok").performClick()
        waitForIdle()

        onNodeWithTag(historyRowTag("example_source", "a")).assertDoesNotExist()
        onNodeWithTag(historyRowTag("example_source", "b")).assertDoesNotExist()
        assertEquals(2, service.deletedKeys.size)
        assertTrue(shell.selection.isEmpty())
    }

    @Test
    fun clearSelectionLeavesListIntact() = runComposeUiTest {
        val (shell, service) = session(historyEntry(workId = "a"))
        setContent {
            ReaderTheme {
                HistoryScreen(
                    session = shell,
                    onOpenWork = { _, _, _ -> },
                    onContinue = { _, _, _ -> },
                )
            }
        }
        waitForIdle()

        onNodeWithTag(historyRowTag("example_source", "a"))
            .performTouchInput { longClick() }
        waitForIdle()
        onNodeWithTag(HistorySelectionClearTag).performClick()
        waitForIdle()

        onNodeWithTag(HistorySelectionBarTag).assertDoesNotExist()
        onNodeWithTag(historyRowTag("example_source", "a")).assertIsDisplayed()
        assertTrue(service.deletedKeys.isEmpty())
    }
}
