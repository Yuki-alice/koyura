package app.koyura.app.sourceui

import app.koyura.source.runtime.SourceDialogAction
import app.koyura.source.runtime.SourceDialogImage
import app.koyura.source.runtime.VeneraUiHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 顶层消息（snackbar 展示， id 用于消费确认）。 */
data class SourceUiMessage(val id: Long, val text: String)

/** 顶层挂起对话框请求（Compose 宿主渲染，完成后经 gate 回填）。 */
sealed interface SourceUiDialog {
    data class Message(
        val title: String,
        val content: String,
        val actions: List<SourceDialogAction>,
        val onAction: suspend (Int) -> Unit,
        val gate: CompletableDeferred<Unit>,
    ) : SourceUiDialog

    data class Input(
        val title: String,
        val image: SourceDialogImage?,
        val validate: (suspend (String) -> String?)?,
        val gate: CompletableDeferred<String?>,
    ) : SourceUiDialog

    data class Select(
        val title: String,
        val options: List<String>,
        val initialIndex: Int?,
        val gate: CompletableDeferred<Int?>,
    ) : SourceUiDialog
}

/**
 * 脚本 UI 的 Compose 宿主：把引擎 `UI.*` 回调翻译成可组合状态。
 *
 * 线程规则：引擎在 IO 线程调同步入口（只写 StateFlow，线程安全）；
 * 挂起对话框经互斥单槽排队，取消时释放占位（原生呈现即 Dialog 消失）。
 * `log` 无宿主动作（日志走引擎侧）；`launchUrl` 走平台浏览器，打不开时回退原文消息。
 */
open class SourceUiHost(
    private val clipboard: SourceClipboard,
    private val urlOpener: SourceUrlOpener = FakeUrlOpener(),
    private val onMessage: ((String) -> Unit)? = null,
    private val background: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : VeneraUiHost {
    private var messageSeq = 0L
    private val _message = MutableStateFlow<SourceUiMessage?>(null)
    val message: StateFlow<SourceUiMessage?> = _message.asStateFlow()

    private val _loading = MutableStateFlow<Map<Int, (() -> Unit)?>>(emptyMap())
    val loading: StateFlow<Map<Int, (() -> Unit)?>> = _loading.asStateFlow()

    private val dialogMutex = Mutex()
    private val _dialog = MutableStateFlow<SourceUiDialog?>(null)
    val dialog: StateFlow<SourceUiDialog?> = _dialog.asStateFlow()

    override fun showMessage(sourceKey: String, message: String) {
        messageSeq += 1
        _message.value = SourceUiMessage(messageSeq, message)
        onMessage?.invoke(message)
    }

    fun consumeMessage(id: Long) {
        if (_message.value?.id == id) _message.value = null
    }

    override fun showLoading(sourceKey: String, id: Int, onCancel: (() -> Unit)?) {
        _loading.value = _loading.value + (id to onCancel)
    }

    override fun cancelLoading(sourceKey: String, id: Int) {
        _loading.value = _loading.value - id
    }

    fun cancelAllLoading() {
        val pending = _loading.value
        _loading.value = emptyMap()
        pending.values.forEach { cancel -> cancel?.let { runCatching { it() } } }
    }

    override suspend fun showDialog(
        sourceKey: String,
        title: String,
        content: String,
        actions: List<SourceDialogAction>,
        runAction: suspend (Int) -> Unit,
    ) {
        val gate = CompletableDeferred<Unit>()
        dialogMutex.withLock {
            _dialog.value = SourceUiDialog.Message(title, content, actions, runAction, gate)
            try {
                gate.await()
            } finally {
                clearDialog()
            }
        }
    }

    override suspend fun showInputDialog(
        sourceKey: String,
        title: String,
        image: SourceDialogImage?,
        validate: (suspend (String) -> String?)?,
    ): String? {
        val gate = CompletableDeferred<String?>()
        dialogMutex.withLock {
            _dialog.value = SourceUiDialog.Input(title, image, validate, gate)
            try {
                return gate.await()
            } finally {
                clearDialog()
            }
        }
    }

    override suspend fun showSelectDialog(
        sourceKey: String,
        title: String,
        options: List<String>,
        initialIndex: Int?,
    ): Int? {
        val gate = CompletableDeferred<Int?>()
        dialogMutex.withLock {
            _dialog.value = SourceUiDialog.Select(title, options, initialIndex, gate)
            try {
                return gate.await()
            } finally {
                clearDialog()
            }
        }
    }

    override suspend fun setClipboard(sourceKey: String, text: String) {
        if (!clipboard.setText(text)) {
            showMessage(sourceKey, text)
        }
    }

    override suspend fun getClipboard(sourceKey: String): String? = clipboard.getText()

    override fun launchUrl(sourceKey: String, url: String) {
        launchUrlAsync(sourceKey, url)
    }

    private fun launchUrlAsync(sourceKey: String, url: String) {
        background.launch {
            if (!urlOpener.open(url)) showMessage(sourceKey, url)
        }
    }

    private fun clearDialog() {
        _dialog.value = null
    }
}

/** 测试用内存宿主（记录消息，不弹 UI）。 */
class RecordingSourceUiHost(
    clipboard: SourceClipboard = FakeClipboard(),
    val messages: MutableList<String> = mutableListOf(),
) : SourceUiHost(clipboard, onMessage = { messages += it })
