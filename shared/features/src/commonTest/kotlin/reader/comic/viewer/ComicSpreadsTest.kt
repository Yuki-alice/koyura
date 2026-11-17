package app.koyura.features.reader.comic.viewer

import kotlin.test.Test
import kotlin.test.assertEquals

class ComicSpreadsTest {
    @Test
    fun singleLayoutStaysSolo() {
        val spreads = buildComicSpreads(
            pageCount = 5,
            layout = ComicPagerLayout.SINGLE_PAGE,
            isLandscape = true,
            firstPageSingle = false,
        )
        assertEquals(listOf(0, 1, 2, 3, 4), spreads.map { it.masterPageIndex })
    }

    @Test
    fun automaticPortraitStaysSingle() {
        val spreads = buildComicSpreads(
            pageCount = 4,
            layout = ComicPagerLayout.AUTOMATIC,
            isLandscape = false,
            firstPageSingle = true,
        )
        assertEquals(4, spreads.size)
    }

    @Test
    fun doubleWithCoverPairsRest() {
        val spreads = buildComicSpreads(
            pageCount = 7,
            layout = ComicPagerLayout.DOUBLE_PAGES,
            isLandscape = true,
            firstPageSingle = true,
        )
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5, 6)),
            spreads.map { it.pageIndices },
        )
    }

    @Test
    fun doubleWithoutCoverPairsFromZero() {
        val spreads = buildComicSpreads(
            pageCount = 5,
            layout = ComicPagerLayout.DOUBLE_PAGES,
            isLandscape = true,
            firstPageSingle = false,
        )
        assertEquals(
            listOf(listOf(0, 1), listOf(2, 3), listOf(4)),
            spreads.map { it.pageIndices },
        )
    }

    @Test
    fun widePagesStaySolo() {
        val spreads = buildComicSpreads(
            pageCount = 5,
            layout = ComicPagerLayout.DOUBLE_PAGES,
            isLandscape = true,
            firstPageSingle = true,
            widePages = setOf(1),
        )
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2, 3), listOf(4)),
            spreads.map { it.pageIndices },
        )
    }

    @Test
    fun displayOrderMirrorsRtl() {
        val spread = ComicSpread(listOf(3, 4))
        assertEquals(listOf(3, 4), spread.displayOrder(isRtl = false, invertDoublePages = false))
        assertEquals(listOf(4, 3), spread.displayOrder(isRtl = true, invertDoublePages = false))
        assertEquals(listOf(4, 3), spread.displayOrder(isRtl = false, invertDoublePages = true))
        assertEquals(listOf(3, 4), spread.displayOrder(isRtl = true, invertDoublePages = true))
    }

    @Test
    fun anchorSurvivesRelayout() {
        val single = buildComicSpreads(7, ComicPagerLayout.SINGLE_PAGE, false, true)
        val doubled = buildComicSpreads(7, ComicPagerLayout.DOUBLE_PAGES, true, true)
        // Page 2 lives in spread 2 when single, spread 1 when doubled; master stays logical.
        // Page 2 lives in spread 2 when single, spread 1 ([1,2]) when doubled.
        // The viewer scrolls to the spread containing page 2, whose master is 1.
        assertEquals(2, spreadIndexForPage(single, 2))
        assertEquals(1, spreadIndexForPage(doubled, 2))
        assertEquals(1, masterPageForSpread(doubled, spreadIndexForPage(doubled, 2)))
    }
}
