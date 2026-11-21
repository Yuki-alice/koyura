package app.koyura.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 白皮书 09 §9.1 修正表验收：第一版抄错、已经进代码的 6 处数值必须逐条对齐真实实现。
 * 这些值会被组件继承，改错一处就会整轮扩散，所以单独立测试而不是散在页面测试里。
 */
class DesignTokensTest {

    @Test
    fun unreadDotMatchesChapterAndHistoryRows() {
        // MangaChapterListItem.kt:128、HistoryItem.kt:178 都是 height(8.dp)。
        assertEquals(8.dp, DesignTokens.Sizes.UnreadDot)
    }

    @Test
    fun downloadIndicatorMatchesChapterIndicator() {
        // ChapterDownloadIndicator.kt:271 IndicatorSize = 26.dp；描边 = 直径/2；箭头 = 直径 − 7dp。
        assertEquals(26.dp, DesignTokens.Sizes.DownloadIndicator)
        assertEquals(13.dp, DesignTokens.Sizes.DownloadProgressStroke)
        assertEquals(19.dp, DesignTokens.Sizes.DownloadArrow)
        // 线性进度条仍用 M3 的 2dp 口径，不能与扇形描边混用。
        assertEquals(2.dp, DesignTokens.Sizes.ProgressBarStroke)
    }

    @Test
    fun themePreviewCardMatchesMeasuredWidth() {
        // AppThemePreferenceWidget.kt:95 .width(114.dp)；📐实测 228px ÷ 2 = 114dp。
        assertEquals(114.dp, DesignTokens.Sizes.ThemePreviewCard)
    }

    @Test
    fun detailCoverCapsAtOneHundredDp() {
        // MangaInfoHeader.kt:622 sizeIn(maxWidth = 100.dp)，高度按 2:3 = 150dp。
        assertEquals(100.dp, DesignTokens.Sizes.DetailCoverMaxWidthPhone)
        assertEquals(150.dp, DesignTokens.Sizes.DetailCoverHeightPhone)
    }

    @Test
    fun libraryListCoverIsSquareDerivedFromRowHeight() {
        // CommonMangaItem.kt:469 MangaCover.Square(fillMaxHeight())：行高 56 − 2×8 = 40dp。
        assertEquals(40.dp, DesignTokens.Sizes.LibraryListCoverSize)
    }

    @Test
    fun navigationAndHistorySizesStayAligned() {
        assertEquals(80.dp, DesignTokens.Sizes.NavigationBarHeight)
        assertEquals(80.dp, DesignTokens.Sizes.NavigationRailMinWidth)
        assertEquals(96.dp, DesignTokens.Sizes.HistoryItemHeight)
        assertEquals(720, DesignTokens.Breakpoints.TabletMinSmallestWidthDp)
        assertEquals(450.dp, DesignTokens.Breakpoints.TwoPaneFirstMax)
        assertEquals(460.dp, DesignTokens.Breakpoints.SheetMaxWidth)
    }
}
