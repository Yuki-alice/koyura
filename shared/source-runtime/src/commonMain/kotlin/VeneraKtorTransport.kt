package app.koyura.source.runtime

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Text-only [VeneraHttpTransport] backed by Ktor.
 *
 * Mapping (this round):
 * - Forwards HTTP method, full URL (including query string), request headers and
 *   text body. A `data` that is already a JSON string (scripts call
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
 * Options implemented this round:
 * - Implemented: method/URL/headers/text-body forwarding, default browser UA,
 *   4xx/5xx passthrough (request-level [ResponseException] mapping),
 *   transport-error throw, cancellation propagation, `,` header join,
 *   binary bodies via [executeBytes] (same mapping, bytes preserved).
 * - Not implemented (stripped/ignored): short-term `cache-time` caching,
 *   `prevent-parallel` serialization, `http_client == "dart:io"` alternate
 *   client, `extra` interceptor options, cookies/session.
 *
 * Ownership: [VeneraSourceRuntime] never closes the transport. If [ownsClient]
 * is true, call [close] to close the underlying [HttpClient]; otherwise the
 * caller that supplied [client] owns it (tests with MockEngine own their
 * client, production code using `createDefaultHttpClient` should own it).
 */
class KtorVeneraHttpTransport(
    private val client: HttpClient,
    private val ownsClient: Boolean = false,
) : VeneraHttpTransport {
    override suspend fun execute(request: VeneraHttpRequest): VeneraHttpResponse {
        try {
            val response = client.request { applyVeneraRequest(request) }
            val headers = response.headers.entries()
                .associate { (name, values) -> name to values.joinToString(",") }
            return VeneraHttpResponse(
                status = response.status.value,
                body = response.bodyAsText(),
                headers = headers,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ResponseException) {
            // Request-level guarantee: an injected client with expectSuccess = true
            // must still surface 4xx/5xx to the script instead of throwing.
            val failed = failure.response
            val headers = failed.headers.entries()
                .associate { (name, values) -> name to values.joinToString(",") }
            return VeneraHttpResponse(
                status = failed.status.value,
                body = failed.bodyAsText(),
                headers = headers,
            )
        }
    }

    /**
     * Same mapping as [execute], but preserves raw response bytes for image
     * payloads. Used by the comic reader for `onImageLoad` configs and direct
     * image URLs; script text APIs keep using [execute].
     */
    suspend fun executeBytes(request: VeneraHttpRequest): VeneraBytesResponse {
        val response = try {
            client.request { applyVeneraRequest(request) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ResponseException) {
            failure.response
        }
        val headers = response.headers.entries()
            .associate { (name, values) -> name to values.joinToString(",") }
        return VeneraBytesResponse(
            status = response.status.value,
            body = response.readRawBytes(),
            headers = headers,
        )
    }

    private fun HttpRequestBuilder.applyVeneraRequest(request: VeneraHttpRequest) {
        val text = textBody(request)
        val contentTypeHeader = request.headers.entries
            .firstOrNull { it.key.equals(HttpHeaders.ContentType, ignoreCase = true) }
            ?.value
        val hasUserAgent = request.headers.keys.any { it.equals(HttpHeaders.UserAgent, ignoreCase = true) }
        method = HttpMethod.parse(request.method.uppercase())
        url(request.url)
        headers {
            for ((name, value) in request.headers) {
                if (isHostControlHeader(name)) continue
                // Content-Type with a text body travels on the body itself;
                // without a body it is a plain header.
                if (text != null && name.equals(HttpHeaders.ContentType, ignoreCase = true)) continue
                append(name, value)
            }
            if (!hasUserAgent) {
                append(HttpHeaders.UserAgent, DEFAULT_BROWSER_USER_AGENT)
            }
        }
        if (text != null) {
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
