package app.koyura.features.reader.comic.viewer

/**
 * Paginated/webtoon reading directions, aligned with Komikku's `ReadingMode`.
 *
 * IDs are stable API: the main agent maps C's effective preferences to these values.
 * - LEFT_TO_RIGHT: horizontal pager, logical order matches layout order.
 * - RIGHT_TO_LEFT: horizontal pager mirrored; taps, swipes and arrow keys follow the
 *   logical reading order (next is visually left).
 * - VERTICAL: vertical pager, one spread per viewport, top-to-bottom.
 * - WEBTOON: continuous vertical strip without inter-page gaps.
 * - CONTINUOUS_VERTICAL: vertical strip with a fixed inter-page gap and tap-to-step
 *   scrolling (Komikku builds it as `WebtoonViewer(isContinuous = false)`).
 */
enum class ComicReadingMode(val id: String) {
    LEFT_TO_RIGHT("LEFT_TO_RIGHT"),
    RIGHT_TO_LEFT("RIGHT_TO_LEFT"),
    VERTICAL("VERTICAL"),
    WEBTOON("WEBTOON"),
    CONTINUOUS_VERTICAL("CONTINUOUS_VERTICAL"),
    ;

    companion object {
        fun fromId(id: String): ComicReadingMode =
            entries.firstOrNull { it.id == id } ?: LEFT_TO_RIGHT
    }
}

/** Paging layout for the three pager modes. Mirrors `PagerConfig.PageLayout`. */
enum class ComicPagerLayout(val id: String) {
    SINGLE_PAGE("SINGLE_PAGE"),
    DOUBLE_PAGES("DOUBLE_PAGES"),
    AUTOMATIC("AUTOMATIC"),
    ;

    companion object {
        fun fromId(id: String): ComicPagerLayout =
            entries.firstOrNull { it.id == id } ?: SINGLE_PAGE
    }
}

/**
 * Image scaling contract. `CROP` is folded into [ComicViewerOptions.cropBorders]:
 * when crop is on, the viewer fills the viewport (cropping the overflow) instead of
 * letterboxing.
 */
enum class ComicScaleFit(val id: String) {
    FIT("FIT"),
    FIT_WIDTH("FIT_WIDTH"),
    FIT_HEIGHT("FIT_HEIGHT"),
    ORIGINAL("ORIGINAL"),
    ;

    companion object {
        fun fromId(id: String): ComicScaleFit =
            entries.firstOrNull { it.id == id } ?: FIT
    }
}

/**
 * Options passed from the host (mapped by the main agent from C's effective preferences).
 *
 * - [pagerLayout]: SINGLE_PAGE always pairs solo; DOUBLE_PAGES pairs adjacent pages;
 *   AUTOMATIC pairs only in landscape (width > height), matching Komikku's
 *   `setDoublePageMode` (double in landscape, single in portrait).
 * - [firstPageSingle]: cover stays solo when pairing (Komikku's shift control).
 * - [invertDoublePages]: mirrors the left/right order inside a spread
 *   (`isLTR = !isRtl xor invert`).
 * - [scaleFit]: FIT / FIT_WIDTH / FIT_HEIGHT / ORIGINAL.
 * - [cropBorders]: fills the viewport instead of letterboxing (approximation of Komikku's
 *   border-crop pass; exact pixel cropping stays with the platform decoder).
 * - [doubleTapZoom]: double-tap toggles 1x/2.5x on pager pages and webtoon images.
 */
data class ComicViewerOptions(
    val mode: ComicReadingMode = ComicReadingMode.LEFT_TO_RIGHT,
    val pagerLayout: ComicPagerLayout = ComicPagerLayout.SINGLE_PAGE,
    val firstPageSingle: Boolean = true,
    val invertDoublePages: Boolean = false,
    val scaleFit: ComicScaleFit = ComicScaleFit.FIT,
    val cropBorders: Boolean = false,
    val doubleTapZoom: Boolean = true,
)

/** Chapter-boundary intent. The host owns the actual chapter switch. */
enum class ComicChapterBoundary {
    PREVIOUS,
    NEXT,
}

/**
 * Jump command from the host (chapter list, slider, continue-reading).
 *
 * - [requestId]: host-incremented id; the same request is consumed once.
 * - [contentSeq]: the [app.koyura.features.reader.comic.ReaderSession.contentSeq] the
 *   command targets; commands for older chapters are ignored.
 * - [pageIndex]: zero-based raw image index (the position inside `pageKeys`).
 *   Display is converted to one-based by C.
 */
data class ComicViewerJump(
    val requestId: Long,
    val contentSeq: Long,
    val pageIndex: Int,
)
