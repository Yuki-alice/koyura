package app.koyura.app.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
            modifier = modifier.fillMaxSize().padding(24.dp).testTag(ExtensionDetailsMissingTag),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.extension_details_missing),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onBack) {
                Text(stringResource(Res.string.extension_details_title))
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
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            app.koyura.designsystem.IconPlaceholder(label = extension.name)
            Text(
                extension.name,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = extension.packageName.substringAfter("eu.kanade.tachiyomi.extension."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
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
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { confirmUninstall = true },
                enabled = status !is OperationStatus.Running,
                modifier = Modifier.weight(1f).testTag(ExtensionDetailsUninstallTag),
            ) {
                Text(stringResource(Res.string.extension_details_uninstall))
            }
            if (extension.hasUpdate) {
                Button(
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
                    Text(
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

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

        app.koyura.designsystem.ListGroupHeader(
            text = stringResource(Res.string.extension_details_sources),
        )
        if (extension.sources.isEmpty()) {
            Text(
                text = stringResource(Res.string.extension_details_no_sources),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            extension.sources.forEach { source ->
                Text(
                    source,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
            }
        }
    }

    if (confirmTrust) {
        AlertDialog(
            onDismissRequest = { confirmTrust = false },
            title = {
                Text(stringResource(Res.string.extension_trust_nsfw_title))
            },
            text = { Text(stringResource(Res.string.extension_trust_nsfw_body, extension.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmTrust = false
                        scope.launch { session.update(extension.key) }
                    },
                    modifier = Modifier.testTag("extension-details-trust-confirm"),
                ) { Text(stringResource(Res.string.extension_trust_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTrust = false }) {
                    Text(stringResource(Res.string.stores_cancel))
                }
            },
        )
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = {
                Text(stringResource(Res.string.extension_details_uninstall_confirm_title, extension.name))
            },
            text = { Text(stringResource(Res.string.extension_details_uninstall_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUninstall = false
                        scope.launch {
                            if (session.uninstall(extension.key)) onBack()
                        }
                    },
                    modifier = Modifier.testTag("extension-details-uninstall-confirm"),
                ) { Text(stringResource(Res.string.extension_details_uninstall)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) {
                    Text(stringResource(Res.string.stores_cancel))
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
        Text(
            text = primaryText,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = secondaryText,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
