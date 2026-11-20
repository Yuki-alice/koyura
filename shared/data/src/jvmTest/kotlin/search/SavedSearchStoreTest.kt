package app.koyura.data.search

import app.koyura.data.extension.createFileBackedTestDriver
import app.koyura.data.session.openExtensionSession
import app.koyura.domain.extension.SearchOptionChoice
import app.koyura.domain.extension.SearchOptionType
import app.koyura.domain.extension.SearchOptionValue.Multiple
import app.koyura.domain.extension.SearchOptionValue.Single
import app.koyura.domain.extension.SearchOptionsSnapshot
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SavedSearchStoreTest {
    @Test
    fun parsesOptionsPersistsCurrentAndReplaysIndependentSavedSearchesAfterReopen() = runTest {
        val files = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "saved-search-${Uuid.random()}"
        suspend fun open() = openExtensionSession(
            createFileBackedTestDriver(root, "search"), files, root / "app",
            HttpClient(MockEngine { error("Unexpected network") }),
            VeneraEnvironment("1.16.0", "en", "test"),
        )
        val selected = SearchOptionsSnapshot(listOf(Single("latest"), Multiple(listOf("b", "a")), Single(null)))
        val defaults = SearchOptionsSnapshot(listOf(Single("top"), Multiple(listOf("a")), Single("all")))
        try {
            val first = open()
            val id = try {
                first.service.installFromContent("options.js", optionScript)
                first.service.installFromContent("plain.js", plainScript)
                val browse = first.service.browse
                val options = browse.searchOptions("options")
                assertEquals(listOf(SearchOptionType.SELECT, SearchOptionType.MULTI_SELECT, SearchOptionType.DROPDOWN),
                    options.options.map { it.type })
                assertEquals(SearchOptionChoice("top", "Top-rated"), options.options.first().choices.first())
                assertEquals(defaults, options.current)
                assertEquals(SearchOptionsSnapshot(), browse.searchOptions("plain").current)
                assertEquals(captured("""["hello",[],null]"""), browse.search("plain", "hello").getValue("captured"))
                assertEquals(captured("""["hello",[],"cursor"]"""), browse.search("plain", "hello", next = "cursor").getValue("captured"))
                assertFailsWith<IllegalArgumentException> { browse.setSearchOptions("options", SearchOptionsSnapshot()) }
                browse.setSearchOptions("options", selected)
                val saved = browse.saveSearch("options", " My search ", "sky")
                assertEquals("My search", saved.name)
                assertEquals(selected, saved.options)
                browse.setSearchOptions("options", defaults)
                saved.id
            } finally {
                first.close()
            }

            val reopened = open()
            try {
                val browse = reopened.service.browse
                val repo = browse.savedSearches
                assertEquals(defaults, browse.searchOptions("options").current)
                val saved = repo.list("options").single()
                assertEquals(id, saved.id)
                assertEquals(saved, repo.get(id))
                assertTrue(repo.list("plain").isEmpty())
                assertEquals(captured("""["sky",["latest","[\"b\",\"a\"]",null],2]"""),
                    browse.searchSaved(saved, page = 2).getValue("captured"))
                assertEquals(captured("""["sky",["top","[\"a\"]","all"],1]"""),
                    browse.search("options", "sky").getValue("captured"))
                assertEquals("new query", repo.update(id, "Renamed", "new query", defaults)?.query)
                assertEquals("Renamed", repo.get(id)?.name)
                assertTrue(repo.delete(id))
                assertFalse(repo.delete(id))
                assertNull(repo.get(id))
                assertNull(repo.update(id, "Absent", "", defaults))
            } finally {
                reopened.close()
            }
        } finally {
            files.deleteRecursively(root, mustExist = false)
        }
    }

    private fun captured(json: String) = Json.parseToJsonElement(json)

    private val optionScript = """
        class Source extends ComicSource {
            name = "Options"; key = "options"; version = "1.0.0"; minAppVersion = "1.0.0";
            search = {
                optionList: [
                    { label: "Sort", options: ["top-Top-rated", "latest-Latest", "invalid"], default: "top" },
                    { label: "Tags", type: "multi-select", options: ["a-Alpha", "b-Beta"], default: ["a"] },
                    { label: "Language", type: "dropdown", options: ["all-All", "en-English"] },
                ],
                load: (keyword, options, page) => ({ comics: [], maxPage: 3, captured: [keyword, options, page] }),
                loadNext: () => { throw new Error("Numbered search must take precedence"); },
            };
        }
    """.trimIndent()

    private val plainScript = """
        class Source extends ComicSource {
            name = "Plain"; key = "plain"; version = "1.0.0"; minAppVersion = "1.0.0";
            search = {
                loadNext: (keyword, options, next) => ({ comics: [], next: null, captured: [keyword, options, next] }),
            };
        }
    """.trimIndent()
}
