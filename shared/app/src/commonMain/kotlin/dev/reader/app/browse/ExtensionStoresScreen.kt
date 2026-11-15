package dev.reader.app.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.app.resources.Res
import dev.reader.app.resources.browse_action_install
import dev.reader.app.resources.browse_empty_stores
import dev.reader.app.resources.browse_operation_failed
import dev.reader.app.resources.browse_tab_extensions
import dev.reader.app.resources.stores_add
import dev.reader.app.resources.stores_add_title
import dev.reader.app.resources.stores_cancel
import dev.reader.app.resources.stores_close_preview
import dev.reader.app.resources.stores_count
import dev.reader.app.resources.stores_disabled
import dev.reader.app.resources.stores_edit
import dev.reader.app.resources.stores_edit_title
import dev.reader.app.resources.stores_enabled
import dev.reader.app.resources.stores_name_field
import dev.reader.app.resources.stores_preview
import dev.reader.app.resources.stores_preview_count
import dev.reader.app.resources.stores_preview_empty
import dev.reader.app.resources.stores_preview_title
import dev.reader.app.resources.stores_refresh
import dev.reader.app.resources.stores_remove
import dev.reader.app.resources.stores_remove_body
import dev.reader.app.resources.stores_remove_confirm
import dev.reader.app.resources.stores_remove_title
import dev.reader.app.resources.stores_save
import dev.reader.app.resources.stores_title
import dev.reader.app.resources.stores_url_duplicate
import dev.reader.app.resources.stores_url_empty
import dev.reader.app.resources.stores_url_field
import dev.reader.app.resources.stores_url_invalid
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal const val StoresScreenTestTag = "stores-root"
internal const val StoresAddTag = "stores-add"
internal const val StoresListTag = "stores-list"
internal const val StoresErrorTag = "stores-error"
internal const val StoreRemoveConfirmTag = "store-remove-confirm"
internal const val CatalogDialogTag = "store-catalog-dialog"

internal fun storeRowTag(id: String) = "store-row-$id"
internal fun storeToggleTag(id: String) = "store-toggle-$id"
internal fun storeEditTag(id: String) = "store-edit-$id"
internal fun storeRemoveTag(id: String) = "store-remove-$id"
internal fun storePreviewTag(id: String) = "store-preview-$id"

/** Full store management: add, edit, enable/disable, remove and catalog preview. */
@Composable
fun ExtensionStoresScreen(
    session: BrowseSession,
    initialStoreId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = session.state
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<StoreDialog?>(null) }
    var previewStoreId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialStoreId, state.load) {
        if (initialStoreId != null && state.load is LoadState.Ready) {
            val store = state.storeById(initialStoreId)
            if (store != null && dialog == null) {
                dialog = StoreDialog.Edit(store)
                previewStoreId = initialStoreId
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().testTag(StoresScreenTestTag)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(Res.string.stores_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                TextButton(onClick = onBack, modifier = Modifier.testTag("stores-back")) {
                    Text(stringResource(Res.string.browse_tab_extensions))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { scope.launch { session.refresh() } },
                    modifier = Modifier.testTag("stores-refresh"),
                ) { Text(stringResource(Res.string.stores_refresh)) }
                Button(
                    onClick = { dialog = StoreDialog.Add },
                    modifier = Modifier.testTag(StoresAddTag),
                ) { Text(stringResource(Res.string.stores_add)) }
            }
        }

        when (val load = state.load) {
            LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            LoadState.Unavailable -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.browse_empty_stores),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is LoadState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp).testTag(StoresErrorTag),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.browse_operation_failed, load.message),
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = { scope.launch { session.refresh() } }) {
                    Text(stringResource(Res.string.stores_refresh))
                }
            }
            LoadState.Ready -> if (state.stores.isEmpty()) {
                EmptyState(stringResource(Res.string.browse_empty_stores))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().testTag(StoresListTag)) {
                    items(state.stores, key = { it.id }) { store ->
                        StoreRow(
                            store = store,
                            status = state.storeOperations[store.id],
                            onToggle = { enabled -> scope.launch { session.setStoreEnabled(store.id, enabled) } },
                            onEdit = { dialog = StoreDialog.Edit(store) },
                            onRemove = { dialog = StoreDialog.Remove(store) },
                            onPreview = {
                                previewStoreId = store.id
                                scope.launch { session.previewStore(store.id) }
                            },
                        )
                    }
                }
            }
        }
    }

    when (val current = dialog) {
        null -> Unit
        StoreDialog.Add -> StoreEditDialog(
            title = stringResource(Res.string.stores_add_title),
            initialName = "",
            initialUrl = "",
            stores = state.stores,
            status = state.storeOperations[AddStoreTarget],
            onDismiss = { dialog = null; session.clearStatus(AddStoreTarget) },
            onConfirm = { name, url ->
                scope.launch {
                    val ok = session.addStore(name, url)
                    if (ok) {
                        dialog = null
                        session.clearStatus(AddStoreTarget)
                    }
                }
            },
        )
        is StoreDialog.Edit -> StoreEditDialog(
            title = stringResource(Res.string.stores_edit_title),
            initialName = current.store.name,
            initialUrl = current.store.indexUrl,
            stores = state.stores,
            editingStoreId = current.store.id,
            status = state.storeOperations[current.store.id],
            onDismiss = { dialog = null },
            onConfirm = { name, url ->
                scope.launch {
                    val ok = session.saveStore(current.store.id, name, url)
                    if (ok) {
                        dialog = null
                        session.clearStatus(current.store.id)
                    }
                }
            },
        )
        is StoreDialog.Remove -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(Res.string.stores_remove_title, current.store.name)) },
            text = { Text(stringResource(Res.string.stores_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val ok = session.removeStore(current.store.id)
                            if (ok) dialog = null
                        }
                    },
                    modifier = Modifier.testTag(StoreRemoveConfirmTag),
                ) { Text(stringResource(Res.string.stores_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) {
                    Text(stringResource(Res.string.stores_cancel))
                }
            },
        )
    }

    previewStoreId?.let { storeId ->
        val store = state.storeById(storeId)
        if (store != null) {
            StoreCatalogDialog(
                store = store,
                catalogState = state.catalogStates[storeId],
                onRetry = { scope.launch { session.previewStore(storeId) } },
                onInstall = { packageName ->
                    val key = extensionKey(storeId, packageName)
                    scope.launch { session.install(key) }
                },
                onDismiss = {
                    previewStoreId = null
                    session.clearStoreCatalog(storeId)
                },
            )
        }
    }
}

