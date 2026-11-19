package app.koyura.features.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 更新页状态座：Fake 仓储的唯一读者。
 *
 * 并发规则沿用 LibrarySession：失败只记 `actionError`，已提交的快照原样保留；
 * 取消不是失败，不记错误。真实引擎由引擎轨接入，届时只替换注入的仓储。
 */
class UpdatesSession(
    private val repository: UpdatesRepository,
) {
    var loadState: UpdatesLoadState by mutableStateOf(UpdatesLoadState.Loading)
        private set

    var entries: List<UpdateWork> by mutableStateOf(emptyList())
        private set

    var actionError: String? by mutableStateOf(null)
        private set

    /** 待读章节总数（底栏 Updates 徽标的数据源）。 */
    val unreadCount: Int
        get() = entries.sumOf { it.chapters.size }

    suspend fun refresh() {
        loadState = UpdatesLoadState.Loading
        try {
            entries = repository.loadUpdates()
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

@Composable
fun rememberUpdatesSession(
    repository: UpdatesRepository = FakeUpdatesRepository(),
): UpdatesSession = remember(repository) { UpdatesSession(repository) }
