package dev.reader.media.novel

/**
 * Why a local novel could not be imported.
 *
 * Each failure mode is distinct so callers can map them to different UI or retry behavior:
 * - [Encoding] — the bytes are not valid text in a supported encoding; retrying is pointless.
 * - [Empty] — the file decoded to no readable text; treat as "nothing to import".
 * - [UnsupportedFormat] — the bytes are a different container/format (for example a zip-based
 *   EPUB renamed to `.txt`); the caller should route to another importer.
 * - [Io] — the open or read failed at the filesystem level (missing file, permissions, truncated
 *   read); the caller may retry or ask the user to re-pick the file.
 *
 * Messages always carry the file path plus a byte or line offset where one exists, so a failure can
 * be located without re-running the import.
 */
sealed class NovelImportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** The file that failed to import, as it was passed to the importer. */
    abstract val path: String

    /** Thrown when the bytes are not valid text in any supported encoding. */
    class Encoding(
        override val path: String,
        /** Byte offset of the first invalid sequence, or `null` when only a line is known. */
        val byteOffset: Long?,
        /** 1-based line number of the failure within the decoded prefix, when known. */
        val line: Int?,
        /** Encoding that was attempted, e.g. `UTF-8` or `UTF-16LE`. */
        val encoding: String,
        cause: Throwable? = null,
    ) : NovelImportException(
        "cannot decode $path as $encoding" +
            (byteOffset?.let { " at byte $it" } ?: "") +
            (line?.let { " (line $it)" } ?: ""),
        cause,
    )

    /** Thrown when the file contains no readable text, including zero-byte files. */
    class Empty(
        override val path: String,
        /** Size in bytes as reported by the filesystem. */
        val byteSize: Long,
    ) : NovelImportException("$path is empty ($byteSize bytes)")

    /** Thrown when the content is a recognizable non-text container. */
    class UnsupportedFormat(
        override val path: String,
        /** Short machine-readable description, e.g. `zip-container`. */
        val format: String,
        /** Bytes that identified the format, lowercase hex, e.g. `504b0304`. */
        val detectedSignature: String,
    ) : NovelImportException("$path is not a supported text novel (detected $format)")

    /**
     * Thrown when opening or reading the file failed below the decoder.
     *
     * The importer drains the whole file during `openTxtNovel`, so [phase] is `open` when the file
     * could not be opened and `read` when draining it failed. A `close` failure after a completed
     * read is swallowed on purpose — it cannot change the decoded result — so `close` is never
     * reported.
     */
    class Io(
        override val path: String,
        /** Phase of the import that failed: `open` or `read`. */
        val phase: String,
        cause: Throwable? = null,
    ) : NovelImportException(
        "I/O failure while $phase of $path: ${cause?.message ?: "unknown cause"}",
        cause,
    )
}

/** Thrown when any member of a [NovelImport] is accessed after [NovelImport.close]. */
class NovelClosedException(
    val path: String,
) : IllegalStateException("novel import for $path is closed")
