package app.koyura.app.browse.globalsearch

import app.koyura.app.browse.BrowseService
import app.koyura.app.browse.SourceInfo
import app.koyura.app.browse.WorkSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** 单源搜索结果：成功给作品，失败给可读原因（不影响其它源）。 */
data class GlobalSearchSourceResult(
    val sourceKey: String,
    val sourceName: String,
    val works: List<WorkSummary> = emptyList(),
    val error: String? = null,
)

/**
 * 全局搜索扇出：对既有 [BrowseService] 逐源并发调用 `search.load`，每个源独立成败。
 *
 * 结构照原版 `GlobalSearchScreenModel` 的按源并行搜索：`coroutineScope { sources.map { async { ... } } }`，
 * 单源异常折成该源的错误结果，取消照常向上抛（不当作失败）。
 */
class GlobalSearchService(
    private val browse: BrowseService?,
) {
    /** 当前已安装来源（名称用于分组头）；未接服务时抛错，由会话记整体错误。 */
    suspend fun sources(): List<SourceInfo> {
        val service = browse ?: throw IllegalStateException("Browse is not connected")
        return service.loadSnapshot().sources
    }

    /** 并行搜索全部来源，返回顺序与 [sources] 一致。 */
    suspend fun search(
        sources: List<SourceInfo>,
        query: String,
    ): List<GlobalSearchSourceResult> = coroutineScope {
        sources.map { source ->
            async { searchOne(source.id, source.name, query) }
        }.awaitAll()
    }

    /** 单源搜索（键 + 展示名）；失败折成该源错误结果。 */
    suspend fun searchOne(sourceKey: String, sourceName: String, query: String): GlobalSearchSourceResult {
        val service = browse
            ?: return GlobalSearchSourceResult(sourceKey, sourceName, error = "Browse is not connected")
        return try {
            val page = service.searchWorks(sourceKey, query)
            GlobalSearchSourceResult(sourceKey, sourceName, page.works)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            GlobalSearchSourceResult(
                sourceKey = sourceKey,
                sourceName = sourceName,
                error = failure.message?.takeIf { it.isNotBlank() } ?: "Search failed",
            )
        }
    }
}
