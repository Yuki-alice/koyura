package dev.reader.app.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.app.resources.Res
import dev.reader.app.resources.source_detail_back
import dev.reader.app.resources.source_detail_chapters
import dev.reader.app.resources.source_detail_empty
import dev.reader.app.resources.source_detail_error
import dev.reader.app.resources.source_detail_no_description
import dev.reader.app.resources.source_detail_title
import dev.reader.app.resources.source_search_button
import dev.reader.app.resources.source_search_empty
import dev.reader.app.resources.source_search_error
import dev.reader.app.resources.source_search_hint
import dev.reader.app.resources.source_search_no_results
import dev.reader.app.resources.source_search_results
import dev.reader.app.resources.source_search_title
import kotlinx.coroutines.launch
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
                        Text(
                            text = stringResource(Res.string.source_search_results, results.size, maxPage),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    items(results, key = { it.id }) { work ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenWork(work.id, work.title) }
                                .testTag(sourceResultTag(work.id))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(work.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (work.subtitle.isNotEmpty()) {
                                Text(
                                    work.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
) {
    var detail by remember(sourceKey, workId) { mutableStateOf<WorkDetail?>(null) }
    var loading by remember(sourceKey, workId) { mutableStateOf(true) }
    var error by remember(sourceKey, workId) { mutableStateOf<String?>(null) }

    LaunchedEffect(sourceKey, workId) {
        loading = true
        error = null
        try {
            detail = session.loadWorkDetail(sourceKey, workId)
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load details"
        } finally {
            loading = false
        }
    }

    Column(modifier = modifier.fillMaxSize().testTag(SourceDetailTestTag)) {
        Text(
            text = stringResource(Res.string.source_detail_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.source_detail_error, error.orEmpty()),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onBack) {
                        Text(stringResource(Res.string.source_detail_back))
                    }
                }
            }
            detail != null -> {
                val loaded = detail!!
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = loaded.title.ifEmpty { fallbackTitle },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.testTag("source-detail-heading"),
                    )
                    if (loaded.subtitle.isNotEmpty()) {
                        Text(
                            text = loaded.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = loaded.description.ifEmpty {
                            stringResource(Res.string.source_detail_no_description)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(Res.string.source_detail_chapters, loaded.chapters.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    loaded.chapters.forEach { chapter ->
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("source-detail-chapter-${chapter.id}"),
                        )
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
