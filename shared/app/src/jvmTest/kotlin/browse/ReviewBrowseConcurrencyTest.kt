package app.koyura.app.browse

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewBrowseConcurrencyTest {
    @Test
    fun lateSuccessfulMutationMustReachUiWithoutResurrectingRemovedRows() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()
        val delayed = FakeBrowseService.Gate()
        val updated = "store:1::com.example.updates"
        val removed = "store:1::com.example.installed"
        service.gate = delayed
        service.gateKey = "update:$updated"
        val update = launch { assertTrue(session.update(updated)) }
        yield()
        assertEquals(OperationStatus.Running, session.statusOf(updated))
        assertTrue(session.uninstall(removed))
        delayed.open()
        update.join()
        assertEquals(OperationStatus.Succeeded, session.statusOf(updated))
        assertTrue(session.state.extensions.none { it.key == removed })
        val actual = service.snapshot.extensions.single { it.key == updated }
        val displayed = session.state.extensions.single { it.key == updated }
        assertEquals(actual.versionName, displayed.versionName, "UI must show the committed version")
        assertEquals(0, session.state.updateCount, "Badge must reflect the committed update")
    }
}
