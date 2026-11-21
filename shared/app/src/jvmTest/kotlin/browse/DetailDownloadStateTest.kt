package app.koyura.app.browse

import app.koyura.designsystem.components.ChapterDownloadState
import app.koyura.domain.downloads.DownloadChapter
import app.koyura.domain.downloads.DownloadJob
import app.koyura.domain.downloads.DownloadStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 详情行指示器状态映射：队列任务 → 五态 + 进度分数。
 */
class DetailDownloadStateTest {
    private fun job(status: DownloadStatus, downloaded: Int = 0, total: Int? = null) = DownloadJob(
        id = "job",
        chapter = DownloadChapter("src", "work", "ch"),
        status = status,
        downloadedPages = downloaded,
        totalPages = total,
    )

    @Test
    fun mapsQueueStatesToIndicator() {
        assertEquals(ChapterDownloadState.NOT_DOWNLOADED, null.toChapterDownloadState())
        assertEquals(ChapterDownloadState.QUEUE, job(DownloadStatus.QUEUED).toChapterDownloadState())
        assertEquals(ChapterDownloadState.QUEUE, job(DownloadStatus.PAUSED).toChapterDownloadState())
        assertEquals(ChapterDownloadState.DOWNLOADING, job(DownloadStatus.DOWNLOADING).toChapterDownloadState())
        assertEquals(ChapterDownloadState.ERROR, job(DownloadStatus.FAILED).toChapterDownloadState())
        assertEquals(ChapterDownloadState.DOWNLOADED, job(DownloadStatus.COMPLETE).toChapterDownloadState())
        assertEquals(ChapterDownloadState.NOT_DOWNLOADED, job(DownloadStatus.CANCELLED).toChapterDownloadState())
    }

    @Test
    fun progressFractionClampsAndHandlesUnknownTotal() {
        assertEquals(0f, null.progressFraction())
        assertEquals(0f, job(DownloadStatus.DOWNLOADING).progressFraction())
        assertEquals(0f, job(DownloadStatus.DOWNLOADING, total = 0).progressFraction())
        assertEquals(0.5f, job(DownloadStatus.DOWNLOADING, downloaded = 5, total = 10).progressFraction())
        assertEquals(1f, job(DownloadStatus.DOWNLOADING, downloaded = 12, total = 10).progressFraction())
    }
}
