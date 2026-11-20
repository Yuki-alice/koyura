package app.koyura.data.search

import app.koyura.domain.extension.SearchOption
import app.koyura.domain.extension.SearchOptionChoice
import app.koyura.domain.extension.SearchOptionType
import app.koyura.domain.extension.SearchOptionValue
import app.koyura.domain.extension.SearchOptionsSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun parseSearchOptions(config: JsonElement): List<SearchOption> {
    if (config == JsonNull) return emptyList()
    return (config as JsonArray).map { item ->
        val field = item as JsonObject
        val type = when ((field["type"] as? JsonPrimitive)?.content) {
            null, "select" -> SearchOptionType.SELECT
            "multi-select" -> SearchOptionType.MULTI_SELECT
            "dropdown" -> SearchOptionType.DROPDOWN
            else -> error("Unsupported search option type")
        }
        val label = (field.getValue("label") as JsonPrimitive).content
        val choices = (field["options"] as? JsonArray).orEmpty().mapNotNull { raw ->
            val text = (raw as? JsonPrimitive)?.content ?: return@mapNotNull null
            val cut = text.indexOf('-')
            if (cut < 0) null else SearchOptionChoice(text.substring(0, cut), text.substring(cut + 1))
        }.associateBy { it.value }.values.toList()
        val keys = choices.map { it.value }
        val declared = field["default"]
        val default = when (type) {
            SearchOptionType.MULTI_SELECT -> {
                val array = when (declared) {
                    is JsonArray -> declared
                    is JsonPrimitive -> runCatching { Json.parseToJsonElement(declared.content) as? JsonArray }.getOrNull()
                    else -> null
                }
                SearchOptionValue.Multiple(array.orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
                    .filter { it in keys }.distinct())
            }
            else -> {
                val selected = (declared as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
                val value = selected?.takeIf { it in keys }
                    ?: if (type == SearchOptionType.DROPDOWN && declared == JsonNull) null
                    else keys.firstOrNull() ?: if (type == SearchOptionType.SELECT) "" else null
                SearchOptionValue.Single(value)
            }
        }
        SearchOption(label, type, choices, default)
    }
}

internal fun List<SearchOption>.requireValid(snapshot: SearchOptionsSnapshot) {
    require(size == snapshot.values.size) { "Incorrect number of search options" }
    zip(snapshot.values).forEach { (field, choice) ->
        val keys = field.choices.map { it.value }
        val valid = when (choice) {
            is SearchOptionValue.Multiple -> field.type == SearchOptionType.MULTI_SELECT &&
                choice.values.all { it in keys } && choice.values.size == choice.values.distinct().size
            is SearchOptionValue.Single -> when (field.type) {
                SearchOptionType.SELECT -> choice.value in keys || (keys.isEmpty() && choice.value == "")
                SearchOptionType.DROPDOWN -> choice.value == null || choice.value in keys
                SearchOptionType.MULTI_SELECT -> false
            }
        }
        require(valid) { "Invalid search option value" }
    }
}

internal fun SearchOptionsSnapshot.toArguments() = JsonArray(values.map {
    when (it) {
        is SearchOptionValue.Single -> it.value?.let(::JsonPrimitive) ?: JsonNull
        is SearchOptionValue.Multiple -> JsonPrimitive(Json.encodeToString(it.values))
    }
})