@Composable
private fun StoreRow(
    store: ExtensionStoreInfo,
    status: OperationStatus?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
) {
    val running = status is OperationStatus.Running
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(storeRowTag(store.id))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = store.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = store.indexUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.stores_count, store.extensionCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        if (store.enabled) Res.string.stores_enabled else Res.string.stores_disabled,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (store.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Switch(
                checked = store.enabled,
                onCheckedChange = onToggle,
                enabled = !running,
                modifier = Modifier.testTag(storeToggleTag(store.id)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = onPreview,
                enabled = !running,
                modifier = Modifier.testTag(storePreviewTag(store.id)),
            ) { Text(stringResource(Res.string.stores_preview)) }
            TextButton(
                onClick = onEdit,
                enabled = !running,
                modifier = Modifier.testTag(storeEditTag(store.id)),
            ) { Text(stringResource(Res.string.stores_edit)) }
            TextButton(
                onClick = onRemove,
                enabled = !running,
                modifier = Modifier.testTag(storeRemoveTag(store.id)),
            ) { Text(stringResource(Res.string.stores_remove)) }
        }
        OperationFeedback(status, store.id)
    }
    HorizontalDivider()
}

@Composable
private fun StoreEditDialog(
    title: String,
    initialName: String,
    initialUrl: String,
    stores: List<ExtensionStoreInfo>,
    status: OperationStatus?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit,
    editingStoreId: String? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var showErrors by remember { mutableStateOf(false) }
    val running = status is OperationStatus.Running
    val urlError = validateStoreUrl(url, stores, editingStoreId)

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.stores_name_field)) },
                    singleLine = true,
                    enabled = !running,
                    modifier = Modifier.testTag("store-name-field"),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(Res.string.stores_url_field)) },
                    singleLine = true,
                    enabled = !running,
                    isError = showErrors && urlError != null,
                    modifier = Modifier.testTag("store-url-field"),
                )
                if (showErrors && urlError != null) {
                    Text(
                        text = when (urlError) {
                            StoreUrlError.Empty -> stringResource(Res.string.stores_url_empty)
                            StoreUrlError.InvalidScheme -> stringResource(Res.string.stores_url_invalid)
                            StoreUrlError.Duplicate -> stringResource(Res.string.stores_url_duplicate)
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("store-url-error"),
                    )
                }
                OperationFeedback(status, editingStoreId ?: AddStoreTarget)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    showErrors = true
                    if (urlError == null) onConfirm(name, url)
                },
                enabled = !running,
                modifier = Modifier.testTag("store-save"),
            ) { Text(stringResource(Res.string.stores_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !running) {
                Text(stringResource(Res.string.stores_cancel))
            }
        },
    )
}

@Composable
private fun StoreCatalogDialog(
    store: ExtensionStoreInfo,
    catalogState: CatalogState?,
    onRetry: () -> Unit,
    onInstall: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(CatalogDialogTag),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.stores_preview_title))
                Text(
                    text = store.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            when (catalogState) {
                null, CatalogState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                is CatalogState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.browse_operation_failed, catalogState.message),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.testTag("catalog-retry"),
                    ) {
                        Text(stringResource(Res.string.stores_refresh))
                    }
                }
                is CatalogState.Ready -> {
                    val entries = catalogState.catalog.entries
                    if (entries.isEmpty()) {
                        Text(stringResource(Res.string.stores_preview_empty))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(Res.string.stores_preview_count, entries.size),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                            ) {
                                items(entries, key = { it.packageName }) { entry ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                text = "${entry.versionName} · ${entry.language}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        TextButton(
                                            onClick = { onInstall(entry.packageName) },
                                            modifier = Modifier.testTag("catalog-install-${entry.packageName}"),
                                        ) { Text(stringResource(Res.string.browse_action_install)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("catalog-close")) {
                Text(stringResource(Res.string.stores_close_preview))
            }
        },
    )
}

private sealed interface StoreDialog {
    data object Add : StoreDialog
    data class Edit(val store: ExtensionStoreInfo) : StoreDialog
    data class Remove(val store: ExtensionStoreInfo) : StoreDialog
}
