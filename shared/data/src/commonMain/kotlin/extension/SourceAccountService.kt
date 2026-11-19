package app.koyura.data.extension

import app.koyura.source.runtime.SourceCookie
import app.koyura.source.runtime.VeneraSourceRuntime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class WebLoginPage(val url: String, val title: String)

data class WebLoginCapture(
    val page: WebLoginPage,
    val cookies: List<SourceCookie>,
    val localStorage: JsonObject,
)

/**
 * The platform opens a fresh WebView session and calls [checkStatus] after main-frame navigation
 * or title changes. If it returns true, capture only that page's cookies and local storage.
 * Close the view and dispose its private website data on success, cancellation or failure.
 */
fun interface WebLoginBrowser {
    suspend fun open(url: String, checkStatus: suspend (WebLoginPage) -> Boolean): WebLoginCapture?
}

internal data class WebLoginOwner(val key: String, val runtime: VeneraSourceRuntime, val instance: Long, val generation: Long)

class PendingWebLogin internal constructor(private val save: suspend () -> Boolean) {
    private val mutex = Mutex()
    private var result: Boolean? = null

    /** If saving throws, the same captured login can be submitted again. */
    suspend fun complete(): Boolean = mutex.withLock {
        result ?: save().also { result = it }
    }
}

class SourceAccountService internal constructor(
    private val extensions: ExtensionService,
    private val browser: WebLoginBrowser? = null,
) {
    val supportsWebLogin: Boolean get() = browser != null

    suspend fun captureWebLogin(sourceKey: String): PendingWebLogin? =
        captureWebLogin(sourceKey, checkNotNull(browser) { "Web login is unavailable on this platform" })

    suspend fun login(sourceKey: String, username: String, password: String) =
        extensions.loginSource(sourceKey, strings(username, password))

    suspend fun loginForm(sourceKey: String, values: List<String>): Boolean =
        extensions.loginSourceForm(sourceKey, JsonArray(values.map(::JsonPrimitive)))

    suspend fun reLogin(sourceKey: String): Boolean = extensions.reLoginSource(sourceKey)

    suspend fun loginWithCookies(sourceKey: String, values: List<String>): Boolean =
        extensions.validateSourceCookies(sourceKey, JsonArray(listOf(JsonArray(values.map(::JsonPrimitive)))))

    suspend fun logout(sourceKey: String) = extensions.logoutSource(sourceKey)

    suspend fun loginWithWebView(sourceKey: String, browser: WebLoginBrowser): Boolean =
        captureWebLogin(sourceKey, browser)?.complete() ?: false

    suspend fun captureWebLogin(sourceKey: String, browser: WebLoginBrowser): PendingWebLogin? {
        val (owner, start) = extensions.beginWebLogin(sourceKey) ?: return null
        requireWebUrl(start)
        var accepted: WebLoginPage? = null
        val capture = browser.open(start) { page ->
            requireWebUrl(page.url)
            extensions.checkWebLogin(owner, strings(page.url, page.title)).also { if (it) accepted = page }
        } ?: return null
        require(capture.page == accepted) { "WebView capture must come from the accepted login page" }
        return PendingWebLogin { extensions.finishWebLogin(owner, capture) }
    }

    private fun requireWebUrl(value: String) {
        val url = Url(value)
        require(url.protocol in setOf(URLProtocol.HTTP, URLProtocol.HTTPS) && url.host.isNotEmpty() &&
            url.user == null && url.password == null && "://" in value) { "Invalid web login URL" }
    }

    private fun strings(vararg values: String) = JsonArray(values.map(::JsonPrimitive))
}
