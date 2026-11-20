package app.koyura.data.downloads

import app.koyura.data.extension.ExtensionDb
import app.koyura.data.extension.createFileBackedTestDriver
import app.koyura.data.session.ExtensionSession
import app.koyura.data.session.openExtensionSession
import app.koyura.domain.downloads.DownloadChapter
import app.koyura.domain.downloads.DownloadJob
import app.koyura.domain.downloads.DownloadStatus
import app.koyura.domain.downloads.DownloadTriggerPreferences
import app.koyura.domain.library.LibraryChapter
import app.koyura.domain.library.WorkKind
import app.koyura.domain.extension.ComicPage
import app.koyura.domain.extension.ReadingFailure
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ChapterDownloadStoreTest {
    private val files = FileSystem.SYSTEM
    private val chapter = DownloadChapter("download", "work", "chapter")
    private val one = "https://comics.example/1.jpg?token=temporary"
    private val two = "https://comics.example/2.jpg"

    @Test
    fun enqueuesAnOrderedUnreadBatchAndPersistsDownloadTriggers() = runTest {
        val root = filesRoot()
        val session = open(root, MockEngine { awaitCancellation() })
        val ids = try {
            val library = session.library
            library.addToLibrary("download", WorkKind.COMIC, "work", "Demo", addedAtMillis = 1)
            library.syncChapters("download", WorkKind.COMIC, "work", listOf("read", "a", "b", "c").map {
                LibraryChapter("download", WorkKind.COMIC, "work", it)
            })
            library.markChapterRead("download", WorkKind.COMIC, "work", "read", true)

            assertTrue(session.downloadTriggers.enqueueNext("download", "work", listOf("a")).isEmpty())
            session.downloadTriggers.setPreferences(DownloadTriggerPreferences(downloadNewChapters = true, downloadAheadCount = 2))
            val batch = session.downloadTriggers.enqueueNext("download", "work", listOf("read", "a", "a", "b", "c"))
            assertEquals(listOf("a", "b"), batch.map { it.chapter.chapterId })
            val repeated = session.downloads.enqueueAll("download", "work", listOf("read", "a", "b", "a"), unreadOnly = true)
            assertEquals(batch.map { it.id }, repeated.map { it.id })

            val separate = session.downloads.enqueueAll("other", "work", listOf(null, "")).single()
            assertEquals(setOf(null), session.downloads.statusesForWork("other", "work").keys)
            assertEquals(separate.id, session.downloads.statusesForWork("other", "work")[null]?.id)
            assertEquals(setOf("a", "b"), session.downloads.statusesForWork("download", "work").keys)
            assertTrue(session.downloads.statusesForWork("download", "missing").isEmpty())
            batch.map { it.id }
        } finally {
            session.close()
        }
        val reopened = open(root, MockEngine { awaitCancellation() })
        try {
            assertEquals(DownloadTriggerPreferences(true, 2), reopened.downloadTriggers.preferences())
            assertEquals(ids, reopened.downloads.statusesForWork("download", "work").values.map { it.id })
        } finally {
            reopened.close()
            files.deleteRecursively(root)
        }
    }

    @Test
    fun pausesCancelsAndRetriesSameRowThenReadsWithoutNetworkAfterRestart() = runTest {
        val root = filesRoot()
        val entered = CompletableDeferred<Unit>()
        val first = open(root, MockEngine {
            entered.complete(Unit)
            awaitCancellation()
        })
        val id = try {
            first.service.installFromContent("download.js", script)
            val id = first.downloads.enqueue(chapter).id
            withContext(Dispatchers.Default) { withTimeout(10_000) { entered.await() } }
            first.downloads.pause(id)
            assertEquals(DownloadStatus.PAUSED, await(first, DownloadStatus.PAUSED).status)
            assertEquals(0, first.downloads.countCompleted("download", "work"))
            first.downloads.cancel(id)
            assertEquals(id, await(first, DownloadStatus.CANCELLED).id)
            assertFalse(files.exists(root / "app/downloads/$id.partial"))
            id
        } finally {
            first.close()
        }
        val calls = AtomicInteger()
        val second = open(root, MockEngine { request ->
            if (calls.incrementAndGet() == 1) respond("", HttpStatusCode.InternalServerError)
            else respond(if (request.url.encodedPath == "/1.jpg") byteArrayOf(1, 2, 3) else byteArrayOf(4, 5))
        })
        try {
            assertEquals(id, await(second, DownloadStatus.CANCELLED).id)
            second.downloads.retry(id)
            assertEquals(id, await(second, DownloadStatus.FAILED).id)
            second.downloads.retry(id)
            val completed = await(second, DownloadStatus.COMPLETE)
            assertEquals(id, completed.id)
            assertEquals(2, completed.downloadedPages)
            assertEquals(2, completed.totalPages)
            assertEquals(1, second.downloads.countCompleted("download", "work"))
            assertEquals(DownloadStatus.COMPLETE, second.downloads.statusesForWork("download", "work")["chapter"]?.status)
            assertEquals(0, second.downloads.countCompleted("different", "work"))
            assertEquals(0, second.downloads.countCompleted("download", "different"))
            assertEquals(3, calls.get())
            assertEquals(id, second.downloads.enqueue(chapter).id)
            assertTrue(files.exists(root / "app/downloads/$id/chapter.json"))
            assertFalse(files.read(root / "app/downloads/$id/chapter.json") { readUtf8() }.contains("token"))
            second.comicReading.saveProgress("download", "work", "chapter", two, 1)
        } finally {
            second.close()
        }
        val third = open(root, MockEngine { error("An offline read hit the network") }, online = false)
        try {
            assertEquals(id, await(third, DownloadStatus.COMPLETE).id)
            val opened = third.comicReading.openChapter("download", "work", "chapter")
            assertEquals(1, opened.startIndex)
            assertContentEquals(byteArrayOf(1, 2, 3),
                third.comicReading.loadImageBytes("download", "work", "chapter", opened.pages[0].imageKey))
            files.delete(root / "app/downloads/$id/0.page")
            assertFailsWith<ReadingFailure.OfflineUnavailable> {
                third.comicReading.loadImageBytes("download", "work", "chapter", opened.pages[0].imageKey)
            }
        } finally {
            third.close()
            files.deleteRecursively(root)
        }
    }

    @Test
    fun reconcilesAtomicMoveBeforeDatabaseCommitAndFallsBackOnMissingPage() = runTest {
        val root = filesRoot()
        val id = Uuid.random().toString()
        val first = open(root, MockEngine { error("Unexpected network") })
        try {
            first.service.installFromContent("download.js", script)
        } finally {
            first.close()
        }
        val driver = createFileBackedTestDriver(root, "downloads")
        try {
            val queries = ExtensionDb(driver).downloadJobsQueries
            queries.insertDownload(id, "download", "work", "chapter")
            queries.updateDownload(DownloadStatus.DOWNLOADING.name, 2, 2, id)
            val packages = ChapterContentPackages(files, root / "app/downloads")
            packages.prepare(id)
            packages.commit(id, listOf(
                packages.writePage(id, ComicPage(one, 0, one), byteArrayOf(10)),
                packages.writePage(id, ComicPage(two, 1, two), byteArrayOf(20)),
            ))
            packages.prepare(id)
        } finally {
            driver.close()
        }
        val calls = AtomicInteger()
        val second = open(root, MockEngine {
            calls.incrementAndGet()
            respond(byteArrayOf(33))
        })
        try {
            assertEquals(id, await(second, DownloadStatus.COMPLETE).id)
            assertFalse(files.exists(root / "app/downloads/$id.partial"))
            val opened = second.comicReading.openChapter("download", "work", "chapter")
            assertContentEquals(byteArrayOf(20), second.comicReading.loadImageBytes("download", "work", "chapter", opened.pages[1].imageKey))
            assertEquals(0, calls.get())
            files.delete(root / "app/downloads/$id/0.page")
            assertContentEquals(byteArrayOf(33), second.comicReading.loadImageBytes("download", "work", "chapter", opened.pages[0].imageKey))
            assertEquals(1, calls.get())
        } finally {
            second.close()
        }
        val third = open(root, MockEngine { error("Unreadable content should be reconciled on open") }, online = false)
        try {
            assertEquals(id, await(third, DownloadStatus.FAILED).id)
            assertEquals(0, third.downloads.countCompleted("download", "work"))
            assertFalse(files.exists(root / "app/downloads/$id"))
        } finally {
            third.close()
            files.deleteRecursively(root)
        }
    }

    @Test
    fun newChaptersAreQueuedInReadingOrderOnlyWhenEnabled() = runTest {
        val root = filesRoot()
        val session = open(root, MockEngine { awaitCancellation() })
        try {
            val work = session.library.addToLibrary("download", WorkKind.COMIC, "work", "Demo", addedAtMillis = 1)
            val update = app.koyura.domain.updates.WorkUpdate(work, 2, listOf("newest", "older").map {
                app.koyura.domain.updates.DetectedChapter(it, it, 2, false)
            })
            session.downloadTriggers.onNewChapters(update)
            assertTrue(session.downloads.jobs.value.isEmpty())
            session.downloadTriggers.setPreferences(DownloadTriggerPreferences(downloadNewChapters = true))
            session.downloadTriggers.onNewChapters(update)
            session.downloadTriggers.onNewChapters(update)
            assertEquals(listOf("older", "newest"), session.downloads.jobs.value.map { it.chapter.chapterId })
        } finally {
            session.close()
            files.deleteRecursively(root)
        }
    }

    private fun filesRoot(): Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "chapter-download-${Uuid.random()}"

    private suspend fun await(session: ExtensionSession, status: DownloadStatus): DownloadJob =
        withContext(Dispatchers.Default) {
            withTimeout(10_000) { session.downloads.observe(chapter).filterNotNull().first { it.status == status } }
        }

    private suspend fun open(root: Path, engine: MockEngine, online: Boolean = true): ExtensionSession =
        openExtensionSession(
            createFileBackedTestDriver(root, "downloads"), files, root / "app", HttpClient(engine),
            VeneraEnvironment("1.16.0", "en", "test"), isOnline = { online },
        )

    private val script = """
        class Source extends ComicSource {
            name = "Download"; key = "download"; version = "1.0.0"; minAppVersion = "1.0.0";
            comic = { loadEp: () => ({ images: ["$one", "$two"] }) };
        }
    """.trimIndent()
}
