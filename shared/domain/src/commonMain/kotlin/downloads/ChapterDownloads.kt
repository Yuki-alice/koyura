package app.koyura.domain.downloads

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class DownloadChapter(val sourceKey: String, val workId: String, val chapterId: String?)

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, CANCELLED, FAILED, COMPLETE }

data class DownloadJob(
    val id: String,
    val chapter: DownloadChapter,
    val status: DownloadStatus,
    val downloadedPages: Int,
    val totalPages: Int?,
)

interface ChapterDownloads {
    val jobs: StateFlow<List<DownloadJob>>
    fun observe(chapter: DownloadChapter): Flow<DownloadJob?>
    suspend fun countCompleted(sourceKey: String, workId: String): Int
    suspend fun enqueue(chapter: DownloadChapter): DownloadJob
    suspend fun pause(id: String)
    suspend fun cancel(id: String)
    suspend fun retry(id: String)
}
