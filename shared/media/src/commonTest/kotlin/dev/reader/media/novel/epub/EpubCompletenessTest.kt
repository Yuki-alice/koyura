package dev.reader.media.novel.epub

import dev.reader.media.epub.ZipCentralDirectory
import dev.reader.media.epub.openEpubNovel
import dev.reader.media.fixtureRoot
import dev.reader.media.novel.NovelBlock
import dev.reader.media.novel.NovelImportException
import dev.reader.media.novel.useImport
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regression tests for the third review round.
 *
 * The central theme is that integrity verification must be **complete**: an index that only covers the
 * last part of a long archive, or a CRC check that only covers stored entries, is a silent bypass. Each
 * test below would fail against that class of bug.
 */
class EpubIntegrityCompletenessTest {

    // ---- H1: index must cover every entry, not just the last 64 KiB ----

    @Test
    fun centralDirectoryIndexesEveryEntryOfALongArchive() {
        val path = EpubFixtures.path("big-central-dir-trunc.epub")
        val index = ZipCentralDirectory.read(FileSystem.SYSTEM, path)
        assertTrue(index != null, "long archive produced no index")
        // 1205 entries were written; the old tail-scanning index saw only a few hundred.
        assertEquals(1205, index!!.size)
        // First, middle and last entries are all present.
        assertTrue(index.entry("OEBPS/filler/filler-0000-padding-padding-padding.bin") != null)
        assertTrue(index.entry("OEBPS/filler/filler-0600-padding-padding-padding.bin") != null)
        assertTrue(index.entry("OEBPS/filler/filler-1199-padding-padding-padding.bin") != null)
        assertTrue(index.entry("OEBPS/text/c1.xhtml") != null)
    }

    @Test
    fun shrunkDeclaredSizeInLongArchiveIsCorruptedNotServed() {
        EpubFixtures.open("big-central-dir-trunc.epub").useImport { novel ->
            val error = assertFailsWith<NovelImportException.CorruptedArchive> {
                novel.readChapter(novel.tableOfContents.first { it.level == 1 }.id)
            }
            assertEquals("OEBPS/text/c1.xhtml", error.entry)
            assertTrue(
                error.detail == "size-mismatch" || error.detail == "decompression-failed",
                "unexpected detail ${error.detail}",
            )
        }
    }

    @Test
    fun storedCorruptionInLongArchiveIsCorruptedNotServed() {
        EpubFixtures.open("big-central-dir-crc.epub").useImport { novel ->
            val error = assertFailsWith<NovelImportException.CorruptedArchive> {
                novel.readChapter(novel.tableOfContents.first { it.level == 1 }.id)
            }
            assertTrue(
                error.detail == "crc-mismatch" || error.detail == "decompression-failed",
                "unexpected detail ${error.detail}",
            )
        }
    }

    // ---- H2: deflated entries are CRC-checked too ----

    @Test
    fun sameLengthDeflateSwapIsDetectedByCrc() {
        // Okio returns the tampered bytes (sizes match); the importer's CRC check must reject them.
        // If Okio itself catches the CRC, the error is still CorruptedArchive, never served text.
        EpubFixtures.open("deflate-swap.epub").useImport { novel ->
            val entry = novel.tableOfContents.first { it.level == 1 }
            val error = assertFailsWith<NovelImportException.CorruptedArchive> {
                novel.readChapter(entry.id)
            }
            assertTrue(
                error.detail == "crc-mismatch" || error.detail == "decompression-failed",
                "unexpected detail ${error.detail}",
            )
        }
    }

    @Test
    fun deflateSwapNeverLeaksTamperedText() {
        EpubFixtures.open("deflate-swap.epub").useImport { novel ->
            val entry = novel.tableOfContents.first { it.level == 1 }
            val result = runCatching { novel.readChapter(entry.id) }
            // The tampered fixture must be rejected. If it is ever accepted, the read must not contain
            // the tampered marker; asserting both branches means the test cannot silently pass.
            if (result.isSuccess) {
                val text = result.getOrThrow().blocks.joinToString(" ") {
                    (it as? NovelBlock.Paragraph)?.text ?: ""
                }
                assertTrue("YOPUMZGD" !in text, "tampered text was served: $text")
                throw AssertionError("deflate-swap chapter was served instead of rejected")
            }
            val error = result.exceptionOrNull()
            assertTrue(
                error is NovelImportException.CorruptedArchive,
                "expected CorruptedArchive, got $error",
            )
        }
    }

    // ---- M: legal empty encryption.xml must not be rejected ----

