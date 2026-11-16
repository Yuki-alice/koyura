package app.koyura.app.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException

/**
 * Holds the Browse tab's data and per-item operation state.
 *
 * Rules enforced here so every surface behaves the same:
 * - an operation on a target is ignored while that target is already running (no double submit);
 * - after every settled operation the session re-reads the authoritative snapshot, so page content,
 *   operation state and the update badge all converge on the committed result even when several
 *   calls overlap or finish out of order;
 * - a cancelled call is not a failure: it clears its own running marker, keeps every already
 *   committed result and leaves the target immediately retryable;
 * - a preview is bound to the index URL that was current when it started; editing that URL, removing
 *   the store or closing the preview invalidates the in-flight request and any later arrival;
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

    /**
     * Counts settled mutations. A snapshot read is only applied when no operation settled while it
     * was in flight, which keeps the oldest stale read from overwriting a newer commit.
     */
    private var settledMutations = 0L

    /** Per-target tokens for value operations such as store toggles and catalog previews. */
    private val targetGeneration = mutableMapOf<String, Long>()

    /**
     * Bumped whenever a store's preview becomes stale (URL edit, removal, dialog close).
     *
     * Any preview that started with an older validity stamp is discarded on arrival, which also
     * covers the case where a newer preview was opened and completed before the old one returned.
     */
    private val catalogValidity = mutableMapOf<String, Long>()

    private val busy: Boolean
        get() = state.itemOperations.values.any { it is OperationStatus.Running } ||
            state.storeOperations.values.any { it is OperationStatus.Running }

    suspend fun refresh() {
        val currentService = service
        if (currentService == null) {
            state = state.copy(load = LoadState.Unavailable)
            return
        }
        val generation = settledMutations
        state = state.copy(load = LoadState.Loading)
        try {
            val snapshot = currentService.loadSnapshot()
            if (generation == settledMutations) applySnapshot(snapshot, LoadState.Ready)
        } catch (cancelled: CancellationException) {
            // A cancelled refresh must not be reported as a load failure.
            if (generation == settledMutations && state.load is LoadState.Loading) {
                state = state.copy(load = LoadState.Ready)
            }
            throw cancelled
        } catch (error: Exception) {
            if (generation == settledMutations) {
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

    /**
     * Runs a source search outside the snapshot machinery: results are per-query UI state, not
     * list state, so they never disturb the convergence bookkeeping above.
     */
    suspend fun searchWorks(sourceKey: String, query: String, page: Int = 1): WorkSearchPage {
        val service = serviceOrThrow()
        return try {
            service.searchWorks(sourceKey, query, page)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: BrowseServiceException) {
            throw error
        } catch (error: Exception) {
            throw BrowseServiceException(error.message ?: "Search failed")
        }
    }

    suspend fun loadWorkDetail(sourceKey: String, workId: String): WorkDetail {
        val service = serviceOrThrow()
        return try {
            service.loadWorkDetail(sourceKey, workId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: BrowseServiceException) {
            throw error
        } catch (error: Exception) {
            throw BrowseServiceException(error.message ?: "Could not load details")
        }
    }

    private fun serviceOrThrow(): BrowseService =
        service ?: throw BrowseServiceException("Browse is not connected")

    fun statusOf(target: String): OperationStatus? =
        state.itemOperations[target] ?: state.storeOperations[target]

    suspend fun install(extensionKey: String): Boolean =
        mutate(OperationKind.Install, extensionKey, storeTarget = false) {
            it.installExtension(extensionKey)
        }

    /**
     * Installs one catalog variant.
     *
     * The variant identity, including the index URL that was previewed, is forwarded unchanged so a
     * repository listing two scripts with the same package name installs the row the user tapped
     * and the runtime can verify the script origin.
     */
    suspend fun installVariant(variant: CatalogVariantId): Boolean =
        mutate(OperationKind.Install, variant.key, storeTarget = false) {
            it.installCatalogVariant(variant)
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
            it.addStore(name.trim(), indexUrl.trim())
        }

    suspend fun saveStore(storeId: String, name: String, indexUrl: String): Boolean {
        val trimmedUrl = indexUrl.trim()
        val changesUrl = state.storeById(storeId)?.indexUrl != trimmedUrl
        return mutate(OperationKind.SaveStore, storeId, storeTarget = true) {
            it.saveStore(storeId, name.trim(), trimmedUrl)
        }.also { saved ->
            // A changed index URL invalidates anything cached from the old one, including in-flight
            // previews that would otherwise land afterwards.
            if (saved && changesUrl) invalidateStoreCatalog(storeId)
        }
    }

    suspend fun removeStore(storeId: String): Boolean =
        mutate(OperationKind.RemoveStore, storeId, storeTarget = true) {
            it.removeStore(storeId)
        }.also { removed ->
            if (removed) invalidateStoreCatalog(storeId)
        }

    suspend fun setStoreEnabled(storeId: String, enabled: Boolean): Boolean =
        mutate(OperationKind.ToggleStore, storeId, storeTarget = true) {
            it.setStoreEnabled(storeId, enabled)
        }

    /**
     * Loads a store catalog for preview.
     *
     * The preview snapshots the store's index URL when it starts; a later URL change or an explicit
     * close invalidates it, so a slow response can never repopulate a stale dialog.
     */
    suspend fun previewStore(storeId: String) {
        val currentService = service ?: return
        if (state.catalogStates[storeId] is CatalogState.Loading) return

        val token = (targetGeneration["catalog:$storeId"] ?: 0L) + 1
        targetGeneration["catalog:$storeId"] = token
        val validity = catalogValidity[storeId] ?: 0L
        val indexUrl = state.storeById(storeId)?.indexUrl.orEmpty()

        state = state.copy(catalogStates = state.catalogStates + (storeId to CatalogState.Loading))
        try {
            val catalog = currentService.previewStore(storeId)
            if (isCurrentPreview(storeId, token, validity)) {
                state = state.copy(
                    catalogStates = state.catalogStates +
                        (storeId to CatalogState.Ready(
                            catalog.copy(repositoryIndexUrl = catalog.repositoryIndexUrl.ifBlank { indexUrl }),
                        )),
                )
            }
        } catch (cancelled: CancellationException) {
            if (isCurrentPreview(storeId, token, validity)) {
                state = state.copy(catalogStates = state.catalogStates - storeId)
            }
            throw cancelled
        } catch (error: Exception) {
            if (isCurrentPreview(storeId, token, validity)) {
                state = state.copy(
                    catalogStates = state.catalogStates + (storeId to CatalogState.Failed(error.userMessage())),
                )
            }
        }
    }

    /** Drops a preview when its dialog closes and invalidates any request still in flight. */
    fun clearStoreCatalog(storeId: String) {
        invalidateStoreCatalog(storeId)
    }

    /**
     * Invalidates the preview and bumps the store's validity stamp.
     *
     * The stamp bump is what makes an already-running request stale, not just the map removal.
     */
    fun invalidateStoreCatalog(storeId: String) {
        catalogValidity[storeId] = (catalogValidity[storeId] ?: 0L) + 1
        targetGeneration.remove("catalog:$storeId")
        state = state.copy(catalogStates = state.catalogStates - storeId)
    }

    fun clearStatus(target: String) {
        state = state.copy(
            itemOperations = state.itemOperations - target,
            storeOperations = state.storeOperations - target,
        )
    }

    private fun isCurrentPreview(storeId: String, token: Long, validity: Long): Boolean =
        targetGeneration["catalog:$storeId"] == token &&
            (catalogValidity[storeId] ?: 0L) == validity

    private suspend fun mutate(
        kind: OperationKind,
        target: String,
        storeTarget: Boolean,
        block: suspend (BrowseService) -> BrowseSnapshot,
    ): Boolean {
        val currentService = service ?: return false
        if (statusOf(target) is OperationStatus.Running) return false

        val token = (targetGeneration[target] ?: 0L) + 1
        targetGeneration[target] = token
        setStatus(target, storeTarget, OperationStatus.Running)

        return try {
            block(currentService)
            settledMutations++
            if (targetGeneration[target] == token) {
                setStatus(target, storeTarget, OperationStatus.Succeeded)
            }
            // Re-read instead of applying the call's own return value: another operation may have
            // committed after this one started, and the UI must show the newest committed state.
            reconcilesSnapshot(currentService)
            true
        } catch (cancelled: CancellationException) {
            // Cancellation is not a failure. Clear this target's marker so it can be retried and
            // leave every already committed snapshot untouched.
            if (targetGeneration[target] == token) {
                clearStatus(target)
            }
            throw cancelled
        } catch (error: Exception) {
            if (targetGeneration[target] == token) {
                setStatus(target, storeTarget, OperationStatus.Failed(kind, error.userMessage()))
            }
            // A failed call changed nothing, but a concurrent success may still be unreflected.
            if (hasPendingSettlements()) reconcilesSnapshot(currentService)
            false
        }
    }

    /**
     * True when some other operation settled while this call was in flight, meaning the local list
     * may already be behind the service.
     */
    private fun hasPendingSettlements(): Boolean =
        state.itemOperations.values.any { it is OperationStatus.Succeeded } ||
            state.storeOperations.values.any { it is OperationStatus.Succeeded }

    private suspend fun reconcilesSnapshot(service: BrowseService) {
        val generation = settledMutations
        val snapshot = try {
            service.loadSnapshot()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return
        }
        if (generation == settledMutations) applySnapshot(snapshot, LoadState.Ready)
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
