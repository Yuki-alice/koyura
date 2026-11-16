package dev.reader.source.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class VeneraEnvironment(
    val compatibleAppVersion: String,
    val locale: String,
    val platform: String,
)

data class VeneraSource(
    val name: String,
    val key: String,
    val version: String,
    val minAppVersion: String?,
    val url: String?,
    val callbacks: Set<VeneraCallback>,
)

enum class VeneraCallback(val path: String, internal val synchronous: Boolean = false) {
    Search("search.load"),
    SearchNext("search.loadNext"),
    LoadInfo("comic.loadInfo"),
    LoadEpisode("comic.loadEp"),
    LoadThumbnails("comic.loadThumbnails"),
    LoadImage("comic.onImageLoad"),
    LoadThumbnail("comic.onThumbnailLoad"),
    ClickTag("comic.onClickTag", synchronous = true),
}

@Serializable
data class VeneraHttpRequest(
    val sourceKey: String?,
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val data: JsonElement? = null,
    val extra: JsonObject? = null,
)

@Serializable
data class VeneraHttpResponse(
    val status: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)

fun interface VeneraHttpTransport {
    suspend fun execute(request: VeneraHttpRequest): VeneraHttpResponse
}

/**
 * Binary counterpart of [VeneraHttpResponse]: status, raw bytes, headers.
 * Used for image payloads where text decoding would corrupt the bytes.
 */
data class VeneraBytesResponse(
    val status: Int,
    val body: ByteArray,
    val headers: Map<String, String> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VeneraBytesResponse) return false
        return status == other.status && body.contentEquals(other.body) && headers == other.headers
    }

    override fun hashCode(): Int {
        var result = status
        result = 31 * result + body.contentHashCode()
        result = 31 * result + headers.hashCode()
        return result
    }
}

class VeneraScriptException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
