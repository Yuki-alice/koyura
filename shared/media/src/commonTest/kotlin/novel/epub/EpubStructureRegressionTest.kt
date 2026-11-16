package app.koyura.media.novel.epub

import app.koyura.media.epub.EpubNovel
import app.koyura.media.epub.EpubPathResolver
import app.koyura.media.epub.ZipCentralDirectory
import app.koyura.media.epub.openEpubNovel
import app.koyura.media.novel.LocalNovelLimits
import app.koyura.media.novel.NovelBlock
import app.koyura.media.novel.NovelImportException
import app.koyura.media.novel.useImport
import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.Sink
import okio.Source
import okio.Timeout
import okio.openZip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for the structure review.
 *
 * Each test names the defect it locks down so a future refactor that reintroduces it fails loudly.
 */
class EpubStructureRegressionTest {

    // ---- 1. Bare text inside block containers must not be dropped ----

    @Test
    fun bareTextInsideBlockContainersIsPreserved() {
        EpubFixtures.open("textloss.epub").useImport { novel ->
            val text = novel.readChapter(novel.tableOfContents.single().id)
                .blocks.filterIsInstance<NovelBlock.Paragraph>()
                .map { it.text }
            assertTrue("first para" in text, "missing first paragraph: $text")
            assertTrue("Volume heading text" in text, "loose div text dropped: $text")
            assertTrue("inner para" in text)
            assertTrue("trailing sentence" in text)
            assertTrue("Plain quoted text without inner p." in text, "blockquote text dropped: $text")
            assertTrue("line one line two line three" in text, "br-separated poetry dropped: $text")
            assertTrue("Editor note." in text, "aside text dropped: $text")
            assertTrue("Figure caption text." in text, "figcaption dropped: $text")
            assertTrue("final" in text)

            // The figure image is still present as an image.
            assertTrue(
                novel.readChapter(novel.tableOfContents.single().id)
                    .blocks.filterIsInstance<NovelBlock.Image>().any { it.reference.altText == "fig" },
            )
        }
    }

    // ---- 2. Images nested inside inline wrappers must still be images ----

    @Test
    fun imagesInsideLinksSpansAndListItemsBecomeImageBlocks() {
        EpubFixtures.open("images-in-links.epub").useImport { novel ->
            val blocks = novel.readChapter(novel.tableOfContents.single().id).blocks
            val images = blocks.filterIsInstance<NovelBlock.Image>()
            assertEquals(
                setOf("linked", "spanned", "inlist"),
                images.map { it.reference.altText }.toSet(),
                "nested images were flattened to alt text: $blocks",
            )
            assertEquals(
                setOf("OEBPS/text/img/x.png", "OEBPS/text/img/y.png", "OEBPS/text/img/z.png"),
                images.mapNotNull { it.reference.resource?.path }.toSet(),
            )
            // And the alt text must not additionally be emitted as paragraphs.
            assertTrue(
                blocks.filterIsInstance<NovelBlock.Paragraph>().none { it.text in setOf("linked", "spanned", "inlist") },
                "alt text leaked as a paragraph: $blocks",
            )
        }
    }

    // ---- 3. Landmarks nav must not be used as the TOC ----

    @Test
    fun landmarksNavIsNotUsedAsTableOfContents() {
        EpubFixtures.open("nav-landmarks.epub").useImport { novel ->
            val titles = novel.tableOfContents.map { it.title }
            assertEquals(listOf("Chapter One", "Chapter Two"), titles)
            assertFalse("Start" in titles, "landmarks entry leaked into TOC: $titles")
        }
    }

    // ---- 4. nav type with a non-epub prefix ----

    @Test
    fun navTypeWithOpsPrefixIsRecognized() {
        EpubFixtures.open("nav-prefixed.epub").useImport { novel ->
            assertEquals(listOf("Prefixed Title"), novel.tableOfContents.map { it.title })
        }
    }

    // ---- 5. Groups sharing a title, and ungrouped chapters ----

    @Test
    fun duplicateGroupTitlesAndUngroupedChaptersGetCorrectParents() {
        EpubFixtures.open("nav-groups.epub").useImport { novel ->
            val toc = novel.tableOfContents
            assertEquals(
                listOf("Part", "One", "Part", "Two", "Three"),
                toc.map { it.title },
                "duplicate group titles collapsed or invisible: $toc",
            )
            assertEquals(listOf(0, 1, 0, 1, 1), toc.map { it.level })
            // Each group owns only its own chapter; the trailing ungrouped chapter has no parent.
            assertEquals(toc[0].id, toc[1].parentId)
            assertEquals(toc[2].id, toc[3].parentId)
            assertNull(toc[4].parentId, "ungrouped chapter inherited a group: ${toc[4]}")
            // The two "Part" entries must be distinct ids.
            assertTrue(toc[0].id != toc[2].id)
        }
    }

    // ---- 6. Repeated spine itemref must still yield unique ids ----

