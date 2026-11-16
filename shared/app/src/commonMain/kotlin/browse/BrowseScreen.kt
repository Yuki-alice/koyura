package app.koyura.app.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koyura.app.resources.Res
import app.koyura.app.resources.browse_action_install
import app.koyura.app.resources.browse_action_open
import app.koyura.app.resources.browse_action_uninstall
import app.koyura.app.resources.browse_action_update
import app.koyura.app.resources.browse_clear_search
import app.koyura.app.resources.browse_empty_extensions
import app.koyura.app.resources.browse_empty_sources
import app.koyura.app.resources.browse_error_prefix
import app.koyura.app.resources.browse_group_available
import app.koyura.app.resources.browse_group_installed
import app.koyura.app.resources.browse_group_updates
import app.koyura.app.resources.browse_language
import app.koyura.app.resources.browse_latest
import app.koyura.app.resources.browse_manage_stores
import app.koyura.app.resources.browse_nsfw_short
import app.koyura.app.resources.browse_operation_failed
import app.koyura.app.resources.browse_operation_running
import app.koyura.app.resources.browse_operation_succeeded
import app.koyura.app.resources.browse_retry
import app.koyura.app.resources.browse_search_extensions
import app.koyura.app.resources.browse_search_sources
import app.koyura.app.resources.browse_tab_extensions
import app.koyura.app.resources.browse_tab_sources
import app.koyura.app.resources.browse_title
import app.koyura.app.resources.browse_unavailable_body
import app.koyura.app.resources.browse_unavailable_title
import app.koyura.app.resources.browse_update_all
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal const val BrowseRootTestTag = "browse-root"
internal const val BrowseSourcesTabTag = "browse-tab-sources"
internal const val BrowseExtensionsTabTag = "browse-tab-extensions"
internal const val BrowseSourcesListTag = "browse-sources-list"
internal const val BrowseExtensionsListTag = "browse-extensions-list"
internal const val BrowseUnavailableTag = "browse-unavailable"
internal const val BrowseErrorTag = "browse-error"
internal const val BrowseEmptyTag = "browse-empty"
internal const val BrowseSourceQueryTag = "browse-source-query"
internal const val BrowseExtensionQueryTag = "browse-extension-query"
internal const val BrowseManageStoresTag = "browse-manage-stores"

internal fun extensionRowTag(key: String) = "browse-extension-$key"
internal fun extensionActionTag(key: String, action: String) = "browse-extension-$key-$action"

/**
 * Root page of the Browse tab: a two-page surface with Sources and Extensions.
 *
 * Page, query and scroll position live here and survive tab switches and the bar/rail width switch
 * because the whole Browse stack is kept by the shell's saveable state holder.
 */
@Composable
fun BrowseScreen(
    session: BrowseSession,
    onOpenExtension: (String) -> Unit,
    onManageStores: (String?) -> Unit,
    onOpenSource: (sourceKey: String, sourceName: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val state = session.state
    val scope = rememberCoroutineScope()
    val sourcesListState = rememberLazyListState()
    val extensionsListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (state.load is LoadState.Loading) session.refresh()
    }

    Column(modifier = modifier.fillMaxSize().testTag(BrowseRootTestTag)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.browse_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.pane == BrowsePane.Extensions) {
                    TextButton(
                        onClick = { scope.launch { session.updateAll() } },
                        enabled = state.updateCount > 0 && !state.isBusy(),
                        modifier = Modifier.testTag("browse-update-all"),
                    ) {
                        Text(stringResource(Res.string.browse_update_all))
                    }
                }
                TextButton(
                    onClick = { onManageStores(null) },
                    modifier = Modifier.testTag(BrowseManageStoresTag),
                ) {
                    Text(stringResource(Res.string.browse_manage_stores))
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = if (state.pane == BrowsePane.Sources) 0 else 1) {
            Tab(
                selected = state.pane == BrowsePane.Sources,
                onClick = { session.selectPane(BrowsePane.Sources) },
                text = { Text(stringResource(Res.string.browse_tab_sources)) },
                modifier = Modifier.testTag(BrowseSourcesTabTag),
            )
            Tab(
                selected = state.pane == BrowsePane.Extensions,
                onClick = { session.selectPane(BrowsePane.Extensions) },
                text = { Text(stringResource(Res.string.browse_tab_extensions)) },
                modifier = Modifier.testTag(BrowseExtensionsTabTag),
            )
        }

        when (val load = state.load) {
            LoadState.Unavailable -> UnavailableState()
            LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is LoadState.Failed -> ErrorState(load.message) { scope.launch { session.refresh() } }
            LoadState.Ready -> when (state.pane) {
                BrowsePane.Sources -> SourcesPane(
                    state = state,
                    listStateHolder = sourcesListState,
                    onQueryChange = session::searchSources,
                    onOpenSource = onOpenSource,
                )
                BrowsePane.Extensions -> ExtensionsPane(
                    session = session,
                    state = state,
                    listStateHolder = extensionsListState,
                    onOpenExtension = onOpenExtension,
                    onManageStores = onManageStores,
                    scope = scope,
                )
            }
        }
    }
}

