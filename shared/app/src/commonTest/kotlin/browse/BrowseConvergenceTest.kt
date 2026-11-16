package app.koyura.app.browse

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Concurrency regressions for snapshot convergence.
 *
 * Every case asserts the UI state matches the service's committed snapshot, not merely that one
 * call returned true.
 */
class BrowseConvergenceTest {
    private val updated = "store:1::com.example.updates"
    private val installed = "store:1::com.example.installed"

    @Test
    fun lateSuccessfulUpdateReachesUiWithoutResurrectingRemovedRow() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()
        val delayed = FakeBrowseService.Gate()
        service.gate = delayed
        service.gateKey = "update:$updated"

        coroutineScope {
            val update = launch { assertTrue(session.update(updated)) }
            yield()
            assertEquals(OperationStatus.Running, session.statusOf(updated))

            // A later, different mutation commits first.
            assertTrue(session.uninstall(installed))
            assertTrue(session.state.extensions.none { it.key == installed })

            delayed.open()
            update.join()
        }

        assertEquals(OperationStatus.Succeeded, session.statusOf(updated))
        assertTrue(session.state.extensions.none { it.key == installed }, "removed row must stay removed")

        val committed = service.snapshot.extensions.single { it.key == updated }
        val displayed = session.state.extensions.single { it.key == updated }
        assertEquals(committed.versionName, displayed.versionName, "UI must show the committed version")
        assertEquals("1.1", displayed.versionName)
        assertEquals(0, session.state.updateCount, "badge must reflect the committed update")
    }

    @Test
    fun laterFailureDoesNotClobberEarlierSuccess() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()

        // The uninstall commits, then a separate update fails.
        assertTrue(session.uninstall(installed))
        service.failures["update:$updated"] = "network down"

        coroutineScope {
            val update = launch { assertFalse(session.update(updated)) }
            yield()
            update.join()
        }

        // Failure is reported on its own row only, and the earlier commit is still visible.
        val failed = assertIs<OperationStatus.Failed>(session.statusOf(updated))
        assertEquals(OperationKind.Update, failed.kind)
        assertEquals("network down", failed.message)
        assertTrue(session.state.extensions.none { it.key == installed }, "earlier uninstall survives")
        assertEquals(
            service.snapshot.extensions.single { it.key == updated }.versionName,
            session.state.extensions.single { it.key == updated }.versionName,
        )
    }

    @Test
    fun cancelledRunLeavesTargetRetryable() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()

        val gate = FakeBrowseService.Gate()
        service.gate = gate
        service.gateKey = "update:$updated"

        coroutineScope {
            val job = launch { session.update(updated) }
            yield()
            assertEquals(OperationStatus.Running, session.statusOf(updated))
            job.cancel()
            job.join()
        }

        // The target is no longer stuck in Running, so a retry is allowed.
        assertTrue(session.statusOf(updated) !is OperationStatus.Running)
        service.gate = null
        assertTrue(session.update(updated), "retry after cancellation must be accepted")
        assertEquals(OperationStatus.Succeeded, session.statusOf(updated))
        assertEquals(0, session.state.updateCount)
    }

    @Test
    fun parallelSuccessesConvergeOnNewestCommittedSnapshot() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()

        coroutineScope {
            val first = launch { session.uninstall(installed) }
            val second = launch { session.update(updated) }
            first.join()
            second.join()
        }

        assertEquals(service.snapshot.extensions.map { it.key }.sorted(), session.state.extensions.map { it.key }.sorted())
        assertTrue(session.state.extensions.none { it.key == installed })
        assertEquals("1.1", session.state.extensions.single { it.key == updated }.versionName)
        assertEquals(0, session.state.updateCount)
    }
}