    @Test
    fun repeatedSpineItemrefProducesUniqueIds() {
        EpubFixtures.open("dup-spine.epub").useImport { novel ->
            val ids = novel.tableOfContents.map { it.id }
            assertEquals(3, ids.size)
            assertEquals(3, ids.distinct().size, "duplicate chapter ids: $ids")
            // Both copies read the same document, and neither read throws.
            assertEquals(
                listOf("one", "two", "one"),
                novel.tableOfContents.map { entry ->
                    novel.readChapter(entry.id).blocks.filterIsInstance<NovelBlock.Paragraph>().single().text
                },
            )
        }
    }

    // ---- 7. readResource normalizes and rejects escapes ----

    @Test
    fun readResourceRejectsUnnormalizedAndEscapingPaths() {
        EpubFixtures.open("encrypted-font.epub").useImport { novel ->
            // The font is encrypted; every spelling of its path must report EncryptedContent.
            for (spelling in listOf(
                "OEBPS/fonts/serif.otf",
                "/OEBPS/fonts/serif.otf",
            )) {
                val error = assertFailsWith<NovelImportException.EncryptedContent>("spelling $spelling") {
                    novel.readResource(spelling)
                }
                assertEquals("OEBPS/fonts/serif.otf", error.entry)
            }
        }
        EpubFixtures.open("refs.epub").useImport { novel ->
            // Escaping the root is a MissingEntry after normalization, never a read of container.xml.
            assertFailsWith<NovelImportException.MissingEntry> {
                novel.readResource("../META-INF/container.xml")
            }
            // Redundant segments and leading slash resolve to the same readable entry.
            assertEquals(
                novel.readResource("OEBPS/images/a b.png").size,
                novel.readResource("/OEBPS/./images/a b.png").size,
            )
        }
    }

    // ---- 8. Namespace-prefixed OPF structure ----

    @Test
    fun prefixedOpfElementsAreParsed() {
        EpubFixtures.open("opf-prefixed.epub").useImport { novel ->
            assertEquals("Prefixed OPF", novel.metadata.title)
            assertEquals(listOf("Prefixed Author"), novel.metadata.authors)
            assertEquals(listOf("Prefixed Chapter"), novel.tableOfContents.map { it.title })
        }
    }

    // ---- 9. container with a bad first rootfile but a good second ----

    @Test
    fun firstBrokenRootfileFallsBackToNextUsableOne() {
        val container = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
              <rootfiles>
                <rootfile media-type="application/oebps-package+xml"/>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()
        val location = app.koyura.media.epub.EpubPackageParser.parseContainer("x.epub", container)
        assertEquals("OEBPS/content.opf", location.packageDocumentPath)
    }

    // ---- 10/11 covered elsewhere: EpubArchive removed; see EpubFixtureSelfCheckTest ----

    // ---- 12. control characters in decoded paths ----

    @Test
    fun decodedControlCharactersAreRejected() {
        val archive = "/books/x.epub"
        fun reason(reference: String): String = assertFailsWith<NovelImportException.UnsafeReference> {
            EpubPathResolver.resolve(archive, "OEBPS/text/c.xhtml", reference)
        }.reason
        assertEquals("control-character", reason("images/%00.png"))
        assertEquals("control-character", reason("images/tab%09name.png"))
        assertEquals("control-character", reason("images/new%0Aline.png"))
    }

    // ---- 14. real fixture image bytes are exact ----

    @Test
    fun realFixtureImageBytesMatchArchiveListing() {
        EpubFixtures.open("cole-voyage-of-life.epub").useImport { novel ->
            // Sizes are taken from `unzip -l cole-voyage-of-life.epub`; exact values catch "big enough".
            assertEquals(72811, novel.readResource("EPUB/images/1-childhood-320.jpg").size)
            assertEquals(12559, novel.readResource("EPUB/images/portrait.jpg").size)
            assertEquals(149915, novel.readResource("EPUB/images/1-childhood-1024.jpg").size)
        }
    }

    // ---- 15. title fallback drops the .epub extension ----

    @Test
    fun missingDcTitleFallsBackToFileStemNotFullName() {
        // A book with an empty dc:title: build it in memory and confirm the stem is used.
        val pkgOpf = """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title></dc:title></metadata>
              <manifest>
                <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine><itemref idref="c1"/></spine>
            </package>
        """.trimIndent()
        val pkg = app.koyura.media.epub.EpubPackageParser.parsePackage("OEBPS/content.opf", "OEBPS/content.opf", pkgOpf)
        // The parser reports a blank title; the importer is responsible for the file-stem fallback.
        assertEquals("", pkg.title)

        val bytes = InMemoryEpub.build(
            opf = pkgOpf,
            extraEntries = mapOf("OEBPS/c1.xhtml" to "<html><body><p>x</p></body></html>"),
        )
        InMemoryEpub.open(bytes, "/books/长夜行.epub").useImport { novel ->
            // The importer derives the title from the path it was opened with, so the extension must
            // be stripped exactly as TXT does.
            assertEquals("长夜行", novel.metadata.title)
        }
    }
}

/**
 * Builds minimal EPUB archives in memory so tests can vary structure without a committed fixture.
 *
 * The archive is written with `FileSystem.SYSTEM` into a temporary file because Okio's `openZip` needs
 * a real path; the file is deleted by the caller through [open]'s returned handle lifecycle.
 */
