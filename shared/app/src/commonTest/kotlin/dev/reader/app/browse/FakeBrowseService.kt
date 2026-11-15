package dev.reader.app.browse

/**
 * In-memory [BrowseService] used by tests.
 *
 * It mirrors the contract the real runtime must satisfy: every mutation returns a full snapshot,
 * failures throw [BrowseServiceException], and calls can be paused or failed on demand so tests can
 * exercise loading, retry, double-submit and stale-response protection.
 */
class FakeBrowseService(
    sources: List<SourceInfo> = defaultSources(),
    extensions: List<ExtensionInfo> = defaultExtensions(),
    stores: List<ExtensionStoreInfo> = defaultStores(),
    private val catalogs: Map<String, StoreCatalog> = defaultCatalogs(),
) : BrowseService {
    var snapshot = BrowseSnapshot(sources = sources, extensions = extensions, stores = stores)
        private set

    /** Number of times each operation ran, keyed by "operation:target". */
    val calls = mutableMapOf<String, Int>()

    /** When set, the next call of that operation throws before mutating anything. */
    val failures = mutableMapOf<String, String>()

    /** When set, calls whose key starts with [gateKey] wait for [gate] before returning. */
    var gate: Gate? = null
    var gateKey: String? = null
    var currentGateConsumed = false

    var loadFailure: String? = null
    var previewFailure: String? = null

    class Gate {
        private var release: (() -> Unit)? = null
        suspend fun await() = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            release = { continuation.resumeWith(Result.success(Unit)) }
        }

        fun open() {
            release?.invoke()
            release = null
        }
    }

    private suspend fun enter(key: String) {
        calls[key] = (calls[key] ?: 0) + 1
        val currentGate = gate
        if (!currentGateConsumed && gateKey != null && key.startsWith(gateKey!!)) {
            currentGateConsumed = true
            currentGate?.await()
        }
        failures[key]?.let { message ->
            failures.remove(key)
            throw BrowseServiceException(message)
        }
    }

    override suspend fun loadSnapshot(): BrowseSnapshot {
        enter("load")
        loadFailure?.let { message ->
            loadFailure = null
            throw BrowseServiceException(message)
        }
        return snapshot
    }

    override suspend fun addStore(url: String): BrowseSnapshot {
        enter("addStore:$url")
        val id = "store:${snapshot.stores.size + 1}"
        snapshot = snapshot.copy(
            stores = snapshot.stores + ExtensionStoreInfo(
                id = id,
                name = url.substringAfterLast('/').ifBlank { url },
                indexUrl = url,
                extensionCount = catalogs[id]?.entries?.size ?: 0,
            ),
        )
        return snapshot
    }

    override suspend fun saveStore(storeId: String, name: String, indexUrl: String): BrowseSnapshot {
        enter("saveStore:$storeId")
        snapshot = snapshot.copy(
            stores = snapshot.stores.map {
                if (it.id == storeId) it.copy(name = name, indexUrl = indexUrl) else it
            },
        )
        return snapshot
    }

    override suspend fun removeStore(storeId: String): BrowseSnapshot {
        enter("removeStore:$storeId")
        snapshot = snapshot.copy(stores = snapshot.stores.filterNot { it.id == storeId })
        return snapshot
    }

    override suspend fun setStoreEnabled(storeId: String, enabled: Boolean): BrowseSnapshot {
        enter("setStoreEnabled:$storeId")
        snapshot = snapshot.copy(
            stores = snapshot.stores.map { if (it.id == storeId) it.copy(enabled = enabled) else it },
        )
        return snapshot
    }

    override suspend fun previewStore(storeId: String): StoreCatalog {
        enter("previewStore:$storeId")
        previewFailure?.let { message ->
            previewFailure = null
            throw BrowseServiceException(message)
        }
        return catalogs[storeId] ?: StoreCatalog(storeId, emptyList())
    }

    override suspend fun installExtension(extensionKey: String): BrowseSnapshot {
        enter("install:$extensionKey")
        snapshot = snapshot.copy(
            extensions = snapshot.extensions.map {
                if (it.key == extensionKey) it.copy(isInstalled = true, availableVersionName = null) else it
            },
        )
        return snapshot
    }

    override suspend fun updateExtension(extensionKey: String): BrowseSnapshot {
        enter("update:$extensionKey")
        snapshot = snapshot.copy(
            extensions = snapshot.extensions.map {
                if (it.key == extensionKey) {
                    it.copy(versionName = it.availableVersionName ?: it.versionName, availableVersionName = null)
                } else {
                    it
                }
            },
        )
        return snapshot
    }

    override suspend fun uninstallExtension(extensionKey: String): BrowseSnapshot {
        enter("uninstall:$extensionKey")
        snapshot = snapshot.copy(extensions = snapshot.extensions.filterNot { it.key == extensionKey })
        return snapshot
    }

    override suspend fun updateAllExtensions(): BrowseSnapshot {
        enter("updateAll")
        snapshot = snapshot.copy(
            extensions = snapshot.extensions.map {
                if (it.hasUpdate) {
                    it.copy(versionName = it.availableVersionName ?: it.versionName, availableVersionName = null)
                } else {
                    it
                }
            },
        )
        return snapshot
    }
}

fun defaultSources(): List<SourceInfo> = listOf(
    SourceInfo(id = "src:en:manga", name = "Manga Example", language = "en", versionName = "1.0", supportsLatest = true),
    SourceInfo(id = "src:ja:manga", name = "日本の例", language = "ja", versionName = "2.0"),
)

fun defaultExtensions(): List<ExtensionInfo> = listOf(
    ExtensionInfo(
        key = "store:1::com.example.updates",
        packageName = "com.example.updates",
        name = "Updates Example",
        language = "en",
        versionName = "1.0",
        availableVersionName = "1.1",
        storeId = "store:1",
        storeName = "Example",
        isInstalled = true,
        sources = listOf("Example Source"),
    ),
    ExtensionInfo(
        key = "store:1::com.example.installed",
        packageName = "com.example.installed",
        name = "Installed Example",
        language = "en",
        versionName = "1.0",
        storeId = "store:1",
        storeName = "Example",
        isInstalled = true,
    ),
    ExtensionInfo(
        key = "store:1::com.example.available",
        packageName = "com.example.available",
        name = "Available Example",
        language = "ja",
        versionName = "3.0",
        storeId = "store:1",
        storeName = "Example",
    ),
    ExtensionInfo(
        key = "store:2::com.example.available",
        packageName = "com.example.available",
        name = "Available Example",
        language = "ja",
        versionName = "9.9",
        storeId = "store:2",
        storeName = "Second Store",
        isNsfw = true,
    ),
)

fun defaultStores(): List<ExtensionStoreInfo> = listOf(
    ExtensionStoreInfo(id = "store:1", name = "Example", indexUrl = "https://example.com/index.json", extensionCount = 3),
    ExtensionStoreInfo(id = "store:2", name = "Second Store", indexUrl = "https://second.example/index.json", extensionCount = 1),
)

fun defaultCatalogs(): Map<String, StoreCatalog> = mapOf(
    "store:1" to StoreCatalog(
        storeId = "store:1",
        entries = listOf(
            StoreCatalogEntry("com.example.available", "Available Example", "3.0", "ja"),
            StoreCatalogEntry("com.example.new", "New Example", "1.0", "en", "Freshly published"),
        ),
    ),
    "store:2" to StoreCatalog(
        storeId = "store:2",
        entries = listOf(StoreCatalogEntry("com.example.available", "Available Example", "9.9", "ja", "Variant from a second store")),
    ),
)
