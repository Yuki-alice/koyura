package app.koyura.app.browse

import app.koyura.designsystem.DesignTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import app.koyura.app.resources.Res
import app.koyura.app.resources.browse_action_install
import app.koyura.app.resources.browse_empty_stores
import app.koyura.app.resources.browse_operation_failed
import app.koyura.app.resources.browse_operation_succeeded
import app.koyura.app.resources.browse_retry
import app.koyura.app.resources.browse_tab_extensions
import app.koyura.app.resources.stores_add
import app.koyura.app.resources.stores_add_title
import app.koyura.app.resources.stores_cancel
import app.koyura.app.resources.stores_close_preview
import app.koyura.app.resources.stores_count
import app.koyura.app.resources.stores_disabled
import app.koyura.app.resources.stores_edit
import app.koyura.app.resources.stores_edit_title
import app.koyura.app.resources.stores_enabled
import app.koyura.app.resources.stores_name_field
import app.koyura.app.resources.stores_preview
import app.koyura.app.resources.stores_preview_count
import app.koyura.app.resources.stores_preview_empty
import app.koyura.app.resources.stores_preview_title
import app.koyura.app.resources.stores_refresh
import app.koyura.app.resources.stores_remove
import app.koyura.app.resources.stores_remove_body
import app.koyura.app.resources.stores_remove_confirm
import app.koyura.app.resources.stores_remove_title
import app.koyura.app.resources.stores_save
import app.koyura.app.resources.stores_title
import app.koyura.app.resources.stores_url_duplicate
import app.koyura.app.resources.stores_url_empty
import app.koyura.app.resources.stores_url_field
import app.koyura.app.resources.stores_url_invalid
import app.koyura.designsystem.components.AppAlertDialog
import app.koyura.designsystem.components.AppButton
import app.koyura.designsystem.components.AppCircularProgress
import app.koyura.designsystem.components.AppDivider
import app.koyura.designsystem.components.AppSwitch
import app.koyura.designsystem.components.AppText
import app.koyura.designsystem.components.AppTextButton
import app.koyura.designsystem.components.AppTextField
import app.koyura.designsystem.components.UiTheme
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Tight)) {
                AppText(
                    text = stringResource(Res.string.stores_title),
                    style = UiTheme.typography.headlineMedium,
                )
                AppTextButton(onClick = onBack, modifier = Modifier.testTag("stores-back")) {
                    AppText(stringResource(Res.string.browse_tab_extensions))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small)) {
                AppTextButton(
                    onClick = { scope.launch { session.refresh() } },
                    modifier = Modifier.testTag("stores-refresh"),
                ) { AppText(stringResource(Res.string.stores_refresh)) }
                AppButton(
                    onClick = { dialog = StoreDialog.Add },
                    modifier = Modifier.testTag(StoresAddTag),
                ) { AppText(stringResource(Res.string.stores_add)) }
            }
        }

        when (val load = state.load) {
            LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppCircularProgress()
            }
            LoadState.Unavailable -> Column(
                modifier = Modifier.fillMaxSize().padding(DesignTokens.Spacing.Large),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small),
            ) {
                AppText(
                    text = stringResource(Res.string.browse_empty_stores),
                    style = UiTheme.typography.bodyMedium,
                )
            }
            is LoadState.Failed -> Column(
                modifier = Modifier.fillMaxSize().padding(DesignTokens.Spacing.Large).testTag(StoresErrorTag),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Regular),
            ) {
                AppText(
                    text = stringResource(Res.string.browse_operation_failed, load.message),
                    color = UiTheme.colorScheme.error,
                )
                AppButton(onClick = { scope.launch { session.refresh() } }) {
                    AppText(stringResource(Res.string.stores_refresh))
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
        is StoreDialog.Remove -> AppAlertDialog(
            onDismissRequest = { dialog = null },
            title = { AppText(stringResource(Res.string.stores_remove_title, current.store.name)) },
            text = { AppText(stringResource(Res.string.stores_remove_body)) },
            confirmButton = {
                AppTextButton(
                    onClick = {
                        scope.launch {
                            val ok = session.removeStore(current.store.id)
                            if (ok) dialog = null
                        }
                    },
                    modifier = Modifier.testTag(StoreRemoveConfirmTag),
                ) { AppText(stringResource(Res.string.stores_remove_confirm)) }
            },
            dismissButton = {
                AppTextButton(onClick = { dialog = null }) {
                    AppText(stringResource(Res.string.stores_cancel))
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
                itemStatus = { variant -> state.itemOperations[variant.key] },
                onRetry = { scope.launch { session.previewStore(storeId) } },
                onInstall = { variant -> scope.launch { session.installVariant(variant) } },
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
            .padding(horizontal = DesignTokens.Spacing.Medium, vertical = DesignTokens.Spacing.Row),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Tight)) {
                AppText(
                    text = store.name,
                    style = UiTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    text = store.indexUrl,
                    style = UiTheme.typography.bodySmall,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(
                    text = stringResource(Res.string.stores_count, store.extensionCount),
                    style = UiTheme.typography.bodySmall,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                )
                AppText(
                    text = stringResource(
                        if (store.enabled) Res.string.stores_enabled else Res.string.stores_disabled,
                    ),
                    style = UiTheme.typography.labelSmall,
                    color = if (store.enabled) {
                        UiTheme.colorScheme.primary
                    } else {
                        UiTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            AppSwitch(
                checked = store.enabled,
                onCheckedChange = onToggle,
                enabled = !running,
                modifier = Modifier.testTag(storeToggleTag(store.id)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.ExtraSmall)) {
            AppTextButton(
                onClick = onPreview,
                enabled = !running,
                modifier = Modifier.testTag(storePreviewTag(store.id)),
            ) { AppText(stringResource(Res.string.stores_preview)) }
            AppTextButton(
                onClick = onEdit,
                enabled = !running,
                modifier = Modifier.testTag(storeEditTag(store.id)),
            ) { AppText(stringResource(Res.string.stores_edit)) }
            AppTextButton(
                onClick = onRemove,
                enabled = !running,
                modifier = Modifier.testTag(storeRemoveTag(store.id)),
            ) { AppText(stringResource(Res.string.stores_remove)) }
        }
        OperationFeedback(status, store.id)
    }
    AppDivider()
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

    AppAlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { AppText(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Regular)) {
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { AppText(stringResource(Res.string.stores_name_field)) },
                    singleLine = true,
                    enabled = !running,
                    modifier = Modifier.testTag("store-name-field"),
                )
                AppTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { AppText(stringResource(Res.string.stores_url_field)) },
                    singleLine = true,
                    enabled = !running,
                    isError = showErrors && urlError != null,
                    modifier = Modifier.testTag("store-url-field"),
                )
                if (showErrors && urlError != null) {
                    AppText(
                        text = when (urlError) {
                            StoreUrlError.Empty -> stringResource(Res.string.stores_url_empty)
                            StoreUrlError.InvalidScheme -> stringResource(Res.string.stores_url_invalid)
                            StoreUrlError.Duplicate -> stringResource(Res.string.stores_url_duplicate)
                        },
                        color = UiTheme.colorScheme.error,
                        style = UiTheme.typography.bodySmall,
                        modifier = Modifier.testTag("store-url-error"),
                    )
                }
                OperationFeedback(status, editingStoreId ?: AddStoreTarget)
            }
        },
        confirmButton = {
            AppTextButton(
                onClick = {
                    showErrors = true
                    if (urlError == null) onConfirm(name, url)
                },
                enabled = !running,
                modifier = Modifier.testTag("store-save"),
            ) { AppText(stringResource(Res.string.stores_save)) }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss, enabled = !running) {
                AppText(stringResource(Res.string.stores_cancel))
            }
        },
    )
}

