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

    /**
     * Deletes one history entry. The default no-op keeps older implementors compiling; the
     * session always hides the row locally, and persistence lands with the data-layer delete
     * (see notes). Fakes override this to record calls.
     */
    suspend fun deleteEntry(sourceKey: String, workId: String) = Unit

    /**
     * Clears all history. Same default no-op contract as [deleteEntry].
     */
    suspend fun clearAll() = Unit

    /**
     * Persists the heart state against the shared library record (same row the detail page
     * and the Library tab read). Default returns false meaning "not supported": the session
     * then keeps the toggle local-only. Production returns true after the SQLite commit.
     */
    suspend fun setFavorite(
        sourceKey: String,
        workId: String,
        title: String,
        coverUrl: String,
        sourceName: String?,
        favorite: Boolean,
    ): Boolean = false

    /**
     * Persisted favorite keys shared with the library record. Default empty; production reads
     * the committed library rows. The session unions this with its local state on refresh.
     */
    suspend fun favoriteKeys(): Set<Pair<String, String>> = emptySet()
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
    /** Work cover URL for the shared cover pipeline; blank until the store records covers. */
    val cover: String = "",
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
