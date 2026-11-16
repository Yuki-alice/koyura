package dev.reader.media.novel

/**
 * Re-locates a stored [ReadingPosition] in a (possibly changed) book.
 *
 * Recovery order, matching the product contract:
 * 1. **Exact** — same chapter id, same block index, same fingerprint: the offset is clamped into the
 *    anchored paragraph and returned unchanged when it still fits.
 * 2. **Re-anchored block** — same chapter, the fingerprint is found at a different index (content was
 *    inserted or removed before it). The offset is clamped into that paragraph.
 * 3. **Previous surviving paragraph** — the anchored paragraph is gone; the nearest earlier paragraph
 *    that still exists is used, with the offset clamped to its end. This is preferred because it keeps
 *    the reader just before where they stopped.
 * 4. **Next surviving paragraph** — no earlier survivor, so the nearest later paragraph is used.
 * 5. **Neighbour chapter** — the chapter itself is gone; the surviving chapter at the same ordinal
 *    position (or the last one) is used, anchored at its first paragraph.
 * 6. **Chapter start** — the last resort when nothing else resolves.
 *
 * Every returned position is valid for the target book, and [PositionResolution.resolution] explains
 * which rule produced it.
 */
object ReadingPositionResolver {

    /**
     * Resolves [stored] against a book described by [chapters] and [contentVersion].
     *
     * @param chapters the target book's chapters in reading order; each supplies its id and blocks.
     * @param contentVersion the target book's content version, stamped onto the result.
     * @param stored the position saved from an earlier import or an earlier version of the file.
     */
    fun resolve(
        chapters: List<ChapterView>,
        contentVersion: String,
        stored: ReadingPosition,
    ): PositionResolution {
        require(chapters.isNotEmpty()) { "a book must expose at least one chapter" }

        // 1/2: same chapter still exists.
        val chapterIndex = chapters.indexOfFirst { it.id == stored.chapterId }
        if (chapterIndex >= 0) {
            val chapter = chapters[chapterIndex]
            val anchored = locateBlock(chapter.blocks, stored.blockIndex, stored.blockFingerprint)
            when (anchored) {
                is BlockMatch.Exact -> {
                    val offset = clampToBlock(anchored.block, stored.codePointOffset)
                    return PositionResolution(
                        positionFor(chapter.id, chapterIndex, contentVersion, anchored.index, anchored.block, offset),
                        PositionResolution.ResolutionKind.EXACT,
                    )
                }
                is BlockMatch.Moved -> {
                    val offset = clampToBlock(anchored.block, stored.codePointOffset)
                    return PositionResolution(
                        positionFor(chapter.id, chapterIndex, contentVersion, anchored.index, anchored.block, offset),
                        PositionResolution.ResolutionKind.REANCHORED_BLOCK,
                    )
                }
                BlockMatch.Gone -> Unit
            }

            // 3/4: recover to a neighbouring paragraph within the same chapter.
            val neighbouring = nearestParagraph(chapter.blocks, stored.blockIndex)
            if (neighbouring != null) {
                val kind = if (neighbouring.fromEnd) {
                    PositionResolution.ResolutionKind.PREVIOUS_PARAGRAPH
                } else {
                    PositionResolution.ResolutionKind.NEXT_PARAGRAPH
                }
                // Clamp to the end of the surviving paragraph, so a long stored offset never points
                // past content that no longer exists.
                val offset = if (neighbouring.fromEnd) {
                    CodePoints.length(neighbouring.block.text)
                } else {
                    clampToBlock(neighbouring.block, stored.codePointOffset)
                }
                return PositionResolution(
                    positionFor(chapter.id, chapterIndex, contentVersion, neighbouring.index, neighbouring.block, offset),
                    kind,
                )
            }
        }

        // 5/6: the chapter is gone (or it has no paragraphs at all). Use the saved ordinal to pick the
        // nearest surviving chapter, so a deleted chapter resolves next door rather than to the book's
        // first chapter.
        val fallbackChapter = neighbourChapter(chapters, chapterIndex, stored.chapterIndex)
        val firstParagraph = firstParagraph(fallbackChapter.blocks)
        if (firstParagraph != null) {
            return PositionResolution(
                positionFor(
                    fallbackChapter.id,
                    chapters.indexOfFirst { it.id == fallbackChapter.id },
                    contentVersion,
                    firstParagraph.index,
                    firstParagraph.block,
                    0,
                ),
                if (chapterIndex >= 0) {
                    PositionResolution.ResolutionKind.CHAPTER_START
                } else {
                    PositionResolution.ResolutionKind.NEIGHBOR_CHAPTER
                },
            )
        }

        // A chapter with no paragraphs at all: anchor at block 0 with an empty-text fingerprint.
        val emptyBlock = ""
        return PositionResolution(
            ReadingPosition(
                chapterId = fallbackChapter.id,
                contentVersion = contentVersion,
                blockIndex = 0,
                blockFingerprint = NovelFingerprints.block(emptyBlock),
                codePointOffset = 0,
                chapterIndex = chapters.indexOfFirst { it.id == fallbackChapter.id },
            ),
            PositionResolution.ResolutionKind.CHAPTER_START,
        )
    }

