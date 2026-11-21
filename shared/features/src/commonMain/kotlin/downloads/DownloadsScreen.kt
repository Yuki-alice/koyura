package app.koyura.features.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.DecorativeActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.EmptyScreen
import app.koyura.designsystem.components.ErrorScreen
import app.koyura.designsystem.components.LoadingScreen
import app.koyura.designsystem.components.AppCircularProgress
import app.koyura.designsystem.components.AppExtendedFab
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.ReaderTopAppBar
import app.koyura.designsystem.components.UiTheme
import app.koyura.features.resources.Res
import app.koyura.features.resources.downloads_cancel
import app.koyura.features.resources.downloads_cancel_all
import app.koyura.features.resources.downloads_empty
import app.koyura.features.resources.downloads_load_failed
import app.koyura.features.resources.downloads_pause
import app.koyura.features.resources.downloads_pause_all
import app.koyura.features.resources.downloads_progress_percent
import app.koyura.features.resources.downloads_resume
import app.koyura.features.resources.downloads_resume_all
import app.koyura.features.resources.downloads_retry
import app.koyura.features.resources.downloads_retry_load
import app.koyura.features.resources.downloads_status_downloading
import app.koyura.features.resources.downloads_status_failed
import app.koyura.features.resources.downloads_status_paused
import app.koyura.features.resources.downloads_status_queued
import app.koyura.features.resources.downloads_title
import org.jetbrains.compose.resources.stringResource

const val DownloadsRootTestTag = "downloads-root"
const val DownloadsListTag = "downloads-list"
const val DownloadsEmptyTag = "downloads-empty"
const val DownloadsErrorTag = "downloads-error"
const val DownloadsFabTag = "downloads-fab"
const val DownloadsCancelAllTag = "downloads-cancel-all"

fun downloadRowTag(id: String) = "downloads-row-$id"
fun downloadPauseTag(id: String) = "downloads-pause-$id"
fun downloadResumeTag(id: String) = "downloads-resume-$id"
fun downloadRetryTag(id: String) = "downloads-retry-$id"
fun downloadCancelTag(id: String) = "downloads-cancel-$id"

/**
 * 下载队列页（白皮书 07 §7.12，子页形态：返回 + 标题）。
 *
 * 行结构：漫画名 + 章节名 + 线性进度条（上方 8dp）+ 右侧状态动作；
 * 右下暂停/恢复 FAB 只在非空时出现。状态机由 [DownloadsStore] 承载
 * （与旧 `DownloadsSession` 同语义）；渲染读不可变 [DownloadsState]。
 */
@Composable
fun DownloadsScreen(
    holder: DownloadsStoreHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by holder.uiState.collectAsState()

    LaunchedEffect(Unit) {
        holder.store.accept(DownloadsIntent.Refresh)
    }

    Column(modifier = modifier.fillMaxSize().testTag(DownloadsRootTestTag)) {
        ReaderTopAppBar(
            title = { AppText(stringResource(Res.string.downloads_title)) },
            navigationIcon = {
                ActionIconButton(
                    icon = ActionIcon.Back,
                    label = stringResource(Res.string.downloads_title),
                    onClick = onBack,
                )
            },
            actions = {
                ActionIconButton(
                    icon = ActionIcon.Delete,
                    label = stringResource(Res.string.downloads_cancel_all),
                    onClick = { holder.store.accept(DownloadsIntent.Clear) },
                    enabled = uiState.entries.isNotEmpty(),
                    modifier = Modifier.testTag(DownloadsCancelAllTag),
                )
            },
        )

        when (val state = uiState.loadState) {
            DownloadQueueLoadState.Loading -> LoadingScreen()
            is DownloadQueueLoadState.Failed -> ErrorScreen(
                message = stringResource(Res.string.downloads_load_failed, state.message),
                modifier = Modifier.testTag(DownloadsErrorTag),
            ) {
                AppTextButton(onClick = { holder.store.accept(DownloadsIntent.Refresh) }) {
                    AppText(stringResource(Res.string.downloads_retry_load))
                }
            }
            DownloadQueueLoadState.Ready -> {
                uiState.actionError?.let { message ->
                    AppText(
                        text = message,
                        color = UiTheme.colorScheme.error,
                        style = UiTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(
                                horizontal = DesignTokens.Spacing.Medium,
                                vertical = DesignTokens.Spacing.Small,
                            )
                            .testTag("downloads-action-error"),
                    )
                }
                if (uiState.entries.isEmpty()) {
                    DownloadsEmptyState()
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag(DownloadsListTag),
                            contentPadding = PaddingValues(vertical = DesignTokens.Spacing.Small),
                        ) {
                            items(uiState.entries, key = { it.id }) { task ->
                                DownloadRow(
                                    task = task,
                                    onPause = { holder.store.accept(DownloadsIntent.Pause(task.id)) },
                                    onResume = { holder.store.accept(DownloadsIntent.Resume(task.id)) },
                                    onRetry = { holder.store.accept(DownloadsIntent.Retry(task.id)) },
                                    onCancel = { holder.store.accept(DownloadsIntent.Cancel(task.id)) },
                                )
                            }
                        }
                        AppExtendedFab(
                            compact = true,
                            text = {
                                AppText(
                                    stringResource(
                                        if (uiState.running) Res.string.downloads_pause_all
                                        else Res.string.downloads_resume_all,
                                    ),
                                )
                            },
                            icon = {
                                DecorativeActionIcon(
                icon = if (uiState.running) ActionIcon.Pause else ActionIcon.Play,
                                )
                            },
                            onClick = {
                                holder.store.accept(
                                    if (uiState.running) DownloadsIntent.PauseAll else DownloadsIntent.ResumeAll,
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(DesignTokens.Spacing.Medium)
                                .testTag(DownloadsFabTag),
                        )
                    }
                }
            }
        }
    }
}

