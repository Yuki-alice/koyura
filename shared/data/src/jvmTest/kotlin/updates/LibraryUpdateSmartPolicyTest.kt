package app.koyura.data.updates

import app.koyura.data.extension.createFileBackedTestDriver
import app.koyura.data.session.openExtensionSession
import app.koyura.domain.library.LibraryChapter
import app.koyura.domain.library.WorkKind
import app.koyura.domain.updates.SmartUpdatePreferences
import app.koyura.domain.updates.UpdateSkipReason
import app.koyura.domain.updates.UpdateTitleMetadata
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class LibraryUpdateSmartPolicyTest {
    @Test
    fun skipsWorkByDefaultAndPersistsExplicitOverridesAcrossReopen() = runTest {
        val files = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "smart-updates-${Uuid.random()}"
        val requests = mutableListOf<String>()
        var offline = true
        suspend fun open() = openExtensionSession(
            createFileBackedTestDriver(root, "smart-updates"), files, root / "app",
            HttpClient(MockEngine { request ->
                val id = request.url.encodedPath.removePrefix("/")
                requests += id
                when {
                    id == "retry" && offline -> respond("temporary error", HttpStatusCode.ServiceUnavailable)
                    id == "final" -> respond("""{"tags":{"状态":["状态：已完结"]},"chapters":{"ep":"Final"}}""")
                    else -> respond("""{"chapters":{"ep":"New chapter"}}""")
                }
            }),
            VeneraEnvironment("1.16.0", "en", "test"),
        )
        try {
            val first = open()
            try {
                first.service.installFromContent("smartscan.js", script)
                assertEquals(SmartUpdatePreferences(), first.updates.preferences())
                val titles = listOf("unstarted", "behind", "caught-up", "empty", "final", "once", "upcoming", "retry")
                for (title in titles) {
                    val displayTitle = if (title == "retry") "Ignore Me (a title)" else title
                    first.library.addToLibrary("smartscan", WorkKind.COMIC, title, displayTitle, addedAtMillis = 1)
                }
                for (title in listOf("unstarted", "behind", "caught-up", "once")) {
                    first.library.syncChapters("smartscan", WorkKind.COMIC, title, listOf(
                        LibraryChapter("smartscan", WorkKind.COMIC, title, "old"),
                    ))
                }
                for (title in listOf("behind", "caught-up", "once")) {
                    first.library.markChapterRead("smartscan", WorkKind.COMIC, title, "old", true)
                }
                first.library.syncChapters("smartscan", WorkKind.COMIC, "behind", listOf(
                    LibraryChapter("smartscan", WorkKind.COMIC, "behind", "old"),
                    LibraryChapter("smartscan", WorkKind.COMIC, "behind", "unread"),
                ))
                first.updates.setTitleMetadata("smartscan", "once", UpdateTitleMetadata(fetchOnce = true))
                first.updates.setTitleMetadata("smartscan", "upcoming", UpdateTitleMetadata(nextUpdateMillis = Long.MAX_VALUE))

                val result = first.updates.checkNow()
                assertEquals(listOf("retry"), result.failures.map { it.work.workId })
                assertEquals(setOf("empty", "final", "caught-up"), result.updates.map { it.work.workId }.toSet())
                assertEquals(mapOf(
                    "unstarted" to UpdateSkipReason.HAS_UNREAD,
                    "behind" to UpdateSkipReason.HAS_UNREAD,
                    "once" to UpdateSkipReason.FETCH_ONCE,
                    "upcoming" to UpdateSkipReason.OUTSIDE_RELEASE_PERIOD,
                ), result.skipped.associate { it.work.workId to it.reason })
                assertEquals(setOf("empty", "final", "retry", "caught-up"), requests.toSet())
                assertNull(result.skipped.first().work.lastUpdateCheckMillis)

                first.updates.setPreferences(SmartUpdatePreferences(
                    skipWithUnread = false,
                    excludedTitleTerms = listOf("  IGNORE me ", "ignore me", "  "),
                ))
            } finally {
                first.close()
            }
            requests.clear()
            offline = false
            val reopened = open()
            try {
                assertEquals(listOf("IGNORE me"), reopened.updates.preferences().excludedTitleTerms)
                val result = reopened.updates.checkNow()
                val skips = result.skipped.associate { it.work.workId to it.reason }
                assertEquals(UpdateSkipReason.COMPLETED, skips["final"])
                assertEquals(UpdateSkipReason.NOT_STARTED, skips["unstarted"])
                assertEquals(UpdateSkipReason.TITLE_FILTER, skips["retry"])
                assertTrue("behind" in requests)
                assertTrue(listOf("retry", "unstarted", "final").none { it in requests })

                requests.clear()
                val retry = reopened.updates.retryFailures()
                assertEquals(listOf("retry"), requests)
                assertEquals("ep", retry.updates.single().newChapters.single().id)
                assertTrue(reopened.updates.failures().isEmpty())

                reopened.updates.setPreferences(SmartUpdatePreferences(enabled = false, excludedTitleTerms = listOf("empty")))
                val unfiltered = reopened.updates.checkNow()
                assertTrue(unfiltered.skipped.isEmpty())
                assertEquals(8, unfiltered.updates.size)
            } finally {
                reopened.close()
            }
        } finally {
            files.deleteRecursively(root, mustExist = false)
        }
    }

    private val script = """
        class Source extends ComicSource {
            name = "Smart scan"; key = "smartscan"; version = "1.0.0"; minAppVersion = "1.0.0";
            comic = { loadInfo: async id => {
                const response = await Network.get("https://updates.test/" + id, { "cache-time": "no" });
                if (response.status !== 200) throw "Temporary failure";
                return JSON.parse(response.body);
            } };
        }
    """.trimIndent()
}
