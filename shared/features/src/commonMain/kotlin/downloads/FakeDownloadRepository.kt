package app.koyura.features.downloads

/**
 * 下载队列 Fake 仓储：默认空（与白皮书 32_download_queue 空态截图一致）。
 *
 * 状态迁移与 `ChapterDownloadStore` 同语义：暂停仅收排队/下载中，
 * 恢复/重试回到排队，取消移除任务；失败任务保留以便重试。
 */
class FakeDownloadRepository(
    tasks: List<DownloadTask> = emptyList(),
    private val failure: String? = null,
) : DownloadQueueRepository {
    private val tasks = tasks.toMutableList()

    override suspend fun loadQueue(): List<DownloadTask> {
        failure?.let { throw IllegalStateException(it) }
        return tasks.toList()
    }

    override suspend fun pause(id: String) {
        mutate(id) {
            if (it.status == FakeDownloadStatus.QUEUED || it.status == FakeDownloadStatus.DOWNLOADING) {
                it.copy(status = FakeDownloadStatus.PAUSED)
            } else {
                it
            }
        }
    }

    override suspend fun resume(id: String) {
        mutate(id) {
            if (it.status == FakeDownloadStatus.PAUSED) it.copy(status = FakeDownloadStatus.QUEUED) else it
        }
    }

    override suspend fun retry(id: String) {
        mutate(id) {
            if (it.status == FakeDownloadStatus.FAILED) it.copy(status = FakeDownloadStatus.QUEUED) else it
        }
    }

    override suspend fun cancel(id: String) {
        tasks.removeAll { it.id == id }
    }

    override suspend fun clear() {
        tasks.clear()
    }

    private fun mutate(id: String, transform: (DownloadTask) -> DownloadTask) {
        val index = tasks.indexOfFirst { it.id == id }
        if (index >= 0) tasks[index] = transform(tasks[index])
    }

    companion object {
        fun sampleEntries(): List<DownloadTask> = listOf(
            DownloadTask(
                id = "job-1",
                sourceKey = "demo-source",
                workId = "kaze-no-kioku",
                workTitle = "Kaze no Kioku",
                chapterId = "ch-3",
                chapterTitle = "Chapter 3",
                status = FakeDownloadStatus.DOWNLOADING,
                downloadedPages = 3,
                totalPages = 10,
            ),
            DownloadTask(
                id = "job-2",
                sourceKey = "demo-source",
                workId = "hoshi-no-utahime",
                workTitle = "Hoshizora no Utahime",
                chapterId = "ch-12",
                chapterTitle = "Chapter 12",
                status = FakeDownloadStatus.QUEUED,
                downloadedPages = 0,
                totalPages = 8,
            ),
            DownloadTask(
                id = "job-3",
                sourceKey = "demo-source",
                workId = "midnight-ramen",
                workTitle = "Midnight Ramen Club",
                chapterId = "ch-7",
                chapterTitle = "Chapter 7",
                status = FakeDownloadStatus.FAILED,
                downloadedPages = 2,
                totalPages = 12,
            ),
        )
    }
}
