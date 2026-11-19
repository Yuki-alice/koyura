package app.koyura.features.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.koyura.designsystem.DesignTokens
import app.koyura.features.resources.Res
import app.koyura.features.resources.account_continue
import app.koyura.features.resources.account_create
import app.koyura.features.resources.account_login
import app.koyura.features.resources.account_login_failed
import app.koyura.features.resources.account_login_title
import app.koyura.features.resources.account_logout
import app.koyura.features.resources.account_logout_confirm_body
import app.koyura.features.resources.account_logout_confirm_title
import app.koyura.features.resources.account_password_login_disabled
import app.koyura.features.resources.account_relogin
import app.koyura.features.resources.account_relogin_subtitle
import app.koyura.features.resources.account_section_title
import app.koyura.features.resources.account_web_login
import app.koyura.features.resources.account_web_login_failed
import app.koyura.features.resources.account_web_login_running
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

fun accountSectionTag(sourceKey: String) = "account-section-$sourceKey"
fun accountLoginToggleTag(sourceKey: String) = "account-login-toggle-$sourceKey"
fun accountFieldTag(sourceKey: String, index: Int) = "account-field-$sourceKey-$index"
fun accountContinueTag(sourceKey: String) = "account-continue-$sourceKey"
fun accountWebLoginTag(sourceKey: String) = "account-weblogin-$sourceKey"
fun accountRegisterTag(sourceKey: String) = "account-register-$sourceKey"
fun accountLogoutTag(sourceKey: String) = "account-logout-$sourceKey"
fun accountLogoutConfirmTag(sourceKey: String) = "account-logout-confirm-$sourceKey"
fun accountReloginTag(sourceKey: String) = "account-relogin-$sourceKey"

/**
 * 按源账号区（Venera `_buildAccount` 分支）。
 *
 * `NotConfigured` 时整块不出现；未登录给登录行（点开展开内联表单），
 * 已登录给 info 行 + 重登/注销行。平台相关反馈经回调交由装配层
 * （网页进行中/失败提示、注册外链），本页只做纯 UI 与会话驱动。
 */
@Composable
fun AccountSection(
    sourceKey: String,
    sourceName: String,
    repository: AccountRepository,
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit = {},
    onLoadingChange: (Boolean) -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    val session = rememberAccountSession(repository, sourceKey)
    LaunchedEffect(session) {
        session.refresh()
    }
    if (session.state is AccountState.NotConfigured) return
    // 所有需组合上下文的字符串先解析，回调里只用纯值。
    val loginFailedText = stringResource(Res.string.account_login_failed)
    val webFailedText = stringResource(Res.string.account_web_login_failed)

    Column(modifier = modifier.testTag(accountSectionTag(sourceKey))) {
        app.koyura.designsystem.ListGroupHeader(
            text = stringResource(Res.string.account_section_title),
        )
        when (val state = session.state) {
            AccountState.NotConfigured -> Unit
            is AccountState.LoggedOut -> AccountLoggedOut(
                sourceKey = sourceKey,
                sourceName = sourceName,
                state = state,
                onLogin = { values ->
                    if (!session.login(values)) onMessage(loginFailedText)
                },
                onWebLogin = {
                    onLoadingChange(true)
                    try {
                        if (!session.webLogin()) onMessage(webFailedText)
                    } finally {
                        onLoadingChange(false)
                    }
                },
                webLoggingIn = session.webLoggingIn,
                onOpenUrl = onOpenUrl,
            )
            is AccountState.LoggedIn -> AccountLoggedIn(
                sourceKey = sourceKey,
                state = state,
                lastFields = session.lastFields,
                onRelogin = { values ->
                    session.login(values)
                },
                onLogout = {
                    session.logout()
                },
            )
        }
        session.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("account-error-$sourceKey"),
            )
        }
    }
}

