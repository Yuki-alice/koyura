package app.koyura.features.updates

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdatesSessionTest {
    @Test
    fun refreshLoadsFakeEntriesAndCountsUnread() = runTest {
        val session = UpdatesSession(FakeUpdatesRepository(FakeUpdatesRepository.sampleEntries()))
        assertIs<UpdatesLoadState.Loading>(session.loadState)
        session.refresh()
        assertIs<UpdatesLoadState.Ready>(session.loadState)
        assertEquals(2, session.entries.size)
        assertEquals(3, session.unreadCount)
    }

    @Test
    fun defaultFakeIsEmptyLikeWhitepaperScreenshot() = runTest {
        val session = UpdatesSession(FakeUpdatesRepository())
        session.refresh()
        assertIs<UpdatesLoadState.Ready>(session.loadState)
        assertTrue(session.entries.isEmpty())
        assertEquals(0, session.unreadCount)
    }

    @Test
    fun failureKeepsSnapshotAndReportsActionError() = runTest {
        val session = UpdatesSession(FakeUpdatesRepository(failure = "boom"))
        session.refresh()
        assertIs<UpdatesLoadState.Failed>(session.loadState)
        assertTrue(session.entries.isEmpty())
        assertEquals("boom", session.actionError)
        session.clearError()
        assertEquals(null, session.actionError)
    }

    @Test
    fun checkNowUsesRepositoryScanResult() = runTest {
        val session = UpdatesSession(object : UpdatesRepository {
            override suspend fun loadUpdates() = emptyList<UpdateWork>()
            override suspend fun checkNow() = FakeUpdatesRepository.sampleEntries()
        })
        session.refresh()
        assertEquals(0, session.unreadCount)
        session.checkNow()
        assertIs<UpdatesLoadState.Ready>(session.loadState)
        assertEquals(3, session.unreadCount)
    }

    @Test
    fun defaultContractDelegatesScanToFeedForFakes() = runTest {
        val session = UpdatesSession(FakeUpdatesRepository(FakeUpdatesRepository.sampleEntries()))
        session.checkNow()
        session.retryFailures()
        assertIs<UpdatesLoadState.Ready>(session.loadState)
        assertEquals(3, session.unreadCount)
    }
}
