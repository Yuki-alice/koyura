package app.koyura.features.account

/** 账号登录字段声明（Venera `AccountConfig`：用户名/密码/cookie 字段）。 */
data class AccountFieldConfig(val label: String, val type: AccountFieldType)

/** 登录字段形态：普通文本 / 密码（遮蔽）/ Cookie（遮蔽）。 */
enum class AccountFieldType {
    TEXT,
    PASSWORD,
    COOKIES,
}

/** 已登录信息行（Venera `infoItems`：标题 + 可空值）。 */
data class AccountInfoItem(val title: String, val value: String?)

/** 源账号状态（Venera `_buildAccount` 分支：未配置/未登录/已登录）。 */
sealed interface AccountState {
    data object NotConfigured : AccountState
    data class LoggedOut(val fields: List<AccountFieldConfig>, val registerUrl: String?, val webLoginSupported: Boolean) : AccountState
    data class LoggedIn(val formLogin: Boolean, val infoItems: List<AccountInfoItem>) : AccountState
}

/**
 * 源账号仓储（Venera 协议基线，Fake 先行、真实引擎后续桥接）。
 *
 * - `state` 为空即 `NotConfigured`（调用方整块隐藏，与 Venera 同）。
 * - `login` 按声明字段顺序传值；Cookie 字段由调用方按序拼好传入。
 * - `captureWebLogin` 平台浏览器不可用时由注入方返回 false。
 */
interface AccountRepository {
    suspend fun state(sourceKey: String): AccountState
    suspend fun login(sourceKey: String, values: List<String>): Boolean
    suspend fun captureWebLogin(sourceKey: String): Boolean
    suspend fun logout(sourceKey: String): Boolean
}
