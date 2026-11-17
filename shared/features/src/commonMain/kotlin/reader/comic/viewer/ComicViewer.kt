package app.koyura.features.reader.comic.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.koyura.domain.reader.ChapterContent
import app.koyura.features.resources.Res
import app.koyura.features.resources.reader_empty_pages
import app.koyura.features.resources.reader_page_failed
import app.koyura.features.resources.reader_retry
import org.jetbrains.compose.resources.stringResource
import app.koyura.features.reader.comic.ReaderPageState
import app.koyura.features.reader.comic.ReaderSession
import app.koyura.features.reader.comic.decodeToImageBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Stable UI test hooks, identical values to the production reader for regression reuse. */
const val ComicViewerRootTag = "comic-viewer"
const val ComicTapPrevTag = "reader-tap-prev"
const val ComicTapNextTag = "reader-tap-next"
const val ComicTapMenuTag = "reader-tap-menu"

fun comicPageTag(index: Int) = "reader-page-$index"
fun comicPageImageTag(index: Int) = "reader-page-image-$index"
fun comicPageRetryTag(pageKey: String) = "reader-page-retry-$pageKey"

/** User-scroll distance that hides the menu on strips (Komikku WebtoonViewer parity). */
private const val WEBTOON_HIDE_THRESHOLD_DP = 24

/**
 * Komikku-style shared comic viewer.
 *
 * The host owns chapters: central taps only call [onToggleMenu], boundary over-scroll only
 * reports [onChapterBoundary] (PREVIOUS/NEXT). Position reporting goes through [session]:
 * the visible spread's master page (logical reading order) is marked displayed, settled
 * spreads are saved by original `pageKey`, so layout/mode/width changes keep the same page.
 */
@Composable
fun ComicViewer(
    session: ReaderSession,
    content: ChapterContent,
    options: ComicViewerOptions,
    jump: ComicViewerJump?,
    onToggleMenu: () -> Unit,
    onChapterBoundary: (ComicChapterBoundary) -> Unit,
    modifier: Modifier = Modifier,
    /** Whether the chrome is currently visible; strips auto-hide it on scroll (Komikku parity). */
    menuVisible: Boolean = true,
    /** Fires once when a strip scroll passes the hide threshold while the menu is visible. */
    onHideMenu: () -> Unit = {},
) {
    when (options.mode) {
        ComicReadingMode.LEFT_TO_RIGHT,
        ComicReadingMode.RIGHT_TO_LEFT,
        ComicReadingMode.VERTICAL,
        -> PagerComicViewer(
            session = session,
            content = content,
            options = options,
            jump = jump,
            onToggleMenu = onToggleMenu,
            onChapterBoundary = onChapterBoundary,
            modifier = modifier.testTag(ComicViewerRootTag),
        )

        ComicReadingMode.WEBTOON,
        ComicReadingMode.CONTINUOUS_VERTICAL,
        -> WebtoonComicViewer(
            session = session,
            content = content,
            options = options,
            jump = jump,
            onToggleMenu = onToggleMenu,
            onChapterBoundary = onChapterBoundary,
            modifier = modifier.testTag(ComicViewerRootTag),
            menuVisible = menuVisible,
            onHideMenu = onHideMenu,
        )
    }
}

