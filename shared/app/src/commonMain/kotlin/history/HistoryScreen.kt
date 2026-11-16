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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import app.koyura.app.resources.Res
import app.koyura.app.resources.browse_error_prefix
import app.koyura.app.resources.browse_retry
import app.koyura.app.resources.history_continue
import app.koyura.app.resources.history_empty
import app.koyura.app.resources.history_no_results
import app.koyura.app.resources.history_page
import app.koyura.app.resources.history_search
import app.koyura.app.resources.history_single_chapter
import app.koyura.app.resources.history_title
import app.koyura.app.resources.history_today
import app.koyura.app.resources.history_unknown_time
import app.koyura.app.resources.history_yesterday
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal const val HistoryRootTestTag = "history-root"
internal const val HistorySearchTag = "history-search-query"
internal const val HistoryListTag = "history-list"
internal const val HistoryEmptyTag = "history-empty"
internal const val HistoryNoResultsTag = "history-no-results"
internal const val HistoryErrorTag = "history-error"

internal fun historyRowTag(sourceKey: String, workId: String) = "history-row-$sourceKey-$workId"
internal fun historyRowTitleTag(sourceKey: String, workId: String) = "history-row-title-$sourceKey-$workId"
internal fun historyRowDetailTag(sourceKey: String, workId: String) = "history-row-detail-$sourceKey-$workId"
internal fun historyContinueTag(sourceKey: String, workId: String) = "history-continue-$sourceKey-$workId"
internal fun historyGroupTag(key: String) = "history-group-$key"

/**
 * Reading history: one row per (source, work), newest first, grouped by day.
 *
 * The list is rendered from SQLite only and survives tab switches, child pages and the bar/rail
 * width switch through the shell's saved-state holder.
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

    LaunchedEffect(Unit) {
        session.refresh()
    }

    val filtered = session.entries.filter { it.matches(query) }
    val sections = filtered.groupedByDate(today())

    Column(modifier = modifier.fillMaxSize().testTag(HistoryRootTestTag)) {
        Text(
            text = stringResource(Res.string.history_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
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
                    EmptyState(stringResource(Res.string.history_no_results), HistoryNoResultsTag)
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
private fun HistoryRow(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
) {
    // Komikku HistoryItem (simplified): fixed 96 dp row, cover placeholder, semibold title,
    // chapter/page secondary line, source line, resume affordance. Tapping the row opens the
    // work; Continue resumes the saved chapter/page. Test tags are unchanged.
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
        app.koyura.designsystem.KomikkuCoverPlaceholder(
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
        TextButton(
            onClick = onContinue,
            modifier = Modifier.testTag(historyContinueTag(entry.sourceKey, entry.workId)),
        ) {
            Text(stringResource(Res.string.history_continue))
        }
    }
}

private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Couldn't open"
