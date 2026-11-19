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
import app.koyura.domain.updates.UpdateFailure
import app.koyura.domain.updates.UpdateScan
import app.koyura.domain.updates.WorkUpdate
import app.koyura.source.runtime.VeneraCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock

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

    override suspend fun checkNow(): UpdateScan = scan(onlyFailures = false)
    override suspend fun retryFailures(): UpdateScan = scan(onlyFailures = true)

    private suspend fun scan(onlyFailures: Boolean): UpdateScan = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            val works = if (onlyFailures) failureRows().map { it.work } else allComics().values.toList()
            val updates = mutableListOf<WorkUpdate>()
            val failures = mutableListOf<UpdateFailure>()
            for (work in works) {
                currentCoroutineContext().ensureActive()
                val chapters = try {
                    val detail = extensions.call(work.sourceKey, VeneraCallback.LoadInfo, JsonArray(listOf(JsonPrimitive(work.workId))))
                    unpackChapters(work, detail.jsonObject["chapters"])
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
                val updated = recordSuccess(work, chapters)
                if (updated != null) updates += updated
            }
            UpdateScan(updates, failures)
        }
    }

    private fun recordSuccess(work: LibraryManga, chapters: List<LibraryChapter>): WorkUpdate? = db.transactionWithResult {
        val current = current(work) ?: return@transactionWithResult null
        val checkedAt = clock.now().toEpochMilliseconds()
        val existing = libraryQueries.selectChaptersForWork(work.sourceKey, work.kind.name, work.workId)
            .executeAsList().mapTo(hashSetOf()) { it.chapter_id }
        val discovered = chapters.filter { it.chapterId.isNotEmpty() && it.chapterId !in existing }
        library.mergeChapters(work.sourceKey, work.kind, work.workId, chapters)
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
}
