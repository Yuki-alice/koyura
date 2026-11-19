package app.koyura.features.account

/**
 * 账号 Fake 仓储：五形态由源 key 区分（会话与 UI 测试照此写）
 * - 无配置：`state` 返回 `NotConfigured`
 * - 纯表单：用户名 + 密码（`login != null`，无 cookie）
 * - 纯网页：无表单字段，仅 web 登录
 * - 混合：用户名 + 密码 + Cookie 字段 + 注册/网页入口
 * - 已登录：`LoggedIn` 带 info 行（`mixed-logged` 表单登录可重登，
 *   `web-logged` 非表单登录无重登行）
 *
 * 默认接受任意非空表单登录；以 `failLoginFor` 注入失败源。
 */
class FakeAccountRepository(
    private val webLoginResults: MutableMap<String, Boolean> = mutableMapOf(),
    private val failLoginFor: MutableSet<String> = mutableSetOf(),
    private val loggedIn: MutableMap<String, LoggedInSeed> = mutableMapOf(
        "mixed-logged" to LoggedInSeed(formLogin = true),
        "web-logged" to LoggedInSeed(formLogin = false),
    ),
) : AccountRepository {
    data class LoggedInSeed(val formLogin: Boolean)

    companion object {
        fun formFields(): List<AccountFieldConfig> = listOf(
            AccountFieldConfig("Username", AccountFieldType.TEXT),
            AccountFieldConfig("Password", AccountFieldType.PASSWORD),
        )

        fun mixedFields(): List<AccountFieldConfig> = listOf(
            AccountFieldConfig("Username", AccountFieldType.TEXT),
            AccountFieldConfig("Password", AccountFieldType.PASSWORD),
            AccountFieldConfig("session", AccountFieldType.COOKIES),
        )
    }

    override suspend fun state(sourceKey: String): AccountState {
        loggedIn[sourceKey]?.let { seed ->
            return AccountState.LoggedIn(
                formLogin = seed.formLogin,
                infoItems = listOf(
                    AccountInfoItem("Username", "demo"),
                    AccountInfoItem("Expires", "2027-01-01"),
                    AccountInfoItem("Note", null),
                ),
            )
        }
        return when (sourceKey) {
            "none" -> AccountState.NotConfigured
            "form-only" -> AccountState.LoggedOut(
                fields = formFields(),
                registerUrl = null,
                webLoginSupported = false,
            )
            "web-only" -> AccountState.LoggedOut(
                fields = emptyList(),
                registerUrl = "https://example.invalid/register",
                webLoginSupported = true,
            )
            else -> AccountState.LoggedOut(
                fields = mixedFields(),
                registerUrl = "https://example.invalid/register",
                webLoginSupported = true,
            )
        }
    }

    override suspend fun login(sourceKey: String, values: List<String>): Boolean {
        if (sourceKey in failLoginFor) return false
        val fields = (state(sourceKey) as? AccountState.LoggedOut)?.fields ?: return false
        if (values.size != fields.size || values.any { it.isBlank() }) return false
        loggedIn[sourceKey] = LoggedInSeed(formLogin = true)
        return true
    }

    override suspend fun captureWebLogin(sourceKey: String): Boolean {
        val ok = webLoginResults[sourceKey] ?: false
        if (ok) loggedIn[sourceKey] = LoggedInSeed(formLogin = false)
        return ok
    }

    override suspend fun logout(sourceKey: String): Boolean {
        return loggedIn.remove(sourceKey) != null
    }

    fun setWebLoginResult(sourceKey: String, ok: Boolean) {
        webLoginResults[sourceKey] = ok
    }
}
