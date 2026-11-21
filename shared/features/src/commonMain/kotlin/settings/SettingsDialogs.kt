package app.koyura.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppAlertDialog
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.SettingsItem
import app.koyura.features.resources.Res
import app.koyura.features.resources.settings_back

/**
 * 设置子页的通用单选弹层：选项直接落成 [SettingsItem]，选中项右侧打勾。
 *
 * 用自适应对话框（`AppAlertDialog`）而不是底部 Sheet，因为设置项在任何窗口宽度下都应是
 * 居中确认框；白皮书 06 §6.13 的底部 Sheet 是内容选择器，不是设置项选择。
 */
@Composable
internal fun SettingsChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().heightIn(max = DesignTokens.Breakpoints.DialogMaxWidth),
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (label, value) ->
                    SettingsItem(
                        title = label,
                        selected = value == selectedValue,
                        control = if (value == selectedValue) {
                            { AppText("✓", color = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                        onClick = { onSelect(value) },
                    )
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss) {
                AppText(org.jetbrains.compose.resources.stringResource(Res.string.settings_back))
            }
        },
    )
}

/** 设置子页的说明/结果弹层（未接入能力的如实说明，不伪造执行结果）。 */
@Composable
internal fun SettingsMessageDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(
                text = message,
                modifier = Modifier.padding(top = DesignTokens.Spacing.Small),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss) {
                AppText(org.jetbrains.compose.resources.stringResource(Res.string.settings_back))
            }
        },
    )
}
