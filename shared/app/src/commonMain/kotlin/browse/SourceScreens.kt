package app.koyura.app.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import app.koyura.app.resources.browse_clear_search
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.DecorativeActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.SearchField
import app.koyura.features.library.ReadButtonEnd
import app.koyura.features.library.ReadButtonStart

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koyura.app.LocalAppearance
import app.koyura.app.LocalReaderService
import app.koyura.app.resources.Res
import app.koyura.app.resources.browse_retry
import app.koyura.app.resources.browse_show_less
import app.koyura.app.resources.browse_show_more
import app.koyura.app.resources.reading_continue
import app.koyura.app.resources.reading_start
import app.koyura.app.resources.source_detail_back
import app.koyura.app.resources.source_detail_chapters
import app.koyura.app.resources.source_detail_chapters_selected
import app.koyura.app.resources.source_detail_clear_selection
import app.koyura.app.resources.source_detail_chapter_filter
import app.koyura.app.resources.source_detail_filter_all
import app.koyura.app.resources.source_detail_filter_downloaded
import app.koyura.app.resources.source_detail_filter_not_downloaded
import app.koyura.app.resources.source_detail_filter_read
import app.koyura.app.resources.source_detail_filter_unread
import app.koyura.app.resources.source_detail_download
import app.koyura.app.resources.source_detail_empty
import app.koyura.app.resources.source_detail_error
import app.koyura.app.resources.source_detail_library_add
import app.koyura.app.resources.source_detail_library_added
import app.koyura.app.resources.source_detail_link
import app.koyura.app.resources.source_detail_mark_read
import app.koyura.app.resources.source_detail_mark_unread
import app.koyura.app.resources.source_detail_no_description
import app.koyura.app.resources.source_detail_progress
import app.koyura.app.resources.source_detail_read
import app.koyura.app.resources.source_detail_suggestions
import app.koyura.app.resources.source_detail_suggestions_empty
import app.koyura.app.resources.source_detail_tags
import app.koyura.app.resources.source_detail_tracking
import app.koyura.app.resources.source_detail_updated
import app.koyura.app.resources.source_detail_webview
import app.koyura.app.resources.source_search_button
import app.koyura.app.resources.source_search_empty
import app.koyura.app.resources.source_search_error
import app.koyura.app.resources.source_search_hint
import app.koyura.app.resources.source_search_no_results
import app.koyura.app.resources.source_search_results
import app.koyura.designsystem.Cover
import app.koyura.designsystem.CoverArtwork
import app.koyura.designsystem.DetailBackdrop
import app.koyura.designsystem.LocalCoverDecoder
import app.koyura.designsystem.LocalCoverImageLoader
import app.koyura.designsystem.components.AppButton
import app.koyura.designsystem.components.AppCircularProgress
import app.koyura.designsystem.components.AppDivider
import app.koyura.designsystem.components.AppExtendedFab
import app.koyura.designsystem.components.AppSurface
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.ChapterDownloadIndicator
import app.koyura.designsystem.components.ChapterDownloadState
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.ProvideUiContentColor
import app.koyura.designsystem.components.ModalReaderSheet
import app.koyura.designsystem.components.ReaderTopAppBar
import app.koyura.designsystem.components.SettingsItem
import app.koyura.designsystem.components.TagChip
import app.koyura.designsystem.components.ReaderTopAppBarHeight
import app.koyura.designsystem.components.UiTheme
import app.koyura.designsystem.rememberCoverBitmap
import app.koyura.designsystem.theme.ThemeMode
import app.koyura.designsystem.theme.VisualTheme
import app.koyura.designsystem.theme.extractCoverSeedColor
import app.koyura.domain.reader.ReaderService
import app.koyura.features.library.LibraryEntryKey
import app.koyura.features.library.LibraryKind
import app.koyura.features.library.LibrarySession
import app.koyura.features.library.NewLibraryEntry
import app.koyura.features.library.rememberLibrarySession
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

internal const val SourceSearchTestTag = "source-search"
internal const val SourceSearchQueryTag = "source-search-query"
internal const val SourceSearchSubmitTag = "source-search-submit"
internal const val SourceSearchResultsTag = "source-search-results"

internal fun sourceResultTag(workId: String) = "source-result-$workId"

