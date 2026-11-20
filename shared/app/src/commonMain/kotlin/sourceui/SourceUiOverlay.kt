package app.koyura.app.sourceui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import app.koyura.app.resources.Res
import app.koyura.app.resources.source_ui_cancel
import app.koyura.app.resources.source_ui_dialog_confirm
import app.koyura.app.resources.source_ui_input_hint
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppAlertDialog
import app.koyura.designsystem.components.AppLinearProgress
import app.koyura.designsystem.components.AppRadioButton
import app.koyura.designsystem.components.AppSnackbarHost
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.AppTextField
import app.koyura.designsystem.components.UiTheme
import app.koyura.designsystem.components.rememberAppSnackbarHostState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** 未装配宿主时（测试/预览）回退为空实现：调用无呈现、无崩溃。 */
val LocalSourceUiHost = staticCompositionLocalOf<SourceUiHost?> { null }

const val SourceUiLoadingTag = "source-ui-loading"
const val SourceUiSnackbarTag = "source-ui-snackbar"
const val SourceUiDialogTag = "source-ui-dialog"
const val SourceUiDialogInputTag = "source-ui-dialog-input"
const val SourceUiDialogErrorTag = "source-ui-dialog-error"

fun sourceUiDialogActionTag(index: Int) = "source-ui-dialog-action-$index"
fun sourceUiDialogOptionTag(index: Int) = "source-ui-dialog-option-$index"

/**
 * 脚本 UI 顶层呈现：加载条（顶）+ snackbar（底）+ 挂起对话框（中）。
 *
 * 对话框视觉按白皮书 06 §6.7（AlertDialog 居中、24dp 内边距）与 08 反馈层级；
 * 输入框校验失败行内标红（error 色 + 文案 + 重试入口即修正后重提）。
 */
@Composable
fun SourceUiOverlay(
    host: SourceUiHost,
    modifier: Modifier = Modifier,
) {
    val loading by host.loading.collectAsState()
    val message by host.message.collectAsState()
    val dialog by host.dialog.collectAsState()
    val snackbarState = rememberAppSnackbarHostState()

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarState.showSnackbar(current.text)
        host.consumeMessage(current.id)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (loading.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .testTag(SourceUiLoadingTag),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppLinearProgress(modifier = Modifier.weight(1f))
                if (loading.values.any { it != null }) {
                    AppTextButton(onClick = { host.cancelAllLoading() }) {
                        AppText(stringResource(Res.string.source_ui_cancel))
                    }
                }
            }
        }

        AppSnackbarHost(
            hostState = snackbarState,
            modifier = Modifier.align(Alignment.BottomCenter).testTag(SourceUiSnackbarTag),
        )

        when (val request = dialog) {
            is SourceUiDialog.Message -> SourceMessageDialog(request = request)
            is SourceUiDialog.Input -> SourceInputDialog(request = request)
            is SourceUiDialog.Select -> SourceSelectDialog(request = request)
            null -> Unit
        }
    }
}

@Composable
private fun SourceMessageDialog(request: SourceUiDialog.Message) {
    val scope = rememberCoroutineScope()
    AppAlertDialog(
        onDismissRequest = {
            scope.launch {
                request.gate.complete(Unit)
            }
        },
        modifier = Modifier.testTag(SourceUiDialogTag),
        title = { AppText(request.title) },
        text = { AppText(request.content) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall)) {
                request.actions.forEachIndexed { index, action ->
                    AppTextButton(
                        onClick = {
                            scope.launch {
                                request.onAction(index)
                                request.gate.complete(Unit)
                            }
                        },
                        modifier = Modifier.testTag(sourceUiDialogActionTag(index)),
                    ) {
                        AppText(action.text)
                    }
                }
            }
        },
    )
}

@Composable
private fun SourceInputDialog(request: SourceUiDialog.Input) {
    val scope = rememberCoroutineScope()
    var input by remember(request) { mutableStateOf("") }
    var error by remember(request) { mutableStateOf<String?>(null) }
    var checking by remember(request) { mutableStateOf(false) }

    fun dismiss(result: String?) {
        if (!request.gate.isCompleted) request.gate.complete(result)
    }

    Dialog(onDismissRequest = { dismiss(null) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SourceUiDialogTag)
                .padding(DesignTokens.Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
        ) {
            AppText(text = request.title, style = UiTheme.typography.titleMedium)
            AppTextField(
                value = input,
                onValueChange = {
                    input = it
                    error = null
                },
                placeholder = { AppText(stringResource(Res.string.source_ui_input_hint)) },
                singleLine = true,
                isError = error != null,
                supportingText = {
                    error?.let {
                        AppText(
                            text = it,
                            color = UiTheme.colorScheme.error,
                            modifier = Modifier.testTag(SourceUiDialogErrorTag),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag(SourceUiDialogInputTag),
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppTextButton(onClick = { dismiss(null) }) {
                    AppText(stringResource(Res.string.source_ui_cancel))
                }
                AppTextButton(
                    onClick = {
                        scope.launch {
                            checking = true
                            try {
                                val verdict = request.validate?.invoke(input)
                                if (verdict != null) {
                                    error = verdict
                                } else {
                                    dismiss(input)
                                }
                            } finally {
                                checking = false
                            }
                        }
                    },
                    enabled = !checking,
                    modifier = Modifier.testTag(sourceUiDialogActionTag(0)),
                ) {
                    AppText(stringResource(Res.string.source_ui_dialog_confirm))
                }
            }
        }
    }
}

@Composable
private fun SourceSelectDialog(request: SourceUiDialog.Select) {
    fun dismiss(result: Int?) {
        if (!request.gate.isCompleted) request.gate.complete(result)
    }

    Dialog(onDismissRequest = { dismiss(null) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SourceUiDialogTag)
                .padding(DesignTokens.Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
        ) {
            AppText(text = request.title, style = UiTheme.typography.titleMedium)
            LazyColumn {
                itemsIndexed(request.options) { index, option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = request.initialIndex == index,
                                role = Role.RadioButton,
                                onClick = { dismiss(index) },
                            )
                            .testTag(sourceUiDialogOptionTag(index)),
                    ) {
                        AppRadioButton(
                            selected = request.initialIndex == index,
                            onClick = null,
                        )
                        AppText(text = option, style = UiTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