    /** Minimal view of a chapter the resolver needs. */
    interface ChapterView {
        val id: String
        val blocks: List<NovelBlock>
    }

    private sealed interface BlockMatch {
        data class Exact(val index: Int, val block: ParagraphBlock) : BlockMatch

        data class Moved(val index: Int, val block: ParagraphBlock) : BlockMatch

        data object Gone : BlockMatch
    }

    /** A paragraph-like block, narrowed to what anchoring needs. */
    private class ParagraphBlock(val text: String)

    /**
     * Finds the anchored block.
     *
     * The exact index is checked first, but only when its fingerprint matches; otherwise the
     * fingerprint is searched across the chapter. Preferring the exact index is what makes repeated
     * identical paragraphs resolve to the correct instance.
     */
    private fun locateBlock(blocks: List<NovelBlock>, storedIndex: Int, fingerprint: String): BlockMatch {
        val exact = blocks.getOrNull(storedIndex)?.asParagraph()
        if (exact != null && NovelFingerprints.block(exact.text) == fingerprint) {
            return BlockMatch.Exact(storedIndex, exact)
        }
        // Search outward from the stored index so a duplicate resolves to the closest occurrence.
        // On equal distance the *later* index wins: inserting paragraphs before the anchor (the common
        // edit) shifts it right, so the later twin is the better guess.
        val candidates = blocks.indices
            .filter { blocks[it].asParagraph() != null }
            .sortedWith(compareBy({ kotlin.math.abs(it - storedIndex) }, { -it }))
        for (index in candidates) {
            val paragraph = blocks[index].asParagraph() ?: continue
            if (NovelFingerprints.block(paragraph.text) == fingerprint) {
                return BlockMatch.Moved(index, paragraph)
            }
        }
        return BlockMatch.Gone
    }

    private class Neighbour(val index: Int, val block: ParagraphBlock, val fromEnd: Boolean)

    /** Nearest preceding paragraph that still exists, else nearest following one. */
    private fun nearestParagraph(blocks: List<NovelBlock>, storedIndex: Int): Neighbour? {
        // Prefer the paragraph immediately before the anchor position, then walk backwards.
        var index = (storedIndex - 1).coerceAtMost(blocks.lastIndex)
        while (index >= 0) {
            blocks[index].asParagraph()?.let { return Neighbour(index, it, fromEnd = true) }
            index--
        }
        // Then the nearest following one.
        index = storedIndex.coerceAtLeast(0)
        while (index <= blocks.lastIndex) {
            blocks[index].asParagraph()?.let { return Neighbour(index, it, fromEnd = false) }
            index++
        }
        return null
    }

    private fun firstParagraph(blocks: List<NovelBlock>): Neighbour? {
        for ((index, block) in blocks.withIndex()) {
            block.asParagraph()?.let { return Neighbour(index, it, fromEnd = false) }
        }
        return null
    }

    /**
     * Picks a surviving chapter near the anchor.
     *
     * Preference order: the chapter at the stored ordinal (clamped to the book), then the nearest
     * chapter at or before it, then the first readable chapter. A chapter with no paragraphs is
     * skipped so the result is always renderable when possible.
     */
    private fun neighbourChapter(
        chapters: List<ChapterView>,
        unmatchedIdIndex: Int,
        storedOrdinal: Int,
    ): ChapterView {
        val readable = { chapter: ChapterView -> chapter.blocks.any { it.asParagraph() != null } }
        if (storedOrdinal >= 0) {
            val clamped = storedOrdinal.coerceAtMost(chapters.lastIndex)
            chapters.getOrNull(clamped)?.takeIf(readable)?.let { return it }
            // Prefer the nearest earlier readable chapter, then later, then anything.
            for (index in clamped - 1 downTo 0) {
                chapters[index].takeIf(readable)?.let { return it }
            }
            for (index in clamped + 1..chapters.lastIndex) {
                chapters[index].takeIf(readable)?.let { return it }
            }
        }
        if (unmatchedIdIndex >= 0) {
            chapters.getOrNull(unmatchedIdIndex)?.takeIf(readable)?.let { return it }
        }
        return chapters.firstOrNull(readable) ?: chapters.first()
    }

    private fun clampToBlock(block: ParagraphBlock, offset: Int): Int =
        CodePoints.clamp(block.text, offset)

    private fun positionFor(
        chapterId: String,
        chapterIndex: Int,
        contentVersion: String,
        blockIndex: Int,
        block: ParagraphBlock,
        offset: Int,
    ): ReadingPosition = ReadingPosition(
        chapterId = chapterId,
        contentVersion = contentVersion,
        blockIndex = blockIndex,
        blockFingerprint = NovelFingerprints.block(block.text),
        codePointOffset = offset,
        chapterIndex = chapterIndex,
    )

    private fun NovelBlock.asParagraph(): ParagraphBlock? =
        (this as? NovelBlock.Paragraph)?.let { ParagraphBlock(it.text) }
}
