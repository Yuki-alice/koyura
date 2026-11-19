package app.koyura.domain.updates

import app.koyura.domain.library.LibraryManga

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

data class UpdateScan(val updates: List<WorkUpdate>, val failures: List<UpdateFailure>)

interface LibraryUpdates {
    suspend fun checkNow(): UpdateScan
    suspend fun retryFailures(): UpdateScan
    suspend fun feed(): List<WorkUpdate>
    suspend fun failures(): List<UpdateFailure>
}
