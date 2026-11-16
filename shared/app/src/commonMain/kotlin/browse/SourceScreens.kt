package app.koyura.app.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koyura.app.resources.Res
import app.koyura.app.resources.browse_retry
import app.koyura.app.resources.browse_show_less
import app.koyura.app.resources.browse_show_more
import app.koyura.app.resources.reading_continue
import app.koyura.app.resources.reading_start
import app.koyura.app.resources.source_detail_back
import app.koyura.app.resources.source_detail_chapters
import app.koyura.app.resources.source_detail_empty
import app.koyura.app.resources.source_detail_error
import app.koyura.app.resources.source_detail_no_description
import app.koyura.app.resources.source_search_button
import app.koyura.app.resources.source_search_empty
import app.koyura.app.resources.source_search_error
import app.koyura.app.resources.source_search_hint
import app.koyura.app.resources.source_search_no_results
import app.koyura.app.resources.source_search_results
import app.koyura.app.resources.source_search_title
import kotlinx.coroutines.launch
import app.koyura.app.LocalReaderService
import app.koyura.domain.reader.ReaderService
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

    Column(modifier = modifier.fillMaxSize().testTag(SourceSearchTestTag)) {
        Text(
            text = stringResource(Res.string.source_search_title, sourceName),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.source_search_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag(SourceSearchQueryTag),
            )
            Button(
                onClick = { runSearch() },
                enabled = !loading,
                modifier = Modifier.testTag(SourceSearchSubmitTag),
            ) { Text(stringResource(Res.string.source_search_button)) }
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
                        app.koyura.designsystem.KomikkuListGroupHeader(
                            text = stringResource(Res.string.source_search_results, results.size, maxPage),
                        )
                    }
                    items(results, key = { it.id }) { work ->
                        // Komikku BrowseSourceList row: cover placeholder with title/subtitle text.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenWork(work.id, work.title) }
                                .testTag(sourceResultTag(work.id))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            app.koyura.designsystem.KomikkuCoverPlaceholder(
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
                                            alpha = app.koyura.designsystem.KomikkuSecondaryAlpha,
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

internal const val SourceDetailTestTag = "source-detail"

/** Work detail page backed by the source's `comic.loadInfo` callback. */
@Composable
fun SourceDetailScreen(
    session: BrowseSession,
    sourceKey: String,
    workId: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    readerService: ReaderService? = LocalReaderService.current,
    onOpenChapter: (chapterId: String?, chapterIds: List<String>, chapterTitles: List<String>) -> Unit = { _, _, _ -> },
) {
    var detail by remember(sourceKey, workId) { mutableStateOf<WorkDetail?>(null) }
    var loading by remember(sourceKey, workId) { mutableStateOf(true) }
    var error by remember(sourceKey, workId) { mutableStateOf<String?>(null) }
    var progress by remember(sourceKey, workId) { mutableStateOf<app.koyura.domain.reader.ReaderProgress?>(null) }
    var reloadTick by remember(sourceKey, workId) { mutableStateOf(0) }

    LaunchedEffect(sourceKey, workId, reloadTick) {
        loading = true
        error = null
        try {
            detail = session.loadWorkDetail(sourceKey, workId)
            progress = readerService?.readProgress(sourceKey, workId)
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load details"
        } finally {
            loading = false
        }
    }

    // Komikku MangaScreen: header with cover/title/meta/description/actions, chapter-count
    // header, then one MangaChapterListItem-style row per chapter. Loading, empty and failed
    // states match the history/browse empty/error pattern with a retry entry.
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
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) {
                    MangaHeader(
                        title = loaded.title.ifEmpty { fallbackTitle },
                        subtitle = loaded.subtitle,
                        description = loaded.description,
                        sourceName = sourceKey,
                    )
                    if (readerService != null) {
                        ReadingButtons(
                            progress = progress,
                            chapterIds = loaded.chapters.map { it.id },
                            chapterTitles = loaded.chapters.map { it.title },
                            onStart = { ids, titles ->
                                onOpenChapter(ids.firstOrNull(), ids, titles)
                            },
                            onContinue = { chapterId, ids, titles -> onOpenChapter(chapterId, ids, titles) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    app.koyura.designsystem.KomikkuListGroupHeader(
                        text = stringResource(Res.string.source_detail_chapters, loaded.chapters.size),
                    )
                    loaded.chapters.forEach { chapter ->
                        MangaChapterRow(
                            chapter = chapter,
                            enabled = readerService != null,
                            onClick = {
                                onOpenChapter(
                                    chapter.id,
                                    loaded.chapters.map { it.id },
                                    loaded.chapters.map { it.title },
                                )
                            },
                        )
                        HorizontalDivider()
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
 * Komikku MangaInfoBox (simplified): cover placeholder, title, source/author line and an
 * expandable description. The heading test tag stays on the title so shell flows keep working.
 */
@Composable
private fun MangaHeader(
    title: String,
    subtitle: String,
    description: String,
    sourceName: String,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        app.koyura.designsystem.KomikkuCoverPlaceholder(
            label = title,
            modifier = Modifier.size(96.dp, 144.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.testTag("source-detail-heading"),
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = sourceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = app.koyura.designsystem.KomikkuSecondaryAlpha,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val body = description.ifEmpty { stringResource(Res.string.source_detail_no_description) }
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
        if (description.isNotEmpty()) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    stringResource(
                        if (expanded) Res.string.browse_show_less else Res.string.browse_show_more,
                    ),
                )
            }
        }
    }
}

/**
 * Komikku MangaChapterListItem (simplified): title row with read-progress affordance and a
 * secondary line, padded start 16 / top 12 / end 8 / bottom 12. Direct tap opens the chapter in
 * the reader; the row test tag is unchanged.
 */
@Composable
private fun MangaChapterRow(
    chapter: WorkChapter,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .testTag("source-detail-chapter-${chapter.id}")
            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = chapter.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = app.koyura.designsystem.KomikkuSecondaryAlpha,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
