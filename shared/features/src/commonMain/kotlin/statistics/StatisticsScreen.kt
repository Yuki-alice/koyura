package app.koyura.features.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.DecorativeActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.SectionCard
import app.koyura.designsystem.components.AppCircularProgress
import app.koyura.designsystem.components.AppMenu
import app.koyura.designsystem.components.AppMenuItem
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.ReaderTopAppBar
import app.koyura.designsystem.components.UiTheme
import app.koyura.features.resources.Res
import app.koyura.features.resources.more_statistics
import app.koyura.features.resources.statistics_chapters
import app.koyura.features.resources.statistics_chapters_downloaded
import app.koyura.features.resources.statistics_chapters_read
import app.koyura.features.resources.statistics_chapters_total
import app.koyura.features.resources.statistics_completed_entries
import app.koyura.features.resources.statistics_entries
import app.koyura.features.resources.statistics_in_global_update
import app.koyura.features.resources.statistics_in_library
import app.koyura.features.resources.statistics_load_failed
import app.koyura.features.resources.statistics_local
import app.koyura.features.resources.statistics_mean_score
import app.koyura.features.resources.statistics_none
import app.koyura.features.resources.statistics_not_applicable
import app.koyura.features.resources.statistics_overview
import app.koyura.features.resources.statistics_read_duration
import app.koyura.features.resources.statistics_refresh
import app.koyura.features.resources.statistics_retry
import app.koyura.features.resources.statistics_started
import app.koyura.features.resources.statistics_title
import app.koyura.features.resources.statistics_tracked_entries
import app.koyura.features.resources.statistics_trackers
import app.koyura.features.resources.statistics_used
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

// region 测试锚点

const val StatisticsRootTag = "statistics-root"
const val StatisticsContentTag = "statistics-content"
const val StatisticsLoadingTag = "statistics-loading"
const val StatisticsErrorTag = "statistics-error"
const val StatisticsRetryTag = "statistics-retry"
const val StatisticsOverflowTag = "statistics-overflow"
const val StatisticsRefreshTag = "statistics-refresh"

fun statisticsSectionTag(key: String) = "statistics-section-$key"
fun statisticsMetricTag(section: String, key: String) = "statistics-metric-$section-$key"

// endregion

/**
 * 统计独立页（白皮书 07 §7.10，More → 统计）。
 *
 * 四张 SectionCard（Overview / Entries / Chapters / Trackers），卡片间距 8dp、标题在卡片外；
 * 数值 titleLarge Bold、说明 bodyMedium 0.78、图标 primary 置底。数据侧只有 Fake 仓储，
 * 生产聚合由数据层接入后替换注入。
 */
@Composable
fun StatisticsScreen(
    session: StatisticsSession,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var overflow by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        session.refresh()
    }

    Column(modifier = modifier.fillMaxSize().testTag(StatisticsRootTag)) {
        ReaderTopAppBar(
            title = { AppText(stringResource(Res.string.statistics_title)) },
            navigationIcon = {
                ActionIconButton(
                    icon = ActionIcon.Back,
                    label = stringResource(Res.string.more_statistics),
                    onClick = onBack,
                )
            },
            actions = {
                ActionIconButton(
                    icon = ActionIcon.More,
                    label = stringResource(Res.string.statistics_refresh),
                    onClick = { overflow = true },
                    modifier = Modifier.testTag(StatisticsOverflowTag),
                )
                AppMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                    AppMenuItem(
                        text = { AppText(stringResource(Res.string.statistics_refresh)) },
                        onClick = {
                            overflow = false
                            scope.launch { session.refresh() }
                        },
                        modifier = Modifier.testTag(StatisticsRefreshTag),
                    )
                }
            },
        )

        when (val load = session.loadState) {
            StatisticsLoadState.Loading -> Box(
                modifier = Modifier.fillMaxSize().testTag(StatisticsLoadingTag),
                contentAlignment = Alignment.Center,
            ) {
                AppCircularProgress()
            }
            is StatisticsLoadState.Failed -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DesignTokens.Spacing.Large)
                    .testTag(StatisticsErrorTag),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
            ) {
                AppText(
                    text = stringResource(Res.string.statistics_load_failed, load.message),
                    color = UiTheme.colorScheme.error,
                    style = UiTheme.typography.bodyMedium,
                )
                AppTextButton(
                    onClick = { scope.launch { session.refresh() } },
                    modifier = Modifier.testTag(StatisticsRetryTag),
                ) {
                    AppText(stringResource(Res.string.statistics_retry))
                }
            }
            StatisticsLoadState.Ready -> StatisticsContent(session.snapshot)
        }
    }
}

