package app.koyura.features.downloads

import androidx.lifecycle.ViewModel
import app.koyura.features.downloads.DownloadQueueLoadState.Failed
import app.koyura.features.downloads.DownloadQueueLoadState.Loading
import app.koyura.features.downloads.DownloadQueueLoadState.Ready
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 下载队列 MVI 样板（阶段 1 首个 Store）。
 *
 * 状态机与 [DownloadsSession] 同语义：暂停仅收排队/下载中（仓储侧收敛），
 * 恢复与重试回到排队，取消与完成为终态（从可见队列移除），失败只记错误不丢快照。
 * 区别在承载：显式 [DownloadsIntent]/[DownloadsState]/[DownloadsLabel]，
 * 转移规则全进 [Reducer]；副作用（仓储读写 + 订阅）全进 [Executor]。
 */
data class DownloadsState(
    val loadState: DownloadQueueLoadState = Loading,
    val entries: List<DownloadTask> = emptyList(),
    val actionError: String? = null,
) {
    /** 是否有活跃任务（FAB 暂停/恢复文案的数据源）。 */
    val running: Boolean
        get() = entries.any { it.status == FakeDownloadStatus.QUEUED || it.status == FakeDownloadStatus.DOWNLOADING }
}

/** 下载队列用户意图（一次性操作；订阅由 Bootstrapper 发起）。 */
sealed interface DownloadsIntent {
    data object Refresh : DownloadsIntent
    data class Pause(val id: String) : DownloadsIntent
    data class Resume(val id: String) : DownloadsIntent
    data class Retry(val id: String) : DownloadsIntent
    data class Cancel(val id: String) : DownloadsIntent
    data object Clear : DownloadsIntent
    data object PauseAll : DownloadsIntent
    data object ResumeAll : DownloadsIntent
    data object ClearError : DownloadsIntent
}

/** 下载队列一次性副作用：提示类事件（导航另行走路由）。 */
sealed interface DownloadsLabel {
    data class Message(val text: String) : DownloadsLabel
}

/** 内部消息：Executor 计算结果 → Reducer 纯转移。 */
internal sealed interface DownloadsMsg {
    data object LoadStarted : DownloadsMsg
    data class Loaded(val entries: List<DownloadTask>) : DownloadsMsg
    data class LoadFailed(val message: String) : DownloadsMsg
    data class ActionFailed(val message: String) : DownloadsMsg
    data object ErrorCleared : DownloadsMsg
}

/** 内部动作：Bootstrapper 发起的订阅。 */
internal sealed interface DownloadsAction {
    data object ObserveQueue : DownloadsAction
}

interface DownloadsStore : Store<DownloadsIntent, DownloadsState, DownloadsLabel>

/**
 * Store 工厂（Koin 以单例提供，`DefaultStoreFactory` 注入；日志/时光机模块未选型不引入）。
 *
 * 仓储经参数传入（会话绑定不在 Koin 里持有可空全局单例，与 Library 一致）。
 */
