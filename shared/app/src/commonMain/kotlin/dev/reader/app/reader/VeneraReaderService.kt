package dev.reader.app.reader

import dev.reader.data.reading.ComicReadingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

internal class VeneraReaderService(private val reading: ComicReadingService) : ReaderService {
    override suspend fun readProgress(sourceKey: String, workId: String): ReaderProgress? =
        withContext(Dispatchers.IO) {
            reading.readLatestProgress(sourceKey, workId)?.let { ReaderProgress(it.chapterId, it.pageIndex) }
        }

    override suspend fun openChapter(sourceKey: String, workId: String, chapterId: String?): ChapterContent =
        withContext(Dispatchers.IO) {
            val chapter = reading.openChapter(sourceKey, workId, chapterId)
            val latest = reading.readLatestProgress(sourceKey, workId)
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
    ) = withContext(Dispatchers.IO) {
        reading.saveProgress(sourceKey, workId, chapterId, pageKey, pageIndex)
    }
}