@Composable
private fun AccountLoggedOut(
    sourceKey: String,
    sourceName: String,
    state: AccountState.LoggedOut,
    onLogin: suspend (List<String>) -> Unit,
    onWebLogin: suspend () -> Unit,
    webLoggingIn: Boolean,
    onOpenUrl: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable(sourceKey) { mutableStateOf(false) }

    AccountRow(
        title = stringResource(Res.string.account_login),
        subtitle = sourceName,
        onClick = { expanded = !expanded },
        modifier = Modifier.testTag(accountLoginToggleTag(sourceKey)),
    )
    if (!expanded) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.account_login_title, sourceName),
            style = MaterialTheme.typography.titleMedium,
        )
        AccountLoginForm(
            sourceKey = sourceKey,
            fields = state.fields,
            onSubmit = { values ->
                scope.launch { onLogin(values) }
            },
        )
        if (state.registerUrl != null) {
            val registerUrl = state.registerUrl
            TextButton(
                onClick = { onOpenUrl(registerUrl) },
                modifier = Modifier.testTag(accountRegisterTag(sourceKey)),
            ) {
                Text(stringResource(Res.string.account_create))
            }
        }
        if (state.webLoginSupported) {
            if (webLoggingIn) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(Res.string.account_web_login_running),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TextButton(
                    onClick = {
                        scope.launch { onWebLogin() }
                    },
                    modifier = Modifier.testTag(accountWebLoginTag(sourceKey)),
                ) {
                    Text(stringResource(Res.string.account_web_login))
                }
            }
        }
    }
}

/** 按 `fields` 动态渲染的登录表单（TEXT/PASSWORD/COOKIES 三种输入形态）。 */
@Composable
private fun AccountLoginForm(
    sourceKey: String,
    fields: List<AccountFieldConfig>,
    onSubmit: (List<String>) -> Unit,
) {
    val values = remember(sourceKey, fields) { mutableStateOf(List(fields.size) { "" }) }
    if (fields.isEmpty()) {
        Text(
            text = stringResource(Res.string.account_password_login_disabled),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    fields.forEachIndexed { index, field ->
        OutlinedTextField(
            value = values.value[index],
            onValueChange = { input ->
                values.value = values.value.toMutableList().also { it[index] = input }
            },
            label = { Text(field.label) },
            singleLine = true,
            visualTransformation = if (field.type == AccountFieldType.TEXT) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            modifier = Modifier.fillMaxWidth().testTag(accountFieldTag(sourceKey, index)),
        )
    }
    Button(
        onClick = { onSubmit(values.value) },
        enabled = values.value.all { it.isNotBlank() },
        modifier = Modifier.testTag(accountContinueTag(sourceKey)),
    ) {
        Text(stringResource(Res.string.account_continue))
    }
}

@Composable
private fun AccountLoggedIn(
    sourceKey: String,
    state: AccountState.LoggedIn,
    lastFields: List<AccountFieldConfig>,
    onRelogin: suspend (List<String>) -> Boolean,
    onLogout: suspend () -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    var relogging by rememberSaveable(sourceKey) { mutableStateOf(false) }

    state.infoItems.forEachIndexed { index, item ->
        AccountRow(
            title = item.title,
            subtitle = item.value,
            onClick = null,
            modifier = Modifier.testTag("account-info-$sourceKey-$index"),
        )
    }
    if (state.formLogin) {
        AccountRow(
            title = stringResource(Res.string.account_relogin),
            subtitle = stringResource(Res.string.account_relogin_subtitle),
            onClick = { relogging = !relogging },
            modifier = Modifier.testTag(accountReloginTag(sourceKey)),
        )
        if (relogging && lastFields.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                AccountLoginForm(
                    sourceKey = sourceKey,
                    fields = lastFields,
                    onSubmit = { values ->
                        scope.launch {
                            if (onRelogin(values)) relogging = false
                        }
                    },
                )
            }
        }
    }
    AccountRow(
        title = stringResource(Res.string.account_logout),
        subtitle = null,
        onClick = { confirmLogout = true },
        modifier = Modifier.testTag(accountLogoutTag(sourceKey)),
    )
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(Res.string.account_logout_confirm_title, "")) },
            text = { Text(stringResource(Res.string.account_logout_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        scope.launch { onLogout() }
                    },
                    modifier = Modifier.testTag(accountLogoutConfirmTag(sourceKey)),
                ) {
                    Text(stringResource(Res.string.account_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text(stringResource(Res.string.account_continue))
                }
            },
        )
    }
}

@Composable
private fun AccountRow(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = DesignTokens.Alpha.Secondary,
                    ),
                )
            }
        }
    }
}
