package app.koyura.source.runtime

import io.ktor.client.HttpClient
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.util.AttributeKey

internal data class SessionRequest(val lease: SourceSessions.Lease, val original: Url, val explicit: String?)

internal val sourceSessionRequest = AttributeKey<SessionRequest>("SourceSessionRequest")

internal fun HttpClient.withSourceSessions(sessions: SourceSessions): HttpClient = config {
    install("SourceSessions") {
        sendPipeline.intercept(HttpSendPipeline.State) {
            val request = context.attributes.getOrNull(sourceSessionRequest) ?: return@intercept
            val url = context.url.build()
            val sameOrigin = url.protocol == request.original.protocol &&
                url.host.equals(request.original.host, ignoreCase = true) && url.port == request.original.port
            context.headers.remove(HttpHeaders.Cookie)
            val cookie = request.explicit?.takeIf { sameOrigin } ?: sessions.requestHeader(request.lease, url)
            if (!cookie.isNullOrEmpty()) context.headers.append(HttpHeaders.Cookie, cookie)
        }
        receivePipeline.intercept(HttpReceivePipeline.State) { response ->
            val request = response.call.request
            val session = request.attributes.getOrNull(sourceSessionRequest) ?: return@intercept
            val cookies = response.headers.getAll(HttpHeaders.SetCookie).orEmpty().mapNotNull {
                runCatching { parseServerSetCookieHeader(it) }.getOrNull()
            }
            sessions.receive(session.lease, request.url, cookies)
        }
    }
}
