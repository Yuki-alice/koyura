package app.koyura.features.more

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MoreSessionTest {
    @Test
    fun switchesStartOffAndToggleIndependently() {
        val session = MoreSession()
        assertFalse(session.downloadedOnly)
        assertFalse(session.incognito)

        session.updateDownloadedOnly(true)
        assertTrue(session.downloadedOnly)
        assertFalse(session.incognito)

        session.updateIncognito(true)
        assertTrue(session.downloadedOnly)
        assertTrue(session.incognito)

        session.updateDownloadedOnly(false)
        assertFalse(session.downloadedOnly)
        assertTrue(session.incognito)
    }
}