/** Search page for one installed source, backed by its `search.load` callback. */
@Composable
fun SourceSearchScreen(
    session: BrowseSession,
    sourceKey: String,
    sourceName: String,
    onOpenWork: (workId: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Present only when the host route can pop (wired by the shell later); hidden otherwise. */
    onBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    // Query, results and paging live in saveable state: child routes can be disposed and
    // recreated when the shell switches between the bottom bar and the navigation rail, and the
    // search must survive that along with tab switches and detail round trips.
    var query by rememberSaveable(sourceKey) { mutableStateOf("") }
    var submittedQuery by rememberSaveable(sourceKey) { mutableStateOf<String?>(null) }
    var resultsJson by rememberSaveable(sourceKey) { mutableStateOf("") }
    var maxPage by rememberSaveable(sourceKey) { mutableStateOf(1) }
    var loading by remember(sourceKey) { mutableStateOf(false) }
    var error by remember(sourceKey) { mutableStateOf<String?>(null) }
    val results: List<WorkSummary> = remember(resultsJson) {
        if (resultsJson.isEmpty()) {
            emptyList()
        } else {
            try {
                kotlinx.serialization.json.Json.decodeFromString(resultsJson)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun runSearch() {
        val needle = query.trim()
        if (needle.isEmpty() || loading) return
        loading = true
        error = null
        scope.launch {
            try {
                val page = session.searchWorks(sourceKey, needle)
                resultsJson = kotlinx.serialization.json.Json.encodeToString(page.works)
                maxPage = page.maxPage
                submittedQuery = needle
            } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Search failed"
            } finally {
                loading = false
            }
        }
    }

    // Returning from a detail page re-runs the last query: the entry keeps its position in the
    // stack, so only transient list state needs rebuilding, never the route.
    LaunchedEffect(submittedQuery) {
        if (submittedQuery != null && results.isEmpty() && error == null && !loading) runSearch()
    }

    CompositionLocalProvider(
        LocalCoverImageLoader provides session.coverLoader(),
        LocalCoverDecoder provides AppCoverDecoder,
    ) {
        Column(modifier = modifier.fillMaxSize().testTag(SourceSearchTestTag)) {
            ReaderTopAppBar(
                title = { AppText(sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        ActionIconButton(
                            icon = ActionIcon.Back,
                            label = stringResource(Res.string.source_detail_back),
                            onClick = onBack,
                            modifier = Modifier.testTag("source-search-back"),
                        )
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(Res.string.source_search_hint),
                    clearLabel = stringResource(Res.string.browse_clear_search),
                    modifier = Modifier.weight(1f).testTag(SourceSearchQueryTag),
                )
                ActionIconButton(
                    icon = ActionIcon.Search,
                    label = stringResource(Res.string.source_search_button),
                    onClick = { runSearch() },
                    enabled = !loading,
                    modifier = Modifier.testTag(SourceSearchSubmitTag),
                )
            }
            when {
                loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(DesignTokens.Spacing.Large),
                        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
                    ) { AppCircularProgress() }
                }
                error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(DesignTokens.Spacing.Large),
                        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Regular),
                    ) {
                        AppText(
                            text = stringResource(Res.string.source_search_error, error.orEmpty()),
                            color = UiTheme.colorScheme.error,
                        )
                        AppButton(onClick = { runSearch() }) {
                            AppText(stringResource(Res.string.source_search_button))
                        }
                    }
                }
                submittedQuery == null -> {
                    AppText(
                        text = stringResource(Res.string.source_search_empty),
                        style = UiTheme.typography.bodyMedium,
                        color = UiTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(DesignTokens.Spacing.Large),
                    )
                }
                results.isEmpty() -> {
                    AppText(
                        text = stringResource(Res.string.source_search_no_results),
                        style = UiTheme.typography.bodyMedium,
                        color = UiTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(DesignTokens.Spacing.Large),
                    )
                }
                else -> {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag(SourceSearchResultsTag),
                    ) {
                        item(key = "search-header") {
                            app.koyura.designsystem.ListGroupHeader(
                                text = stringResource(Res.string.source_search_results, results.size, maxPage),
                            )
                        }
                        items(results, key = { it.id }) { work ->
                            // Komikku BrowseSourceList row: shared cover (2:3, 48x72) + title/subtitle.
                            // Covers load only for composed rows, so long lists never prefetch fully.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenWork(work.id, work.title) }
                                    .testTag(sourceResultTag(work.id))
                                    .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Medium),
                            ) {
                                Cover(
                                    sourceKey = sourceKey,
                                    coverUrl = work.cover,
                                    label = work.title,
                                    modifier = Modifier.size(width = DesignTokens.Sizes.SearchCoverWidth, height = DesignTokens.Sizes.SearchCoverHeight),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Tight),
                                ) {
                                    AppText(
                                        work.title,
                                        style = UiTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (work.subtitle.isNotEmpty()) {
                                        AppText(
                                            work.subtitle,
                                            style = UiTheme.typography.bodySmall,
                                            color = UiTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = DesignTokens.Alpha.Secondary,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (work.tags.isNotEmpty()) {
                                        AppText(
                                            work.tags.joinToString(" · "),
                                            style = UiTheme.typography.bodySmall,
                                            color = UiTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = DesignTokens.Alpha.Secondary,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            AppDivider()
                        }
                    }
                }
            }
        }
    }
}

internal const val SourceDetailTestTag = "source-detail"
internal const val SourceDetailToolbarTag = "source-detail-toolbar"
internal const val SourceDetailBackTag = "source-detail-back"

/** Width at or above which the detail page uses the Komikku two-pane layout. */
internal val DetailWideWidthThreshold = DesignTokens.Breakpoints.TabletMinSmallestWidthDp.dp

/** Work detail page backed by the source's `comic.loadInfo` callback. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SourceDetailScreen(
    session: BrowseSession,
    sourceKey: String,
    workId: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    readerService: ReaderService? = LocalReaderService.current,
    librarySession: LibrarySession? = null,
    onOpenChapter: (chapterId: String?, chapterIds: List<String>, chapterTitles: List<String>) -> Unit = { _, _, _ -> },
) {
    val scope = rememberCoroutineScope()
    var detail by remember(sourceKey, workId) { mutableStateOf<WorkDetail?>(null) }
    var loading by remember(sourceKey, workId) { mutableStateOf(true) }
    var error by remember(sourceKey, workId) { mutableStateOf<String?>(null) }
    var progress by remember(sourceKey, workId) { mutableStateOf<app.koyura.domain.reader.ReaderProgress?>(null) }
    var reloadTick by remember(sourceKey, workId) { mutableStateOf(0) }
    // 详情与书架同一收藏状态：有装配会话时读已提交快照，无装配时保持页内视觉态。
    val library = librarySession ?: rememberLibrarySession()
    val libraryKey = remember(sourceKey, workId) { LibraryEntryKey(sourceKey, LibraryKind.COMIC, workId) }
    val inLibrary = library.isFavorite(libraryKey)
    val sourceName = remember(session.state.sources, sourceKey) {
        session.state.sources.firstOrNull { it.id == sourceKey }?.name.orEmpty()
    }
    var selectedIds by remember(sourceKey, workId) { mutableStateOf(setOf<String>()) }
    var readStates by remember(sourceKey, workId) { mutableStateOf(mapOf<String, Boolean>()) }
    // FAB 吸附侧：优先书架偏好（Komikku `reading_button_position`），拖动后本地覆盖即时生效。
    var fabDragAtStart by rememberSaveable(sourceKey, workId) { mutableStateOf<Boolean?>(null) }
    val fabAtStart = fabDragAtStart ?: library.snapshot.prefs.readButtonAtStart

    LaunchedEffect(sourceKey, workId, reloadTick) {
        loading = true
        error = null
        try {
            detail = session.loadWorkDetail(sourceKey, workId)
            progress = readerService?.readProgress(sourceKey, workId)
            // 真实已读：书架目录同步后读同一状态；未收藏/无目录即空映射，回退到进度章标读。
            readStates = try {
                library.chapterReadStates(libraryKey)
            } catch (_: Exception) {
                emptyMap()
            }
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load details"
        } finally {
            loading = false
        }
    }

    // Komikku MangaScreen: toolbar with a visible back entry, blurred header backdrop, cover /
    // title / meta / description / four-key action row, tags chips, chapter-count header, then
    // MangaChapterListItem-style rows. Two-pane on wide windows, single column on narrow ones.
    Column(modifier = modifier.fillMaxSize().testTag(SourceDetailTestTag)) {
        when {
            loading -> androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AppCircularProgress()
            }
            error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(DesignTokens.Spacing.Large),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Regular),
                ) {
                    AppText(
                        text = stringResource(Res.string.source_detail_error, error.orEmpty()),
                        color = UiTheme.colorScheme.error,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Regular)) {
                        AppButton(onClick = onBack) {
                            AppText(stringResource(Res.string.source_detail_back))
                        }
                        AppButton(
                            onClick = { reloadTick++ },
                        ) {
                            AppText(stringResource(Res.string.browse_retry))
                        }
                    }
                }
            }
            detail != null -> {
                val loaded = detail!!
                val chapterIds = remember(loaded) { loaded.chapters.map { it.id } }
                val chapterTitles = remember(loaded) { loaded.chapters.map { it.title } }
                val canContinue = progress != null &&
                    (progress!!.chapterId == null || progress!!.chapterId in chapterIds)
                val coverLoader = remember(session) { session.coverLoader() }
                CompositionLocalProvider(
                    LocalCoverImageLoader provides coverLoader,
                    LocalCoverDecoder provides AppCoverDecoder,
                ) {
                    val artwork = rememberCoverBitmap(sourceKey, loaded.cover)
                    DetailCoverTheme(artwork) {
                        Column(Modifier.fillMaxSize().background(UiTheme.colorScheme.surface)) {
                            library.actionError?.let { libraryError ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.ExtraSmall),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
                                ) {
                                    // 点按错误行即清除（与书架页的清除按钮同一语义，避免引入新文案）。
                                    AppText(
                                        text = libraryError,
                                        color = UiTheme.colorScheme.error,
                                        style = UiTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                            .testTag("detail-library-error")
                                            .clickable { library.clearActionError() },
                                    )
                                }
                            }
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val infoScroll = rememberScrollState()
                                val chaptersScroll = rememberScrollState()
                                val info: @Composable (Boolean) -> Unit = { wide ->
                                    DetailInfoColumn(
                                        detail = loaded,
                                        artwork = artwork,
                                        fallbackTitle = fallbackTitle,
                                        sourceName = sourceName.ifBlank { sourceKey },
                                        wide = wide,
                                        inLibrary = inLibrary,
                                        onToggleLibrary = {
                                            scope.launch {
                                                library.setFavorite(
                                                    NewLibraryEntry(
                                                        sourceKey = sourceKey,
                                                        workId = workId,
                                                        title = loaded.title.ifEmpty { fallbackTitle },
                                                        coverUrl = loaded.cover,
                                                        sourceName = sourceName,
                                                        totalChapters = loaded.chapters.size,
                                                    ),
                                                    !inLibrary,
                                                )
                                            }
                                        },
                                        canContinue = canContinue,
                                        onContinue = {
                                            progress?.let { saved ->
                                                val target = saved.chapterId?.takeIf { it in chapterIds }
                                                    ?: chapterIds.firstOrNull()
                                                onOpenChapter(target, chapterIds, chapterTitles)
                                            }
                                        },
                                        progress = progress,
                                    )
                                }
                                val chapters: @Composable () -> Unit = {
                                    DetailChapterList(
                                        chapters = loaded.chapters,
                                        enabled = readerService != null,
                                        selectedIds = selectedIds,
                                        onToggleSelect = { id ->
                                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                                        },
                                        onClearSelection = { selectedIds = emptySet() },
                                        readStates = readStates,
                                        fallbackReadChapterId = progress?.chapterId,
                                        canMarkRead = inLibrary,
                                        onMarkRead = { ids, read ->
                                            scope.launch {
                                                if (library.markDetailChapters(libraryKey, ids, read)) {
                                                    readStates = library.chapterReadStates(libraryKey)
                                                    selectedIds = emptySet()
                                                }
                                            }
                                        },
                                        onOpen = { chapter -> onOpenChapter(chapter.id, chapterIds, chapterTitles) },
                                    )
                                }
                                val wide = maxWidth >= DetailWideWidthThreshold
                                if (wide) {
                                    val masterWidth = minOf(maxWidth / 2, DesignTokens.Breakpoints.TwoPaneFirstMax)
                                    Row(Modifier.fillMaxSize()) {
                                        Column(
                                            Modifier.width(masterWidth).fillMaxHeight()
                                                .testTag("detail-master-pane").verticalScroll(infoScroll),
                                        ) {
                                            Spacer(Modifier.height(ReaderTopAppBarHeight))
                                            info(true)
                                            DetailSuggestions()
                                        }
                                        Column(
                                            Modifier.weight(1f).fillMaxHeight().verticalScroll(chaptersScroll),
                                        ) {
                                            Spacer(Modifier.height(ReaderTopAppBarHeight))
                                            chapters()
                                        }
                                    }
                                } else {
                                    Column(Modifier.fillMaxSize().testTag("detail-scroll").verticalScroll(infoScroll)) {
                                        Spacer(Modifier.height(ReaderTopAppBarHeight))
                                        info(false)
                                        chapters()
                                        DetailSuggestions()
                                    }
                                }
                                // 继续阅读悬浮钮（Komikku MangaScreen.kt:532 FAB 语义）：
                                // 有未读章节 + 非多选 + 阅读器已接线时出现；点击直达继续/首章，
                                // 横向拖动松手按中心过半左右吸边并落盘（书架偏好）。
                                val unreadExists = loaded.chapters.any { chapter ->
                                    !(readStates[chapter.id] ?: (progress?.chapterId == chapter.id))
                                }
                                // 无分章作品同样可开始阅读（bookless 源），与原内联钮行为一致。
                                val canStart = loaded.chapters.isEmpty() || unreadExists
                                if (readerService != null && selectedIds.isEmpty() && canStart) {
                                    val savedId = progress?.chapterId?.takeIf { it in chapterIds }
                                    val targetId = savedId ?: chapterIds.firstOrNull()
                                    DetailReadFab(
                                        atStart = fabAtStart,
                                        label = if (savedId != null) {
                                            stringResource(
                                                Res.string.reading_continue,
                                                (progress?.pageIndex ?: 0) + 1,
                                            )
                                        } else {
                                            stringResource(Res.string.reading_start)
                                        },
                                        layoutWidthPx = with(LocalDensity.current) { maxWidth.toPx() },
                                        // 无分章作品以 null 章 id 进入阅读器（既有契约）。
                                        onOpen = {
                                            onOpenChapter(targetId, chapterIds, chapterTitles)
                                        },
                                        onSnap = { start ->
                                            fabDragAtStart = start
                                            scope.launch {
                                                library.updatePrefs {
                                                    it.copy(
                                                        readButtonPosition = if (start) {
                                                            ReadButtonStart
                                                        } else {
                                                            ReadButtonEnd
                                                        },
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }

                                val appBarHeightPx = with(LocalDensity.current) { ReaderTopAppBarHeight.toPx() }
                                // 双 alpha（白皮书 04 §4.6 / 09 坑 6）：
                                // 背景在「开始滚动」时就到位，标题要等首项（AppBar 高度）完全滚出。
                                val scrollValue = maxOf(infoScroll.value, if (wide) chaptersScroll.value else 0)
                                val backgroundFraction = (scrollValue / (appBarHeightPx * 0.25f)).coerceIn(0f, 1f)
                                val titleFraction = (scrollValue / appBarHeightPx).coerceIn(0f, 1f)
                                ReaderTopAppBar(
                                    title = loaded.title.ifEmpty { fallbackTitle },
                                    scrollFraction = backgroundFraction,
                                    titleAlpha = titleFraction,
                                    navigationIcon = {
                                        ActionIconButton(
                                            icon = ActionIcon.Back,
                                            label = stringResource(Res.string.source_detail_back),
                                            onClick = onBack,
                                            modifier = Modifier.testTag(SourceDetailBackTag),
                                        )
                                    },
                                    modifier = Modifier.testTag(SourceDetailToolbarTag),
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                AppText(
                    text = stringResource(Res.string.source_detail_empty),
                    modifier = Modifier.padding(DesignTokens.Spacing.Large),
                )
            }
        }
    }
}

@Composable
private fun DetailCoverTheme(artwork: ImageBitmap?, content: @Composable () -> Unit) {
    val appearance = LocalAppearance.current
    val enableCoverSeed = appearance == null || (appearance.coverBased && appearance.customSeedColor == null)
    val state = androidx.compose.runtime.key(artwork, enableCoverSeed) {
        produceState<Color?>(initialValue = null) {
            if (artwork != null && enableCoverSeed) {
                value = withContext(Dispatchers.Default) {
                    runCatching { extractCoverSeedColor(artwork) }.getOrNull()
                }
            }
        }
    }
    val scene = remember {
        movableContentOf<@Composable () -> Unit> { body ->
            ProvideUiContentColor(UiTheme.colorScheme.onSurface, body)
        }
    }
    val seed = state.value
    val mode = if (UiTheme.colorScheme.surface.luminance() < 0.5f) ThemeMode.DARK else ThemeMode.LIGHT
    if (seed == null) {
        scene(content)
    } else {
        VisualTheme(
            seedColor = seed,
            mode = mode,
            amoled = appearance?.amoled == true,
            paletteStyle = appearance?.coverBasedStyle ?: com.materialkolor.PaletteStyle.Vibrant,
        ) { scene(content) }
    }
}

@Composable
private fun DetailSuggestions() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(DesignTokens.Spacing.Medium)
            .testTag("detail-suggestions"),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
    ) {
        AppText(stringResource(Res.string.source_detail_suggestions), style = UiTheme.typography.titleMedium)
        AppText(
            stringResource(Res.string.source_detail_suggestions_empty),
            style = UiTheme.typography.bodyMedium,
            color = UiTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Komikku MangaInfoBox: blurred cover backdrop (surface fallback, never palette-crashes),
 * 96x144 shared cover, title, source/author line, expandable description, the four-key action
 * row (在书架中/追踪天数/进度记录/WebView), start/continue buttons, tag chips and metadata.
 */
@Composable
private fun DetailHeading(title: String, subtitle: String, sourceName: String, centered: Boolean) {
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
    ) {
        AppText(
            text = title,
            style = UiTheme.typography.titleLarge,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.testTag("source-detail-heading"),
        )
        if (subtitle.isNotBlank()) {
            AppText(
                text = subtitle,
                style = UiTheme.typography.bodyMedium,
                color = UiTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AppText(
            text = sourceName,
            style = UiTheme.typography.bodySmall,
            color = UiTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailAction(
    icon: ActionIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    highlightAlways: Boolean = false,
) {
    AppTextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = DesignTokens.Sizes.MinimumTouchTarget),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = DesignTokens.Spacing.Tight, vertical = DesignTokens.Spacing.Small),
        contentColor = if (active) UiTheme.colorScheme.primary else UiTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
        ) {
            DecorativeActionIcon(icon, modifier = Modifier.size(DesignTokens.Sizes.DetailActionIcon))
            AppText(
                text = label,
                style = UiTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailInfoColumn(
    detail: WorkDetail,
    artwork: ImageBitmap?,
    fallbackTitle: String,
    sourceName: String,
    wide: Boolean,
    inLibrary: Boolean,
    onToggleLibrary: () -> Unit,
    canContinue: Boolean,
    onContinue: () -> Unit,
    progress: app.koyura.domain.reader.ReaderProgress?,
) {
    var expanded by remember { mutableStateOf(false) }
    val title = detail.title.ifEmpty { fallbackTitle }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 平板居中封面占宽 65%（白皮书 07 §7.4），240dp 上限保持手机/折叠屏可读。
        val coverWidth = (maxWidth * 0.65f).coerceIn(DesignTokens.Breakpoints.DetailCoverMinWidth, DesignTokens.Breakpoints.DetailCoverMaxWidth)
        DetailBackdrop(cover = artwork, modifier = Modifier.matchParentSize())
        if (wide) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Medium),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Regular),
            ) {
                CoverArtwork(
                    bitmap = artwork,
                    label = title,
                    modifier = Modifier.size(coverWidth, coverWidth * 1.5f),
                )
                DetailHeading(title, detail.subtitle, sourceName, centered = true)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Regular),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Medium),
            ) {
                CoverArtwork(
                    bitmap = artwork,
                    label = title,
                    modifier = Modifier.width(DesignTokens.Sizes.DetailCoverMaxWidthPhone)
                        .height(DesignTokens.Sizes.DetailCoverHeightPhone),
                )
                Column(Modifier.weight(1f)) {
                    DetailHeading(title, detail.subtitle, sourceName, centered = false)
                }
            }
        }
    }
    // Four-key action row: library toggle commits to the shared shelf (same snapshot as the
    // Library tab); save failures keep the committed snapshot and surface a page error.
    // Tracking and WebView stay disabled (tracker/sync/WebView are later tracks, see notes).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.Small),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        DetailAction(
            icon = if (inLibrary) ActionIcon.Favorite else ActionIcon.FavoriteBorder,
            label = stringResource(if (inLibrary) Res.string.source_detail_library_added else Res.string.source_detail_library_add),
            onClick = onToggleLibrary,
            modifier = Modifier.weight(1f).testTag("detail-favorite"),
            active = inLibrary,
        )
        DetailAction(
            icon = ActionIcon.Hourglass,
            label = stringResource(Res.string.source_detail_tracking),
            onClick = {},
            enabled = false,
            modifier = Modifier.weight(1f).testTag("detail-tracking"),
        )
        DetailAction(
            icon = ActionIcon.Sync,
            label = stringResource(Res.string.source_detail_progress),
            onClick = onContinue,
            enabled = canContinue,
            highlightAlways = true,
            modifier = Modifier.weight(1f).testTag("detail-progress"),
        )
        DetailAction(
            icon = ActionIcon.Public,
            label = stringResource(Res.string.source_detail_webview),
            onClick = {},
            enabled = false,
            highlightAlways = true,
            modifier = Modifier.weight(1f).testTag("detail-webview"),
        )
    }
    val body = detail.description.ifEmpty { stringResource(Res.string.source_detail_no_description) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.ExtraSmall),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
    ) {
        Box(Modifier.fillMaxWidth()) {
            AppText(
                text = body,
                style = UiTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (!expanded) {
                // 折叠时底部 24dp 渐隐到页面底色，暗示还有内容（白皮书 07 §7.11）。
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(DesignTokens.Spacing.Large)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, UiTheme.colorScheme.surface),
                            ),
                            RoundedCornerShape(bottomStart = 0.dp, bottomEnd = 0.dp),
                        ),
                )
            }
        }
        if (detail.description.isNotEmpty()) {
            Box(Modifier.align(Alignment.CenterHorizontally)) {
                ActionIconButton(
                    icon = if (expanded) ActionIcon.Collapse else ActionIcon.Expand,
                    label = stringResource(if (expanded) Res.string.browse_show_less else Res.string.browse_show_more),
                    onClick = { expanded = !expanded },
                )
            }
        }
    }
    if (detail.tags.isNotEmpty()) {
        AppText(
            text = stringResource(Res.string.source_detail_tags),
            style = UiTheme.typography.labelLarge,
            color = UiTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.ExtraSmall),
        )
        val multiGroup = detail.tags.size > 1
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.Spacing.Medium)
                .testTag("detail-tags"),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
        ) {
            detail.tags.forEach { (group, values) ->
                values.forEachIndexed { index, value ->
                    val chipText = if (multiGroup) "$group · $value" else value
                    // 类型标签 = M3 SuggestionChip（small 圆角 + labelLarge + 描边容器），
                    // Code: Chip.kt:50（shape = shapes.small）经 NamespaceTags 用于详情标签区。
                    TagChip(
                        label = chipText,
                        onClick = {},
                        modifier = Modifier.testTag("detail-chip-$group-$index"),
                    )
                }
            }
        }
    }
    if (detail.updateTime.isNotEmpty() || detail.url.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.ExtraSmall)
                .testTag("detail-meta"),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Tight),
        ) {
            if (detail.updateTime.isNotEmpty()) {
                AppText(
                    text = stringResource(Res.string.source_detail_updated, detail.updateTime),
                    style = UiTheme.typography.bodySmall,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (detail.url.isNotEmpty()) {
                AppText(
                    text = "${stringResource(Res.string.source_detail_link)}: ${detail.url}",
                    style = UiTheme.typography.bodySmall,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Chapter-count header, optional multi-select bar, then one row per chapter.
 *
 * Long-press starts selection; while selecting, taps toggle. Batch mark-read writes the
 * production chapter table (`library_chapter.is_read`), download stays disabled
 * (download track is a later round). The row test tag is unchanged.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailChapterList(
    chapters: List<WorkChapter>,
    enabled: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    readStates: Map<String, Boolean>,
    fallbackReadChapterId: String?,
    canMarkRead: Boolean,
    onMarkRead: (Set<String>, Boolean) -> Unit,
    onOpen: (WorkChapter) -> Unit,
) {
    var filter by remember { mutableStateOf(ChapterFilter.ALL) }
    var showFilter by remember { mutableStateOf(false) }
    val visibleChapters = chapters.filter { chapter ->
        val read = readStates[chapter.id] ?: (fallbackReadChapterId == chapter.id)
        when (filter) {
            ChapterFilter.ALL -> true
            ChapterFilter.UNREAD -> !read
            ChapterFilter.READ -> read
            // 下载轨未接通前，「已下载」「未下载」都只按未下载处理（如实展示，不伪造状态）。
            ChapterFilter.DOWNLOADED -> false
            ChapterFilter.NOT_DOWNLOADED -> true
        }
    }
    // 章节数头部：16sp + ▤，点击打开筛选弹层（白皮书 07 §7.12；平板图 tablet/37）。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showFilter = true }
            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small)
            .testTag("detail-chapter-filter-open"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = "▤",
            style = UiTheme.typography.titleMedium.copy(fontSize = 16.sp),
            color = UiTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(DesignTokens.Spacing.Small))
        AppText(
            text = stringResource(Res.string.source_detail_chapters, visibleChapters.size),
            style = UiTheme.typography.titleMedium.copy(fontSize = 16.sp),
            color = UiTheme.colorScheme.onSurfaceVariant,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        if (filter != ChapterFilter.ALL) {
            AppText(
                text = filter.label(),
                style = UiTheme.typography.bodySmall,
                color = UiTheme.colorScheme.primary,
            )
        }
    }
    if (showFilter) {
        ModalReaderSheet(onDismissRequest = { showFilter = false }) {
            AppText(
                text = stringResource(Res.string.source_detail_chapter_filter),
                modifier = Modifier.padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small),
                style = UiTheme.typography.titleMedium,
            )
            ChapterFilter.entries.forEach { option ->
                SettingsItem(
                    title = option.label(),
                    selected = option == filter,
                    onClick = {
                        filter = option
                        showFilter = false
                    },
                )
            }
        }
    }
    if (selectedIds.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.ExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(Res.string.source_detail_chapters_selected, selectedIds.size),
                style = UiTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .testTag("detail-selection-count"),
            )
            AppTextButton(
                onClick = onClearSelection,
                modifier = Modifier.testTag("detail-clear-selection"),
            ) { AppText(stringResource(Res.string.source_detail_clear_selection)) }
            AppTextButton(
                onClick = { onMarkRead(selectedIds, true) },
                enabled = canMarkRead,
                modifier = Modifier.testTag("detail-mark-read"),
            ) { AppText(stringResource(Res.string.source_detail_mark_read)) }
            AppTextButton(
                onClick = { onMarkRead(selectedIds, false) },
                enabled = canMarkRead,
                modifier = Modifier.testTag("detail-mark-unread"),
            ) { AppText(stringResource(Res.string.source_detail_mark_unread)) }
        }
    }
    visibleChapters.forEach { chapter ->
        val read = readStates[chapter.id] ?: (fallbackReadChapterId == chapter.id)
        MangaChapterRow(
            chapter = chapter,
            enabled = enabled,
            selected = selectedIds.contains(chapter.id),
            read = read,
            onClick = {
                if (selectedIds.isNotEmpty()) onToggleSelect(chapter.id) else onOpen(chapter)
            },
            onToggleSelect = { onToggleSelect(chapter.id) },
        )
        AppDivider()
    }
}

/**
 * Komikku MangaChapterListItem: title row with read affordance, a meta secondary line
 * (scanlator/date/page count, falling back to the chapter id), a trailing download entry
 * (disabled until the download track lands) and 16/12/8/12 padding.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MangaChapterRow(
    chapter: WorkChapter,
    enabled: Boolean,
    selected: Boolean,
    read: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onToggleSelect,
            )
            .testTag("source-detail-chapter-${chapter.id}")
            .padding(DesignTokens.Sizes.ChapterItemPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            selected -> DecorativeActionIcon(
                ActionIcon.Checklist,
                modifier = Modifier.padding(end = DesignTokens.Spacing.Small).size(DesignTokens.Spacing.Medium),
                tint = UiTheme.colorScheme.primary,
            )

            // 未读点 8dp primary；已读不画点（白皮书 06 §6.7 / MangaChapterListItem.kt:128）。
            !read -> Box(
                Modifier.padding(end = DesignTokens.Spacing.Small)
                    .size(DesignTokens.Sizes.UnreadDot)
                    .background(UiTheme.colorScheme.primary, CircleShape),
            )

            else -> Spacer(Modifier.width(DesignTokens.Sizes.UnreadDot + DesignTokens.Spacing.Small))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Compact),
        ) {
            AppText(
                text = chapter.title,
                style = UiTheme.typography.bodyMedium,
                color = when {
                    selected -> UiTheme.colorScheme.primary
                    // 已读标题 38% 前景（白皮书 06 §6.7）。
                    read -> UiTheme.colorScheme.onSurface.copy(alpha = DesignTokens.Alpha.Disabled)
                    else -> UiTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = chapterSecondaryLine(chapter),
                style = UiTheme.typography.bodySmall,
                color = UiTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (read) DesignTokens.Alpha.Disabled else DesignTokens.Alpha.Secondary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (read) {
            AppText(
                text = stringResource(Res.string.source_detail_read),
                style = UiTheme.typography.labelSmall,
                color = UiTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = DesignTokens.Spacing.ExtraSmall),
            )
        }
        // 状态层 40dp 包住 26dp 指示器（白皮书 06 §6.7「状态层 40dp」）。
        Box(
            modifier = Modifier
                .size(DesignTokens.Sizes.SelectionMarker)
                .testTag("detail-chapter-download-${chapter.id}"),
            contentAlignment = Alignment.Center,
        ) {
            ChapterDownloadIndicator(state = ChapterDownloadState.NOT_DOWNLOADED)
        }
    }
}

/** 章节筛选选项（白皮书 07 §7.12；平板截图 tablet/37，手机按同一结构类推）。 */
private enum class ChapterFilter {
    ALL, UNREAD, READ, DOWNLOADED, NOT_DOWNLOADED;

    @Composable
    fun label(): String = stringResource(
        when (this) {
            ALL -> Res.string.source_detail_filter_all
            UNREAD -> Res.string.source_detail_filter_unread
            READ -> Res.string.source_detail_filter_read
            DOWNLOADED -> Res.string.source_detail_filter_downloaded
            NOT_DOWNLOADED -> Res.string.source_detail_filter_not_downloaded
        },
    )
}

private fun chapterSecondaryLine(chapter: WorkChapter): String {
    val parts = mutableListOf<String>()
    chapter.scanlator?.takeIf { it.isNotBlank() }?.let { parts += it }
    chapter.dateMillis?.let { parts += formatChapterDate(it) }
    chapter.pageCount?.let { parts += "${it}P" }
    if (parts.isEmpty()) parts += chapter.id
    return parts.joinToString(" · ")
}

private fun formatChapterDate(millis: Long): String = try {
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
} catch (_: Exception) {
    millis.toString()
}

/** 详情页继续阅读悬浮钮的测试锚点（Komikku MangaScreen.kt:532 一带）。 */
internal const val DetailFabTag = "detail-fab"

/**
 * 左右吸边判定（Komikku 手势结尾规则）：FAB 中心过半即吸附右侧。
 * 纯函数，单测直接断言。
 */
internal fun snapFabToStart(fabCenterX: Float, layoutWidth: Float): Boolean =
    fabCenterX < layoutWidth / 2f

/**
 * 详情页继续阅读悬浮钮：SmallExtendedFAB + Play 图标 + Start/Resume 文案。
 *
 * 横向拖动松手后按中心位置左右吸边（[snapFabToStart]），吸附结果由调用方
 * 持久化到书架偏好（`reading_button_position`）；点击直达继续/首章。
 */
@Composable
private fun BoxScope.DetailReadFab(
    atStart: Boolean,
    label: String,
    layoutWidthPx: Float,
    onOpen: () -> Unit,
    onSnap: (Boolean) -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var fabWidth by remember { mutableIntStateOf(0) }
    AppExtendedFab(
        compact = true,
        text = { AppText(label) },
        icon = { DecorativeActionIcon(ActionIcon.Play) },
        onClick = onOpen,
        modifier = Modifier
            .align(if (atStart) Alignment.BottomStart else Alignment.BottomEnd)
            .padding(DesignTokens.Spacing.Medium)
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .onGloballyPositioned { coordinates ->
                fabWidth = coordinates.size.width
            }
            .pointerInput(atStart, layoutWidthPx) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val leftEdge = if (atStart) 0f else layoutWidthPx - fabWidth
                        val snapped = snapFabToStart(
                            leftEdge + offsetX + fabWidth / 2f,
                            layoutWidthPx,
                        )
                        offsetX = 0f
                        if (snapped != atStart) onSnap(snapped)
                    },
                ) { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount
                }
            }
            .testTag(DetailFabTag),
    )
}
