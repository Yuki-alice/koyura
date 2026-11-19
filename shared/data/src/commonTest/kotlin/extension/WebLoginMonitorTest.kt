package app.koyura.data.extension

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebLoginMonitorTest {
    @Test
    fun capturesOnlyTheAcceptedDocumentAfterNavigationSettles() = runTest {
        val old = WebLoginPage("https://source.test/first", "Accepted")
        val current = WebLoginPage("https://source.test/final", "Accepted")
        var page = old
        val visits = mutableListOf<WebLoginPage>()
        val result = WebLoginMonitor.awaitCapture(CompletableDeferred(), { page }, {
            visits += it
            page = current
            true
        }) { WebLoginCapture(it, emptyList(), JsonObject(emptyMap())) }
        assertEquals(listOf(old, current), visits)
        assertEquals(current, result?.page)
        assertNull(WebLoginMonitor.storage(old, """{"url":"https://source.test/final","title":"Accepted","storage":{}}"""))
    }

    @Test
    fun closingThePlatformViewCancelsTheProbe() = runTest {
        val dismissal = CompletableDeferred<Unit>().also { it.complete(Unit) }
        val result = WebLoginMonitor.awaitCapture(dismissal, { null }, { error("No page") }) {
            error("No capture")
        }
        assertNull(result)
    }
}