/** 四段卡片：标题在卡片外，卡片之间 8dp，整页左右 16dp。 */
@Composable
private fun StatisticsContent(snapshot: StatisticsSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                // 组标题横 32dp（白皮书 07 §7.10 统计页）。
                horizontal = DesignTokens.Spacing.ExtraLarge,
                vertical = DesignTokens.Spacing.Small,
            )
            .testTag(StatisticsContentTag),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
    ) {
        StatisticsSection(
            sectionKey = "overview",
            title = stringResource(Res.string.statistics_overview),
            metrics = listOf(
                Metric(
                    key = "inLibrary",
                    value = snapshot.overview.inLibrary.toString(),
                    label = stringResource(Res.string.statistics_in_library),
                    icon = ActionIcon.Categories,
                ),
                Metric(
                    key = "readDuration",
                    value = formatReadDuration(
                        snapshot.overview.readDurationMillis,
                        fallback = stringResource(Res.string.statistics_none),
                    ),
                    label = stringResource(Res.string.statistics_read_duration),
                    icon = ActionIcon.Hourglass,
                ),
                Metric(
                    key = "completedEntries",
                    value = snapshot.overview.completedEntries.toString(),
                    label = stringResource(Res.string.statistics_completed_entries),
                    icon = ActionIcon.Checklist,
                ),
            ),
        )
        StatisticsSection(
            sectionKey = "entries",
            title = stringResource(Res.string.statistics_entries),
            metrics = listOf(
                Metric(
                    key = "inGlobalUpdate",
                    value = snapshot.entries.inGlobalUpdate.toString(),
                    label = stringResource(Res.string.statistics_in_global_update),
                ),
                Metric(
                    key = "started",
                    value = snapshot.entries.started.toString(),
                    label = stringResource(Res.string.statistics_started),
                ),
                Metric(
                    key = "local",
                    value = snapshot.entries.local.toString(),
                    label = stringResource(Res.string.statistics_local),
                ),
            ),
        )
        StatisticsSection(
            sectionKey = "chapters",
            title = stringResource(Res.string.statistics_chapters),
            metrics = listOf(
                Metric(
                    key = "total",
                    value = snapshot.chapters.total.toString(),
                    label = stringResource(Res.string.statistics_chapters_total),
                ),
                Metric(
                    key = "read",
                    value = snapshot.chapters.read.toString(),
                    label = stringResource(Res.string.statistics_chapters_read),
                ),
                Metric(
                    key = "downloaded",
                    value = snapshot.chapters.downloaded.toString(),
                    label = stringResource(Res.string.statistics_chapters_downloaded),
                ),
            ),
        )
        StatisticsSection(
            sectionKey = "trackers",
            title = stringResource(Res.string.statistics_trackers),
            metrics = listOf(
                Metric(
                    key = "trackedEntries",
                    value = snapshot.trackers.trackedEntries.toString(),
                    label = stringResource(Res.string.statistics_tracked_entries),
                ),
                Metric(
                    key = "meanScore",
                    value = formatMeanScore(
                        snapshot.trackers.meanScore,
                        snapshot.trackers.trackedEntries,
                        notApplicable = stringResource(Res.string.statistics_not_applicable),
                    ),
                    label = stringResource(Res.string.statistics_mean_score),
                ),
                Metric(
                    key = "used",
                    value = snapshot.trackers.used.toString(),
                    label = stringResource(Res.string.statistics_used),
                ),
            ),
        )
    }
}

/** 一张卡片：标题（卡片外，titleMedium）+ [SectionCard] 内三列数值。 */
@Composable
private fun StatisticsSection(
    sectionKey: String,
    title: String,
    metrics: List<Metric>,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(statisticsSectionTag(sectionKey)),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
    ) {
        AppText(
            text = title,
            style = UiTheme.typography.titleSmall,
            color = UiTheme.colorScheme.onSurface,
        )
        SectionCard(cornerRadius = DesignTokens.Radius.ExtraLarge) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
                verticalAlignment = Alignment.Top,
            ) {
                metrics.forEach { metric ->
                    MetricColumn(
                        sectionKey = sectionKey,
                        metric = metric,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 单列数值：值 titleLarge Bold、说明 bodyMedium 0.78、图标 primary 置底。 */
@Composable
private fun MetricColumn(
    sectionKey: String,
    metric: Metric,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag(statisticsMetricTag(sectionKey, metric.key)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
    ) {
        AppText(
            text = metric.value,
            style = UiTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = UiTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AppText(
            text = metric.label,
            // 标签 11sp（白皮书 07 §7.10）。
            style = UiTheme.typography.labelSmall,
            color = UiTheme.colorScheme.onSurfaceVariant.copy(
                alpha = DesignTokens.Alpha.Secondary,
            ),
            textAlign = TextAlign.Center,
        )
        metric.icon?.let { icon ->
            DecorativeActionIcon(
                icon = icon,
                tint = UiTheme.colorScheme.primary,
                modifier = Modifier.size(DesignTokens.Sizes.DetailActionIcon),
            )
        }
    }
}

/** 一列统计数值：展示串 + 说明 + 可选置底图标（Overview 三段才有图标）。 */
private data class Metric(
    val key: String,
    val value: String,
    val label: String,
    val icon: ActionIcon? = null,
)
