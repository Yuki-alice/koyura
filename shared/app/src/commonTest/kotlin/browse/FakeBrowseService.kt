package app.koyura.app.browse

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

    override suspend fun addStore(name: String, indexUrl: String): BrowseSnapshot {
        enter("addStore:$indexUrl")
        lastAddedStoreName = name
        val id = "store:${snapshot.stores.size + 1}"
        snapshot = snapshot.copy(
            stores = snapshot.stores + ExtensionStoreInfo(
                id = id,
                name = name.ifBlank { indexUrl.substringAfterLast('/').ifBlank { indexUrl } },
                indexUrl = indexUrl,
                extensionCount = catalogs[id]?.entries?.size ?: 0,
            ),
        )
        return snapshot
    }

    /** Name passed to the most recent [addStore] call, so tests can assert the UI forwarded it. */
    var lastAddedStoreName: String? = null

    /** Variants passed to the most recent [installCatalogVariant] call. */
    val installedVariants = mutableListOf<CatalogVariantId>()

    /** Catalog contents served per store after an index URL change, keyed by store id. */
    var refreshedCatalogs: Map<String, StoreCatalog> = emptyMap()

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
        return refreshedCatalogs[storeId] ?: catalogs[storeId] ?: StoreCatalog(storeId, emptyList())
    }

    override suspend fun installCatalogVariant(variant: CatalogVariantId): BrowseSnapshot {
        installedVariants += variant
        enter("installVariant:${variant.key}")
        val key = extensionKey(variant.repositoryId, variant.sourceKey)
        val listed = catalogs[variant.repositoryId]?.entries
            ?.firstOrNull { it.sourceKey == variant.sourceKey && it.scriptUrl == variant.scriptUrl }
        snapshot = snapshot.copy(
            extensions = snapshot.extensions.map {
                if (it.key == key) {
                    it.copy(
                        isInstalled = true,
                        availableVersionName = null,
                        versionName = listed?.versionName ?: it.versionName,
                    )
                } else {
                    it
                }
            },
        )
        return snapshot
    }

    override suspend fun searchWorks(sourceKey: String, query: String, page: Int): WorkSearchPage {
        enter("search:$sourceKey:$query:$page")
        return searchPages[query] ?: WorkSearchPage(emptyList(), 1)
    }

    override suspend fun loadWorkDetail(sourceKey: String, workId: String): WorkDetail {
        enter("detail:$sourceKey:$workId")
        return workDetails[workId] ?: throw BrowseServiceException("Unknown work: $workId")
    }

    /**
     * Canned cover bytes keyed by (sourceKey, coverUrl): present → decoded, absent → null
     * (placeholder), matching the production single-attempt contract.
     */
    var coverBytes: Map<Pair<String, String>, ByteArray> = emptyMap()

    override suspend fun loadCover(sourceKey: String, coverUrl: String): ByteArray? {
        enter("cover:$sourceKey:$coverUrl")
        if (coverUrl.isEmpty()) return null
        return coverBytes[sourceKey to coverUrl]
    }

    /** Canned search pages keyed by query, so UI tests can drive search without a runtime. */
    var searchPages: Map<String, WorkSearchPage> = emptyMap()

    /** Canned work details keyed by work id. */
    var workDetails: Map<String, WorkDetail> = emptyMap()

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
            StoreCatalogEntry("com.example.available", "Available Example", "3.0", "ja", sourceKey = "available_ja", scriptUrl = "https://example.com/available_ja.js"),
            StoreCatalogEntry("com.example.new", "New Example", "1.0", "en", "Freshly published", sourceKey = "new_en", scriptUrl = "https://example.com/new_en.js"),
        ),
    ),
    "store:2" to StoreCatalog(
        storeId = "store:2",
        entries = listOf(StoreCatalogEntry("com.example.available", "Available Example", "9.9", "ja", "Variant from a second store", sourceKey = "available_ja", scriptUrl = "https://second.example/available_ja.js")),
    ),
)

/** Builds the expected variant key for a default catalog row. */
fun catalogVariantKey(repositoryId: String, sourceKey: String, scriptUrl: String, ordinal: Int = 0): String =
    CatalogVariantId(repositoryId, sourceKey, scriptUrl, ordinal).key
