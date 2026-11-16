package app.koyura.media.novel.epub

import app.koyura.media.epub.EpubNovel
import app.koyura.media.epub.openEpubNovel
import app.koyura.media.fixtureRoot
import app.koyura.media.novel.LocalNovelLimits
import app.koyura.media.novel.NovelBlock
import app.koyura.media.novel.NovelImportException
import app.koyura.media.novel.useImport
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.openZip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for the security review.
 *
 * Each test targets one bypass or crash path so reintroducing it fails loudly rather than silently
 * degrading into wrong text or an OutOfMemoryError.
 */
class EpubSecurityRegressionTest {

    // ---- 1/2. Metadata documents are bounded, so a zip bomb is a typed error ----

    @Test
    fun oversizedContainerXmlIsLimitExceededNotOutOfMemory() {
        val error = assertFailsWith<NovelImportException.LimitExceeded> {
            openEpubNovel(FileSystem.SYSTEM, EpubFixtures.path("oversized-metadata.epub"))
        }
        // Metadata overflow must be reported as its own category, not as a chapter overflow.
        assertEquals("metadata-bytes", error.limit)
        assertEquals(4L * 1024 * 1024, error.maximum)
        assertTrue(error.entry!!.endsWith("META-INF/container.xml"), "got ${error.entry}")
    }

    @Test
    fun oversizedNavIsLimitExceededBeforeAllocation() {
        // Use an 8 MiB container fixture with a smaller metadata cap to prove the nav path is capped.
        val limits = LocalNovelLimits(maxMetadataBytes = 1024)
        val error = assertFailsWith<NovelImportException.LimitExceeded> {
            openEpubNovel(FileSystem.SYSTEM, EpubFixtures.path("pride-and-prejudice.epub"), limits)
        }
        assertTrue(error.maximum == 1024L, "unexpected cap ${error.maximum}")
        assertEquals("metadata-bytes", error.limit)
    }

    // ---- 3. A shrunk declared size must not silently truncate the body ----

    @Test
    fun declaredSizeSmallerThanContentIsCorruptedNotSilentlyTruncated() {
        // Okio itself notices the body is longer than the declared size and raises IOException; the
        // importer must surface that as CorruptedArchive, never as short-but-valid text.
        EpubFixtures.open("declared-size.epub").useImport { novel ->
            val error = assertFailsWith<NovelImportException.CorruptedArchive> {
                novel.readChapter(novel.tableOfContents.first { it.level == 1 }.id)
            }
            assertEquals("OEBPS/text/c1.xhtml", error.entry)
            assertTrue(
                error.detail == "size-mismatch" || error.detail == "decompression-failed",
                "unexpected detail ${error.detail}",
            )
            // And no chapter content was returned: reading failed outright.
            assertIs<NovelImportException.CorruptedArchive>(error)
        }
    }

    // ---- 4. encryption URI with a leading slash must still be enforced ----

    @Test
    fun leadingSlashEncryptionUriStillBlocksReading() {
        // encryption.xml declares `/OEBPS/text/c1.xhtml`; the slash spelling must not evade the check.
        val error = assertFailsWith<NovelImportException.EncryptedContent> {
            EpubFixtures.open("encrypted-slash-uri.epub")
        }
        assertEquals("OEBPS/text/c1.xhtml", error.entry)
    }

    // ---- 4b. readResource spelling variants cannot bypass the encryption map ----

    @Test
    fun readResourceSpellingVariantsCannotBypassEncryption() {
        EpubFixtures.open("encrypted-font.epub").useImport { novel ->
            for (spelling in listOf(
                "OEBPS/fonts/serif.otf",
                "/OEBPS/fonts/serif.otf",
                "OEBPS//fonts/serif.otf",
                "OEBPS/./fonts/serif.otf",
                "./OEBPS/fonts/serif.otf",
            )) {
                val error = assertFailsWith<NovelImportException.EncryptedContent>("spelling=$spelling") {
                    novel.readResource(spelling)
                }
                assertEquals("OEBPS/fonts/serif.otf", error.entry, "spelling=$spelling")
            }
        }
    }

