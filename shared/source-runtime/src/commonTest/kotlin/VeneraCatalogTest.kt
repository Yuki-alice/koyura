package app.koyura.source.runtime

import app.koyura.network.applyReaderDefaults
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VeneraCatalogTest {
    @Test
    fun normalizeRejectsNonHttp() {
        val bad = listOf("", "   ", "example.com/x", "not a url", "ftp://h/x", "file:///tmp/x.js", "javascript:alert(1)", "http://", "https://")
        for (value in bad) {
            assertFailsWith<VeneraCatalogError.InvalidUrl>("value: '$value'") {
                normalizeVeneraUrl(value)
            }
        }
    }

    @Test
    fun normalizeStripsFragmentKeepsQueryAndCaseFolds() {
        assertEquals("https://example.com/repo?a=b", normalizeVeneraUrl("https://example.com/repo?a=b#frag"))
        assertEquals("https://example.com/", normalizeVeneraUrl("https://example.com"))
        assertEquals("http://localhost:8080/x.js", normalizeVeneraUrl("HTTP://localhost:8080/x.js#y"))
    }

    @Test
    fun parseStripsSingleBomAndSkipsInvalidRows() {
        val contents = "\uFEFF[" +
            """{"key":"good","name":"G","version":"1.0.0","url":"https://example.com/a.js"},""" +
            """{"key":"bad-key","name":"B","version":"1.0.0","url":"https://example.com/b.js"},""" +
            """{"key":"badver","name":"B","version":"1","url":"https://example.com/c.js"},""" +
            """{"key":"notarget","name":"B","version":"1.0.0"},""" +
            """{"key":"local","name":"B","version":"1.0.0","fileName":"file:///tmp/x.js"}""" +
            "]"
        val catalog = parseVeneraCatalog(contents, baseUrl = "https://example.com/repo/index")
        assertEquals(1, catalog.entries.size)
        assertEquals("good", catalog.entries.single().key)
        assertEquals(listOf("bad-key", "badver", "notarget", "local"), catalog.skipped)
    }

    @Test
    fun parsePrefersNonEmptyUrlOverFileName() {
        val contents = """[{"key":"k","name":"N","version":"1.0.0","url":"https://example.com/a.js","fileName":"b.js"}]"""
        val catalog = parseVeneraCatalog(contents)
        assertEquals("https://example.com/a.js", catalog.entries.single().url)
        val fallback = """[{"key":"k","name":"N","version":"1.0.0","url":"  ","fileName":"https://example.com/b.js"}]"""
        assertEquals("https://example.com/b.js", parseVeneraCatalog(fallback).entries.single().url)
    }

    @Test
    fun parseResolvesRelativeAgainstFinalBase() {
        val base = "https://cdn.test/repo/index"
        assertEquals("https://cdn.test/repo/one.js", parseVeneraCatalog("""[{"key":"a","name":"N","version":"1.0.0","url":"one.js"}]""", base).entries.single().url)
        assertEquals("https://cdn.test/up.js", parseVeneraCatalog("""[{"key":"a","name":"N","version":"1.0.0","url":"../up.js"}]""", base).entries.single().url)
        assertEquals("https://cdn.test/root.js", parseVeneraCatalog("""[{"key":"a","name":"N","version":"1.0.0","url":"/root.js"}]""", base).entries.single().url)
        assertEquals("https://cdn.test/repo/nested.js?token=a/b", parseVeneraCatalog("""[{"key":"a","name":"N","version":"1.0.0","url":"nested.js?token=a/b"}]""", base).entries.single().url)
        assertEquals("https://cdn.other/x.js", parseVeneraCatalog("""[{"key":"a","name":"N","version":"1.0.0","url":"//cdn.other/x.js"}]""", base).entries.single().url)
        assertEquals("http://localhost:8080/s.js", parseVeneraCatalog("""[{"key":"a","name":"N","version":"1.0.0","url":"http://localhost:8080/s.js"}]""", base).entries.single().url)
    }

    @Test
    fun parseMapsMalformedToInvalidOrEmpty() {
        assertFailsWith<VeneraCatalogError.InvalidCatalog> { parseVeneraCatalog("{not json") }
        assertFailsWith<VeneraCatalogError.InvalidCatalog> { parseVeneraCatalog("""{"key":"a"}""") }
        assertFailsWith<VeneraCatalogError.EmptyCatalog> {
            parseVeneraCatalog("""[{"key":"bad-key","name":"B","version":"1.0.0","url":"https://example.com/a.js"}]""")
        }
    }

    @Test
    fun entrySelectionPrefersPreviousUrl() {
        val entries = listOf(
            VeneraCatalogEntry("k", "A", "1.0.0", "https://example.com/a.js"),
            VeneraCatalogEntry("k", "B", "1.0.1", "https://example.com/b.js"),
        )
        assertEquals("https://example.com/b.js", selectCatalogEntry("k", entries, "https://example.com/b.js").url)
        assertEquals(1, selectCatalogEntry("solo", listOf(VeneraCatalogEntry("solo", "S", "1.0.0", "https://example.com/s.js")), null).url.let { 1 })
        assertFailsWith<VeneraCatalogError.MissingSource> { selectCatalogEntry("missing", entries, null) }
        assertFailsWith<VeneraCatalogError.AmbiguousSource> { selectCatalogEntry("k", entries, "https://example.com/other.js") }
    }

    @Test
    fun versionOrderingCoversHotfix() {
        assertTrue(isVeneraUpdateAvailable("1.0.0", "1.0.0-alpha"))
        assertTrue(isVeneraUpdateAvailable("1.0.0-hotfix", "1.0.0"))
        assertTrue(isVeneraUpdateAvailable("2.0.0", "1.9.9"))
        assertTrue(!isVeneraUpdateAvailable("1.0.0", "1.0.0"))
        assertTrue(!isVeneraUpdateAvailable("1.0.0-alpha", "1.0.0"))
        assertTrue(!isVeneraUpdateAvailable("1.9.9", "2.0.0"))
    }

    @Test
    fun loadUsesFinalUrlAndUtf8() = runTest {
        val mock = MockEngine { request ->
            assertTrue(request.url.toString().startsWith("https://example.invalid/repo"))
            respond(
                "\uFEFF[{\"key\":\"k\",\"name\":\"N\",\"version\":\"1.0.0\",\"url\":\"a.js\"}]",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json; charset=utf-8"),
            )
        }
        val client = HttpClient(mock) { applyReaderDefaults() }
        try {
            val catalog = loadVeneraCatalog(client, "https://example.invalid/repo/index")
            assertEquals("https://example.invalid/repo/a.js", catalog.entries.single().url)
        } finally {
            client.close()
        }
    }

    @Test
    fun loadThrowsOnNon200() = runTest {
        val mock = MockEngine { respond("down", HttpStatusCode.ServiceUnavailable) }
        val client = HttpClient(mock) { applyReaderDefaults() }
        try {
            assertFailsWith<VeneraCatalogError.Unavailable> {
                loadVeneraCatalog(client, "https://example.invalid/repo/index")
            }
        } finally {
            client.close()
        }
    }
}
