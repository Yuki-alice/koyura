package app.koyura.source.runtime

import app.koyura.network.applyReaderDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VeneraRepositoryStoreTest {
    @Test
    fun crudValidatesNameUrlAndDuplicates() {
        val store = VeneraRepositoryStore()
        val repo = store.add("Main", "https://example.com/repo#index")
        assertEquals("https://example.com/repo", repo.url)
        assertTrue(repo.enabled)
        assertFailsWith<IllegalStateException> { store.add("Dup", "https://example.com/repo#other") }
        assertFailsWith<VeneraCatalogError.InvalidUrl> { store.add("Bad", "ftp://example.com/x") }
        val updated = store.update(repo.id, "Main2", "https://example.com/other")
        assertEquals("Main2", updated.name)
        val disabled = store.setEnabled(repo.id, false)
        assertEquals(false, disabled.enabled)
        assertEquals(false, store.find(repo.id)?.enabled)
        store.remove(repo.id)
        assertNull(store.find(repo.id))
    }

    @Test
    fun removeSnapshotsNameOntoOrigins() {
        val store = VeneraRepositoryStore()
        val repo = store.add("Main", "https://example.com/repo")
        store.setOrigin("k", VeneraOrigin("repository", repo.id, repo.name, "https://example.com/a.js"))
        store.remove(repo.id)
        assertEquals("Main", store.originFor("k")?.repositoryName)
    }

    @Test
    fun entryForPrefersPreviousUrlAndSurfacesAmbiguity() {
        val store = VeneraRepositoryStore()
        val repo = store.add("Main", "https://example.com/repo")
        val entries = listOf(
            VeneraCatalogEntry("k", "A", "1.0.0", "https://example.com/a.js"),
            VeneraCatalogEntry("k", "B", "1.0.1", "https://example.com/b.js"),
        )
        store.setOrigin("k", VeneraOrigin("repository", repo.id, repo.name, "https://example.com/b.js"))
        assertEquals("https://example.com/b.js", store.entryFor("k", entries).url)
        store.setOrigin("k", VeneraOrigin("repository", repo.id, repo.name, "https://example.com/other.js"))
        assertFailsWith<VeneraCatalogError.AmbiguousSource> { store.entryFor("k", entries) }
    }

    @Test
    fun previewLoadsWithoutSaving() = runTest {
        val store = VeneraRepositoryStore()
        val repo = store.add("Main", "https://example.invalid/repo/index")
        val mock = MockEngine {
            respond("""[{"key":"k","name":"N","version":"1.0.0","url":"a.js"}]""", HttpStatusCode.OK)
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        try {
            val catalog = store.preview(client, repo.id)
            assertEquals("https://example.invalid/repo/a.js", catalog.entries.single().url)
            // Preview must not create origins or alter revision beyond CRUD.
            val before = store.revision()
            store.preview(client, repo.id)
            assertTrue(store.revision() == before)
            assertNull(store.originFor("k"))
        } finally {
            client.close()
        }
    }
}
