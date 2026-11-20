package app.koyura.app.reader

import app.koyura.data.downloads.DownloadTriggers
import app.koyura.data.history.HistoryStore
import app.koyura.domain.downloads.DownloadChapter
import app.koyura.data.reading.ComicReadingService
import app.koyura.domain.reader.ChapterContent
import app.koyura.domain.reader.ReaderProgress
import app.koyura.domain.reader.ReaderService
import app.koyura.domain.reader.ReadingDurationRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlinx.coroutines.withContext

internal class VeneraReaderService(
    private val reading: ComicReadingService,
    private val history: HistoryStore? = null,
    private val library: app.koyura.data.library.LibraryStore? = null,
    private val downloadTriggers: DownloadTriggers? = null,
) : ReaderService, ReadingDurationRecorder {
    private val displayedChapter = MutableStateFlow<Pair<DownloadChapter, Int>?>(null)

    override suspend fun recordReadingDuration(sourceKey: String, workId: String, chapterId: String?, elapsedMillis: Long) {
        history?.addReadDuration(sourceKey, workId, chapterId, elapsedMillis)
    }

    override suspend fun readProgress(sourceKey: String, workId: String): ReaderProgress? =
        withContext(Dispatchers.IO) {
            reading.readLatestProgress(sourceKey, workId)?.let { ReaderProgress(it.chapterId, it.pageIndex) }
        }

    override suspend fun openChapter(sourceKey: String, workId: String, chapterId: String?): ChapterContent =
        withContext(Dispatchers.IO) {
            val chapter = reading.openChapter(sourceKey, workId, chapterId)
            val latest = reading.readLatestProgress(sourceKey, workId)
            displayedChapter.value = DownloadChapter(sourceKey, workId, chapter.chapterId) to chapter.pages.size
            ChapterContent(
                chapterId = chapter.chapterId,
                title = "",
                pageKeys = chapter.pages.map { it.pageKey },
                startPageIndex = chapter.startIndex,
                imageKeys = chapter.pages.filter { it.imageKey != it.pageKey }.associate { it.pageKey to it.imageKey },
                saveInitialPosition = chapter.startPageKey != null && (
                    latest == null || latest.chapterId != chapter.chapterId ||
                        latest.pageKey != chapter.startPageKey || latest.pageIndex != chapter.startIndex
                ),
            )
        }

    override suspend fun loadPageBytes(sourceKey: String, workId: String, chapterId: String?, imageKey: String): ByteArray =
        withContext(Dispatchers.IO) { reading.loadImageBytes(sourceKey, workId, chapterId, imageKey) }

    override suspend fun saveProgress(
        sourceKey: String,
        workId: String,
        chapterId: String?,
        pageKey: String,
        pageIndex: Int,
        workTitle: String?,
        chapterTitle: String?,
        isLastPage: Boolean,
    ): Unit = withContext(Dispatchers.IO) {
        val historyStore = history
        if (historyStore == null) {
            // No history store: keep the position-only writer for old callers.
            reading.saveProgress(sourceKey, workId, chapterId, pageKey, pageIndex)
        } else {
            // HistoryStore commits progress, metadata and history visibility together.
            historyStore.save(
                sourceKey = sourceKey,
                workId = workId,
                chapterId = chapterId,
                pageKey = pageKey,
                pageIndex = pageIndex,
                readAtMillis = Clock.System.now().toEpochMilliseconds(),
                workTitle = workTitle?.takeIf { it.isNotBlank() },
                chapterTitle = chapterTitle?.takeIf { it.isNotBlank() },
            )
        }
        // 读到末页时记录章节已读，中途页只更新进度；章节表缺失时不建行（目录同步负责建行）。
        if (isLastPage) {
            val store = library
            if (store != null) {
                try {
                    val kind = app.koyura.domain.library.WorkKind.COMIC
                    val existing = store.listChapters(sourceKey, kind, workId)
                    if (existing.any { it.chapterId == (chapterId ?: "") }) {
                        store.markChapterRead(sourceKey, kind, workId, chapterId ?: "", true)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 已读标记失败不回滚进度，进度已提交为准。
                }
            }
        }
        val displayed = displayedChapter.value
        if (chapterId != null && displayed?.first == DownloadChapter(sourceKey, workId, chapterId)
            && (pageIndex + 1L) * 4 > displayed.second
        ) {
            try {
                downloadTriggers?.enqueueAheadFrom(sourceKey, workId, chapterId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The position is already committed; the next page can try the optional prefetch again.
            }
        }
        Unit
    }
}
