package app.koyura.features.account

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountSessionTest {
    @Test
    fun unconfiguredStaysHidden() = runTest {
        val session = AccountSession(FakeAccountRepository(), "none")
        session.refresh()
        assertIs<AccountState.NotConfigured>(session.state)
        assertNull(session.errorMessage)
    }

    @Test
    fun formOnlyHasNoWebOrRegister() = runTest {
        val session = AccountSession(FakeAccountRepository(), "form-only")
        session.refresh()
        val state = session.state
        assertIs<AccountState.LoggedOut>(state)
        assertEquals(2, state.fields.size)
        assertNull(state.registerUrl)
        assertFalse(state.webLoginSupported)
    }

    @Test
    fun webOnlyHasNoFields() = runTest {
        val session = AccountSession(FakeAccountRepository(), "web-only")
        session.refresh()
        val state = session.state
        assertIs<AccountState.LoggedOut>(state)
        assertTrue(state.fields.isEmpty())
        assertTrue(state.webLoginSupported)
    }

    @Test
    fun mixedHasFormWebAndRegister() = runTest {
        val session = AccountSession(FakeAccountRepository(), "mixed")
        session.refresh()
        val state = session.state
        assertIs<AccountState.LoggedOut>(state)
        assertEquals(3, state.fields.size)
        assertTrue(state.webLoginSupported)
        assertEquals("https://example.invalid/register", state.registerUrl)
    }

    @Test
    fun loginTransitionsToLoggedInWithInfoItems() = runTest {
        val repo = FakeAccountRepository()
        val session = AccountSession(repo, "mixed")
        session.refresh()

        assertFalse(session.login(listOf("user", "pass", "")))
        assertIs<AccountState.LoggedOut>(session.state)

        assertTrue(session.login(listOf("user", "pass", "cookie")))
        val state = session.state
        assertIs<AccountState.LoggedIn>(state)
        assertTrue(state.formLogin)
        assertEquals(3, state.infoItems.size)
    }

    @Test
    fun failedLoginKeepsLoggedOut() = runTest {
        val repo = FakeAccountRepository(failLoginFor = mutableSetOf("mixed"))
        val session = AccountSession(repo, "mixed")
        session.refresh()
        assertFalse(session.login(listOf("user", "pass", "cookie")))
        assertIs<AccountState.LoggedOut>(session.state)
    }

    @Test
    fun webLoginSuccessAndFailure() = runTest {
        val repo = FakeAccountRepository()
        val session = AccountSession(repo, "mixed")
        session.refresh()

        assertFalse(session.webLogin())
        assertFalse(session.webLoggingIn)
        assertIs<AccountState.LoggedOut>(session.state)

        repo.setWebLoginResult("mixed", true)
        assertTrue(session.webLogin())
        val state = session.state
        assertIs<AccountState.LoggedIn>(state)
        assertFalse(state.formLogin)
    }

    @Test
    fun logoutReturnsToLoggedOut() = runTest {
        val session = AccountSession(FakeAccountRepository(), "mixed-logged")
        session.refresh()
        assertIs<AccountState.LoggedIn>(session.state)

        assertTrue(session.logout())
        assertIs<AccountState.LoggedOut>(session.state)
    }
}
