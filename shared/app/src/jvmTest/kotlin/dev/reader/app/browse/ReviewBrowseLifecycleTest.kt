package dev.reader.app.browse

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewBrowseLifecycleTest {
    @Test
    fun changingIndexUrlInvalidatesAnInFlightPreview() = runTest {
        val baseline = FakeBrowseService()
        val captured = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val service = object : BrowseService by baseline {
            override suspend fun previewStore(storeId: String): StoreCatalog {
                val previous = baseline.previewStore(storeId)
                captured.complete(Unit)
                release.await()
                return previous
            }
        }
        val session = BrowseSession(service)
        session.refresh()
        val id = "store:1"
        val preview = launch { session.previewStore(id) }
        captured.await()
        assertTrue(session.saveStore(id, "New repository", "https://example.invalid/new-index.json"))
        assertEquals("https://example.invalid/new-index.json", session.state.storeById(id)?.indexUrl)
        assertNull(session.state.catalogStates[id])
        release.complete(Unit)
        preview.join()
        assertNull(session.state.catalogStates[id], "Older preview must not be reinstated after the URL changes")
    }

    @Test
    fun cancellingMutationDoesNotShowFailure() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()
        val key = "store:1::com.example.updates"
        service.gate = FakeBrowseService.Gate()
        service.gateKey = "update:$key"
        val update = launch { session.update(key) }
        yield()
        assertEquals(OperationStatus.Running, session.statusOf(key))
        update.cancelAndJoin()
        assertNull(session.statusOf(key), "Cancelling a request must not present a failure")
    }
}
