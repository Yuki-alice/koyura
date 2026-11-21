package app.koyura.features.library

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Koin 持有、按宿主域化的书架 ViewModel（阶段 0 样板）。
 *
 * 包入调用方传入的同一份 [LibrarySession]（与装配层持有的实例一致，不分叉），
 * Koin 只负责生命周期 + 域化；无 Koin 的测试/预览直接调 [LibraryScreen]
 * （其内部走 [LibrarySession.asUiStateFlow] 同一快照）。
 *
 * 调用方必须处于 Koin 作用域内（生产由外壳自宿主的隔离 Koin 提供）。
 * 现跟随宿主 ViewModelStoreOwner；按 NavKey 域化待多 VM 条目时再接。
 */
@Composable
fun rememberLibraryViewModel(
    session: LibrarySession,
): LibraryViewModel = koinViewModel<LibraryViewModel>(
    parameters = { parametersOf(session) },
)
