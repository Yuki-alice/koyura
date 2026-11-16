package app.koyura.media.novel.epub

import app.koyura.media.epub.EpubPackageParser
import app.koyura.media.epub.EpubXhtmlParser
import app.koyura.media.epub.openEpubNovel
import app.koyura.media.novel.NovelBlock
import app.koyura.media.novel.NovelImportException
import app.koyura.media.novel.useImport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for the fourth review round.
 *
 * Two classes of defect are covered: images lost when an inline wrapper sits at block level, and
 * encryption records that are present but incomplete being treated as "not encrypted" (fail-open).
 */
class EpubBlockImageRegressionTest {

    // ---- H1: block-level <a><img/></a> must still produce an Image ----

    @Test
    fun blockLevelLinkedCoverImageIsNotFlattenedToAltText() {
        EpubFixtures.open("block-image.epub").useImport { novel ->
            val blocks = novel.readChapter(novel.tableOfContents.single().id).blocks
            val images = blocks.filterIsInstance<NovelBlock.Image>()
            assertEquals(
                setOf("cover", "plate"),
                images.map { it.reference.altText }.toSet(),
                "block-level linked images were lost: $blocks",
            )
            assertEquals(
                setOf("OEBPS/text/cover.jpg", "OEBPS/text/plate.jpg"),
                images.mapNotNull { it.reference.resource?.path }.toSet(),
            )
            // The alt strings must not additionally appear as paragraphs.
            assertTrue(
                blocks.filterIsInstance<NovelBlock.Paragraph>().none { it.text == "cover" || it.text == "plate" },
                "alt text leaked as a paragraph: $blocks",
            )
        }
    }

    @Test
    fun blockContainerLinkedImageProducesImageBlock() {
        val blocks = EpubXhtmlParser.parse(
            "x.epub",
            "OEBPS/text/c.xhtml",
            "<html><body><div class=\"cover\"><a href=\"x\"><img src=\"cover.jpg\" alt=\"c\"/></a></div></body></html>",
        ).blocks
        val image = blocks.single() as NovelBlock.Image
        // Resolved relative to the document directory inside OEBPS/text.
        assertEquals("OEBPS/text/cover.jpg", image.reference.resource?.path)
        assertEquals("c", image.reference.altText)
    }

    @Test
    fun anchorAsDirectBodyChildProducesImage() {
        val blocks = EpubXhtmlParser.parse(
            "x.epub",
            "text/c.xhtml",
            "<html><body><a href=\"y\"><img src=\"plate.jpg\" alt=\"p\"/></a></body></html>",
        ).blocks
        val image = blocks.single() as NovelBlock.Image
        assertEquals("text/plate.jpg", image.reference.resource?.path)
        assertEquals("p", image.reference.altText)
    }

    // ---- M: ruby annotations stay in one paragraph and rp is dropped ----

    @Test
    fun rubyAnnotationInsideBlockContainerStaysOneParagraph() {
        val blocks = EpubXhtmlParser.parse(
            "x.epub",
            "c.xhtml",
            "<html><body><div>これは<ruby>漢字<rp>(</rp><rt>かんじ</rt><rp>)</rp></ruby>です。</div></body></html>",
        ).blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text }
        assertEquals(1, blocks.size, "ruby split the sentence: $blocks")
        assertEquals("これは漢字かんじです。", blocks.single())
        assertFalse(blocks.single().contains("("), "rp fallback parentheses leaked")
    }

    @Test
    fun rubyFixtureParagraphIsSingle() {
        EpubFixtures.open("block-image.epub").useImport { novel ->
            val paragraphs = novel.readChapter(novel.tableOfContents.single().id)
                .blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text }
            val japanese = paragraphs.single { it.contains("漢字") }
            assertEquals("これは漢字かんじです。", japanese)
        }
    }
}

class EpubEncryptionFailClosedTest {

    // ---- H: EncryptedData without a usable reference must fail closed ----

