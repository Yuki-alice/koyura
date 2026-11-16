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
 * Resolution is **lazy by chapter**. Only the chapter ids in the table of contents are known up front;
 * a chapter's blocks are requested through [ChapterLoader] when a rule actually needs them. When the
 * stored chapter still exists and resolves exactly (or re-anchors inside the same chapter), that one
 * chapter is the only body read — an unreadable, oversized or corrupt later chapter cannot break a
 * resume that never needs it. Only when a rule has to move to another chapter are candidates loaded,
 * one at a time, in the documented preference order.
 *
 * A candidate that fails to load propagates its error ([NovelImportException.LimitExceeded],
 * [NovelImportException.CorruptedArchive], …) instead of being silently skipped: masking an archive or
 * size-limit failure would hand the caller a position in a book that cannot be rendered.
 *
 * Every returned position is valid for the target book, and [PositionResolution.resolution] explains
 * which rule produced it.
 */
object ReadingPositionResolver {

    /**
     * Supplies a chapter's blocks on demand.
     *
     * Implementations are expected to be cheap to call repeatedly for the same id (both importers
     * cache parsed chapters), so a caller may load the same chapter more than once without paying for
     * a second parse.
     */
    fun interface ChapterLoader {
        /** Returns the blocks of [chapterId] in reading order. */
        fun blocksOf(chapterId: String): List<NovelBlock>
    }

    /**
     * Resolves [stored] against a book described by [chapterIds] and [contentVersion].
     *
     * @param chapterIds the target book's chapter ids in table-of-contents order. This is the cheap
     *   metadata only; no chapter body is touched until a rule needs it.
     * @param contentVersion the target book's content version, stamped onto the result.
     * @param stored the position saved from an earlier import or an earlier version of the file.
     * @param loader reads a chapter's blocks when a rule needs them.
     */
    fun resolve(
        chapterIds: List<String>,
        contentVersion: String,
        stored: ReadingPosition,
        loader: ChapterLoader,
    ): PositionResolution {
        require(chapterIds.isNotEmpty()) { "a book must expose at least one chapter" }

        // A chapter body is loaded at most once per resolution, so fallback candidate scans cannot
        // read the same document twice even when several rules consider it.
        val loaded = HashMap<String, List<NovelBlock>>()
        fun blocksOf(chapterId: String): List<NovelBlock> =
            loaded.getOrPut(chapterId) { loader.blocksOf(chapterId) }

        // 1/2: same chapter still exists.
        val chapterIndex = chapterIds.indexOf(stored.chapterId)
        if (chapterIndex >= 0) {
            val blocks = blocksOf(stored.chapterId)
            when (val anchored = locateBlock(blocks, stored.blockIndex, stored.blockFingerprint)) {
                is BlockMatch.Exact -> {
                    val offset = clampToBlock(anchored.block, stored.codePointOffset)
                    return PositionResolution(
                        positionFor(stored.chapterId, chapterIndex, contentVersion, anchored.index, anchored.block, offset),
                        PositionResolution.ResolutionKind.EXACT,
                    )
                }
                is BlockMatch.Moved -> {
                    val offset = clampToBlock(anchored.block, stored.codePointOffset)
                    return PositionResolution(
                        positionFor(stored.chapterId, chapterIndex, contentVersion, anchored.index, anchored.block, offset),
                        PositionResolution.ResolutionKind.REANCHORED_BLOCK,
                    )
                }
                BlockMatch.Gone -> Unit
            }

            // 3/4: recover to a neighbouring paragraph within the same chapter. Running out of
            // paragraphs here falls through to the cross-chapter rules, as before.
            val neighbouring = nearestParagraph(blocks, stored.blockIndex)
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
                    positionFor(stored.chapterId, chapterIndex, contentVersion, neighbouring.index, neighbouring.block, offset),
                    kind,
                )
            }
        }

        // 5/6: the chapter is gone (or it has no paragraphs at all). Walk the candidate chapters in
        // the documented preference order and stop at the first one that can render a paragraph.
        for (candidateIndex in fallbackOrder(chapterIds, chapterIndex, stored.chapterIndex)) {
            val candidateId = chapterIds[candidateIndex]
            val firstParagraph = firstParagraph(blocksOf(candidateId))
            if (firstParagraph != null) {
                return PositionResolution(
                    positionFor(candidateId, candidateIndex, contentVersion, firstParagraph.index, firstParagraph.block, 0),
                    if (chapterIndex >= 0) {
                        PositionResolution.ResolutionKind.CHAPTER_START
                    } else {
                        PositionResolution.ResolutionKind.NEIGHBOR_CHAPTER
                    },
                )
            }
        }

        // A book with no paragraphs anywhere: anchor at block 0 of the first chapter with an
        // empty-text fingerprint, matching the old eager implementation.
        val emptyBlock = ""
        return PositionResolution(
            ReadingPosition(
                chapterId = chapterIds.first(),
                contentVersion = contentVersion,
                blockIndex = 0,
                blockFingerprint = NovelFingerprints.block(emptyBlock),
                codePointOffset = 0,
                chapterIndex = 0,
            ),
            PositionResolution.ResolutionKind.CHAPTER_START,
        )
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
     * Candidate chapter indexes to try when the anchor chapter cannot supply a paragraph.
     *
     * The order is the historical one: the saved ordinal clamped into the book, then earlier chapters
     * nearest-first, then later chapters nearest-first, then the matched id (a chapter that exists but
     * has no paragraphs), then every chapter as a last sweep. Duplicates are dropped so each candidate
     * is loaded at most once.
     */
    private fun fallbackOrder(
        chapterIds: List<String>,
        matchedIndex: Int,
        storedOrdinal: Int,
    ): List<Int> {
        val order = LinkedHashSet<Int>()
        if (storedOrdinal >= 0) {
            val clamped = storedOrdinal.coerceAtMost(chapterIds.lastIndex)
            order += clamped
            for (index in clamped - 1 downTo 0) order += index
            for (index in clamped + 1..chapterIds.lastIndex) order += index
        }
        if (matchedIndex >= 0) order += matchedIndex
        for (index in chapterIds.indices) order += index
        return order.toList()
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
