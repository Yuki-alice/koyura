package dev.reader.media

import dev.reader.media.epub.EpubArchive
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves that both test targets can reach the checked-in EPUB fixtures.
 *
 * Runs on the JVM and in the iOS simulator; [fixtureRoot] is wired per platform in
 * `build.gradle.kts`.
 */
class EpubFixtureSelfCheckTest {
    @Test
    fun opensPrideAndPrejudiceAndReadsContainer() {
        val epubPath = fixtureRoot().toPath() / "epub" / "pride-and-prejudice.epub"
        assertTrue(FileSystem.SYSTEM.exists(epubPath), "missing fixture: $epubPath")

        val archive = EpubArchive.open(FileSystem.SYSTEM, epubPath)
        assertEquals("epub/content.opf", archive.container.packageDocumentPath)
        assertEquals(
            "application/oebps-package+xml",
            archive.container.packageDocumentMediaType,
        )

        val containerXml = archive.readText(EpubArchive.CONTAINER_ENTRY)
        assertTrue(containerXml.contains("urn:oasis:names:tc:opendocument:xmlns:container"))
        assertTrue(containerXml.contains("full-path=\"epub/content.opf\""))
    }

    @Test
    fun opensColeVoyageOfLifeAndReadsContainer() {
        val epubPath = fixtureRoot().toPath() / "epub" / "cole-voyage-of-life.epub"
        assertTrue(FileSystem.SYSTEM.exists(epubPath), "missing fixture: $epubPath")

        val archive = EpubArchive.open(FileSystem.SYSTEM, epubPath)
        assertEquals("EPUB/cole.opf", archive.container.packageDocumentPath)
        assertTrue(archive.readText(EpubArchive.CONTAINER_ENTRY).contains("EPUB/cole.opf"))
    }
}
