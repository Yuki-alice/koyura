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

/** 返回键消费结果（顺序照 `LibrarySession.consumeBack`）：UI 只关心是否已被本页吃掉。 */
enum class HistoryBackOutcome {
    /** 清掉了多选，页面不动。 */
    ClearedSelection,

    /** 本层无可清状态，交回导航栈 / 平台。 */
    NotConsumed,
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

    /**
     * 多选集合（三元键 `(sourceKey, workId)`）。非空时顶栏切选择模式并隐藏外壳
     * （由装配层读取，与书架多选同一语义）。
     */
    var selection: Set<Pair<String, String>> by mutableStateOf(emptySet())
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
        selection = emptySet()
    }

    // region 多选与批量删除（键 (sourceKey, workId)）

    fun isSelected(entry: HistoryEntry): Boolean = selection.contains(entry.sourceKey to entry.workId)

    fun toggleSelect(entry: HistoryEntry) {
        val key = entry.sourceKey to entry.workId
        selection = if (selection.contains(key)) selection - key else selection + key
    }

    /** 全选当前可见行；默认取过滤后的可见集，界面可传入已叠加查询的列表。 */
    fun selectAllVisible(entries: List<HistoryEntry> = visibleEntries()) {
        selection = entries.map { it.sourceKey to it.workId }.toSet()
    }

    /** 反选当前可见行（只翻转可见范围内，范围外的选择保留）。 */
    fun invertSelection(entries: List<HistoryEntry> = visibleEntries()) {
        val visible = entries.map { it.sourceKey to it.workId }.toSet()
        val keptOutside = selection - visible
        selection = keptOutside + (visible - selection)
    }

    fun clearSelection() {
        selection = emptySet()
    }

    /** 返回键是否应被历史页消费（多选非空时消费）。 */
    val wantsBack: Boolean
        get() = selection.isNotEmpty()

    /** 返回键规则（顺序照书架）：先清多选，再无本层可清状态交回导航。 */
    fun consumeBack(): HistoryBackOutcome = when {
        selection.isNotEmpty() -> {
            clearSelection()
            HistoryBackOutcome.ClearedSelection
        }
        else -> HistoryBackOutcome.NotConsumed
    }

    /**
     * 批量删除所选：逐条走 [delete]，单项失败只记 `errorMessage` 并继续整批，
     * 失败项保留在选择集内以便重试；成功项从选择集移除。
     */
    suspend fun deleteSelected() {
        val targets = entries.filter { isSelected(it) }
        for (entry in targets) {
            delete(entry)
            val key = entry.sourceKey to entry.workId
            if (hiddenKeys.contains(key)) selection = selection - key
        }
    }

    // endregion

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
            // 生产以仓储提交为准：支持持久收藏的服务直接替换本地心态，
            // 书架/详情取消收藏后刷新即同步；不支持的服务保留本地心态。
            current.favoriteKeys()?.let { favoriteKeys = it }
            // 同一会话内重新阅读的作品已回到列表：只保留仍不可见行的本地隐藏，
            // 删除/清空的行继续隐藏直到重启或再次阅读。
            val visible = entries.map { it.sourceKey to it.workId }.toSet()
            hiddenKeys = hiddenKeys.filterNot { it in visible }.toSet()
            // 选择集收敛到仍存在的行：已消失的行不再占计数。
            selection = selection.filter { it in visible }.toSet()
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
