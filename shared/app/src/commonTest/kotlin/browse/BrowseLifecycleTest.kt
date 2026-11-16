package app.koyura.app.browse

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lifecycle regressions: stale previews must die with the store state that created them, and
 * cancellation must be distinguishable from failure.
 */
class BrowseLifecycleTest {
    private val storeId = "store:1"

    private class GatedPreview(
        private val baseline: FakeBrowseService,
        private val between: suspend () -> Unit = {},
    ) : BrowseService by baseline {
        override suspend fun previewStore(storeId: String): StoreCatalog {
            val catalog = baseline.previewStore(storeId)
            between()
            return catalog
        }
    }

    @Test
    fun inFlightPreviewIsDroppedWhenIndexUrlChanges() = runTest {
        val captured = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val service = GatedPreview(FakeBrowseService()) {
            captured.complete(Unit)
            release.await()
        }
        val session = BrowseSession(service)
        session.refresh()

        val preview = launch { session.previewStore(storeId) }
        captured.await()
        assertTrue(session.saveStore(storeId, "New repository", "https://example.invalid/new-index.json"))
        assertNull(session.state.catalogStates[storeId])

        release.complete(Unit)
        preview.join()

        assertNull(session.state.catalogStates[storeId], "old preview must not be reinstated")
        assertEquals("https://example.invalid/new-index.json", session.state.storeById(storeId)?.indexUrl)
    }

    @Test
    fun inFlightPreviewIsDroppedWhenStoreIsRemoved() = runTest {
        val captured = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val service = GatedPreview(FakeBrowseService()) {
            captured.complete(Unit)
            release.await()
        }
        val session = BrowseSession(service)
        session.refresh()

        val preview = launch { session.previewStore(storeId) }
        captured.await()
        assertTrue(session.removeStore(storeId))
        release.complete(Unit)
        preview.join()

        assertNull(session.state.catalogStates[storeId])
    }

    @Test
    fun closingPreviewInvalidatesInFlightRequest() = runTest {
        val captured = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val service = GatedPreview(FakeBrowseService()) {
            captured.complete(Unit)
            release.await()
        }
        val session = BrowseSession(service)
        session.refresh()

        val preview = launch { session.previewStore(storeId) }
        captured.await()
        session.clearStoreCatalog(storeId)
        release.complete(Unit)
        preview.join()

        assertNull(session.state.catalogStates[storeId], "closed preview must stay closed")
    }

    @Test
    fun oldPreviewCannotLandAfterReopenedPreviewCompletes() = runTest {
        val slowRelease = CompletableDeferred<Unit>()
        var call = 0
        val service = GatedPreview(FakeBrowseService()) {
            call += 1
            // Only the first preview is parked; the reopened one returns immediately.
            if (call == 1) slowRelease.await()
        }
        val session = BrowseSession(service)
        session.refresh()

        val first = launch { session.previewStore(storeId) }
        yield()
        session.clearStoreCatalog(storeId)
        session.previewStore(storeId)
        val reopened = session.state.catalogStates[storeId]
        assertTrue(reopened is CatalogState.Ready, "reopened preview should be ready")

        slowRelease.complete(Unit)
        first.join()

        val settled = session.state.catalogStates[storeId]
        assertTrue(settled is CatalogState.Ready, "reopened preview must survive the older arrival")
        assertEquals(reopened, settled, "older arrival must not replace the reopened result")
    }

    @Test
    fun cancellingMutationClearsRunningWithoutFailure() = runTest {
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

        assertNull(session.statusOf(key), "cancellation must not present a failure")
    }

    @Test
    fun cancellingMutationKeepsCommittedStateAndAllowsRetry() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()
        val removed = "store:1::com.example.installed"
        val key = "store:1::com.example.updates"

        // Commit an uninstall first, then cancel an unrelated update.
        assertTrue(session.uninstall(removed))
        service.gate = FakeBrowseService.Gate()
        service.gateKey = "update:$key"
        val update = launch { session.update(key) }
        yield()
        update.cancelAndJoin()

        assertTrue(session.state.extensions.none { it.key == removed }, "committed state survives")
        assertNull(session.statusOf(key))

        service.gate = null
        assertTrue(session.update(key), "target must be retryable after cancellation")
        assertEquals(OperationStatus.Succeeded, session.statusOf(key))
        assertEquals(0, session.state.updateCount)
    }

    @Test
    fun cancellingRefreshDoesNotShowFailureAndCanRetry() = runTest {
        val service = FakeBrowseService()
        val gate = FakeBrowseService.Gate()
        val slowLoads = object : BrowseService by service {
            var first = true
            override suspend fun loadSnapshot(): BrowseSnapshot {
                if (first) {
                    first = false
                    gate.await()
                }
                return service.loadSnapshot()
            }
        }
        val session = BrowseSession(slowLoads)

        val load = launch { session.refresh() }
        yield()
        assertEquals(LoadState.Loading, session.state.load)
        load.cancelAndJoin()

        assertTrue(session.state.load is LoadState.Ready, "cancelled refresh is not an error: ${session.state.load}")

        session.refresh()
        assertEquals(LoadState.Ready, session.state.load)
        assertEquals(4, session.state.extensions.size)
    }

    @Test
    fun cancellingPreviewLeavesStoreReusable() = runTest {
        // The first attempt parks; the retry must complete without a second gate.
        val firstAttemptGate = FakeBrowseService.Gate()
        var attempts = 0
        val service = object : BrowseService by FakeBrowseService() {
            override suspend fun previewStore(storeId: String): StoreCatalog {
                attempts += 1
                if (attempts == 1) firstAttemptGate.await()
                return StoreCatalog(storeId, listOf(StoreCatalogEntry("pkg", "Late", "1.0", "en")))
            }
        }
        val session = BrowseSession(service)
        session.refresh()

        val preview = launch { session.previewStore(storeId) }
        yield()
        assertEquals(CatalogState.Loading, session.state.catalogStates[storeId])
        preview.cancelAndJoin()

        assertNull(session.state.catalogStates[storeId], "cancelled preview clears its loading marker")

        session.previewStore(storeId)
        assertTrue(session.state.catalogStates[storeId] is CatalogState.Ready)
    }

    @Test
    fun installVariantCarriesPreviewedIndexUrl() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()

        session.previewStore(storeId)
        val ready = session.state.catalogStates[storeId] as CatalogState.Ready
        assertEquals("https://example.com/index.json", ready.catalog.repositoryIndexUrl)

        val variant = ready.catalog.variantRefs()
            .single { it.entry.sourceKey == "new_en" }
            .variant
        assertEquals("https://example.com/index.json", variant.repositoryIndexUrl)

        assertTrue(session.installVariant(variant))
        val submitted = service.installedVariants.single()
        assertEquals("https://example.com/index.json", submitted.repositoryIndexUrl)
        assertEquals("new_en", submitted.sourceKey)
        assertEquals("https://example.com/new_en.js", submitted.scriptUrl)
    }

    @Test
    fun previewAfterEditCarriesTheNewIndexUrl() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()

        assertTrue(session.saveStore(storeId, "Example", "https://moved.example/index.json"))
        session.previewStore(storeId)

        val ready = session.state.catalogStates[storeId] as CatalogState.Ready
        assertEquals("https://moved.example/index.json", ready.catalog.repositoryIndexUrl)
    }
}
