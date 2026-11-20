package app.koyura.data.updates

import app.cash.sqldelight.db.SqlDriver
import app.koyura.data.extension.ExtensionDb
import app.koyura.data.extension.ExtensionService
import app.koyura.data.extension.Library_manga
import app.koyura.data.library.LibraryStore
import app.koyura.domain.library.LibraryChapter
import app.koyura.domain.library.LibraryManga
import app.koyura.domain.library.WorkKind
import app.koyura.domain.updates.DetectedChapter
import app.koyura.domain.updates.LibraryUpdates
import app.koyura.domain.updates.NewChaptersDownloadHook
import app.koyura.domain.updates.SkippedUpdate
import app.koyura.domain.updates.SmartUpdatePreferences
import app.koyura.domain.updates.UpdateFailure
import app.koyura.domain.updates.UpdateScan
import app.koyura.domain.updates.UpdateSkipReason
import app.koyura.domain.updates.UpdateTitleMetadata
import app.koyura.domain.updates.WorkUpdate
import app.koyura.source.runtime.VeneraCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock
import kotlin.time.Instant

class LibraryUpdateScanner internal constructor(
    driver: SqlDriver,
    private val library: LibraryStore,
    private val extensions: ExtensionService,
    private val clock: Clock = Clock.System,
) : LibraryUpdates {
    private val db = ExtensionDb(driver)
    private val libraryQueries = db.extensionDbQueries
    private val queries = db.libraryUpdatesQueries
    private val scanMutex = Mutex()
    private val downloadHook = MutableStateFlow<NewChaptersDownloadHook?>(null)
    private val releaseChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    internal val releaseWindowChanges = releaseChanges.asSharedFlow()

    fun setNewChaptersDownloadHook(hook: NewChaptersDownloadHook?) {
        downloadHook.value = hook
    }

    override suspend fun checkNow(): UpdateScan = scan(onlyFailures = false)
    override suspend fun retryFailures(): UpdateScan = scan(onlyFailures = true)

    suspend fun preferences(): SmartUpdatePreferences = withContext(Dispatchers.IO) {
        library.getPref(SMART_UPDATE_PREFERENCE)?.let { value ->
            runCatching { preferencesJson.decodeFromString<SmartUpdatePreferences>(value) }.getOrNull()
        } ?: SmartUpdatePreferences()
    }

    suspend fun setPreferences(value: SmartUpdatePreferences) = withContext(Dispatchers.IO) {
        val titles = value.excludedTitleTerms.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        library.setPref(SMART_UPDATE_PREFERENCE, preferencesJson.encodeToString(value.copy(excludedTitleTerms = titles)))
        releaseChanges.tryEmit(Unit)
        Unit
    }

    suspend fun setTitleMetadata(sourceKey: String, workId: String, metadata: UpdateTitleMetadata) =
        withContext(Dispatchers.IO) {
            val kind = WorkKind.COMIC.name
            db.transaction {
                require(libraryQueries.selectLibraryByKey(sourceKey, kind, workId).executeAsOneOrNull() != null) {
                    "Smart update metadata requires a library work"
                }
                queries.setTitleMetadata(
                    sourceKey, kind, workId, metadata.completed?.asSqlFlag(), metadata.fetchOnce.asSqlFlag(),
                    metadata.nextUpdateMillis,
                )
            }
            releaseChanges.tryEmit(Unit)
            Unit
        }

    /** Earliest local day a work skipped solely for its future release becomes eligible. */
    internal suspend fun nextReleaseWindowMillis(): Long? = withContext(Dispatchers.IO) {
        val smart = preferences()
        if (!smart.enabled || !smart.skipOutsideReleasePeriod) return@withContext null
        val zone = TimeZone.currentSystemDefault()
        val endOfToday = endOfCurrentDay()
        db.transactionWithResult {
            allComics().values.asSequence()
                .filter { skipReason(it, smart, endOfToday) == UpdateSkipReason.OUTSIDE_RELEASE_PERIOD }
                .filterNot { work -> smart.excludedTitleTerms.any { work.title.contains(it, ignoreCase = true) } }
                .mapNotNull { queries.selectTitleMetadata(it.sourceKey, it.kind.name, it.workId).executeAsOneOrNull()?.next_update }
                .minOrNull()
        }?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).date.atStartOfDayIn(zone).toEpochMilliseconds() }
    }

    private suspend fun scan(onlyFailures: Boolean): UpdateScan = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            val works = if (onlyFailures) failureRows().map { it.work } else allComics().values.toList()
            val smart = if (onlyFailures) null else preferences().takeIf { it.enabled }
            val releaseWindowEnd = if (smart?.skipOutsideReleasePeriod == true) endOfCurrentDay() else Long.MAX_VALUE
            val updates = mutableListOf<WorkUpdate>()
            val failures = mutableListOf<UpdateFailure>()
            val skipped = mutableListOf<SkippedUpdate>()
            val triggerFailures = mutableListOf<LibraryManga>()
            val hook = downloadHook.value
            for (work in works) {
                currentCoroutineContext().ensureActive()
                if (smart != null) {
                    val reason = skipReason(work, smart, releaseWindowEnd)
                    if (reason != null) {
                        skipped += SkippedUpdate(work, reason)
                        continue
                    }
                }
                val (chapters, completed) = try {
                    val detail = extensions.call(
                        work.sourceKey, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive(work.workId))),
                    ).jsonObject
                    unpackChapters(work, detail["chapters"]) to completionStatus(detail)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    currentCoroutineContext().ensureActive()
                    // Source errors can carry session values or signed URLs. Store a stable diagnostic.
                    val failed = recordFailure(work, "Could not load chapter list (${failure::class.simpleName ?: "source error"})")
                    if (failed != null) failures += failed
                    continue
                }
                currentCoroutineContext().ensureActive()
                val updated = recordSuccess(work, chapters, completed)
                if (updated == null) continue
                updates += updated
                if (hook != null && updated.newChapters.isNotEmpty()) {
                    try {
                        hook.onNewChapters(updated)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        triggerFailures += updated.work
                    }
                }
            }
            UpdateScan(updates, failures, skipped, triggerFailures)
        }
    }

    private fun recordSuccess(
        work: LibraryManga,
        chapters: List<LibraryChapter>,
        completed: Boolean?,
    ): WorkUpdate? = db.transactionWithResult {
        val current = current(work) ?: return@transactionWithResult null
        val checkedAt = clock.now().toEpochMilliseconds()
        val existing = libraryQueries.selectChaptersForWork(work.sourceKey, work.kind.name, work.workId)
            .executeAsList().mapTo(hashSetOf()) { it.chapter_id }
        val discovered = chapters.filter { it.chapterId.isNotEmpty() && it.chapterId !in existing }
        library.mergeChapters(work.sourceKey, work.kind, work.workId, chapters)
        if (completed != null) {
            val flag = completed.asSqlFlag()
            queries.insertCompletionStatus(work.sourceKey, work.kind.name, work.workId, flag)
            queries.updateCompletionStatus(flag, work.sourceKey, work.kind.name, work.workId)
        }
        for (chapter in discovered) {
            queries.recordDetectedChapter(work.sourceKey, work.kind.name, work.workId, chapter.chapterId, checkedAt)
        }
        queries.writeLastChecked(checkedAt, work.sourceKey, work.kind.name, work.workId)
        queries.clearUpdateFailure(work.sourceKey, work.kind.name, work.workId)
        WorkUpdate(current.copy(lastUpdateCheckMillis = checkedAt), checkedAt, discovered.asReversed().map {
            DetectedChapter(it.chapterId, it.title, checkedAt, isRead = false)
        })
    }

    private fun recordFailure(work: LibraryManga, message: String): UpdateFailure? = db.transactionWithResult {
        val current = current(work) ?: return@transactionWithResult null
        val checkedAt = clock.now().toEpochMilliseconds()
        queries.writeLastChecked(checkedAt, work.sourceKey, work.kind.name, work.workId)
        queries.recordUpdateFailure(work.sourceKey, work.kind.name, work.workId, checkedAt, message)
        UpdateFailure(current.copy(lastUpdateCheckMillis = checkedAt), checkedAt, message)
    }

    override suspend fun feed(): List<WorkUpdate> = withContext(Dispatchers.IO) {
        db.transactionWithResult {
            val works = allComics()
            queries.selectDetectedChapters().executeAsList()
                .groupBy { Triple(it.source_key, it.kind, it.work_id) }
                .mapNotNull { (key, rows) ->
                    val work = works[key] ?: return@mapNotNull null
                    WorkUpdate(work, work.lastUpdateCheckMillis ?: rows.first().detected_at, rows.map {
                        DetectedChapter(it.chapter_id, it.title, it.detected_at, it.is_read != 0L)
                    })
                }
        }
    }

    override suspend fun failures(): List<UpdateFailure> =
        withContext(Dispatchers.IO) { db.transactionWithResult { failureRows() } }

    private fun failureRows(): List<UpdateFailure> {
        val works = allComics()
        return queries.selectUpdateFailures().executeAsList().mapNotNull {
            val work = works[Triple(it.source_key, it.kind, it.work_id)] ?: return@mapNotNull null
            UpdateFailure(work, it.checked_at, it.message)
        }
    }

    private fun current(snapshot: LibraryManga): LibraryManga? =
        libraryQueries.selectLibraryByKey(snapshot.sourceKey, snapshot.kind.name, snapshot.workId)
            .executeAsOneOrNull()?.takeIf { it.added_at == snapshot.addedAtMillis }?.toWork()

    private fun allComics() = libraryQueries.selectLibraryAll().executeAsList().filter { it.kind == WorkKind.COMIC.name }
        .associate { Triple(it.source_key, it.kind, it.work_id) to it.toWork() }

    private fun Library_manga.toWork() =
        LibraryManga(source_key, WorkKind.valueOf(kind), work_id, title, cover_url, source_name, added_at, last_update_check)

    private fun skipReason(work: LibraryManga, smart: SmartUpdatePreferences, releaseWindowEnd: Long): UpdateSkipReason? {
        val metadata = queries.selectTitleMetadata(work.sourceKey, work.kind.name, work.workId).executeAsOneOrNull()
        val counts = queries.chapterReadCounts(work.sourceKey, work.kind.name, work.workId).executeAsOne()
        return when {
            metadata?.fetch_once == 1L && counts.total_chapters > 0 -> UpdateSkipReason.FETCH_ONCE
            smart.skipCompleted && metadata?.completed == 1L -> UpdateSkipReason.COMPLETED
            smart.skipWithUnread && counts.total_chapters > counts.read_chapters -> UpdateSkipReason.HAS_UNREAD
            smart.skipNotStarted && counts.total_chapters > 0 && counts.read_chapters == 0L -> UpdateSkipReason.NOT_STARTED
            smart.skipOutsideReleasePeriod && (metadata?.next_update ?: Long.MIN_VALUE) > releaseWindowEnd ->
                UpdateSkipReason.OUTSIDE_RELEASE_PERIOD
            smart.excludedTitleTerms.any { work.title.contains(it, ignoreCase = true) } -> UpdateSkipReason.TITLE_FILTER
            else -> null
        }
    }

    private fun endOfCurrentDay(): Long {
        val zone = TimeZone.currentSystemDefault()
        return clock.now().toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(zone).toEpochMilliseconds() - 1
    }

    private fun completionStatus(detail: JsonObject): Boolean? {
        val explicit = (detail["status"] as? JsonPrimitive)?.content
        val tagged = (detail["tags"] as? JsonObject)?.entries?.asSequence()
            ?.filter { (name) -> name.trim().lowercase() in statusLabels }
            ?.flatMap { (_, value) -> (value as? JsonArray).orEmpty() }
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
        return (listOfNotNull(explicit).asSequence() + (tagged ?: emptySequence())).firstNotNullOfOrNull { status ->
            when (status.substringAfterLast('：').substringAfterLast(':').trim().lowercase()) {
                "completed", "complete", "finished", "完结", "完結", "已完结", "已完結" -> true
                "ongoing", "publishing", "连载", "連載", "连载中", "連載中" -> false
                else -> null
            }
        }
    }

    private fun Boolean.asSqlFlag(): Long = if (this) 1L else 0L

    private fun unpackChapters(work: LibraryManga, value: JsonElement?): List<LibraryChapter> {
        fun chapter(id: String, title: JsonElement): LibraryChapter {
            require(id.isNotEmpty() && title is JsonPrimitive && title.isString) { "Invalid chapter entry" }
            return LibraryChapter(work.sourceKey, work.kind, work.workId, id, title.content)
        }
        if (value == null || value == JsonNull || (value is JsonObject && value.isEmpty())) {
            return listOf(LibraryChapter(work.sourceKey, work.kind, work.workId, ""))
        }
        val entries = value.jsonObject
        val chapters = entries.entries.flatMap { (id, entry) ->
            if (entry is JsonObject) entry.map { (id, title) -> chapter(id, title) } else listOf(chapter(id, entry))
        }
        return chapters.distinctBy { it.chapterId }
    }
    private companion object {
        const val SMART_UPDATE_PREFERENCE = "updates.smart_scan"
        val preferencesJson = Json { ignoreUnknownKeys = true }
        val statusLabels = setOf("status", "状态", "狀態")
    }

}
