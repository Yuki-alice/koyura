package app.koyura.app.updates

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.app.AppScaffold
import app.koyura.app.navigation.createAppNavigationState
import app.koyura.designsystem.ReaderTheme
import app.koyura.designsystem.navigation.NavTab
import app.koyura.features.more.MoreRootTestTag
import app.koyura.features.more.MoreScreen
import app.koyura.features.more.MoreSession
import app.koyura.features.more.moreRowTag
import app.koyura.features.more.moreSwitchTag
import app.koyura.features.updates.FakeUpdatesRepository
import app.koyura.features.updates.UpdatesEmptyTag
import app.koyura.features.updates.UpdatesListTag
import app.koyura.features.updates.UpdatesErrorsListTag
import app.koyura.features.updates.UpdatesRetryFailuresTag
import app.koyura.features.updates.UpdatesRootTestTag
import app.koyura.features.updates.updatesFailureTag
import app.koyura.features.updates.UpdatesScreen
import app.koyura.features.updates.UpdatesSession
import app.koyura.features.updates.updatesChapterTag
import app.koyura.features.updates.updatesWorkTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Updates 与 More 首轮 UI 回归：空态、分组列表、行点击、开关状态。
 */
class UpdatesMoreUiTest {
    @Test
    fun updatesEmptyMatchesWhitepaperEmptyState() = runComposeUiTest {
        val session = UpdatesSession(FakeUpdatesRepository())
        setContent {
            ReaderTheme {
                UpdatesScreen(session = session, onOpenWork = { _, _, _ -> })
            }
        }
        waitForIdle()

        onNodeWithTag(UpdatesRootTestTag).assertIsDisplayed()
        onNodeWithTag(UpdatesEmptyTag).assertIsDisplayed()
    }

    @Test
    fun updatesListGroupsLeaderAndChapterRows() = runComposeUiTest {
        val session = UpdatesSession(FakeUpdatesRepository(FakeUpdatesRepository.sampleEntries()))
        var opened = 0
        setContent {
            ReaderTheme {
                UpdatesScreen(
                    session = session,
                    onOpenWork = { _, _, _ -> opened++ },
                )
            }
        }
        waitForIdle()

        onNodeWithTag(UpdatesListTag).assertIsDisplayed()
        onNodeWithTag(updatesWorkTag("demo-source", "kaze-no-kioku")).assertIsDisplayed()
        onNodeWithTag(updatesChapterTag("demo-source", "kaze-no-kioku", "ch-3")).assertIsDisplayed()
        onNodeWithTag(updatesChapterTag("demo-source", "kaze-no-kioku", "ch-3")).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun moreShowsAllRowsAndSwitchesToggle() = runComposeUiTest {
        val session = MoreSession()
        setContent {
            ReaderTheme {
                MoreScreen(session = session)
            }
        }
        waitForIdle()

        onNodeWithTag(MoreRootTestTag).assertIsDisplayed()
        listOf(
            "downloaded-only",
            "incognito",
            "download-queue",
            "categories",
            "statistics",
            "update-errors",
            "data-storage",
            "batch-add",
            "settings",
            "about",
            "help",
        ).forEach { key ->
            // 长列表后段需先滚入视口再断言可见。
            onNodeWithTag(moreRowTag(key)).performScrollTo().assertIsDisplayed()
        }

        assertFalse(session.downloadedOnly)
        onNodeWithTag(moreSwitchTag("downloaded-only")).performClick()
        assertTrue(session.downloadedOnly)
        onNodeWithTag(moreSwitchTag("downloaded-only")).performClick()
        assertFalse(session.downloadedOnly)

        assertFalse(session.incognito)
        onNodeWithTag(moreSwitchTag("incognito")).performClick()
        assertTrue(session.incognito)
    }

    @Test
    fun globalBannersFollowTheMoreSwitchesInOrder() = runComposeUiTest {
        val navigation = createAppNavigationState().apply { select(NavTab.More) }
        val session = MoreSession()
        setContent {
            ReaderTheme {
                AppScaffold(navigation = navigation, useRail = false, moreSession = session, indexing = true)
            }
        }
        onNodeWithTag(moreSwitchTag("downloaded-only")).performClick()
        onNodeWithTag(moreSwitchTag("incognito")).performClick()
        val positions = listOf("indexing", "downloaded-only", "incognito").map { key ->
            onNodeWithTag("status-$key").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue(positions[0] < positions[1] && positions[1] < positions[2])
        runOnIdle { navigation.select(NavTab.Library) }
        onNodeWithTag("status-downloaded-only").assertIsDisplayed()
        onNodeWithTag("status-incognito").assertIsDisplayed()
    }

    @Test
    fun updateErrorsViewShowsFailuresAndRetryClears() = runComposeUiTest {
        val session = UpdatesSession(
            FakeUpdatesRepository(
                FakeUpdatesRepository.sampleEntries(),
                failed = FakeUpdatesRepository.sampleFailures(),
            ),
        )
        setContent {
            ReaderTheme {
                UpdatesScreen(
                    session = session,
                    onOpenWork = { _, _, _ -> },
                )
            }
        }
        waitForIdle()

        onNodeWithTag("updates-overflow").performClick()
        waitForIdle()
        onNodeWithTag("updates-errors-toggle").performClick()
        waitForIdle()

        onNodeWithTag(UpdatesErrorsListTag).assertIsDisplayed()
        onNodeWithTag(updatesFailureTag("demo-source", "midnight-ramen")).assertIsDisplayed()

        onNodeWithTag(UpdatesRetryFailuresTag).performClick()
        waitForIdle()
        onAllNodesWithTag(updatesFailureTag("demo-source", "midnight-ramen")).assertCountEquals(0)
    }
}