// ---------------------------------------------------------------------------
// Pager (LTR / RTL / vertical)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagerComicViewer(
    session: ReaderSession,
    content: ChapterContent,
    options: ComicViewerOptions,
    jump: ComicViewerJump?,
    onToggleMenu: () -> Unit,
    onChapterBoundary: (ComicChapterBoundary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val isRtl = options.mode == ComicReadingMode.RIGHT_TO_LEFT
    val isVertical = options.mode == ComicReadingMode.VERTICAL
    val hasPrev = session.previousChapterId(content.chapterId) != null
    val hasNext = session.nextChapterId(content.chapterId) != null

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        var widePages by remember(content.pageKeys) { mutableStateOf(emptySet<Int>()) }
        val logicalSpreads = remember(
            content.pageKeys,
            options.pagerLayout,
            isLandscape,
            options.firstPageSingle,
            widePages,
        ) {
            buildComicSpreads(
                pageCount = content.pageKeys.size,
                layout = options.pagerLayout,
                isLandscape = isLandscape,
                firstPageSingle = options.firstPageSingle,
                widePages = widePages,
            )
        }
        // Komikku reverses the adapter for R2L; taps/keys stay visual.
        val spreads = remember(logicalSpreads, isRtl) {
            if (isRtl && !isVertical) logicalSpreads.reversed() else logicalSpreads
        }
        if (spreads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.reader_empty_pages))
            }
            return@BoxWithConstraints
        }

        val startSpread = remember(session.contentSeq, content.chapterId) {
            val anchor = content.startPageIndex.coerceIn(0, content.pageKeys.size - 1)
            val logical = spreadIndexForPage(logicalSpreads, anchor)
            if (isRtl && !isVertical) logicalSpreads.size - 1 - logical else logical
        }
        val pagerState = rememberPagerState(
            initialPage = startSpread.coerceIn(0, spreads.size - 1),
        ) { spreads.size }
        var consumedRequest by remember(session.contentSeq) { mutableStateOf<Long?>(null) }
        var zoomPulse by remember(session.contentSeq) { mutableStateOf(0L) }
        val focusRequester = remember { FocusRequester() }

        fun displayToLogical(displayIndex: Int): Int =
            if (isRtl && !isVertical) spreads.size - 1 - displayIndex else displayIndex

        /**
         * Page reported for a displayed spread. Fresh turns land on spreads that do
         * not contain the previous anchor, so they report the master page (logical
         * reading order). Relayouts (resize, mode/layout switch) keep the spread
         * containing the anchor, which is then reported unchanged instead of being
         * downgraded to the spread master.
         */
        fun reportedPage(displayIndex: Int): Int? {
            val spread = spreads.getOrNull(displayIndex) ?: return null
            val anchor = session.currentPage
            return if (anchor in spread.pageIndices) anchor else spread.masterPageIndex
        }

        fun goVisual(delta: Int) {
            val target = pagerState.currentPage + delta
            val mirrored = isRtl && !isVertical
            if (target < 0) {
                // Visual start: story start in LTR, story end in RTL.
                val boundary = if (mirrored) ComicChapterBoundary.NEXT else ComicChapterBoundary.PREVIOUS
                val hasNeighbour = if (mirrored) hasNext else hasPrev
                if (hasNeighbour) onChapterBoundary(boundary)
                return
            }
            if (target >= spreads.size) {
                // Visual end: story end in LTR, story start in RTL.
                val boundary = if (mirrored) ComicChapterBoundary.PREVIOUS else ComicChapterBoundary.NEXT
                val hasNeighbour = if (mirrored) hasPrev else hasNext
                if (hasNeighbour) onChapterBoundary(boundary)
                return
            }
            scope.launch { pagerState.animateScrollToPage(target) }
        }

        // Jump: same request once, stale chapters ignored.
        LaunchedEffect(jump, session.contentSeq, spreads) {
            val command = jump ?: return@LaunchedEffect
            if (command.contentSeq != session.contentSeq) return@LaunchedEffect
            if (command.requestId == consumedRequest) return@LaunchedEffect
            if (command.pageIndex !in content.pageKeys.indices) return@LaunchedEffect
            val logical = spreadIndexForPage(logicalSpreads, command.pageIndex)
            val display = (if (isRtl && !isVertical) logicalSpreads.size - 1 - logical else logical)
                .coerceIn(0, spreads.size - 1)
            consumedRequest = command.requestId
            if (pagerState.currentPage != display) pagerState.scrollToPage(display)
            // A jump is an explicit position request. Commit the anchor and save even
            // when the target already sits in the visible spread: there is no page
            // change event then, and anchor-preserving reporting would keep page 1.
            session.markDisplayed(command.pageIndex)
            session.savePage(command.pageIndex)
        }

        // Re-anchor on layout/width/mode change: scroll to the spread that still
        // contains the current original page; reporting keeps that page.
        LaunchedEffect(spreads, options.mode) {
            val current = session.currentPage
            if (current !in content.pageKeys.indices) return@LaunchedEffect
            val logical = spreadIndexForPage(logicalSpreads, current)
            val display = if (isRtl && !isVertical) logicalSpreads.size - 1 - logical else logical
            if (display != pagerState.currentPage) pagerState.scrollToPage(display)
        }

        // Displayed + settled reporting (anchor-preserving, see reportedPage).
        LaunchedEffect(pagerState, spreads) {
            snapshotFlow { pagerState.currentPage }.collect { display ->
                val page = reportedPage(display) ?: return@collect
                if (page != session.currentPage) session.markDisplayed(page)
            }
        }
        LaunchedEffect(pagerState, spreads) {
            snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { display ->
                val page = reportedPage(display) ?: return@collect
                session.savePage(page)
            }
        }
        // Demand loading: current spread plus neighbours. Only pages never attempted
        // are requested here; failed pages wait for an explicit retry (Komikku shows
        // the error row until the user retries), so a failure stays observable.
        LaunchedEffect(pagerState, content.pageKeys) {
            snapshotFlow { pagerState.currentPage }.collect { display ->
                val logical = displayToLogical(display)
                val pages = logicalSpreads.getOrNull(logical)?.pageIndices ?: return@collect
                val neighbours = buildSet {
                    addAll(pages)
                    logicalSpreads.getOrNull(logical - 1)?.pageIndices?.let(::addAll)
                    logicalSpreads.getOrNull(logical + 1)?.pageIndices?.let(::addAll)
                }
                neighbours.forEach { pageIndex ->
                    if (!session.pages.containsKey(content.pageKeys[pageIndex])) {
                        session.ensurePage(content.pageKeys[pageIndex])
                    }
                }
            }
        }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        // Visual keys (Komikku): arrows move the viewport, not the story.
                        Key.DirectionRight, Key.PageDown, Key.DirectionDown -> {
                            goVisual(1)
                            true
                        }

                        Key.DirectionLeft, Key.PageUp, Key.DirectionUp -> {
                            goVisual(-1)
                            true
                        }

                        else -> false
                    }
                },
        ) {
            key(session.contentSeq) {
                if (isVertical) {
                    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { display ->
                        PagerSpread(
                            spread = spreads[display],
                            content = content,
                            session = session,
                            options = options,
                            isRtl = false,
                            zoomPulse = zoomPulse,
                            onWideDetected = { widePages = widePages + it },
                        )
                    }
                } else {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { display ->
                        PagerSpread(
                            spread = spreads[display],
                            content = content,
                            session = session,
                            options = options,
                            isRtl = isRtl,
                            zoomPulse = zoomPulse,
                            onWideDetected = { widePages = widePages + it },
                        )
                    }
                }
            }

            // Tap zones above the pager (below chrome): the tiled overlay resolves each
            // tap to prev/menu/next per the configured zone mode. Double-tap on a zone zooms
            // instead of turning, so single taps never fire twice.
            TapZoneOverlay(
                zone = options.tapZone,
                isVertical = isVertical,
                invert = options.invertTapZone,
                onPrev = { goVisual(-1) },
                onMenu = onToggleMenu,
                onNext = { goVisual(1) },
                onDoubleTap = { zoomPulse++ },
            )
        }
    }
}

