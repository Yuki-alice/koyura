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

class VeneraScriptException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
