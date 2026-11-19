package app.koyura.data.session

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WindowsSourceSecretStoreTest {
    @Test
    fun encodesEachKeyAsOneCaseSensitiveSafeFilename() {
        for (key in listOf("../outside", "CON", "folder\\file", "source")) {
            assertTrue(windowsSessionFilename(key).matches(Regex("[a-f0-9]{64}\\.session")))
        }
        assertNotEquals(windowsSessionFilename("name"), windowsSessionFilename("NAME"))
    }
}
