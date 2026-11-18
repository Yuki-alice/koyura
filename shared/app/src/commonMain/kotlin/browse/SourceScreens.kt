package app.koyura.app.browse

import app.koyura.app.resources.browse_clear_search
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.style.TextAlign
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.SearchField

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import app.koyura.designsystem.DetailBackdrop
import app.koyura.designsystem.LocalCoverDecoder
import app.koyura.designsystem.LocalCoverImageLoader
import app.koyura.features.library.LibraryEntryKey
import app.koyura.features.library.LibraryKind
import app.koyura.features.library.LibrarySession
import app.koyura.features.library.NewLibraryEntry
import app.koyura.features.library.rememberLibrarySession
import app.koyura.designsystem.rememberCoverBitmap
import app.koyura.domain.reader.ReaderService
import kotlinx.coroutines.launch
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
@OptIn(ExperimentalMaterial3Api::class)
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
            TopAppBar(
                title = { Text(sourceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) { CircularProgressIndicator() }
                }
                error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.source_search_error, error.orEmpty()),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = { runSearch() }) {
                            Text(stringResource(Res.string.source_search_button))
                        }
                    }
                }
                submittedQuery == null -> {
                    Text(
                        text = stringResource(Res.string.source_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                results.isEmpty() -> {
                    Text(
                        text = stringResource(Res.string.source_search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
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
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Cover(
                                    sourceKey = sourceKey,
                                    coverUrl = work.cover,
                                    label = work.title,
                                    modifier = Modifier.size(width = 48.dp, height = 72.dp),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        work.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (work.subtitle.isNotEmpty()) {
                                        Text(
                                            work.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = app.koyura.designsystem.DesignTokens.Alpha.Secondary,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (work.tags.isNotEmpty()) {
                                        Text(
                                            work.tags.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = app.koyura.designsystem.DesignTokens.Alpha.Secondary,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
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
internal val DetailWideWidthThreshold = 720.dp

/** Work detail page backed by the source's `comic.loadInfo` callback. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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
    var visualInLibrary by rememberSaveable(sourceKey, workId) { mutableStateOf(false) }
    val inLibrary = library?.isFavorite(libraryKey) ?: visualInLibrary
    val sourceName = remember(session.state.sources, sourceKey) {
        session.state.sources.firstOrNull { it.id == sourceKey }?.name.orEmpty()
    }
    var selectedIds by remember(sourceKey, workId) { mutableStateOf(setOf<String>()) }
    var readStates by remember(sourceKey, workId) { mutableStateOf(mapOf<String, Boolean>()) }

    LaunchedEffect(sourceKey, workId, reloadTick) {
        loading = true
        error = null
        try {
            detail = session.loadWorkDetail(sourceKey, workId)
            progress = readerService?.readProgress(sourceKey, workId)
            // 真实已读：书架目录同步后读同一状态；未收藏/无目录即空映射，回退到进度章标读。
            readStates = try {
                library?.chapterReadStates(libraryKey) ?: emptyMap()
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
                CircularProgressIndicator()
            }
            error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.source_detail_error, error.orEmpty()),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onBack) {
                            Text(stringResource(Res.string.source_detail_back))
                        }
                        Button(
                            onClick = { reloadTick++ },
                        ) {
                            Text(stringResource(Res.string.browse_retry))
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
                CompositionLocalProvider(
                    LocalCoverImageLoader provides session.coverLoader(),
                    LocalCoverDecoder provides AppCoverDecoder,
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                loaded.title.ifEmpty { fallbackTitle },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
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
                    library?.actionError?.let { libraryError ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 点按错误行即清除（与书架页的清除按钮同一语义，避免引入新文案）。
                            Text(
                                text = libraryError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                                    .testTag("detail-library-error")
                                    .clickable { library.clearActionError() },
                            )
                        }
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        if (maxWidth >= DetailWideWidthThreshold) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .weight(0.42f)
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    DetailInfoColumn(
                                        detail = loaded,
                                        sourceKey = sourceKey,
                                        fallbackTitle = fallbackTitle,
                                        sourceName = sourceName.ifBlank { sourceKey },
                                        wide = true,
                                        inLibrary = inLibrary,
                                        onToggleLibrary = {
                                            val repo = library
                                            if (repo == null) {
                                                visualInLibrary = !visualInLibrary
                                            } else {
                                                scope.launch {
                                                    repo.setFavorite(
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
                                            }
                                        },
                                        canContinue = canContinue,
                                        onContinue = {
                                            val saved = progress
                                            if (saved != null) {
                                                onOpenChapter(saved.chapterId, chapterIds, chapterTitles)
                                            }
                                        },
                                        readerService = readerService,
                                        chapterIds = chapterIds,
                                        chapterTitles = chapterTitles,
                                        progress = progress,
                                        onOpenChapter = onOpenChapter,
                                    )
                                }
                                Column(modifier = Modifier.weight(0.58f).fillMaxSize()) {
                                    DetailChapterList(
                                        chapters = loaded.chapters,
                                        enabled = readerService != null,
                                        selectedIds = selectedIds,
                                        onToggleSelect = { id ->
                                            selectedIds = if (selectedIds.contains(id)) {
                                                selectedIds - id
                                            } else {
                                                selectedIds + id
                                            }
                                        },
                                        onClearSelection = { selectedIds = emptySet() },
                                        readStates = readStates,
                                        fallbackReadChapterId = progress?.chapterId,
                                        canMarkRead = inLibrary && library != null,
                                        onMarkRead = { ids, read ->
                                            val repo = library
                                            if (repo != null) {
                                                scope.launch {
                                                    if (repo.markDetailChapters(libraryKey, ids, read)) {
                                                        // 重读同一映射，保证“提交结果为准”。
                                                        readStates = repo.chapterReadStates(libraryKey)
                                                        selectedIds = emptySet()
                                                    }
                                                }
                                            }
                                        },
                                        onOpen = { chapter ->
                                            onOpenChapter(chapter.id, chapterIds, chapterTitles)
                                        },
                                    )
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            ) {
                                DetailInfoColumn(
                                    detail = loaded,
                                    sourceKey = sourceKey,
                                    fallbackTitle = fallbackTitle,
                                    sourceName = sourceName.ifBlank { sourceKey },
                                    wide = false,
                                    inLibrary = inLibrary,
                                    onToggleLibrary = {
                                        val repo = library
                                        if (repo == null) {
                                            visualInLibrary = !visualInLibrary
                                        } else {
                                            scope.launch {
                                                repo.setFavorite(
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
                                        }
                                    },
                                    canContinue = canContinue,
                                    onContinue = {
                                        val saved = progress
                                        if (saved != null) {
                                            onOpenChapter(saved.chapterId, chapterIds, chapterTitles)
                                        }
                                    },
                                    readerService = readerService,
                                    chapterIds = chapterIds,
                                    chapterTitles = chapterTitles,
                                    progress = progress,
                                    onOpenChapter = onOpenChapter,
                                )
                                DetailChapterList(
                                    chapters = loaded.chapters,
                                    enabled = readerService != null,
                                    selectedIds = selectedIds,
                                    onToggleSelect = { id ->
                                        selectedIds = if (selectedIds.contains(id)) {
                                            selectedIds - id
                                        } else {
                                            selectedIds + id
                                        }
                                    },
                                    onClearSelection = { selectedIds = emptySet() },
                                    readStates = readStates,
                                    fallbackReadChapterId = progress?.chapterId,
                                    canMarkRead = inLibrary && library != null,
                                    onMarkRead = { ids, read ->
                                        val repo = library
                                        if (repo != null) {
                                            scope.launch {
                                                if (repo.markDetailChapters(libraryKey, ids, read)) {
                                                    readStates = repo.chapterReadStates(libraryKey)
                                                    selectedIds = emptySet()
                                                }
                                            }
                                        }
                                    },
                                    onOpen = { chapter ->
                                        onOpenChapter(chapter.id, chapterIds, chapterTitles)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                Text(
                    text = stringResource(Res.string.source_detail_empty),
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.testTag("source-detail-heading"),
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = sourceName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ActionIcon(icon, null, modifier = Modifier.size(24.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
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
    sourceKey: String,
    fallbackTitle: String,
    sourceName: String,
    wide: Boolean,
    inLibrary: Boolean,
    onToggleLibrary: () -> Unit,
    canContinue: Boolean,
    onContinue: () -> Unit,
    readerService: ReaderService?,
    chapterIds: List<String>,
    chapterTitles: List<String>,
    progress: app.koyura.domain.reader.ReaderProgress?,
    onOpenChapter: (chapterId: String?, chapterIds: List<String>, chapterTitles: List<String>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val title = detail.title.ifEmpty { fallbackTitle }
    val backdrop = rememberCoverBitmap(sourceKey, detail.cover)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val coverWidth = (maxWidth * 0.62f).coerceIn(100.dp, 240.dp)
        DetailBackdrop(cover = backdrop, modifier = Modifier.matchParentSize())
        if (wide) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Cover(
                    sourceKey = sourceKey,
                    coverUrl = detail.cover,
                    label = title,
                    modifier = Modifier.size(coverWidth, coverWidth * 1.5f),
                )
                DetailHeading(title, detail.subtitle, sourceName, centered = true)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Cover(
                    sourceKey = sourceKey,
                    coverUrl = detail.cover,
                    label = title,
                    modifier = Modifier.size(96.dp, 144.dp),
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
            .padding(horizontal = 8.dp),
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
            modifier = Modifier.weight(1f).testTag("detail-progress"),
        )
        DetailAction(
            icon = ActionIcon.Public,
            label = stringResource(Res.string.source_detail_webview),
            onClick = {},
            enabled = false,
            modifier = Modifier.weight(1f).testTag("detail-webview"),
        )
    }
    val body = detail.description.ifEmpty { stringResource(Res.string.source_detail_no_description) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail.description.isNotEmpty()) {
            ActionIconButton(
                icon = if (expanded) ActionIcon.Collapse else ActionIcon.Expand,
                label = stringResource(if (expanded) Res.string.browse_show_less else Res.string.browse_show_more),
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
    if (readerService != null) {
        ReadingButtons(
            progress = progress,
            chapterIds = chapterIds,
            chapterTitles = chapterTitles,
            onStart = { ids, titles ->
                onOpenChapter(ids.firstOrNull(), ids, titles)
            },
            onContinue = { chapterId, ids, titles -> onOpenChapter(chapterId, ids, titles) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    if (detail.tags.isNotEmpty()) {
        Text(
            text = stringResource(Res.string.source_detail_tags),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        val multiGroup = detail.tags.size > 1
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("detail-tags"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            detail.tags.forEach { (group, values) ->
                values.forEachIndexed { index, value ->
                    val chipText = if (multiGroup) "$group · $value" else value
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.testTag("detail-chip-$group-$index"),
                    ) {
                        Text(
                            text = chipText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
    if (detail.updateTime.isNotEmpty() || detail.url.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("detail-meta"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (detail.updateTime.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.source_detail_updated, detail.updateTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (detail.url.isNotEmpty()) {
                Text(
                    text = "${stringResource(Res.string.source_detail_link)}: ${detail.url}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    app.koyura.designsystem.ListGroupHeader(
        text = stringResource(Res.string.source_detail_chapters, chapters.size),
    )
    if (selectedIds.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.source_detail_chapters_selected, selectedIds.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .testTag("detail-selection-count"),
            )
            TextButton(
                onClick = onClearSelection,
                modifier = Modifier.testTag("detail-clear-selection"),
            ) { Text(stringResource(Res.string.source_detail_clear_selection)) }
            TextButton(
                onClick = { onMarkRead(selectedIds, true) },
                enabled = canMarkRead,
                modifier = Modifier.testTag("detail-mark-read"),
            ) { Text(stringResource(Res.string.source_detail_mark_read)) }
            TextButton(
                onClick = { onMarkRead(selectedIds, false) },
                enabled = canMarkRead,
                modifier = Modifier.testTag("detail-mark-unread"),
            ) { Text(stringResource(Res.string.source_detail_mark_unread)) }
        }
    }
    chapters.forEach { chapter ->
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
        HorizontalDivider()
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
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            ActionIcon(
                ActionIcon.Checklist,
                null,
                modifier = Modifier.padding(end = 8.dp).size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Box(
                Modifier.padding(end = 8.dp).size(6.dp)
                    .background(
                        if (read) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary,
                        CircleShape,
                    ),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = chapterSecondaryLine(chapter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = app.koyura.designsystem.DesignTokens.Alpha.Secondary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (read) {
            Text(
                text = stringResource(Res.string.source_detail_read),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        ActionIconButton(
            icon = ActionIcon.Download,
            label = stringResource(Res.string.source_detail_download),
            onClick = {},
            enabled = false,
            modifier = Modifier.testTag("detail-chapter-download-${chapter.id}"),
        )
    }
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

/**
 * Start/continue actions driven by the saved progress. Shown only when a reader service is wired;
 * without one the detail page stays a plain info screen.
 */
@Composable
private fun ReadingButtons(
    progress: app.koyura.domain.reader.ReaderProgress?,
    chapterIds: List<String>,
    chapterTitles: List<String>,
    onStart: (chapterIds: List<String>, chapterTitles: List<String>) -> Unit,
    onContinue: (chapterId: String?, chapterIds: List<String>, chapterTitles: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { onStart(chapterIds, chapterTitles) },
            modifier = Modifier.testTag("reading-start"),
        ) {
            Text(stringResource(Res.string.reading_start))
        }
        val saved = progress
        if (saved != null && (saved.chapterId == null || saved.chapterId in chapterIds)) {
            val label = stringResource(Res.string.reading_continue, saved.pageIndex + 1)
            Button(
                onClick = { onContinue(saved.chapterId, chapterIds, chapterTitles) },
                modifier = Modifier.testTag("reading-continue"),
            ) {
                Text(label)
            }
        }
    }
}
