package app.koyura.features.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 下载队列页状态座，沿用 `UpdatesSession` 模式。
 *
 * 状态迁移对齐 `ChapterDownloadStore`：暂停仅作用于排队/下载中，
 * 恢复与重试回到排队，取消与完成为终态（从可见队列移除）。
 */
class DownloadsSession(
    private val repository: DownloadQueueRepository,
) {
    private val writes = Mutex()

    var loadState: DownloadQueueLoadState by mutableStateOf(DownloadQueueLoadState.Loading)
        private set

    var entries: List<DownloadTask> by mutableStateOf(emptyList())
        private set

    var actionError: String? by mutableStateOf(null)
        private set

    /** 是否有活跃任务（FAB 暂停/恢复文案的数据源）。 */
    val running: Boolean
        get() = entries.any { it.status == FakeDownloadStatus.QUEUED || it.status == FakeDownloadStatus.DOWNLOADING }

    suspend fun refresh() = refresh(showLoading = true)

    internal suspend fun observeQueue() {
        repository.changes.collect { refresh(showLoading = false) }
    }

    private suspend fun refresh(showLoading: Boolean) = writes.withLock {
        if (showLoading) loadState = DownloadQueueLoadState.Loading
        try {
            entries = repository.loadQueue()
            actionError = null
            loadState = DownloadQueueLoadState.Ready
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            actionError = failure.message?.takeIf { it.isNotBlank() } ?: "Couldn't load downloads"
            loadState = DownloadQueueLoadState.Failed(actionError!!)
        }
    }

    private suspend fun mutate(action: suspend DownloadQueueRepository.() -> Unit) = writes.withLock {
        try {
            repository.action()
            entries = repository.loadQueue()
            actionError = null
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            actionError = failure.message?.takeIf { it.isNotBlank() } ?: "Couldn't update download"
        }
    }

    suspend fun pause(id: String) = mutate { pause(id) }

    suspend fun resume(id: String) = mutate { resume(id) }

    suspend fun retry(id: String) = mutate { retry(id) }

    suspend fun cancel(id: String) = mutate { cancel(id) }

    suspend fun clear() = mutate { clear() }

    suspend fun pauseAll() {
        entries.filter {
            it.status == FakeDownloadStatus.QUEUED || it.status == FakeDownloadStatus.DOWNLOADING
        }.forEach { pause(it.id) }
    }

    suspend fun resumeAll() {
        entries.filter { it.status == FakeDownloadStatus.PAUSED }.forEach { resume(it.id) }
    }

    fun clearError() {
        actionError = null
    }
}

val LocalDownloadQueueRepository = staticCompositionLocalOf<DownloadQueueRepository?> { null }
val LocalDownloadQueueVisible = staticCompositionLocalOf { false }

@Composable
fun rememberDownloadsSession(
    repository: DownloadQueueRepository? = LocalDownloadQueueRepository.current,
): DownloadsSession {
    val resolved = repository ?: remember { FakeDownloadRepository() }
    val session = remember(resolved) { DownloadsSession(resolved) }
    if (LocalDownloadQueueVisible.current) {
        LaunchedEffect(session) { session.observeQueue() }
    }
    return session
}
