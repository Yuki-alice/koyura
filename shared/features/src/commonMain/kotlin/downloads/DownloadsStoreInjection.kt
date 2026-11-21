package app.koyura.features.downloads

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Koin 持有的下载 Store 持有者（阶段 1 样板下载迁移第一棒）。
 *
 * 包入调用方传入的仓储；UI 观察 [DownloadsStoreHolder.uiState]、
 * 收 [DownloadsStoreHolder.labels] 做提示。无 Koin 的测试直接构造
 * [DownloadsStoreFactory]（单测即此路径零 Koin 可跑）。
 *
 * 调用方必须处于 Koin 作用域内（生产由外壳自宿主的隔离 Koin 提供）。
 */
@Composable
fun rememberDownloadsStoreHolder(
    repository: DownloadQueueRepository = LocalDownloadQueueRepository.current ?: FakeDownloadRepository(),
): DownloadsStoreHolder = koinViewModel<DownloadsStoreHolder>(
    parameters = { parametersOf(repository) },
)
