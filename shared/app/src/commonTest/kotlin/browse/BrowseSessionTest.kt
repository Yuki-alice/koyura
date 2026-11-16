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

/** Milestone 2: per-target operation rules enforced by the session, independent of the UI. */
class BrowseSessionTest {
    @Test
    fun noServiceLeavesUnavailableState() = runTest {
        val session = BrowseSession(service = null)
        session.refresh()
        assertEquals(LoadState.Unavailable, session.state.load)
    }

    @Test
    fun loadFailureIsRetryable() = runTest {
        val service = FakeBrowseService()
        service.loadFailure = "network down"
        val session = BrowseSession(service)

        session.refresh()
        assertEquals(LoadState.Failed("network down"), session.state.load)

        session.refresh()
        assertEquals(LoadState.Ready, session.state.load)
        assertEquals(4, session.state.extensions.size)
    }

    @Test
    fun installMarksOnlyItsOwnTarget() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()

        session.install("store:1::com.example.available")

        assertEquals(
            OperationStatus.Succeeded,
            session.state.itemOperations["store:1::com.example.available"],
        )
        assertEquals(null, session.state.itemOperations["store:2::com.example.available"])
    }

    @Test
    fun failedInstallKeepsTargetFailureAndAllowsRetry() = runTest {
        val service = FakeBrowseService()
        service.failures["install:store:1::com.example.available"] = "disk full"
        val session = BrowseSession(service)
        session.refresh()

        val first = session.install("store:1::com.example.available")

        assertFalse(first)
        val failed = assertIs<OperationStatus.Failed>(
            session.state.itemOperations["store:1::com.example.available"],
        )
        assertEquals(OperationKind.Install, failed.kind)
        assertEquals("disk full", failed.message)

        val second = session.install("store:1::com.example.available")
        assertTrue(second)
        assertEquals(
            OperationStatus.Succeeded,
            session.state.itemOperations["store:1::com.example.available"],
        )
        assertEquals(2, service.calls["install:store:1::com.example.available"])
    }

    @Test
    fun repeatedSubmitWhileRunningIsIgnored() = runTest {
        val service = FakeBrowseService()
        val gate = FakeBrowseService.Gate()
        service.gate = gate
        service.gateKey = "install:"
        val session = BrowseSession(service)

        coroutineScope {
            val first = launch { session.install("store:1::com.example.available") }
            // The first call is now parked on the gate; a second one must be dropped.
            yield()

            assertTrue(session.statusOf("store:1::com.example.available") is OperationStatus.Running)
            val duplicate = session.install("store:1::com.example.available")
            assertFalse(duplicate)
            assertEquals(1, service.calls["install:store:1::com.example.available"])

            gate.open()
            first.join()
        }
    }

    @Test
    fun duplicateSubmitOnSameTargetIsRejected() = runTest {
        val service = FakeBrowseService()
        val gate = FakeBrowseService.Gate()
        service.gate = gate
        service.gateKey = "update:store:1::com.example.updates"
        val session = BrowseSession(service)
        session.refresh()

        coroutineScope {
            val runningUpdate = launch { session.update("store:1::com.example.updates") }
            yield()

            // A different action on the same row must not start while its update is in flight.
            assertFalse(session.uninstall("store:1::com.example.updates"))
            assertEquals(null, service.calls["uninstall:store:1::com.example.updates"])

            gate.open()
            runningUpdate.join()
        }
    }

    @Test
    fun staleSnapshotCannotOverwriteNewerResult() = runTest {
        val service = FakeBrowseService()
        val slowGate = FakeBrowseService.Gate()
        val session = BrowseSession(service)
        session.refresh()

        coroutineScope {
            // Park an update on one row, then uninstall a different row; the newer snapshot wins.
            service.gate = slowGate
            service.gateKey = "update:store:1::com.example.updates"
            val slowUpdate = launch { session.update("store:1::com.example.updates") }
            yield()
            service.gate = null
            service.gateKey = null
            session.uninstall("store:1::com.example.installed")

            assertTrue(session.state.extensions.none { it.key == "store:1::com.example.installed" })

            // Releasing the older update must not resurrect the row removed by the newer call.
            slowGate.open()
            slowUpdate.join()

            assertTrue(
                session.state.extensions.none { it.key == "store:1::com.example.installed" },
                "older response must not replace the newer snapshot",
            )
            assertTrue(session.state.extensions.any { it.key == "store:1::com.example.updates" })
        }
    }

    @Test
    fun storeCrudRoundTrip() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()

        assertTrue(session.addStore("Third", "https://third.example/index.json"))
        val added = session.state.stores.last()
        assertEquals("https://third.example/index.json", added.indexUrl)

        assertTrue(session.saveStore(added.id, "Renamed", added.indexUrl))
        assertEquals("Renamed", session.state.storeById(added.id)?.name)

        assertTrue(session.setStoreEnabled(added.id, false))
        assertEquals(false, session.state.storeById(added.id)?.enabled)

        assertTrue(session.removeStore(added.id))
        assertEquals(null, session.state.storeById(added.id))
    }

    @Test
    fun previewFailureIsRetryablePerStore() = runTest {
        val service = FakeBrowseService()
        service.previewFailure = "index unreachable"
        val session = BrowseSession(service)
        session.refresh()

        session.previewStore("store:1")
        val failed = assertIs<CatalogState.Failed>(session.state.catalogStates["store:1"])
        assertEquals("index unreachable", failed.message)

        session.previewStore("store:1")
        val ready = assertIs<CatalogState.Ready>(session.state.catalogStates["store:1"])
        assertEquals(2, ready.catalog.entries.size)

        // A different store keeps its own catalog state.
        assertEquals(null, session.state.catalogStates["store:2"])
    }

    @Test
    fun updateAllClearsUpdateCount() = runTest {
        val service = FakeBrowseService()
        val session = BrowseSession(service)
        session.refresh()
        assertEquals(1, session.state.updateCount)

        session.updateAll()

        assertEquals(0, session.state.updateCount)
        assertTrue(session.state.extensions.all { !it.hasUpdate })
    }

    @Test
    fun urlValidationCoversEmptySchemeAndDuplicate() {
        val stores = defaultStores()

        assertEquals(StoreUrlError.Empty, validateStoreUrl("   ", stores))
        assertEquals(StoreUrlError.InvalidScheme, validateStoreUrl("ftp://example.com", stores))
        assertEquals(
            StoreUrlError.Duplicate,
            validateStoreUrl("https://EXAMPLE.com/index.json", stores),
        )
        assertEquals(
            null,
            validateStoreUrl("https://example.com/index.json", stores, editingStoreId = "store:1"),
        )
        assertEquals(null, validateStoreUrl("https://new.example/index.json", stores))
    }

    @Test
    fun extensionGroupsFilterAndSortIndependently() {
        val sections = defaultExtensions().groupedExtensions("")

        assertEquals(
            listOf(ExtensionGroup.Updates, ExtensionGroup.Installed, ExtensionGroup.Available),
            sections.map { it.group },
        )
        assertEquals(2, sections.last().extensions.size)

        val filtered = defaultExtensions().groupedExtensions("Second Store")
        assertEquals(1, filtered.size)
        assertEquals("store:2::com.example.available", filtered.single().extensions.single().key)
    }
}
