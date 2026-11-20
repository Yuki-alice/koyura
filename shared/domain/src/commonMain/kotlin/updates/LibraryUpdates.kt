package app.koyura.domain.updates

import app.koyura.domain.library.LibraryManga
import kotlinx.serialization.Serializable

data class DetectedChapter(
    val id: String,
    val title: String,
    val detectedAtMillis: Long,
    val isRead: Boolean,
)

data class WorkUpdate(
    val work: LibraryManga,
    val checkedAtMillis: Long,
    val newChapters: List<DetectedChapter>,
) {
    val newChapterCount: Int get() = newChapters.size
}

data class UpdateFailure(val work: LibraryManga, val checkedAtMillis: Long, val message: String)

@Serializable
data class SmartUpdatePreferences(
    val enabled: Boolean = true,
    val skipCompleted: Boolean = true,
    val skipWithUnread: Boolean = true,
    val skipNotStarted: Boolean = true,
    val skipOutsideReleasePeriod: Boolean = true,
    val excludedTitleTerms: List<String> = emptyList(),
)

data class UpdateTitleMetadata(
    val completed: Boolean? = null,
    val fetchOnce: Boolean = false,
    val nextUpdateMillis: Long? = null,
)

enum class UpdateSkipReason { FETCH_ONCE, COMPLETED, NOT_STARTED, HAS_UNREAD, OUTSIDE_RELEASE_PERIOD, TITLE_FILTER }

data class SkippedUpdate(val work: LibraryManga, val reason: UpdateSkipReason)

data class UpdateScan(
    val updates: List<WorkUpdate>,
    val failures: List<UpdateFailure>,
    val skipped: List<SkippedUpdate> = emptyList(),
    val downloadTriggerFailures: List<LibraryManga> = emptyList(),
)

fun interface NewChaptersDownloadHook {
    suspend fun onNewChapters(update: WorkUpdate)
}

interface LibraryUpdates {
    suspend fun checkNow(): UpdateScan
    suspend fun retryFailures(): UpdateScan
    suspend fun feed(): List<WorkUpdate>
    suspend fun failures(): List<UpdateFailure>
}
