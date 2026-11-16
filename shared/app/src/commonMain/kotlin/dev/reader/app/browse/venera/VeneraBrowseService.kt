package dev.reader.app.browse.venera

import dev.reader.app.browse.BrowseService
import dev.reader.app.browse.BrowseServiceException
import dev.reader.app.browse.BrowseSnapshot
import dev.reader.app.browse.CatalogVariantId
import dev.reader.app.browse.ExtensionGroup
import dev.reader.app.browse.ExtensionInfo
import dev.reader.app.browse.ExtensionStoreInfo
import dev.reader.app.browse.SourceInfo
import dev.reader.app.browse.StoreCatalog
import dev.reader.app.browse.StoreCatalogEntry
import dev.reader.app.browse.WorkChapter
import dev.reader.app.browse.WorkDetail
import dev.reader.app.browse.WorkSearchPage
import dev.reader.app.browse.WorkSummary
import dev.reader.app.browse.extensionKey
import dev.reader.data.extension.ExtensionService
import dev.reader.domain.extension.ExtensionFailure
import dev.reader.source.runtime.VeneraCallback
import dev.reader.source.runtime.VeneraCatalogEntry
import dev.reader.source.runtime.isVeneraUpdateAvailable
import dev.reader.source.runtime.selectCatalogEntry
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject


/**
 * Production [BrowseService] backed by the persistent Venera extension lifecycle.
 *
 * Every read goes through [ExtensionService]: repositories and installed sources from SQLite,
 * catalogs and scripts over Ktor, and source callbacks through QuickJS. Same-key variants stay
 * distinguishable because install and update always resolve through the repository catalog with
 * the previewed index URL, never through a bare key.
 */
