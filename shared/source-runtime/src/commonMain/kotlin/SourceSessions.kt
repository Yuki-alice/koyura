package app.koyura.source.runtime

import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.renderCookieHeader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

/** Implementations store the opaque payload in a platform vault or encrypt it with a vault key. */
interface SourceSecretStore {
    fun read(sourceKey: String): String?
    fun write(sourceKey: String, value: String?)
}

internal expect class SessionLock() {
    fun <T> locked(action: () -> T): T
}

@Serializable
data class SourceCookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val expires: Long? = null,
    val maxAge: Int? = null,
)

@Serializable
internal data class StoredCookie(val cookie: SourceCookie, val host: String, val path: String)

@Serializable
internal data class SourceSessionState(
    val cookies: List<StoredCookie> = emptyList(),
    val data: Map<String, JsonElement> = emptyMap(),
    val settings: Map<String, JsonElement> = emptyMap(),
)

/** One session store is shared by script requests, image requests and WebView handoff. */
class SourceSessions(private val secrets: SourceSecretStore? = null) {
    internal data class Lease(val key: String, val generation: Long)
    private val lock = SessionLock()
    private val states = mutableMapOf<String, SourceSessionState>()
    private val generations = mutableMapOf<String, Long>()

    internal fun lease(key: String): Lease = lock.locked { Lease(key, generations[key] ?: 0L) }

    internal fun cookies(key: String, url: String): List<SourceCookie> =
        lock.locked { matching(state(key), sessionUrl(url)).map { it.cookie } }

    internal fun requestHeader(lease: Lease, url: Url): String? = lock.locked {
        if (!isCurrent(lease)) return@locked null
        matching(state(lease.key), url).joinToString("; ") {
            renderCookieHeader(Cookie(it.cookie.name, it.cookie.value, encoding = CookieEncoding.RAW))
        }.takeIf { it.isNotEmpty() }
    }

    internal fun receive(lease: Lease, url: Url, cookies: List<Cookie>) = lock.locked {
        if (cookies.isEmpty() || !isCurrent(lease)) return@locked
        updateCookies(lease.key, url, cookies.map {
            SourceCookie(it.name, it.value, it.domain, it.path, it.secure, it.httpOnly, it.expires?.timestamp, it.maxAge)
        })
    }

    internal fun setCookies(key: String, url: String, cookies: List<SourceCookie>) = lock.locked {
        updateCookies(key, sessionUrl(url), cookies)
    }

    internal fun deleteCookies(key: String, url: String) = lock.locked {
        val host = sessionUrl(url).host.lowercase()
        val state = state(key)
        update(key, state.copy(cookies = state.cookies.filterNot { cookieHostMatches(it, host) }))
    }

    fun clear(key: String) = lock.locked {
        val settings = state(key).settings
        update(key, SourceSessionState(settings = settings))
        generations[key] = (generations[key] ?: 0L) + 1
    }

    fun forget(key: String) = lock.locked {
        update(key, SourceSessionState())
        generations[key] = (generations[key] ?: 0L) + 1
    }

    fun saveWebLogin(key: String, url: String, cookies: List<SourceCookie>, storage: JsonObject) = lock.locked {
        val current = state(key)
        val data = current.data + ("_localStorage" to storage) +
            ("account" to JsonPrimitive("ok"))
        val address = sessionUrl(url)
        update(key, current.copy(data = data, cookies = mergeCookies(current, address, cookies)))
    }

    fun applyLoginMarker(key: String) = applyData(key, mapOf("account" to JsonPrimitive("ok")))

    internal fun getData(key: String, name: String): JsonElement = lock.locked { state(key).data[name] ?: JsonNull }

    internal fun applyData(key: String, changes: Map<String, JsonElement?>) = lock.locked {
        if (changes.isEmpty()) return@locked
        val current = state(key)
        val data = current.data.toMutableMap()
        for ((name, value) in changes) {
            if (value == null) data.remove(name) else data[name] = value
        }
        update(key, current.copy(data = data))
    }

