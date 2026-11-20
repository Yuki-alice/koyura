package app.koyura.data.browse

import app.koyura.data.extension.createFileBackedTestDriver
import app.koyura.data.session.openExtensionSession
import app.koyura.domain.browse.SourceFeedId
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SourceFeedServiceTest {
    @Test
    fun persistsOrderingAndReconcilesReplacedExploreDeclarationsByTitleAndSource() = runTest {
        val files = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "feed-${Uuid.random()}"
        val latestA = SourceFeedId("alpha", "Latest")
        val latestB = SourceFeedId("beta", "Latest")
        val curated = SourceFeedId("alpha", "Curated")
        val removed = SourceFeedId("alpha", "Removed")
        val new = SourceFeedId("alpha", "New")
        var revision = "1.0.0"
        suspend fun open() = openExtensionSession(
            createFileBackedTestDriver(root, "feed"), files, root / "app",
            HttpClient(MockEngine { request ->
                val key = request.url.encodedPath.substringAfterLast('/').removeSuffix(".js")
                val pages = when {
                    key == "beta" -> listOf("Latest" to "numbered")
                    revision == "1.0.0" -> listOf("Latest" to "numbered", "Removed" to "plain", "Curated" to "plain")
                    else -> listOf("New" to "plain", "Curated" to "plain", "Latest" to "cursor")
                }
                respond(script(key, revision, pages))
            }),
            VeneraEnvironment("1.16.0", "en", "test"),
        )
        try {
            val first = open()
            try {
                first.service.installFromUrl("https://fixtures.test/alpha.js")
                first.service.installFromUrl("https://fixtures.test/beta.js")
                assertEquals(listOf(latestA, removed, curated, latestB), first.feed.feedItems().map { it.id })
                assertTrue(first.feed.setEnabled(latestA, false))
                first.feed.reorder(listOf(latestB, curated, removed))
                assertEquals(listOf(latestB, curated, removed), first.feed.feedItems().map { it.id })
                revision = "2.0.0"
                first.service.update("alpha")
                val settings = first.feed.feedSettings()
                assertEquals(listOf(latestA, latestB, curated, new), settings.map { it.id })
                assertFalse(settings[0].enabled)
                assertTrue(settings[0].cursor)
                assertFalse(settings[0].numbered)
                assertEquals(2, settings[0].index)
                assertTrue(settings[1].numbered)
                assertTrue(settings.last().enabled)
                assertFalse(first.feed.setEnabled(removed, false))
            } finally {
                first.close()
            }
            val reopened = open()
            try {
                assertEquals(listOf(latestB, curated, new), reopened.feed.feedItems().map { it.id })
                assertTrue(reopened.feed.setEnabled(latestA, true))
                assertEquals(listOf(latestA, latestB, curated, new), reopened.feed.feedItems().map { it.id })
            } finally {
                reopened.close()
            }
        } finally {
            files.deleteRecursively(root, mustExist = false)
        }
    }

    private fun script(key: String, version: String, pages: List<Pair<String, String>>): String {
        val definitions = pages.joinToString(",") { (title, pagination) ->
            when (pagination) {
                "numbered" -> """{title:"$title",type:"multiPageComicList",load:page=>({comics:[],maxPage:1})}"""
                "cursor" -> """{title:"$title",type:"multiPageComicList",loadNext:next=>({comics:[],next:null})}"""
                else -> """{title:"$title",type:"multiPartPage",load:()=>[]}"""
            }
        }
        return """
            class Source extends ComicSource {
                name = "$key"; key = "$key"; version = "$version"; minAppVersion = "1.0.0";
                url = "https://fixtures.test/$key.js";
                explore = [$definitions];
            }
        """.trimIndent()
    }
}
