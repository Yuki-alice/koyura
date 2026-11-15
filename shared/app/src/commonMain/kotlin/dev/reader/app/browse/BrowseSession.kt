package dev.reader.app.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Holds the Browse tab's data and per-item operation state.
 *
 * Rules enforced here so every surface behaves the same:
 * - an operation on a target is ignored while that target is already running (no double submit);
 * - every mutating call increases a snapshot generation, and a response only replaces the list if no
 *   newer call started in the meantime (a slow old request cannot clobber a newer result);
 * - feedback is stored per target, so one row's failure never marks another row.
 */
class BrowseSession internal constructor(
    private val service: BrowseService?,
    initialState: BrowseState = BrowseState(),
) {
    var state by mutableStateOf(
        if (service == null) initialState.copy(load = LoadState.Unavailable) else initialState,
    )
        private set

    private var snapshotGeneration = 0L
    private val targetGeneration = mutableMapOf<String, Long>()

    private val busy: Boolean
        get() = state.itemOperations.values.any { it is OperationStatus.Running } ||
            state.storeOperations.values.any { it is OperationStatus.Running }

    suspend fun refresh() {
        val currentService = service
        if (currentService == null) {
            state = state.copy(load = LoadState.Unavailable)
            return
        }
        val generation = ++snapshotGeneration
        state = state.copy(load = LoadState.Loading)
        try {
            val snapshot = currentService.loadSnapshot()
            if (generation == snapshotGeneration) applySnapshot(snapshot, LoadState.Ready)
        } catch (error: Exception) {
            if (generation == snapshotGeneration) {
                state = state.copy(load = LoadState.Failed(error.userMessage()))
            }
        }
    }

    fun selectPane(pane: BrowsePane) {
        state = state.copy(pane = pane)
    }

    fun searchSources(query: String) {
        state = state.copy(sourceQuery = query)
    }

    fun searchExtensions(query: String) {
        state = state.copy(extensionQuery = query)
    }

    fun statusOf(target: String): OperationStatus? =
        state.itemOperations[target] ?: state.storeOperations[target]

    suspend fun install(extensionKey: String): Boolean =
        mutate(OperationKind.Install, extensionKey, storeTarget = false) {
            it.installExtension(extensionKey)
        }

    suspend fun update(extensionKey: String): Boolean =
        mutate(OperationKind.Update, extensionKey, storeTarget = false) {
            it.updateExtension(extensionKey)
        }

    suspend fun uninstall(extensionKey: String): Boolean =
        mutate(OperationKind.Uninstall, extensionKey, storeTarget = false) {
            it.uninstallExtension(extensionKey)
        }

    suspend fun updateAll(): Boolean =
        mutate(OperationKind.UpdateAll, UpdateAllTarget, storeTarget = false) {
            it.updateAllExtensions()
        }

    suspend fun addStore(name: String, indexUrl: String): Boolean =
        mutate(OperationKind.AddStore, AddStoreTarget, storeTarget = true) {
            it.addStore(indexUrl.trim())
        }

    suspend fun saveStore(storeId: String, name: String, indexUrl: String): Boolean =
        mutate(OperationKind.SaveStore, storeId, storeTarget = true) {
            it.saveStore(storeId, name.trim(), indexUrl.trim())
        }

    suspend fun removeStore(storeId: String): Boolean =
        mutate(OperationKind.RemoveStore, storeId, storeTarget = true) {
            it.removeStore(storeId)
        }

    suspend fun setStoreEnabled(storeId: String, enabled: Boolean): Boolean =
        mutate(OperationKind.ToggleStore, storeId, storeTarget = true) {
            it.setStoreEnabled(storeId, enabled)
        }

    /** Loads a store catalog for preview. Retries replace the previous attempt for that store. */
    suspend fun previewStore(storeId: String) {
        val currentService = service ?: return
        if (state.catalogStates[storeId] is CatalogState.Loading) return
        val token = (targetGeneration["catalog:$storeId"] ?: 0L) + 1
        targetGeneration["catalog:$storeId"] = token
        state = state.copy(catalogStates = state.catalogStates + (storeId to CatalogState.Loading))
        val next = try {
            CatalogState.Ready(currentService.previewStore(storeId))
        } catch (error: Exception) {
            CatalogState.Failed(error.userMessage())
        }
        if (targetGeneration["catalog:$storeId"] == token) {
            state = state.copy(catalogStates = state.catalogStates + (storeId to next))
        }
    }

    fun clearStoreCatalog(storeId: String) {
        state = state.copy(catalogStates = state.catalogStates - storeId)
    }

    fun clearStatus(target: String) {
        state = state.copy(
            itemOperations = state.itemOperations - target,
            storeOperations = state.storeOperations - target,
        )
    }

    private suspend fun mutate(
        kind: OperationKind,
        target: String,
        storeTarget: Boolean,
        block: suspend (BrowseService) -> BrowseSnapshot,
    ): Boolean {
        val currentService = service ?: return false
        if (statusOf(target) is OperationStatus.Running) return false

        val generation = ++snapshotGeneration
        val token = (targetGeneration[target] ?: 0L) + 1
        targetGeneration[target] = token
        setStatus(target, storeTarget, OperationStatus.Running)

        return try {
            val snapshot = block(currentService)
            if (targetGeneration[target] == token) {
                setStatus(target, storeTarget, OperationStatus.Succeeded)
            }
            if (generation == snapshotGeneration) {
                applySnapshot(snapshot, LoadState.Ready)
            }
            true
        } catch (error: Exception) {
            if (targetGeneration[target] == token) {
                setStatus(target, storeTarget, OperationStatus.Failed(kind, error.userMessage()))
            }
            false
        }
    }

    private fun setStatus(target: String, storeTarget: Boolean, status: OperationStatus) {
        state = if (storeTarget) {
            state.copy(storeOperations = state.storeOperations + (target to status))
        } else {
            state.copy(itemOperations = state.itemOperations + (target to status))
        }
    }

    private fun applySnapshot(snapshot: BrowseSnapshot, load: LoadState) {
        state = state.copy(
            load = load,
            sources = snapshot.sources,
            extensions = snapshot.extensions,
            stores = snapshot.stores,
        )
    }

    private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Operation failed"
}

/** Provides the injected runtime to the Browse UI; production leaves it unset until wired. */
val LocalBrowseService = staticCompositionLocalOf<BrowseService?> { null }

/** Provides the session to detail screens without threading it through every Composable. */
val LocalBrowseSession = staticCompositionLocalOf<BrowseSession?> { null }

/**
 * Creates the session once per shell and keeps it across tab switches, child pages and the
 * bar/rail width switch.
 */
@Composable
fun rememberBrowseSession(service: BrowseService? = LocalBrowseService.current): BrowseSession =
    remember(service) { BrowseSession(service) }

/** Convenience for launching session work from UI callbacks. */
@Composable
fun rememberBrowseScope() = rememberCoroutineScope()

/** Extension lookup used by the details route; returns null when the entry disappeared. */
fun BrowseState.extensionByKey(key: String): ExtensionInfo? = extensions.firstOrNull { it.key == key }

fun BrowseState.storeById(id: String): ExtensionStoreInfo? = stores.firstOrNull { it.id == id }

fun BrowseState.isBusy(): Boolean =
    itemOperations.values.any { it is OperationStatus.Running } ||
        storeOperations.values.any { it is OperationStatus.Running }
