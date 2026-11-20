package app.koyura.app.browse.venera

import app.koyura.app.browse.BrowseServiceException
import app.koyura.app.browse.globalsearch.GlobalSearchService
import app.koyura.data.session.ExtensionSession
import app.koyura.data.session.openExtensionSession
import app.koyura.source.runtime.VeneraEnvironment
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SourceSearchPaginationTest {
    @Test
    fun numberedAndCursorPagesReachTheSourceAndFailureStaysLocalToItsSource() = runTest {
        val fixture = VeneraHarness()
        try {
            fixture.service.installFromContent("numbered.js", source("numbered",
                """load: (q, o, p) => ({comics: [{id: "" + p, title: q + p}], maxPage: 3})"""))
            fixture.service.installFromContent("cursor.js", source("cursor",
                """loadNext: (q, o, token) => ({comics: [{id: token || "first", title: q}], next: token ? null : "tail"})"""))
            fixture.service.installFromContent("bad.js", source("bad", """load: () => ({comics: false})"""))
            val browse = VeneraBrowseService(fixture.service, search = fixture.service.browse)
            val global = GlobalSearchService(browse)
            val results = global.search(global.sources(), "query").associateBy { it.sourceKey }
            assertNotNull(results.getValue("bad").error)
            assertEquals(3, results.getValue("numbered").searchPage?.maxPage)
            assertEquals("tail", results.getValue("cursor").searchPage?.next)

            val numbered = global.searchOne("numbered", "Numbered", "query", page = 2)
            assertEquals("query2", numbered.works.single().title)
            assertEquals(2, numbered.searchPage?.page)
            assertTrue(assertNotNull(numbered.searchPage).hasMore)

            val second = global.searchOne("cursor", "Cursor", "query", page = 2, next = "tail")
            assertEquals("tail", second.works.single().id)
            assertNull(second.searchPage?.next)
            assertFalse(assertNotNull(second.searchPage).hasMore)
            assertNotNull(global.searchOne("cursor", "Cursor", "query", page = 2).error)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun savedSearchCanBeReadEditedExecutedAndDeletedAcrossReopen() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "saved-query-${Uuid.random()}"
        val environment = VeneraEnvironment("1.16.0", "en", "test")
        try {
            val session = openExtensionSession(root, environment)
            val id = try {
                session.service.installFromContent("numbered.js", source("numbered",
                    """load: (q, o, p) => ({comics: [{id: "" + p, title: q + p}], maxPage: 3})"""))
                val browse = session.browse()
                val saved = assertNotNull(browse.saveSearch("numbered", "First", "query"))
                val updated = assertNotNull(browse.updateSavedSearch(saved.id, "Renamed", "updated"))
                assertEquals(saved.options, updated.options)
                assertEquals("Renamed", browse.savedSearch(saved.id)?.name)
                saved.id
            } finally {
                session.close()
            }
            val reopened = openExtensionSession(root, environment)
            try {
                val browse = reopened.browse()
                assertEquals(id, browse.savedSearches("numbered").single().id)
                assertEquals("updated2", browse.searchSavedWorks(id, page = 2).works.single().title)
                assertTrue(browse.deleteSavedSearch(id))
                assertFalse(browse.deleteSavedSearch(id))
                assertNull(browse.updateSavedSearch(id, "Missing", "query"))
                assertFailsWith<BrowseServiceException> { browse.searchSavedWorks(id) }
            } finally {
                reopened.close()
            }
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    private fun ExtensionSession.browse() = VeneraBrowseService(service, search = service.browse)

    private fun source(key: String, callback: String) = """
        class Source extends ComicSource {
            name = "$key"; key = "$key"; version = "1.0.0"; minAppVersion = "1.0.0";
            search = {$callback};
        }
    """.trimIndent()
}
