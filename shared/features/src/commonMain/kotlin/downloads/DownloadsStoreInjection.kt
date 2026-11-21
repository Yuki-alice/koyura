package app.koyura.features.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Koin 持有的下载 Store 持有者（阶段 1 样板下载迁移第一棒）。
 *
 * 包入调用方传入的仓储；UI 观察 [DownloadsStoreHolder.uiState]、
 * 收 [DownloadsStoreHolder.labels] 做提示。单测直接构造
 * [DownloadsStoreFactory]（零 Koin 可跑）。
 *
 * 调用方必须处于 Koin 作用域内（生产与测试直挂均由外壳自宿主的隔离 Koin 提供）。
 * fallback 仓储在 helper 内 memo保证参数稳定否则 koinViewModel 会反复建新实例。
 */
@Composable
fun rememberDownloadsStoreHolder(
    repository: DownloadQueueRepository? = LocalDownloadQueueRepository.current,
): DownloadsStoreHolder {
    val resolved = repository ?: remember { FakeDownloadRepository() }
    return koinViewModel<DownloadsStoreHolder>(
        parameters = { parametersOf(resolved) },
    )
}
