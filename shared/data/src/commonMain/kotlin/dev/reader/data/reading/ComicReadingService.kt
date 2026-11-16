package dev.reader.data.reading

import app.cash.sqldelight.db.SqlDriver
import dev.reader.data.extension.ExtensionDb
import dev.reader.data.extension.ExtensionService
import dev.reader.domain.extension.ComicPage
import dev.reader.domain.extension.OpenedChapter
import dev.reader.domain.extension.ReadingFailure
import dev.reader.domain.extension.ReadingProgress
import dev.reader.source.runtime.KtorVeneraHttpTransport
import dev.reader.source.runtime.VeneraCallback
import dev.reader.source.runtime.VeneraHttpRequest
import dev.reader.source.runtime.normalizeVeneraUrl
import dev.reader.source.runtime.VeneraSourceRuntime
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

private fun chapterKey(chapterId: String?): String = chapterId ?: ""

/**
 * Comic reading over an installed extension (milestone reading).
 *
 * Depends on an opened [ExtensionService] for QuickJS callbacks and shares the
 * given [HttpClient] for image downloads (neither is owned or closed here;
 * the owning [dev.reader.data.session.ExtensionSession] releases them).
 *
 * - [openChapter] resolves `comic.loadEp`, builds stable page keys from the
 *   raw image identifiers (later duplicates get a `#n` suffix) and resumes at
 *   the saved page key, falling back to the clamped saved index.
 * - [loadPageImage] resolves a page key against a fresh listing (survives
 *   reorder/removal with [ReadingFailure.PageNotFound]) and downloads it.
 * - Resolved (possibly signed) image URLs are never persisted; only raw keys
 *   and zero-based indexes are stored.
 */
