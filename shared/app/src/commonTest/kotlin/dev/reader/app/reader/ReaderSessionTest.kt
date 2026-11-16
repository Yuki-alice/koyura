package dev.reader.app.reader

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderSessionTest {
    private fun sessionWith(
        service: FakeReaderService = demoReaderService(),
    ) = ReaderSession(service)

    @Test
    fun openReturnsSavedStartPageForMatchingChapter() = runTest {
        val service = demoReaderService()
        service.savedProgress["example_source" to "demo"] = ReaderProgress("ep_1", 2)
        val session = sessionWith(service)

        session.open("example_source", "demo", "ep_1", listOf("ep_1", "ep_2"))

        val content = assertIs<ReaderContentState.Ready>(session.content)
        assertEquals(listOf("p0", "p1", "p2", "p3", "p4"), content.content.pageKeys)
        assertEquals(2, content.content.startPageIndex)
        assertEquals(2, session.currentPage)
    }

    @Test
    fun openFallsBackToZeroForOtherChapters() = runTest {
        val service = demoReaderService()
        service.savedProgress["example_source" to "demo"] = ReaderProgress("ep_1", 3)
        val session = sessionWith(service)

        session.open("example_source", "demo", "ep_2", listOf("ep_1", "ep_2"))

        val content = assertIs<ReaderContentState.Ready>(session.content)
        assertEquals(0, content.content.startPageIndex)
    }

    @Test
    fun openFailureSurfacesAndLeavesPageEmpty() = runTest {
        val service = FakeReaderService()
        val session = sessionWith(service)

        session.open("example_source", "demo", "missing", emptyList())

        val failed = assertIs<ReaderContentState.Failed>(session.content)
        assertTrue(failed.message.isNotBlank())
    }

    @Test
    fun initialOpenNeverWritesProgress() = runTest {
        val service = demoReaderService()
        service.savedProgress["example_source" to "demo"] = ReaderProgress("ep_1", 2)
        val session = sessionWith(service)

        session.open("example_source", "demo", "ep_1", listOf("ep_1", "ep_2"))
        // Even an explicit save of the restored page is a no-op: nothing changed.
        session.saveCurrent()

        assertTrue(service.saveCalls.isEmpty(), "got ${service.saveCalls}")
    }

    @Test
    fun saveSkipsAlreadySavedIndex() = runTest {
        val service = demoReaderService()
        val session = sessionWith(service)
        session.open("example_source", "demo", "ep_1", listOf("ep_1", "ep_2"))

        session.savePage(3)
        session.savePage(3)

        assertEquals(
            listOf(FakeReaderService.SaveCall("example_source", "demo", "ep_1", 3)),
            service.saveCalls,
        )
    }

    @Test
    fun saveCurrentPersistsDisplayedPage() = runTest {
        val service = demoReaderService()
        val session = sessionWith(service)
        session.open("example_source", "demo", "ep_1", listOf("ep_1", "ep_2"))

        session.markDisplayed(4)
        session.saveCurrent()

        assertEquals(
            listOf(FakeReaderService.SaveCall("example_source", "demo", "ep_1", 4)),
            service.saveCalls,
        )
    }

    @Test
    fun ensurePageLoadsOnceAndCaches() = runTest {
        val service = demoReaderService()
        val session = sessionWith(service)
        session.open("example_source", "demo", "ep_1", listOf("ep_1", "ep_2"))

        session.ensurePage("p1")
        session.ensurePage("p1")

        assertIs<ReaderPageState.Ready>(session.pages["p1"])
        assertEquals(1, service.loadCounts["p1"])
    }

    @Test
    fun retryReloadsAfterFailure() = runTest {
        val service = demoReaderService()
        service.pageFailures["p2"] = 1
        val session = sessionWith(service)
        session.open("example_source", "demo", "ep_1", listOf("ep_1", "ep_2"))

        session.ensurePage("p2")
        assertIs<ReaderPageState.Failed>(session.pages["p2"])
        session.retryPage("p2")
        assertIs<ReaderPageState.Ready>(session.pages["p2"])
        assertEquals(2, service.loadCounts["p2"])
    }

    @Test
    fun lateOpenResultIsDropped() = runTest {
        val service = demoReaderService()
        val gate = FakeReaderService.Gate()
        service.gate = gate
        service.gateKey = "open"
        val session = sessionWith(service)

        val first = async { session.open("example_source", "demo", "ep_1", listOf("ep_1", "ep_2")) }
        yield()
        service.gate = null
        session.open("example_source", "demo", "ep_2", listOf("ep_1", "ep_2"))
        gate.open()
        first.await()

        val content = assertIs<ReaderContentState.Ready>(session.content)
        assertEquals("ep_2", content.content.chapterId)
        assertEquals(listOf("q0", "q1", "q2"), content.content.pageKeys)
    }

    @Test
    fun latePageResultIsDroppedAfterChapterSwitch() = runTest {
        val service = demoReaderService()
        val gate = FakeReaderService.Gate()
        service.gate = gate
        service.gateKey = "page:p0"
        val session = sessionWith(service)
        session.open("example_source", "demo", "ep_1", listOf("ep_1", "ep_2"))

        val parked = async { session.ensurePage("p0") }
        yield()
        service.gate = null
        session.open("example_source", "demo", "ep_2", listOf("ep_1", "ep_2"))
        gate.open()
        parked.await()

        // The late p0 result must not leak into the new chapter's page map.
        assertNull(session.pages["p0"])
        session.ensurePage("q0")
        assertIs<ReaderPageState.Ready>(session.pages["q0"])
        val content = assertIs<ReaderContentState.Ready>(session.content)
        assertEquals("ep_2", content.content.chapterId)
    }

    @Test
    fun chapterNavigationUsesStoredOrder() = runTest {
        val session = sessionWith()
        session.open("example_source", "demo", "ep_1", listOf("ep_1", "ep_2"))

        assertEquals(null, session.previousChapterId("ep_1"))
        assertEquals("ep_2", session.nextChapterId("ep_1"))
        assertEquals("ep_1", session.previousChapterId("ep_2"))
        assertEquals(null, session.nextChapterId("ep_2"))
        assertEquals(null, session.previousChapterId(null))
        assertEquals(null, session.nextChapterId(null))
    }
}