@Composable
private fun StoreCatalogDialog(
    store: ExtensionStoreInfo,
    catalogState: CatalogState?,
    itemStatus: (CatalogVariantId) -> OperationStatus?,
    onRetry: () -> Unit,
    onInstall: (CatalogVariantId) -> Unit,
    onDismiss: () -> Unit,
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(CatalogDialogTag),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Tight)) {
                AppText(stringResource(Res.string.stores_preview_title))
                AppText(
                    text = store.name,
                    style = UiTheme.typography.bodySmall,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            when (catalogState) {
                null, CatalogState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = DesignTokens.Sizes.CatalogInputMinHeight),
                    contentAlignment = Alignment.Center,
                ) { AppCircularProgress() }
                is CatalogState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small)) {
                    AppText(
                        text = stringResource(Res.string.browse_operation_failed, catalogState.message),
                        color = UiTheme.colorScheme.error,
                    )
                    AppButton(
                        onClick = onRetry,
                        modifier = Modifier.testTag("catalog-retry"),
                    ) {
                        AppText(stringResource(Res.string.stores_refresh))
                    }
                }
                is CatalogState.Ready -> {
                    val refs = catalogState.catalog.variantRefs()
                    if (refs.isEmpty()) {
                        AppText(stringResource(Res.string.stores_preview_empty))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Small)) {
                            AppText(
                                text = stringResource(Res.string.stores_preview_count, refs.size),
                                style = UiTheme.typography.labelMedium,
                            )
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = DesignTokens.Breakpoints.CatalogResultsMaxHeight),
                            ) {
                                items(refs, key = { it.variant.key }) { ref ->
                                    CatalogRow(
                                        ref = ref,
                                        status = itemStatus(ref.variant),
                                        onInstall = { onInstall(ref.variant) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onDismiss, modifier = Modifier.testTag("catalog-close")) {
                AppText(stringResource(Res.string.stores_close_preview))
            }
        },
    )
}

