package app.koyura.data.downloads

import app.koyura.domain.downloads.ChapterDownloads
import app.koyura.domain.downloads.DownloadJob
import app.koyura.domain.downloads.DownloadStatus
import app.koyura.domain.downloads.DownloadTriggerPreferences
import app.koyura.domain.library.LibraryRepository
import app.koyura.domain.library.WorkKind
import app.koyura.domain.updates.NewChaptersDownloadHook
import app.koyura.domain.updates.WorkUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class DownloadTriggers internal constructor(
    private val library: LibraryRepository,
    private val downloads: ChapterDownloads,
) : NewChaptersDownloadHook {
    suspend fun preferences(): DownloadTriggerPreferences = withContext(Dispatchers.IO) {
        library.getPref(PREFERENCE)?.let {
            runCatching { preferenceJson.decodeFromString<DownloadTriggerPreferences>(it) }.getOrNull()
        } ?: DownloadTriggerPreferences()
    }

    suspend fun setPreferences(value: DownloadTriggerPreferences) = withContext(Dispatchers.IO) {
        library.setPref(PREFERENCE, preferenceJson.encodeToString(value))
    }

    override suspend fun onNewChapters(update: WorkUpdate) {
        if (!preferences().downloadNewChapters) return
        // The update feed lists the newest chapter first. Queue in reading order.
        downloads.enqueueAll(update.work.sourceKey, update.work.workId, update.newChapters.asReversed().map { it.id })
    }

    /** [followingChapterIds] starts after the current reading position, in reading order. */
    suspend fun enqueueNext(sourceKey: String, workId: String, followingChapterIds: List<String?>): List<DownloadJob> =
        downloads.enqueueAll(sourceKey, workId, followingChapterIds, unreadOnly = true, limit = preferences().downloadAheadCount)

    /** Keep ahead of an offline read once both the displayed chapter and its successor are available. */
    suspend fun enqueueAheadFrom(sourceKey: String, workId: String, chapterId: String) {
        val limit = preferences().downloadAheadCount
        if (limit == 0) return
        val jobs = downloads.statusesForWork(sourceKey, workId)
        if (jobs[chapterId]?.status != DownloadStatus.COMPLETE) return
        val ids = library.listChapters(sourceKey, WorkKind.COMIC, workId).map { it.chapterId }
        val current = ids.indexOf(chapterId)
        if (current < 0) return
        val next = ids.getOrNull(current + 1) ?: return
        if (jobs[next]?.status != DownloadStatus.COMPLETE) return
        downloads.enqueueAll(sourceKey, workId, ids.drop(current + 2), unreadOnly = true, limit = limit)
    }

    private companion object {
        const val PREFERENCE = "downloads.triggers"
        val preferenceJson = Json { ignoreUnknownKeys = true }
    }
}
