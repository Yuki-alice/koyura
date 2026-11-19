package app.koyura.source.runtime

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Comment
import com.fleeksoft.ksoup.nodes.DataNode
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Each engine owns its documents. Keep upstream's nine-document retention limit. */
internal class HtmlHost {
    private val documents = linkedMapOf<Int, ParsedHtml>()
    private var nextDocument = 0

    fun dispatch(payload: String): String {
        val request = Json.parseToJsonElement(payload).jsonObject
        val result = try {
            JsonObject(mapOf("value" to handle(request)))
        } catch (error: Exception) {
            JsonObject(mapOf("error" to JsonPrimitive(error.message ?: "HTML operation failed")))
        }
        return result.toString()
    }

    private fun handle(request: JsonObject): JsonElement {
        val operation = request.getValue("operation").jsonPrimitive.content
        if (operation == "parse") {
            val html = request.getValue("value").jsonPrimitive.content
            require(html.length <= MAX_HTML_LENGTH) { "HTML document is too large" }
            val parsed = Ksoup.parse(html)
            parsed.outputSettings().prettyPrint(false)
            if (documents.size == MAX_DOCUMENTS) documents.remove(documents.keys.first())
            val id = nextDocument++
            documents[id] = ParsedHtml(parsed)
            return JsonPrimitive(id)
        }
        val id = request.getValue("document").jsonPrimitive.int
        if (operation == "dispose") {
            documents.remove(id)
            return JsonNull
        }
        val document = documents[id] ?: error("HTML document has been disposed")
        val node = request["node"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.int?.let(document.nodes::get)
        val element = node as? Element
        fun value() = request.getValue("value").jsonPrimitive.content
        return when (operation) {
            "query" -> document.references((element ?: document.root).select(value()).filter { it !== element })
            "queryFirst" -> document.reference((element ?: document.root).selectStream(value()).firstOrNull { it !== element })
            "byId" -> document.reference(document.root.getElementById(value()))
            "text" -> when (node) {
                is TextNode -> JsonPrimitive(node.getWholeText())
                is Comment -> JsonPrimitive(node.getData())
                is DataNode -> JsonPrimitive(node.getWholeData())
                is Element -> JsonPrimitive(node.wholeText())
                else -> JsonNull
            }
            "attributes" -> JsonObject(checkNotNull(element).attributes().associate { it.key to JsonPrimitive(it.value) })
            "children" -> document.references(checkNotNull(element).children())
            "nodes" -> document.references(checkNotNull(element).childNodes())
            "html" -> JsonPrimitive(checkNotNull(element).html())
            "parent" -> document.reference(checkNotNull(element).parent()?.takeUnless { it is Document })
            "classes" -> JsonArray(checkNotNull(element).classNames().map(::JsonPrimitive))
            "id" -> JsonPrimitive(checkNotNull(element).id())
            "name" -> JsonPrimitive(checkNotNull(element).tagName())
            "previous" -> document.reference(checkNotNull(element).previousElementSibling())
            "next" -> document.reference(checkNotNull(element).nextElementSibling())
            "type" -> JsonPrimitive(when (node) {
                is Document -> "document"
                is Element -> "element"
                is Comment -> "comment"
                is TextNode, is DataNode -> "text"
                else -> "unknown"
            })
            else -> error("Unknown HTML operation: $operation")
        }
    }

    private class ParsedHtml(val root: Document) {
        val nodes = mutableListOf<Node>()
        private val ids = mutableMapOf<Node, Int>()

        fun references(values: Iterable<Node>) = JsonArray(values.map(::reference))

        fun reference(node: Node?): JsonElement {
            if (node == null) return JsonNull
            return JsonPrimitive(ids.getOrPut(node) {
                nodes.add(node)
                nodes.lastIndex
            })
        }
    }

    private companion object {
        const val MAX_DOCUMENTS = 9
        const val MAX_HTML_LENGTH = 4 * 1024 * 1024
    }
}
