package app.koyura.data.downloads

import app.cash.sqldelight.db.SqlDriver
import app.koyura.data.extension.Download_jobs
import app.koyura.data.extension.ExtensionDb
import app.koyura.domain.downloads.ChapterDownloads
import app.koyura.domain.downloads.DownloadChapter
import app.koyura.domain.downloads.DownloadJob
import app.koyura.domain.downloads.DownloadStatus
import app.koyura.domain.extension.ComicPage
import app.koyura.domain.extension.OpenedChapter
import app.koyura.domain.library.WorkKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

internal class ChapterDownloadStore(
    driver: SqlDriver,
    private val packages: ChapterContentPackages,
    private val openChapter: suspend (DownloadChapter) -> OpenedChapter,
    private val loadPage: suspend (DownloadChapter, ComicPage) -> ByteArray,
) : ChapterDownloads {
    private val db = ExtensionDb(driver)
    private val queries = db.downloadJobsQueries
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val state = MutableStateFlow<List<DownloadJob>>(emptyList())
    override val jobs = state.asStateFlow()
    private var active: Pair<String, Job>? = null
    private var closed = false

    suspend fun start() = withContext(Dispatchers.IO) {
        val rows = queries.selectAllDownloads().executeAsList()
        packages.discardOrphans(rows.mapTo(hashSetOf()) { it.id })
        for (row in rows) {
            packages.discardTemporary(row.id)
            val committed = packages.verifiedManifest(row.id)
            when {
                committed != null && row.status in RECOVERABLE_COMMIT ->
                    mark(row.id, DownloadStatus.COMPLETE, committed.pages.size, committed.pages.size)
                committed != null -> packages.discardCommitted(row.id)
                else -> {
                    packages.discardCommitted(row.id)
                    when (DownloadStatus.valueOf(row.status)) {
                        DownloadStatus.DOWNLOADING -> mark(row.id, DownloadStatus.QUEUED)
                        DownloadStatus.COMPLETE -> mark(row.id, DownloadStatus.FAILED)
                        else -> Unit
                    }
                }
            }
        }
        publish()
        scope.launch {
            for (signal in wake) {
                while (isActive) {
                    val task = mutex.withLock {
                        val row = queries.selectNextDownload().executeAsOneOrNull() ?: return@withLock null
                        mark(row.id, DownloadStatus.DOWNLOADING)
                        publish()
                        launch(start = CoroutineStart.LAZY) { download(row.toJob()) }.also {
                            active = row.id to it
                        }
                    } ?: break
                    task.start()
                    task.join()
                    mutex.withLock { if (active?.second === task) active = null }
                }
            }
        }
        wake.trySend(Unit)
        Unit
    }

    override fun observe(chapter: DownloadChapter) = jobs.map { values ->
        values.firstOrNull { it.chapter == chapter }
    }.distinctUntilChanged()

    override suspend fun countCompleted(sourceKey: String, workId: String): Int = withContext(Dispatchers.IO) {
        queries.countCompletedDownloads(sourceKey, workId).executeAsOne().toInt()
    }

    override suspend fun statusesForWork(sourceKey: String, workId: String): Map<String?, DownloadJob> =
        withContext(Dispatchers.IO) {
            queries.selectDownloadsForWork(sourceKey, workId).executeAsList().map { it.toJob() }
                .associateBy { it.chapter.chapterId }
        }

    override suspend fun enqueue(chapter: DownloadChapter): DownloadJob =
        enqueueAll(chapter.sourceKey, chapter.workId, listOf(chapter.chapterId)).single()

    override suspend fun enqueueAll(
        sourceKey: String,
        workId: String,
        chapterIds: List<String?>,
        unreadOnly: Boolean,
        limit: Int?,
    ): List<DownloadJob> {
        require(sourceKey.isNotBlank() && workId.isNotBlank())
        require(limit == null || limit >= 0)
        if (chapterIds.isEmpty() || limit == 0) return emptyList()
        return mutate {
            db.transactionWithResult {
                val read = if (unreadOnly) {
                    db.extensionDbQueries.selectChaptersForWork(sourceKey, WorkKind.COMIC.name, workId)
                        .executeAsList().filter { it.is_read != 0L }.mapTo(hashSetOf()) { it.chapter_id }
                } else emptySet()
                val selected = chapterIds.asSequence().map { it.orEmpty() }.distinct()
                    .filterNot { it in read }.take(limit ?: chapterIds.size)
                selected.map { id ->
                    queries.insertDownload(Uuid.random().toString(), sourceKey, workId, id)
                    queries.selectDownloadForChapter(sourceKey, workId, id).executeAsOne().toJob()
                }.toList()
            }
        }
    }

    override suspend fun pause(id: String) = stop(id, DownloadStatus.PAUSED, setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING))
    override suspend fun cancel(id: String) = stop(id, DownloadStatus.CANCELLED, setOf(
        DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED, DownloadStatus.FAILED,
    ))

    override suspend fun retry(id: String) = mutate {
        val row = queries.selectDownload(id).executeAsOneOrNull() ?: return@mutate
        if (DownloadStatus.valueOf(row.status) in setOf(DownloadStatus.FAILED, DownloadStatus.CANCELLED, DownloadStatus.PAUSED)) {
            mark(id, DownloadStatus.QUEUED)
        }
    }

    private suspend fun stop(id: String, status: DownloadStatus, allowed: Set<DownloadStatus>) {
        val task = mutate {
            val row = queries.selectDownload(id).executeAsOneOrNull()
            if (row == null || DownloadStatus.valueOf(row.status) !in allowed) return@mutate null
            mark(id, status)
            active?.takeIf { it.first == id }?.second.also { it?.cancel() }
        }
        task?.join()
    }

    suspend fun close() {
        mutex.withLock {
            closed = true
            wake.close()
        }
        scope.coroutineContext[Job]?.cancelAndJoin()
    }

    private suspend fun download(job: DownloadJob) {
        try {
            packages.verifiedManifest(job.id)?.let { committed ->
                mutex.withLock {
                    currentCoroutineContext().ensureActive()
                    mark(job.id, DownloadStatus.COMPLETE, committed.pages.size, committed.pages.size)
                    publish()
                }
                return
            }
            packages.discardCommitted(job.id)
            packages.prepare(job.id)
            val pages = openChapter(job.chapter).pages
            require(pages.isNotEmpty()) { "Chapter is empty" }
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                mark(job.id, DownloadStatus.DOWNLOADING, 0, pages.size)
                publish()
            }
            val content = ArrayList<ContentPage>(pages.size)
            for (page in pages) {
                currentCoroutineContext().ensureActive()
                content += packages.writePage(job.id, page, loadPage(job.chapter, page))
                mutex.withLock {
                    currentCoroutineContext().ensureActive()
                    mark(job.id, DownloadStatus.DOWNLOADING, content.size, pages.size)
                    publish()
                }
            }
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                packages.commit(job.id, content)
                mark(job.id, DownloadStatus.COMPLETE, content.size, content.size)
                publish()
            }
        } catch (cancelled: CancellationException) {
            val status = if (currentCoroutineContext().isActive) DownloadStatus.FAILED else DownloadStatus.QUEUED
            withContext(NonCancellable) {
                mutex.withLock {
                    if (queries.selectDownload(job.id).executeAsOne().status == DownloadStatus.DOWNLOADING.name) {
                        mark(job.id, status)
                        publish()
                    }
                }
            }
            throw cancelled
        } catch (_: Exception) {
            mutex.withLock {
                if (queries.selectDownload(job.id).executeAsOne().status == DownloadStatus.DOWNLOADING.name) {
                    mark(job.id, DownloadStatus.FAILED)
                    publish()
                }
            }
        } finally {
            runCatching { packages.discardTemporary(job.id) }
        }
    }

    private suspend fun <T> mutate(operation: () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed) { "Chapter download queue is closed" }
            operation().also {
                publish()
                wake.trySend(Unit)
            }
        }
    }

    private fun publish() {
        state.value = queries.selectAllDownloads().executeAsList().map { it.toJob() }
    }

    private fun mark(id: String, status: DownloadStatus, count: Int = 0, total: Int? = null) {
        queries.updateDownload(status.name, count.toLong(), total?.toLong(), id)
    }

    private fun Download_jobs.toJob() = DownloadJob(
        id, DownloadChapter(source_key, work_id, chapter_id.ifEmpty { null }),
        DownloadStatus.valueOf(status), completed_pages.toInt(), total_pages?.toInt(),
    )

    private companion object {
        val RECOVERABLE_COMMIT = setOf(DownloadStatus.QUEUED.name, DownloadStatus.DOWNLOADING.name, DownloadStatus.COMPLETE.name, DownloadStatus.FAILED.name)
    }
}
