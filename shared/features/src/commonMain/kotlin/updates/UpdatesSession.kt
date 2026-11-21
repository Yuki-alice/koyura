package app.koyura.features.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 更新页状态座：注入仓储（生产为扫描引擎桥）的唯一读者。
 *
 * 并发规则沿用 LibrarySession：失败只记 `actionError`，已提交的快照原样保留；
 * 取消不是失败，不记错误。
 */
class UpdatesSession(
    private val repository: UpdatesRepository,
) {
    var loadState: UpdatesLoadState by mutableStateOf(UpdatesLoadState.Loading)
        private set

    var entries: List<UpdateWork> by mutableStateOf(emptyList())
        private set

    /** 上次扫描失败的作品（更新错误入口的数据源）。 */
    var failures: List<UpdateFailure> by mutableStateOf(emptyList())
        private set

    /** 顶栏筛选框的标题 query（页内状态，不进返回栈）。 */
    var query: String by mutableStateOf("")
        private set

    /** 标题筛选后的条目；query 为空时即全量。 */
    val visibleEntries: List<UpdateWork>
        get() {
            val needle = query.trim()
            if (needle.isEmpty()) return entries
            return entries.filter { it.title.contains(needle, ignoreCase = true) }
        }

    fun updateQuery(value: String) {
        query = value
    }

    var actionError: String? by mutableStateOf(null)
        private set

    /** 待读章节总数（底栏 Updates 徽标的数据源）。 */
    val unreadCount: Int
        get() = entries.sumOf { it.chapters.size }

    suspend fun refresh() = load { repository.loadUpdates() }

    /** 顶栏刷新按钮：触发一次真实扫描（生产实现），不改动 feed 读取语义。 */
    suspend fun checkNow() = load { repository.checkNow() }

    /** 只重试上次扫描失败的作品。 */
    suspend fun retryFailures() = load { repository.retryFailures() }

    private suspend fun load(source: suspend () -> List<UpdateWork>) {
        loadState = UpdatesLoadState.Loading
        try {
            entries = source()
            failures = repository.failures()
            actionError = null
            loadState = UpdatesLoadState.Ready
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            actionError = failure.message?.takeIf { it.isNotBlank() } ?: "Couldn't load updates"
            loadState = UpdatesLoadState.Failed(actionError!!)
        }
    }

    fun clearError() {
        actionError = null
    }
}

/** 注入的更新仓储；未装配时（测试与预览）落回默认 Fake。 */
val LocalUpdatesRepository = staticCompositionLocalOf<UpdatesRepository?> { null }

/**
 * 页签级会话。不传仓储时用 remember 持稳默认 Fake：默认参数在每次重组
 * 都会新建实例，会让 remember 的 key 失稳、会话随重组被重置。
 */
@Composable
fun rememberUpdatesSession(
    repository: UpdatesRepository? = LocalUpdatesRepository.current,
): UpdatesSession {
    val resolved = repository ?: remember { FakeUpdatesRepository() }
    return remember(resolved) { UpdatesSession(resolved) }
}
