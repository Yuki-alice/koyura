package dev.reader.app.reader

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Boundary between the comic reader UI and the reading runtime.
 *
 * The UI owns no business rules beyond presentation: opening a chapter yields an ordered page key
 * list plus a 0-based start index, pages are single-load with per-page retry, and every save goes
 * through [saveProgress] with an explicit page index. Implementations throw
 * [ReaderServiceException] with a user-presentable message on failure.
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

    /** Returns the encoded image bytes for one page (any format Coil can decode). */
    suspend fun loadPageBytes(sourceKey: String, workId: String, chapterId: String?, pageKey: String): ByteArray

    /** Persists the currently displayed page. */
    suspend fun saveProgress(sourceKey: String, workId: String, chapterId: String?, pageIndex: Int)
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
)

class ReaderServiceException(message: String) : Exception(message)

/** Provides the injected runtime to reader screens; unset until a service is wired. */
val LocalReaderService = staticCompositionLocalOf<ReaderService?> { null }
