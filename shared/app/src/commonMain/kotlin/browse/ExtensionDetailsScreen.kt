package app.koyura.app.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.koyura.app.resources.Res
import app.koyura.app.resources.extension_details_language_title
import app.koyura.app.resources.extension_details_missing
import app.koyura.app.resources.extension_details_no_sources
import app.koyura.app.resources.extension_details_sources
import app.koyura.app.resources.extension_details_store_title
import app.koyura.app.resources.extension_details_title
import app.koyura.app.resources.extension_details_uninstall
import app.koyura.app.resources.extension_details_uninstall_confirm_body
import app.koyura.app.resources.extension_details_uninstall_confirm_title
import app.koyura.app.resources.extension_details_update
import app.koyura.app.resources.extension_details_version_title
import app.koyura.app.resources.extension_trust_confirm
import app.koyura.app.resources.extension_trust_nsfw_body
import app.koyura.app.resources.extension_trust_nsfw_title
import app.koyura.app.resources.stores_cancel
import app.koyura.designsystem.DesignTokens
import app.koyura.designsystem.components.AppAlertDialog
import app.koyura.designsystem.components.AppButton
import app.koyura.designsystem.components.AppDivider
import app.koyura.designsystem.components.AppOutlinedButton
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.UiTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal const val ExtensionDetailsTestTag = "extension-details"
internal const val ExtensionDetailsUninstallTag = "extension-details-uninstall"
internal const val ExtensionDetailsUpdateTag = "extension-details-update"
internal const val ExtensionDetailsMissingTag = "extension-details-missing"

