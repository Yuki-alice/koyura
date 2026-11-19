package app.koyura.data.updates

import app.koyura.data.extension.createFileBackedTestDriver
import app.koyura.data.session.openExtensionSession
import app.koyura.domain.library.LibraryChapter
import app.koyura.domain.library.WorkKind
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class LibraryUpdateScannerTest {
    @Test
    fun detectsNewChaptersPreservesLocalHistoryAndRetriesPersistentFailures() = runTest {
        val files = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "updates-${Uuid.random()}"
        val requests = mutableListOf<String>()
        var broken = true
        suspend fun open() = openExtensionSession(
            createFileBackedTestDriver(root, "updates"), files, root / "app",
            HttpClient(MockEngine { request ->
                val work = request.url.encodedPath.removePrefix("/")
                requests += work
                when {
                    work == "broken" && broken -> respond("temporary failure", HttpStatusCode.ServiceUnavailable)
                    work == "broken" -> respond("""{"chapters":{"ep_recovered":"Recovered"}}""")
                    else -> respond("""{"chapters":{"Volume":{"ep_keep":"Updated title","ep_new":"New chapter"}}}""")
                }
            }),
            VeneraEnvironment("1.16.0", "en", "test"),
        )
        try {
            val first = open()
            try {
                first.service.installFromContent("updates.js", script)
                for (id in listOf("normal", "broken")) {
                    first.library.addToLibrary("updates", WorkKind.COMIC, id, id, addedAtMillis = 10)
                }
                val local = listOf("ep_keep", "ep_gone").map { LibraryChapter("updates", WorkKind.COMIC, "normal", it, it) }
                first.library.syncChapters("updates", WorkKind.COMIC, "normal", local)
                first.library.markChapterRead("updates", WorkKind.COMIC, "normal", "ep_keep", true)
                val initial = first.updates.checkNow()
                assertEquals(listOf("broken"), initial.failures.map { it.work.workId })
                assertEquals(listOf("ep_new"), initial.updates.single().newChapters.map { it.id })
                assertNotNull(initial.failures.single().work.lastUpdateCheckMillis)
                first.library.syncChapters("updates", WorkKind.COMIC, "normal", local.take(1))
            } finally {
                first.close()
            }
            broken = false
            val reopened = open()
            try {
                assertEquals(listOf("broken"), reopened.updates.failures().map { it.work.workId })
                val chapters = reopened.library.listChapters("updates", WorkKind.COMIC, "normal").associateBy { it.chapterId }
                assertEquals(setOf("ep_keep", "ep_gone", "ep_new"), chapters.keys)
                assertEquals(true, chapters.getValue("ep_keep").isRead)
                assertEquals(false, chapters.getValue("ep_new").isRead)
                assertEquals("ep_new", reopened.updates.feed().single().newChapters.single().id)
                requests.clear()
                val retry = reopened.updates.retryFailures()
                assertEquals(listOf("broken"), requests)
                assertEquals("ep_recovered", retry.updates.single().newChapters.single().id)
                assertTrue(reopened.updates.failures().isEmpty())
                assertTrue(reopened.updates.checkNow().updates.all { it.newChapters.isEmpty() })
                assertEquals(2, reopened.updates.feed().sumOf { it.newChapterCount })
                reopened.library.removeFromLibrary("updates", WorkKind.COMIC, "normal")
                assertEquals("broken", reopened.updates.feed().single().work.workId)
            } finally {
                reopened.close()
            }
        } finally {
            files.deleteRecursively(root, mustExist = false)
        }
    }

    private val script = """
        class Source extends ComicSource {
            name = "Updates";
            key = "updates";
            version = "1.0.0";
            minAppVersion = "1.0.0";
            comic = {
                loadInfo: async id => {
                    const result = await Network.get("https://updates.test/" + id);
                    if (result.status !== 200) throw "Service unavailable";
                    return JSON.parse(result.body);
                }
            };
        }
    """.trimIndent()
}