class VeneraBrowseService(
    private val extensions: ExtensionService,
) : BrowseService {
    override suspend fun loadSnapshot(): BrowseSnapshot = runService {
        val installed = extensions.listInstalled()
        val updates = try {
            extensions.checkUpdatesDetailed().updates
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A flaky repository must not take down the whole Browse page.
            emptyMap()
        }
        BrowseSnapshot(
            sources = installed.map { record ->
                SourceInfo(
                    id = record.sourceKey,
                    name = record.name,
                    language = "",
                    versionName = record.version,
                    supportsLatest = false,
                )
            },
            extensions = installed.map { record ->
                val origin = extensions.originFor(record.sourceKey)
                val repositoryId = origin?.repositoryId
                ExtensionInfo(
                    key = if (repositoryId != null) {
                        extensionKey(repositoryId, record.sourceKey)
                    } else {
                        record.sourceKey
                    },
                    packageName = record.sourceKey,
                    name = record.name,
                    language = "",
                    versionName = record.version,
                    storeId = repositoryId.orEmpty(),
                    storeName = origin?.repositoryName ?: origin?.kind.orEmpty(),
                    isInstalled = true,
                    availableVersionName = updates[record.sourceKey],
                )
            },
            stores = extensions.listRepositories().map { repo ->
                ExtensionStoreInfo(
                    id = repo.id,
                    name = repo.name,
                    indexUrl = repo.url,
                    enabled = repo.enabled,
                    extensionCount = 0,
                )
            },
        )
    }

    override suspend fun addStore(name: String, indexUrl: String): BrowseSnapshot = runService {
        val trimmedUrl = indexUrl.trim()
        val label = name.trim().ifBlank {
            trimmedUrl.substringAfterLast('/').trim('/').ifBlank { trimmedUrl }
        }
        extensions.addRepository(label, trimmedUrl)
        loadSnapshot()
    }

    override suspend fun saveStore(storeId: String, name: String, indexUrl: String): BrowseSnapshot =
        runService {
            extensions.updateRepository(storeId, name.trim(), indexUrl.trim())
            loadSnapshot()
        }

    override suspend fun removeStore(storeId: String): BrowseSnapshot = runService {
        extensions.removeRepository(storeId)
        loadSnapshot()
    }

    override suspend fun setStoreEnabled(storeId: String, enabled: Boolean): BrowseSnapshot =
        runService {
            extensions.setRepositoryEnabled(storeId, enabled)
            loadSnapshot()
        }

    override suspend fun previewStore(storeId: String): StoreCatalog = runService {
        val (_, catalog) = extensions.previewCatalog(storeId)
        StoreCatalog(
            storeId = storeId,
            entries = catalog.entries.map { entry ->
                StoreCatalogEntry(
                    packageName = entry.key,
                    name = entry.name,
                    versionName = entry.version,
                    language = "",
                    summary = entry.description,
                    sourceKey = entry.key,
                    scriptUrl = entry.url,
                )
            },
        )
    }

    override suspend fun installExtension(extensionKey: String): BrowseSnapshot = runService {
        val separator = extensionKey.lastIndexOf("::")
        if (separator < 0) {
            throw BrowseServiceException(
                "Cannot install $extensionKey without its repository; install from a store preview instead.",
            )
        }
        val repositoryId = extensionKey.substring(0, separator)
        val sourceKey = extensionKey.substring(separator + 2)
        val (previewedUrl, catalog) = extensions.previewCatalog(repositoryId)
        val selected = try {
            selectCatalogEntry(
                sourceKey,
                catalog.entries.map { VeneraCatalogEntry(it.key, it.name, it.version, it.url, it.description) },
                null,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw BrowseServiceException(error.message ?: "Source not available: $sourceKey")
        }
        extensions.installFromCatalog(repositoryId, selected, previewedUrl)
        loadSnapshot()
    }

    override suspend fun installCatalogVariant(variant: CatalogVariantId): BrowseSnapshot = runService {
        // The entry identity (repository, key, resolved script URL) comes from the preview the user
        // tapped; only the metadata travels with the variant because the runtime re-reads the script.
        val entry = VeneraCatalogEntry(
            key = variant.sourceKey,
            name = variant.sourceKey,
            version = "",
            url = variant.scriptUrl,
        )
        extensions.installFromCatalog(variant.repositoryId, entry, variant.repositoryIndexUrl)
        loadSnapshot()
    }

    override suspend fun updateExtension(extensionKey: String): BrowseSnapshot = runService {
        val sourceKey = installedSourceKey(extensionKey)
        extensions.update(sourceKey)
        loadSnapshot()
    }

    override suspend fun uninstallExtension(extensionKey: String): BrowseSnapshot = runService {
        extensions.uninstall(installedSourceKey(extensionKey))
        loadSnapshot()
    }

    override suspend fun updateAllExtensions(): BrowseSnapshot = runService {
        val pending = try {
            extensions.checkUpdatesDetailed().updates.keys.toList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        var failures = 0
        for (key in pending) {
            try {
                extensions.update(key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures += 1
            }
        }
        val snapshot = loadSnapshot()
        if (pending.isNotEmpty() && failures == pending.size) {
            throw BrowseServiceException("None of the ${pending.size} pending updates could be installed.")
        }
        snapshot
    }

    override suspend fun searchWorks(sourceKey: String, query: String, page: Int): WorkSearchPage =
        runService {
            val arguments = JsonArray(
                listOf(JsonPrimitive(query), JsonArray(emptyList()), JsonPrimitive(page)),
            )
            val result = extensions.call(sourceKey, VeneraCallback.Search, arguments).jsonObject
            val comics = result["comics"]?.jsonArray
                ?: throw BrowseServiceException("Malformed search response from $sourceKey")
            val maxPage = result["maxPage"]?.jsonPrimitive?.intOrNull ?: 1
            WorkSearchPage(
                works = comics.map { item ->
                    val comic = item.jsonObject
                    WorkSummary(
                        id = comic.string("id"),
                        title = comic.string("title"),
                        subtitle = comic.optionalString("subtitle"),
                        cover = comic.optionalString("cover"),
                        tags = comic["tags"]?.jsonArray?.mapNotNull { textOrNull(it) }.orEmpty(),
                    )
                },
                maxPage = maxPage,
            )
        }

    override suspend fun loadWorkDetail(sourceKey: String, workId: String): WorkDetail = runService {
        val detail = extensions
            .call(sourceKey, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive(workId))))
            .jsonObject
        val chapters = detail["chapters"]?.jsonObject
        WorkDetail(
            id = workId,
            title = detail.string("title"),
            subtitle = detail.optionalString("subtitle"),
            cover = detail.optionalString("cover"),
            description = detail.optionalString("description"),
            tags = detail["tags"]?.jsonObject?.mapValues { (_, value) ->
                value.jsonArray.mapNotNull { textOrNull(it) }
            }.orEmpty(),
            chapters = chapters?.entries?.map { (id, value) ->
                WorkChapter(id = id, title = value.jsonPrimitive.content)
            }.orEmpty(),
            updateTime = detail.optionalString("updateTime"),
            url = detail.optionalString("url"),
        )
    }

    private fun installedSourceKey(extensionKey: String): String {
        val separator = extensionKey.lastIndexOf("::")
        return if (separator < 0) extensionKey else extensionKey.substring(separator + 2)
    }

    private fun JsonObject.string(field: String): String =
        textOrNull(this[field])?.takeIf { it.isNotEmpty() }
            ?: throw BrowseServiceException("Malformed response: missing $field")

    private fun JsonObject.optionalString(field: String): String =
        textOrNull(this[field]).orEmpty()

    private fun textOrNull(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    private suspend fun <T> runService(block: suspend () -> T): T {
        try {
            return block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ExtensionFailure) {
            throw BrowseServiceException(failure.message ?: "Extension operation failed")
        }
    }
}
