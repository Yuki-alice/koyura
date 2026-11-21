package app.koyura.features.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.Cover
import app.koyura.designsystem.CoverDecoder
import app.koyura.designsystem.CoverImageLoader
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.EmptyScreen
import app.koyura.designsystem.LocalCoverDecoder
import app.koyura.designsystem.LocalCoverImageLoader
import app.koyura.designsystem.components.AppCircularProgress
import app.koyura.designsystem.components.AppDivider
import app.koyura.designsystem.components.AppMenu
import app.koyura.designsystem.components.AppMenuItem
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.SearchField
import app.koyura.designsystem.components.ModalReaderSheet
import app.koyura.designsystem.components.ReaderTopAppBar
import app.koyura.designsystem.components.UiTheme
import app.koyura.domain.updates.LibraryUpdateScheduleState
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import app.koyura.features.resources.Res
import app.koyura.features.resources.updates_calendar
import app.koyura.features.resources.updates_chapters
import app.koyura.features.resources.updates_empty
import app.koyura.features.resources.updates_errors_empty
import app.koyura.features.resources.updates_errors_title
import app.koyura.features.resources.updates_filter
import app.koyura.features.resources.updates_filter_hint
import app.koyura.features.resources.updates_last_updated
import app.koyura.features.resources.updates_load_failed
import app.koyura.features.resources.updates_refresh
import app.koyura.features.resources.updates_retry
import app.koyura.features.resources.updates_schedule_last
import app.koyura.features.resources.updates_schedule_next
import app.koyura.features.resources.updates_schedule_none
import app.koyura.features.resources.updates_schedule_stats
import app.koyura.features.resources.updates_retry_failures
import app.koyura.features.resources.updates_show_errors
import app.koyura.features.resources.updates_show_updates
import app.koyura.features.resources.updates_smart_settings
import app.koyura.features.resources.updates_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

const val UpdatesRootTestTag = "updates-root"
const val UpdatesListTag = "updates-list"
const val UpdatesEmptyTag = "updates-empty"
const val UpdatesErrorTag = "updates-error"
const val UpdatesRefreshTag = "updates-refresh"
const val UpdatesOverflowTag = "updates-overflow"
const val UpdatesFilterTag = "updates-filter"
const val UpdatesFilterFieldTag = "updates-filter-field"
const val UpdatesScheduleTag = "updates-schedule"
const val UpdatesScheduleDialogTag = "updates-schedule-dialog"
const val UpdatesErrorsListTag = "updates-errors-list"
const val UpdatesRetryFailuresTag = "updates-retry-failures"
const val UpdatesSmartSettingsTag = "updates-smart-settings"

fun updatesWorkTag(sourceKey: String, workId: String) = "updates-work-$sourceKey-$workId"
fun updatesChapterTag(sourceKey: String, workId: String, chapterId: String) =
    "updates-chapter-$sourceKey-$workId-$chapterId"
fun updatesFailureTag(sourceKey: String, workId: String) = "updates-failure-$sourceKey-$workId"

/**
 * 更新页：按作品聚合的新章节组（白皮书 07 §7.7）。
 *
 * 首行是领导者（48dp 封面 + 标题 + 未读数），后续章节行缩进对齐标题
 * （Code: UpdatesUiItem.kt，领导者行顶 8dp + 纵向 4dp + 横向 16dp，章节行以前导占位
 * Box 对齐）。空态复用 EmptyScreen 规范（白皮书 06 §6.8）。
 *
 * 数据侧走生产更新引擎：筛选按标题过滤快照，日历读后台调度状态，
 * 刷新触发真实扫描。章节点击打开作品详情（详情内可进章节）。
 */