class DownloadsStoreFactory(
    private val storeFactory: StoreFactory = DefaultStoreFactory(),
) {
    fun create(repository: DownloadQueueRepository): DownloadsStore =
        object : DownloadsStore, Store<DownloadsIntent, DownloadsState, DownloadsLabel> by storeFactory.create(
            name = "DownloadsStore",
            initialState = DownloadsState(),
            bootstrapper = SimpleBootstrapper(DownloadsAction.ObserveQueue),
            executorFactory = { ExecutorImpl(repository) },
            reducer = ReducerImpl,
        ) {}

    private class ExecutorImpl(
        private val repository: DownloadQueueRepository,
    ) : CoroutineExecutor<DownloadsIntent, DownloadsAction, DownloadsState, DownloadsMsg, DownloadsLabel>() {
        override fun executeAction(action: DownloadsAction) {
            when (action) {
                DownloadsAction.ObserveQueue -> scope.launch {
                    repository.changes.collect { load(showLoading = false) }
                }
            }
        }

        override fun executeIntent(intent: DownloadsIntent) {
            when (intent) {
                DownloadsIntent.Refresh -> scope.launch { load(showLoading = true) }
                is DownloadsIntent.Pause -> scope.launch { mutate { pause(intent.id) } }
                is DownloadsIntent.Resume -> scope.launch { mutate { resume(intent.id) } }
                is DownloadsIntent.Retry -> scope.launch { mutate { retry(intent.id) } }
                is DownloadsIntent.Cancel -> scope.launch { mutate { cancel(intent.id) } }
                DownloadsIntent.Clear -> scope.launch { mutate { clear() } }
                DownloadsIntent.PauseAll -> scope.launch {
                    state().entries
                        .filter { it.status == FakeDownloadStatus.QUEUED || it.status == FakeDownloadStatus.DOWNLOADING }
                        .forEach { entry ->
                            try {
                                repository.pause(entry.id)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // 单项失败不中断整批，最终 reload 收敛与 Session 一致。
                            }
                        }
                    load(showLoading = false)
                }
                DownloadsIntent.ResumeAll -> scope.launch {
                    state().entries
                        .filter { it.status == FakeDownloadStatus.PAUSED }
                        .forEach { entry ->
                            try {
                                repository.resume(entry.id)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // 同上。
                            }
                        }
                    load(showLoading = false)
                }
                DownloadsIntent.ClearError -> dispatch(DownloadsMsg.ErrorCleared)
            }
        }

        private suspend fun load(showLoading: Boolean) {
            // 首帧 Loading 与 Session 初始态一致；订阅触发的静默刷新不闪骨架屏。
            if (showLoading) dispatch(DownloadsMsg.LoadStarted)
            try {
                val entries = repository.loadQueue()
                dispatch(DownloadsMsg.Loaded(entries))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val message = failure.message?.takeIf { it.isNotBlank() } ?: "Couldn't load downloads"
                dispatch(DownloadsMsg.LoadFailed(message))
                // 后台静默刷新失败与 Session 一致翻整页 Failed，另附一条提示 Label；
                // 前景刷新整页错误已足够不再重复提示。
                if (!showLoading) publish(DownloadsLabel.Message(message))
            }
        }

        private suspend fun mutate(block: suspend DownloadQueueRepository.() -> Unit) {
            try {
                repository.block()
                dispatch(DownloadsMsg.Loaded(repository.loadQueue()))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val message = failure.message?.takeIf { it.isNotBlank() } ?: "Couldn't update download"
                dispatch(DownloadsMsg.ActionFailed(message))
                publish(DownloadsLabel.Message(message))
            }
        }
    }

    private object ReducerImpl : Reducer<DownloadsState, DownloadsMsg> {
        override fun DownloadsState.reduce(msg: DownloadsMsg): DownloadsState = when (msg) {
            DownloadsMsg.LoadStarted -> copy(loadState = Loading)
            is DownloadsMsg.Loaded -> copy(loadState = Ready, entries = msg.entries, actionError = null)
            is DownloadsMsg.LoadFailed -> copy(loadState = Failed(msg.message), actionError = msg.message)
            is DownloadsMsg.ActionFailed -> copy(actionError = msg.message)
            DownloadsMsg.ErrorCleared -> copy(actionError = null)
        }
    }
}

/**
 * Store 的 ViewModel 持有者（与 LibraryViewModel 同模式）：
 * 生命周期跟随宿主；UI 观察 [uiState]、收 [labels] 做提示。
 */
class DownloadsStoreHolder(val store: DownloadsStore) : ViewModel() {
    val uiState: StateFlow<DownloadsState> = store.stateFlow
    val labels: Flow<DownloadsLabel> = store.labels

    override fun onCleared() {
        store.dispose()
        super.onCleared()
    }
}
