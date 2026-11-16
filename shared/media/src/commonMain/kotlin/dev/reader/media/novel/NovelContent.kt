package dev.reader.media.novel

import okio.Closeable

/**
 * Metadata every imported local novel exposes.
 *
 * TXT has no embedded metadata, so [title] is derived from the file name and [authors] from a
 * leading `作者：…` / `作者:…` line when present. EPUB fills every field from the package document.
 * [author] returns the first author for convenience; use [authors] when several exist.
 */
data class NovelMetadata(
    val title: String,
    val authors: List<String> = emptyList(),
    val language: String? = null,
    /** Cover resource declared by the package document, resolved to an archive path. */
    val cover: NovelResource? = null,
) {
    /** First author, or `null` when the book declares none. */
    val author: String? get() = authors.firstOrNull()
}

/**
 * A binary resource inside an archive, addressable through [NovelImport.readResource].
 *
 * [path] is normalized, archive-root relative and never contains `.`/`..`/absolute/remote parts.
 * [mediaType] is the declared MIME type when the container provides one.
 */
data class NovelResource(
    val path: String,
    val mediaType: String? = null,
    val altText: String? = null,
)

/**
 * One entry of a novel's table of contents.
 *
 * [level] expresses hierarchy: `0` is a volume/part heading and `1` a readable chapter. [parentId] is
 * the id of the enclosing level-0 entry, or `null` when the entry is top level. [id] is stable for
 * the same file bytes, so it can be persisted as a progress key.
 */
data class TocEntry(
    val id: String,
    val title: String,
    val level: Int,
    val parentId: String?,
)

/**
 * A block of novel body content.
 *
 * The hierarchy is small and shared by every novel format. Readers should treat unknown future
 * variants as ignorable rather than failing.
 */
sealed interface NovelBlock {
    /** A heading whose [level] is 1..6, matching the source `h1`..`h6`. */
    data class Heading(val level: Int, val text: String) : NovelBlock

    /** A single paragraph. Leading indentation has already been removed; inner text is untouched. */
    data class Paragraph(val text: String) : NovelBlock

    /** A visual separator such as `***` or `---` that is not an image. */
    data class Separator(val style: SeparatorStyle) : NovelBlock

    /**
     * An image reference.
     *
     * [reference] carries the raw source text. For EPUB, [ImageReference.resource] is set to a
     * normalized archive path and [NovelImport.readResource] returns its bytes; remote images have no
     * resource and must not be fetched.
     */
    data class Image(val reference: ImageReference) : NovelBlock
}

/** Shape of a separator line, kept so the reader can render the author's intent. */
enum class SeparatorStyle {
    ASTERISKS,
    DASHES,
    DOTS,
}

/**
 * An image reference from a novel file.
 *
 * [source] is exactly what the file contains (for example `images/ferry.png` or a Markdown URL).
 * [resource] is non-null only when the reference resolved to a local archive resource that can be
 * read; it is `null` for TXT (no archive) and for remote or unsafe references. [altText] is `null`
 * when the file supplies no alt text.
 */
data class ImageReference(
    val source: String,
    val altText: String? = null,
    val resource: NovelResource? = null,
)

/** A parsed chapter, including its stable id and table-of-contents placement. */
data class NovelChapter(
    val id: String,
    val title: String,
    val level: Int,
    val parentId: String?,
    val blocks: List<NovelBlock>,
)

/**
 * Runs [block] with this import and always closes it, even when [block] throws.
 *
 * Provided explicitly because `use` is not available for every closeable type on every Kotlin
 * target; callers can also use try/finally.
 */
inline fun <R> NovelImport.useImport(block: (NovelImport) -> R): R {
    try {
        return block(this)
    } finally {
        close()
    }
}

/**
 * A readable, closeable local novel.
 *
 * Implementations parse the container when opened and read chapter bodies and resources on demand.
 * `close()` releases everything they retain: for TXT the decoded text, for EPUB the zip filesystem
 * and its handles. After `close()`, [readChapter] and [readResource] throw [NovelClosedException];
 * the immutable snapshots [metadata], [encoding] and [tableOfContents] stay readable so a closed book
 * can still be listed.
 */
interface NovelImport : Closeable {
    /** Path descriptor of the underlying file, used in errors and logs. */
    val file: String

    /**
     * Text encoding of the source text.
     *
     * TXT reports the detected encoding. EPUB XHTML is XML, which is UTF-8/UTF-16 by definition, so
     * EPUB implementations report [TextEncoding.UTF_8].
     */
    val encoding: TextEncoding

    val metadata: NovelMetadata

    /** Ordered table of contents: volume headings and readable chapters appear in reading order. */
    val tableOfContents: List<TocEntry>

    /**
     * Fingerprint of this book's content, stable for identical file bytes.
     *
     * Computed from the ordered chapter ids and their paragraph fingerprints, so any change in text,
     * chapter order or chapter presence yields a different value. A stored [ReadingPosition] carries
     * the version it was taken against, letting callers detect that the file changed.
     */
    val contentVersion: String

    /**
     * Parses and returns the chapter with [id].
     *
     * @throws NovelClosedException when the import has been closed.
     * @throws IllegalArgumentException when [id] is not in [tableOfContents].
     */
    fun readChapter(id: String): NovelChapter

    /**
     * Reads the raw bytes of a local resource, such as a cover or an in-chapter image.
     *
     * [path] must be the normalized archive path carried by [NovelResource.path] or
     * [ImageReference.resource]; implementations never fetch remote URLs. TXT has no resources, so an
     * **open** TXT import throws [UnsupportedOperationException]; a **closed** import throws
     * [NovelClosedException] first, like every other content read.
     *
     * Known limitation: archive entry names containing literal `%` or `#` are ambiguous with percent
     * escapes and fragments, so a document that references such a name (even percent-encoded) may
     * resolve to an unreachable path and report `MissingEntry`. Names without those characters, which
     * covers the EPUB fixtures and typical books, are unaffected.
     *
     * @throws NovelClosedException when the import has been closed.
     * @throws NovelImportException.MissingEntry when the resource does not exist.
     * @throws NovelImportException.LimitExceeded when the resource exceeds the configured limit.
     */
    fun readResource(path: String): ByteArray

    /**
     * Builds a [ReadingPosition] for a code point [offset] inside the block at [blockIndex].
     *
     * [blockIndex] indexes the chapter's [NovelChapter.blocks] list (so headings, separators and
     * images count). When the block is not a paragraph, the returned position anchors to the nearest
     * following paragraph so it is always renderable; when the chapter has no paragraphs the position
     * anchors to block 0 with an empty fingerprint.
     *
     * @throws NovelClosedException when the import has been closed.
     * @throws IllegalArgumentException when [chapterId] is not in [tableOfContents].
     */
    fun positionOf(chapterId: String, blockIndex: Int, codePointOffset: Int): ReadingPosition

    /**
     * Re-locates [stored] in this book, applying the recovery rules in [ReadingPositionResolver].
     *
     * The result is always a valid position for this book, together with the [PositionResolution]
     * describing which rule matched.
     *
     * @throws NovelClosedException when the import has been closed.
     */
    fun resolvePosition(stored: ReadingPosition): PositionResolution
}
