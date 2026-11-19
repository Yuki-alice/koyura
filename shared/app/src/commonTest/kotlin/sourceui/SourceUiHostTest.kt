package app.koyura.app.sourceui

import app.koyura.source.runtime.SourceDialogAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SourceUiHostTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun unconfined() = scope
    @Test
    fun messageEmitsAndConsumes() = runTest {
        val host = SourceUiHost(FakeClipboard())
        host.showMessage("src", "hello")
        assertEquals("hello", host.message.value?.text)

        host.consumeMessage(host.message.value!!.id)
        assertNull(host.message.value)
    }

    @Test
    fun loadingTracksAndCancels() = runTest {
        val host = SourceUiHost(FakeClipboard())
        var cancelled = 0
        host.showLoading("src", 1, onCancel = { cancelled++ })
        host.showLoading("src", 2, onCancel = null)
        assertEquals(setOf(1, 2), host.loading.value.keys)

        host.cancelLoading("src", 1)
        assertEquals(setOf(2), host.loading.value.keys)

        host.cancelAllLoading()
        assertTrue(host.loading.value.isEmpty())
        assertEquals(0, cancelled)
    }

    @Test
    fun dialogActionRunsCallbackAndDismisses() = runTest {
        val host = SourceUiHost(FakeClipboard())
        var picked = -1
        unconfined().async {
            host.showDialog(
                sourceKey = "src",
                title = "T",
                content = "C",
                actions = listOf(SourceDialogAction("A", ""), SourceDialogAction("B", "")),
            ) { picked = it }
        }
        advanceUntilIdle()

        val request = host.dialog.value
        assertTrue(request is SourceUiDialog.Message)
        assertEquals(listOf("A", "B"), request.actions.map { it.text })

        // 模拟宿主点选第二个动作。
        request.onAction(1)
        request.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, picked)
        assertNull(host.dialog.value)
    }

    @Test
    fun inputDialogReturnsTextAndSurfacesValidationError() = runTest {
        val host = SourceUiHost(FakeClipboard())
        val job = unconfined().async {
            host.showInputDialog("src", "Login", null) { if (it.isBlank()) "empty" else null }
        }
        advanceUntilIdle()

        val request = host.dialog.value
        assertTrue(request is SourceUiDialog.Input)

        // 校验失败不关框（宿主侧只断言 validate 语义，UI 层测错误展示）。
        assertEquals("empty", request.validate?.invoke(""))
        assertNull(request.validate?.invoke("user"))

        request.gate.complete("user")
        advanceUntilIdle()
        assertEquals("user", job.await())
        assertNull(host.dialog.value)
    }

    @Test
    fun selectDialogReturnsIndex() = runTest {
        val host = SourceUiHost(FakeClipboard())
        val job = unconfined().async {
            host.showSelectDialog("src", "Pick", listOf("a", "b"), initialIndex = 0)
        }
        advanceUntilIdle()

        val request = host.dialog.value
        assertTrue(request is SourceUiDialog.Select)
        assertEquals(listOf("a", "b"), request.options)

        request.gate.complete(1)
        advanceUntilIdle()
        assertEquals(1, job.await())
        assertNull(host.dialog.value)
    }

    @Test
    fun cancellationReleasesDialogSlot() = runTest {
        val host = SourceUiHost(FakeClipboard())
        val job = unconfined().async {
            host.showInputDialog("src", "Login", null, null)
        }
        advanceUntilIdle()
        assertTrue(host.dialog.value is SourceUiDialog.Input)

        job.cancel()
        advanceUntilIdle()
        assertNull(host.dialog.value)
        // 取消后槽位释放，后续消息正常进入。
        host.showMessage("src", "after")
        assertEquals("after", host.message.value?.text)
    }

    @Test
    fun clipboardRoundTripsThroughHost() = runTest {
        val clipboard = FakeClipboard("abc")
        val host = SourceUiHost(clipboard)
        assertEquals("abc", host.getClipboard("src"))

        host.setClipboard("src", "xyz")
        assertEquals("xyz", clipboard.text)
    }

    @Test
    fun clipboardFailureFallsBackToMessage() = runTest {
        val failing = object : SourceClipboard {
            override suspend fun getText(): String? = null
            override suspend fun setText(text: String): Boolean = false
        }
        val seen = mutableListOf<String>()
        val host = SourceUiHost(failing, onMessage = { seen += it })
        host.setClipboard("src", "fallback-text")
        advanceUntilIdle()
        assertEquals(listOf("fallback-text"), seen)
    }

    @Test
    fun recordingHostKeepsMessageLog() = runTest {
        val host = RecordingSourceUiHost()
        val job = unconfined().launch { host.showMessage("src", "m1") }
        job.join()
        assertEquals(listOf("m1"), host.messages)
        assertEquals("m1", host.message.value?.text)
    }

    @Test
    fun launchUrlOpensOrFallsBackToMessage() = runTest {
        val opener = FakeUrlOpener()
        val seen = mutableListOf<String>()
        val immediate = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val host = SourceUiHost(FakeClipboard(), opener, { seen += it }, immediate)
            host.launchUrl("src", "https://example.invalid/register")
            advanceUntilIdle()
            assertEquals(listOf("https://example.invalid/register"), opener.opened)
            assertTrue(seen.isEmpty())

            opener.result = false
            host.launchUrl("src", "https://example.invalid/other")
            advanceUntilIdle()
            assertEquals(listOf("https://example.invalid/register"), opener.opened)
            assertEquals(listOf("https://example.invalid/other"), seen)
        } finally {
            immediate.cancel()
        }
    }
}
