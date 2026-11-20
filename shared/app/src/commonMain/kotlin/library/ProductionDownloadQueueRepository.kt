package app.koyura.app.library

import androidx.compose.runtime.staticCompositionLocalOf
import app.koyura.data.downloads.ChapterDownloadStore
import app.koyura.domain.downloads.DownloadChapter
import app.koyura.domain.downloads.DownloadStatus
import app.koyura.features.downloads.DownloadQueueRepository
import app.koyura.features.downloads.DownloadTask
import app.koyura.features.downloads.FakeDownloadStatus

internal val LocalProductionDownloads = staticCompositionLocalOf<ProductionDownloadQueueRepository?> { null }

class ProductionDownloadQueueRepository(private val store: ChapterDownloadStore) : DownloadQueueRepository {
    val jobs get() = store.jobs

    override suspend fun loadQueue(): List<DownloadTask> = store.loadQueue().mapNotNull { entry ->
        val job = entry.job
        val status = when (job.status) {
            DownloadStatus.QUEUED -> FakeDownloadStatus.QUEUED
            DownloadStatus.DOWNLOADING -> FakeDownloadStatus.DOWNLOADING
            DownloadStatus.PAUSED -> FakeDownloadStatus.PAUSED
            DownloadStatus.FAILED -> FakeDownloadStatus.FAILED
            DownloadStatus.COMPLETE, DownloadStatus.CANCELLED -> return@mapNotNull null
        }
        DownloadTask(
            job.id, job.chapter.sourceKey, job.chapter.workId, entry.workTitle,
            job.chapter.chapterId.orEmpty(), entry.chapterTitle, status, job.downloadedPages, job.totalPages ?: 0,
        )
    }

    override suspend fun pause(id: String) = store.pause(id)
    override suspend fun resume(id: String) = store.resume(id)
    override suspend fun retry(id: String) = store.retry(id)
    override suspend fun cancel(id: String) = store.cancel(id)
    override suspend fun clear() = store.clearQueue()

    suspend fun enqueue(sourceKey: String, workId: String, chapterId: String?) =
        store.enqueue(DownloadChapter(sourceKey, workId, chapterId))

    suspend fun statusesForWork(sourceKey: String, workId: String) = store.statusesForWork(sourceKey, workId)
}
