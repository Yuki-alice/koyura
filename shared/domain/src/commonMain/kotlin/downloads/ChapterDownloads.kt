package app.koyura.domain.downloads

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

data class DownloadChapter(val sourceKey: String, val workId: String, val chapterId: String?)

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, CANCELLED, FAILED, COMPLETE }

data class DownloadJob(
    val id: String,
    val chapter: DownloadChapter,
    val status: DownloadStatus,
    val downloadedPages: Int,
    val totalPages: Int?,
)

@Serializable
data class DownloadTriggerPreferences(
    val downloadNewChapters: Boolean = false,
    val downloadAheadCount: Int = 0,
) {
    init {
        require(downloadAheadCount >= 0)
    }
}

interface ChapterDownloads {
    val jobs: StateFlow<List<DownloadJob>>
    fun observe(chapter: DownloadChapter): Flow<DownloadJob?>
    suspend fun countCompleted(sourceKey: String, workId: String): Int
    suspend fun statusesForWork(sourceKey: String, workId: String): Map<String?, DownloadJob>
    suspend fun enqueue(chapter: DownloadChapter): DownloadJob
    suspend fun enqueueAll(
        sourceKey: String,
        workId: String,
        chapterIds: List<String?>,
        unreadOnly: Boolean = false,
        limit: Int? = null,
    ): List<DownloadJob>
    suspend fun pause(id: String)
    suspend fun cancel(id: String)
    suspend fun retry(id: String)
}
