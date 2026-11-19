package app.koyura.app.downloads

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.app.AppScaffold
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import app.koyura.features.downloads.DownloadsEmptyTag
import app.koyura.features.downloads.DownloadsListTag
import app.koyura.features.downloads.DownloadsRootTestTag
import app.koyura.features.downloads.DownloadsScreen
import app.koyura.features.downloads.DownloadsSession
import app.koyura.features.downloads.FakeDownloadRepository
import app.koyura.features.downloads.downloadCancelTag
import app.koyura.features.downloads.downloadPauseTag
import app.koyura.features.downloads.downloadRetryTag
import app.koyura.features.downloads.downloadRowTag
import app.koyura.features.more.moreRowTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 下载队列首轮 UI 回归：空态、分组行、暂停/重试/取消动作。
 */
class DownloadsQueueUiTest {
    @Test
    fun emptyQueueMatchesWhitepaperEmptyState() = runComposeUiTest {
        val session = DownloadsSession(FakeDownloadRepository())
        setContent {
            ReaderTheme {
                DownloadsScreen(session = session, onBack = {})
            }
        }
        waitForIdle()

        onNodeWithTag(DownloadsRootTestTag).assertIsDisplayed()
        onNodeWithTag(DownloadsEmptyTag).assertIsDisplayed()
    }

    @Test
    fun rowsExposeStatusActionsAndMutateQueue() = runComposeUiTest {
        val session = DownloadsSession(FakeDownloadRepository(FakeDownloadRepository.sampleEntries()))
        setContent {
            ReaderTheme {
                DownloadsScreen(session = session, onBack = {})
            }
        }
        waitForIdle()

        onNodeWithTag(DownloadsListTag).assertIsDisplayed()
        onNodeWithTag(downloadRowTag("job-1")).assertIsDisplayed()

        // 暂停下载中的任务，暂停态出现恢复与取消。
        onNodeWithTag(downloadPauseTag("job-1")).performClick()
        waitForIdle()
        assertTrue(session.entries.first { it.id == "job-1" }.status.name == "PAUSED")

        // 取消暂停中的任务，任务移出队列。
        onNodeWithTag(downloadCancelTag("job-1")).performClick()
        waitForIdle()
        assertTrue(session.entries.none { it.id == "job-1" })

        // 重试失败的任务回到排队。
        onNodeWithTag(downloadRetryTag("job-3")).performClick()
        waitForIdle()
        assertEquals("QUEUED", session.entries.first { it.id == "job-3" }.status.name)
    }

    @Test
    fun moreDownloadQueueEntryOpensQueuePage() = runComposeUiTest {
        val navigation = createAppNavigationState(initialTab = NavTab.More)
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = false)
            }
        }
        waitForIdle()

        onNodeWithTag(moreRowTag("download-queue")).performClick()
        waitForIdle()
        onNodeWithTag(DownloadsRootTestTag).assertIsDisplayed()
    }
}
