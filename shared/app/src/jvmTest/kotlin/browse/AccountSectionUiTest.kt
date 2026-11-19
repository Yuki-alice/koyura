package app.koyura.app.browse

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import app.koyura.designsystem.ReaderTheme
import app.koyura.features.account.AccountRepository
import app.koyura.features.account.AccountSection
import app.koyura.features.account.FakeAccountRepository
import app.koyura.features.account.accountContinueTag
import app.koyura.features.account.accountFieldTag
import app.koyura.features.account.accountLoginToggleTag
import app.koyura.features.account.accountLogoutConfirmTag
import app.koyura.features.account.accountLogoutTag
import app.koyura.features.account.accountRegisterTag
import app.koyura.features.account.accountReloginTag
import app.koyura.features.account.accountSectionTag
import app.koyura.features.account.accountWebLoginTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 账号区五形态回归：无配置/纯表单/纯网页/混合/已登录 + 登录登出与网页登录反馈。
 */
class AccountSectionUiTest {
    private fun ComposeUiTest.render(
        sourceKey: String,
        repository: AccountRepository = FakeAccountRepository(),
        messages: MutableList<String> = mutableListOf(),
        loading: MutableList<Boolean> = mutableListOf(),
        onOpenUrl: (String) -> Unit = {},
    ) {
        setContent {
            ReaderTheme {
                AccountSection(
                    sourceKey = sourceKey,
                    sourceName = "Demo Source",
                    repository = repository,
                    onMessage = { messages += it },
                    onLoadingChange = { loading += it },
                    onOpenUrl = onOpenUrl,
                )
            }
        }
        waitForIdle()
    }

    @Test
    fun unconfiguredSourceRendersNothing() = runComposeUiTest {
        render("none")
        onAllNodesWithTag(accountSectionTag("none")).assertCountEquals(0)
    }

    @Test
    fun formOnlyShowsFieldsWithoutWebOrRegister() = runComposeUiTest {
        render("form-only")
        onNodeWithTag(accountSectionTag("form-only")).assertIsDisplayed()
        onNodeWithTag(accountLoginToggleTag("form-only")).performClick()
        waitForIdle()
        onNodeWithTag(accountFieldTag("form-only", 0)).assertIsDisplayed()
        onNodeWithTag(accountFieldTag("form-only", 1)).assertIsDisplayed()
        onAllNodesWithTag(accountWebLoginTag("form-only")).assertCountEquals(0)
        onAllNodesWithTag(accountRegisterTag("form-only")).assertCountEquals(0)
    }

    @Test
    fun webOnlyShowsWebLoginWithoutFields() = runComposeUiTest {
        render("web-only")
        onNodeWithTag(accountLoginToggleTag("web-only")).performClick()
        waitForIdle()
        onAllNodesWithTag(accountFieldTag("web-only", 0)).assertCountEquals(0)
        onNodeWithTag(accountWebLoginTag("web-only")).assertIsDisplayed()
        onNodeWithTag(accountRegisterTag("web-only")).assertIsDisplayed()
    }

    @Test
    fun mixedLoginFlowAndLogoutWithConfirm() = runComposeUiTest {
        val messages = mutableListOf<String>()
        var openedUrl: String? = null
        render("mixed", messages = messages, onOpenUrl = { openedUrl = it })

        // 注册外链透出配置地址。
        onNodeWithTag(accountLoginToggleTag("mixed")).performClick()
        waitForIdle()
        onNodeWithTag(accountRegisterTag("mixed")).performClick()
        waitForIdle()
        assertEquals("https://example.invalid/register", openedUrl)

        // 三字段填满后提交登录成功。
        onNodeWithTag(accountFieldTag("mixed", 0)).performTextInput("user")
        onNodeWithTag(accountFieldTag("mixed", 1)).performTextInput("pass")
        onNodeWithTag(accountFieldTag("mixed", 2)).performTextInput("cookie")
        onNodeWithTag(accountContinueTag("mixed")).performClick()
        waitForIdle()

        // 已登录：info 行 + 重登行 + 注销行。
        onNodeWithTag(accountReloginTag("mixed")).assertIsDisplayed()
        onNodeWithTag(accountLogoutTag("mixed")).performClick()
        waitForIdle()
        onNodeWithTag(accountLogoutConfirmTag("mixed")).performClick()
        waitForIdle()

        // 注销后回到未登录。
        onNodeWithTag(accountLoginToggleTag("mixed")).assertIsDisplayed()
        assertTrue(messages.isEmpty())
    }

    @Test
    fun failedLoginSurfacesMessageAndKeepsForm() = runComposeUiTest {
        val messages = mutableListOf<String>()
        val repo = FakeAccountRepository(failLoginFor = mutableSetOf("mixed"))
        render("mixed", repository = repo, messages = messages)

        onNodeWithTag(accountLoginToggleTag("mixed")).performClick()
        waitForIdle()
        onNodeWithTag(accountFieldTag("mixed", 0)).performTextInput("user")
        onNodeWithTag(accountFieldTag("mixed", 1)).performTextInput("pass")
        onNodeWithTag(accountFieldTag("mixed", 2)).performTextInput("cookie")
        onNodeWithTag(accountContinueTag("mixed")).performClick()
        waitForIdle()

        assertEquals(1, messages.size)
        onNodeWithTag(accountLoginToggleTag("mixed")).assertIsDisplayed()
    }

    @Test
    fun webLoginSuccessAndFailureFeedback() = runComposeUiTest {
        val messages = mutableListOf<String>()
        val loading = mutableListOf<Boolean>()
        val repo = FakeAccountRepository()
        render("mixed", repository = repo, messages = messages, loading = loading)

        onNodeWithTag(accountLoginToggleTag("mixed")).performClick()
        waitForIdle()
        onNodeWithTag(accountWebLoginTag("mixed")).performClick()
        waitForIdle()

        // 默认 Fake 网页登录失败：进行中态出现过，失败提示送达。
        assertEquals(listOf(true, false), loading)
        assertEquals(1, messages.size)

        // 成功后进入已登录（非表单登录，无重登行）。
        repo.setWebLoginResult("mixed", true)
        onNodeWithTag(accountWebLoginTag("mixed")).performClick()
        waitForIdle()
        onAllNodesWithTag(accountReloginTag("mixed")).assertCountEquals(0)
    }

    @Test
    fun loggedInShowsInfoAndReloginForm() = runComposeUiTest {
        render("mixed")

        // 先走完整登录，表单字段被会话记住。
        onNodeWithTag(accountLoginToggleTag("mixed")).performClick()
        waitForIdle()
        onNodeWithTag(accountFieldTag("mixed", 0)).performTextInput("user")
        onNodeWithTag(accountFieldTag("mixed", 1)).performTextInput("pass")
        onNodeWithTag(accountFieldTag("mixed", 2)).performTextInput("cookie")
        onNodeWithTag(accountContinueTag("mixed")).performClick()
        waitForIdle()

        onNodeWithTag("account-info-mixed-0").assertIsDisplayed()
        onNodeWithTag(accountReloginTag("mixed")).performClick()
        waitForIdle()
        onNodeWithTag(accountFieldTag("mixed", 0)).assertIsDisplayed()
    }

    @Test
    fun preLoggedInShowsInfoWithoutReloginFields() = runComposeUiTest {
        render("mixed-logged")

        onNodeWithTag(accountSectionTag("mixed-logged")).assertIsDisplayed()
        onNodeWithTag("account-info-mixed-logged-0").assertIsDisplayed()
    }
}
