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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
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
import app.koyura.designsystem.LocalCoverDecoder
import app.koyura.designsystem.LocalCoverImageLoader
import app.koyura.features.resources.Res
import app.koyura.features.resources.updates_calendar
import app.koyura.features.resources.updates_chapters
import app.koyura.features.resources.updates_empty
import app.koyura.features.resources.updates_filter
import app.koyura.features.resources.updates_load_failed
import app.koyura.features.resources.updates_refresh
import app.koyura.features.resources.updates_retry
import app.koyura.features.resources.updates_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

const val UpdatesRootTestTag = "updates-root"
const val UpdatesListTag = "updates-list"
const val UpdatesEmptyTag = "updates-empty"
const val UpdatesErrorTag = "updates-error"
const val UpdatesRefreshTag = "updates-refresh"

fun updatesWorkTag(sourceKey: String, workId: String) = "updates-work-$sourceKey-$workId"
fun updatesChapterTag(sourceKey: String, workId: String, chapterId: String) =
    "updates-chapter-$sourceKey-$workId-$chapterId"

/**
 * 更新页：按作品聚合的新章节组（白皮书 07 §7.7）。
 *
 * 首行是领导者（48dp 封面 + 标题 + 未读数），后续章节行缩进对齐标题
 * （Code: UpdatesUiItem.kt，领导者行顶 8dp + 纵向 4dp + 横向 16dp，章节行以前导占位
 * Box 对齐）。空态复用 EmptyScreen 规范（白皮书 06 §6.8）。
 *
 * 数据侧只有 Fake 仓储：筛选与日历本期禁用（随真实更新引擎启用），刷新真实重读。
 * 章节点击打开作品详情（详情内可进章节），直达阅读器的章节级跳转待真实源后接。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    session: UpdatesSession,
    onOpenWork: (sourceKey: String, workId: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    coverLoader: CoverImageLoader? = LocalCoverImageLoader.current,
    coverDecoder: CoverDecoder? = LocalCoverDecoder.current,
) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        session.refresh()
    }

    CompositionLocalProvider(
        LocalCoverImageLoader provides coverLoader,
        LocalCoverDecoder provides coverDecoder,
    ) {
        Column(modifier = modifier.fillMaxSize().testTag(UpdatesRootTestTag)) {
            TopAppBar(
                title = { Text(stringResource(Res.string.updates_title)) },
                actions = {
                    // 筛选与日历随真实更新引擎启用（本期 Fake 数据无可筛维度）。
                    ActionIconButton(
                        icon = ActionIcon.Filter,
                        label = stringResource(Res.string.updates_filter),
                        onClick = {},
                        enabled = false,
                    )
                    ActionIconButton(
                        icon = ActionIcon.Calendar,
                        label = stringResource(Res.string.updates_calendar),
                        onClick = {},
                        enabled = false,
                    )
                    ActionIconButton(
                        icon = ActionIcon.Sync,
                        label = stringResource(Res.string.updates_refresh),
                        onClick = { scope.launch { session.refresh() } },
                        modifier = Modifier.testTag(UpdatesRefreshTag),
                    )
                },
            )

            when (val state = session.loadState) {
                UpdatesLoadState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is UpdatesLoadState.Failed -> Column(
                    modifier = Modifier.fillMaxSize()
                        .padding(DesignTokens.Spacing.Large)
                        .testTag(UpdatesErrorTag),
                    verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
                ) {
                    Text(
                        text = stringResource(Res.string.updates_load_failed, state.message),
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { scope.launch { session.refresh() } }) {
                        Text(stringResource(Res.string.updates_retry))
                    }
                }
                UpdatesLoadState.Ready -> {
                    session.actionError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .padding(
                                    horizontal = DesignTokens.Spacing.Medium,
                                    vertical = DesignTokens.Spacing.Small,
                                )
                                .testTag("updates-action-error"),
                        )
                    }
                    if (session.entries.isEmpty()) {
                        UpdatesEmptyState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag(UpdatesListTag),
                        ) {
                            items(session.entries, key = { "${it.sourceKey}-${it.workId}" }) { work ->
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
    }
}

/** 空态：displayMedium 颜文字（0.78）+ bodyMedium 文案（白皮书 06 §6.8）。 */
@Composable
private fun UpdatesEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize()
            .padding(horizontal = DesignTokens.Spacing.Large)
            .testTag(UpdatesEmptyTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "(˘･_･˘)",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = DesignTokens.Alpha.Secondary,
            ),
        )
        Text(
            text = stringResource(Res.string.updates_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = DesignTokens.Alpha.Secondary,
            ),
        )
    }
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
            Text(
                text = work.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.updates_chapters, work.chapters.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
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
                        MaterialTheme.colorScheme.primary,
                        CircleShape,
                    )
                    .width(DesignTokens.Spacing.Small)
                    .aspectRatio(1f),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = chapter.dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = DesignTokens.Alpha.Secondary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