    @Test
    fun encryptedSpineDocumentWithoutEncryptionMethodStillBlocks() {
        // The record has a CipherReference but no EncryptionMethod, so the algorithm is null. Presence
        // must still count as encrypted.
        val error = assertFailsWith<NovelImportException.EncryptedContent> {
            EpubFixtures.open("encrypted-no-method.epub")
        }
        assertEquals("OEBPS/text/c1.xhtml", error.entry)
        assertNull(error.algorithm, "algorithm should be absent for this fixture")
    }

    @Test
    fun encryptedDataWithoutCipherReferenceFailsClosed() {
        val error = assertFailsWith<NovelImportException.EncryptedContent> {
            EpubFixtures.open("encrypted-no-cipherref.epub")
        }
        // The record could not be mapped to a path, so the whole book is refused.
        assertEquals("META-INF/encryption.xml", error.entry)
    }

    @Test
    fun lowercaseEncryptedDataTagsStillBlock() {
        val error = assertFailsWith<NovelImportException.EncryptedContent> {
            EpubFixtures.open("encrypted-lowercase.epub")
        }
        assertEquals("OEBPS/text/c1.xhtml", error.entry)
    }

    @Test
    fun parserReportsPresenceWithNullAlgorithm() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                        xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
              <enc:EncryptedData>
                <enc:CipherData><enc:CipherReference URI="OEBPS/c1.xhtml"/></enc:CipherData>
              </enc:EncryptedData>
            </encryption>
        """.trimIndent()
        val map = EpubPackageParser.parseEncryption("x.epub", xml)
        assertTrue(map.containsKey("OEBPS/c1.xhtml"), "encrypted entry missing from map: $map")
        assertNull(map["OEBPS/c1.xhtml"])
    }

    @Test
    fun parserRecordsUnresolvedWhenCipherDataIsMissing() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                        xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
              <enc:EncryptedData>
                <enc:EncryptionMethod Algorithm="aes"/>
              </enc:EncryptedData>
            </encryption>
        """.trimIndent()
        val map = EpubPackageParser.parseEncryption("x.epub", xml)
        assertTrue(map.containsKey(EpubPackageParser.UNRESOLVED_ENCRYPTION_KEY), "got $map")
    }

    // ---- M: well-formedness checker must be structural, not a string heuristic ----

    @Test
    fun mismatchedClosingTagIsMalformed() {
        assertTrue(EpubPackageParser.isMalformedEncryption("<encryption></wrong>"))
    }

    @Test
    fun unterminatedElementIsMalformed() {
        assertTrue(
            EpubPackageParser.isMalformedEncryption(
                "<encryption xmlns=\"x\"><enc:EncryptedData>",
            ),
        )
    }

    @Test
    fun multipleRootElementsAreMalformed() {
        assertTrue(EpubPackageParser.isMalformedEncryption("<a/><b/>"))
    }

    @Test
    fun declarationOnlyAndCommentOnlyAreAccepted() {
        // OCF tooling writes these; they cannot hide ciphertext.
        assertFalse(EpubPackageParser.isMalformedEncryption("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"))
        assertFalse(EpubPackageParser.isMalformedEncryption("<?xml version=\"1.0\"?><!-- none -->"))
        assertFalse(EpubPackageParser.isMalformedEncryption("<encryption/>"))
        assertFalse(
            EpubPackageParser.isMalformedEncryption(
                "<encryption xmlns=\"x\"><enc:EncryptedData/></encryption>",
            ),
        )
    }

    @Test
    fun plainTextIsMalformed() {
        assertTrue(EpubPackageParser.isMalformedEncryption("lorem ipsum dolor"))
    }
}

class EpubNavHeaderRegressionTest {

    // ---- L: a list wrapped in <header> must still be found ----

    @Test
    fun navListInsideHeaderStillContributes() {
        val navXml = """
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <body><nav epub:type="toc">
              <header><ol><li><a href="a.xhtml">A</a></li></ol></header>
              <div><ol><li><a href="b.xhtml">B</a></li></ol></div>
            </nav></body></html>
        """.trimIndent()
        val entries = app.koyura.media.epub.EpubNavParser.parse(
            "x.epub", "nav.xhtml", navXml, setOf("a.xhtml", "b.xhtml"),
        )
        assertEquals(listOf("A", "B"), entries.map { it.title })
    }
}
