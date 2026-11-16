package dev.reader.media.novel

import okio.Closeable

/**
 * Metadata every imported local novel exposes.
 *
 * TXT has no embedded metadata, so [title] is derived from the file name and [author] is taken from
 * a leading `作者：…` / `作者:…` line when present.
 */
data class NovelMetadata(
    val title: String,
    val author: String? = null,
)

/**
 * One entry of a novel's table of contents.
 *
 * [level] expresses hierarchy: `0` is a volume/part heading (`第一卷`, `第二部`) and `1` is a
 * readable chapter/section (`第一章`, `第一节`, `楔子`, `番外`). [parentId] is the id of the
 * enclosing level-0 entry, or `null` when the entry is top level. [id] is stable for the same file
 * bytes, so it can be persisted as a progress key.
 */
data class TocEntry(
    val id: String,
    val title: String,
    val level: Int,
    val parentId: String?,
)

/**
 * A block of novel body text.
 *
 * The hierarchy is intentionally small and shared by every novel format. Readers should treat
 * unknown future variants as ignorable rather than failing.
 */
sealed interface NovelBlock {
    /** A single paragraph. Leading indentation has already been removed; inner text is untouched. */
    data class Paragraph(val text: String) : NovelBlock

    /** A visual separator such as `***` or `---` that is not an image. */
    data class Separator(val style: SeparatorStyle) : NovelBlock

    /**
     * A reference to an image. TXT carries no image bytes, so [reference] is the raw, unresolved
     * reference written in the text (for Markdown image syntax, the URL inside the parentheses).
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
 * An unresolved image reference from a novel file.
 *
 * [source] is exactly what the file contains (for example `images/ferry.png`); resolving it against
 * the book location, and loading bytes, belongs to a later import stage. [altText] is `null` when
 * the file supplies no alt text.
 */
data class ImageReference(
    val source: String,
    val altText: String? = null,
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
 * Implementations decode the file once when opened and parse chapter blocks on demand.
 * `close()` releases the decoded text and parsed chapters, so implementations must drop their
 * retained bodies rather than rely on GC. After `close()`, [readChapter] throws
 * [NovelClosedException]; the immutable snapshots [metadata], [encoding] and [tableOfContents] stay
 * readable so a closed book can still be listed.
 */
interface NovelImport : Closeable {
    /** Path descriptor of the underlying file, used in errors and logs. */
    val file: String

    /** Text encoding detected when the file was opened. */
    val encoding: TextEncoding

    val metadata: NovelMetadata

    /** Ordered table of contents: volume headings and readable chapters appear in file order. */
    val tableOfContents: List<TocEntry>

    /**
     * Parses and returns the chapter with [id].
     *
     * @throws NovelClosedException when the import has been closed.
     * @throws IllegalArgumentException when [id] is not in [tableOfContents].
     */
    fun readChapter(id: String): NovelChapter
}
