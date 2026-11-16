package app.koyura.domain.reader

/**
 * Boundary between the comic reader UI and the reading runtime.
 *
 * The UI owns no business rules beyond presentation: opening a chapter yields an ordered page key
 * list plus a 0-based start index, pages are single-load with per-page retry, and every save goes
 * through [saveProgress] with the displayed page key and index.
 *
 * Platform- or framework-specific injection (e.g. Compose `CompositionLocal` providers) lives in
 * the app assembly, not here: this module stays pure Kotlin.
 */
interface ReaderService {
    /** Returns the last saved position for this work, or null when it was never opened. */
    suspend fun readProgress(sourceKey: String, workId: String): ReaderProgress?

    /**
     * Opens [chapterId] ([null] for works without chapters) and returns its ordered page keys plus
     * the 0-based page the reader should display first. When the service knows a saved position for
     * this chapter it returns that page so "continue reading" lands correctly.
     */
    suspend fun openChapter(sourceKey: String, workId: String, chapterId: String?): ChapterContent

    /** Returns the encoded image bytes for one page for decoding by the platform. */
    suspend fun loadPageBytes(sourceKey: String, workId: String, chapterId: String?, imageKey: String): ByteArray

    /**
     * Persists the currently displayed page.
     *
     * [workTitle] and [chapterTitle] are best-effort display metadata recorded alongside the
     * position; history UI falls back to IDs when they are absent.
     */
    suspend fun saveProgress(
        sourceKey: String,
        workId: String,
        chapterId: String?,
        pageKey: String,
        pageIndex: Int,
        workTitle: String? = null,
        chapterTitle: String? = null,
    )
}

/** A saved reading position. [pageIndex] is 0-based. */
data class ReaderProgress(
    val chapterId: String?,
    val pageIndex: Int,
)

/** An opened chapter: ordered pages plus the 0-based page to display first. */
data class ChapterContent(
    val chapterId: String?,
    val title: String,
    val pageKeys: List<String>,
    val startPageIndex: Int,
    /** Source image identifiers when they differ from the stable page keys. */
    val imageKeys: Map<String, String> = emptyMap(),
    /** Whether the chapter starts at a position that still needs to be committed. */
    val saveInitialPosition: Boolean = false,
)

class ReaderServiceException(message: String) : Exception(message)
