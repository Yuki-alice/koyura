package app.koyura.source.runtime

import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.renderCookieHeader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
class SourceSessions(
    private val secrets: SourceSecretStore? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    internal data class Lease(val key: String, val generation: Long)
    private class Entry(val key: String) {
        val lock = SessionLock()
        var state: SourceSessionState? = null
        var generation = 0L
        var dirty = false
        var pending: Job? = null
    }

    private val lock = SessionLock()
    private val entries = mutableMapOf<String, Entry>()
    private val writer = CoroutineScope(SupervisorJob() + dispatcher)
    private var closed = false

    private fun <T> access(key: String, operation: Entry.() -> T): T {
        require(key.matches(SOURCE_KEY)) { "Invalid source key" }
        val entry = lock.locked {
            check(!closed) { "Source sessions are closed" }
            entries.getOrPut(key) { Entry(key) }
        }
        return entry.lock.locked {
            lock.locked { check(!closed) { "Source sessions are closed" } }
            entry.operation()
        }
    }

    internal fun lease(key: String): Lease = access(key) { Lease(key, generation) }

    internal fun cookies(key: String, url: String): List<SourceCookie> =
        access(key) { matching(load(this), sessionUrl(url)).map { it.cookie } }

    internal fun requestHeader(lease: Lease, url: Url): String? = access(lease.key) {
        if (generation != lease.generation) return@access null
        matching(load(this), url).joinToString("; ") {
            renderCookieHeader(Cookie(it.cookie.name, it.cookie.value, encoding = CookieEncoding.RAW))
        }.takeIf { it.isNotEmpty() }
    }

    internal fun receive(lease: Lease, url: Url, cookies: List<Cookie>) = access(lease.key) {
        if (cookies.isEmpty() || generation != lease.generation) return@access
        val current = load(this)
        val next = current.copy(cookies = mergeCookies(current, url, cookies.map {
            SourceCookie(it.name, it.value, it.domain, it.path, it.secure, it.httpOnly, it.expires?.timestamp, it.maxAge)
        }))
        if (next == current) return@access
        state = next
        if (secrets == null) return@access
        dirty = true
        pending?.cancel()
        pending = writer.launch {
            delay(COOKIE_DEBOUNCE_MILLIS)
            this@access.lock.locked {
                if (isActive && dirty) {
                    // A vault failure leaves the latest snapshot dirty for the next save or close.
                    runCatching { persist(this@access, load(this@access)) }
                }
            }
        }
    }

    internal fun setCookies(key: String, url: String, cookies: List<SourceCookie>) = access(key) {
        val current = load(this)
        update(this, current.copy(cookies = mergeCookies(current, sessionUrl(url), cookies)))
    }

    internal fun deleteCookies(key: String, url: String) = access(key) {
        val host = sessionUrl(url).host.lowercase()
        val current = load(this)
        update(this, current.copy(cookies = current.cookies.filterNot { cookieHostMatches(it, host) }))
    }

    fun clear(key: String) = access(key) {
        update(this, SourceSessionState(settings = load(this).settings))
        generation++
    }

    fun forget(key: String) = access(key) {
        update(this, SourceSessionState())
        generation++
    }

    fun saveWebLogin(key: String, url: String, cookies: List<SourceCookie>, storage: JsonObject) = access(key) {
        val current = load(this)
        val data = current.data + ("_localStorage" to storage) + ("account" to JsonPrimitive("ok"))
        update(this, current.copy(data = data, cookies = mergeCookies(current, sessionUrl(url), cookies)))
    }

    fun applyLoginMarker(key: String) = applyData(key, mapOf("account" to JsonPrimitive("ok")))

    fun saveFormLogin(key: String, arguments: JsonArray) = applyData(key, mapOf("account" to arguments))

    fun accountMarker(key: String): JsonElement = getData(key, "account")

    internal fun getData(key: String, name: String): JsonElement = access(key) { load(this).data[name] ?: JsonNull }

    internal fun applyData(key: String, changes: Map<String, JsonElement?>) = access(key) {
        if (changes.isEmpty()) return@access
        val current = load(this)
        val data = current.data.toMutableMap()
        for ((name, value) in changes) {
            if (value == null) data.remove(name) else data[name] = value
        }
        update(this, current.copy(data = data))
    }

    internal fun getSetting(key: String, name: String): JsonElement = access(key) {
        load(this).settings[name] ?: JsonNull
    }

    fun setSetting(key: String, name: String, value: JsonElement?) = access(key) {
        val current = load(this)
        val settings = if (value == null) current.settings - name else current.settings + (name to value)
        update(this, current.copy(settings = settings))
    }

    /** Flushes the last cookie burst on graceful application shutdown. */
    fun close() {
        val active = lock.locked {
            closed = true
            entries.values.toList()
        }
        writer.cancel()
        var failure: Exception? = null
        for (entry in active) {
            entry.lock.locked {
                try {
                    if (entry.dirty) persist(entry, load(entry))
                } catch (error: Exception) {
                    if (failure == null) failure = error else failure?.addSuppressed(error)
                }
            }
        }
        failure?.let { throw it }
    }

    private fun load(entry: Entry): SourceSessionState = entry.state ?: (
        secrets?.read(entry.key)?.let { Json.decodeFromString<SourceSessionState>(it) } ?: SourceSessionState()
    ).also { entry.state = it }

    private fun update(entry: Entry, next: SourceSessionState) {
        if (next == load(entry) && !entry.dirty) return
        persist(entry, next)
        entry.pending?.cancel()
        entry.pending = null
        entry.state = next
    }

    private fun persist(entry: Entry, next: SourceSessionState) {
        secrets?.write(entry.key, if (next == SourceSessionState()) null else Json.encodeToString(next))
        entry.dirty = false
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
        const val COOKIE_DEBOUNCE_MILLIS = 250L
        val SOURCE_KEY = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val COOKIE_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
        val INVALID_COOKIE_VALUE = ('\u0000'..'\u001f').toSet() + setOf('\u007f', ';')
    }
}
