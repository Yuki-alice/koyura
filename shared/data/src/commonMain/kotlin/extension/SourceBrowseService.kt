package app.koyura.data.extension

import app.koyura.domain.extension.ExtensionFailure
import app.koyura.source.runtime.SourceConfigField
import app.koyura.source.runtime.SourceIndexedCallback
import app.koyura.source.runtime.VeneraCallback
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SourceExplorePage(
    val index: Int,
    val title: String,
    val type: String,
    val numbered: Boolean,
    val cursor: Boolean,
)

class SourceBrowseService internal constructor(private val extensions: ExtensionService) {
    suspend fun explorePages(key: String): List<SourceExplorePage> =
        configuration(key, SourceConfigField.Explore).jsonArray.map { entry ->
            val page = entry.jsonObject
            SourceExplorePage(
                page.getValue("index").jsonPrimitive.int, page.getValue("title").jsonPrimitive.content,
                page.getValue("type").jsonPrimitive.content, page.getValue("numbered").jsonPrimitive.boolean,
                page.getValue("cursor").jsonPrimitive.boolean,
            )
        }

    suspend fun explore(key: String, page: SourceExplorePage, position: Int? = null, next: String? = null): JsonElement {
        val live = runtime(key)
        return when (page.type) {
            "multiPageComicList" -> if (page.numbered) {
                live.callIndexed(key, SourceIndexedCallback.ExplorePage, page.index, args(JsonPrimitive(positivePage(position ?: 1))))
            } else {
                require(page.cursor)
                live.callIndexed(key, SourceIndexedCallback.ExploreNext, page.index, args(text(next)))
            }
            "mixed" -> {
                require(position == null || position >= 0)
                live.callIndexed(key, SourceIndexedCallback.ExplorePage, page.index, args(JsonPrimitive(position ?: 0)))
            }
            "multiPartPage" -> live.callIndexed(key, SourceIndexedCallback.ExplorePage, page.index)
            "singlePageWithMultiPart" -> JsonArray(
                live.callIndexed(key, SourceIndexedCallback.ExplorePage, page.index).jsonObject.map { (title, comics) ->
                    JsonObject(mapOf("title" to JsonPrimitive(title), "comics" to comics))
                },
            )
            else -> error("Unknown explore page type: ${page.type}")
        }
    }

    suspend fun categories(key: String): JsonObject? =
        configuration(key, SourceConfigField.Category) as? JsonObject

    suspend fun categoryItems(key: String, partIndex: Int): JsonArray {
        val part = categories(key)?.get("parts")?.jsonArray?.getOrNull(partIndex)?.jsonObject
            ?: error("Category part is unavailable")
        val categories = part["categories"] as? JsonArray
        if (categories == null && part["type"] == JsonPrimitive("dynamic")) {
            return runtime(key).callIndexed(key, SourceIndexedCallback.CategoryPart, partIndex).jsonArray
        }
        return JsonArray(categories.orEmpty().mapIndexed { index, item ->
            if (item is JsonObject) item else legacyTarget(part, index, item.jsonPrimitive.content)
        })
    }

    suspend fun categoryOptions(key: String, category: String, param: String?): JsonArray {
        val live = runtime(key)
        return if (live.registeredSources().first { it.key == key }.callbacks.contains(VeneraCallback.CategoryOptions)) {
            live.call(key, VeneraCallback.CategoryOptions, args(text(category), text(param))).jsonArray
        } else {
            (live.describe(key, SourceConfigField.CategoryOptions) as? JsonArray).orEmpty().filter { entry ->
                val option = entry.jsonObject
                val shown = option["showWhen"] as? JsonArray
                val hidden = option["notShowWhen"] as? JsonArray
                (shown == null || text(category) in shown) && (hidden == null || text(category) !in hidden)
            }.let(::JsonArray)
        }
    }

    suspend fun categoryComics(key: String, category: String, param: String?, options: List<String>, page: Int): JsonElement =
        runtime(key).call(key, VeneraCallback.CategoryComics, args(
            text(category), text(param), JsonArray(options.map(::JsonPrimitive)), JsonPrimitive(positivePage(page)),
        ))

    suspend fun ranking(key: String, option: String, page: Int = 1, next: String? = null): JsonElement {
        val live = runtime(key)
        val callbacks = live.registeredSources().first { it.key == key }.callbacks
        return if (VeneraCallback.RankingPage in callbacks) {
            live.call(key, VeneraCallback.RankingPage, args(text(option), JsonPrimitive(positivePage(page))))
        } else {
            live.call(key, VeneraCallback.RankingNext, args(text(option), text(next)))
        }
    }

    suspend fun configuration(key: String, field: SourceConfigField): JsonElement = runtime(key).describe(key, field)

    private fun runtime(key: String) = extensions.runtime ?: throw ExtensionFailure.MissingExtension(key)

    private fun positivePage(page: Int): Int {
        require(page >= 1)
        return page
    }

    private fun legacyTarget(part: JsonObject, index: Int, label: String): JsonObject {
        val itemType = part.getValue("itemType").jsonPrimitive.content
        val param = part["groupParam"] ?: (part["categoryParams"] as? JsonArray)?.getOrNull(index) ?: JsonNull
        val (page, attributes) = when (itemType) {
            "category" -> "category" to JsonObject(mapOf("category" to text(label), "param" to param))
            "search" -> "search" to JsonObject(mapOf("keyword" to text(label)))
            "search_with_namespace" -> "search" to JsonObject(mapOf(
                "keyword" to text(part.getValue("name").jsonPrimitive.content + ":" + label),
            ))
            else -> itemType to JsonNull
        }
        return JsonObject(mapOf("label" to text(label), "target" to JsonObject(mapOf("page" to text(page), "attributes" to attributes))))
    }

    private fun args(vararg values: JsonElement) = JsonArray(values.toList())
    private fun text(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
}
