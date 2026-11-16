package app.koyura.media.novel

import kotlinx.serialization.Serializable

/**
 * A serializable reading position inside one chapter.
 *
 * The position is designed to survive a re-import of the same book, including a rebuilt novel
 * instance, and to degrade gracefully when the text changes:
 *
 * - [chapterId] — the stable chapter id from [TocEntry.id]. Stable for identical file bytes.
 * - [contentVersion] — a fingerprint of the whole book's content, so a caller can tell whether the
 *   position was taken against the same bytes. Same bytes always produce the same value.
 * - [blockIndex] — index of the paragraph-like block within the chapter's block list, counted over
 *   every block (headings, paragraphs, separators, images) so the value is meaningful for rendering
 *   too. It is the primary key for disambiguating repeated paragraph text.
 * - [blockFingerprint] — a content fingerprint of the anchored block, independent of position. Two
 *   paragraphs with identical text share a fingerprint, which is why [blockIndex] is also stored.
 * - [codePointOffset] — offset within the block's text in **Unicode code points**, not UTF-16 code
 *   units. An emoji or any supplementary-plane character counts as one.
 *
 * A position always refers to a *paragraph-like* block. Headings, separators and images carry no
 * offset; anchoring to one of them resolves to the nearest following paragraph (see
 * [ReadingPositionResolver]).
 */
@Serializable
data class ReadingPosition(
    val chapterId: String,
    val contentVersion: String,
    val blockIndex: Int,
    val blockFingerprint: String,
    val codePointOffset: Int,
    /**
     * Ordinal of the chapter in the book's table of contents when the position was saved.
     *
     * Used only when [chapterId] no longer exists, to pick the nearest surviving chapter. `-1` means
     * unknown (older saved positions); the resolver then falls back to the first readable chapter.
     */
    val chapterIndex: Int = -1,
)

/**
 * Outcome of re-resolving a stored [ReadingPosition] against a (possibly changed) book.
 *
 * [position] is always a usable position in the target book. [resolution] says how it was obtained so
 * a caller can tell the user "your position moved" and why.
 */
data class PositionResolution(
    val position: ReadingPosition,
    val resolution: ResolutionKind,
) {
    /** How a stored position was re-located. */
    enum class ResolutionKind {
        /** The anchor matched the same chapter, block fingerprint and index, at the same offset. */
        EXACT,

        /** The anchored block moved; it was found again by fingerprint at a new block index. */
        REANCHORED_BLOCK,

        /** The anchored block is gone; the nearest preceding surviving paragraph was used. */
        PREVIOUS_PARAGRAPH,

        /** The anchored block is gone and no previous paragraph survived; the next one was used. */
        NEXT_PARAGRAPH,

        /** The whole chapter is gone; the nearest surviving chapter was used. */
        NEIGHBOR_CHAPTER,

        /** Nothing matched, so the position fell back to the start of the nearest chapter. */
        CHAPTER_START,
    }
}

/**
 * Counts and converts Unicode code points, so offsets treat an emoji or a supplementary-plane
 * character as a single unit.
 *
 * Kotlin strings are UTF-16, so `String.length` and `indexOf` count code units: an emoji such as 😀
 * occupies two. Every offset in [ReadingPosition] is a code point count, and these helpers convert
 * between the two spaces.
 */
object CodePoints {
    /** Number of Unicode code points in [text]; surrogate pairs count once. */
    fun length(text: String): Int {
        var count = 0
        var index = 0
        while (index < text.length) {
            if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
                index += 2
            } else {
                index++
            }
            count++
        }
        return count
    }

    /**
     * Converts a code point offset to a UTF-16 string index, clamped to `[0, length]`.
     *
     * Clamping is deliberate: a position saved before a shorter paragraph must resolve to the
     * paragraph's end rather than throwing or landing mid-surrogate-pair.
     */
    fun toStringIndex(text: String, codePointOffset: Int): Int {
        if (codePointOffset <= 0) return 0
        var remaining = codePointOffset
        var index = 0
        while (index < text.length) {
            if (remaining == 0) return index
            if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
                index += 2
            } else {
                index++
            }
            remaining--
        }
        return text.length
    }

    /** Converts a UTF-16 string index to a code point offset, clamped to `[0, length]`. */
    fun toCodePointOffset(text: String, stringIndex: Int): Int {
        val limit = stringIndex.coerceIn(0, text.length)
        var count = 0
        var index = 0
        while (index < limit) {
            if (text[index].isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
                index += 2
            } else {
                index++
            }
            count++
        }
        return count
    }

    /** Clamps [offset] to the code point length of [text]. */
    fun clamp(text: String, offset: Int): Int = offset.coerceIn(0, length(text))
}