@Composable
private fun PagerSpread(
    spread: ComicSpread,
    content: ChapterContent,
    session: ReaderSession,
    options: ComicViewerOptions,
    isRtl: Boolean,
    zoomPulse: Long,
    onWideDetected: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val order = spread.displayOrder(isRtl, options.invertDoublePages)
    Row(Modifier.fillMaxSize()) {
        order.forEach { pageIndex ->
            Box(Modifier.weight(1f).fillMaxHeight()) {
                ZoomableComicPage(
                    index = pageIndex,
                    pageKey = content.pageKeys[pageIndex],
                    state = session.pages[content.pageKeys[pageIndex]],
                    options = options,
                    zoomPulse = zoomPulse,
                    onRetry = { scope.launch { session.retryPage(content.pageKeys[pageIndex]) } },
                    onWideDetected = { onWideDetected(pageIndex) },
                    viewportFill = true,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Webtoon (continuous + gap variants)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WebtoonComicViewer(
    session: ReaderSession,
    content: ChapterContent,
    options: ComicViewerOptions,
    jump: ComicViewerJump?,
    onToggleMenu: () -> Unit,
    onChapterBoundary: (ComicChapterBoundary) -> Unit,
    modifier: Modifier = Modifier,
    menuVisible: Boolean = true,
    onHideMenu: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Komikku: WEBTOON (isContinuous=true) has no inter-page gap; CONTINUOUS_VERTICAL
    // (isContinuous=false) separates pages with a 15dp gap and steps page-by-page on tap.
    val continuous = options.mode == ComicReadingMode.WEBTOON
    val gap = if (continuous) 0.dp else 15.dp
    val hasPrev = session.previousChapterId(content.chapterId) != null
    val hasNext = session.nextChapterId(content.chapterId) != null
    // LazyColumn positions include an optional header spacer (previous-chapter end).
    val headerCount = if (hasPrev) 1 else 0
    if (content.pageKeys.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.reader_empty_pages))
        }
        return
    }
    // Start (and re-anchor after mode switches) at the currently displayed page so
    // pager -> webtoon and WEBTOON -> CONTINUOUS keep the same original page.
    val startItem = remember(session.contentSeq, options.mode) {
        (headerCount + session.currentPage)
            .coerceIn(headerCount, headerCount + content.pageKeys.size - 1)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startItem)
    var consumedRequest by remember(session.contentSeq) { mutableStateOf<Long?>(null) }
    // Reset the strip on chapter switches: the list state otherwise survives across
    // chapters and would keep the stale offset. Lands on the restored position.
    LaunchedEffect(session.contentSeq) {
        listState.scrollToItem(startItem)
    }
    // Pending jump target (item index). Item heights grow as images decode and scroll
    // offsets are pixel-based, so the jump re-asserts itself on every visible-range
    // change until the target settles at the top — no clock waits involved.
    var pendingJumpTarget by remember(session.contentSeq) { mutableStateOf<Int?>(null) }

    // Jump: same request once, stale chapters ignored.
    LaunchedEffect(jump, session.contentSeq) {
        val command = jump ?: return@LaunchedEffect
        if (command.contentSeq != session.contentSeq) return@LaunchedEffect
        if (command.requestId == consumedRequest) return@LaunchedEffect
        if (command.pageIndex !in content.pageKeys.indices) return@LaunchedEffect
        consumedRequest = command.requestId
        pendingJumpTarget = headerCount + command.pageIndex
    }

    // Preload a window around a pending jump target: scrolling alone cannot pull in
    // distant pages (the display loader only covers the visible window), and without
    // the neighbours' aspect heights the strip cannot grow enough to reach the target.
    // A fast jump skips intermediate positions, so the window (not just the target)
    // is requested explicitly.
    LaunchedEffect(pendingJumpTarget, content.pageKeys) {
        val target = pendingJumpTarget ?: return@LaunchedEffect
        val targetPage = (target - headerCount).coerceIn(0, content.pageKeys.size - 1)
        val from = (targetPage - 3).coerceAtLeast(0)
        val until = (targetPage + 3).coerceAtMost(content.pageKeys.size - 1)
        (from..until).forEach { index -> session.ensurePage(content.pageKeys[index]) }
    }

    // Re-assert a pending jump until it settles. Item heights grow as images decode
    // (aspect heights are unknown up front) and scroll offsets are pixel-based, so an
    // early jump clamps; re-issuing on every page-state change converges once the
    // neighbourhood has its final heights. The request clears when the target page
    // is settled and on top. Reachability is guaranteed by the viewport-sized end
    // spacer below: any item can be brought to the top. No clock waits involved.
    LaunchedEffect(pendingJumpTarget, listState, session.contentSeq) {
        val target = pendingJumpTarget ?: return@LaunchedEffect
        val targetPage = (target - headerCount).coerceIn(0, content.pageKeys.size - 1)
        val windowFrom = (targetPage - 2).coerceAtLeast(0)
        val windowUntil = (targetPage + 1).coerceAtMost(content.pageKeys.size - 1)
        snapshotFlow {
            val first = listState.firstVisibleItemIndex
            val settled = (windowFrom..windowUntil).all { index ->
                when (session.pages[content.pageKeys[index]]) {
                    is ReaderPageState.Ready, is ReaderPageState.Failed -> true
                    else -> false
                }
            }
            first to settled
        }.collect { (first, settled) ->
            if (first == target && settled) {
                pendingJumpTarget = null
            } else {
                listState.scrollToItem(target)
            }
        }
    }

    // Komikku WebtoonViewer parity: a user scroll past a small threshold hides the menu once
    // it is visible; scrolling never re-shows it (only taps toggle). Programmatic motion is
    // excluded: jumps run while a jump target is pending, the initial settle scroll is absorbed
    // by baselining the first observed position, and fling-free position reports re-baseline.
    LaunchedEffect(listState, session.contentSeq, menuVisible) {
        if (!menuVisible) return@LaunchedEffect
        val thresholdPx = with(density) { WEBTOON_HIDE_THRESHOLD_DP.dp.toPx() }
        var baseIndex = listState.firstVisibleItemIndex
        var baseOffset = listState.firstVisibleItemScrollOffset
        var settled = false
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress,
            )
        }.collect { (index, offset, inProgress) ->
            if (pendingJumpTarget != null || !settled) {
                baseIndex = index
                baseOffset = offset
                settled = true
                return@collect
            }
            if (!inProgress) {
                baseIndex = index
                baseOffset = offset
                return@collect
            }
            val moved = index != baseIndex ||
                kotlin.math.abs(offset - baseOffset) > thresholdPx
            if (moved) onHideMenu()
        }
    }

    // Displayed + settled: first visible page drives both (webtoon has no snap point).
    // Only pages never attempted are requested; failures wait for explicit retry.
    LaunchedEffect(listState, content.pageKeys) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { position ->
            val pageIndex = (position - headerCount).coerceIn(0, content.pageKeys.size - 1)
            session.markDisplayed(pageIndex)
            if (!session.pages.containsKey(content.pageKeys[pageIndex])) {
                session.ensurePage(content.pageKeys[pageIndex])
            }
            if (pageIndex + 1 < content.pageKeys.size &&
                !session.pages.containsKey(content.pageKeys[pageIndex + 1])
            ) {
                session.ensurePage(content.pageKeys[pageIndex + 1])
            }
        }
    }
    LaunchedEffect(listState, content.pageKeys) {
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { position ->
            val pageIndex = (position - headerCount).coerceIn(0, content.pageKeys.size - 1)
            session.savePage(pageIndex)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            // Header spacer keeps jump math stable (positions shift by headerCount when
            // a previous chapter exists). Boundary crossings are reported by edge taps
            // at the list ends (like pager over-scroll), because a full-screen tap
            // overlay sits above the list and would swallow button taps.
            if (hasPrev) {
                item(key = "prev-spacer") {
                    Spacer(Modifier.height(8.dp))
                }
            }
            itemsIndexed(content.pageKeys, key = { _, key -> key }) { pageIndex, pageKey ->
                ZoomableComicPage(
                    index = pageIndex,
                    pageKey = pageKey,
                    state = session.pages[pageKey],
                    options = options,
                    zoomPulse = 0L,
                    onRetry = { scope.launch { session.retryPage(pageKey) } },
                    onWideDetected = {},
                    // LazyColumn items have unbounded height: fill the width and wrap the
                    // aspect height so the strip actually scrolls (fillMaxSize collapses).
                    viewportFill = false,
                )
            }
            item(key = "end-spacer") {
                // Viewport-sized trailing pad: with it, every page (including the last)
                // can be brought to the top, so jump targets always converge by index.
                Spacer(Modifier.height(maxHeight))
            }
        }

        fun atTop(): Boolean {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull() ?: return true
            return first.index == 0 && first.offset == 0
        }

        fun atBottom(): Boolean {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return false
            return info.visibleItemsInfo.lastOrNull()?.index == total - 1
        }

        // Edge tap zones scroll stepwise; at the ends they report the chapter
        // boundary instead (the host owns the actual chapter switch). The tiled overlay
        // keeps the same prev/menu/next actions under the configured zone mode.
        TapZoneOverlay(
            zone = options.tapZone,
            isVertical = true,
            invert = options.invertTapZone,
            onPrev = {
                if (hasPrev && atTop()) {
                    onChapterBoundary(ComicChapterBoundary.PREVIOUS)
                } else {
                    scope.launch {
                        val target = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                        listState.animateScrollToItem(target)
                    }
                }
            },
            onMenu = onToggleMenu,
            onNext = {
                if (hasNext && atBottom()) {
                    onChapterBoundary(ComicChapterBoundary.NEXT)
                } else {
                    scope.launch {
                        val total = listState.layoutInfo.totalItemsCount
                        val target = (listState.firstVisibleItemIndex + 1).coerceAtMost((total - 1).coerceAtLeast(0))
                        listState.animateScrollToItem(target.coerceAtLeast(0))
                    }
                }
            },
            onDoubleTap = {},
        )
    }
}