@Composable
private fun UnavailableState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(BrowseUnavailableTag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.browse_unavailable_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(Res.string.browse_unavailable_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(BrowseErrorTag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.browse_error_prefix, message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onRetry, modifier = Modifier.testTag("browse-retry")) {
            Text(stringResource(Res.string.browse_retry))
        }
    }
}

@Composable
private fun SourcesPane(
    state: BrowseState,
    listStateHolder: androidx.compose.foundation.lazy.LazyListState,
    onQueryChange: (String) -> Unit,
    onOpenSource: (sourceKey: String, sourceName: String) -> Unit,
) {
    val sections = state.sources.groupedSources(state.sourceQuery)
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.sourceQuery,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(Res.string.browse_search_sources)) },
            singleLine = true,
            trailingIcon = if (state.sourceQuery.isNotEmpty()) {
                {
                    TextButton(onClick = { onQueryChange("") }) {
                        Text(stringResource(Res.string.browse_clear_search))
                    }
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(BrowseSourceQueryTag),
        )
        if (sections.isEmpty()) {
            EmptyState(stringResource(Res.string.browse_empty_sources))
            return@Column
        }
        LazyColumn(
            state = listStateHolder,
            modifier = Modifier.fillMaxSize().testTag(BrowseSourcesListTag),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            sections.forEach { section ->
                item(key = "source-header-${section.language}", contentType = "header") {
                    SectionHeader(stringResource(Res.string.browse_language, section.language))
                }
                items(section.sources, key = { it.id }, contentType = { "source" }) { source ->
                    SourceRow(source, onOpenSource)
                }
            }
        }
    }
}

