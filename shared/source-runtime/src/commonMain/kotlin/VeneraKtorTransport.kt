package app.koyura.source.runtime

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.content.ByteArrayContent
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okio.Buffer
import okio.ByteString.Companion.decodeBase64

/**
 * Source HTTP transport backed by Ktor.
 *
 * Request and response mapping:
 * - Forwards HTTP method, full URL (including query string), request headers and
 *   text or binary body. A `data` that is already a JSON string (scripts call
 *   `JSON.stringify` before `Network.post`) is sent as its raw text, not
 *   re-encoded with extra quotes. JSON numbers/booleans use their literal text;
 *   objects/arrays use their JSON encoding; null/absent means no body.
 * - When the script omits `User-Agent` (any casing), sends the upstream default
 *   browser UA from `reference/Venera-Next/lib/foundation/consts.dart` (`webUA`),
 *   mirroring `lib/foundation/js_engine.dart` `_performHttp`. An explicit script
 *   UA in any casing is forwarded as-is and never duplicated.
 * - HTTP 4xx/5xx are returned as [VeneraHttpResponse] for the script to check
 *   via `status`, even when the injected [HttpClient] uses `expectSuccess = true`:
 *   the request catches Ktor's [ResponseException] and maps it back to a
 *   response. Only transport failures (connect/timeout/DNS/closed engine)
 *   throw, which [VeneraSourceRuntime] converts into a script-visible request
 *   error. [CancellationException] always propagates.
 * - Multi-value response headers are joined with `,` like upstream
 *   `js_engine.dart` (`values.join(',')`).
 *
 * Host control fields stripped before sending (case-insensitive, never sent to
 * the site): `cache-time`, `prevent-parallel`, `http_client`.
 *
 * Source GET caching and opt-in request serialization run around the Ktor call.
 * Ktor continues to handle redirects and apply the source Cookie jar to each
 * actual exchange. Dio-specific `http_client` and `extra` are ignored.
 *
 * Ownership: [VeneraSourceRuntime] never closes the transport. If [ownsClient]
 * is true, call [close] to close the underlying [HttpClient]; otherwise the
 * caller that supplied [client] owns it (tests with MockEngine own their
 * client, production code using `createDefaultHttpClient` should own it).
 */
