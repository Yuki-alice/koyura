package app.koyura.app.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.koyura.app.browse.BrowseService
import app.koyura.app.browse.BrowseServiceException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Loading state of the history list. */
sealed interface HistoryLoadState {
    data object Loading : HistoryLoadState
    data object Ready : HistoryLoadState
    data class Failed(val message: String) : HistoryLoadState
}

/** One date group in the history list; [date] is null for entries without a recorded time. */
data class HistorySection(
    val date: kotlinx.datetime.LocalDate?,
    val entries: List<HistoryEntry>,
)

internal fun HistoryEntry.matches(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return workId.contains(needle, ignoreCase = true) ||
        sourceKey.contains(needle, ignoreCase = true) ||
        (workTitle?.contains(needle, ignoreCase = true) == true) ||
        (chapterTitle?.contains(needle, ignoreCase = true) == true) ||
        (chapterId?.contains(needle, ignoreCase = true) == true)
}

/**
 * Groups entries by local calendar day, newest day first. Entries without a recorded read time
 * form their own trailing group instead of borrowing the migration date.
 */
internal fun List<HistoryEntry>.groupedByDate(today: kotlinx.datetime.LocalDate): List<HistorySection> {
    val (dated, undated) = partition { it.readAtMillis != null }
    val byDate = dated.groupBy {
        kotlin.time.Instant.fromEpochMilliseconds(it.readAtMillis!!)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    val ordered = byDate.keys.sortedDescending().map { date ->
        HistorySection(date, byDate.getValue(date))
    }
    return if (undated.isEmpty()) ordered else ordered + HistorySection(null, undated)
}

/**
 * Holds the history tab's list state. The list comes from SQLite only, so it renders offline;
 * opening works and chapters resolve through the optional browse service at tap time.
 */
class HistorySession(
    private val service: HistoryService?,
    private val browse: BrowseService? = null,
) {
    var entries: List<HistoryEntry> by mutableStateOf(emptyList())
        private set

    var loadState: HistoryLoadState by mutableStateOf(HistoryLoadState.Loading)
        private set

    var errorMessage: String? by mutableStateOf(null)
        private set

    /**
     * Locally hidden entries (deleted rows). Applied on top of every refresh until the
     * data-layer delete lands (see notes); survives tab switches and the bar/rail switch
     * because the session outlives both. With a persistent service the rows are gone from
     * SQLite, so this is only a same-session hide for fakes and failed deletes.
     */
    var hiddenKeys: Set<Pair<String, String>> by mutableStateOf(emptySet())
        private set

    /**
     * Favorited entries (heart state). Union of the persisted library rows (same record the
     * detail page and the Library tab read) and same-session local toggles, so a heart set
     * on a service without favorite support still renders until the next refresh.
     */
    var favoriteKeys: Set<Pair<String, String>> by mutableStateOf(emptySet())
        private set

    /** Source filter from the filter dialog; empty means all sources. */
    var filteredSourceKeys: Set<String> by mutableStateOf(emptySet())
        private set

    /** When true the list shows favorited entries only. */
    var showFavoritesOnly: Boolean by mutableStateOf(false)
        private set

    /** Entries after local hide/favorite/source filtering, before the query filter. */
    fun visibleEntries(): List<HistoryEntry> = entries.filter { entry ->
        val key = entry.sourceKey to entry.workId
        if (hiddenKeys.contains(key)) return@filter false
        if (showFavoritesOnly && !favoriteKeys.contains(key)) return@filter false
        if (filteredSourceKeys.isNotEmpty() && !filteredSourceKeys.contains(entry.sourceKey)) {
            return@filter false
        }
        true
    }

    /** Distinct source keys in the current snapshot, for the filter dialog. */
    fun availableSources(): List<String> = entries.map { it.sourceKey }.distinct().sorted()

    /** Deletes one row: asks the service first, then hides locally on success. */
    suspend fun delete(entry: HistoryEntry) {
        val current = service
        if (current != null) {
            try {
                current.deleteEntry(entry.sourceKey, entry.workId)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                reportError(failure.message ?: "Operation failed")
                return
            }
        }
        hiddenKeys = hiddenKeys + (entry.sourceKey to entry.workId)
        favoriteKeys = favoriteKeys - (entry.sourceKey to entry.workId)
    }

    /** Clears the whole list: service first, then local hide-all on success. */
    suspend fun clearAll() {
        val current = service
        if (current != null) {
            try {
                current.clearAll()
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                reportError(failure.message ?: "Operation failed")
                return
            }
        }
        hiddenKeys = entries.map { it.sourceKey to it.workId }.toSet()
        favoriteKeys = emptySet()
    }

    /** Toggles the heart state: persisted to the shared library record when supported. */
    suspend fun toggleFavorite(entry: HistoryEntry) {
        val key = entry.sourceKey to entry.workId
        val current = service
        if (current != null) {
            val want = !favoriteKeys.contains(key)
            try {
                if (current.setFavorite(
                        sourceKey = entry.sourceKey,
                        workId = entry.workId,
                        title = entry.workTitle?.takeIf { it.isNotBlank() } ?: entry.workId,
                        coverUrl = entry.cover,
                        sourceName = null,
                        favorite = want,
                    )
                ) {
                    favoriteKeys = if (want) favoriteKeys + key else favoriteKeys - key
                    return
                }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                reportError(failure.message ?: "Operation failed")
                return
            }
        }
        favoriteKeys = if (favoriteKeys.contains(key)) favoriteKeys - key else favoriteKeys + key
    }

    fun isFavorite(entry: HistoryEntry): Boolean =
        favoriteKeys.contains(entry.sourceKey to entry.workId)

    fun setSourceFilter(keys: Set<String>) {
        filteredSourceKeys = keys
    }

    fun setFavoritesOnly(only: Boolean) {
        showFavoritesOnly = only
    }

    fun clearFilters() {
        filteredSourceKeys = emptySet()
        showFavoritesOnly = false
    }

    suspend fun refresh() {
        val current = service
        if (current == null) {
            loadState = HistoryLoadState.Ready
            entries = emptyList()
            return
        }
        loadState = HistoryLoadState.Loading
        try {
            entries = current.loadHistory()
            // Union, never replace: a heart set while the service lacked favorite support
            // must survive until the persisted rows confirm it.
            favoriteKeys = favoriteKeys + current.favoriteKeys()
            loadState = HistoryLoadState.Ready
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            entries = emptyList()
            loadState = HistoryLoadState.Failed(failure.userMessage())
        }
    }

    fun reportError(message: String) {
        errorMessage = message
    }

    /**
     * Cover loader bound to this tab's browse service (null → placeholders).
     *
     * History rows resolve covers through the same pipeline as search/detail so all three
     * surfaces share one cache and one fallback.
     */
    fun coverLoader(): app.koyura.designsystem.CoverImageLoader? =
        app.koyura.app.browse.browseCoverLoader(browse)

    fun clearError() {
        errorMessage = null
    }

    /**
     * Resolves the up-to-date chapter list for continuing an entry.
     *
     * Throws when the source is gone or the saved chapter is no longer listed; the entry and its
     * progress stay untouched so the failure is shown without losing history.
     */
    suspend fun resolveChapters(entry: HistoryEntry): Pair<List<String>, List<String>> {
        val current = browse ?: throw BrowseServiceException("Browse is not connected")
        val detail = current.loadWorkDetail(entry.sourceKey, entry.workId)
        val ids = detail.chapters.map { it.id }
        if (entry.chapterId != null && entry.chapterId !in ids) {
            throw BrowseServiceException("Saved chapter is no longer listed")
        }
        return ids to detail.chapters.map { it.title }
    }

    private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Operation failed"
}

/**
 * Creates the session once per shell and keeps it across tab switches, child pages and the
 * bar/rail width switch.
 */
@Composable
fun rememberHistorySession(
    service: HistoryService? = LocalHistoryService.current,
    browse: BrowseService? = app.koyura.app.browse.LocalBrowseService.current,
): HistorySession = remember(service, browse) { HistorySession(service, browse) }
