package app.koyura.features.library

import androidx.compose.runtime.Composable
import app.koyura.features.downloads.DownloadQueueRepository
import app.koyura.features.downloads.LocalDownloadQueueRepository
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Koin 持有、按宿主域化的书架 ViewModel（阶段 0 样板）。
 *
 * 调用方必须处于 KoinApplication 内（生产路径由 ExtensionApp 提供，
 * 测试/预览需自行包裹 KoinApplication，否则按 Koin 既有行为抛错）。
 * 仓储经 parameters 传入，沿用既有 CompositionLocal 装配，不在 Koin 里持有可空全局单例。
 */
@Composable
fun rememberLibraryViewModel(
    repository: LibraryRepository? = LocalLibraryRepository.current,
    downloads: DownloadQueueRepository? = LocalDownloadQueueRepository.current,
): LibraryViewModel = koinViewModel<LibraryViewModel>(
    parameters = { parametersOf(repository, downloads) },
)
