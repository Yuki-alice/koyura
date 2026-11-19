package app.koyura.data.reading

import app.cash.sqldelight.db.SqlDriver
import app.koyura.data.downloads.ChapterContentPackages
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.extension.ExtensionService
import app.koyura.data.extension.Reading_progress
import app.koyura.domain.extension.ComicPage
import app.koyura.domain.extension.OpenedChapter
import app.koyura.domain.extension.ReadingFailure
import app.koyura.domain.extension.ReadingProgress
import app.koyura.source.runtime.ParsedImageConfig
import app.koyura.source.runtime.VeneraCallback
import app.koyura.source.runtime.VeneraHttpRequest
import app.koyura.source.runtime.modifyImageWithScript
import app.koyura.source.runtime.normalizeVeneraUrl
import app.koyura.source.runtime.VeneraSourceRuntime
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
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
 * the owning [app.koyura.data.session.ExtensionSession] releases them).
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
    private var localContent: ChapterContentPackages? = null
    private var isOnline: () -> Boolean = { true }

    internal fun attachLocalContent(packages: ChapterContentPackages, isOnline: () -> Boolean) {
        localContent = packages
        this.isOnline = isOnline
    }

    suspend fun openChapter(sourceKey: String, workId: String, chapterId: String?): OpenedChapter {
        val local = localPackage(sourceKey, workId, chapterId)
        if (local != null) return opened(sourceKey, workId, chapterId, local.pages)
        if (!isOnline()) throw ReadingFailure.OfflineUnavailable()
        return openOnlineChapter(sourceKey, workId, chapterId)
    }

    internal suspend fun openOnlineChapter(sourceKey: String, workId: String, chapterId: String?): OpenedChapter {
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
        val rawKeys = images.toSet()
        val occurrences = mutableMapOf<String, Int>()
        val pages = images.mapIndexed { index, imageKey ->
            var occurrence = (occurrences[imageKey] ?: 0) + 1
            if (occurrence > 1) {
                while ("$imageKey#$occurrence" in rawKeys) occurrence++
            }
            occurrences[imageKey] = occurrence
            val pageKey = if (occurrence == 1) imageKey else "$imageKey#$occurrence"
            ComicPage(pageKey = pageKey, index = index, imageKey = imageKey)
        }
        return opened(sourceKey, workId, chapterId, pages)
    }

    private suspend fun opened(
        sourceKey: String, workId: String, chapterId: String?, pages: List<ComicPage>,
    ): OpenedChapter {
        val saved = readProgress(sourceKey, workId, chapterId)
        val startIndex = when {
            pages.isEmpty() -> 0
            saved == null -> 0
            else -> pages.indexOfFirst {
                it.pageKey == saved.pageKey ||
                    ChapterContentPackages.locator(it.pageKey) == saved.pageKey ||
                    it.pageKey == ChapterContentPackages.locator(saved.pageKey)
            }
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

    /**
     * Downloads one image, honoring the source's `onImageLoad` config when the
     * source provides it (URL, method, headers, text body, `onResponse`
     * transforms) and retrying through `onLoadFailed` replacements up to five
     * additional times. Without the callback the raw key is fetched with GET.
     *
     * The runtime lock is held only while invoking script callbacks; the
     * network waits in between leave other source callbacks unaffected.
     */
    suspend fun loadImageBytes(sourceKey: String, workId: String, chapterId: String?, imageKey: String): ByteArray {
        val local = localPackage(sourceKey, workId, chapterId)
        val bytes = if (local != null) withContext(Dispatchers.IO) {
            localContent?.readPage(local.jobId, imageKey)
        } else null
        if (bytes != null) return bytes
        if (!isOnline()) throw ReadingFailure.OfflineUnavailable()
        val remoteKey = if (imageKey.startsWith(ChapterContentPackages.LOCATOR_PREFIX)) {
            val remote = openOnlineChapter(sourceKey, workId, chapterId)
            val index = local?.pages?.indexOfFirst { it.pageKey == imageKey } ?: -1
            val page = remote.pages.firstOrNull { ChapterContentPackages.locator(it.pageKey) == imageKey }
                ?: remote.pages.getOrNull(index)
                ?: throw ReadingFailure.PageNotFound(imageKey)
            page.imageKey
        } else imageKey
        return loadOnlineImageBytes(sourceKey, workId, chapterId, remoteKey)
    }

    private data class LocalChapter(val jobId: String, val pages: List<ComicPage>)

    private suspend fun localPackage(sourceKey: String, workId: String, chapterId: String?): LocalChapter? {
        val packages = localContent ?: return null
        return withContext(Dispatchers.IO) {
            val row = db.downloadJobsQueries.selectDownloadForChapter(sourceKey, workId, chapterKey(chapterId)).executeAsOneOrNull()
                ?: return@withContext null
            if (row.status != "COMPLETE") return@withContext null
            packages.manifest(row.id)?.let { stored -> LocalChapter(row.id, stored.comicPages()) }
        }
    }

    internal suspend fun loadOnlineImageBytes(sourceKey: String, workId: String, chapterId: String?, imageKey: String): ByteArray {
        val live = liveRuntime(sourceKey)
        val source = live.registeredSources().firstOrNull { it.key == sourceKey }
            ?: throw ReadingFailure.SourceMissing(sourceKey)
        if (VeneraCallback.LoadImage !in source.callbacks) {
            return downloadDirect(sourceKey, imageKey)
        }
        val args = JsonArray(
            listOf(
                JsonPrimitive(imageKey),
                JsonPrimitive(workId),
                chapterId?.let(::JsonPrimitive) ?: JsonNull,
            ),
        )
        return loadWithConfig(
            live = live,
            sourceKey = sourceKey,
            imageKey = imageKey,
            callback = VeneraCallback.LoadImage,
            arguments = args,
            capturable = listOf("onResponse", "onLoadFailed"),
            supportsRetry = true,
        )
    }

    /**
     * Downloads one thumbnail through `comic.onThumbnailLoad`. Thumbnails use
     * the same URL/method/headers/body mapping plus `onResponse` transforms,
     * but (like upstream) have no `onLoadFailed` retry: a failed load throws.
     */
    suspend fun loadThumbnailBytes(sourceKey: String, imageKey: String): ByteArray {
        val live = liveRuntime(sourceKey)
        val source = live.registeredSources().firstOrNull { it.key == sourceKey }
            ?: throw ReadingFailure.SourceMissing(sourceKey)
        if (VeneraCallback.LoadThumbnail !in source.callbacks) {
            return downloadDirect(sourceKey, imageKey)
        }
        return loadWithConfig(
            live = live,
            sourceKey = sourceKey,
            imageKey = imageKey,
            callback = VeneraCallback.LoadThumbnail,
            // onLoadFailed is tracked for ownership like upstream, but the
            // thumbnail path performs a single attempt and never retries.
            arguments = JsonArray(listOf(JsonPrimitive(imageKey))),
            capturable = listOf("onResponse", "onLoadFailed"),
            supportsRetry = false,
        )
    }

    private suspend fun liveRuntime(sourceKey: String): VeneraSourceRuntime {
        val live = extensions.runtime ?: throw ReadingFailure.SourceMissing(sourceKey)
        if (live.isClosed) throw ReadingFailure.RuntimeClosed()
        return live
    }

    private suspend fun downloadDirect(sourceKey: String, imageKey: String): ByteArray {
        val url = try {
            normalizeVeneraUrl(imageKey)
        } catch (e: Exception) {
            throw ReadingFailure.ConfigInvalid(e)
        }
        return downloadImageBytes(
            VeneraHttpRequest(sourceKey = sourceKey, method = "GET", url = url, headers = emptyMap()),
        )
    }

    private suspend fun loadWithConfig(
        live: VeneraSourceRuntime,
        sourceKey: String,
        imageKey: String,
        callback: VeneraCallback,
        arguments: JsonArray,
        capturable: List<String>,
        supportsRetry: Boolean,
    ): ByteArray {
        var config = parseImageConfig(live, sourceKey, callback, arguments, capturable)
        val tracked = config.liveHandles.toMutableSet()
        var retriesRemaining = MAX_IMAGE_RETRIES
        suspend fun replaceOrRethrow(failure: ReadingFailure): ParsedImageConfig {
            val handle = if (supportsRetry) config.onLoadFailed else null
            if (handle == null || retriesRemaining <= 0) throw failure
            retriesRemaining--
            val next = try {
                live.invokeImageOnLoadFailed(handle, capturable)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ReadingFailure.RuntimeClosed) {
                throw e
            } catch (e: Exception) {
                throw mapEngineError(e)
            } ?: throw failure
            tracked.addAll(next.liveHandles)
            return next
        }
        try {
            while (true) {
                val request = imageHttpRequest(sourceKey, imageKey, config)
                val bytes = try {
                    downloadImageBytes(request)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ReadingFailure.RuntimeClosed) {
                    throw e
                } catch (e: ReadingFailure.HttpError) {
                    config = replaceOrRethrow(e)
                    continue
                } catch (e: ReadingFailure.TransportFailed) {
                    config = replaceOrRethrow(e)
                    continue
                } catch (e: Exception) {
                    throw mapEngineError(e)
                }
                try {
                    val transformed = applyOnResponse(live, config.onResponse, bytes)
                    val script = config.modifyImage
                    return if (supportsRetry && script != null) modifyImageWithScript(transformed, script) else transformed
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ReadingFailure.RuntimeClosed) {
                    throw e
                } catch (e: ReadingFailure.InvalidTransform) {
                    config = replaceOrRethrow(e)
                    continue
                } catch (e: Exception) {
                    config = replaceOrRethrow(ReadingFailure.InvalidTransform(e))
                    continue
                }
            }
        } finally {
            live.releaseImageCallbacks(tracked)
        }
    }

    private fun imageHttpRequest(sourceKey: String, imageKey: String, config: ParsedImageConfig): VeneraHttpRequest {
        // Upstream falls back to the original image key when the config omits
        // its URL (lib/network/images.dart: `configs['url'] ?? imageKey`).
        val url = try {
            normalizeVeneraUrl(config.url ?: imageKey)
        } catch (e: ReadingFailure) {
            throw e
        } catch (e: Exception) {
            throw ReadingFailure.ConfigInvalid(e)
        }
        return VeneraHttpRequest(
            sourceKey = sourceKey,
            method = config.method?.uppercase() ?: "GET",
            url = url,
            headers = config.headers,
            data = config.data,
        )
    }

    private suspend fun downloadImageBytes(request: VeneraHttpRequest): ByteArray {
        val response = try {
            extensions.sourceTransport.executeImage(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw mapEngineError(e, ReadingFailure::TransportFailed)
        }
        if (response.status !in 200..299) throw ReadingFailure.HttpError(response.status)
        return response.body
    }

    private suspend fun applyOnResponse(
        live: VeneraSourceRuntime,
        handle: Int?,
        bytes: ByteArray,
    ): ByteArray {
        if (handle == null) return bytes
        try {
            return live.invokeImageOnResponse(handle, bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReadingFailure.RuntimeClosed) {
            throw e
        } catch (e: Exception) {
            throw mapEngineError(e, ReadingFailure::InvalidTransform)
        }
    }

    private suspend fun parseImageConfig(
        live: VeneraSourceRuntime,
        sourceKey: String,
        callback: VeneraCallback,
        arguments: JsonArray,
        capturable: List<String>,
    ): ParsedImageConfig {
        return try {
            live.parseImageConfig(sourceKey, callback, arguments, capturable)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ReadingFailure) {
            throw e
        } catch (e: Exception) {
            throw mapEngineError(e, ReadingFailure::ConfigInvalid)
        }
    }

    /** Normalizes engine/bridge failures: cancellations and closes pass through, the rest map. */
    private fun mapEngineError(
        error: Throwable,
        wrap: (Throwable) -> ReadingFailure = { ReadingFailure.ConfigInvalid(it) },
    ): ReadingFailure {
        val message = error.message.orEmpty()
        if (message.contains("closed", ignoreCase = true)) return ReadingFailure.RuntimeClosed()
        return wrap(error)
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

    suspend fun readProgress(sourceKey: String, workId: String, chapterId: String?): ReadingProgress? =
        db.extensionDbQueries.selectProgress(sourceKey, workId, chapterKey(chapterId))
            .executeAsOneOrNull()?.toProgress()

    suspend fun readLatestProgress(sourceKey: String, workId: String): ReadingProgress? =
        db.extensionDbQueries.selectLatestProgress(sourceKey, workId).executeAsOneOrNull()?.toProgress()

    /** A chapterless work is reported as ""; a work without progress returns null. */
    suspend fun recentChapter(sourceKey: String, workId: String): String? =
        db.extensionDbQueries.selectLatestProgress(sourceKey, workId).executeAsOneOrNull()?.chapter_id

    private fun Reading_progress.toProgress() = ReadingProgress(
        sourceKey = source_key,
        workId = work_id,
        chapterId = chapter_id.ifEmpty { null },
        pageKey = page_key,
        pageIndex = page_index.toInt(),
    )

    companion object {
        /**
         * Follow-up requests after the initial image fetch: a failing fetch may
         * be repeated with a replacement config from `onLoadFailed` at most
         * this many times, mirroring upstream's retry budget.
         */
        const val MAX_IMAGE_RETRIES = 5
    }
}
