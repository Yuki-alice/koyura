package app.koyura.features.reader.comic

import androidx.compose.runtime.Composable
import app.koyura.domain.reader.ReaderService
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Koin 持有的阅读器 Store 持有者（阶段 1 第三棒）。
 *
 * 包入调用方传入的仓储（可空：无服务装配时 Store 保持初始态意图空转，
 * 与 [ReaderSession] 的 null-service 语义一致）；UI 观察 [ReaderStoreHolder.uiState]。
 * 单测直接构造 [ReaderStoreFactory]（零 Koin 可跑）。
 *
 * 调用方必须处于 Koin 作用域内（生产由外壳自宿主的隔离 Koin 提供）。
 */
@Composable
fun rememberReaderStoreHolder(
    service: ReaderService?,
): ReaderStoreHolder = koinViewModel<ReaderStoreHolder>(
    parameters = { parametersOf(service) },
)
