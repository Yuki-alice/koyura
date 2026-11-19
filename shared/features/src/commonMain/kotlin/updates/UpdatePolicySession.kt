package app.koyura.features.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException

/**
 * 智能更新设置会话：读策略、整份覆盖写入并以仓储提交结果为准（同 UpdatesSession 纪律：
 * 失败只记 `actionError`，已提交策略原样保留；取消不算失败）。
 */
class UpdatePolicySession(
    private val repository: UpdatePolicyRepository,
) {
    var loadState: UpdatePolicyLoadState by mutableStateOf(UpdatePolicyLoadState.Loading)
        private set

    /** 已提交策略；加载失败时保留上一次成功读取的值。 */
    var policy: UpdatePolicy by mutableStateOf(UpdatePolicy())
        private set

    var actionError: String? by mutableStateOf(null)
        private set

    suspend fun refresh() {
        loadState = UpdatePolicyLoadState.Loading
        try {
            policy = repository.policy()
            loadState = UpdatePolicyLoadState.Ready
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            loadState = UpdatePolicyLoadState.Failed(failure.userMessage())
        }
    }

    /** 写入整份策略；成功后重读仓储，界面以提交结果为准。失败返回 false 并记提示。 */
    suspend fun apply(next: UpdatePolicy): Boolean {
        return try {
            repository.apply(next.normalized())
            policy = repository.policy()
            actionError = null
            loadState = UpdatePolicyLoadState.Ready
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            actionError = failure.userMessage()
            false
        }
    }

    fun clearError() {
        actionError = null
    }

    private fun Exception.userMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "Couldn't update smart updates"
}

/** 注入的策略仓储；未装配时（测试与预览）落回默认 Fake。 */
val LocalUpdatePolicyRepository = staticCompositionLocalOf<UpdatePolicyRepository?> { null }

@Composable
fun rememberUpdatePolicySession(
    repository: UpdatePolicyRepository? = LocalUpdatePolicyRepository.current,
): UpdatePolicySession {
    val resolved = repository ?: remember { FakeUpdatePolicyRepository() }
    return remember(resolved) { UpdatePolicySession(resolved) }
}
