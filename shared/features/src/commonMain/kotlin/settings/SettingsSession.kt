package app.koyura.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 设置页状态座：仓储的唯一读者与写者，对外只暴露不可变快照。
 *
 * 每次更新先写仓储再发布新快照，保证「保存成功即生效」；不引入协程、Koin 或 ViewModel。
 */
class SettingsSession(
    private val repository: SettingsRepository,
) {
    private var _uiState by mutableStateOf(SettingsUiState(preferences = repository.load()))
    val uiState: SettingsUiState get() = _uiState

    /** master-detail 的选中项；手机 push 子页时由路由层驱动。 */
    fun selectSubpage(subpage: SettingsSubpage) {
        _uiState = _uiState.copy(selectedSubpage = subpage)
    }

    /** 整份偏好更新：调用方基于 [uiState] 的 preferences 做 copy，写回后立即生效。 */
    fun update(transform: (SettingsPreferences) -> SettingsPreferences) {
        val next = transform(_uiState.preferences)
        repository.save(next)
        _uiState = _uiState.copy(preferences = next)
    }
}

/** 注入的设置仓储；未装配时（预览与测试）落回内存 Fake。 */
val LocalSettingsRepository = staticCompositionLocalOf<SettingsRepository?> { null }

@Composable
fun rememberSettingsSession(
    repository: SettingsRepository? = LocalSettingsRepository.current,
): SettingsSession {
    val resolved = repository ?: remember { FakeSettingsRepository() }
    return remember(resolved) { SettingsSession(resolved) }
}
