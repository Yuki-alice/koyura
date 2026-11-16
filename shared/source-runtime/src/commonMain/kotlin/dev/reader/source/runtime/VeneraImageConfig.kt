package dev.reader.source.runtime

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * An image loading config parsed through [VeneraSourceRuntime.parseImageConfig].
 *
 * Function-valued fields named in the capturable list arrive as integer
 * [handles] into a JS-side registry instead of serialized values; everything
 * else is plain JSON. [onResponse]/[onLoadFailed] are the tracked handles (or
 * null when absent); [liveHandles] lists every handle the caller must release
 * via [VeneraSourceRuntime.releaseImageCallbacks].
 */
data class ParsedImageConfig(
    val url: String?,
    val method: String?,
    val headers: Map<String, String>,
    val data: JsonElement?,
    val onResponse: Int?,
    val onLoadFailed: Int?,
    val liveHandles: Set<Int>,
) {
    companion object {
        fun decode(element: JsonElement): ParsedImageConfig {
            val root = element as? JsonObject
                ?: throw VeneraScriptException("Image config must be an object")
            val data = root["data"]
                ?: throw VeneraScriptException("Image config is missing its plain body")
            val handles = (root["handles"] as? JsonObject)
                ?: throw VeneraScriptException("Image config is missing its callback handles")
            fun handle(name: String): Int? = when (val value = handles[name]) {
                null, is JsonNull -> null
                is JsonPrimitive -> value.content.toIntOrNull()
                    ?: throw VeneraScriptException("Image config handle is not an integer: $name")
                else -> throw VeneraScriptException("Image config handle is not an integer: $name")
            }
            val body = data as? JsonObject
                ?: throw VeneraScriptException("Image config must be an object")
            fun optionalString(name: String): String? {
                val value = body[name] ?: return null
                if (value is JsonNull) return null
                return (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: throw VeneraScriptException("Image config field is not a string: $name")
            }
            val headers = (body["headers"] as? JsonObject)?.mapValues { (name, value) ->
                (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: throw VeneraScriptException("Image config header is not a string: $name")
            } ?: emptyMap()
            return ParsedImageConfig(
                url = optionalString("url"),
                method = optionalString("method"),
                headers = headers,
                data = body["data"]?.takeIf { it !is JsonNull },
                onResponse = handle("onResponse"),
                onLoadFailed = handle("onLoadFailed"),
                liveHandles = handles.values.mapNotNullTo(linkedSetOf()) {
                    (it as? JsonPrimitive)?.content?.toIntOrNull()
                },
            )
        }
    }
}
