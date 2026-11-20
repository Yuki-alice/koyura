package app.koyura.app.updates

import app.koyura.data.session.ExtensionSession
import app.koyura.data.session.openExtensionSession
import app.koyura.source.runtime.VeneraEnvironment
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class BackgroundLibraryUpdatesTest {
    @Test
    fun reusesTheForegroundSessionAndClosesTheColdBackgroundSession() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "background-updates-${Uuid.random()}"
        val access = BackgroundLibraryUpdates()
        val environment = VeneraEnvironment("1.16.0", "en", "test")
        try {
            val foreground = access.openForeground { openExtensionSession(root, environment) }
            try {
                val result = access.runDue { error("A background wake opened a second session") }
                assertFalse(result.ran)
                assertFalse(foreground.isClosed)
            } finally {
                access.closeForeground(foreground)
            }
            assertTrue(foreground.isClosed)

            var cold: ExtensionSession? = null
            val result = access.runDue { openExtensionSession(root, environment).also { cold = it } }
            assertFalse(result.ran)
            assertTrue(assertNotNull(cold).isClosed)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }
}
