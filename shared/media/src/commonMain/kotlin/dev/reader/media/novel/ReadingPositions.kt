package dev.reader.media.novel

/**
 * Helpers shared by the TXT and EPUB implementations to build positions and content versions.
 *
 * Keeping this in one place guarantees both formats use the same anchoring rules: the same block
 * selection, the same fingerprint, and the same code point clamping.
 */
internal object ReadingPositions {

    /**
     * Builds a position for [codePointOffset] in the block at [blockIndex].
     *
     * The block index is honored when it points at a paragraph. Otherwise — a heading, separator, image,
     * or an out-of-range index — the anchor moves to the nearest following paragraph so the position is
     * always renderable. When the chapter has no paragraphs at all, block 0 is used with an empty
     * fingerprint and offset 0.
     */
    fun forBlockInChapter(
        chapterId: String,
        chapterIndex: Int,
        contentVersion: String,
        blocks: List<NovelBlock>,
        blockIndex: Int,
        codePointOffset: Int,
    ): ReadingPosition {
        val target = paragraphAt(blocks, blockIndex)
            ?: firstParagraphAtOrAfter(blocks, blockIndex)
            ?: firstParagraphAnywhere(blocks)
        if (target == null) {
            return ReadingPosition(
                chapterId = chapterId,
                contentVersion = contentVersion,
                blockIndex = 0,
                blockFingerprint = NovelFingerprints.block(""),
                codePointOffset = 0,
                chapterIndex = chapterIndex,
            )
        }
        val (index, text) = target
        return ReadingPosition(
            chapterId = chapterId,
            contentVersion = contentVersion,
            blockIndex = index,
            blockFingerprint = NovelFingerprints.block(text),
            codePointOffset = CodePoints.clamp(text, codePointOffset),
            chapterIndex = chapterIndex,
        )
    }

    /** `true` when the block at [index] is a paragraph. */
    fun isParagraph(blocks: List<NovelBlock>, index: Int): Boolean =
        blocks.getOrNull(index) is NovelBlock.Paragraph

    /** Text of the paragraph at [index], or `null`. */
    fun paragraphText(blocks: List<NovelBlock>, index: Int): String? =
        (blocks.getOrNull(index) as? NovelBlock.Paragraph)?.text

    private fun paragraphAt(blocks: List<NovelBlock>, index: Int): Pair<Int, String>? {
        val text = paragraphText(blocks, index) ?: return null
        return index to text
    }

    private fun firstParagraphAtOrAfter(blocks: List<NovelBlock>, fromIndex: Int): Pair<Int, String>? {
        var index = fromIndex.coerceAtLeast(0)
        while (index < blocks.size) {
            paragraphText(blocks, index)?.let { return index to it }
            index++
        }
        return null
    }

    private fun firstParagraphAnywhere(blocks: List<NovelBlock>): Pair<Int, String>? {
        for ((index, block) in blocks.withIndex()) {
            (block as? NovelBlock.Paragraph)?.let { return index to it.text }
        }
        return null
    }
}

/**
 * Builds a book-wide content version from ordered chapter ids and their section text.
 *
 * The hash covers ids and raw (un-normalized) chapter text in order, so reordering chapters, editing
 * any character, inserting a blank line that changes paragraph structure, or dropping a chapter all
 * change the version, while reopening identical bytes reproduces it exactly.
 */
internal fun buildContentVersion(sections: List<ContentVersionSection>): String {
    val builder = StringBuilder()
    for (section in sections) {
        builder.append(section.id).append('\u0000')
            .append(NovelFingerprints.exact(section.text)).append('\u0001')
    }
    return NovelFingerprints.content(builder.toString())
}

/** Minimal section view used to compute a content version. */
internal interface ContentVersionSection {
    val id: String

    /** Text hashed into the version; for TXT this is the section body. */
    val text: String
}
