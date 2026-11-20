package app.koyura.data.extension

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class SourceChapterEntry(val id: String, val title: String, val metadata: JsonObject? = null)

/** Reads flat or grouped chapter maps, preserving source order and optional entry metadata. */
fun parseSourceChapterCatalog(value: JsonElement?, strict: Boolean): List<SourceChapterEntry> {
    if (value == null || value == JsonNull) return emptyList()
    if (value !is JsonObject) {
        require(!strict) { "Invalid chapter catalog" }
        return emptyList()
    }

    fun parseEntry(id: String, element: JsonElement): SourceChapterEntry? {
        val metadata = element as? JsonObject
        val title = (metadata?.get("title") ?: element) as? JsonPrimitive
        if (title?.isString == true && (id.isNotEmpty() || !strict)) {
            return SourceChapterEntry(id, title.content, metadata)
        }
        require(!strict) { "Invalid chapter entry" }
        return null
    }

    return value.entries.flatMap { (id, entry) ->
        if (entry is JsonObject && (entry["title"] as? JsonPrimitive)?.isString != true) {
            entry.entries.mapNotNull { (chapterId, chapter) -> parseEntry(chapterId, chapter) }
        } else {
            listOfNotNull(parseEntry(id, entry))
        }
    }
}
