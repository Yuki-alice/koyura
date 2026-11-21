package app.koyura.features.reader.comic

import androidx.lifecycle.ViewModel
import app.koyura.domain.reader.ChapterContent
import app.koyura.domain.reader.ReaderService
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 阅读器 MVI 样板（阶段 1 第三棒，最复杂的状态机）。
 *
 * 与 [ReaderSession] 同语义：open token 丢弃慢到的旧章节结果，
 * 页级 token 丢弃重试前的迟到到达，加载中/已就绪不重复提交，
 * 打开已存位置不覆盖进度，落盘互斥且去重。
 *
 * 关键差异：token簿与绑定上下文收归 [Executor] 本地同步字段
 * （MVIKotlin 的 dispatch/reduce 是异步的读 `state()` 会滞后，
 * 并发判定必须看执行器内同步更新的簿册不能看 state）。
 * 状态只放 UI 渲染需要的不可变快照。
 */
data class ReaderState(
    val content: ReaderContentState = ReaderContentState.Loading,
    val currentPage: Int = 0,
    val pages: Map<String, ReaderPageState> = emptyMap(),
    val contentSeq: Long = 0L,
    val chapterIds: List<String> = emptyList(),
) {
    /** Chapter id before [chapterId] in source order, or null at the first chapter. */
    fun previousChapterId(chapterId: String?): String? {
        if (chapterId == null) return null
        val index = chapterIds.indexOf(chapterId)
        return if (index > 0) chapterIds[index - 1] else null
    }

    /** Chapter id after [chapterId] in source order, or null at the last chapter. */
    fun nextChapterId(chapterId: String?): String? {
        if (chapterId == null) return null
        val index = chapterIds.indexOf(chapterId)
        return if (index >= 0 && index + 1 < chapterIds.size) chapterIds[index + 1] else null
    }
}

/** 阅读器意图：开章、取页、重试、翻页上报、落盘。 */
sealed interface ReaderIntent {
    data class Open(
        val sourceKey: String,
        val workId: String,
        val chapterId: String?,
        val chapterIds: List<String>,
        val workTitle: String? = null,
        val chapterTitles: List<String> = emptyList(),
    ) : ReaderIntent

    data class EnsurePage(val pageKey: String) : ReaderIntent
    data class RetryPage(val pageKey: String) : ReaderIntent
    data class MarkDisplayed(val index: Int) : ReaderIntent
    data class SavePage(val index: Int) : ReaderIntent
    data object SaveCurrent : ReaderIntent
}

/** 内部消息：Executor 计算结果 → Reducer 纯转移。 */
internal sealed interface ReaderMsg {
    data class OpenStarted(val chapterIds: List<String>) : ReaderMsg
    data class ContentReady(val content: ChapterContent, val startPage: Int) : ReaderMsg
    data class ContentFailed(val message: String) : ReaderMsg
    data class PageStarted(val key: String) : ReaderMsg
    data class PageReady(val key: String, val bytes: ByteArray) : ReaderMsg
    data class PageFailed(val key: String, val message: String) : ReaderMsg
    data class PageCleared(val key: String) : ReaderMsg
    data class Displayed(val index: Int) : ReaderMsg
}

interface ReaderStore : Store<ReaderIntent, ReaderState, Nothing>

/**
 * Store 工厂（Koin 以单例提供，`DefaultStoreFactory` 注入；日志/时光机模块未选型不引入）。
 *
 * 仓储经参数传入（会话绑定不在 Koin 里持有可空全局单例与之前各 Store 一致）。
 */
