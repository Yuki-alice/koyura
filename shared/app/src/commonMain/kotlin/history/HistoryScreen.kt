package app.koyura.app.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import app.koyura.app.browse.AppCoverDecoder
import app.koyura.app.resources.Res
import app.koyura.app.resources.browse_error_prefix
import app.koyura.app.resources.browse_retry
import app.koyura.app.resources.history_clear_all
import app.koyura.app.resources.history_clear_confirm
import app.koyura.app.resources.history_clear_confirm_body
import app.koyura.app.resources.history_clear_confirm_title
import app.koyura.app.resources.history_continue
import app.koyura.app.resources.history_delete
import app.koyura.app.resources.history_empty
import app.koyura.app.resources.history_favorite_add
import app.koyura.app.resources.history_favorite_remove
import app.koyura.app.resources.history_filter
import app.koyura.app.resources.history_filter_clear
import app.koyura.app.resources.history_filter_favorites
import app.koyura.app.resources.history_filter_sources
import app.koyura.app.resources.history_filter_title
import app.koyura.app.resources.history_no_results
import app.koyura.app.resources.history_page
import app.koyura.app.resources.history_search
import app.koyura.app.resources.history_single_chapter
import app.koyura.app.resources.history_title
import app.koyura.app.resources.history_today
import app.koyura.app.resources.history_unknown_time
import app.koyura.app.resources.history_yesterday
import app.koyura.designsystem.KomikkuCover
import app.koyura.designsystem.LocalCoverDecoder
import app.koyura.designsystem.LocalCoverImageLoader
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal const val HistoryRootTestTag = "history-root"
internal const val HistorySearchTag = "history-search-query"
internal const val HistoryListTag = "history-list"
internal const val HistoryEmptyTag = "history-empty"
internal const val HistoryNoResultsTag = "history-no-results"
internal const val HistoryErrorTag = "history-error"
internal const val HistoryFilterTag = "history-filter"
internal const val HistoryFilterDialogTag = "history-filter-dialog"
internal const val HistoryClearAllTag = "history-clear-all"
internal const val HistoryClearConfirmTag = "history-clear-confirm"

internal fun historyRowTag(sourceKey: String, workId: String) = "history-row-$sourceKey-$workId"
internal fun historyRowTitleTag(sourceKey: String, workId: String) = "history-row-title-$sourceKey-$workId"
internal fun historyRowDetailTag(sourceKey: String, workId: String) = "history-row-detail-$sourceKey-$workId"
internal fun historyContinueTag(sourceKey: String, workId: String) = "history-continue-$sourceKey-$workId"
internal fun historyDeleteTag(sourceKey: String, workId: String) = "history-delete-$sourceKey-$workId"
internal fun historyFavoriteTag(sourceKey: String, workId: String) = "history-favorite-$sourceKey-$workId"
internal fun historyGroupTag(key: String) = "history-group-$key"

/**
 * Reading history: one row per (source, work), newest first, grouped by day.
 *
 * The list is rendered from SQLite only and survives tab switches, child pages and the bar/rail
 * width switch through the shell's saved-state holder. Deletes/favorites/filters apply on top
 * of the snapshot in the session; persistence for delete/favorite lands with the data-layer
 * store (see notes).
 */
