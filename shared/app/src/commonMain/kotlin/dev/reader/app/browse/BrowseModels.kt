package dev.reader.app.browse

/**
 * Immutable view models for the Browse tab.
 *
 * These types are shared with an injectable [BrowseService]; the UI never mutates them directly.
 */

data class SourceInfo(
    val id: String,
    val name: String,
    val language: String,
    val versionName: String,
    val supportsLatest: Boolean = false,
)

enum class ExtensionGroup {
    Updates,
    Installed,
    Available,
}

data class ExtensionInfo(
    /** Stable identity: the same package served by two stores must stay distinguishable. */
    val key: String,
    val packageName: String,
    val name: String,
    val language: String,
    val versionName: String,
    val storeId: String,
    val storeName: String,
    val isInstalled: Boolean = false,
    val availableVersionName: String? = null,
    val isNsfw: Boolean = false,
    val sources: List<String> = emptyList(),
    val summary: String = "",
) {
    val hasUpdate: Boolean
        get() = availableVersionName != null && availableVersionName != versionName

    val group: ExtensionGroup
        get() = when {
            hasUpdate -> ExtensionGroup.Updates
            isInstalled -> ExtensionGroup.Installed
            else -> ExtensionGroup.Available
        }
}

data class ExtensionStoreInfo(
    val id: String,
    val name: String,
    val indexUrl: String,
    val enabled: Boolean = true,
    val extensionCount: Int = 0,
)

data class StoreCatalogEntry(
    val packageName: String,
    val name: String,
    val versionName: String,
    val language: String,
    val summary: String = "",
)

data class StoreCatalog(
    val storeId: String,
    val entries: List<StoreCatalogEntry>,
)

data class BrowseSnapshot(
    val sources: List<SourceInfo> = emptyList(),
    val extensions: List<ExtensionInfo> = emptyList(),
    val stores: List<ExtensionStoreInfo> = emptyList(),
)

enum class BrowsePane {
    Sources,
    Extensions,
}

sealed interface LoadState {
    data object Loading : LoadState
    data object Ready : LoadState
    data class Failed(val message: String) : LoadState

    /** No browse service was injected; the normal entry point stays usable but shows an empty state. */
    data object Unavailable : LoadState
}

enum class OperationKind {
    Install,
    Update,
    Uninstall,
    AddStore,
    SaveStore,
    RemoveStore,
    ToggleStore,
    PreviewStore,
    UpdateAll,
}

/** Per-target feedback, keyed by extension key or store id. */
sealed interface OperationStatus {
    data object Running : OperationStatus
    data object Succeeded : OperationStatus
    data class Failed(val kind: OperationKind, val message: String) : OperationStatus
}

val OperationStatus.isRunning: Boolean
    get() = this is OperationStatus.Running

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val catalog: StoreCatalog) : CatalogState
    data class Failed(val message: String) : CatalogState
}

data class BrowseState(
    val pane: BrowsePane = BrowsePane.Sources,
    val load: LoadState = LoadState.Loading,
    val sources: List<SourceInfo> = emptyList(),
    val extensions: List<ExtensionInfo> = emptyList(),
    val stores: List<ExtensionStoreInfo> = emptyList(),
    val sourceQuery: String = "",
    val extensionQuery: String = "",
    val itemOperations: Map<String, OperationStatus> = emptyMap(),
    val storeOperations: Map<String, OperationStatus> = emptyMap(),
    val catalogStates: Map<String, CatalogState> = emptyMap(),
) {
    val updateCount: Int
        get() = extensions.count { it.hasUpdate }
}

data class SourceSection(
    val language: String,
    val sources: List<SourceInfo>,
)

data class ExtensionSection(
    val group: ExtensionGroup,
    val extensions: List<ExtensionInfo>,
)

/** Filters by name, package, store and language, then groups updates first, installed, available. */
fun List<ExtensionInfo>.groupedExtensions(query: String): List<ExtensionSection> {
    val needle = query.trim()
    val filtered = if (needle.isEmpty()) {
        this
    } else {
        filter {
            it.name.contains(needle, ignoreCase = true) ||
                it.packageName.contains(needle, ignoreCase = true) ||
                it.storeName.contains(needle, ignoreCase = true) ||
                it.language.contains(needle, ignoreCase = true)
        }
    }
    return ExtensionGroup.entries.mapNotNull { group ->
        val items = filtered.filter { it.group == group }.sortedBy { it.name.lowercase() }
        if (items.isEmpty()) null else ExtensionSection(group, items)
    }
}

/** Groups sources by language, sorted by language code and then name. */
fun List<SourceInfo>.groupedSources(query: String): List<SourceSection> {
    val needle = query.trim()
    val filtered = if (needle.isEmpty()) {
        this
    } else {
        filter {
            it.name.contains(needle, ignoreCase = true) ||
                it.language.contains(needle, ignoreCase = true)
        }
    }
    val byLanguage = filtered.groupBy { it.language }
    return byLanguage.keys.sorted().map { language ->
        val items = byLanguage.getValue(language)
        SourceSection(language, items.sortedBy { it.name.lowercase() })
    }
}

/** Validation result for a store index URL, kept pure so both the dialog and tests can use it. */
enum class StoreUrlError {
    Empty,
    InvalidScheme,
    Duplicate,
}

/**
 * Validates a store index URL.
 *
 * [editingStoreId] lets an edit keep its own URL without tripping the duplicate check.
 */
fun validateStoreUrl(
    url: String,
    stores: List<ExtensionStoreInfo>,
    editingStoreId: String? = null,
): StoreUrlError? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return StoreUrlError.Empty
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        return StoreUrlError.InvalidScheme
    }
    val duplicate = stores.any { it.id != editingStoreId && it.indexUrl.equals(trimmed, ignoreCase = true) }
    return if (duplicate) StoreUrlError.Duplicate else null
}