class KtorVeneraHttpTransport(
    private val client: HttpClient,
    private val ownsClient: Boolean = false,
    private val sessions: SourceSessions? = null,
) : VeneraHttpTransport {
    private val sessionClient = sessions?.let(client::withSourceSessions)
    private val requestClient get() = sessionClient ?: client

    private val requestPolicy = SourceRequestPolicy()

    override suspend fun execute(request: VeneraHttpRequest): VeneraHttpResponse =
        (exchange(request, binary = false) as SourceRequestPolicy.Response.Text).value

    override suspend fun executeBytes(request: VeneraHttpRequest): VeneraBytesResponse =
        (exchange(request, binary = true) as SourceRequestPolicy.Response.Bytes).value

    suspend fun executeImage(request: VeneraHttpRequest): VeneraBytesResponse =
        (exchange(request, binary = true, allowCache = false) as SourceRequestPolicy.Response.Bytes).value

    private suspend fun exchange(
        request: VeneraHttpRequest,
        binary: Boolean,
        allowCache: Boolean = true,
    ): SourceRequestPolicy.Response {
        val url = Url(request.url)
        val method = request.method.uppercase()
        val explicit = request.headers.header(HttpHeaders.Cookie)
        val path = SourceRequestPolicy.Path(request.sourceKey, method, url.toString())
        val serialize = request.headers.header("prevent-parallel").equals("true", ignoreCase = true)
        val lease = request.sourceKey?.let { sessions?.lease(it) }
        val session = lease?.let { SessionRequest(it, url, explicit) }
        return requestPolicy.serialize(path, serialize) {
            fun identity() = if (lease != null) sessions?.httpIdentity(lease, url, explicit)
                else SourceSessions.HttpIdentity(0L, explicit)

            val eligible = allowCache && request.sourceKey != null && method == "GET" &&
                textBody(request) == null && request.binaryData == null && request.headers.header(HttpHeaders.Range) == null
            val identity = if (eligible) identity() else null
            val key = identity?.let { cacheKey(path, request.headers, it, binary) }
            val bypass = request.headers.header(HttpHeaders.CacheControl).orEmpty().split(',').any {
                it.trim().lowercase() in setOf("no-cache", "no-store", "max-age=0")
            }
            val control = if (bypass) "no" else request.headers.header("cache-time")?.trim()?.lowercase()
            if (key != null) requestPolicy.lookup(key, control)?.let { return@serialize it }

            val response = fetch(request, session)
            val headers = response.headers.entries().associate { (name, values) -> name to values.joinToString(",") }
            val result = if (binary) {
                SourceRequestPolicy.Response.Bytes(VeneraBytesResponse(response.status.value, response.readRawBytes(), headers))
            } else {
                SourceRequestPolicy.Response.Text(VeneraHttpResponse(response.status.value, response.bodyAsText(), headers))
            }
            if (key != null && control != "no" && response.call.request.url == url && identity == identity()) {
                requestPolicy.save(key, result)
            }
            result
        }
    }

    private suspend fun fetch(request: VeneraHttpRequest, session: SessionRequest?): HttpResponse =
        try {
            requestClient.request { applyVeneraRequest(request, session) }
        } catch (failure: ResponseException) {
            failure.response
        }

    private fun cacheKey(
        path: SourceRequestPolicy.Path,
        headers: Map<String, String>,
        identity: SourceSessions.HttpIdentity,
        binary: Boolean,
    ) = Buffer().apply {
        fun field(value: String) {
            val bytes = value.encodeToByteArray()
            writeInt(bytes.size)
            write(bytes)
        }
        field(checkNotNull(path.sourceKey))
        field(path.method)
        field(path.url)
        writeByte(if (binary) 1 else 0)
        writeLong(identity.revision)
        field(identity.cookie.orEmpty())
        for ((name, value) in headers.entries.sortedBy { it.key.lowercase() }) {
            if (isHostControlHeader(name)) continue
            field(name.lowercase())
            field(value)
        }
    }.sha256()

    private fun HttpRequestBuilder.applyVeneraRequest(request: VeneraHttpRequest, session: SessionRequest?) {
        val text = textBody(request)
        val bytes = request.binaryData?.let { requireNotNull(it.decodeBase64()).toByteArray() }
        val contentTypeHeader = request.headers.entries
            .firstOrNull { it.key.equals(HttpHeaders.ContentType, ignoreCase = true) }
            ?.value
        val hasUserAgent = request.headers.keys.any { it.equals(HttpHeaders.UserAgent, ignoreCase = true) }
        method = HttpMethod.parse(request.method.uppercase())
        url(request.url)
        if (session != null) attributes.put(sourceSessionRequest, session)
        headers {
            for ((name, value) in request.headers) {
                if (isHostControlHeader(name)) continue
                // Content-Type with a body travels on the content itself;
                // without a body it is a plain header.
                if ((text != null || bytes != null) && name.equals(HttpHeaders.ContentType, ignoreCase = true)) continue
                append(name, value)
            }
            if (!hasUserAgent) {
                append(HttpHeaders.UserAgent, DEFAULT_BROWSER_USER_AGENT)
            }
        }
        if (bytes != null) {
            val contentType = contentTypeHeader?.let(ContentType::parse) ?: ContentType.Application.OctetStream
            setBody(ByteArrayContent(bytes, contentType))
        } else if (text != null) {
            if (contentTypeHeader != null) {
                val contentType = runCatching { ContentType.parse(contentTypeHeader) }.getOrNull()
                if (contentType != null) {
                    setBody(TextContent(text, contentType))
                } else {
                    header(HttpHeaders.ContentType, contentTypeHeader)
                    setBody(text)
                }
            } else {
                setBody(text)
            }
        }
    }

    /**
     * Closes the underlying [HttpClient] only when this transport owns it.
     * No-op when the client was injected by the caller.
     */
    fun close() {
        requestPolicy.clear()
        sessionClient?.close()
        if (ownsClient) client.close()
    }

    companion object {
        /**
         * Upstream default browser UA (`webUA` in
         * `reference/Venera-Next/lib/foundation/consts.dart`), applied when the
         * script omits `User-Agent`, mirroring `_performHttp` in
         * `reference/Venera-Next/lib/foundation/js_engine.dart`.
         */
        const val DEFAULT_BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"

        private val hostControlHeaders = setOf("cache-time", "prevent-parallel", "http_client")

        internal fun isHostControlHeader(name: String): Boolean =
            name.lowercase() in hostControlHeaders

        internal fun textBody(request: VeneraHttpRequest): String? {
            val data = request.data ?: return null
            if (data is JsonNull) return null
            if (data is JsonPrimitive) {
                if (data.isString) return data.content
                return data.toString()
            }
            return data.toString()
        }
    }
}