/** 空态：displayMedium 颜文字（0.78）+ bodyMedium 文案（白皮书 06 §6.8）。 */
@Composable
private fun DownloadsEmptyState() {
    EmptyScreen(
        emoji = "(︶︿︶)",
        message = stringResource(Res.string.downloads_empty),
        modifier = Modifier.testTag(DownloadsEmptyTag),
    )
}

/** 队列行：漫画名 + 章节名 + 线性进度条（上方 8dp）+ 右侧状态动作。 */
@Composable
private fun DownloadRow(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(downloadRowTag(task.id))
            .padding(
                horizontal = DesignTokens.Spacing.Medium,
                vertical = DesignTokens.Spacing.Small,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
        ) {
            AppText(
                text = task.workTitle,
                style = UiTheme.typography.bodyMedium,
                color = UiTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = task.chapterTitle,
                style = UiTheme.typography.bodySmall,
                color = UiTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = DesignTokens.Alpha.Secondary,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.totalPages > 0) {
                val progress = (task.downloadedPages.toFloat() / task.totalPages).coerceIn(0f, 1f)
                val progressColor = if (task.status == FakeDownloadStatus.FAILED) {
                    UiTheme.colorScheme.error
                } else {
                    UiTheme.colorScheme.primary
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = DesignTokens.Spacing.ExtraSmall),
                ) {
                    AppCircularProgress(
                        progress = { progress },
                        modifier = Modifier.size(DesignTokens.Sizes.DownloadIndicator),
                        strokeWidth = DesignTokens.Sizes.DownloadProgressStroke,
                        color = progressColor,
                    )
                    AppText(
                        text = stringResource(Res.string.downloads_progress_percent, (progress * 100).toInt()),
                        style = UiTheme.typography.labelMedium,
                        color = progressColor,
                    )
                }
            }
            AppText(
                text = downloadStatusText(task),
                style = UiTheme.typography.bodySmall,
                color = if (task.status == FakeDownloadStatus.FAILED) {
                    UiTheme.colorScheme.error
                } else {
                    UiTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = DesignTokens.Alpha.Secondary,
                    )
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (task.status) {
            FakeDownloadStatus.QUEUED, FakeDownloadStatus.DOWNLOADING -> ActionIconButton(
                icon = ActionIcon.Pause,
                label = stringResource(Res.string.downloads_pause),
                onClick = onPause,
                modifier = Modifier.testTag(downloadPauseTag(task.id)),
            )
            FakeDownloadStatus.PAUSED -> Row {
                ActionIconButton(
                    icon = ActionIcon.Play,
                    label = stringResource(Res.string.downloads_resume),
                    onClick = onResume,
                    modifier = Modifier.testTag(downloadResumeTag(task.id)),
                )
                ActionIconButton(
                    icon = ActionIcon.Close,
                    label = stringResource(Res.string.downloads_cancel),
                    onClick = onCancel,
                    modifier = Modifier.testTag(downloadCancelTag(task.id)),
                )
            }
            FakeDownloadStatus.FAILED -> Row {
                ActionIconButton(
                    icon = ActionIcon.Sync,
                    label = stringResource(Res.string.downloads_retry),
                    onClick = onRetry,
                    modifier = Modifier.testTag(downloadRetryTag(task.id)),
                )
                ActionIconButton(
                    icon = ActionIcon.Close,
                    label = stringResource(Res.string.downloads_cancel),
                    onClick = onCancel,
                    modifier = Modifier.testTag(downloadCancelTag(task.id)),
                )
            }
        }
    }
}

@Composable
private fun downloadStatusText(task: DownloadTask): String = when (task.status) {
    FakeDownloadStatus.QUEUED -> stringResource(Res.string.downloads_status_queued)
    FakeDownloadStatus.DOWNLOADING -> stringResource(
        Res.string.downloads_status_downloading,
        task.downloadedPages,
        task.totalPages,
    )
    FakeDownloadStatus.PAUSED -> stringResource(Res.string.downloads_status_paused)
    FakeDownloadStatus.FAILED -> stringResource(Res.string.downloads_status_failed)
}
