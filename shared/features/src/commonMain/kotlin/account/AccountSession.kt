package app.koyura.features.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 账号页状态座：单源账号状态的唯一读者，沿用 `UpdatesSession` 模式。
 *
 * 网页登录进行中由调用方经反馈宿主展示（`showLoading`/`showMessage`），
 * 本会话只记 `webLoggingIn` 位供行内指示器使用。
 */
class AccountSession(
    private val repository: AccountRepository,
    val sourceKey: String,
) {
    var state: AccountState by mutableStateOf(AccountState.NotConfigured)
        private set

    var errorMessage: String? by mutableStateOf(null)
        private set

    var webLoggingIn: Boolean by mutableStateOf(false)
        private set

    /** 最近一次未登录态的字段声明：已登录页「重新登录」回填表单用。 */
    var lastFields: List<AccountFieldConfig> by mutableStateOf(emptyList())
        private set

    suspend fun refresh() {
        try {
            val next = repository.state(sourceKey)
            if (next is AccountState.LoggedOut) lastFields = next.fields
            state = next
            errorMessage = null
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            errorMessage = failure.message?.takeIf { it.isNotBlank() } ?: "Couldn't load account"
        }
    }

    suspend fun login(values: List<String>): Boolean {
        return try {
            val ok = repository.login(sourceKey, values)
            if (ok) {
                errorMessage = null
                refresh()
            }
            ok
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            errorMessage = failure.message?.takeIf { it.isNotBlank() } ?: "Login failed"
            false
        }
    }

    suspend fun webLogin(): Boolean {
        webLoggingIn = true
        return try {
            val ok = repository.captureWebLogin(sourceKey)
            if (ok) {
                errorMessage = null
                refresh()
            }
            ok
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            errorMessage = failure.message?.takeIf { it.isNotBlank() } ?: "Web login failed"
            false
        } finally {
            webLoggingIn = false
        }
    }

    suspend fun logout(): Boolean {
        return try {
            val ok = repository.logout(sourceKey)
            if (ok) {
                errorMessage = null
                refresh()
            }
            ok
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            errorMessage = failure.message?.takeIf { it.isNotBlank() } ?: "Logout failed"
            false
        }
    }

    fun clearError() {
        errorMessage = null
    }
}

@Composable
fun rememberAccountSession(
    repository: AccountRepository,
    sourceKey: String,
): AccountSession = remember(repository, sourceKey) {
    AccountSession(repository, sourceKey)
}
