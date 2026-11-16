package dev.reader.app.browse

/**
 * Boundary between the Browse UI and the source/extension runtime.
 *
 * The UI owns no business rules beyond presentation: every call returns the complete snapshot the
 * screen should render next, so a caller can also use a fake in tests and a real service later.
 * Implementations should throw [BrowseServiceException] with a user-presentable message on failure.
 */
interface BrowseService {
    suspend fun loadSnapshot(): BrowseSnapshot

    /**
     * Adds a repository. [name] is the user's label; pass a blank string to let the service derive
     * one from the index.
     */
    suspend fun addStore(name: String, indexUrl: String): BrowseSnapshot
    suspend fun saveStore(storeId: String, name: String, indexUrl: String): BrowseSnapshot
    suspend fun removeStore(storeId: String): BrowseSnapshot
    suspend fun setStoreEnabled(storeId: String, enabled: Boolean): BrowseSnapshot
    suspend fun previewStore(storeId: String): StoreCatalog

    suspend fun installExtension(extensionKey: String): BrowseSnapshot

    /**
     * Installs one catalog listing identified by [CatalogVariantId].
     *
     * Required for repositories that publish several scripts under one package name: the variant
     * carries the repository, script key and resolved URL so the service can fetch the right script.
     */
    suspend fun installCatalogVariant(variant: CatalogVariantId): BrowseSnapshot

    suspend fun updateExtension(extensionKey: String): BrowseSnapshot
    suspend fun uninstallExtension(extensionKey: String): BrowseSnapshot
    suspend fun updateAllExtensions(): BrowseSnapshot
}

class BrowseServiceException(message: String) : Exception(message)

/** Key prefix used to address the add-store dialog without an existing store id. */
const val AddStoreTarget = "store:add"

/** Key used for the update-all bulk action. */
const val UpdateAllTarget = "extension:update-all"

/** Builds the stable extension key used across routes and per-item operation maps. */
fun extensionKey(storeId: String, packageName: String): String = "$storeId::$packageName"
