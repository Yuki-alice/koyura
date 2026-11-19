package app.koyura.features.downloads

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DownloadsSessionTest {
    @Test
    fun refreshLoadsFakeEntriesAndDerivesRunning() = runTest {
        val session = DownloadsSession(FakeDownloadRepository(FakeDownloadRepository.sampleEntries()))
        assertIs<DownloadQueueLoadState.Loading>(session.loadState)
        session.refresh()
        assertIs<DownloadQueueLoadState.Ready>(session.loadState)
        assertEquals(3, session.entries.size)
        assertTrue(session.running)
    }

    @Test
    fun defaultFakeIsEmptyLikeWhitepaperScreenshot() = runTest {
        val session = DownloadsSession(FakeDownloadRepository())
        session.refresh()
        assertIs<DownloadQueueLoadState.Ready>(session.loadState)
        assertTrue(session.entries.isEmpty())
        assertFalse(session.running)
    }

    @Test
    fun pauseResumeRetryCancelFollowStoreSemantics() = runTest {
        val session = DownloadsSession(FakeDownloadRepository(FakeDownloadRepository.sampleEntries()))
        session.refresh()

        session.pause("job-1")
        assertEquals(FakeDownloadStatus.PAUSED, session.entries.first { it.id == "job-1" }.status)

        session.resume("job-1")
        assertEquals(FakeDownloadStatus.QUEUED, session.entries.first { it.id == "job-1" }.status)

        session.retry("job-3")
        assertEquals(FakeDownloadStatus.QUEUED, session.entries.first { it.id == "job-3" }.status)

        session.cancel("job-3")
        assertTrue(session.entries.none { it.id == "job-3" })
    }

    @Test
    fun pauseAllAndResumeAllToggleRunning() = runTest {
        val session = DownloadsSession(FakeDownloadRepository(FakeDownloadRepository.sampleEntries()))
        session.refresh()

        session.pauseAll()
        assertFalse(session.running)
        assertTrue(session.entries.none {
            it.status == FakeDownloadStatus.QUEUED || it.status == FakeDownloadStatus.DOWNLOADING
        })

        session.resumeAll()
        assertTrue(session.running)
    }

    @Test
    fun failureKeepsSnapshotAndReportsActionError() = runTest {
        val session = DownloadsSession(FakeDownloadRepository(failure = "boom"))
        session.refresh()
        assertIs<DownloadQueueLoadState.Failed>(session.loadState)
        assertTrue(session.entries.isEmpty())
        assertEquals("boom", session.actionError)
        session.clearError()
        assertEquals(null, session.actionError)
    }
}
