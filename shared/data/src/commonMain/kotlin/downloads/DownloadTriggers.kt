package app.koyura.data.downloads

import app.koyura.domain.downloads.ChapterDownloads
import app.koyura.domain.downloads.DownloadJob
import app.koyura.domain.downloads.DownloadTriggerPreferences
import app.koyura.domain.library.LibraryRepository
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

    private companion object {
        const val PREFERENCE = "downloads.triggers"
        val preferenceJson = Json { ignoreUnknownKeys = true }
    }
}