    @Test
    fun emptyEncryptionElementOpensNormally() {
        EpubFixtures.open("empty-encryption.epub").useImport { novel ->
            assertEquals(
                listOf("readable"),
                novel.readChapter(novel.tableOfContents.single().id)
                    .blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text },
            )
        }
    }

    @Test
    fun commentOnlyEncryptionXmlOpensNormally() {
        val bytes = InMemoryEpub.build(
            opf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
                  <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
            """.trimIndent(),
            extraEntries = mapOf(
                "OEBPS/c1.xhtml" to "<html><body><p>plain</p></body></html>",
                "META-INF/encryption.xml" to
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!-- nothing encrypted -->",
            ),
        )
        InMemoryEpub.open(bytes, "/books/x.epub").useImport { novel ->
            assertEquals(
                listOf("plain"),
                novel.readChapter(novel.tableOfContents.single().id)
                    .blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text },
            )
        }
    }

    @Test
    fun malformedEncryptionXmlIsStillRejected() {
        val bytes = InMemoryEpub.build(
            opf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
                  <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
            """.trimIndent(),
            extraEntries = mapOf(
                "OEBPS/c1.xhtml" to "<html><body><p>cipher</p></body></html>",
                "META-INF/encryption.xml" to "<encryption><broken",
            ),
        )
        val error = assertFailsWith<NovelImportException.CorruptedArchive> {
            InMemoryEpub.open(bytes, "/books/x.epub")
        }
        assertEquals("invalid-encryption", error.detail)
    }
}

class EpubInlineTextRegressionTest {

    // ---- M1: inline markup inside a block container must not split a sentence ----

    @Test
    fun inlineMarkupInsideBlockContainerStaysOneParagraph() {
        EpubFixtures.open("textloss.epub").useImport { novel ->
            val paragraphs = novel.readChapter(novel.tableOfContents.single().id)
                .blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text }
            // The fixture's figure caption contains <em>; it must stay one paragraph.
            assertContains(paragraphs, "Figure caption text.")
        }
    }

    @Test
    fun divWithInlineElementKeepsSingleParagraph() {
        val blocks = dev.reader.media.epub.EpubXhtmlParser.parse(
            "x.epub",
            "c.xhtml",
            "<html><body><div>She said <em>no</em> loudly.</div></body></html>",
        ).blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text }
        assertEquals(listOf("She said no loudly."), blocks)
    }

    @Test
    fun adjacentInlineSpansAreOneParagraph() {
        val blocks = dev.reader.media.epub.EpubXhtmlParser.parse(
            "x.epub",
            "c.xhtml",
            "<html><body><div><span>Roses are red</span><span>, violets are blue</span></div></body></html>",
        ).blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text }
        assertEquals(listOf("Roses are red, violets are blue"), blocks)
    }

    @Test
    fun blockquoteAndFigcaptionWithInlineMarkupStayWhole() {
        val blocks = dev.reader.media.epub.EpubXhtmlParser.parse(
            "x.epub",
            "c.xhtml",
            "<html><body>" +
                "<blockquote>He said <em>no</em> again.</blockquote>" +
                "<figure><figcaption>A <em>fine</em> caption</figcaption></figure>" +
                "</body></html>",
        ).blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text }
        assertEquals(listOf("He said no again.", "A fine caption"), blocks)
    }

    @Test
    fun realBlockElementsStillStartNewParagraphs() {
        val blocks = dev.reader.media.epub.EpubXhtmlParser.parse(
            "x.epub",
            "c.xhtml",
            "<html><body><div>one<p>two</p>three</div></body></html>",
        ).blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text }
        assertEquals(listOf("one", "two", "three"), blocks)
    }
}

class EpubNavListRegressionTest {

    // ---- M4: direct and wrapped lists both contribute ----

    @Test
    fun directAndWrappedNavListsBothContribute() {
        EpubFixtures.open("nav-mixed.epub").useImport { novel ->
            assertEquals(
                listOf("Direct A", "Wrapped B"),
                novel.tableOfContents.map { it.title },
                "wrapped list was dropped when a direct list existed",
            )
        }
    }

    @Test
    fun navParserReadsBothListFormsDirectly() {
        val nav1 = """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <body><nav epub:type="toc">
              <ol><li><a href="a.xhtml">A</a></li></ol>
              <div><ol><li><a href="b.xhtml">B</a></li></ol></div>
            </nav></body></html>
        """.trimIndent()
        val entries = dev.reader.media.epub.EpubNavParser.parse(
            "x.epub", "nav.xhtml", nav1, setOf("a.xhtml", "b.xhtml"),
        )
        assertEquals(listOf("A", "B"), entries.map { it.title })

        val nav2 = """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <body><nav epub:type="toc">
              <ul><li><a href="a.xhtml">A</a></li></ul>
              <ul><li><a href="b.xhtml">B</a></li></ul>
            </nav></body></html>
        """.trimIndent()
        val ulEntries = dev.reader.media.epub.EpubNavParser.parse(
            "x.epub", "nav.xhtml", nav2, setOf("a.xhtml", "b.xhtml"),
        )
        assertEquals(listOf("A", "B"), ulEntries.map { it.title })
    }
}
