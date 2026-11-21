package app.koyura.features.updates

import androidx.lifecycle.ViewModel
import app.koyura.features.updates.UpdatesLoadState.Failed
import app.koyura.features.updates.UpdatesLoadState.Loading
import app.koyura.features.updates.UpdatesLoadState.Ready
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
 * 更新页 MVI 样板（阶段 1 第二棒）。
 *
 * 状态机与 [UpdatesSession] 同语义：失败只记 `actionError` 已提交快照保留，
 * 取消不是失败；`checkNow` 触发真实扫描，`retryFailures` 只重试失败作品。
 * 区别在显式 [UpdatesIntent]/[UpdatesState]/[UpdatesLabel]，
 * 转移规则全进 [Reducer]；副作用（仓储读写）全进 [Executor]。
 */
data class UpdatesState(
    val loadState: UpdatesLoadState = Loading,
    val entries: List<UpdateWork> = emptyList(),
    val failures: List<UpdateFailure> = emptyList(),
    val query: String = "",
    val actionError: String? = null,
) {
    /** 标题筛选后的条目；query 为空时即全量。 */
    val visibleEntries: List<UpdateWork>
        get() {
            val needle = query.trim()
            if (needle.isEmpty()) return entries
            return entries.filter { it.title.contains(needle, ignoreCase = true) }
        }

    /** 待读章节总数（底栏 Updates 徽标的数据源；按全量快照统计不随筛选变）。 */
    val unreadCount: Int
        get() = entries.sumOf { it.chapters.size }
}

/** 更新页意图：刷新 / 真实扫描 / 重试失败 / 标题筛选 / 清错。 */
sealed interface UpdatesIntent {
    data object Refresh : UpdatesIntent
    data object CheckNow : UpdatesIntent
    data object RetryFailures : UpdatesIntent
    data class UpdateQuery(val value: String) : UpdatesIntent
    data object ClearError : UpdatesIntent
}

/** 更新页一次性副作用：提示类事件。 */
sealed interface UpdatesLabel {
    data class Message(val text: String) : UpdatesLabel
}

/** 内部消息：Executor 计算结果 → Reducer 纯转移。 */
internal sealed interface UpdatesMsg {
    data object LoadStarted : UpdatesMsg
    data class Loaded(val entries: List<UpdateWork>, val failures: List<UpdateFailure>) : UpdatesMsg
    data class LoadFailed(val message: String) : UpdatesMsg
    data class QueryChanged(val value: String) : UpdatesMsg
    data object ErrorCleared : UpdatesMsg
}

interface UpdatesStore : Store<UpdatesIntent, UpdatesState, UpdatesLabel>

/**
 * Store 工厂（Koin 以单例提供，`DefaultStoreFactory` 注入；日志/时光机模块未选型不引入）。
 *
 * 仓储经参数传入（会话绑定不在 Koin 里持有可空全局单例与 Library/downloads 一致）。
 */
class UpdatesStoreFactory(
    private val storeFactory: StoreFactory = DefaultStoreFactory(),
) {
    fun create(repository: UpdatesRepository): UpdatesStore =
        object : UpdatesStore, Store<UpdatesIntent, UpdatesState, UpdatesLabel> by storeFactory.create(
            name = "UpdatesStore",
            initialState = UpdatesState(),
            bootstrapper = SimpleBootstrapper(),
            executorFactory = { ExecutorImpl(repository) },
            reducer = ReducerImpl,
        ) {
        }

    private class ExecutorImpl(
        private val repository: UpdatesRepository,
    ) : CoroutineExecutor<UpdatesIntent, Nothing, UpdatesState, UpdatesMsg, UpdatesLabel>() {
        override fun executeIntent(intent: UpdatesIntent) {
            when (intent) {
                UpdatesIntent.Refresh -> scope.launch { load { repository.loadUpdates() } }
                UpdatesIntent.CheckNow -> scope.launch { load { repository.checkNow() } }
                UpdatesIntent.RetryFailures -> scope.launch { load { repository.retryFailures() } }
                is UpdatesIntent.UpdateQuery -> dispatch(UpdatesMsg.QueryChanged(intent.value))
                UpdatesIntent.ClearError -> dispatch(UpdatesMsg.ErrorCleared)
            }
        }

        private suspend fun load(source: suspend () -> List<UpdateWork>) {
            dispatch(UpdatesMsg.LoadStarted)
            try {
                val entries = source()
                val failures = repository.failures()
                dispatch(UpdatesMsg.Loaded(entries, failures))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val message = failure.message?.takeIf { it.isNotBlank() } ?: "Couldn't load updates"
                dispatch(UpdatesMsg.LoadFailed(message))
                publish(UpdatesLabel.Message(message))
            }
        }
    }

    private object ReducerImpl : Reducer<UpdatesState, UpdatesMsg> {
        override fun UpdatesState.reduce(msg: UpdatesMsg): UpdatesState = when (msg) {
            UpdatesMsg.LoadStarted -> copy(loadState = Loading)
            is UpdatesMsg.Loaded -> copy(
                loadState = Ready,
                entries = msg.entries,
                failures = msg.failures,
                actionError = null,
            )
            is UpdatesMsg.LoadFailed -> copy(loadState = Failed(msg.message), actionError = msg.message)
            is UpdatesMsg.QueryChanged -> copy(query = msg.value)
            UpdatesMsg.ErrorCleared -> copy(actionError = null)
        }
    }
}

/**
 * Store 的 ViewModel 持有者（与 LibraryViewModel/downloads 同模式）：
 * 生命周期跟随宿主；UI 观察 [uiState]、收 [labels] 做提示。
 */
class UpdatesStoreHolder(val store: UpdatesStore) : ViewModel() {
    val uiState: StateFlow<UpdatesState> = store.stateFlow
    val labels: Flow<UpdatesLabel> = store.labels

    override fun onCleared() {
        store.dispose()
        super.onCleared()
    }
}
