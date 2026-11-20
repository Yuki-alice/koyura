package app.koyura.features.downloads

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Fake 下载状态：对齐数据层 `ChapterDownloadStore` 的状态机语义
 *（`DownloadStatus`：排队/下载中/暂停/失败/完成；取消与完成为终态，不再展示）。
 */
enum class FakeDownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    FAILED,
}

/** 队列中的一个下载任务（Fake 展示模型，真实引擎接入后替换为 Domain 模型）。 */
data class DownloadTask(
    val id: String,
    val sourceKey: String,
    val workId: String,
    val workTitle: String,
    val chapterId: String,
    val chapterTitle: String,
    val status: FakeDownloadStatus,
    val downloadedPages: Int,
    val totalPages: Int,
)

/** 下载队列表数据源。队列改变时调用方可重新读取持久快照。 */
interface DownloadQueueRepository {
    val changes: Flow<Unit> get() = emptyFlow()
    suspend fun loadQueue(): List<DownloadTask>
    suspend fun pause(id: String)
    suspend fun resume(id: String)
    suspend fun retry(id: String)
    suspend fun cancel(id: String)
    suspend fun clear()
}

/** 下载队列加载态（对齐 Updates 三态）。 */
sealed interface DownloadQueueLoadState {
    data object Loading : DownloadQueueLoadState
    data object Ready : DownloadQueueLoadState
    data class Failed(val message: String) : DownloadQueueLoadState
}
