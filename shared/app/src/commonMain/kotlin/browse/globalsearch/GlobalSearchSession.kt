package app.koyura.app.browse.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.koyura.app.browse.BrowseService
import app.koyura.app.browse.LocalBrowseService
import app.koyura.app.browse.WorkSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 单个来源在列表里的状态：加载中 / 已有结果 / 该源失败。 */
sealed interface GlobalSearchSourceState {
    data object Loading : GlobalSearchSourceState

    data class Ready(val works: List<WorkSummary>) : GlobalSearchSourceState

    data class Failed(val message: String) : GlobalSearchSourceState
}

/** 按源分组的一组结果（组头为源名 + 状态）。 */
data class GlobalSearchGroup(
    val sourceKey: String,
    val sourceName: String,
    val state: GlobalSearchSourceState,
) {
    val works: List<WorkSummary>
        get() = (state as? GlobalSearchSourceState.Ready)?.works.orEmpty()
}

/**
 * 全局搜索会话：照 `BrowseSession` 的写法持有查询与逐源结果。
 *
 * - 每次提交先按当前来源列表铺加载态，再并发扇出；每个源完成即更新自己那一组，
 *   组间不互相覆盖（部分失败只标记该源）。
 * - 整体失败（列源失败 / 未接服务）记 `error`；取消不算失败。
 * - 结果不是快照数据，不做 `BrowseSession` 的收敛计数。
 */
class GlobalSearchSession(
    browse: BrowseService?,
    private val service: GlobalSearchService = GlobalSearchService(browse),
) {
    var query: String by mutableStateOf("")
        private set

    var groups: List<GlobalSearchGroup> by mutableStateOf(emptyList())
        private set

    /** 是否已执行过至少一次搜索（区分「未搜索」与「无结果」空态）。 */
    var hasSearched: Boolean by mutableStateOf(false)
        private set

    var searching: Boolean by mutableStateOf(false)
        private set

    /** 整体错误（列源失败、未接服务）；单源错误在各自分组里。 */
    var error: String? by mutableStateOf(null)
        private set

    val resultCount: Int
        get() = groups.sumOf { it.works.size }

    fun updateQuery(value: String) {
        query = value
    }

    fun clearError() {
        error = null
    }

    /** 提交当前查询：逐源并发搜索，返回时所有源都已落定。 */
    suspend fun submit() {
        val needle = query.trim()
        if (needle.isEmpty()) {
            groups = emptyList()
            hasSearched = false
            error = null
            return
        }
        searching = true
        error = null
        try {
            val sources = service.sources()
            groups = sources.map { GlobalSearchGroup(it.id, it.name, GlobalSearchSourceState.Loading) }
            hasSearched = true
            coroutineScope {
                sources.forEach { source ->
                    launch {
                        apply(service.searchOne(source.id, source.name, needle))
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message?.takeIf { it.isNotBlank() } ?: "Search failed"
            groups = emptyList()
            hasSearched = true
        } finally {
            searching = false
        }
    }

    /** 只重试某一个失败源（其余分组保持不动）。 */
    suspend fun retry(sourceKey: String) {
        val group = groups.firstOrNull { it.sourceKey == sourceKey } ?: return
        if (group.state is GlobalSearchSourceState.Loading) return
        groups = groups.map {
            if (it.sourceKey == sourceKey) it.copy(state = GlobalSearchSourceState.Loading) else it
        }
        apply(service.searchOne(group.sourceKey, group.sourceName, query.trim()))
    }

    private fun apply(result: GlobalSearchSourceResult) {
        val state = result.error?.let { GlobalSearchSourceState.Failed(it) }
            ?: GlobalSearchSourceState.Ready(result.works)
        groups = groups.map { group ->
            if (group.sourceKey == result.sourceKey) group.copy(state = state) else group
        }
    }
}

/** 全局搜索会话随路由创建（结果页是独立子路由，不跨页签保留）。 */
@Composable
fun rememberGlobalSearchSession(
    browse: BrowseService? = LocalBrowseService.current,
): GlobalSearchSession = remember(browse) { GlobalSearchSession(browse) }
