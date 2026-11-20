package app.koyura.app.browse.venera

import app.koyura.app.browse.BrowseService
import app.koyura.app.browse.BrowseServiceException
import app.koyura.app.browse.BrowseSnapshot
import app.koyura.app.browse.CatalogVariantId
import app.koyura.app.browse.ExtensionGroup
import app.koyura.app.browse.ExtensionInfo
import app.koyura.app.browse.ExtensionSource
import app.koyura.app.browse.ExtensionStoreInfo
import app.koyura.app.browse.SourceInfo
import app.koyura.app.browse.StoreCatalog
import app.koyura.app.browse.StoreCatalogEntry
import app.koyura.app.browse.WorkChapter
import app.koyura.app.browse.WorkDetail
import app.koyura.app.browse.WorkSearchPage
import app.koyura.app.browse.WorkSummary
import app.koyura.app.browse.extensionKey
import app.koyura.data.extension.ExtensionService
import app.koyura.data.extension.SourceBrowseService
import app.koyura.data.extension.SourceChapterEntry
import app.koyura.data.extension.parseSourceChapterCatalog
import app.koyura.domain.extension.ExtensionFailure
import app.koyura.domain.extension.SavedSearch
import app.koyura.domain.extension.SearchOptionsSnapshot
import app.koyura.domain.extension.SourceSearchOptions
import app.koyura.source.runtime.VeneraCallback
import app.koyura.source.runtime.VeneraCatalogEntry
import app.koyura.source.runtime.isVeneraUpdateAvailable
import app.koyura.source.runtime.selectCatalogEntry
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

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
    /**
     * 单次取图字节通路。生产装配传入 `comic.onThumbnailLoad`（见
     * `ComicReadingService.loadThumbnailBytes`）；未传入时封面保持占位，
     * 页面其余部分不受影响。
     */
    private val thumbnails: (suspend (sourceKey: String, imageKey: String) -> ByteArray)? = null,
    /**
     * 详情目录同步钩：成功取得源目录后调用（保留同 ID 已读态，新章默认未读），
     * 失败或取消不调用以保留上次有效目录；生产装配传入书架仓储的同步入口。
     */
    private val onDetailLoaded: (suspend (sourceKey: String, workId: String, chapters: List<WorkChapter>) -> Unit)? = null,
    /**
     * 搜索通道：源搜索选项与保存搜索（生产注入 `session.service.browse`）。
     * 注入时 [searchWorks] 按源声明的选项默认值/当前持久化取值发请求；
     * 未注入（测试与旧装配）回退空选项，行为与历史一致。
     */
    private val search: SourceBrowseService? = null,
    private val sources: ProductionSourceRepository? = null,
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
        val visible = sources?.discovery?.sources()?.sources?.map { it.key }
        val byKey = installed.associateBy { it.sourceKey }
        val visibleRecords = visible?.mapNotNull(byKey::get) ?: installed
        BrowseSnapshot(
            sources = visibleRecords.map { record ->
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
                    // Venera 模型一脚本一源：详情账号区按该 key 取状态。
                    sources = listOf(ExtensionSource(key = record.sourceKey, name = record.name)),
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
        val (indexUrl, catalog) = extensions.previewCatalog(storeId)
        StoreCatalog(
            storeId = storeId,
            repositoryIndexUrl = indexUrl,
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
        searchWorksPage(sourceKey, query, page, next = null).let { WorkSearchPage(it.works, it.maxPage ?: page) }

    internal suspend fun searchWorksPage(
        sourceKey: String,
        query: String,
        page: Int,
        next: String?,
    ): SourceSearchPage = runService {
        require(page > 0)
        val service = search
        val result = if (service != null) {
            service.search(sourceKey, query, page, next)
        } else {
            require(next == null) { "Cursor pagination requires the source search service" }
            extensions.call(
                sourceKey,
                VeneraCallback.Search,
                JsonArray(listOf(JsonPrimitive(query), JsonArray(emptyList()), JsonPrimitive(page))),
            ).jsonObject
        }
        parseSearchPage(sourceKey, result, page)
    }

    private fun parseSearchPage(sourceKey: String, result: JsonObject, page: Int): SourceSearchPage {
        val comics = result["comics"] as? JsonArray
            ?: throw BrowseServiceException("Malformed search response from $sourceKey")
        val maximum = (result["maxPage"] as? JsonPrimitive)?.intOrNull
        val continuation = if (maximum == null) textOrNull(result["next"]) else null
        return SourceSearchPage(
            works = comics.map { item ->
                val comic = item as? JsonObject ?: throw BrowseServiceException("Malformed comic from $sourceKey")
                WorkSummary(
                    id = comic.string("id"),
                    title = comic.string("title"),
                    subtitle = comic.optionalString("subtitle"),
                    cover = comic.optionalString("cover"),
                    tags = (comic["tags"] as? JsonArray)?.mapNotNull(::textOrNull).orEmpty(),
                )
            },
            page = page,
            maxPage = maximum ?: if ("next" in result) null else page,
            next = continuation,
        )
    }

    /** 源搜索选项与当前持久化取值；未注入搜索通道（测试装配）返回 null，调用方整块隐藏。 */
    suspend fun sourceSearchOptions(sourceKey: String): SourceSearchOptions? =
        search?.searchOptions(sourceKey)

    /** 更新当前选项取值（按源持久化）；未注入通道时为无操作。 */
    suspend fun setSourceSearchOptions(sourceKey: String, current: SearchOptionsSnapshot) {
        search?.setSearchOptions(sourceKey, current)
    }

    /** 保存搜索列表（按源）；未注入通道时为空，UI 呈现空态。 */
    suspend fun savedSearches(sourceKey: String): List<SavedSearch> =
        search?.savedSearches?.list(sourceKey).orEmpty()

    /** 按当前选项快照保存搜索；未注入通道时返回 null，调用方提示不可用。 */
    suspend fun saveSearch(sourceKey: String, name: String, query: String): SavedSearch? =
        search?.saveSearch(sourceKey, name, query)

    /** 删除保存搜索；未注入通道时返回 false。 */
    suspend fun deleteSavedSearch(id: Long): Boolean =
        search?.savedSearches?.delete(id) ?: false

    suspend fun savedSearch(id: Long): SavedSearch? = search?.savedSearches?.get(id)

    /** Renaming or editing the text preserves the saved option snapshot. */
    suspend fun updateSavedSearch(id: Long, name: String, query: String): SavedSearch? {
        val service = search ?: return null
        val saved = service.savedSearches.get(id) ?: return null
        return service.savedSearches.update(id, name, query, saved.options)
    }

    suspend fun searchSavedWorks(id: Long, page: Int = 1, next: String? = null): SourceSearchPage = runService {
        val service = search ?: throw BrowseServiceException("Saved search is unavailable")
        val saved = service.savedSearches.get(id) ?: throw BrowseServiceException("Saved search was removed")
        parseSearchPage(saved.sourceKey, service.searchSaved(saved, page, next), page)
    }

    override suspend fun loadWorkDetail(sourceKey: String, workId: String): WorkDetail {
        val loaded = runService {
            val detail = extensions
                .call(sourceKey, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive(workId))))
                .jsonObject
            WorkDetail(
                id = workId,
                title = detail.string("title"),
                subtitle = detail.optionalString("subtitle"),
                cover = detail.optionalString("cover"),
                description = detail.optionalString("description"),
                tags = detail["tags"]?.jsonObject?.mapValues { (_, value) ->
                    value.jsonArray.mapNotNull { textOrNull(it) }
                }.orEmpty(),
                chapters = parseSourceChapterCatalog(detail["chapters"], strict = false).map { it.toWorkChapter() },
                updateTime = detail.optionalString("updateTime"),
                url = detail.optionalString("url"),
            )
        }
        // 成功才同步目录（保留同 ID 已读，新章默认未读）；失败/取消抛错，不碰上次有效目录。
        val sink = onDetailLoaded
        if (sink != null) {
            try {
                sink(sourceKey, workId, loaded.chapters)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 目录同步失败不遮挡详情展示，详情仍可阅读，书架计数下次刷新补齐。
            }
        }
        return loaded
    }

    private fun SourceChapterEntry.toWorkChapter(): WorkChapter {
        val date = (metadata?.get("dateMillis") as? JsonPrimitive)?.longOrNull
            ?: parseChapterDate(textOrNull(metadata?.get("uploadTime")) ?: textOrNull(metadata?.get("date")))
        return WorkChapter(
            id = id,
            title = title,
            dateMillis = date,
            scanlator = textOrNull(metadata?.get("scanlator"))?.trim()?.takeIf(String::isNotEmpty),
            pageCount = (metadata?.get("pageCount") as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 },
        )
    }

    private fun parseChapterDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value).toEpochMilliseconds()
        } catch (_: IllegalArgumentException) {
            try {
                LocalDate.parse(value).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
            } catch (_: IllegalArgumentException) {
                null
            }
        }
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

    override suspend fun loadCover(sourceKey: String, coverUrl: String): ByteArray? {
        if (coverUrl.isBlank()) return null
        val load = thumbnails ?: return null
        return try {
            load(sourceKey, coverUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

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
