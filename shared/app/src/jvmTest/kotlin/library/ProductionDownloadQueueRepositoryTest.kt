package app.koyura.app.library

import app.koyura.data.session.openExtensionSession
import app.koyura.domain.downloads.DownloadStatus
import app.koyura.domain.library.LibraryChapter
import app.koyura.domain.library.WorkKind
import app.koyura.source.runtime.VeneraEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ProductionDownloadQueueRepositoryTest {
    @Test
    fun failedAndCancelledJobsSurviveReopenAndCanBeEnqueuedAgain() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "queue-${Uuid.random()}"
        try {
            val environment = VeneraEnvironment("1.16.0", "en", "test")
            val first = openExtensionSession(root, environment)
            val id = try {
                first.library.addToLibrary("source", WorkKind.COMIC, "work", "Work title", addedAtMillis = 1)
                first.library.syncChapters("source", WorkKind.COMIC, "work", listOf(
                    LibraryChapter("source", WorkKind.COMIC, "work", "chapter", "Chapter title"),
                ))
                val queue = ProductionDownloadQueueRepository(first.downloadQueue)
                queue.enqueue("source", "work", "chapter").id.also { id ->
                    withContext(Dispatchers.Default) {
                        withTimeout(5_000) { queue.jobs.first { jobs -> jobs.any { it.id == id && it.status == DownloadStatus.FAILED } } }
                    }
                    val item = queue.loadQueue().single()
                    assertEquals("Work title", item.workTitle)
                    assertEquals("Chapter title", item.chapterTitle)
                    assertEquals("FAILED", item.status.name)
                }
            } finally {
                first.close()
            }
            val reopened = openExtensionSession(root, environment)
            try {
                val queue = ProductionDownloadQueueRepository(reopened.downloadQueue)
                assertEquals(id, queue.loadQueue().single().id)
                queue.resume(id)
                assertEquals("FAILED", queue.loadQueue().single().status.name)
                queue.clear()
                assertTrue(queue.loadQueue().isEmpty())
                assertEquals(DownloadStatus.CANCELLED, queue.statusesForWork("source", "work")["chapter"]?.status)
                assertEquals(id, queue.enqueue("source", "work", "chapter").id)
            } finally {
                reopened.close()
            }
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }
}
