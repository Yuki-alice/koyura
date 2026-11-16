package app.koyura.app.history

import app.koyura.app.browse.BrowseService
import app.koyura.app.browse.BrowseServiceException
import app.koyura.app.browse.WorkDetail
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeBrowse(
    val details: Map<Pair<String, String>, WorkDetail>,
) : BrowseService by error("unused") {
    override suspend fun loadWorkDetail(sourceKey: String, workId: String): WorkDetail =
        details[sourceKey to workId] ?: throw BrowseServiceException("Unknown work: $workId")
}

private fun error(message: String): BrowseService = object : BrowseService {
    override suspend fun loadSnapshot() = throw UnsupportedOperationException()
    override suspend fun addStore(name: String, indexUrl: String) = throw UnsupportedOperationException()
    override suspend fun saveStore(storeId: String, name: String, indexUrl: String) = throw UnsupportedOperationException()
    override suspend fun removeStore(storeId: String) = throw UnsupportedOperationException()
    override suspend fun setStoreEnabled(storeId: String, enabled: Boolean) = throw UnsupportedOperationException()
    override suspend fun previewStore(storeId: String) = throw UnsupportedOperationException()
    override suspend fun installExtension(extensionKey: String) = throw UnsupportedOperationException()
    override suspend fun installCatalogVariant(variant: app.koyura.app.browse.CatalogVariantId) = throw UnsupportedOperationException()
    override suspend fun updateExtension(extensionKey: String) = throw UnsupportedOperationException()
    override suspend fun uninstallExtension(extensionKey: String) = throw UnsupportedOperationException()
    override suspend fun updateAllExtensions() = throw UnsupportedOperationException()
    override suspend fun searchWorks(sourceKey: String, query: String, page: Int) = throw UnsupportedOperationException()
    override suspend fun loadWorkDetail(sourceKey: String, workId: String): WorkDetail = throw UnsupportedOperationException()
}

class HistorySessionTest {
    @Test
    fun refreshLoadsNewestFirstAndClearsError() = runTest {
        val service = FakeHistoryService(
            listOf(historyEntry(workId = "old"), historyEntry(workId = "new")),
        )
        val session = HistorySession(service)

        assertIs<HistoryLoadState.Loading>(session.loadState)
        session.refresh()
        assertIs<HistoryLoadState.Ready>(session.loadState)
        assertEquals(listOf("old", "new").let { it }, session.entries.map { it.workId })
        session.reportError("boom")
        session.clearError()
        assertNull(session.errorMessage)
    }

    @Test
    fun refreshFailureKeepsOldEntries() = runTest {
        val service = FakeHistoryService(listOf(historyEntry()))
        val session = HistorySession(service)
        session.refresh()
        assertEquals(1, session.entries.size)

        service.failNext = "disk gone"
        session.refresh()
        assertIs<HistoryLoadState.Failed>(session.loadState)
        assertTrue(session.entries.isEmpty())
    }

    @Test
    fun resolveChaptersReturnsIdsAndTitles() = runTest {
        val detail = WorkDetail(
            id = "demo",
            title = "星图",
            chapters = listOf(
                app.koyura.app.browse.WorkChapter("ep_1", "第一章"),
                app.koyura.app.browse.WorkChapter("ep_2", "第二章"),
            ),
        )
        val session = HistorySession(FakeHistoryService(), FakeBrowse(mapOf("example_source" to "demo" to detail)))
        val (ids, titles) = session.resolveChapters(historyEntry())
        assertEquals(listOf("ep_1", "ep_2"), ids)
        assertEquals(listOf("第一章", "第二章"), titles)
    }

    @Test
    fun resolveChaptersRejectsMissingChapter() = runTest {
        val detail = WorkDetail(id = "demo", title = "星图", chapters = listOf(app.koyura.app.browse.WorkChapter("ep_1", "第一章")))
        val session = HistorySession(FakeHistoryService(), FakeBrowse(mapOf("example_source" to "demo" to detail)))
        assertFailsWith<BrowseServiceException> {
            session.resolveChapters(historyEntry())
        }
    }

    @Test
    fun resolveChaptersWithoutSourceFails() = runTest {
        val session = HistorySession(FakeHistoryService())
        assertFailsWith<BrowseServiceException> {
            session.resolveChapters(historyEntry())
        }
    }
}
