package app.koyura.data.extension

import app.koyura.data.session.openExtensionSession
import app.koyura.source.runtime.VeneraCallback
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class SourceBrowseServiceTest {
    @Test
    fun preservesExplorationAndCategoryPaginationAndInteractionCallbacks() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "source-browse-${Uuid.random()}"
        val session = openExtensionSession(
            createTestDriver(), FileSystem.SYSTEM, root, HttpClient(MockEngine { respond("ok") }),
            VeneraEnvironment("1.16.0", "en", "test"),
        )
        try {
            val extensions = session.service
            extensions.installFromContent("browse.js", script)
            val browse = extensions.browse
            val pages = browse.explorePages("browse")
            assertEquals(listOf("Numbered", "Cursor", "Legacy", "Mixed"), pages.map { it.title })
            assertEquals(JsonPrimitive(3), browse.explore("browse", pages[0], position = 3).jsonObject["maxPage"])
            assertEquals(JsonPrimitive("first"), browse.explore("browse", pages[1]).jsonObject["next"])
            assertEquals(JsonNull, browse.explore("browse", pages[1], next = "first").jsonObject["next"])
            assertEquals(JsonPrimitive("New"), browse.explore("browse", pages[2]).jsonArray.single().jsonObject["title"])
            assertEquals(JsonPrimitive(0), browse.explore("browse", pages[3]).jsonObject["maxPage"])
            val legacy = browse.categoryItems("browse", 0).single().jsonObject.getValue("target").jsonObject
            assertEquals(JsonPrimitive("adventure"), legacy.getValue("attributes").jsonObject["param"])
            val dynamic = browse.categoryItems("browse", 1).single().jsonObject
            assertEquals(JsonPrimitive("Today"), dynamic["label"])
            assertEquals(JsonPrimitive("Adventure"), browse.categoryOptions("browse", "Adventure", null).single().jsonObject["label"])
            assertEquals(
                JsonArray(listOf(JsonPrimitive("Adventure"), JsonNull, JsonArray(listOf(JsonPrimitive("hot"))), JsonPrimitive(2))),
                browse.categoryComics("browse", "Adventure", null, listOf("hot"), 2).jsonObject["args"],
            )
            assertEquals(JsonPrimitive("all"), browse.ranking("browse", "all").jsonObject["next"])
            assertEquals(JsonPrimitive("work"), extensions.call("browse", VeneraCallback.FavoriteFolders,
                JsonArray(listOf(JsonPrimitive("work")))).jsonObject.getValue("favorited").jsonArray.single())
            val comment = extensions.call("browse", VeneraCallback.Comments, JsonArray(emptyList()))
                .jsonObject.getValue("comments").jsonArray.single().jsonObject
            assertEquals(JsonPrimitive("Thanks"), comment["content"])
        } finally {
            session.close()
            FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
        }
    }

    private val script = """
        class Source extends ComicSource {
            name = "Browse"; key = "browse"; version = "1.0.0"; minAppVersion = "1.0.0";
            explore = [
                {title: "Numbered", type: "multiPageComicList", load: async page => ({ comics: [], maxPage: page }), loadNext: () => { throw "Wrong loader"; }},
                {title: "Cursor", type: "multiPageComicList", loadNext: next => ({ comics: [], next: next == null ? "first" : null })},
                {title: "Legacy", type: "singlePageWithMultiPart", load: () => ({ New: [] })},
                {title: "Mixed", type: "mixed", load: index => ({ data: [], maxPage: index })}
            ];
            category = { title: "Genres", parts: [
                { name: "Legacy", type: "fixed", categories: ["Adventure"], itemType: "category", groupParam: "adventure" },
                { name: "Today", type: "dynamic", loader: () => [{ label: "Today", target: { page: "search", attributes: { text: "today" } } }] },
            ]};
            categoryComics = {
                load: async (...args) => ({ comics: [], maxPage: 2, args }),
                optionLoader: async category => [{ label: category, options: ["hot-Hot"] }],
                ranking: { options: ["all-All"], loadWithNext: (option, next) => ({ comics: [], next: next ?? option }) },
            };
            favorites = { multiFolder: true, loadFolders: comicId => ({ folders: {}, favorited: [comicId] }) };
            comic = { loadComments: async () => ({ comments: [new Comment({ userName: "Reader", content: "Thanks" })] }) };
        }
    """.trimIndent()
}