/**
 * Tiled tap overlay for one viewer: lays [tapZoneRects] out in fractions of the viewport,
 * transposing along/across for vertical modes. DEFAULT reproduces the historical fixed thirds
 * (same order, tags and actions), so gestures and UI tests are unchanged; other modes only
 * take effect once the settings surface sources [ComicViewerOptions.tapZone].
 *
 * Zones keep the stable prev/menu/next test tags by action; modes without a menu rect
 * (LEFT_RIGHT) or with no rects at all (DISABLED) simply omit those tags.
 */
@Composable
private fun TapZoneOverlay(
    zone: ComicTapZoneMode,
    isVertical: Boolean,
    invert: Boolean,
    onPrev: () -> Unit,
    onMenu: () -> Unit,
    onNext: () -> Unit,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rects = remember(zone) { tapZoneRects(zone) }
    if (rects.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        rects.forEach { rect ->
            val action = if (!invert) {
                rect.action
            } else {
                when (rect.action) {
                    ComicTapAction.PREVIOUS -> ComicTapAction.NEXT
                    ComicTapAction.NEXT -> ComicTapAction.PREVIOUS
                    ComicTapAction.MENU, ComicTapAction.NONE -> rect.action
                }
            }
            val tag = when (action) {
                ComicTapAction.PREVIOUS -> ComicTapPrevTag
                ComicTapAction.MENU -> ComicTapMenuTag
                ComicTapAction.NEXT -> ComicTapNextTag
                ComicTapAction.NONE -> return@forEach
            }
            val onTap = when (action) {
                ComicTapAction.PREVIOUS -> onPrev
                ComicTapAction.MENU -> onMenu
                ComicTapAction.NEXT -> onNext
                ComicTapAction.NONE -> return@forEach
            }
            val x = (if (!isVertical) rect.alongFrom else rect.acrossFrom) * maxWidth.value
            val y = (if (!isVertical) rect.acrossFrom else rect.alongFrom) * maxHeight.value
            val w = (if (!isVertical) rect.alongTo else rect.acrossTo) * maxWidth.value - x
            val h = (if (!isVertical) rect.acrossTo else rect.alongTo) * maxHeight.value - y
            Box(
                Modifier
                    .offset(x = x.dp, y = y.dp)
                    .size(width = w.dp, height = h.dp),
            ) {
                TapZone(
                    tag = tag,
                    modifier = Modifier.fillMaxSize(),
                    onTap = onTap,
                    onDoubleTap = onDoubleTap,
                )
            }
        }
    }
}

