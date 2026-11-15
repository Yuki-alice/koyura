package dev.reader.app.browse

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.resources.Res
import dev.reader.app.resources.extension_details_missing
import dev.reader.app.resources.extension_details_no_sources
import dev.reader.app.resources.extension_details_package
import dev.reader.app.resources.extension_details_sources
import dev.reader.app.resources.extension_details_store
import dev.reader.app.resources.extension_details_title
import dev.reader.app.resources.extension_details_uninstall
import dev.reader.app.resources.extension_details_uninstall_confirm_body
import dev.reader.app.resources.extension_details_uninstall_confirm_title
import dev.reader.app.resources.extension_details_update
import dev.reader.app.resources.extension_details_version
import dev.reader.app.resources.stores_cancel
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag(ExtensionDetailsTestTag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.extension_details_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(extension.name, style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(Res.string.extension_details_package, extension.packageName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.extension_details_version, extension.versionName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(Res.string.extension_details_store, extension.storeName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        Text(
            text = stringResource(Res.string.extension_details_sources),
            style = MaterialTheme.typography.titleMedium,
        )
        if (extension.sources.isEmpty()) {
            Text(
                text = stringResource(Res.string.extension_details_no_sources),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            extension.sources.forEach { source ->
                Text(source, style = MaterialTheme.typography.bodyMedium)
            }
        }

        OperationFeedback(status, extension.key)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (extension.hasUpdate) {
                Button(
                    onClick = { scope.launch { session.update(extension.key) } },
                    enabled = status !is OperationStatus.Running,
                    modifier = Modifier.testTag(ExtensionDetailsUpdateTag),
                ) {
                    Text(
                        stringResource(
                            Res.string.extension_details_update,
                            extension.availableVersionName ?: extension.versionName,
                        ),
                    )
                }
            }
            OutlinedButton(
                onClick = { confirmUninstall = true },
                enabled = status !is OperationStatus.Running,
                modifier = Modifier.testTag(ExtensionDetailsUninstallTag),
            ) {
                Text(stringResource(Res.string.extension_details_uninstall))
            }
        }
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
