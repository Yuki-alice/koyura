package app.koyura.features.reader.comic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import app.koyura.domain.reader.ChapterContent
import app.koyura.domain.reader.ReaderService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Loading state of the opened chapter. */
sealed interface ReaderContentState {
    data object Loading : ReaderContentState
    data class Ready(val content: ChapterContent) : ReaderContentState
    data class Failed(val message: String) : ReaderContentState
}

/** Loading state of a single page. */
sealed interface ReaderPageState {
    data object Loading : ReaderPageState
    data class Ready(val bytes: ByteArray) : ReaderPageState
    data class Failed(val message: String) : ReaderPageState
}

/**
 * Holds one reader screen's data: the opened chapter, per-page image states and the saved page.
 *
 * Concurrency rules, enforced here so every surface behaves the same:
 * - every [openChapter] call bumps a token; page results whose token no longer matches are dropped,
 *   so a slow request from a previous chapter (or an older open of the same chapter) can never
 *   overwrite the current one;
 * - the same tokening applies per page key, so retrying a failed page cannot be clobbered by the
 *   failed attempt landing late;
 * - a second load for a page that is already loading or ready is ignored (no duplicate submit);
 * - the pager reports displayed positions, and opening at an already saved position
 *   does not overwrite it.
 */
class ReaderSession(
    private val service: ReaderService?,
) {
    var content: ReaderContentState by mutableStateOf(ReaderContentState.Loading)
        private set

    /** Index of the currently displayed page, driven by the pager. */
    var currentPage: Int by mutableStateOf(0)
        private set

    /** Per-page image states, keyed by page key. */
    val pages: SnapshotStateMap<String, ReaderPageState> = mutableStateMapOf()

    private var openToken = 0L
    private val pageTokens = mutableMapOf<String, Long>()

    /**
     * Bumps on every [open] call. The pager is keyed on it so switching chapters rebuilds page
     * state instead of reusing the previous chapter's.
     */
    var contentSeq by mutableStateOf(0L)
        private set
    private var lastSavedIndex: Int? = null
    private val saveMutex = Mutex()

    private var boundSourceKey: String = ""
    private var boundWorkId: String = ""
    private var chapterIds: List<String> = emptyList()
    private var boundWorkTitle: String? = null
    private var boundChapterTitles: Map<String, String> = emptyMap()

    val orderedChapterIds: List<String>
        get() = chapterIds

    suspend fun open(
        sourceKey: String,
        workId: String,
        chapterId: String?,
        chapterIds: List<String>,
        workTitle: String? = null,
        chapterTitles: List<String> = emptyList(),
    ) {
        val currentService = service ?: return
        val token = ++openToken
        contentSeq += 1
        boundSourceKey = sourceKey
        boundWorkId = workId
        this.chapterIds = chapterIds.toList()
        boundWorkTitle = workTitle
        boundChapterTitles = chapterIds.zip(chapterTitles).toMap()
        content = ReaderContentState.Loading
        pages.clear()
        pageTokens.clear()
        currentPage = 0
        lastSavedIndex = null
        try {
            val opened = currentService.openChapter(sourceKey, workId, chapterId)
            if (token != openToken) return
            content = ReaderContentState.Ready(opened)
            currentPage = opened.startPageIndex.coerceInPageRange(opened.pageKeys.size)
            if (!opened.saveInitialPosition) lastSavedIndex = currentPage
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (token == openToken) {
                content = ReaderContentState.Failed(failure.userMessage())
            }
        }
    }

    /** Starts loading [pageKey] unless it is already loading or ready. */
    suspend fun ensurePage(pageKey: String) {
        val currentService = service ?: return
        val ready = content as? ReaderContentState.Ready ?: return
        val token = openToken
        when (pages[pageKey]) {
            is ReaderPageState.Loading, is ReaderPageState.Ready -> return
            else -> Unit
        }
        val pageToken = (pageTokens[pageKey] ?: 0L) + 1
        pageTokens[pageKey] = pageToken
        pages[pageKey] = ReaderPageState.Loading
        try {
            val bytes = currentService.loadPageBytes(
                boundSourceKey,
                boundWorkId,
                ready.content.chapterId,
                ready.content.imageKeys[pageKey] ?: pageKey,
            )
            if (token == openToken && pageTokens[pageKey] == pageToken) {
                pages[pageKey] = ReaderPageState.Ready(bytes)
            }
        } catch (cancelled: CancellationException) {
            if (token == openToken && pageTokens[pageKey] == pageToken) {
                pages.remove(pageKey)
            }
            throw cancelled
        } catch (failure: Exception) {
            if (token == openToken && pageTokens[pageKey] == pageToken) {
                pages[pageKey] = ReaderPageState.Failed(failure.userMessage())
            }
        }
    }

    /** Retries a failed page; a new token supersedes any late arrival of the previous attempt. */
    suspend fun retryPage(pageKey: String) {
        pages.remove(pageKey)
        ensurePage(pageKey)
    }

    /** Records a newly displayed page. Called by the pager for every page change. */
    fun markDisplayed(index: Int) {
        currentPage = index
    }

    /**
     * Persists [index] unless it is already the last saved value. The initial page is seeded as
     * saved on open, so restoring progress never writes page 0 over it first.
     */
    suspend fun savePage(index: Int) {
        val currentService = service ?: return
        val ready = content as? ReaderContentState.Ready ?: return
        val pageKey = ready.content.pageKeys.getOrNull(index) ?: return
        val token = openToken
        saveMutex.withLock {
            if (token != openToken || index == lastSavedIndex) return
            val isLast = ready.content.pageKeys.isNotEmpty() && index == ready.content.pageKeys.size - 1
            currentService.saveProgress(
                boundSourceKey,
                boundWorkId,
                ready.content.chapterId,
                pageKey,
                index,
                workTitle = boundWorkTitle,
                chapterTitle = boundChapterTitles[ready.content.chapterId],
                isLastPage = isLast,
            )
            if (token == openToken) lastSavedIndex = index
        }
    }

    /** Persists the currently displayed page unless it is already saved. */
    suspend fun saveCurrent() {
        savePage(currentPage)
    }

    /** Chapter id before [chapterId] in source order, or null at the first chapter. */
    fun previousChapterId(chapterId: String?): String? {
        if (chapterId == null) return null
        val index = chapterIds.indexOf(chapterId)
        return if (index > 0) chapterIds[index - 1] else null
    }

    /** Chapter id after [chapterId] in source order, or null at the last chapter. */
    fun nextChapterId(chapterId: String?): String? {
        if (chapterId == null) return null
        val index = chapterIds.indexOf(chapterId)
        return if (index >= 0 && index + 1 < chapterIds.size) chapterIds[index + 1] else null
    }

    private fun Int.coerceInPageRange(size: Int): Int = when {
        size <= 0 -> 0
        this < 0 -> 0
        this >= size -> size - 1
        else -> this
    }

    private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Operation failed"
}

/**
 * Creates the session once per reader entry and keeps it across recompositions and the bar/rail
 * width switch.
 *
 * The service is always passed explicitly by the app assembly (which owns the `LocalReaderService`
 * injection); features never reads ambient services itself.
 */
@Composable
fun rememberReaderSession(service: ReaderService?): ReaderSession =
    remember(service) { ReaderSession(service) }

/** Convenience for launching session work from UI callbacks. */
@Composable
fun rememberReaderScope() = rememberCoroutineScope()