/**
 * Navigation tap zone.
 *
 * The detector deliberately never consumes pointer input, so pager drags, list
 * scrolls and pinch gestures pass through to the content below (a stock clickable
 * overlay would swallow the gesture stream and break swiping). Taps are recognised
 * manually: a press that lifts without leaving touch slop. Double-tap uses a 400ms
 * window; single taps are confirmed after the window expires. The confirmation wait
 * runs on a real-time dispatcher (not the composition clock), so UI tests with a
 * virtual clock still observe the tap after their real-time settle sleeps; UI tests
 * additionally drive the zone through the semantics click action, which fires a
 * single tap immediately.
 */
@Composable
private fun TapZone(
    tag: String,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val tapScope = rememberCoroutineScope()
    Box(
        modifier
            .testTag(tag)
            .semantics {
                onClick {
                    onTap()
                    true
                }
            }
            .pointerInput(onTap, onDoubleTap) {
                val slop = viewConfiguration.touchSlop
                // Far-past sentinel: event uptimes can be tiny early in the process
                // lifetime, and 0 would misclassify the first tap as a double-tap.
                var lastTapUp = -10_000L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragged = false
                    var upTime = 0L
                    var finished = false
                    while (!finished) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            finished = event.changes.all { !it.pressed }
                        } else if (!change.pressed) {
                            upTime = change.uptimeMillis
                            finished = true
                        } else if (!dragged && (change.position - down.position).getDistance() > slop) {
                            dragged = true
                        }
                    }
                    if (dragged) return@awaitEachGesture
                    val now = upTime
                    if (now - lastTapUp < 400L) {
                        lastTapUp = -10_000L
                        onDoubleTap()
                    } else {
                        lastTapUp = now
                        // Real-time wait: the composition clock can be virtual in UI
                        // tests, where this confirmation must still fire.
                        tapScope.launch(Dispatchers.Default) {
                            delay(400L)
                            if (lastTapUp == now) {
                                lastTapUp = -10_000L
                                onTap()
                            }
                        }
                    }
                }
            },
    )
}

