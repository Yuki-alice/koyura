package app.koyura.app.browse.globalsearch

import app.koyura.app.browse.BrowseService
import app.koyura.app.browse.SourceInfo
import app.koyura.app.browse.WorkSummary
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** 全局搜索允许的源键过滤器（生产为 NSFW/隐藏偏好计算）；未提供时不过滤。 */
val LocalSearchSourceFilter = staticCompositionLocalOf<(suspend () -> Set<String>)?> { null }

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
 *
 * 来源候选先经 NSFW/隐藏过滤：注入 `allowedSourceKeys`（生产为按源偏好计算的可搜索键）
 * 后扇出只覆盖该集合；过滤实现失败回退全量扇出，保证过滤层异常不湮没搜索能力。
 */
class GlobalSearchService(
    private val browse: BrowseService?,
    private val allowedSourceKeys: (suspend () -> Set<String>)? = null,
) {
    /** 当前已安装来源（名称用于分组头）；未接服务时抛错，由会话记整体错误。 */
    suspend fun sources(): List<SourceInfo> {
        val service = browse ?: throw IllegalStateException("Browse is not connected")
        return service.loadSnapshot().sources
    }

    /**
     * 并行搜索允许的来源（过滤后），返回顺序与 [sources] 一致。
     * `sources` 参数应已是展示候选；本函数对其中不在允许集的源跳过扇出。
     */
    suspend fun search(
        sources: List<SourceInfo>,
        query: String,
    ): List<GlobalSearchSourceResult> = coroutineScope {
        val allowed = allowedKeysOrNull()
        val targets = if (allowed == null) sources else sources.filter { it.id in allowed }
        targets.map { source ->
            async { searchOne(source.id, source.name, query) }
        }.awaitAll()
    }

    /**
     * 生产装配的过滤键；失败时返回 null（调用方回退全量扇出）。
     */
    private suspend fun allowedKeysOrNull(): Set<String>? = try {
        allowedSourceKeys?.invoke()
    } catch (_: Exception) {
        null
    }

    /** 收缩展示候选：只保留允许集内的源（无过滤器时原样返回）。 */
    suspend fun filterSources(sources: List<SourceInfo>): List<SourceInfo> {
        val allowed = allowedKeysOrNull() ?: return sources
        return sources.filter { it.id in allowed }
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