    internal fun getSetting(key: String, name: String): JsonElement = lock.locked {
        state(key).settings[name] ?: JsonNull
    }

    fun setSetting(key: String, name: String, value: JsonElement?) = lock.locked {
        val current = state(key)
        val settings = if (value == null) current.settings - name else current.settings + (name to value)
        update(key, current.copy(settings = settings))
    }

    private fun isCurrent(lease: Lease) = lease.generation == (generations[lease.key] ?: 0L)

    private fun state(key: String): SourceSessionState {
        require(key.matches(SOURCE_KEY)) { "Invalid source key" }
        return states.getOrPut(key) {
            secrets?.read(key)?.let { Json.decodeFromString<SourceSessionState>(it) } ?: SourceSessionState()
        }
    }

    private fun update(key: String, next: SourceSessionState) {
        if (next == state(key)) return
        secrets?.write(key, if (next == SourceSessionState()) null else Json.encodeToString(next))
        states[key] = next
    }

    private fun updateCookies(key: String, url: Url, cookies: List<SourceCookie>) {
        val current = state(key)
        update(key, current.copy(cookies = mergeCookies(current, url, cookies)))
    }

    private fun mergeCookies(state: SourceSessionState, url: Url, cookies: List<SourceCookie>): List<StoredCookie> {
        val now = Clock.System.now().toEpochMilliseconds()
        val host = url.host.lowercase()
        val merged = state.cookies.filter { (it.cookie.expires ?: Long.MAX_VALUE) > now }.toMutableList()
        for (cookie in cookies) {
            if (!COOKIE_NAME.matches(cookie.name) || cookie.value.any { it in INVALID_COOKIE_VALUE }) continue
            val domain = cookie.domain?.lowercase()?.trimStart('.')
            if (domain != null && ('.' !in domain || !domainMatches(host, domain))) continue
            if (cookie.secure && url.protocol != URLProtocol.HTTPS) continue
            val path = cookie.path?.takeIf { it.startsWith('/') } ?: url.encodedPath.substringBeforeLast('/').ifEmpty { "/" }
            val expires = cookie.maxAge?.let { now + it.toLong() * 1000 } ?: cookie.expires
            val effectiveHost = domain ?: host
            merged.removeAll { it.cookie.name == cookie.name && it.host == effectiveHost && it.path == path }
            if (expires == null || expires > now) {
                merged += StoredCookie(cookie.copy(domain = domain, path = path, expires = expires), effectiveHost, path)
            }
        }
        return merged
    }

    private fun matching(state: SourceSessionState, url: Url): List<StoredCookie> {
        val now = Clock.System.now().toEpochMilliseconds()
        val path = url.encodedPath.ifEmpty { "/" }
        return state.cookies.filter {
            (it.cookie.expires ?: Long.MAX_VALUE) > now && cookieHostMatches(it, url.host.lowercase()) &&
                (path == it.path || path.startsWith(it.path.trimEnd('/') + "/")) &&
                (!it.cookie.secure || url.protocol == URLProtocol.HTTPS)
        }.sortedByDescending { it.path.length }
    }

    private fun cookieHostMatches(stored: StoredCookie, host: String): Boolean =
        if (stored.cookie.domain == null) host == stored.host else domainMatches(host, stored.host)

    private fun domainMatches(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    private fun sessionUrl(raw: String): Url = Url(if ("://" in raw) raw else "https://$raw").also {
        require(it.protocol == URLProtocol.HTTP || it.protocol == URLProtocol.HTTPS) { "Invalid session URL" }
    }

    private companion object {
        val SOURCE_KEY = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val COOKIE_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
        val INVALID_COOKIE_VALUE = ('\u0000'..'\u001f').toSet() + setOf('\u007f', ';')
    }
}
