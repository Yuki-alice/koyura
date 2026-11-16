package dev.reader.domain.extension

/**
 * Milestone reading: progress identity is (sourceKey, workId, chapterId).
 * `chapterId` is null for chapterless works and stored as "".
 */
data class ReadingProgress(
    val sourceKey: String,
    val workId: String,
    val chapterId: String?,
    val pageKey: String,
    val pageIndex: Int,
)

/** One page of an opened chapter, in display order. */
data class ComicPage(
    /** Stable identity built from the source's raw image identifier. */
    val pageKey: String,
    /** Zero-based position in the chapter. */
    val index: Int,
    /** Raw image identifier as returned by `comic.loadEp`. */
    val imageKey: String,
)

/** An opened chapter: ordered pages plus where to resume. */
data class OpenedChapter(
    val sourceKey: String,
    val workId: String,
    val chapterId: String?,
    val pages: List<ComicPage>,
    /** Zero-based index to resume at (pageKey match preferred, else clamped). */
    val startIndex: Int,
    /** Page key at [startIndex], null when the chapter is empty. */
    val startPageKey: String?,
)

sealed class ReadingFailure(message: String, cause: Throwable? = null) : IllegalStateException(message, cause) {
    class SourceMissing(val key: String) : ReadingFailure("Extension is not installed: $key")
    class ChapterInvalid(cause: Throwable? = null) : ReadingFailure("Source returned an unusable chapter", cause)
    class ConfigInvalid(cause: Throwable? = null) : ReadingFailure("Source returned an unusable image config", cause)
    class TransformUnsupported(val kind: String) : ReadingFailure(
        "Image transform is not supported in this round: $kind (onResponse / modifyImage / onLoadFailed)",
    )
    class PageNotFound(val pageKey: String) : ReadingFailure("Page is no longer listed: $pageKey")
    class HttpError(val status: Int) : ReadingFailure("Image server returned HTTP $status")
    class TransportFailed(cause: Throwable) : ReadingFailure("Image request failed: ${cause.message}", cause)
}