class ReaderStoreFactory(
    private val storeFactory: StoreFactory = DefaultStoreFactory(),
) {
    fun create(repository: ReaderService?): ReaderStore =
        object : ReaderStore, Store<ReaderIntent, ReaderState, Nothing> by storeFactory.create(
            name = "ReaderStore",
            initialState = ReaderState(),
            bootstrapper = SimpleBootstrapper(),
            executorFactory = { ExecutorImpl(repository) },
            reducer = ReducerImpl,
        ) {
        }

    private class ExecutorImpl(
        private val repository: ReaderService?,
    ) : CoroutineExecutor<ReaderIntent, Nothing, ReaderState, ReaderMsg, Nothing>() {
        private var openToken = 0L
        private val pageTokens = mutableMapOf<String, Long>()
        private val inflight = mutableSetOf<String>()
        private var lastSavedIndex: Int? = null
        private val saveMutex = Mutex()

        private var boundSourceKey = ""
        private var boundWorkId = ""
        private var boundWorkTitle: String? = null
        private var boundChapterTitles: Map<String, String> = emptyMap()

        override fun executeIntent(intent: ReaderIntent) {
            when (intent) {
                is ReaderIntent.Open -> {
                    val service = repository ?: return
                    val token = ++openToken
                    val chapters = intent.chapterIds.toList()
                    boundSourceKey = intent.sourceKey
                    boundWorkId = intent.workId
                    boundWorkTitle = intent.workTitle
                    boundChapterTitles = intent.chapterIds.zip(intent.chapterTitles).toMap()
                    pageTokens.clear()
                    inflight.clear()
                    lastSavedIndex = null
                    dispatch(ReaderMsg.OpenStarted(chapters))
                    scope.launch {
                        loadChapter(service, token, intent.sourceKey, intent.workId, intent.chapterId)
                    }
                }
                is ReaderIntent.EnsurePage -> ensure(intent.pageKey, force = false)
                is ReaderIntent.RetryPage -> {
                    inflight.remove(intent.pageKey)
                    val pageToken = (pageTokens[intent.pageKey] ?: 0L) + 1
                    pageTokens[intent.pageKey] = pageToken
                    dispatch(ReaderMsg.PageCleared(intent.pageKey))
                    ensure(intent.pageKey, force = true)
                }
                is ReaderIntent.MarkDisplayed -> dispatch(ReaderMsg.Displayed(intent.index))
                is ReaderIntent.SavePage -> scope.launch { savePage(intent.index) }
                ReaderIntent.SaveCurrent -> scope.launch { savePage(state().currentPage) }
            }
        }

        private suspend fun loadChapter(
            service: ReaderService,
            token: Long,
            sourceKey: String,
            workId: String,
            chapterId: String?,
        ) {
            try {
                val opened = service.openChapter(sourceKey, workId, chapterId)
                if (token != openToken) return
                val startPage = opened.startPageIndex.coerceInPageRange(opened.pageKeys.size)
                if (!opened.saveInitialPosition) lastSavedIndex = startPage
                dispatch(ReaderMsg.ContentReady(opened, startPage))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (token == openToken) {
                    dispatch(ReaderMsg.ContentFailed(failure.userMessage()))
                }
            }
        }

        /**
         * 取页守卫（执行线程内同步判定无挂起点因此原子）：
         * inflight 优先（同步准确），state 兜底（只可能滞后绝不超前，滞后方向安全）。
         */
        private fun ensure(pageKey: String, force: Boolean) {
            val service = repository ?: return
            val ready = state().content as? ReaderContentState.Ready ?: return
            if (!force) {
                if (pageKey in inflight) return
                when (state().pages[pageKey]) {
                    is ReaderPageState.Loading, is ReaderPageState.Ready -> return
                    else -> Unit
                }
            }
            val token = openToken
            val pageToken = (pageTokens[pageKey] ?: 0L) + 1
            pageTokens[pageKey] = pageToken
            inflight.add(pageKey)
            dispatch(ReaderMsg.PageStarted(pageKey))
            scope.launch {
                try {
                    val bytes = service.loadPageBytes(
                        boundSourceKey,
                        boundWorkId,
                        ready.content.chapterId,
                        ready.content.imageKeys[pageKey] ?: pageKey,
                    )
                    if (token == openToken && pageTokens[pageKey] == pageToken) {
                        dispatch(ReaderMsg.PageReady(pageKey, bytes))
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    if (token == openToken && pageTokens[pageKey] == pageToken) {
                        dispatch(ReaderMsg.PageCleared(pageKey))
                    }
                    throw cancelled
                } catch (failure: Exception) {
                    if (token == openToken && pageTokens[pageKey] == pageToken) {
                        dispatch(ReaderMsg.PageFailed(pageKey, failure.userMessage()))
                    }
                } finally {
                    inflight.remove(pageKey)
                }
            }
        }

        private suspend fun savePage(index: Int) {
            val service = repository ?: return
            val ready = state().content as? ReaderContentState.Ready ?: return
            val pageKey = ready.content.pageKeys.getOrNull(index) ?: return
            val token = openToken
            saveMutex.withLock {
                if (token != openToken || index == lastSavedIndex) return
                val isLast = ready.content.pageKeys.isNotEmpty() && index == ready.content.pageKeys.size - 1
                service.saveProgress(
                    boundSourceKey,
                    boundWorkId,
                    ready.content.chapterId,
                    pageKey,
                    index,
                    workTitle = boundWorkTitle,
                    chapterTitle = boundChapterTitles[ready.content.chapterId],
                    isLastPage = isLast,
                )
                if (token == openToken) lastSavedIndex = index
            }
        }

        private fun Int.coerceInPageRange(size: Int): Int = when {
            size <= 0 -> 0
            this < 0 -> 0
            this >= size -> size - 1
            else -> this
        }

        private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Operation failed"
    }

    private object ReducerImpl : Reducer<ReaderState, ReaderMsg> {
        override fun ReaderState.reduce(msg: ReaderMsg): ReaderState = when (msg) {
            is ReaderMsg.OpenStarted -> copy(
                content = ReaderContentState.Loading,
                currentPage = 0,
                pages = emptyMap(),
                contentSeq = contentSeq + 1,
                chapterIds = msg.chapterIds,
            )
            is ReaderMsg.ContentReady -> copy(content = ReaderContentState.Ready(msg.content), currentPage = msg.startPage)
            is ReaderMsg.ContentFailed -> copy(content = ReaderContentState.Failed(msg.message))
            is ReaderMsg.PageStarted -> copy(pages = pages + (msg.key to ReaderPageState.Loading))
            is ReaderMsg.PageReady -> copy(pages = pages + (msg.key to ReaderPageState.Ready(msg.bytes)))
            is ReaderMsg.PageFailed -> copy(pages = pages + (msg.key to ReaderPageState.Failed(msg.message)))
            is ReaderMsg.PageCleared -> copy(pages = pages - msg.key)
            is ReaderMsg.Displayed -> copy(currentPage = msg.index)
        }
    }
}

/**
 * Store 的 ViewModel 持有者（与之前各 Store 同模式）：
 * 生命周期跟随宿主；UI 观察 [uiState]。
 *
 * 失败态即 state（章节/页各自 Failed），无额外 Label 通道。
 */
class ReaderStoreHolder(val store: ReaderStore) : ViewModel() {
    val uiState: StateFlow<ReaderState> = store.stateFlow

    override fun onCleared() {
        store.dispose()
        super.onCleared()
    }
}
