package dev.reader.media.novel

import okio.ByteString.Companion.encodeUtf8

/**
 * Stable fingerprints used by [ReadingPosition] and by chapter/content versioning.
 *
 * Both use SHA-256 over UTF-8 bytes, truncated to 16 hex characters. A short digest is enough to
 * distinguish paragraphs within one book while keeping positions small; collisions across a single
 * book's paragraphs are not realistic, and the block index breaks any remaining tie.
 */
internal object NovelFingerprints {
    private const val DIGEST_HEX_LENGTH = 16

    /**
     * Normalizes text before hashing so cosmetic differences do not invalidate a position.
     *
     * Collapses runs of whitespace (including ideographic spaces) to one ASCII space and trims the
     * ends. This keeps a paragraph fingerprint stable when an editor re-wraps a line without changing
     * words, while still distinguishing any real content change.
     */
    fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        var lastWasSpace = false
        for (char in text) {
            val isSpace = char == ' ' || char == '\t' || char == '\n' || char == '\r' || char == '\u3000'
            if (isSpace) {
                if (!lastWasSpace) builder.append(' ')
                lastWasSpace = true
            } else {
                builder.append(char)
                lastWasSpace = false
            }
        }
        return builder.toString().trim()
    }

    /**
     * Fingerprint of a paragraph used for anchoring.
     *
     * Whitespace-normalized, so re-wrapping a paragraph does not invalidate a saved position. This is
     * deliberately *less* strict than [exact], which is what content versioning uses.
     */
    fun block(text: String): String = digest(normalize(text))

    /**
     * Fingerprint of raw text with **no** normalization.
     *
     * Used for content versioning, where any byte-level change must be visible. Normalizing first
     * would hide edits that change paragraph structure, such as inserting a blank line that splits one
     * paragraph into two.
     */
    fun exact(text: String): String = digest(text)

    /**
     * Fingerprint for a whole book's content.
     *
     * Computed from the ordered chapter ids and their exact (un-normalized) text, so any change to
     * chapter order, a single character, a whitespace-only edit that affects paragraph structure, or a
     * chapter's presence produces a different version. Identical file bytes always produce the same
     * value.
     */
    fun content(versionSource: String): String = digest(versionSource)

    private fun digest(input: String): String =
        input.encodeUtf8().sha256().hex().take(DIGEST_HEX_LENGTH)
}