// ---------------------------------------------------------------------------
// Zoomable page (pinch + double-tap, FIT variants, retry)
// ---------------------------------------------------------------------------

@Composable
private fun ZoomableComicPage(
    index: Int,
    pageKey: String,
    state: ReaderPageState?,
    options: ComicViewerOptions,
    zoomPulse: Long,
    onRetry: () -> Unit,
    onWideDetected: () -> Unit,
    viewportFill: Boolean,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Pinch state is relative (1x = the fitted base); the base transform below
    // maps the (border-cropped) content rect into the viewport.
    var pinch by remember(pageKey) { mutableStateOf(1f) }
    var pan by remember(pageKey) { mutableStateOf(Offset.Zero) }
    var viewportPx by remember(pageKey) { mutableStateOf(IntSize.Zero) }
    // Fitted size at 1x: FIT_WIDTH long strips and FIT_HEIGHT wide pages overflow
    // the viewport, so they must pan before any zoom (Komikku's canPan* contract).
    var baseW by remember(pageKey) { mutableStateOf(0f) }
    var baseH by remember(pageKey) { mutableStateOf(0f) }
    fun clampPan(candidate: Offset, zoom: Float): Offset {
        if (viewportPx.width <= 0 || viewportPx.height <= 0) return Offset.Zero
        val overX = ((baseW * zoom) - viewportPx.width).coerceAtLeast(0f) / 2f
        val overY = ((baseH * zoom) - viewportPx.height).coerceAtLeast(0f) / 2f
        if (overX <= 0.5f && overY <= 0.5f) return Offset.Zero
        return Offset(candidate.x.coerceIn(-overX, overX), candidate.y.coerceIn(-overY, overY))
    }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (pinch * zoomChange).coerceIn(1f, 5f)
        pinch = next
        pan = clampPan(pan + panChange, next)
    }

    // Double-tap pulse from the tap zones toggles 1x / 2.5x.
    LaunchedEffect(zoomPulse) {
        if (zoomPulse == 0L) return@LaunchedEffect
        if (options.doubleTapZoom) {
            pinch = if (pinch > 1.2f) 1f else 2.5f
            if (pinch <= 1f) pan = Offset.Zero
        }
    }

    // Webtoon items size themselves from the content aspect; pager viewports
    // are bounded by the pager itself.
    when (state) {
        null, ReaderPageState.Loading -> Box(
            modifier = (if (viewportFill) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .testTag(comicPageTag(index)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        is ReaderPageState.Ready -> {
            val bitmap = remember(state.bytes) {
                runCatching { state.bytes.decodeToImageBitmap() }.getOrNull()
            }
            if (bitmap == null) {
                Box(
                    modifier = (if (viewportFill) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                        .testTag(comicPageTag(index)),
                    contentAlignment = Alignment.Center,
                ) {
                    PageError(pageKey = pageKey, onRetry = onRetry)
                }
            } else {
                val wide = bitmap.width > bitmap.height
                LaunchedEffect(bitmap.width, bitmap.height) {
                    if (wide) onWideDetected()
                }
                // Real border crop (Komikku parity): detect solid outer strips
                // and fit the remaining content rect with the user's scale type.
                // Borderless pages detect zero margins, so corner content stays.
                val crop = remember(bitmap, options.cropBorders) {
                    if (!options.cropBorders) {
                        ComicCrop.NONE
                    } else {
                        val pixels = runCatching { bitmap.toPixelMap().buffer }.getOrNull()
                        if (pixels == null) ComicCrop.NONE
                        else detectBorderCrop(pixels, bitmap.width, bitmap.height)
                    }
                }
                val contentW = (bitmap.width - crop.left - crop.right).coerceAtLeast(1)
                val contentH = (bitmap.height - crop.top - crop.bottom).coerceAtLeast(1)
                // Display geometry in px. The middle box is exactly the displayed
                // content rect and clips the cropped borders away; the bitmap is
                // drawn at its scaled size underneath, shifted so the content
                // aligns with the middle box. Pinch/pan apply on top.
                val geometry = remember(viewportPx, bitmap.width, bitmap.height, crop, options.scaleFit, viewportFill) {
                    val vw = viewportPx.width.toFloat()
                    val vh = viewportPx.height.toFloat()
                    val bw = bitmap.width.toFloat()
                    val bh = bitmap.height.toFloat()
                    val l = crop.left.toFloat()
                    val t = crop.top.toFloat()
                    val cw = contentW.toFloat()
                    val ch = contentH.toFloat()
                    if (viewportFill) {
                        contentFitTransform(vw, vh, bw, bh, crop, options.scaleFit)
                    } else {
                        // Vertical strip: width is the only constraint, content
                        // starts at the top (the aspect ratio sizes the item).
                        val s = if (options.scaleFit == ComicScaleFit.ORIGINAL) {
                            1f
                        } else if (cw > 0f && vw > 0f) {
                            vw / cw
                        } else {
                            1f
                        }
                        Triple(s, (vw - s * cw) / 2f - s * l, -s * t)
                    }
                }
                val (baseScale, baseTx, baseTy) = geometry
                // Draw the content rect directly from the bitmap: no nested
                // layout constraints, so large scaled images cannot be squeezed.
                val dstW = contentW * baseScale
                val dstH = contentH * baseScale
                val dstX = baseTx + baseScale * crop.left
                val dstY = baseTy + baseScale * crop.top
                androidx.compose.runtime.SideEffect {
                    baseW = dstW
                    baseH = dstH
                }
                Box(
                    modifier = (if (viewportFill) {
                        Modifier.fillMaxSize().clipToBounds()
                    } else {
                        Modifier.fillMaxWidth()
                            .aspectRatio(contentW.toFloat() / contentH.toFloat())
                    })
                        .onSizeChanged { viewportPx = it }
                        .testTag(comicPageTag(index)),
                ) {
                    Box(
                        // requiredSize: FIT_WIDTH on a long strip produces content
                        // taller than the spread; plain size() is coerced by the
                        // parent constraint, which squashed the bitmap.
                        modifier = Modifier
                            .requiredSize(with(density) { dstW.toDp() }, with(density) { dstH.toDp() })
                            .offset {
                                androidx.compose.ui.unit.IntOffset(dstX.roundToInt(), dstY.roundToInt())
                            }
                            .testTag(comicPageImageTag(index))
                            .graphicsLayer(
                                scaleX = pinch,
                                scaleY = pinch,
                                translationX = pan.x,
                                translationY = pan.y,
                            )
                            .drawBehind {
                                drawImage(
                                    image = bitmap,
                                    srcOffset = androidx.compose.ui.unit.IntOffset(crop.left, crop.top),
                                    srcSize = androidx.compose.ui.unit.IntSize(contentW, contentH),
                                    dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                                    dstSize = androidx.compose.ui.unit.IntSize(
                                        dstW.roundToInt().coerceAtLeast(1),
                                        dstH.roundToInt().coerceAtLeast(1),
                                    ),
                                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                                )
                            }
                            .transformable(transformState),
                    )
                }
            }
        }

        is ReaderPageState.Failed -> Box(
            modifier = (if (viewportFill) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .testTag(comicPageTag(index)),
            contentAlignment = Alignment.Center,
        ) {
            PageError(pageKey = pageKey, onRetry = onRetry)
        }
    }
}

@Composable
private fun PageError(
    pageKey: String,
    onRetry: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.reader_page_failed),
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag(comicPageRetryTag(pageKey)),
        ) {
            Text(stringResource(Res.string.reader_retry))
        }
    }
}
