package app.koyura.app.account

import app.koyura.data.extension.ExtensionService
import app.koyura.domain.extension.SourceAccountFieldType
import app.koyura.domain.extension.SourceLoginState
import app.koyura.features.account.AccountFieldConfig
import app.koyura.features.account.AccountFieldType
import app.koyura.features.account.AccountInfoItem
import app.koyura.features.account.AccountRepository
import app.koyura.features.account.AccountState

/**
 * 生产账号仓储：`ExtensionService` 的源账号查询与动作到界面层 [AccountRepository] 的唯一桥。
 *
 * - 源未声明 `account` 配置映射 [AccountState.NotConfigured]，详情页整块隐藏（Venera 同）。
 * - 登录态区分表单/网页：重登回填表单仅表单登录显示，对齐 Venera `data["account"]` 标记语义。
 * - 网页登录复用会话装配的平台浏览器；平台未提供（桌面）时 [captureWebLogin] 返回 false。
 */
class ProductionAccountRepository(
    private val extensions: ExtensionService,
) : AccountRepository {

    override suspend fun state(sourceKey: String): AccountState {
        val config = extensions.sourceAccountConfig(sourceKey) ?: return AccountState.NotConfigured
        return when (extensions.sourceLoginState(sourceKey)) {
            SourceLoginState.LOGGED_OUT -> AccountState.LoggedOut(
                fields = config.fields.map { AccountFieldConfig(it.label, it.type.toFeature()) },
                registerUrl = config.registerWebsite,
                webLoginSupported = config.declaresWebLogin && extensions.accounts.supportsWebLogin,
            )
            SourceLoginState.FORM -> AccountState.LoggedIn(formLogin = true, infoItems = infoItems(sourceKey))
            SourceLoginState.WEB -> AccountState.LoggedIn(formLogin = false, infoItems = infoItems(sourceKey))
        }
    }

    override suspend fun login(sourceKey: String, values: List<String>): Boolean =
        extensions.accounts.loginForm(sourceKey, values)

    override suspend fun captureWebLogin(sourceKey: String): Boolean {
        if (!extensions.accounts.supportsWebLogin) return false
        return extensions.accounts.captureWebLogin(sourceKey)?.complete() ?: false
    }

    override suspend fun logout(sourceKey: String): Boolean {
        extensions.accounts.logout(sourceKey)
        return true
    }

    private suspend fun infoItems(sourceKey: String): List<AccountInfoItem> =
        extensions.sourceAccountInfo(sourceKey).map { AccountInfoItem(it.title, it.value) }
}

private fun SourceAccountFieldType.toFeature(): AccountFieldType = when (this) {
    SourceAccountFieldType.TEXT -> AccountFieldType.TEXT
    SourceAccountFieldType.PASSWORD -> AccountFieldType.PASSWORD
    SourceAccountFieldType.COOKIES -> AccountFieldType.COOKIES
}
