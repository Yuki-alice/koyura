package app.koyura.features.statistics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException

/**
 * 统计页状态座：注入仓储的唯一读者，三态沿用 `UpdatesSession`（失败只记 `loadState` 的
 * Failed，已提交快照原样保留；取消不算失败）。
 */
class StatisticsSession(
    private val repository: StatisticsRepository,
) {
    var loadState: StatisticsLoadState by mutableStateOf(StatisticsLoadState.Loading)
        private set

    /** 已提交的统计快照；加载失败时保留上一次成功结果。 */
    var snapshot: StatisticsSnapshot by mutableStateOf(StatisticsSnapshot())
        private set

    suspend fun refresh() {
        loadState = StatisticsLoadState.Loading
        try {
            snapshot = repository.load()
            loadState = StatisticsLoadState.Ready
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val message = failure.message?.takeIf { it.isNotBlank() } ?: "Couldn't load statistics"
            loadState = StatisticsLoadState.Failed(message)
        }
    }
}

/** 注入的统计仓储；未装配时（测试与预览）落回默认 Fake。 */
val LocalStatisticsRepository = staticCompositionLocalOf<StatisticsRepository?> { null }

@Composable
fun rememberStatisticsSession(
    repository: StatisticsRepository? = LocalStatisticsRepository.current,
): StatisticsSession {
    val resolved = repository ?: remember { FakeStatisticsRepository() }
    return remember(resolved) { StatisticsSession(resolved) }
}
