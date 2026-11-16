package app.koyura.media

import app.koyura.media.epub.EpubNovel
import app.koyura.media.epub.openEpubNovel
import app.koyura.media.novel.useImport
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves that both test targets can reach the checked-in EPUB fixtures and open them through the
 * public importer.
 *
 * Runs on the JVM and in the iOS simulator; [fixtureRoot] is wired per platform in
 * `build.gradle.kts`. This replaces the earlier `EpubArchive`-based check: the importer is the only
 * public read path, so validating container resolution here exercises the real code.
 */
class EpubFixtureSelfCheckTest {
    @Test
    fun opensPrideAndPrejudiceAndResolvesPackageDocument() {
        val epubPath = fixtureRoot().toPath() / "epub" / "pride-and-prejudice.epub"
        assertTrue(FileSystem.SYSTEM.exists(epubPath), "missing fixture: $epubPath")

        openEpubNovel(FileSystem.SYSTEM, epubPath).useImport { novel ->
            val epub = novel as EpubNovel
            assertEquals("epub/content.opf", epub.packageDocument.opfPath)
            assertEquals("Pride and Prejudice", novel.metadata.title)
            assertTrue(novel.tableOfContents.isNotEmpty())
        }
    }

    @Test
    fun opensColeVoyageOfLifeAndResolvesPackageDocument() {
        val epubPath = fixtureRoot().toPath() / "epub" / "cole-voyage-of-life.epub"
        assertTrue(FileSystem.SYSTEM.exists(epubPath), "missing fixture: $epubPath")

        openEpubNovel(FileSystem.SYSTEM, epubPath).useImport { novel ->
            val epub = novel as EpubNovel
            assertEquals("EPUB/cole.opf", epub.packageDocument.opfPath)
            assertEquals("Thomas Cole - The Voyage of Life", novel.metadata.title)
        }
    }
}
