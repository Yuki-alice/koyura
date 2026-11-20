package app.koyura.features.updates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.designsystem.ReaderTheme
import app.koyura.features.updates.FakeUpdatePolicyRepository
import app.koyura.features.updates.FakeUpdatesRepository
import app.koyura.features.updates.UpdatePolicy
import app.koyura.features.updates.UpdatePolicyDialogTag
import app.koyura.features.updates.UpdatePolicyExcludedTag
import app.koyura.features.updates.UpdatePolicyCancelTag
import app.koyura.features.updates.UpdatePolicyConfirmTag
import app.koyura.features.updates.UpdatePolicySession
import app.koyura.features.updates.UpdatesOverflowTag
import app.koyura.features.updates.UpdatesScreen
import app.koyura.features.updates.UpdatesSession
import app.koyura.features.updates.UpdatesSmartSettingsTag
import app.koyura.features.updates.updatePolicySwitchTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * 智能更新设置对话框：溢出入口、开关读写、排除词多行输入与保存。
 */
class UpdatePolicyUiTest {
    @Test
    fun overflowOpensDialogAndSaveAppliesPolicy() = runComposeUiTest {
        val repository = FakeUpdatePolicyRepository()
        val policySession = UpdatePolicySession(repository)
        setContent {
            ReaderTheme {
                UpdatesScreen(
                    session = UpdatesSession(FakeUpdatesRepository()),
                    onOpenWork = { _, _, _ -> },
                    policySession = policySession,
                )
            }
        }
        waitForIdle()

        onNodeWithTag(UpdatesOverflowTag).performClick()
        waitForIdle()
        onNodeWithTag(UpdatesSmartSettingsTag).performClick()
        waitForIdle()

        onNodeWithTag(UpdatePolicyDialogTag).assertIsDisplayed()
        // 默认策略：跳过未读打开，点一次改成关闭。
        onNodeWithTag(updatePolicySwitchTag("skipWithUnread")).performClick()
        onNodeWithTag(UpdatePolicyExcludedTag).performTextInput("外传\nFanbook")
        onNodeWithTag(UpdatePolicyConfirmTag).performClick()
        waitForIdle()

        onNodeWithTag(UpdatePolicyDialogTag).assertDoesNotExist()
        val applied = repository.applied.single()
        assertFalse(applied.skipWithUnread)
        assertEquals(listOf("外传", "Fanbook"), applied.excludedTitleTerms)
        assertEquals(applied, policySession.policy)
    }

    @Test
    fun dialogShowsStoredValuesAndCancelWritesNothing() = runComposeUiTest {
        val stored = UpdatePolicy(enabled = false, excludedTitleTerms = listOf("短篇"))
        val repository = FakeUpdatePolicyRepository(stored)
        val policySession = UpdatePolicySession(repository)
        setContent {
            ReaderTheme {
                UpdatesScreen(
                    session = UpdatesSession(FakeUpdatesRepository()),
                    onOpenWork = { _, _, _ -> },
                    policySession = policySession,
                )
            }
        }
        waitForIdle()

        onNodeWithTag(UpdatesOverflowTag).performClick()
        waitForIdle()
        onNodeWithTag(UpdatesSmartSettingsTag).performClick()
        waitForIdle()

        // 仓储值读回后进入草稿：取消不写入。
        onNodeWithTag(UpdatePolicyDialogTag).assertIsDisplayed()
        onNodeWithTag(UpdatePolicyCancelTag).performClick()
        waitForIdle()
        assertEquals(stored, policySession.policy)
        assertEquals(0, repository.applyCount)
    }
}
