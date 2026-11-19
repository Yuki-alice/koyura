package app.koyura.source.runtime

import io.ktor.http.HttpHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal class SourceRequestPolicy(
    private val clock: TimeSource = TimeSource.Monotonic,
) {
    data class Path(val sourceKey: String?, val method: String, val url: String)

    sealed interface Response {
        val status: Int
        val headers: Map<String, String>
        val maximumBodySize: Long

        data class Text(val value: VeneraHttpResponse) : Response {
            override val status get() = value.status
            override val headers get() = value.headers
            override val maximumBodySize get() = value.body.length.toLong() * 4
        }

        data class Bytes(val value: VeneraBytesResponse) : Response {
            override val status get() = value.status
            override val headers get() = value.headers
            override val maximumBodySize get() = value.body.size.toLong()
        }
    }

    private class Gate {
        val mutex = Mutex()
        var users = 0
    }

    private data class Entry(val response: Response, val created: TimeMark, val lifetime: Duration, val size: Long)
    private val lock = SessionLock()
    private val gates = mutableMapOf<Path, Gate>()
    private val entries = linkedMapOf<ByteString, Entry>()
    private var bytes = 0L

    suspend fun <T> serialize(path: Path, enabled: Boolean, block: suspend () -> T): T {
        if (!enabled) return block()
        val gate = lock.locked { gates.getOrPut(path, ::Gate).also { it.users++ } }
        try {
            return gate.mutex.withLock { block() }
        } finally {
            lock.locked {
                gate.users--
                if (gate.users == 0) gates.remove(path)
            }
        }
    }

    fun lookup(key: ByteString, control: String?): Response? = lock.locked {
        val entry = entries[key] ?: return@locked null
        if (control == "no" || entry.created.elapsedNow() >= entry.lifetime) {
            remove(key)
            return@locked null
        }
        val lifetime = if (control == "long") LONG_LIFETIME else SHORT_LIFETIME
        if (entry.created.elapsedNow() >= lifetime) return@locked null
        entries.remove(key)
        entries[key] = entry
        entry.response.snapshot(hit = true)
    }

    fun save(key: ByteString, response: Response) {
        val lifetime = response.cacheLifetime()
        lock.locked {
            remove(key)
            val bodySize = response.maximumBodySize
            if (lifetime == Duration.ZERO || bodySize !in 1..MAX_BODY_SIZE) return@locked
            val size = bodySize + response.headers.entries.sumOf { (name, value) ->
                4L * (name.length + value.length)
            }
            if (size > MAX_ENTRY_SIZE) return@locked
            while (entries.size >= MAX_ENTRIES || bytes + size > MAX_CACHE_SIZE) remove(entries.keys.first())
            entries[key] = Entry(response.snapshot(hit = false), clock.markNow(), lifetime, size)
            bytes += size
        }
    }

    fun clear() = lock.locked {
        entries.clear()
        bytes = 0
    }

    private fun remove(key: ByteString) {
        val entry = entries.remove(key) ?: return
        bytes -= entry.size
    }

    private fun Response.cacheLifetime(): Duration {
        if (status != 200 || headers.keys.any { it.equals(HttpHeaders.SetCookie, ignoreCase = true) } ||
            headers.header(HttpHeaders.Vary)?.split(',')?.any { it.trim() == "*" } == true
        ) return Duration.ZERO
        val controls = headers.header(HttpHeaders.CacheControl).orEmpty().split(',').map { it.trim().lowercase() }
        if (controls.any { it.substringBefore('=') in setOf("no-store", "no-cache") }) return Duration.ZERO
        val serverAge = controls.firstNotNullOfOrNull {
            if (it.startsWith("max-age=")) it.substringAfter('=').trim('"').toLongOrNull() else null
        }?.coerceAtLeast(0)?.seconds
        return minOf(serverAge ?: LONG_LIFETIME, LONG_LIFETIME)
    }

    private fun Response.snapshot(hit: Boolean): Response {
        val copy = if (hit) headers.filterKeys { !it.equals("venera-cache", ignoreCase = true) }
            .plus("venera-cache" to "true") else headers.toMap()
        return when (this) {
            is Response.Text -> Response.Text(value.copy(headers = copy))
            is Response.Bytes -> Response.Bytes(value.copy(body = value.body.copyOf(), headers = copy))
        }
    }

    private companion object {
        val SHORT_LIFETIME = 5.seconds
        val LONG_LIFETIME = 6.hours
        const val MAX_BODY_SIZE = 1_048_576L
        const val MAX_ENTRY_SIZE = MAX_BODY_SIZE + 65_536
        const val MAX_CACHE_SIZE = 10_485_760L
        const val MAX_ENTRIES = 256
    }
}

internal fun Map<String, String>.header(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