@Composable
private fun ExtensionsPane(
    session: BrowseSession,
    state: BrowseState,
    listStateHolder: androidx.compose.foundation.lazy.LazyListState,
    onOpenExtension: (String) -> Unit,
    onManageStores: (String?) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val sections = state.extensions.groupedExtensions(state.extensionQuery)
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.extensionQuery,
            onValueChange = session::searchExtensions,
            placeholder = { Text(stringResource(Res.string.browse_search_extensions)) },
            singleLine = true,
            trailingIcon = if (state.extensionQuery.isNotEmpty()) {
                {
                    TextButton(onClick = { session.searchExtensions("") }) {
                        Text(stringResource(Res.string.browse_clear_search))
                    }
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(BrowseExtensionQueryTag),
        )
        if (state.isBusy()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("browse-busy"))
        }
        if (sections.isEmpty()) {
            EmptyState(
                text = if (state.extensions.isEmpty()) {
                    stringResource(Res.string.browse_empty_extensions)
                } else {
                    stringResource(Res.string.browse_empty_extensions)
                },
                action = {
                    TextButton(
                        onClick = { onManageStores(null) },
                        modifier = Modifier.testTag("browse-empty-stores"),
                    ) {
                        Text(stringResource(Res.string.browse_manage_stores))
                    }
                },
            )
            return@Column
        }
        LazyColumn(
            state = listStateHolder,
            modifier = Modifier.fillMaxSize().testTag(BrowseExtensionsListTag),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            sections.forEach { section ->
                item(key = "extension-header-${section.group}", contentType = "header") {
                    SectionHeader(
                        when (section.group) {
                            ExtensionGroup.Updates -> stringResource(Res.string.browse_group_updates)
                            ExtensionGroup.Installed -> stringResource(Res.string.browse_group_installed)
                            ExtensionGroup.Available -> stringResource(Res.string.browse_group_available)
                        },
                    )
                }
                items(section.extensions, key = { it.key }, contentType = { "extension" }) { extension ->
                    ExtensionRow(
                        extension = extension,
                        status = state.itemOperations[extension.key],
                        onOpen = { onOpenExtension(extension.key) },
                        onInstall = { scope.launch { session.install(extension.key) } },
                        onUpdate = { scope.launch { session.update(extension.key) } },
                        onUninstall = { scope.launch { session.uninstall(extension.key) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceInfo,
    onOpenSource: (sourceKey: String, sourceName: String) -> Unit,
) {
    // Komikku BaseSourceItem: 40 dp icon, bodyMedium title, secondary language/version line,
    // optional Latest action. The row keeps its test tag and opens the search page on tap.
    app.koyura.designsystem.KomikkuBrowseRow(
        onClick = { onOpenSource(source.id, source.name) },
        icon = {
            app.koyura.designsystem.KomikkuIconPlaceholder(label = source.name)
        },
        action = {
            if (source.supportsLatest) {
                TextButton(onClick = { onOpenSource(source.id, source.name) }) {
                    Text(stringResource(Res.string.browse_latest))
                }
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .weight(1f),
            ) {
                Text(
                    text = source.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = listOf(source.language, source.versionName)
                        .filter { it.isNotEmpty() }
                        .joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = app.koyura.designsystem.KomikkuSecondaryAlpha,
                    ),
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("source-row-${source.id}"),
    )
}

@Composable
private fun ExtensionRow(
    extension: ExtensionInfo,
    status: OperationStatus?,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onUninstall: () -> Unit,
) {
    // Komikku ExtensionItem: 40 dp icon slot with progress while Running, bodyMedium title,
    // secondary version/language/store line with update arrow and NSFW flag, text actions.
    // Test tags stay on the row and on each action so the install/update/uninstall flows keep
    // working while the row rhythm matches BaseBrowseItem.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(extensionRowTag(extension.key)),
    ) {
        app.koyura.designsystem.KomikkuBrowseRow(
            onClick = { if (extension.isInstalled) onOpen() },
            icon = {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (status is OperationStatus.Running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    app.koyura.designsystem.KomikkuIconPlaceholder(label = extension.name)
                }
            },
            action = {
                when {
                    extension.hasUpdate -> {
                        TextButton(
                            onClick = onUpdate,
                            enabled = status !is OperationStatus.Running,
                            modifier = Modifier.testTag(extensionActionTag(extension.key, "update")),
                        ) { Text(stringResource(Res.string.browse_action_update)) }
                    }
                    extension.isInstalled -> {
                        TextButton(
                            onClick = onUninstall,
                            enabled = status !is OperationStatus.Running,
                            modifier = Modifier.testTag(extensionActionTag(extension.key, "uninstall")),
                        ) { Text(stringResource(Res.string.browse_action_uninstall)) }
                    }
                    else -> {
                        TextButton(
                            onClick = onInstall,
                            enabled = status !is OperationStatus.Running,
                            modifier = Modifier.testTag(extensionActionTag(extension.key, "install")),
                        ) { Text(stringResource(Res.string.browse_action_install)) }
                    }
                }
                if (extension.isInstalled) {
                    TextButton(
                        onClick = onOpen,
                        modifier = Modifier.testTag(extensionActionTag(extension.key, "open")),
                    ) { Text(stringResource(Res.string.browse_action_open)) }
                }
            },
            content = {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = extension.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            extension.versionName.takeIf { it.isNotEmpty() },
                            extension.language.takeIf { it.isNotEmpty() },
                            "@${extension.storeName}".takeIf { extension.storeName.isNotEmpty() },
                            extension.availableVersionName
                                ?.takeIf { extension.hasUpdate }
                                ?.let { "→ $it" },
                            if (extension.isNsfw) {
                                stringResource(Res.string.browse_nsfw_short).uppercase()
                            } else {
                                null
                            },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (extension.isNsfw) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = app.koyura.designsystem.KomikkuSecondaryAlpha,
                            )
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OperationFeedback(status, extension.key)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    HorizontalDivider()
}

@Composable
internal fun OperationFeedback(status: OperationStatus?, target: String) {
    when (status) {
        null -> Unit
        OperationStatus.Running -> Text(
            text = stringResource(Res.string.browse_operation_running),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("operation-running-$target"),
        )
        OperationStatus.Succeeded -> Text(
            text = stringResource(Res.string.browse_operation_succeeded),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.testTag("operation-succeeded-$target"),
        )
        is OperationStatus.Failed -> Text(
            text = stringResource(Res.string.browse_operation_failed, status.message),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("operation-failed-$target"),
        )
    }
}

@Composable
internal fun SectionHeader(text: String) {
    // Komikku ListGroupHeader: semibold bodyMedium in onSurfaceVariant.
    app.koyura.designsystem.KomikkuListGroupHeader(text = text)
}

@Composable
internal fun EmptyState(text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(BrowseEmptyTag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}
