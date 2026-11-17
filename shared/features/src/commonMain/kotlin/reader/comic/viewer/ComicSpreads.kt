package app.koyura.features.reader.comic.viewer

/**
 * One pager viewport in logical reading order.
 *
 * [pageIndices] holds one or two zero-based raw image indices. Order is always logical
 * (ascending page numbers); visual left/right order is derived by [displayOrder].
 * The master page reported to [app.koyura.features.reader.comic.ReaderSession] is
 * [masterPageIndex], the first entry in logical order, so saves always use the original
 * `pageKey` and survive layout/mode/width changes.
 */
data class ComicSpread(val pageIndices: List<Int>) {
    init {
        require(pageIndices.size in 1..2) { "spread holds 1-2 pages" }
    }

    /** Master page in logical reading order. */
    val masterPageIndex: Int get() = pageIndices.first()
}

/**
 * Builds spreads in logical order.
 *
 * Rules (from `PagerViewerAdapter.setJoinedItems` + `ReaderActivity.setDoublePageMode`):
 * - SINGLE_PAGE, or AUTOMATIC in portrait, yields solo spreads.
 * - DOUBLE_PAGES, or AUTOMATIC in landscape, pairs adjacent pages; when
 *   [firstPageSingle] is true page 0 stays solo (cover), then pairs follow.
 * - Pages in [widePages] (landscape spreads detected after decode) stay solo and never
 *   pair with a neighbour; neighbours next to a wide page stay solo as well.
 * - The trailing odd page stays solo.
 *
 * @param isLandscape true when the viewport is wider than tall; only matters for AUTOMATIC.
 */
fun buildComicSpreads(
    pageCount: Int,
    layout: ComicPagerLayout,
    isLandscape: Boolean,
    firstPageSingle: Boolean,
    widePages: Set<Int> = emptySet(),
): List<ComicSpread> {
    if (pageCount <= 0) return emptyList()
    val doubled = when (layout) {
        ComicPagerLayout.DOUBLE_PAGES -> true
        ComicPagerLayout.AUTOMATIC -> isLandscape
        ComicPagerLayout.SINGLE_PAGE -> false
    }
    if (!doubled) return List(pageCount) { ComicSpread(listOf(it)) }

    val spreads = mutableListOf<ComicSpread>()
    var index = 0
    if (firstPageSingle) {
        spreads += ComicSpread(listOf(0))
        index = 1
    }
    while (index < pageCount) {
        if (index in widePages) {
            spreads += ComicSpread(listOf(index))
            index += 1
            continue
        }
        val next = index + 1
        if (next >= pageCount || next in widePages) {
            spreads += ComicSpread(listOf(index))
            index += 1
        } else {
            spreads += ComicSpread(listOf(index, next))
            index += 2
        }
    }
    return spreads
}

/**
 * Visual order inside [spread]: logical left-to-right when
 * `isLtr = !isRtl xor invert` (Komikku's `isLTR = (viewer !is R2L) xor invertDoublePages`),
 * otherwise reversed. RTL reading shows the logical first page on the right.
 */
fun ComicSpread.displayOrder(
    isRtl: Boolean,
    invertDoublePages: Boolean,
): List<Int> {
    val isLtr = !isRtl xor invertDoublePages
    return if (isLtr) pageIndices else pageIndices.reversed()
}

/** Index of the spread whose logical pages contain [pageIndex], or 0 when absent. */
fun spreadIndexForPage(
    spreads: List<ComicSpread>,
    pageIndex: Int,
): Int {
    val found = spreads.indexOfFirst { pageIndex in it.pageIndices }
    return if (found >= 0) found else 0
}

/** Master (logical first) page of [spreadIndex], or 0 when out of range. */
fun masterPageForSpread(
    spreads: List<ComicSpread>,
    spreadIndex: Int,
): Int = spreads.getOrNull(spreadIndex)?.masterPageIndex ?: 0
