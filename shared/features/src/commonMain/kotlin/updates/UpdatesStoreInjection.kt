package app.koyura.features.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Koin 持有的更新 Store 持有者（阶段 1 第二棒）。
 *
 * 包入调用方传入的仓储；UI 观察 [UpdatesStoreHolder.uiState]、
 * 收 [UpdatesStoreHolder.labels] 做提示。单测直接构造
 * [UpdatesStoreFactory]（零 Koin 可跑）。
 *
 * 调用方必须处于 Koin 作用域内（生产与测试直挂均由外壳自宿主的隔离 Koin 提供）。
 * fallback 仓储在 helper 内 memo保证参数稳定否则 koinViewModel 会反复建新实例。
 */
@Composable
fun rememberUpdatesStoreHolder(
    repository: UpdatesRepository? = LocalUpdatesRepository.current,
): UpdatesStoreHolder {
    val resolved = repository ?: remember { FakeUpdatesRepository() }
    return koinViewModel<UpdatesStoreHolder>(
        parameters = { parametersOf(resolved) },
    )
}
