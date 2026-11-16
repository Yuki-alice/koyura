package app.koyura.features.reader.comic.viewer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.koyura.domain.reader.ChapterContent
import app.koyura.features.reader.comic.ReaderPageState
import app.koyura.features.reader.comic.ReaderSession
import app.koyura.features.reader.comic.decodeToImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Stable UI test hooks, identical values to the production reader for regression reuse. */
const val ComicViewerRootTag = "reader-root"
const val ComicTapPrevTag = "reader-tap-prev"
const val ComicTapNextTag = "reader-tap-next"
const val ComicTapMenuTag = "reader-tap-menu"

fun comicPageTag(index: Int) = "reader-page-$index"
fun comicPageImageTag(index: Int) = "reader-page-image-$index"
fun comicPageRetryTag(pageKey: String) = "reader-page-retry-$pageKey"

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
                Text("No pages")
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

        fun spreadMaster(displayIndex: Int): Int =
            spreads.getOrNull(displayIndex)?.masterPageIndex ?: 0

        fun goVisual(delta: Int) {
            val target = pagerState.currentPage + delta
            if (target < 0) {
                if (hasPrev) onChapterBoundary(ComicChapterBoundary.PREVIOUS)
                return
            }
            if (target >= spreads.size) {
                if (hasNext) onChapterBoundary(ComicChapterBoundary.NEXT)
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
            val display = if (isRtl && !isVertical) logicalSpreads.size - 1 - logical else logical
            consumedRequest = command.requestId
            pagerState.scrollToPage(display.coerceIn(0, spreads.size - 1))
        }

        // Re-anchor on layout/width/mode change: keep the same original page.
        LaunchedEffect(spreads) {
            val current = session.currentPage
            if (current !in content.pageKeys.indices) return@LaunchedEffect
            val logical = spreadIndexForPage(logicalSpreads, current)
            val display = if (isRtl && !isVertical) logicalSpreads.size - 1 - logical else logical
            if (display != pagerState.currentPage) pagerState.scrollToPage(display)
        }

        // Displayed + settled reporting (master page in logical reading order).
        LaunchedEffect(pagerState, spreads) {
            snapshotFlow { pagerState.currentPage }.collect { session.markDisplayed(spreadMaster(it)) }
        }
        LaunchedEffect(pagerState, spreads) {
            snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect {
                session.savePage(spreadMaster(it))
            }
        }
        // Demand loading: current spread plus neighbours. Only pages never attempted
        // are requested here; failed pages wait for an explicit retry (Komikku shows
        // the error row until the user retries), so a failure stays observable.
        LaunchedEffect(pagerState, content.pageKeys) {
            snapshotFlow { pagerState.currentPage }.collect { display ->
                val logical = if (isRtl && !isVertical) spreads.size - 1 - display else display
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

            // Tap zones above the pager (below chrome): edges turn, centre toggles menu.
            // Double-tap on a zone zooms instead of turning, so single taps never fire twice.
            if (isVertical) {
                Column(Modifier.fillMaxSize()) {
                    TapZone(
                        tag = ComicTapPrevTag,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onTap = { goVisual(-1) },
                        onDoubleTap = { zoomPulse++ },
                    )
                    TapZone(
                        tag = ComicTapMenuTag,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onTap = onToggleMenu,
                        onDoubleTap = { zoomPulse++ },
                    )
                    TapZone(
                        tag = ComicTapNextTag,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onTap = { goVisual(1) },
                        onDoubleTap = { zoomPulse++ },
                    )
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    TapZone(
                        tag = ComicTapPrevTag,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onTap = { goVisual(-1) },
                        onDoubleTap = { zoomPulse++ },
                    )
                    TapZone(
                        tag = ComicTapMenuTag,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onTap = onToggleMenu,
                        onDoubleTap = { zoomPulse++ },
                    )
                    TapZone(
                        tag = ComicTapNextTag,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onTap = { goVisual(1) },
                        onDoubleTap = { zoomPulse++ },
                    )
                }
            }
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
) {
    val scope = rememberCoroutineScope()
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
            Text("No pages")
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
        // boundary instead (the host owns the actual chapter switch).
        Row(Modifier.fillMaxSize()) {
            TapZone(
                tag = ComicTapPrevTag,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onTap = {
                    if (hasPrev && atTop()) {
                        onChapterBoundary(ComicChapterBoundary.PREVIOUS)
                    } else {
                        scope.launch {
                            val target = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                            listState.animateScrollToItem(target)
                        }
                    }
                },
                onDoubleTap = {},
            )
            TapZone(
                tag = ComicTapMenuTag,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onTap = onToggleMenu,
                onDoubleTap = {},
            )
            TapZone(
                tag = ComicTapNextTag,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onTap = {
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
    var scale by remember(pageKey) { mutableStateOf(1f) }
    var offset by remember(pageKey) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (scale * zoomChange).coerceIn(1f, 5f)
        scale = next
        offset = if (next <= 1f) Offset.Zero else offset + panChange
    }

    // Double-tap pulse from the tap zones toggles 1x / 2.5x.
    LaunchedEffect(zoomPulse) {
        if (zoomPulse == 0L) return@LaunchedEffect
        if (options.doubleTapZoom) {
            scale = if (scale > 1.2f) 1f else 2.5f
            if (scale <= 1f) offset = Offset.Zero
        }
    }

    Box(
        modifier = (if (viewportFill) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .testTag(comicPageTag(index)),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            null, ReaderPageState.Loading -> CircularProgressIndicator()
            is ReaderPageState.Ready -> {
                val bitmap = remember(state.bytes) {
                    runCatching { state.bytes.decodeToImageBitmap() }.getOrNull()
                }
                if (bitmap == null) {
                    PageError(pageKey = pageKey, onRetry = onRetry)
                } else {
                    val wide = bitmap.width > bitmap.height
                    LaunchedEffect(bitmap.width, bitmap.height) {
                        if (wide) onWideDetected()
                    }
                    val contentScale = when {
                        options.cropBorders -> ContentScale.Crop
                        else -> when (options.scaleFit) {
                            ComicScaleFit.FIT -> ContentScale.Fit
                            ComicScaleFit.FIT_WIDTH -> ContentScale.FillWidth
                            ComicScaleFit.FIT_HEIGHT -> ContentScale.FillHeight
                            ComicScaleFit.ORIGINAL -> ContentScale.None
                        }
                    }
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = "page image",
                        contentScale = contentScale,
                        modifier = (if (viewportFill) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                            .testTag(comicPageImageTag(index))
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y,
                            )
                            .transformable(transformState),
                    )
                }
            }

            is ReaderPageState.Failed -> PageError(pageKey = pageKey, onRetry = onRetry)
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
            text = "Couldn't load page",
            color = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag(comicPageRetryTag(pageKey)),
        ) {
            Text("Retry")
        }
    }
}
