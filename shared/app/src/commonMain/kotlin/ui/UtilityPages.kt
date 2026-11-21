package app.koyura.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.koyura.app.resources.Res
import app.koyura.app.resources.utility_about_body
import app.koyura.app.resources.utility_back
import app.koyura.app.resources.utility_batch_add_action
import app.koyura.app.resources.utility_batch_hint
import app.koyura.app.resources.utility_build
import app.koyura.app.resources.utility_help_body
import app.koyura.app.resources.utility_no_errors
import app.koyura.app.resources.utility_retry
import app.koyura.app.resources.utility_storage_body
import app.koyura.app.resources.utility_storage_format
import app.koyura.app.resources.utility_version
import app.koyura.designsystem.ActionIcon
import app.koyura.designsystem.ActionIconButton
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppButton
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextField
import app.koyura.designsystem.components.EmptyScreen
import app.koyura.designsystem.BrandPlaceholder
import app.koyura.designsystem.ListGroupHeader
import app.koyura.designsystem.components.UiTheme
import app.koyura.features.library.LocalLibraryRepository
import app.koyura.features.library.NewLibraryEntry
import app.koyura.features.updates.LocalUpdatesRepository
import app.koyura.features.updates.UpdateFailure
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

const val UtilityRootTag = "utility-root"

/**
 * More 页的五个工具页（白皮书 07 §7.10）；每个 key 都有真实数据源，不再统一颜文字占位。
 *
 * - `update-errors`：`UpdatesRepository.failures()` + 重试。
 * - `data-storage`：`LibraryRepository.load()` 的条目/分类计数。
 * - `batch-add`：逐行 `sourceKey|workId|title` 写入真实书架。
 * - `about`：版本 / 构建 / 许可说明。
 * - `help`：三条上手路径。
 *
 * 未知 key 才退回空态，避免"入口存在但页面空白"。
 */
@Composable
fun UtilityPageScreen(
    key: String,
    title: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(UiTheme.colorScheme.surface)
            .testTag(UtilityRootTag),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DesignTokens.Sizes.TopBarHeight)
                .padding(horizontal = DesignTokens.Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIconButton(
                icon = ActionIcon.Back,
                label = stringResource(Res.string.utility_back),
                onClick = onBack,
            )
            AppText(
                text = title,
                style = UiTheme.typography.titleLarge,
                color = UiTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = DesignTokens.Spacing.Small),
            )
        }
        androidx.compose.material3.HorizontalDivider(
            thickness = DesignTokens.Spacing.Hairline,
            color = UiTheme.colorScheme.outlineVariant,
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (key) {
                "update-errors" -> UpdateErrorsPage()
                "data-storage" -> DataStoragePage()
                "batch-add" -> BatchAddPage()
                "about" -> AboutUtilityPage()
                "help" -> HelpUtilityPage()
                else -> EmptyScreen(
                    emoji = "(・∀・)",
                    message = title,
                    actionLabel = stringResource(Res.string.utility_back),
                    onAction = onBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun UpdateErrorsPage() {
    val repository = LocalUpdatesRepository.current
    var retryToken by remember { mutableStateOf(0) }
    val failures by produceState<List<UpdateFailure>>(emptyList(), repository, retryToken) {
        value = runCatching { repository?.failures() }.getOrNull().orEmpty()
    }
    val scope = rememberCoroutineScope()
    if (failures.isEmpty()) {
        EmptyScreen(
            emoji = "(・∀・)",
            message = stringResource(Res.string.utility_no_errors),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ListGroupHeader(text = "${failures.size}")
        failures.forEach { failure ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small),
            ) {
                AppText(
                    text = failure.title.ifEmpty { failure.workId },
                    style = UiTheme.typography.bodyMedium,
                )
                AppText(
                    text = failure.message,
                    style = UiTheme.typography.bodySmall,
                    color = UiTheme.colorScheme.error,
                )
            }
        }
        AppButton(
            onClick = {
                scope.launch {
                    runCatching { repository?.retryFailures() }
                    retryToken++
                }
            },
            modifier = Modifier.padding(DesignTokens.Spacing.Medium),
        ) { AppText(stringResource(Res.string.utility_retry)) }
    }
}

@Composable
private fun DataStoragePage() {
    val repository = LocalLibraryRepository.current
    val snapshot by produceState<app.koyura.features.library.LibrarySnapshot?>(null, repository) {
        value = runCatching { repository?.load() }.getOrNull()
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DesignTokens.Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Medium),
    ) {
        AppText(
            text = stringResource(
                Res.string.utility_storage_format,
                snapshot?.entries?.size ?: 0,
                snapshot?.categories?.size ?: 0,
            ),
            style = UiTheme.typography.headlineSmall,
        )
        AppText(
            text = stringResource(Res.string.utility_storage_body),
            style = UiTheme.typography.bodyMedium,
            color = UiTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BatchAddPage() {
    val repository = LocalLibraryRepository.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DesignTokens.Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Medium),
    ) {
        AppText(
            text = stringResource(Res.string.utility_batch_hint),
            style = UiTheme.typography.bodyMedium,
            color = UiTheme.colorScheme.onSurfaceVariant,
        )
        AppTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth().height(DesignTokens.Sizes.CatalogInputMinHeight),
        )
        AppButton(
            onClick = {
                val lines = input.lines().map { it.trim() }.filter { it.isNotEmpty() }
                scope.launch {
                    var added = 0
                    for (line in lines) {
                        val parts = line.split('|').map { it.trim() }
                        if (parts.size < 2) continue
                        val ok = runCatching {
                            repository?.addToLibrary(
                                NewLibraryEntry(
                                    sourceKey = parts[0],
                                    workId = parts[1],
                                    title = parts.getOrNull(2).orEmpty(),
                                ),
                            )
                        }.isSuccess
                        if (ok) added++
                    }
                    result = "$added / ${lines.size}"
                    input = ""
                }
            },
            enabled = repository != null && input.isNotBlank(),
        ) { AppText(stringResource(Res.string.utility_batch_add_action)) }
        if (result != null) {
            AppText(text = result!!, style = UiTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun AboutUtilityPage() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DesignTokens.Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
    ) {
        BrandPlaceholder(modifier = Modifier.size(DesignTokens.Sizes.BrandLogo))
        Spacer(Modifier.height(DesignTokens.Spacing.Medium))
        AppText(text = "Koyura", style = UiTheme.typography.headlineSmall)
        AppText(
            text = "${stringResource(Res.string.utility_version)} 1.0.0",
            style = UiTheme.typography.bodyMedium,
            color = UiTheme.colorScheme.onSurfaceVariant,
        )
        AppText(
            text = stringResource(Res.string.utility_build),
            style = UiTheme.typography.bodySmall,
            color = UiTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Medium))
        AppText(
            text = stringResource(Res.string.utility_about_body),
            style = UiTheme.typography.bodyMedium,
            color = UiTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HelpUtilityPage() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DesignTokens.Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
    ) {
        stringResource(Res.string.utility_help_body).lines().forEach { line ->
            if (line.isNotBlank()) {
                AppText(text = "· $line", style = UiTheme.typography.bodyMedium)
            }
        }
    }
}