@Composable
private fun CatalogRow(
    ref: CatalogEntryRef,
    status: OperationStatus?,
    onInstall: () -> Unit,
) {
    val entry = ref.entry
    val running = status is OperationStatus.Running
    Column(
        modifier = Modifier.fillMaxWidth().testTag("catalog-entry-${ref.variant.key}"),
        verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Tight),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.Spacing.Compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                AppText(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AppText(
                    text = listOfNotNull(
                        entry.versionName.takeIf { it.isNotEmpty() },
                        entry.language.takeIf { it.isNotEmpty() },
                        entry.sourceKey.takeIf { it.isNotEmpty() && it != entry.name },
                    ).joinToString(" · "),
                    style = UiTheme.typography.bodySmall,
                    color = UiTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.scriptUrl.isNotEmpty()) {
                    AppText(
                        text = entry.scriptUrl,
                        style = UiTheme.typography.labelSmall,
                        color = UiTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                running -> AppCircularProgress(
                    modifier = Modifier.size(DesignTokens.Spacing.Large),
                    strokeWidth = DesignTokens.Sizes.ProgressBarStroke,
                )
                status is OperationStatus.Failed -> AppTextButton(
                    onClick = onInstall,
                    modifier = Modifier.testTag("catalog-retry-${ref.variant.key}"),
                ) { AppText(stringResource(Res.string.browse_retry)) }
                status is OperationStatus.Succeeded -> AppText(
                    text = stringResource(Res.string.browse_operation_succeeded),
                    style = UiTheme.typography.labelMedium,
                    color = UiTheme.colorScheme.tertiary,
                )
                else -> AppTextButton(
                    onClick = onInstall,
                    modifier = Modifier.testTag("catalog-install-${ref.variant.key}"),
                ) { AppText(stringResource(Res.string.browse_action_install)) }
            }
        }
        OperationFeedback(status, ref.variant.key)
    }
    AppDivider()
}

private sealed interface StoreDialog {
    data object Add : StoreDialog
    data class Edit(val store: ExtensionStoreInfo) : StoreDialog
    data class Remove(val store: ExtensionStoreInfo) : StoreDialog
}
