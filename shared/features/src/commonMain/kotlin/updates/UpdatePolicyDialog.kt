package app.koyura.features.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppAlertDialog
import app.koyura.designsystem.components.AppSwitch
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.AppTextField
import app.koyura.designsystem.components.UiTheme
import app.koyura.features.resources.Res
import app.koyura.features.resources.library_cancel
import app.koyura.features.resources.updates_policy_enabled
import app.koyura.features.resources.updates_policy_enabled_subtitle
import app.koyura.features.resources.updates_policy_excluded_hint
import app.koyura.features.resources.updates_policy_excluded_title
import app.koyura.features.resources.updates_policy_save
import app.koyura.features.resources.updates_policy_skip_completed
import app.koyura.features.resources.updates_policy_skip_completed_subtitle
import app.koyura.features.resources.updates_policy_skip_not_started
import app.koyura.features.resources.updates_policy_skip_not_started_subtitle
import app.koyura.features.resources.updates_policy_skip_outside_window
import app.koyura.features.resources.updates_policy_skip_outside_window_subtitle
import app.koyura.features.resources.updates_policy_skip_unread
import app.koyura.features.resources.updates_policy_skip_unread_subtitle
import app.koyura.features.resources.updates_smart_settings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

const val UpdatePolicyDialogTag = "updates-policy-dialog"
const val UpdatePolicyExcludedTag = "updates-policy-excluded"
const val UpdatePolicyConfirmTag = "updates-policy-confirm"
const val UpdatePolicyCancelTag = "updates-policy-cancel"

fun updatePolicySwitchTag(key: String) = "updates-policy-switch-$key"

/**
 * 智能更新设置对话框（Updates 顶栏溢出入口）。
 *
 * 开关组与引擎 `SmartUpdatePreferences` 一一对应；标题排除词是多行输入（每行一个，
 * 也接受逗号分隔），保存时按引擎同一口径归一化（去空白、丢空项、按小写去重）。
 */
@Composable
fun UpdatePolicyDialog(
    session: UpdatePolicySession,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by remember(session.policy) { mutableStateOf(session.policy) }
    var excludedText by remember(session.policy) {
        mutableStateOf(session.policy.excludedTitleTerms.joinToString("\n"))
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(UpdatePolicyDialogTag),
        title = { AppText(stringResource(Res.string.updates_smart_settings)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = DesignTokens.Breakpoints.UpdatePolicyMaxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
            ) {
                PolicySwitchRow(
                    key = "enabled",
                    title = stringResource(Res.string.updates_policy_enabled),
                    subtitle = stringResource(Res.string.updates_policy_enabled_subtitle),
                    checked = draft.enabled,
                    onCheckedChange = { draft = draft.copy(enabled = it) },
                )
                PolicySwitchRow(
                    key = "skipCompleted",
                    title = stringResource(Res.string.updates_policy_skip_completed),
                    subtitle = stringResource(Res.string.updates_policy_skip_completed_subtitle),
                    checked = draft.skipCompleted,
                    onCheckedChange = { draft = draft.copy(skipCompleted = it) },
                )
                PolicySwitchRow(
                    key = "skipWithUnread",
                    title = stringResource(Res.string.updates_policy_skip_unread),
                    subtitle = stringResource(Res.string.updates_policy_skip_unread_subtitle),
                    checked = draft.skipWithUnread,
                    onCheckedChange = { draft = draft.copy(skipWithUnread = it) },
                )
                PolicySwitchRow(
                    key = "skipNotStarted",
                    title = stringResource(Res.string.updates_policy_skip_not_started),
                    subtitle = stringResource(Res.string.updates_policy_skip_not_started_subtitle),
                    checked = draft.skipNotStarted,
                    onCheckedChange = { draft = draft.copy(skipNotStarted = it) },
                )
                PolicySwitchRow(
                    key = "skipOutsideReleasePeriod",
                    title = stringResource(Res.string.updates_policy_skip_outside_window),
                    subtitle = stringResource(Res.string.updates_policy_skip_outside_window_subtitle),
                    checked = draft.skipOutsideReleasePeriod,
                    onCheckedChange = { draft = draft.copy(skipOutsideReleasePeriod = it) },
                )
                AppText(
                    text = stringResource(Res.string.updates_policy_excluded_title),
                    style = UiTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = DesignTokens.Spacing.Small),
                )
                AppTextField(
                    value = excludedText,
                    onValueChange = { excludedText = it },
                    placeholder = { AppText(stringResource(Res.string.updates_policy_excluded_hint)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag(UpdatePolicyExcludedTag),
                )
                session.actionError?.let { message ->
                    AppText(
                        text = message,
                        color = UiTheme.colorScheme.error,
                        style = UiTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            AppTextButton(
                onClick = {
                    val terms = excludedText
                        .split('\n', ',', '，')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    scope.launch {
                        if (session.apply(draft.copy(excludedTitleTerms = terms))) onDismiss()
                    }
                },
                modifier = Modifier.testTag(UpdatePolicyConfirmTag),
            ) {
                AppText(stringResource(Res.string.updates_policy_save))
            }
        },
        dismissButton = {
            AppTextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(UpdatePolicyCancelTag),
            ) {
                AppText(stringResource(Res.string.library_cancel))
            }
        },
    )
}

/** 开关行：标题 + 12sp/0.78 副标题 + 行尾 Switch，整行可点。 */
@Composable
private fun PolicySwitchRow(
    key: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = DesignTokens.Spacing.ExtraSmall)
            .testTag(updatePolicySwitchTag(key)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = title,
                style = UiTheme.typography.bodyLarge,
                color = UiTheme.colorScheme.onSurface,
            )
            AppText(
                text = subtitle,
                style = UiTheme.typography.bodySmall,
                color = UiTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = DesignTokens.Alpha.Secondary,
                ),
            )
        }
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = DesignTokens.Spacing.Small),
        )
    }
}