class ComicReadingService(
    driver: SqlDriver,
    private val extensions: ExtensionService,
    private val httpClient: HttpClient,
) {
    private val db = ExtensionDb(driver)
    private val progressMutex = Mutex()

    suspend fun openChapter(sourceKey: String, workId: String, chapterId: String?): OpenedChapter {
        val live = extensions.runtime ?: throw ReadingFailure.SourceMissing(sourceKey)
        if (live.registeredSources().none { it.key == sourceKey }) throw ReadingFailure.SourceMissing(sourceKey)
        val args = JsonArray(
            listOf(
                JsonPrimitive(workId),
                chapterId?.let(::JsonPrimitive) ?: JsonNull,
            ),
        )
        val detail = try {
            live.call(sourceKey, VeneraCallback.LoadEpisode, args).jsonObject
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ReadingFailure.ChapterInvalid(e)
        }
        val images = try {
            detail.getValue("images").jsonArray.map {
                (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
                    ?: throw ReadingFailure.ChapterInvalid(null)
            }
        } catch (e: ReadingFailure) {
            throw e
        } catch (e: Exception) {
            throw ReadingFailure.ChapterInvalid(e)
        }
        val seen = mutableMapOf<String, Int>()
        val pages = images.mapIndexed { index, imageKey ->
            val occurrence = (seen[imageKey] ?: 0) + 1
            seen[imageKey] = occurrence
            val pageKey = if (occurrence == 1) imageKey else "$imageKey#$occurrence"
            ComicPage(pageKey = pageKey, index = index, imageKey = imageKey)
        }
        val saved = readProgress(sourceKey, workId, chapterId)
        val startIndex = when {
            pages.isEmpty() -> 0
            saved == null -> 0
            else -> pages.indexOfFirst { it.pageKey == saved.pageKey }
                .takeIf { it >= 0 }
                ?: saved.pageIndex.coerceIn(0, pages.size - 1)
        }
        return OpenedChapter(
            sourceKey = sourceKey,
            workId = workId,
            chapterId = chapterId,
            pages = pages,
            startIndex = startIndex,
            startPageKey = pages.getOrNull(startIndex)?.pageKey,
        )
    }

    suspend fun loadPageImage(sourceKey: String, workId: String, chapterId: String?, pageKey: String): ByteArray {
        val opened = openChapter(sourceKey, workId, chapterId)
        val page = opened.pages.firstOrNull { it.pageKey == pageKey }
            ?: throw ReadingFailure.PageNotFound(pageKey)
        return loadImageBytes(sourceKey, workId, chapterId, page.imageKey)
    }

    suspend fun loadImageBytes(sourceKey: String, workId: String, chapterId: String?, imageKey: String): ByteArray {
        val live = extensions.runtime ?: throw ReadingFailure.SourceMissing(sourceKey)
        val source = live.registeredSources().firstOrNull { it.key == sourceKey }
            ?: throw ReadingFailure.SourceMissing(sourceKey)
        val config = if (VeneraCallback.LoadImage in source.callbacks) {
            val args = JsonArray(
                listOf(
                    JsonPrimitive(imageKey),
                    JsonPrimitive(workId),
                    chapterId?.let(::JsonPrimitive) ?: JsonNull,
                ),
            )
            try {
                live.call(sourceKey, VeneraCallback.LoadImage, args)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw ReadingFailure.ConfigInvalid(e)
            }
        } else {
            null
        }
        val (url, method, headers, body) = try {
            val parsed = parseImageConfig(config, imageKey)
            // Image URLs must be absolute (upstream passes them straight to
            // Dio, where a relative URL fails). Fail fast with a clear error
            // instead of requesting a wrong host.
            parsed.copy(url = normalizeVeneraUrl(parsed.url))
        } catch (e: ReadingFailure) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ReadingFailure.ConfigInvalid(e)
        }
        val transport = KtorVeneraHttpTransport(httpClient)
        val response = try {
            transport.executeBytes(
                VeneraHttpRequest(
                    sourceKey = sourceKey,
                    method = method,
                    url = url,
                    headers = headers,
                    data = body,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ReadingFailure.TransportFailed(e)
        }
        if (response.status !in 200..299) throw ReadingFailure.HttpError(response.status)
        return response.body
    }

    suspend fun saveProgress(
        sourceKey: String,
        workId: String,
        chapterId: String?,
        pageKey: String,
        pageIndex: Int,
    ) {
        progressMutex.withLock {
            db.extensionDbQueries.upsertProgress(
                source_key = sourceKey,
                work_id = workId,
                chapter_id = chapterKey(chapterId),
                page_key = pageKey,
                page_index = pageIndex.toLong(),
            )
        }
    }

    suspend fun readProgress(sourceKey: String, workId: String, chapterId: String?): ReadingProgress? {
        val row = db.extensionDbQueries
            .selectProgress(sourceKey, workId, chapterKey(chapterId))
            .executeAsOneOrNull() ?: return null
        return ReadingProgress(
            sourceKey = row.source_key,
            workId = row.work_id,
            chapterId = row.chapter_id.ifEmpty { null },
            pageKey = row.page_key,
            pageIndex = row.page_index.toInt(),
        )
    }

    /**
     * Most recently read chapter of a work, or null when nothing was saved.
     * A chapterless work is reported as "" (documented marker).
     */
    suspend fun recentChapter(sourceKey: String, workId: String): String? {
        val row = db.extensionDbQueries
            .selectChaptersForWork(sourceKey, workId)
            .executeAsList()
            .firstOrNull() ?: return null
        return row.chapter_id
    }

    private fun parseImageConfig(
        config: JsonElement?,
        imageKey: String,
    ): ImageRequest {
        if (config == null || config is JsonNull) {
            return ImageRequest(url = imageKey, method = "GET", headers = emptyMap(), body = null)
        }
        val obj = config as? JsonObject ?: throw ReadingFailure.ConfigInvalid(null)
        for (field in listOf("onResponse", "modifyImage", "onLoadFailed")) {
            val value = obj[field]
            if (value != null && value !is JsonNull) throw ReadingFailure.TransformUnsupported(field)
        }
        fun stringField(name: String): String? =
            (obj[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val url = stringField("url") ?: imageKey
        val method = stringField("method")?.uppercase() ?: "GET"
        val headers = (obj["headers"] as? JsonObject)
            ?.mapValues { (_, v) ->
                (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: v.toString()
            } ?: emptyMap()
        val data = obj["data"]?.takeIf { it !is JsonNull }
        return ImageRequest(url = url, method = method, headers = headers, body = data)
    }

private data class ImageRequest(
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        val body: JsonElement?,
    )
}
