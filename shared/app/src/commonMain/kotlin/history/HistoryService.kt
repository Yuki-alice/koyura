package app.koyura.app.history

import androidx.compose.runtime.staticCompositionLocalOf
import app.koyura.domain.history.HistoryRecord

/**
 * Boundary between the history UI and its data. The snapshot is always complete and already
 * ordered newest-first, with entries lacking a recorded read time at the end; the UI only
 * filters, groups and renders.
 */
interface HistoryService {
    suspend fun loadHistory(): List<HistoryEntry>
}

/** One row in the history list: the latest saved progress for a (sourceKey, workId) pair. */
data class HistoryEntry(
    val sourceKey: String,
    val workId: String,
    val chapterId: String?,
    val pageKey: String,
    val pageIndex: Int,
    val workTitle: String?,
    val chapterTitle: String?,
    val readAtMillis: Long?,
) {
    /** Human-readable title, falling back to the work id when no title was recorded. */
    val displayTitle: String get() = workTitle?.takeIf { it.isNotBlank() } ?: workId
}

/** Provides the injected history service; unset until a service is wired. */
val LocalHistoryService = staticCompositionLocalOf<HistoryService?> { null }

internal fun HistoryRecord.toEntry() = HistoryEntry(
    sourceKey = sourceKey,
    workId = workId,
    chapterId = chapterId,
    pageKey = pageKey,
    pageIndex = pageIndex,
    workTitle = workTitle,
    chapterTitle = chapterTitle,
    readAtMillis = readAtMillis,
)