@Composable
fun UpdatesScreen(
    session: UpdatesSession,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    /** 后台调度快照（装配层提供；null 时日历键置灰）。 */
    scheduleState: LibraryUpdateScheduleState? = null,
    modifier: Modifier = Modifier,
    coverLoader: CoverImageLoader? = LocalCoverImageLoader.current,
    coverDecoder: CoverDecoder? = LocalCoverDecoder.current,
    policySession: UpdatePolicySession = rememberUpdatePolicySession(),
) {
    val scope = rememberCoroutineScope()
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }

    LaunchedEffect(showPolicy) {
        // 每次打开对话框都重读一次仓储，避免展示过期策略。
        if (showPolicy) policySession.refresh()
    }

    LaunchedEffect(Unit) {
        session.refresh()
    }

    CompositionLocalProvider(
        LocalCoverImageLoader provides coverLoader,
        LocalCoverDecoder provides coverDecoder,
    ) {
        Column(modifier = modifier.fillMaxSize().testTag(UpdatesRootTestTag)) {
            ReaderTopAppBar(
                title = { AppText(stringResource(Res.string.updates_title)) },
                actions = {
                    ActionIconButton(
                        icon = ActionIcon.Filter,
                        label = stringResource(Res.string.updates_filter),
                        onClick = { showFilter = !showFilter },
                        modifier = Modifier.testTag(UpdatesFilterTag),
                    )
                    ActionIconButton(
                        icon = ActionIcon.Calendar,
                        label = stringResource(Res.string.updates_calendar),
                        onClick = { showSchedule = true },
                        enabled = scheduleState != null,
                        modifier = Modifier.testTag(UpdatesScheduleTag),
                    )
                    ActionIconButton(
                        icon = ActionIcon.Sync,
                        label = stringResource(Res.string.updates_refresh),
                        onClick = { scope.launch { session.checkNow() } },
                        modifier = Modifier.testTag(UpdatesRefreshTag),
                    )
                    ActionIconButton(
                        icon = ActionIcon.More,
                        label = stringResource(Res.string.updates_show_errors),
                        onClick = { overflowOpen = true },
                        modifier = Modifier.testTag(UpdatesOverflowTag),
                    )
                    AppMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                    ) {
                        AppMenuItem(
                            text = {
                                AppText(
                                    if (showErrors) {
                                        stringResource(Res.string.updates_show_updates)
                                    } else {
                                        stringResource(
                                            Res.string.updates_show_errors,
                                        ) + " (${session.failures.size})"
                                    },
                                )
                            },
                            onClick = {
                                overflowOpen = false
                                showErrors = !showErrors
                            },
                            modifier = Modifier.testTag("updates-errors-toggle"),
                        )
                        AppMenuItem(
                            text = { AppText(stringResource(Res.string.updates_smart_settings)) },
                            onClick = {
                                overflowOpen = false
                                showPolicy = true
                            },
                            modifier = Modifier.testTag(UpdatesSmartSettingsTag),
                        )
                    }
                },
            )

            if (showFilter) {
                SearchField(
                    value = session.query,
                    onValueChange = session::updateQuery,
                    placeholder = stringResource(Res.string.updates_filter_hint),
                    clearLabel = stringResource(Res.string.updates_filter),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = DesignTokens.Spacing.Medium,
                            vertical = DesignTokens.Spacing.Small,
                        )
                        .testTag(UpdatesFilterFieldTag),
                )
            }
            if (showSchedule && scheduleState != null) {
                UpdateScheduleDialog(state = scheduleState, onDismiss = { showSchedule = false })
            }
            when (val state = session.loadState) {
                UpdatesLoadState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    AppCircularProgress()
                }
                is UpdatesLoadState.Failed -> Column(
                    modifier = Modifier.fillMaxSize()
                        .padding(DesignTokens.Spacing.Large)
                        .testTag(UpdatesErrorTag),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
                ) {
                    AppText(
                        text = stringResource(Res.string.updates_load_failed, state.message),
                        color = UiTheme.colorScheme.error,
                    )
                    AppTextButton(onClick = { scope.launch { session.checkNow() } }) {
                        AppText(stringResource(Res.string.updates_retry))
                    }
                }
                UpdatesLoadState.Ready -> {
                    session.actionError?.let { message ->
                        AppText(
                            text = message,
                            color = UiTheme.colorScheme.error,
                            style = UiTheme.typography.bodyMedium,
                            modifier = Modifier
                                .padding(
                                    horizontal = DesignTokens.Spacing.Medium,
                                    vertical = DesignTokens.Spacing.Small,
                                )
                                .testTag("updates-action-error"),
                        )
                    }
                    if (showErrors) {
                        UpdateFailuresView(
                            failures = session.failures,
                            onRetryAll = { scope.launch { session.retryFailures() } },
                        )
                    } else if (session.visibleEntries.isEmpty()) {
                        UpdatesEmptyState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag(UpdatesListTag),
                        ) {
                            // 「Last updated」斜体行（bodyMedium + Italic，16/8 内边距，白皮书 07 §7.4）。
                            item(key = "updates-last-updated") {
                                AppText(
                                    // 仓储没有 lastChecked 字段：只声明「本次会话」，不编造时间戳。
                                    text = stringResource(Res.string.updates_last_updated),
                                    style = UiTheme.typography.bodyMedium.copy(
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    ),
                                    color = UiTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = DesignTokens.Spacing.Medium,
                                            vertical = DesignTokens.Spacing.Small,
                                        ),
                                )
                            }
                            items(session.visibleEntries, key = { "${it.sourceKey}-${it.workId}" }) { work ->
                                UpdateLeaderRow(
                                    work = work,
                                    onOpen = { onOpenWork(work.sourceKey, work.workId, work.title) },
                                )
                                work.chapters.forEach { chapter ->
                                    UpdateChapterRow(
                                        work = work,
                                        chapter = chapter,
                                        onOpen = { onOpenWork(work.sourceKey, work.workId, work.title) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showPolicy) {
            UpdatePolicyDialog(session = policySession, onDismiss = { showPolicy = false })
        }
    }
}

/**
 * 更新计划对话框：下次检查时间 + 上次检查结果（后台调度器的真实快照）。
 *
 * 未安排时只展示「未安排」；时间按设备时区格式化到分钟，不编造秒级精度。
 */
@Composable
private fun UpdateScheduleDialog(
    state: LibraryUpdateScheduleState,
    onDismiss: () -> Unit,
) {
    ModalReaderSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = DesignTokens.Spacing.Medium,
                    vertical = DesignTokens.Spacing.Medium,
                )
                .testTag(UpdatesScheduleDialogTag),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
        ) {
            AppText(
                text = stringResource(Res.string.updates_calendar),
                style = UiTheme.typography.titleMedium,
            )
            val next = state.nextUpdateMillis
            AppText(
                text = if (next == null) {
                    stringResource(Res.string.updates_schedule_none)
                } else {
                    stringResource(Res.string.updates_schedule_next, formatScheduleMillis(next))
                },
                style = UiTheme.typography.bodyMedium,
                color = UiTheme.colorScheme.onSurfaceVariant,
            )
            val last = state.lastCheck
            if (last != null) {
                AppText(
                    text = stringResource(Res.string.updates_schedule_last, formatScheduleMillis(last.checkedAtMillis)),
                    style = UiTheme.typography.bodyMedium,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                )
                AppText(
                    text = stringResource(
                        Res.string.updates_schedule_stats,
                        last.worksChecked,
                        last.newChapters,
                        last.failures,
                    ),
                    style = UiTheme.typography.bodySmall,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 调度毫秒 → 设备时区「yyyy-MM-dd HH:mm」（分钟精度，不编造秒）。 */
internal fun formatScheduleMillis(millis: Long): String = try {
    val dateTime = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
    "%04d-%02d-%02d %02d:%02d".format(
        dateTime.year, dateTime.monthNumber, dateTime.dayOfMonth, dateTime.hour, dateTime.minute,
    )
} catch (_: Exception) {
    millis.toString()
}

/** 空态：displayMedium 颜文字（0.78）+ bodyMedium 文案（白皮书 06 §6.8）。 */
@Composable
private fun UpdatesEmptyState() {
    EmptyScreen(
        emoji = "Σ(ಠ_ಠ)",
        message = stringResource(Res.string.updates_empty),
        modifier = Modifier.testTag(UpdatesEmptyTag),
    )
}

/** 领导者行：48dp 封面 + 标题 + 未读数（Code: UpdatesUiItem.kt:279-298 行边距）。 */
@Composable
private fun UpdateLeaderRow(
    work: UpdateWork,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(top = DesignTokens.Spacing.Small)
            .padding(
                vertical = DesignTokens.Spacing.ExtraSmall,
                horizontal = DesignTokens.Spacing.Medium,
            )
            .testTag(updatesWorkTag(work.sourceKey, work.workId)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            sourceKey = work.sourceKey,
            coverUrl = work.coverUrl,
            label = work.title,
            modifier = Modifier
                .width(DesignTokens.Sizes.UpdateCoverWidth)
                .aspectRatio(2f / 3f),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = DesignTokens.Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
        ) {
            AppText(
                text = work.title,
                style = UiTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = UiTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = stringResource(Res.string.updates_chapters, work.chapters.size),
                style = UiTheme.typography.bodySmall,
                color = UiTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = DesignTokens.Alpha.Secondary,
                ),
            )
        }
    }
}

/** 章节行：前导占位 Box 与领导者封面等宽，标题对齐（Code: UpdatesUiItem.kt:350-357）。 */
@Composable
private fun UpdateChapterRow(
    work: UpdateWork,
    chapter: UpdateChapter,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = DesignTokens.Spacing.Medium)
            .testTag(updatesChapterTag(work.sourceKey, work.workId, chapter.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(DesignTokens.Sizes.UpdateCoverWidth))
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = DesignTokens.Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(end = DesignTokens.Spacing.ExtraSmall)
                    .background(
                        UiTheme.colorScheme.primary,
                        CircleShape,
                    )
                    .width(DesignTokens.Spacing.Small)
                    .aspectRatio(1f),
            )
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = chapter.title,
                    style = UiTheme.typography.bodyMedium,
                    color = UiTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    text = chapter.dateText,
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
}

/** 更新错误视图：失败作品与错误信息 + 全部重试（调 `session.retryFailures`）。 */
@Composable
private fun UpdateFailuresView(
    failures: List<UpdateFailure>,
    onRetryAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().testTag(UpdatesErrorsListTag)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = DesignTokens.Spacing.Medium,
                    vertical = DesignTokens.Spacing.Small,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(
                text = stringResource(Res.string.updates_errors_title),
                style = UiTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            AppTextButton(
                onClick = onRetryAll,
                enabled = failures.isNotEmpty(),
                modifier = Modifier.testTag(UpdatesRetryFailuresTag),
            ) {
                AppText(stringResource(Res.string.updates_retry_failures))
            }
        }
        if (failures.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(DesignTokens.Spacing.Large),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = stringResource(Res.string.updates_errors_empty),
                    style = UiTheme.typography.bodyMedium,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(failures, key = { "${it.sourceKey}-${it.workId}" }) { failure ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(updatesFailureTag(failure.sourceKey, failure.workId))
                            .padding(
                                horizontal = DesignTokens.Spacing.Medium,
                                vertical = DesignTokens.Spacing.Small,
                            ),
                        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
                    ) {
                        AppText(
                            text = failure.title,
                            style = UiTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = UiTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AppText(
                            text = failure.message,
                            style = UiTheme.typography.bodySmall,
                            color = UiTheme.colorScheme.error,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AppDivider()
                }
            }
        }
    }
}
