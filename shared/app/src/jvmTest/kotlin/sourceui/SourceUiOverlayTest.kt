package app.koyura.app.sourceui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.designsystem.ReaderTheme
import app.koyura.source.runtime.SourceDialogAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 宿主呈现回归：对话框三形态、输入校验、snackbar、加载态。
 *
 * 对话框渲染在独立窗口层，查找统一走未合并语义树；后台驱动协程挂在测试
 * Main 调度器上随 waitForIdle 推进，结束后统一取消不向后续测试泄漏。
 */
class SourceUiOverlayTest {
    private fun testScope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun ComposeUiTest.dialogNode(tag: String) = onNodeWithTag(tag, useUnmergedTree = true)

    @Test
    fun messageDialogRendersActionsAndResumes() = runComposeUiTest {
        val scope = testScope()
        try {
            val host = SourceUiHost(FakeClipboard())
            setContent {
                ReaderTheme {
                    SourceUiOverlay(host = host)
                }
            }
            waitForIdle()

            var picked = -1
            val job = scope.async {
                host.showDialog(
                    sourceKey = "src",
                    title = "Confirm",
                    content = "Proceed?",
                    actions = listOf(SourceDialogAction("No", ""), SourceDialogAction("Yes", "primary")),
                ) { picked = it }
            }
            waitForIdle()

            dialogNode(SourceUiDialogTag).assertIsDisplayed()
            dialogNode(sourceUiDialogActionTag(1)).performClick()
            waitForIdle()

            assertEquals(Unit, job.await())
            assertEquals(1, picked)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun inputDialogShowsValidationErrorThenSubmits() = runComposeUiTest {
        val scope = testScope()
        try {
            val host = SourceUiHost(FakeClipboard())
            setContent {
                ReaderTheme {
                    SourceUiOverlay(host = host)
                }
            }
            waitForIdle()

            val job = scope.async {
                host.showInputDialog("src", "Login", null) { if (it.isBlank()) "empty" else null }
            }
            waitForIdle()

            // 空提交触发校验错误并保留对话框。
            dialogNode(sourceUiDialogActionTag(0)).performClick()
            waitForIdle()
            dialogNode(SourceUiDialogErrorTag).assertIsDisplayed()

            // 修正后提交返回值并关框。
            dialogNode(SourceUiDialogInputTag).performTextInput("user")
            dialogNode(sourceUiDialogActionTag(0)).performClick()
            assertEquals("user", job.await())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun selectDialogTapReturnsIndex() = runComposeUiTest {
        val scope = testScope()
        try {
            val host = SourceUiHost(FakeClipboard())
            setContent {
                ReaderTheme {
                    SourceUiOverlay(host = host)
                }
            }
            waitForIdle()

            val job = scope.async {
                host.showSelectDialog("src", "Pick", listOf("a", "b"), initialIndex = null)
            }
            waitForIdle()

            dialogNode(sourceUiDialogOptionTag(1)).performClick()
            assertEquals(1, job.await())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun snackbarAndLoadingRender() = runComposeUiTest {
        val scope = testScope()
        try {
            val host = SourceUiHost(FakeClipboard())
            setContent {
                ReaderTheme {
                    SourceUiOverlay(host = host)
                }
            }
            waitForIdle()

            scope.launch { host.showMessage("src", "hello-snack") }
            // 只推进 1 秒：snackbar（默认 4 秒）仍在展示。
            mainClock.advanceTimeBy(1000)
            waitForIdle()
            onNodeWithTag(SourceUiSnackbarTag).assertIsDisplayed()
            // 推进到自动消失并消费。
            mainClock.advanceTimeBy(4000)
            waitForIdle()

            scope.launch { host.showLoading("src", 7, onCancel = null) }
            waitForIdle()
            onNodeWithTag(SourceUiLoadingTag).assertIsDisplayed()

            scope.launch { host.cancelLoading("src", 7) }
            waitForIdle()
            assertTrue(host.loading.value.isEmpty())
        } finally {
            scope.cancel()
        }
    }
}
