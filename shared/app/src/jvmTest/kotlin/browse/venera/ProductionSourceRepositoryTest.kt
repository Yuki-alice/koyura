package app.koyura.app.browse.venera

import app.koyura.data.session.ExtensionSession
import app.koyura.data.session.openExtensionSession
import app.koyura.domain.browse.SourceFeedId
import app.koyura.source.runtime.VeneraEnvironment
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ProductionSourceRepositoryTest {
    @Test
    fun fixedSourceAndFeedSettingsSurviveReopen() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "source-settings-${Uuid.random()}"
        val environment = VeneraEnvironment("1.16.0", "en", "test")
        val feedId = SourceFeedId("sample", "Latest")
        try {
            val original = openExtensionSession(root, environment)
            try {
                original.service.installFromContent("sample.js", """
                    class Sample extends ComicSource {
                        name = "Sample"; key = "sample"; version = "1.0.0"; minAppVersion = "1.0.0";
                        search = {load: () => ({comics: [], maxPage: 1})};
                        explore = [{title: "Latest", type: "multiPageComicList", load: (page) => ({comics: [], maxPage: page})}];
                    }
                """.trimIndent())
                val sources = original.sources()
                assertTrue(sources.discovery.sources().sources.single().pinned.not())
                sources.discovery.setPinned("sample", true)
                sources.discovery.setDefaultSource("sample")
                assertEquals("2", sources.loadFeedPage(feedId, position = 2).jsonObject.getValue("maxPage").jsonPrimitive.content)
                assertTrue(sources.feed.setEnabled(feedId, false))
            } finally {
                original.close()
            }
            val reopened = openExtensionSession(root, environment)
            try {
                val sources = reopened.sources()
                assertTrue(sources.discovery.sources().sources.single().pinned)
                assertEquals("sample", sources.discovery.sources().defaultSourceKey)
                assertEquals(setOf("sample"), sources.discovery.searchSourceKeys())
                assertTrue(sources.feed.feedItems().isEmpty())
                assertFailsWith<IllegalArgumentException> { sources.loadFeedPage(feedId) }
            } finally {
                reopened.close()
            }
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }

    private fun ExtensionSession.sources() = ProductionSourceRepository(discovery, feed, service.browse)
}
