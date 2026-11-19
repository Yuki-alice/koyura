package app.koyura.data.extension

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object WebLoginMonitor {
    const val STORAGE_SCRIPT = "JSON.stringify({url:location.href,title:document.title,storage:{...localStorage}})"

    fun allowedUrl(value: String): Boolean = runCatching {
        val url = Url(value)
        "://" in value && url.protocol in setOf(URLProtocol.HTTP, URLProtocol.HTTPS) &&
            url.host.isNotEmpty() && url.user == null && url.password == null
    }.getOrDefault(false)

    fun storage(page: WebLoginPage, result: String?): JsonObject? = runCatching {
        val objectValue = Json.parseToJsonElement(checkNotNull(result)).jsonObject
        if (objectValue.getValue("url").jsonPrimitive.content != page.url ||
            objectValue.getValue("title").jsonPrimitive.content != page.title
        ) return null
        objectValue.getValue("storage").jsonObject
    }.getOrNull()

    suspend fun awaitCapture(
        dismissed: Deferred<Unit>,
        readPage: () -> WebLoginPage?,
        checkStatus: suspend (WebLoginPage) -> Boolean,
        capture: suspend (WebLoginPage) -> WebLoginCapture?,
    ): WebLoginCapture? = coroutineScope {
        val observer = async {
            var previous: WebLoginPage? = null
            var result: WebLoginCapture? = null
            while (result == null) {
                val page = readPage()?.takeIf { allowedUrl(it.url) }
                if (page != null && page != previous && checkStatus(page)) {
                    if (readPage() == page) result = capture(page)
                    if (result == null) {
                        delay(150)
                        continue
                    }
                }
                previous = page
                if (result == null) delay(150)
            }
            result
        }
        try {
            select {
                dismissed.onAwait { null }
                observer.onAwait { it }
            }
        } finally {
            observer.cancel()
        }
    }
}