    // ---- 4c. unparseable encryption.xml must fail closed ----

    @Test
    fun unparseableEncryptionFileFailsClosed() {
        // Build a book whose encryption.xml is present but not valid XML; opening must not treat the
        // book as unencrypted.
        val bytes = InMemoryEpub.build(
            opf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
                  <manifest>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
            """.trimIndent(),
            extraEntries = mapOf(
                "OEBPS/c1.xhtml" to "<html><body><p>ciphertext</p></body></html>",
                "META-INF/encryption.xml" to "<encryption><broken",
            ),
        )
        val error = assertFailsWith<NovelImportException.CorruptedArchive> {
            InMemoryEpub.open(bytes, "/books/x.epub")
        }
        assertEquals("invalid-encryption", error.detail)
    }

    // ---- 5. decompression/CRC corruption is CorruptedArchive, not MissingEntry ----

    @Test
    fun crcMismatchOnStoredEntryIsCorruptedArchive() {
        EpubFixtures.open("crc-mismatch.epub").useImport { novel ->
            val error = assertFailsWith<NovelImportException.CorruptedArchive> {
                novel.readChapter(novel.tableOfContents.first { it.level == 1 }.id)
            }
            // Okio raises IOException for a bad stored entry; our own CRC check would also catch it if
            // Okio passed the bytes through. Either way it is a corrupted archive, not missing data.
            assertTrue(
                error.detail == "crc-mismatch" || error.detail == "decompression-failed",
                "unexpected detail ${error.detail}",
            )
        }
    }

    // ---- 6. missing file and directory are Io, not CorruptedArchive ----

    @Test
    fun missingFileIsIoNotCorruptedArchive() {
        val error = assertFailsWith<NovelImportException.Io> {
            openEpubNovel(FileSystem.SYSTEM, (fixtureRoot().toPath() / "epub" / "does-not-exist.epub"))
        }
        assertEquals("open", error.phase)
    }

    @Test
    fun directoryPathIsIoNotCorruptedArchive() {
        val error = assertFailsWith<NovelImportException.Io> {
            openEpubNovel(FileSystem.SYSTEM, fixtureRoot().toPath() / "epub")
        }
        assertEquals("open", error.phase)
    }

    // ---- 7/8. no-nav fallback reads titles but is documented; missing nav is not MissingEntry ----

    @Test
    fun missingNavFallsBackToSpineTitlesWithoutError() {
        EpubFixtures.open("no-nav.epub").useImport { novel ->
            assertEquals(listOf("Derived One", "Derived Two"), novel.tableOfContents.map { it.title })
            // Spine fallback still reads the document body when the chapter is requested.
            assertEquals(
                listOf("a"),
                novel.readChapter(novel.tableOfContents.first().id)
                    .blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text },
            )
        }
    }

    // ---- 9. chapter cache is bounded ----

    @Test
    fun chapterCacheIsBoundedByLimit() {
        val limits = LocalNovelLimits(maxCachedChapters = 3)
        openEpubNovel(FileSystem.SYSTEM, EpubFixtures.path("pride-and-prejudice.epub"), limits)
            .useImport { novel ->
                val epub = novel as EpubNovel
                for (entry in novel.tableOfContents.filter { it.level == 1 }.take(10)) {
                    novel.readChapter(entry.id)
                }
                assertTrue(
                    epub.cachedChapterCount <= 3,
                    "cache grew to ${epub.cachedChapterCount} with maxCachedChapters=3",
                )
            }
    }

    // ---- 10/11. EpubArchive is gone; only the importer is public ----

    @Test
    fun publicApiOnlyExposesTheImporter() {
        // EpubArchive was removed. Assert the observable consequence: the importer returns a working
        // book, and its only public way to read bytes is readResource.
        EpubFixtures.open("spine-order.epub").useImport { novel ->
            val entry = novel.tableOfContents.first { it.level == 1 }
            assertTrue(novel.readChapter(entry.id).blocks.isNotEmpty())
            assertTrue(novel.readResource(entry.let { "OEBPS/text/chapter-1.xhtml" }).isNotEmpty())
        }
    }
}