/** Installed extension details reached from the extension list. */
@Composable
fun ExtensionDetailsScreen(
    session: BrowseSession,
    extensionKey: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    accountRepository: app.koyura.features.account.AccountRepository? =
        app.koyura.features.account.LocalAccountRepository.current,
) {
    val state = session.state
    val extension = state.extensionByKey(extensionKey)
    val scope = rememberCoroutineScope()
    var confirmUninstall by remember { mutableStateOf(false) }
    // NSFW extensions need an explicit trust tap before updating (Komikku's trust/confirm
    // gating, text-only until the permission/trust panel lands with the platform installers).
    var confirmTrust by remember { mutableStateOf(false) }

    if (extension == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(DesignTokens.Spacing.Large).testTag(ExtensionDetailsMissingTag),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Regular),
        ) {
            AppText(
                text = stringResource(Res.string.extension_details_missing),
                style = UiTheme.typography.bodyMedium,
            )
            AppButton(onClick = onBack) {
                AppText(stringResource(Res.string.extension_details_title))
            }
        }
        return
    }

    val status = state.itemOperations[extension.key]

    // Komikku ExtensionDetailsScreen DetailsHeader: centered icon, name, package, a
    // version/language/store info row, then uninstall/update actions, then the source list.
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(ExtensionDetailsTestTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.Spacing.Medium)
                .padding(top = DesignTokens.Spacing.Medium, bottom = DesignTokens.Spacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            app.koyura.designsystem.IconPlaceholder(label = extension.name)
            AppText(
                extension.name,
                style = UiTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            AppText(
                text = extension.packageName.substringAfter("eu.kanade.tachiyomi.extension."),
                style = UiTheme.typography.bodySmall,
                color = UiTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.Spacing.ExtraLarge, vertical = DesignTokens.Spacing.Small),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExtensionInfoText(
                primaryText = extension.versionName.ifEmpty { "—" },
                secondaryText = stringResource(Res.string.extension_details_version_title),
                modifier = Modifier.weight(1f),
            )
            ExtensionInfoText(
                primaryText = extension.language.ifEmpty { "—" },
                secondaryText = stringResource(Res.string.extension_details_language_title),
                modifier = Modifier.weight(1f),
            )
            ExtensionInfoText(
                primaryText = extension.storeName.ifEmpty { "—" },
                secondaryText = stringResource(Res.string.extension_details_store_title),
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .padding(horizontal = DesignTokens.Spacing.Medium)
                .padding(top = DesignTokens.Spacing.Small),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Regular),
        ) {
            AppOutlinedButton(
                onClick = { confirmUninstall = true },
                enabled = status !is OperationStatus.Running,
                modifier = Modifier.weight(1f).testTag(ExtensionDetailsUninstallTag),
            ) {
                AppText(stringResource(Res.string.extension_details_uninstall))
            }
            if (extension.hasUpdate) {
                AppButton(
                    onClick = {
                        if (extension.isNsfw) {
                            confirmTrust = true
                        } else {
                            scope.launch { session.update(extension.key) }
                        }
                    },
                    enabled = status !is OperationStatus.Running,
                    modifier = Modifier.weight(1f).testTag(ExtensionDetailsUpdateTag),
                ) {
                    AppText(
                        stringResource(
                            Res.string.extension_details_update,
                            extension.availableVersionName ?: extension.versionName,
                        ),
                    )
                }
            }
        }

        OperationFeedback(
            status = status,
            target = extension.key,
        )

        AppDivider(modifier = Modifier.padding(top = DesignTokens.Spacing.Small))

        app.koyura.designsystem.ListGroupHeader(
            text = stringResource(Res.string.extension_details_sources),
        )
        if (extension.sources.isEmpty()) {
            AppText(
                text = stringResource(Res.string.extension_details_no_sources),
                style = UiTheme.typography.bodyMedium,
                color = UiTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small),
            )
        } else {
            extension.sources.forEach { source ->
                AppText(
                    source.name,
                    style = UiTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small),
                )
                AppDivider()
            }
        }

        // 按源账号区：未配置的源整块不出现（Venera `_buildAccount` 同）。
        // 网页进行中/失败提示与注册外链走反馈宿主（SourceUiHost）。
        val accountRepo = accountRepository
            ?: remember { app.koyura.features.account.FakeAccountRepository() }
        val uiHost = app.koyura.app.sourceui.LocalSourceUiHost.current
        extension.sources.forEach { source ->
            key(source.key) {
                app.koyura.features.account.AccountSection(
                    sourceKey = source.key,
                    sourceName = source.name,
                    repository = accountRepo,
                    onMessage = { message ->
                        uiHost?.showMessage(source.key, message)
                    },
                    onLoadingChange = { running ->
                        val id = source.key.hashCode()
                        if (running) {
                            uiHost?.showLoading(source.key, id, onCancel = null)
                        } else {
                            uiHost?.cancelLoading(source.key, id)
                        }
                    },
                    onOpenUrl = { url ->
                        uiHost?.launchUrl(source.key, url)
                    },
                )
                AppDivider()
            }
        }
    }

    if (confirmTrust) {
        AppAlertDialog(
            onDismissRequest = { confirmTrust = false },
            title = {
                AppText(stringResource(Res.string.extension_trust_nsfw_title))
            },
            text = { AppText(stringResource(Res.string.extension_trust_nsfw_body, extension.name)) },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        confirmTrust = false
                        scope.launch { session.update(extension.key) }
                    },
                    modifier = Modifier.testTag("extension-details-trust-confirm"),
                ) { AppText(stringResource(Res.string.extension_trust_confirm)) }
            },
            dismissButton = {
                AppTextButton(onClick = { confirmTrust = false }) {
                    AppText(stringResource(Res.string.stores_cancel))
                }
            },
        )
    }

    if (confirmUninstall) {
        AppAlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = {
                AppText(stringResource(Res.string.extension_details_uninstall_confirm_title, extension.name))
            },
            text = { AppText(stringResource(Res.string.extension_details_uninstall_confirm_body)) },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        confirmUninstall = false
                        scope.launch {
                            if (session.uninstall(extension.key)) onBack()
                        }
                    },
                    modifier = Modifier.testTag("extension-details-uninstall-confirm"),
                ) { AppText(stringResource(Res.string.extension_details_uninstall)) }
            },
            dismissButton = {
                AppTextButton(onClick = { confirmUninstall = false }) {
                    AppText(stringResource(Res.string.stores_cancel))
                }
            },
        )
    }
}

@Composable
private fun ExtensionInfoText(
    primaryText: String,
    secondaryText: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppText(
            text = primaryText,
            textAlign = TextAlign.Center,
            style = UiTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AppText(
            text = secondaryText,
            textAlign = TextAlign.Center,
            style = UiTheme.typography.bodyMedium,
            // 次级文字 onSurfaceVariant × 0.78（白皮书 02 §2.5 语义色；Browse 来源副行同式）。
            color = UiTheme.colorScheme.onSurfaceVariant.copy(
                alpha = DesignTokens.Alpha.Secondary,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
