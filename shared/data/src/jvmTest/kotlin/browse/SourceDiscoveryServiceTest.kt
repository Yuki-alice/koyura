package app.koyura.data.browse

import app.koyura.data.extension.createFileBackedTestDriver
import app.koyura.data.session.openExtensionSession
import app.koyura.source.runtime.VeneraEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class SourceDiscoveryServiceTest {
    @Test
    fun persistsPinGroupsDefaultAndVisibilityAndFiltersBeforeSearchOrFeed() = runTest {
        val files = FileSystem.SYSTEM
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "discovery-${Uuid.random()}"
        suspend fun open() = openExtensionSession(
            createFileBackedTestDriver(root, "discovery"), files, root / "app",
            HttpClient(MockEngine { error("Discovery must not fetch content") }),
            VeneraEnvironment("1.16.0", "en", "test"),
        )
        try {
            val first = open()
            val group = try {
                first.service.installFromContent("normal.js", source("normal", "Alpha", mode = "load"))
                first.service.installFromContent("marked.js", source("marked", "Zeta", mode = "loadNext"))
                first.service.installFromContent("hidden.js", source("hidden", "Middle", mode = "load"))
                first.service.installFromContent("exploreonly.js", source("exploreonly", "Browse", mode = null))
                val discovery = first.discovery
                assertTrue(discovery.sources().showNsfw)
                assertTrue(discovery.sources().groups.isEmpty())
                assertEquals(setOf("normal", "marked", "hidden"), discovery.searchSourceKeys())
                val reading = discovery.createGroup(" Reading ")
                val special = discovery.createGroup("Special")
                assertFailsWith<IllegalArgumentException> { discovery.createGroup("reading") }
                discovery.setSourceGroups("marked", setOf(reading.id, special.id))
                discovery.setSourceGroups("normal", setOf(reading.id))
                discovery.setPinned("marked", true)
                assertEquals(listOf("marked", "normal"), discovery.sources(groupId = reading.id).sources.map { it.key })
                discovery.setDefaultSource("marked")
                assertEquals("marked", discovery.sources().defaultSourceKey)
                discovery.setNsfw("marked", true)
                discovery.setHidden("hidden", true)
                discovery.setShowNsfw(false)
                discovery.renameGroup(reading.id, " To read ")
                assertEquals(setOf("normal"), discovery.searchSourceKeys())
                assertEquals(setOf("normal", "exploreonly"), first.feed.feedItems().map { it.sourceKey }.toSet())
                assertEquals(4, first.feed.feedSettings().size)
                reading
            } finally {
                first.close()
            }

            val reopened = open()
            try {
                val discovery = reopened.discovery
                val settings = discovery.sources(includeFiltered = true)
                assertFalse(settings.showNsfw)
                assertEquals("marked", settings.preferredSourceKey)
                assertNull(settings.defaultSourceKey)
                assertEquals("To read", settings.groups.first().name)
                assertEquals("marked", settings.sources.first().key)
                assertTrue(settings.sources.first().pinned)
                assertTrue(settings.sources.first().nsfw)
                assertTrue(settings.sources.single { it.key == "hidden" }.hidden)
                assertFalse(settings.sources.single { it.key == "exploreonly" }.canSearch)
                assertEquals(listOf("normal"), discovery.sources(groupId = group.id).sources.map { it.key })
                assertEquals(setOf("normal"), discovery.searchSourceKeys())
                assertEquals(setOf("normal", "exploreonly"), reopened.feed.feedItems().map { it.sourceKey }.toSet())

                discovery.setShowNsfw(true)
                assertEquals("marked", discovery.sources().defaultSourceKey)
                assertEquals(setOf("normal", "marked"), discovery.searchSourceKeys())
                discovery.setShowNsfw(false)
                discovery.setNsfw("marked", false)
                discovery.setHidden("hidden", false)
                assertEquals(setOf("normal", "marked", "hidden"), discovery.searchSourceKeys())
                discovery.deleteGroup(group.id)
                assertTrue(discovery.sources(groupId = group.id).sources.isEmpty())
                assertFalse(discovery.sources().sources.any { group.id in it.groupIds })
                discovery.setDefaultSource(null)
                assertNull(discovery.sources().defaultSourceKey)
            } finally {
                reopened.close()
            }
        } finally {
            files.deleteRecursively(root, mustExist = false)
        }
    }

    private fun source(key: String, title: String, mode: String?): String {
        val search = mode?.let { """search = {$it: () => ({comics:[],maxPage:1,next:null})};""" }.orEmpty()
        return """
            class Source extends ComicSource {
                name = "$title"; key = "$key"; version = "1.0.0"; minAppVersion = "1.0.0";
                category = {title:"Work categories",parts:[]};
                explore = [{title:"Latest",type:"multiPartPage",load:()=>[]}];
                $search
            }
        """.trimIndent()
    }
}
