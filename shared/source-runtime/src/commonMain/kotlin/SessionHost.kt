package app.koyura.source.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class SessionHost(private val sessions: SourceSessions) {
    private val json = Json { encodeDefaults = true }

    fun dispatch(payload: String): String {
        val request = json.parseToJsonElement(payload).jsonObject
        return try {
            JsonObject(mapOf("value" to handle(request))).toString()
        } catch (_: Exception) {
            JsonObject(mapOf("error" to JsonPrimitive("Source session operation failed"))).toString()
        }
    }

    private fun handle(request: JsonObject): JsonElement {
        fun string(name: String) = request.getValue(name).jsonPrimitive.content
        val key = string("key")
        return when (string("operation")) {
            "getCookies" -> json.encodeToJsonElement(sessions.cookies(key, string("url")))
            "setCookies" -> {
                sessions.setCookies(key, string("url"), json.decodeFromJsonElement(request.getValue("cookies")))
                JsonNull
            }
            "deleteCookies" -> {
                sessions.deleteCookies(key, string("url"))
                JsonNull
            }
            "loadData" -> sessions.getData(key, string("name"))
            "loadSetting" -> sessions.getSetting(key, string("name"))
            "applyData" -> {
                val changes = request.getValue("changes").jsonObject.mapValues { (_, entry) ->
                    entry.jsonObject["value"]
                }
                sessions.applyData(key, changes)
                JsonNull
            }
            else -> error("Unknown session operation")
        }
    }
}
