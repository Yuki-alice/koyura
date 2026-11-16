package dev.reader.media.novel

/**
 * Why a local novel could not be imported or read.
 *
 * Each failure mode is distinct so callers can map them to different UI or retry behavior:
 * - [Encoding] — the bytes are not valid text in a supported encoding; retrying is pointless.
 * - [Empty] — the file decoded to no readable text; treat as "nothing to import".
 * - [UnsupportedFormat] — the bytes are a different container/format; route to another importer.
 * - [Io] — the open or read failed at the filesystem level; the caller may retry or re-pick the file.
 * - [CorruptedArchive] — the ZIP/EPUB structure is broken.
 * - [MissingEntry] — a required archive entry or manifest item does not exist.
 * - [UnsafeReference] — a document reference escapes the archive root or points to a remote URL.
 * - [EncryptedContent] — content required for reading is encrypted.
 * - [LimitExceeded] — a configured [LocalNovelLimits] cap was hit.
 *
 * Messages always carry the archive path plus the offending entry or reference, so a failure can be
 * located without re-running the import.
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
     * The importer drains the whole file during TXT `open`, so [phase] is `open` when the file could
     * not be opened and `read` when draining it failed. A `close` failure after a completed read is
     * swallowed on purpose — it cannot change the decoded result — so `close` is never reported.
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

    /**
     * Thrown when the archive is not a readable ZIP/EPUB container.
     *
     * [entry] is `null` when the whole archive failed to open, or the entry that failed to parse.
     * [detail] is a short, non-localized cause such as `not-a-zip` or `no-rootfile`.
     */
    class CorruptedArchive(
        override val path: String,
        /** Archive entry involved, or `null` when the archive container itself is broken. */
        val entry: String?,
        /** Short machine-readable reason, e.g. `not-a-zip`, `invalid-xml`, `no-rootfile`. */
        val detail: String,
        cause: Throwable? = null,
    ) : NovelImportException(
        "corrupted archive $path" + (entry?.let { " at $it" } ?: "") + " ($detail)",
        cause,
    )

    /**
     * Thrown when a required archive entry or manifest-backed item does not exist.
     *
     * [entry] is the normalized path that was expected; [declaredBy] names the referring file such as
     * `META-INF/container.xml` or the OPF path, which makes a manifest/spine mismatch locatable.
     */
    class MissingEntry(
        override val path: String,
        /** Normalized archive path that is missing. */
        val entry: String,
        /** Referring document, or `null` when the entry is structurally required. */
        val declaredBy: String? = null,
    ) : NovelImportException(
        "$path is missing entry $entry" + (declaredBy?.let { " declared by $it" } ?: ""),
    )

    /**
     * Thrown when a document reference escapes the archive root, is absolute, or is remote.
     *
     * [reference] is the raw reference text; [document] is the normalized path of the file that
     * contained it; [reason] is `escapes-root`, `absolute`, `remote` or `bad-percent-encoding`.
     */
    class UnsafeReference(
        override val path: String,
        /** Raw reference exactly as written in the document. */
        val reference: String,
        /** Normalized archive path of the referring document. */
        val document: String,
        /** Short machine-readable reason. */
        val reason: String,
    ) : NovelImportException(
        "$path rejects reference '$reference' in $document ($reason)",
    )

    /**
     * Thrown when content required for reading is encrypted.
     *
     * [entry] is the encrypted archive path and [algorithm] the declared encryption algorithm when
     * present. Fonts and other non-reading resources are not reported here; they are excluded instead.
     */
    class EncryptedContent(
        override val path: String,
        /** Encrypted archive path needed for reading (spine document, nav, cover or in-chapter image). */
        val entry: String,
        /** Encryption algorithm from `encryption.xml`, e.g. `http://www.w3.org/2001/04/xmlenc#aes256-cbc`. */
        val algorithm: String?,
    ) : NovelImportException("$path has encrypted content at $entry" + (algorithm?.let { " ($it)" } ?: ""))

    /** Thrown when a configured [LocalNovelLimits] cap is exceeded. */
    class LimitExceeded(
        override val path: String,
        /** `chapter-bytes`, `resource-bytes` or `chapter-count`. */
        val limit: String,
        /** Configured maximum. */
        val maximum: Long,
        /** Observed value that exceeded [maximum]. */
        val actual: Long,
        /** Entry or resource the limit applies to, when relevant. */
        val entry: String? = null,
    ) : NovelImportException(
        "$path exceeded $limit limit: $actual > $maximum" + (entry?.let { " at $it" } ?: ""),
    )
}

/** Thrown when any member of a [NovelImport] that reads content is accessed after `close`. */
class NovelClosedException(
    val path: String,
) : IllegalStateException("novel import for $path is closed")
