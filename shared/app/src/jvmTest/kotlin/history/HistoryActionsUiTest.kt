package app.koyura.app.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.app.browse.FakeBrowseService
import app.koyura.designsystem.ReaderTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 历史行级操作：删除、收藏心、筛选对话、清空确认。
 */
class HistoryActionsUiTest {
    private fun session(): Pair<HistorySession, FakeHistoryService> {
        val browse = FakeBrowseService()
        val history = FakeHistoryService(
            listOf(
                historyEntry(workId = "demo"),
                historyEntry(workId = "night", sourceKey = "other_source", workTitle = "夜行记"),
            ),
        )
        return HistorySession(history, browse) to history
    }

    @Test
    fun deleteRemovesRowAndFavoriteToggles() = runComposeUiTest {
        val (shell, history) = session()
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

        onNodeWithTag(historyRowTag("example_source", "demo")).assertIsDisplayed()

        // 删除：行消失且服务侧有记录。
        onNodeWithTag(historyRowActionsTag("example_source", "demo")).performClick()
        onNodeWithTag(historyDeleteTag("example_source", "demo")).performClick()
        waitForIdle()
        onNodeWithTag(historyRowTag("example_source", "demo")).assertDoesNotExist()
        assertEquals(listOf("example_source" to "demo"), history.deletedKeys)

        // 收藏：心态切换且行保留。
        onNodeWithTag(historyRowActionsTag("other_source", "night")).performClick()
        onNodeWithTag(historyFavoriteTag("other_source", "night")).performClick()
        onNodeWithTag(historyRowTag("other_source", "night")).assertIsDisplayed()
    }

    @Test
    fun filterDialogAndClearAllBehave() = runComposeUiTest {
        val (shell, _) = session()
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

        // 筛选对话可开合。
        onNodeWithTag(HistoryFilterTag).performClick()
        onNodeWithTag(HistoryFilterDialogTag).assertIsDisplayed()
        onNodeWithTag("history-filter-apply").performClick()
        onNodeWithTag(historyRowTag("example_source", "demo")).assertIsDisplayed()

        // 清空需二次确认，确认后空态。
        onNodeWithTag(HistoryClearAllTag).performClick()
        onNodeWithTag(HistoryClearConfirmTag).assertIsDisplayed()
        onNodeWithTag("history-clear-confirm-ok").performClick()
        waitForIdle()
        onNodeWithTag(HistoryEmptyTag).assertIsDisplayed()
    }
}