object InMemoryEpub {
    fun build(opf: String, extraEntries: Map<String, String>): ByteArray {
        val tmp = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / ("epub-${kotlin.random.Random.nextLong()}.epub")
        val rawSink = FileSystem.SYSTEM.sink(tmp)
        try {
            val sink = rawSink.buffer()
            val zipWriter = SimpleZipWriter(sink)
            zipWriter.add("mimetype", "application/epub+zip".encodeToByteArray(), store = true)
            zipWriter.add(
                "META-INF/container.xml",
                ("""<?xml version="1.0" encoding="UTF-8"?>
                    <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>""").encodeToByteArray(),
            )
            zipWriter.add("OEBPS/content.opf", opf.encodeToByteArray())
            for ((name, content) in extraEntries) {
                zipWriter.add(name, content.encodeToByteArray())
            }
            zipWriter.finish()
        } finally {
            rawSink.close()
        }
        val bytes = FileSystem.SYSTEM.read(tmp) { readByteArray() }
        FileSystem.SYSTEM.delete(tmp)
        return bytes
    }

    /**
     * Writes [bytes] under a temporary directory using the file name from [displayPath], then opens it.
     *
     * The real file name matters because TXT and EPUB share the "title falls back to the file stem"
     * rule; opening a random temp name would not exercise it.
     */
    fun open(bytes: ByteArray, displayPath: String): app.koyura.media.novel.NovelImport =
        open(bytes, displayPath, LocalNovelLimits.DEFAULT)

    /**
     * Like [open], but with explicit [limits].
     *
     * A failed open removes its temporary directory; a successful one leaves it to the OS temp
     * cleanup, exactly as the single-argument overload does.
     */
    fun open(
        bytes: ByteArray,
        displayPath: String,
        limits: LocalNovelLimits,
    ): app.koyura.media.novel.NovelImport {
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "epub-inmem-${kotlin.random.Random.nextLong()}"
        FileSystem.SYSTEM.createDirectories(dir)
        val name = displayPath.substringAfterLast('/').ifBlank { "book.epub" }
        val tmp = dir / name
        FileSystem.SYSTEM.write(tmp) { write(bytes) }
        return try {
            openEpubNovel(FileSystem.SYSTEM, tmp, limits)
        } catch (e: Throwable) {
            FileSystem.SYSTEM.deleteRecursively(dir)
            throw e
        }
    }
}

/** Writes a ZIP with the stored-mimetype convention EPUB expects. */
private class SimpleZipWriter(private val sink: okio.BufferedSink) {
    private val records = mutableListOf<Record>()
    private var offset = 0L

    private data class Record(
        val name: String,
        val crc: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Long,
    )

    fun add(name: String, data: ByteArray, store: Boolean = false) {
        val crc = crc32(data)
        val nameBytes = name.encodeToByteArray()
        val payload = data // stored-only writer keeps the test helper simple and deterministic
        val headerOffset = offset

        sink.writeIntLe(0x04034b50)
        sink.writeShortLe(20)
        sink.writeShortLe(0)
        sink.writeShortLe(0) // stored
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeIntLe(crc.toInt())
        sink.writeIntLe(payload.size)
        sink.writeIntLe(payload.size)
        sink.writeShortLe(nameBytes.size)
        sink.writeShortLe(0)
        sink.write(nameBytes)
        sink.write(payload)
        offset += 30L + nameBytes.size + payload.size
        records += Record(name, crc, payload.size.toLong(), payload.size.toLong(), headerOffset)
    }

    fun finish() {
        val centralStart = offset
        for (record in records) {
            val nameBytes = record.name.encodeToByteArray()
            sink.writeIntLe(0x02014b50)
            sink.writeShortLe(20)
            sink.writeShortLe(20)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeIntLe(record.crc.toInt())
            sink.writeIntLe(record.compressedSize.toInt())
            sink.writeIntLe(record.uncompressedSize.toInt())
            sink.writeShortLe(nameBytes.size)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeShortLe(0)
            sink.writeIntLe(0)
            sink.writeIntLe(record.localHeaderOffset.toInt())
            sink.write(nameBytes)
        }
        val centralSize = sink.buffer.size - centralStart
        sink.writeIntLe(0x06054b50)
        sink.writeShortLe(0)
        sink.writeShortLe(0)
        sink.writeShortLe(records.size)
        sink.writeShortLe(records.size)
        sink.writeIntLe(centralSize.toInt())
        sink.writeIntLe(centralStart.toInt())
        sink.writeShortLe(0)
        sink.flush()
    }
}

private val CRC_TABLE: IntArray = IntArray(256) { index ->
    var value = index
    repeat(8) { value = if (value and 1 != 0) (value ushr 1) xor 0xEDB88320.toInt() else value ushr 1 }
    value
}

private fun crc32(bytes: ByteArray): Long {
    var crc = -1
    for (byte in bytes) crc = (crc ushr 8) xor CRC_TABLE[(crc xor byte.toInt()) and 0xFF]
    return crc.inv().toLong() and 0xFFFFFFFFL
}
