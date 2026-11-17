package app.koyura.features.reader.comic.chrome

import app.koyura.domain.reader.preferences.ReaderLayout
import app.koyura.features.reader.comic.viewer.ComicPagerLayout
import app.koyura.features.reader.comic.viewer.buildComicSpreads
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderChromeNavigationTest {
    private fun doubleSpreads() = buildComicSpreads(
        pageCount = 7,
        layout = ComicPagerLayout.DOUBLE_PAGES,
        isLandscape = true,
        firstPageSingle = true,
    )

    @Test
    fun spreadEndFollowsPairedPages() {
        val spreads = doubleSpreads()
        // [0], [1,2], [3,4], [5,6]
        assertNull(spreadEndForPage(spreads, 0))
        assertEquals(2, spreadEndForPage(spreads, 1))
        assertEquals(1, spreadEndForPage(spreads, 2))
        assertEquals(4, spreadEndForPage(spreads, 3))
        assertNull(spreadEndForPage(emptyList(), 0))
        assertNull(spreadEndForPage(spreads, 99))
    }

    @Test
    fun spreadRangePrefixFormatsSoloAndPair() {
        assertEquals("1", spreadRangePrefix(0, null))
        assertEquals("1", spreadRangePrefix(0, 0))
        assertEquals("4-5", spreadRangePrefix(3, 4))
        assertEquals("5-4", spreadRangePrefix(4, 3))
    }

    @Test
    fun layoutSwitcherCyclesSingleDoubleAuto() {
        assertEquals(ReaderLayout.DOUBLE_PAGES, nextReaderLayout(ReaderLayout.SINGLE_PAGE))
        assertEquals(ReaderLayout.AUTOMATIC, nextReaderLayout(ReaderLayout.DOUBLE_PAGES))
        assertEquals(ReaderLayout.SINGLE_PAGE, nextReaderLayout(ReaderLayout.AUTOMATIC))
    }
}