@Composable
fun HistoryScreen(
    session: HistorySession,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    onContinue: (entry: HistoryEntry, chapterIds: List<String>, chapterTitles: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        session.refresh()
    }

    val filtered = session.visibleEntries().filter { it.matches(query) }
    val sections = filtered.groupedByDate(today())

    CompositionLocalProvider(
        LocalCoverImageLoader provides session.coverLoader(),
        LocalCoverDecoder provides AppCoverDecoder,
    ) {
        Column(modifier = modifier.fillMaxSize().testTag(HistoryRootTestTag)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.history_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
                TextButton(
                    onClick = { showFilter = true },
                    modifier = Modifier.testTag(HistoryFilterTag),
                ) { Text(stringResource(Res.string.history_filter)) }
                TextButton(
                    onClick = { confirmClear = true },
                    enabled = session.entries.isNotEmpty(),
                    modifier = Modifier.testTag(HistoryClearAllTag),
                ) { Text(stringResource(Res.string.history_clear_all)) }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.history_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(HistorySearchTag),
            )

            when (val load = session.loadState) {
                HistoryLoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is HistoryLoadState.Failed -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp).testTag(HistoryErrorTag),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.browse_error_prefix, load.message),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = { scope.launch { session.refresh() } }) {
                        Text(stringResource(Res.string.browse_retry))
                    }
                }
                HistoryLoadState.Ready -> {
                    session.errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("history-action-error"),
                        )
                    }
                    if (session.entries.isEmpty()) {
                        EmptyState(stringResource(Res.string.history_empty), HistoryEmptyTag)
                    } else if (sections.isEmpty()) {
                        // 本地删除/清空后可见行为空且无查询与筛选时，显示空态而非无结果，
                        // 与刷新后（服务侧已空）一致。
                        if (query.isBlank() &&
                            session.filteredSourceKeys.isEmpty() &&
                            !session.showFavoritesOnly
                        ) {
                            EmptyState(stringResource(Res.string.history_empty), HistoryEmptyTag)
                        } else {
                            EmptyState(stringResource(Res.string.history_no_results), HistoryNoResultsTag)
                        }
                    } else {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().testTag(HistoryListTag),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            sections.forEach { section ->
                                val groupKey = section.date?.toString() ?: "unknown"
                                stickyHeader(key = "history-group-$groupKey") {
                                    HistoryGroupHeader(section.date, groupKey)
                                }
                                items(section.entries, key = { "history-${it.sourceKey}-${it.workId}" }) { entry ->
                                    HistoryRow(
                                        entry = entry,
                                        favorited = session.isFavorite(entry),
                                        onOpen = { onOpenWork(entry.sourceKey, entry.workId, entry.displayTitle) },
                                        onContinue = {
                                            scope.launch {
                                                session.clearError()
                                                try {
                                                    val (ids, titles) = session.resolveChapters(entry)
                                                    onContinue(entry, ids, titles)
                                                } catch (failure: Exception) {
                                                    session.reportError(failure.userMessage())
                                                }
                                            }
                                        },
                                        onDelete = { scope.launch { session.delete(entry) } },
                                        onToggleFavorite = { session.toggleFavorite(entry) },
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilter) {
        HistoryFilterDialog(
            session = session,
            onDismiss = { showFilter = false },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            modifier = Modifier.testTag(HistoryClearConfirmTag),
            title = { Text(stringResource(Res.string.history_clear_confirm_title)) },
            text = { Text(stringResource(Res.string.history_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        scope.launch { session.clearAll() }
                    },
                    modifier = Modifier.testTag("history-clear-confirm-ok"),
                ) { Text(stringResource(Res.string.history_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(Res.string.history_filter_clear))
                }
            },
        )
    }
}

private fun today(): kotlinx.datetime.LocalDate =
    Clock.System.todayIn(TimeZone.currentSystemDefault())

private fun daysBetween(today: kotlinx.datetime.LocalDate, date: kotlinx.datetime.LocalDate): Long =
    today.toEpochDays() - date.toEpochDays()
@Composable
private fun EmptyState(text: String, testTag: String) {
    Box(modifier = Modifier.fillMaxSize().testTag(testTag), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryGroupHeader(date: kotlinx.datetime.LocalDate?, groupKey: String) {
    val now = remember { today() }
    val text = when (date) {
        null -> stringResource(Res.string.history_unknown_time)
        now -> stringResource(Res.string.history_today)
        else -> when (daysBetween(now, date)) {
            1L -> stringResource(Res.string.history_yesterday)
            else -> date.toString()
        }
    }
    // Komikku ListGroupHeader: semibold bodyMedium in onSurfaceVariant.
    Box(modifier = Modifier.testTag(historyGroupTag(groupKey))) {
        app.koyura.designsystem.KomikkuListGroupHeader(text = text)
    }
}

@Composable
private fun HistoryFilterDialog(
    session: HistorySession,
    onDismiss: () -> Unit,
) {
    // Local draft so toggling sources does not reshuffle the list behind the dialog.
    var draftSources by remember(session.entries) { mutableStateOf(session.filteredSourceKeys) }
    var draftFavorites by remember(session.showFavoritesOnly) { mutableStateOf(session.showFavoritesOnly) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(HistoryFilterDialogTag),
        title = { Text(stringResource(Res.string.history_filter_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.history_filter_sources),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                session.availableSources().forEach { sourceKey ->
                    val checked = draftSources.isEmpty() || draftSources.contains(sourceKey)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                draftSources = if (draftSources.contains(sourceKey)) {
                                    // Removing the last explicit source means "all", same as empty.
                                    draftSources - sourceKey
                                } else if (draftSources.isEmpty()) {
                                    session.availableSources().toSet() - sourceKey
                                } else {
                                    draftSources + sourceKey
                                }
                            }
                            .testTag("history-filter-source-$sourceKey"),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                        )
                        Text(
                            text = sourceKey,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { draftFavorites = !draftFavorites }
                        .testTag("history-filter-favorites"),
                ) {
                    Checkbox(checked = draftFavorites, onCheckedChange = null)
                    Text(
                        text = stringResource(Res.string.history_filter_favorites),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    session.setSourceFilter(
                        if (draftSources.size == session.availableSources().size) emptySet() else draftSources,
                    )
                    session.setFavoritesOnly(draftFavorites)
                    onDismiss()
                },
                modifier = Modifier.testTag("history-filter-apply"),
            ) { Text(stringResource(Res.string.history_filter)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    session.clearFilters()
                    onDismiss()
                },
                modifier = Modifier.testTag("history-filter-reset"),
            ) { Text(stringResource(Res.string.history_filter_clear)) }
        },
    )
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    favorited: Boolean,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    // Komikku HistoryItem: fixed 96 dp row, shared cover (56x80), semibold title, chapter/page
    // secondary line, source line, favorite + delete + resume affordances. Tapping the row
    // opens the work; Continue resumes the saved chapter/page. Test tags are unchanged.
    val chapterLabel = entry.chapterTitle?.takeIf { it.isNotBlank() }
        ?: entry.chapterId
        ?: stringResource(Res.string.history_single_chapter)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag(historyRowTag(entry.sourceKey, entry.workId))
            .height(96.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomikkuCover(
            sourceKey = entry.sourceKey,
            coverUrl = entry.cover,
            label = entry.displayTitle,
            modifier = Modifier.size(width = 56.dp, height = 80.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.displayTitle,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(historyRowTitleTag(entry.sourceKey, entry.workId)),
            )
            Text(
                text = "$chapterLabel · " + stringResource(Res.string.history_page, entry.pageIndex + 1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = app.koyura.designsystem.KomikkuSecondaryAlpha,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(historyRowDetailTag(entry.sourceKey, entry.workId)),
            )
            Text(
                text = entry.sourceKey,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = app.koyura.designsystem.KomikkuSecondaryAlpha,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            TextButton(
                onClick = onToggleFavorite,
                modifier = Modifier.testTag(historyFavoriteTag(entry.sourceKey, entry.workId)),
            ) {
                Text(
                    stringResource(
                        if (favorited) Res.string.history_favorite_remove else Res.string.history_favorite_add,
                    ),
                    color = if (favorited) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag(historyDeleteTag(entry.sourceKey, entry.workId)),
                ) {
                    Text(stringResource(Res.string.history_delete))
                }
                TextButton(
                    onClick = onContinue,
                    modifier = Modifier.testTag(historyContinueTag(entry.sourceKey, entry.workId)),
                ) {
                    Text(stringResource(Res.string.history_continue))
                }
            }
        }
    }
}

private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Couldn't open"
